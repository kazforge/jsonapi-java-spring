package com.kazforge.jsonapi.spring.webmvc.document;

import static org.junit.jupiter.api.Assertions.*;

import com.kazforge.jsonapi.api.*;
import com.kazforge.jsonapi.core.validation.PrimaryDataContext;
import com.kazforge.jsonapi.patch.PatchCommand;
import com.kazforge.jsonapi.spring.webmvc.JsonApiEndpoint;
import com.kazforge.jsonapi.spring.webmvc.request.JsonApiRequestContextInterceptor;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConversionException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.method.HandlerMethod;

/**
 * Declaration checks must fail before consulting the application's identity callback or runtime.
 */
@NullMarked
class JsonApiPatchTargetTest {
  private final JsonApi unusedRuntime =
      new JsonApi() {
        @Override
        public JsonApiResources resources() {
          throw new AssertionError("Unexpected resource binding");
        }

        @Override
        public JsonApiRelationships relationships() {
          throw new AssertionError("Unexpected linkage binding");
        }

        @Override
        public JsonApiDocuments documents() {
          throw new AssertionError("Unexpected document read");
        }

        @Override
        public JsonApiPatches patches() {
          throw new AssertionError("Unexpected PATCH binding");
        }
      };

  @BeforeEach
  void requestContext() throws Exception {
    var request = new MockHttpServletRequest("PATCH", "/articles/42");
    new JsonApiRequestContextInterceptor()
        .preHandle(request, new MockHttpServletResponse(), new HandlerMethod(this, "endpoint"));
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
  }

  @AfterEach
  void clearContext() {
    RequestContextHolder.resetRequestAttributes();
  }

  @ParameterizedTest
  @CsvSource({"endpoint,true", "relationship,false"})
  void onlyResourceHandlersClaimPatchDtos(String endpoint, boolean readable) throws Exception {
    var request = new MockHttpServletRequest("PATCH", "/articles/42");
    new JsonApiRequestContextInterceptor()
        .preHandle(request, new MockHttpServletResponse(), new HandlerMethod(this, endpoint));
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    var converter = new JsonApiResourceHttpMessageConverter(unusedRuntime);
    assertEquals(
        readable,
        converter.canRead(Dto.class, MediaType.parseMediaType("application/vnd.api+json")));
  }

  @ParameterizedTest
  @ValueSource(strings = {"raw", "wildcard", "unresolved", "list", "array", "commandList"})
  void invalidDeclarationsFailBeforeIdentityResolution(String field) throws Exception {
    var converter =
        new JsonApiResourceHttpMessageConverter(
            unusedRuntime,
            request -> {
              throw new AssertionError("Identity must not be resolved for an invalid declaration");
            });
    var type = Targets.class.getDeclaredField(field).getGenericType();
    var input = new MockHttpInputMessage(new byte[] {1});
    var failure =
        assertThrows(HttpMessageConversionException.class, () -> converter.read(type, null, input));
    assertInstanceOf(IllegalStateException.class, failure.getCause());
  }

  @ParameterizedTest
  @ValueSource(strings = {"dto", "command"})
  void defaultConstructorRequiresIdentityForBothPatchPaths(String field) throws Exception {
    var converter = new JsonApiResourceHttpMessageConverter(unusedRuntime);
    var type = Targets.class.getDeclaredField(field).getGenericType();
    var input = new MockHttpInputMessage(new byte[] {1});
    var failure =
        assertThrows(HttpMessageConversionException.class, () -> converter.read(type, null, input));
    assertEquals(
        "PATCH endpoint identity is required",
        Objects.requireNonNull(failure.getCause()).getMessage());
  }

  @JsonApiEndpoint(primaryDataContext = PrimaryDataContext.RESOURCE)
  @SuppressWarnings("unused") // Selected reflectively by HandlerMethod.
  public void endpoint() {
    throw new AssertionError("Only endpoint metadata is needed for converter preflight");
  }

  @SuppressWarnings({"unused", "rawtypes"}) // Reflection-only controller target declarations.
  static class Targets<T> {
    @Nullable PatchCommand raw;
    @Nullable PatchCommand<? extends Number> wildcard;
    @Nullable PatchCommand<T> unresolved;
    @Nullable List<String> list;
    String @Nullable [] array;
    @Nullable PatchCommand<List<String>> commandList;
    @Nullable Dto dto;
    @Nullable PatchCommand<Dto> command;
  }

  record Dto(String title) {}

  @JsonApiEndpoint(primaryDataContext = PrimaryDataContext.RELATIONSHIP)
  @SuppressWarnings("unused") // Selected reflectively by HandlerMethod.
  public void relationship() {
    throw new AssertionError("Only endpoint metadata is needed for converter preflight");
  }
}
