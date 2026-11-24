package com.kazforge.jsonapi.spring.webmvc.document;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.kazforge.jsonapi.api.JsonApi;
import com.kazforge.jsonapi.api.JsonApiRelationships;
import com.kazforge.jsonapi.core.model.JsonApiDocument;
import com.kazforge.jsonapi.core.model.ResourceIdentifier;
import com.kazforge.jsonapi.core.validation.PrimaryDataContext;
import com.kazforge.jsonapi.diagnostic.CodecFailureCategory;
import com.kazforge.jsonapi.diagnostic.JsonApiDocumentReadException;
import com.kazforge.jsonapi.diagnostic.JsonApiMappingException;
import com.kazforge.jsonapi.diagnostic.MappingDiagnostic;
import com.kazforge.jsonapi.diagnostic.MappingLocation;
import com.kazforge.jsonapi.diagnostic.SourceLocation;
import com.kazforge.jsonapi.query.JsonApiQueryException;
import com.kazforge.jsonapi.spring.webmvc.JsonApiEndpoint;
import com.kazforge.jsonapi.spring.webmvc.error.JsonApiErrorResponse;
import com.kazforge.jsonapi.spring.webmvc.media.JsonApiMediaNegotiationAdvice;
import com.kazforge.jsonapi.spring.webmvc.media.JsonApiMediaNegotiationInterceptor;
import com.kazforge.jsonapi.spring.webmvc.request.JsonApiRequestContextInterceptor;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConversionException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.mock.http.MockHttpOutputMessage;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.method.HandlerMethod;

class JsonApiRelationshipBindingTest {
  private static final MediaType JSON_API = MediaType.parseMediaType("application/vnd.api+json");
  private static final ResourceIdentifier ID = ResourceIdentifier.of("people", "1");
  private static final Type MANY = listType("many");
  private static final Type WRONG_LIST = listType("wrongList");
  private static final Type WILDCARD = listType("wildcard");
  private MockHttpServletRequest request;
  private MockHttpServletResponse response;
  private JsonApiRelationshipHttpMessageConverter converter;
  private int reads;
  private int writes;
  private @Nullable Object readValue;
  private @Nullable Object written;
  private @Nullable RuntimeException failure;

  @BeforeEach
  void setup() throws Exception {
    JsonApiRelationships relationships =
        (JsonApiRelationships)
            Proxy.newProxyInstance(
                JsonApiRelationships.class.getClassLoader(),
                new Class<?>[] {JsonApiRelationships.class},
                (proxy, method, args) -> {
                  if (method.getName().startsWith("read")) {
                    reads++;
                    assertEquals(
                        "body",
                        new String(((InputStream) args[0]).readAllBytes(), StandardCharsets.UTF_8));
                    if (failure != null) throw failure;
                    return readValue;
                  }
                  writes++;
                  written = args[0];
                  if (failure != null) throw failure;
                  ((OutputStream) args[1]).write("linkage".getBytes(StandardCharsets.UTF_8));
                  return null;
                });
    JsonApi runtime =
        (JsonApi)
            Proxy.newProxyInstance(
                JsonApi.class.getClassLoader(),
                new Class<?>[] {JsonApi.class},
                (proxy, method, args) -> {
                  assertEquals("relationships", method.getName());
                  return relationships;
                });
    converter = new JsonApiRelationshipHttpMessageConverter(runtime);
    dispatch("PATCH", "relationship", null);
  }

  @AfterEach
  void clear() {
    RequestContextHolder.resetRequestAttributes();
  }

  @Test
  void readsExactlyOnceAndReturnsNonNullClearOperation() throws Exception {
    readValue = null;
    var clear =
        assertInstanceOf(
            JsonApiToOneRelationship.class,
            converter.read(JsonApiToOneRelationship.class, input()));
    assertNull(clear.identifier());
    readValue = ID;
    var one =
        assertInstanceOf(
            JsonApiToOneRelationship.class,
            converter.read(JsonApiToOneRelationship.class, input()));
    assertSame(ID, one.identifier());
    readValue = List.of(ID);
    for (String method : List.of("PATCH", "POST", "DELETE")) {
      dispatch(method, "relationship", null);
      assertSame(readValue, converter.read(MANY, null, input()));
    }
    assertEquals(5, reads);
  }

