package com.kazforge.jsonapi.spring.webmvc.document;

import static org.junit.jupiter.api.Assertions.*;

import com.kazforge.jsonapi.core.model.JsonApiObject;
import com.kazforge.jsonapi.core.model.Link;
import com.kazforge.jsonapi.core.model.Links;
import com.kazforge.jsonapi.core.model.Meta;
import com.kazforge.jsonapi.document.DocumentEnvelope;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.Test;

@NullMarked
class JsonApiNullableResponseTest {
  @Test
  void preservesAbsentAndPresentValuesAcrossEnvelopeConveniences() {
    JsonApiNullableResponse<String> absent = JsonApiNullableResponse.of(null);
    assertNull(absent.value());
    assertNull(absent.envelope().meta());
    assertNull(absent.envelope().links());
    assertNull(absent.envelope().jsonapi());

    JsonApiNullableResponse<String> enriched =
        absent
            .withMeta(Meta.of(Map.of("request", "req-1")))
            .withLinks(Links.ofLinks(Map.of("self", new Link.StringLink("/articles/1"))))
            .withJsonApi(JsonApiObject.ofVersion("1.1"));
    assertNull(enriched.value());
    assertEquals(
        "req-1", Objects.requireNonNull(enriched.envelope().meta()).members().get("request"));
    assertTrue(Objects.requireNonNull(enriched.envelope().links()).links().containsKey("self"));
    assertEquals(JsonApiObject.ofVersion("1.1"), enriched.envelope().jsonapi());

    JsonApiNullableResponse<String> present = JsonApiNullableResponse.of("value");
    assertEquals("value", present.value());
    assertNull(present.envelope().meta());
    assertNull(present.envelope().links());
    assertNull(present.envelope().jsonapi());
  }

  @Test
  void allowsAnAbsentValueWithAnEnvelope() {
    JsonApiNullableResponse<String> response =
        new JsonApiNullableResponse<>(null, DocumentEnvelope.empty());
    assertNull(response.value());
    assertNotNull(response.envelope());
  }

  @Test
  @SuppressWarnings({"ConstantConditions", "NullAway"})
  void ordinaryNonNullResponseStillRejectsNull() {
    assertThrows(NullPointerException.class, () -> JsonApiResponse.of(null));
  }
}
