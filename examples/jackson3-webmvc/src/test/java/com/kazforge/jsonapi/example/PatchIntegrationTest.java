package com.kazforge.jsonapi.example;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.kazforge.jsonapi.annotation.JsonApiAttribute;
import com.kazforge.jsonapi.annotation.JsonApiId;
import com.kazforge.jsonapi.annotation.JsonApiRelationship;
import com.kazforge.jsonapi.annotation.JsonApiResource;
import com.kazforge.jsonapi.core.model.ResourceIdentifier;
import com.kazforge.jsonapi.patch.PatchPresence;
import com.kazforge.jsonapi.spring.webmvc.JsonApiEndpoint;
import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@NullMarked
class PatchIntegrationTest {
  private static final String JSON_API = "application/vnd.api+json";
  private static final JsonMapper MAPPER = JsonMapper.builder().build();
  private AnnotationConfigWebApplicationContext context;
  private MockMvc mvc;

  @BeforeEach
  void setup() {
    context = new AnnotationConfigWebApplicationContext();
    context.setServletContext(new MockServletContext());
    context.register(ConsumerConfiguration.class, ValidationController.class);
    context.refresh();
    mvc = MockMvcBuilders.webAppContextSetup(context).build();
  }

  @AfterEach
  void close() {
    context.close();
  }

  @Test
  void bindsPresenceDtoAndUsesEachRequestsRouteIdentity() throws Exception {
    for (String id : List.of("42", "43")) {
      var response =
          mvc.perform(
                  patch("/articles/" + id)
                      .contentType(JSON_API)
                      .content(
                          """
                          {"data":{"type":"articles","id":"%s","attributes":{"title":null}}}
                          """
                              .formatted(id)))
              .andExpect(status().isOk())
              .andExpect(content().contentType(JSON_API))
              .andReturn()
              .getResponse();
      var data = MAPPER.readTree(response.getContentAsByteArray()).path("data");
      assertEquals(id, data.path("id").asString());
      assertTrue(data.at("/attributes/title").isNull());
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"Updated", "\u00a0"})
  void returnsTheUpdatedArticle(String title) throws Exception {
    var response =
        mvc.perform(
                patch("/articles/42")
                    .contentType(JSON_API)
                    .content(
                        """
                        {"data":{"type":"articles","id":"42","attributes":{"title":"%s"}}}
                        """
                            .formatted(title)))
            .andExpect(status().isOk())
            .andExpect(content().contentType(JSON_API))
            .andReturn()
            .getResponse();
    var body = MAPPER.readTree(response.getContentAsByteArray());
    assertEquals("articles", body.at("/data/type").asString());
    assertEquals("42", body.at("/data/id").asString());
    assertEquals(title, body.at("/data/attributes/title").asString());
  }

  @Test
  void bindsOnlyPrimaryChangesWhenIncludedIsPresent() throws Exception {
    var response =
        mvc.perform(
                patch("/articles/42")
                    .contentType(JSON_API)
                    .content(
                        """
                        {"data":{"type":"articles","id":"42","relationships":{
                          "editor":{"data":{"type":"people","id":"9"}}}},
                         "included":[{"type":"people","id":"9","attributes":{"unknown":"not a PATCH DTO"}}]}
                        """))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse();
    var body = MAPPER.readTree(response.getContentAsByteArray());
    assertEquals("Hello JSON:API", body.at("/data/attributes/title").asString());
    assertEquals("9", body.at("/data/relationships/editor/data/id").asString());
    assertFalse(body.has("included"));
  }

  @ParameterizedTest
  @CsvSource({"people,42", "articles,43"})
  void identityConflictsAre409(String type, String id) throws Exception {
    assertError(
        "/articles/42",
        "{\"data\":{\"type\":\"%s\",\"id\":\"%s\"}}".formatted(type, id),
        409,
        "ENDPOINT_IDENTITY_MISMATCH");
  }

  @Test
  void translatesReadMappingAndSpringValidationFailures() throws Exception {
    assertError("/articles/42", "{private", 400, "MALFORMED_JSON");
    assertError("/articles/42", "{\"data\":[]}", 400, "AGGREGATE_VALIDATION");
    assertError(
        "/articles/42",
        """
        {"data":{"type":"articles","id":"42","attributes":{"private":1}}}
        """,
        400,
        "UNKNOWN_PATCH_MEMBER");
    assertValidation(
        mvc.perform(
            patch("/articles/42")
                .contentType(JSON_API)
                .content(
                    """
                    {"data":{"type":"articles","id":"42","attributes":{"title":"\u2003"}}}
                    """)),
        "INVALID_FIELD",
        Set.of("/data/attributes/title"));
  }