  @Test
  void doesNotClaimOtherRolesOrTransportBodies() throws Exception {
    assertEquals(List.of(JSON_API), converter.getSupportedMediaTypes());
    assertTrue(converter.canRead(JsonApiToOneRelationship.class, JSON_API));
    assertTrue(converter.canWrite(ResourceIdentifier.class, MediaType.TEXT_PLAIN));
    assertFalse(converter.canRead(JsonApiToOneRelationship.class, null));
    assertFalse(converter.canRead(JsonApiToOneRelationship.class, MediaType.APPLICATION_JSON));
    assertFalse(converter.canRead(null, null, JSON_API));
    assertFalse(converter.canWrite(null, null, JSON_API));
    for (Class<?> type :
        List.of(byte[].class, String.class, JsonApiDocument.class, JsonApiErrorResponse.class)) {
      assertFalse(converter.canRead(type, JSON_API));
      assertFalse(converter.canWrite(type, JSON_API));
    }
    dispatch("PATCH", "resource", null);
    assertFalse(converter.canRead(JsonApiToOneRelationship.class, JSON_API));
    assertFalse(converter.canWrite(ResourceIdentifier.class, JSON_API));
    assertReadFailure(HttpMessageConversionException.class, JsonApiToOneRelationship.class);
    var output = new MockHttpOutputMessage();
    assertThrows(
        HttpMessageNotWritableException.class, () -> converter.write(ID, JSON_API, output));
    RequestContextHolder.resetRequestAttributes();
    assertFalse(converter.canRead(JsonApiToOneRelationship.class, JSON_API));
    assertFalse(converter.canWrite(ResourceIdentifier.class, JSON_API));
  }

  @Test
  void runtimeTransportExclusionsApplyToObjectDeclaredResponses() {
    for (Class<?> type :
        List.of(byte[].class, String.class, JsonApiDocument.class, JsonApiErrorResponse.class)) {
      assertFalse(converter.canWrite(Object.class, type, JSON_API));
    }
    assertTrue(converter.canWrite(Object.class, ResourceIdentifier.class, JSON_API));
    assertTrue(converter.canWrite(MANY, List.of(ID).getClass(), JSON_API));
    assertTrue(converter.canWrite(MANY, null, JSON_API));
  }

  @Test
  void objectDeclaredBytesReachSpringConverterEvenWhenRelationshipConverterComesFirst()
      throws Exception {
    var mvc =
        MockMvcBuilders.standaloneSetup(new TransportController())
            .setMessageConverters(converter, new ByteArrayHttpMessageConverter())
            .addInterceptors(
                new JsonApiMediaNegotiationInterceptor(), new JsonApiRequestContextInterceptor())
            .setControllerAdvice(new JsonApiMediaNegotiationAdvice())
            .build();
    mvc.perform(get("/transport"))
        .andExpect(status().isOk())
        .andExpect(content().contentType(JSON_API))
        .andExpect(content().bytes("wire".getBytes(StandardCharsets.UTF_8)));
    assertEquals(0, writes);
  }

  @Test
  void invalidEndpointDeclarationsNeverFallBackToAnotherBinding() throws Exception {
    for (String method : List.of("POST", "DELETE", "GET", "PUT")) {
      dispatch(method, "relationship", null);
      assertReadFailure(HttpMessageConversionException.class, JsonApiToOneRelationship.class);
    }
    dispatch("PATCH", "relationship", null);
    for (Type type :
        List.of(ResourceIdentifier.class, List.class, WRONG_LIST, WILDCARD, Object.class)) {
      assertTrue(converter.canRead(type, null, JSON_API));
      assertReadFailure(HttpMessageConversionException.class, type);
    }
    assertReadFailure(HttpMessageConversionException.class, null);
    assertEquals(0, reads);
  }

  @Test
  void readFailuresAreNotRetriedAndKeepInputErrorsDistinctFromServerErrors() {
    for (MappingDiagnostic diagnostic :
        List.of(
            MappingDiagnostic.RESOURCE_TYPE_MISMATCH,
            MappingDiagnostic.RELATIONSHIP_CARDINALITY_MISMATCH)) {
      failure =
          new JsonApiMappingException(diagnostic, null, MappingLocation.of("data"), "private");
      var error =
          assertReadFailure(HttpMessageNotReadableException.class, JsonApiToOneRelationship.class);
      assertSame(failure, error.getCause());
    }
    failure =
        new JsonApiDocumentReadException(
            CodecFailureCategory.LOCAL_VALIDATION, "/data/id", SourceLocation.UNKNOWN, "private");
    assertSame(
        failure,
        assertReadFailure(HttpMessageNotReadableException.class, JsonApiToOneRelationship.class)
            .getCause());
    failure =
        new JsonApiMappingException(
            MappingDiagnostic.UNKNOWN_PATCH_MEMBER, null, MappingLocation.of("data"), "private");
    assertSame(
        failure,
        assertReadFailure(HttpMessageConversionException.class, JsonApiToOneRelationship.class)
            .getCause());
    failure = new UnsupportedOperationException("private");
    assertSame(
        failure,
        assertReadFailure(HttpMessageConversionException.class, JsonApiToOneRelationship.class)
            .getCause());
    assertEquals(5, reads);
  }

