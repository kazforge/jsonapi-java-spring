package com.kazforge.jsonapi.spring.webmvc.error;

import static org.junit.jupiter.api.Assertions.*;

import com.kazforge.jsonapi.core.model.ErrorObject;
import com.kazforge.jsonapi.core.model.JsonApiObject;
import com.kazforge.jsonapi.core.model.Links;
import com.kazforge.jsonapi.core.model.Meta;
import com.kazforge.jsonapi.document.DocumentEnvelope;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class JsonApiErrorResponseTest {
  @Test
  void snapshotsErrorsWithoutChangingTheirValuesOrOrdering() {
    ErrorObject first = ErrorObject.ofTitle("First");
    ErrorObject second = ErrorObject.builder().status("409").code("SECOND").build();
    var input = new ArrayList<>(List.of(first, second));
    var response = JsonApiErrorResponse.of(input);
    input.clear();
    assertEquals(List.of(first, second), response.errors());
    assertSame(first, response.errors().getFirst());
    assertSame(second, response.errors().getLast());
    var errors = response.errors();
    assertThrows(UnsupportedOperationException.class, errors::clear);
    assertEquals(DocumentEnvelope.empty(), response.envelope());
    assertEquals(List.of(first), JsonApiErrorResponse.of(first).errors());
    assertTrue(JsonApiErrorResponse.of(List.of()).errors().isEmpty());
  }

  @Test
  void envelopeCopiesPreserveOtherMembers() {
    var original = JsonApiErrorResponse.of(ErrorObject.ofTitle("Not found"));
    var meta = Meta.empty();
    var links = Links.empty();
    var jsonapi = JsonApiObject.ofVersion("1.1");
    var response = original.withMeta(meta).withLinks(links).withJsonApi(jsonapi);
    assertEquals(new DocumentEnvelope(links, meta, jsonapi), response.envelope());
    assertEquals(original.errors(), response.errors());
    assertEquals(DocumentEnvelope.empty(), original.envelope());
    assertEquals(
        response,
        new JsonApiErrorResponse(original.errors(), new DocumentEnvelope(links, meta, jsonapi)));
  }

  @Test
  @SuppressWarnings({"NullAway", "DataFlowIssue"}) // Intentionally exercise null rejection.
  void rejectsNullListElementsAndEnvelope() {
    assertThrows(NullPointerException.class, () -> JsonApiErrorResponse.of((ErrorObject) null));
    assertThrows(
        NullPointerException.class, () -> JsonApiErrorResponse.of((List<ErrorObject>) null));
    List<ErrorObject> errorsWithNull = Arrays.asList(ErrorObject.ofTitle("First"), null);
    assertThrows(NullPointerException.class, () -> JsonApiErrorResponse.of(errorsWithNull));
    assertThrows(NullPointerException.class, () -> new JsonApiErrorResponse(List.of(), null));
  }
}