  @Test
  void createValidationUsesRenamedIndexedMembers() throws Exception {
    assertValidation(
        mvc.perform(
            post("/validation/articles")
                .contentType(JSON_API)
                .content(
                    """
                    {"data":{"type":"articles","attributes":{
                      "manuscript":{"entries":[{"wire~/value":"ok"},{"wire~/value":" "}]}
                    }}}
                    """)),
        "INVALID_FIELD",
        Set.of("/data/attributes/manuscript/entries/1/wire~0~1value"));
  }

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      textBlock =
          """
          "attributes":{"section":{"entries":[{"wire~/value":" "}]}} | INVALID_FIELD | /data/attributes/section/entries/0/wire~0~1value
          "attributes":{"section":{}} | INVALID_FIELD | /data/attributes/section
          "relationships":{"editor":{"data":null}} | INVALID_FIELD | /data/relationships/editor/data
          "attributes":{} | INVALID_RESOURCE | /data
          """)
  void patchValidatesSuppliedValuesAndObjectPolicy(String supplied, String code, String pointer)
      throws Exception {
    assertValidation(
        mvc.perform(
            patch("/validation/articles/42")
                .contentType(JSON_API)
                .content("{\"data\":{\"type\":\"articles\",\"id\":\"42\"," + supplied + "}}")),
        code,
        Set.of(pointer));
  }

  @Test
  void ordinaryHandlersKeepTheirOwnBodyHandling() throws Exception {
    var response =
        mvc.perform(
                patch("/ordinary")
                    .contentType(JSON_API)
                    .content(
                        """
                        {"id":"42","title":"ordinary DTO"}
                        """))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse();
    var body = MAPPER.readTree(response.getContentAsByteArray());
    assertEquals("ordinary DTO", body.path("title").asString());
    assertFalse(body.has("data"));

    var invalid =
        mvc.perform(patch("/ordinary").contentType(JSON_API).content("{\"title\":\" \"}"))
            .andExpect(status().isBadRequest())
            .andReturn();
    assertInstanceOf(MethodArgumentNotValidException.class, invalid.getResolvedException());
    assertNotEquals(JSON_API, invalid.getResponse().getContentType());
    assertTrue(invalid.getResponse().getContentAsString().isEmpty());
  }

  private static void assertValidation(ResultActions result, String code, Set<String> pointers)
      throws Exception {
    var response =
        result
            .andExpect(status().isBadRequest())
            .andExpect(content().contentType(JSON_API))
            .andExpect(header().string("Vary", "Accept"))
            .andReturn()
            .getResponse();
    assertFalse(response.getContentAsString().contains("private"));
    JsonNode errors = MAPPER.readTree(response.getContentAsByteArray()).path("errors");
    assertEquals(pointers.size(), errors.size());
    for (JsonNode error : errors) {
      assertFalse(error.has("status"));
      assertEquals(code, error.path("code").asString());
      assertEquals(
          code.equals("INVALID_FIELD") ? "Invalid field value" : "Invalid resource",
          error.path("title").asString());
      assertFalse(error.has("detail"));
    }
    assertEquals(
        pointers,
        StreamSupport.stream(errors.spliterator(), false)
            .map(error -> error.at("/source/pointer").asString())
            .collect(Collectors.toSet()));
  }

  private void assertError(String path, String json, int status, String code) throws Exception {
    var response =
        mvc.perform(patch(path).contentType(JSON_API).content(json))
            .andExpect(status().is(status))
            .andExpect(content().contentType(JSON_API))
            .andReturn()
            .getResponse();
    var error = MAPPER.readTree(response.getContentAsByteArray()).at("/errors/0");
    assertEquals(code, error.path("code").asString());
    assertFalse(error.has("detail"));
    assertFalse(response.getContentAsString().contains("private"));
  }

  @RestController
  @JsonApiEndpoint
  static class ValidationController {
    @PostMapping("/validation/articles")
    void create(@Valid @RequestBody ArticleCreate input) {}

    @PatchMapping("/validation/articles/{id}")
    void update(@Valid @RequestBody ValidatedPatch input) {}
  }

  @JsonApiResource(type = "articles")
  record ArticleCreate(
      @JsonApiId @Nullable String id,
      @JsonApiAttribute @JsonProperty("manuscript") Optional<@Valid Section> section) {}

  @HasChange
  @JsonApiResource(type = "articles")
  record ValidatedPatch(
      @JsonApiId String id,
      @JsonApiAttribute PatchPresence<@Valid Section> section,
      @JsonApiRelationship PatchPresence<@NotNull ResourceIdentifier> editor) {}

  record Section(@NotNull List<@Valid Entry> entries) {}

  record Entry(
      @JsonProperty("wire~/value") @NotBlank(message = "private validation message")
          String value) {}

  @Target(ElementType.TYPE)
  @Retention(RetentionPolicy.RUNTIME)
  @Constraint(validatedBy = HasChangeValidator.class)
  @interface HasChange {
    String message() default "private object policy";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
  }

  public static class HasChangeValidator implements ConstraintValidator<HasChange, ValidatedPatch> {
    @Override
    public boolean isValid(ValidatedPatch patch, ConstraintValidatorContext context) {
      return !patch.section().isOmitted() || !patch.editor().isOmitted();
    }
  }
}