  @Test
  void responseUsesOnlyNeutralRelationshipOperationsAndPreservesStatusHeadersAndOutputMode()
      throws Exception {
    response.setStatus(202);
    response.addHeader("X-App", "yes");
    response.getWriter().write("discard");
    var output = new ServletServerHttpResponse(response);
    converter.write(ID, JSON_API, output);
    assertSame(ID, written);
    assertEquals(202, response.getStatus());
    assertEquals("yes", response.getHeader("X-App"));
    assertEquals(JSON_API.toString(), response.getContentType());
    assertEquals("linkage", response.getContentAsString());
    assertEquals(7, output.getHeaders().getContentLength());
    converter.write(new JsonApiToOneRelationship(null), JSON_API, new MockHttpOutputMessage());
    assertNull(written);
    converter.write(new JsonApiToOneRelationship(ID), JSON_API, new MockHttpOutputMessage());
    assertSame(ID, written);
    converter.write(List.of(ID), MANY, JSON_API, new MockHttpOutputMessage());
    assertEquals(List.of(ID), written);
    converter.write(List.of(), MANY, JSON_API, new MockHttpOutputMessage());
    assertEquals(List.of(), written);
    assertEquals(5, writes);
  }

  @Test
  void committedAndNoContentResponsesStayUntouched() throws Exception {
    response.setStatus(204);
    converter.write(new JsonApiToOneRelationship(null), JSON_API, new MockHttpOutputMessage());
    response.setStatus(200);
    response.flushBuffer();
    converter.write(ID, JSON_API, new MockHttpOutputMessage());
    assertEquals(0, writes);
  }

  @Test
  void invalidServerValuesAndSerializationFailuresDoNotWritePartialBodies() {
    for (Object value : List.of("private", List.of("private"), List.of(ID))) {
      var output = new MockHttpOutputMessage();
      assertThrows(
          HttpMessageNotWritableException.class, () -> converter.write(value, JSON_API, output));
      assertEquals("", output.getBodyAsString());
    }
    var invalidValues = List.of("private");
    var invalidOutput = new MockHttpOutputMessage();
    assertThrows(
        HttpMessageNotWritableException.class,
        () -> converter.write(invalidValues, MANY, JSON_API, invalidOutput));
    failure = new IllegalStateException("private");
    var output = new MockHttpOutputMessage();
    assertThrows(
        HttpMessageNotWritableException.class, () -> converter.write(ID, JSON_API, output));
    assertEquals("", output.getBodyAsString());
    assertEquals(1, writes);
  }

  @Test
  void includeRejectionPrecedesMutationAndDoesNotBecomeAServerError() throws Exception {
    dispatch("PATCH", "relationship", "include=author");
    assertReadFailure(JsonApiQueryException.class, JsonApiToOneRelationship.class);
    var output = new MockHttpOutputMessage();
    assertThrows(JsonApiQueryException.class, () -> converter.write(ID, JSON_API, output));
    assertEquals(0, reads);
    assertEquals(0, writes);
    dispatch("PATCH", "relationship", "include=");
    converter.read(JsonApiToOneRelationship.class, input());
    assertEquals(1, reads);
  }

  @Test
  void resolvesInheritedGenericControllerParameters() throws Exception {
    Type generic =
        GenericController.class.getDeclaredMethod("replace", List.class)
            .getGenericParameterTypes()[0];
    readValue = List.of(ID);
    assertSame(readValue, converter.read(generic, PeopleController.class, input()));
  }

  private <T extends RuntimeException> T assertReadFailure(Class<T> expected, @Nullable Type type) {
    var body = input();
    return assertThrows(expected, () -> converter.read(type, null, body));
  }

  private void dispatch(String method, String handlerName, @Nullable String query)
      throws Exception {
    request = new MockHttpServletRequest(method, "/relationship");
    request.setQueryString(query);
    response = new MockHttpServletResponse();
    new JsonApiRequestContextInterceptor()
        .preHandle(
            request,
            response,
            new HandlerMethod(new Controller(), Controller.class.getDeclaredMethod(handlerName)));
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request, response));
  }

  private static MockHttpInputMessage input() {
    return new MockHttpInputMessage("body".getBytes(StandardCharsets.UTF_8));
  }

  private static Type listType(String method) {
    try {
      return Controller.class.getDeclaredMethod(method).getGenericReturnType();
    } catch (ReflectiveOperationException e) {
      throw new LinkageError(e.getMessage(), e);
    }
  }

  static class GenericController<T> {
    void replace(List<T> value) {
      // Only the generic signature is used to test inherited parameter resolution.
    }
  }

  static class PeopleController extends GenericController<ResourceIdentifier> {}

  @RestController
  @JsonApiEndpoint(primaryDataContext = PrimaryDataContext.RELATIONSHIP)
  static class TransportController {
    @GetMapping("/transport")
    ResponseEntity<Object> bytes() {
      return ResponseEntity.ok("wire".getBytes(StandardCharsets.UTF_8));
    }
  }

  static class Controller {
    @JsonApiEndpoint(primaryDataContext = PrimaryDataContext.RELATIONSHIP)
    void relationship() {
      // Only endpoint metadata is read by the interceptor; this method is never invoked.
    }

    @JsonApiEndpoint
    void resource() {
      // Only resource-role metadata is used to verify converter isolation.
    }

    List<ResourceIdentifier> many() {
      return List.of();
    }

    List<String> wrongList() {
      return List.of();
    }

    List<?> wildcard() {
      return List.of();
    }
  }
}
