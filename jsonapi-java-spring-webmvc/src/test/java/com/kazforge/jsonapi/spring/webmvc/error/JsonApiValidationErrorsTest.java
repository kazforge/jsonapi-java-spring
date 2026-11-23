package com.kazforge.jsonapi.spring.webmvc.error;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.kazforge.jsonapi.diagnostic.MappingLocation;
import com.kazforge.jsonapi.mapping.BindingSource;
import com.kazforge.jsonapi.mapping.BoundResource;
import com.kazforge.jsonapi.patch.PatchPresence;
import com.kazforge.jsonapi.spring.webmvc.validation.PatchPresenceValueExtractor;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.valueextraction.ExtractedValue;
import jakarta.validation.valueextraction.ValueExtractor;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

class JsonApiValidationErrorsTest {
  @Test
  void sendsOnlyProvenLogicalSegmentsFromWrappedViolationsToCore() {
    var body =
        new Body(
            PatchPresence.present(new Child(" ")),
            List.of("ok", " "),
            Set.of(new Child(" ")),
            Map.of("0", new Child(" ")),
            Optional.of(" "),
            PatchPresence.present(null),
            PatchPresence.omitted());
    var result = new BeanPropertyBindingResult(body, "test");
    try (var validator = new LocalValidatorFactoryBean()) {
      validator.setConfigurationInitializer(configuration ->
          configuration
              .addValueExtractor(new PatchPresenceValueExtractor())
              .addValueExtractor(new NamedOptionalExtractor()));
      validator.afterPropertiesSet();
      validator.validate(body, result);
    }

    var errors = JsonApiValidationErrors.map(result, new RecordingBinding(body)).errors();
    assertEquals(6, errors.size());
    assertEquals(
        Set.of(
            "/data/patch/value",
            "/data/indexed/1",
            "/data/unordered",
            "/data/keyed",
            "/data/optional",
            "/data/present"),
        errors.stream()
            .map(error -> Objects.requireNonNull(error.source()).pointer())
            .collect(Collectors.toSet()));
  }

  @ParameterizedTest
  @CsvSource({
    "values[1].value, /data/values/1/value",
    "values[private.key].value, /data/values",
    "values[01].value, /data/values",
    "[0].value,",
    ".value,",
    "value/info,"
  })
  void unwrappedSpringFieldErrorsUseOnlyUnambiguousPathSyntax(String field, String pointer) {
    var body = new Object();
    var result = new BeanPropertyBindingResult(body, "test");
    result.addError(new FieldError("test", field, "private message"));
    var error = JsonApiValidationErrors.map(result, new RecordingBinding(body)).errors().getFirst();
    var source = error.source();
    assertEquals(pointer, source == null ? null : source.pointer());
  }

  record Body(
      PatchPresence<@Valid Child> patch,
      List<@NotBlank String> indexed,
      Set<@Valid Child> unordered,
      Map<String, @Valid Child> keyed,
      Optional<@NotBlank String> optional,
      PatchPresence<@NotNull String> present,
      PatchPresence<@NotNull String> omitted) {}

  record Child(@NotBlank String value) {}

  // Distinguishes a typed extraction node from a real property named "value".
  private static final class NamedOptionalExtractor
      implements ValueExtractor<Optional<@ExtractedValue ?>> {
    @Override
    public void extractValues(Optional<?> original, ValueReceiver receiver) {
      receiver.value("value", original.orElse(null));
    }
  }

  /** Echo the lookup path so any unsafe guess remains visible in the test. */
  record RecordingBinding(Object resource) implements BoundResource<Object> {
    @Override
    public BindingSource source(List<String> logicalPath) {
      return new BindingSource(
          BindingSource.Presence.PRESENT_VALUE,
          MappingLocation.of("data", logicalPath.toArray(String[]::new)));
    }
  }
}
