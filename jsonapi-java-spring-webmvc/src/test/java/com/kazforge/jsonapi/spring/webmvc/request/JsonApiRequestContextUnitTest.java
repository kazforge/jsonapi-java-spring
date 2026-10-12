package com.kazforge.jsonapi.spring.webmvc.request;

import static org.junit.jupiter.api.Assertions.*;

import com.kazforge.jsonapi.core.validation.PrimaryDataContext;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.method.HandlerMethod;

class JsonApiRequestContextUnitTest {
  @Test
  void redispatchReusesQueryButRefreshesOperationAndChangedInput() throws Exception {
    var interceptor = new JsonApiRequestContextInterceptor();
    var request = new MockHttpServletRequest("GET", "/resource");
    var response = new MockHttpServletResponse();
    var resource =
        new HandlerMethod(
            new JsonApiRequestContextTest.ResourceController(),
            "handle",
            JsonApiRequestContext.class,
            jakarta.servlet.http.HttpServletRequest.class);
    var linkage =
        new HandlerMethod(
            new JsonApiRequestContextTest.LinkageController(),
            "handle",
            JsonApiRequestContext.class);
    assertTrue(interceptor.preHandle(request, response, resource));
    var first = JsonApiRequestContext.find(request);
    assertNotNull(first);
    interceptor.preHandle(request, response, linkage);
    var second = JsonApiRequestContext.find(request);
    assertNotNull(second);
    assertSame(first.query(), second.query());
    assertEquals(PrimaryDataContext.RELATIONSHIP, second.primaryDataContext());
    request.setQueryString("include=");
    interceptor.preHandle(request, response, resource);
    var third = JsonApiRequestContext.find(request);
    assertNotNull(third);
    assertNotSame(first.query(), third.query());
    assertTrue(third.representationSelection().includeRequested());
    interceptor.preHandle(
        request,
        response,
        new HandlerMethod(new JsonApiRequestContextTest.MixedController(), "plain"));
    assertNull(JsonApiRequestContext.find(request));
    assertTrue(interceptor.preHandle(request, response, new Object()));
    assertNull(JsonApiRequestContext.find(request));
  }

  @Test
  void resolverOnlySupportsContextAndRequiresPreparedState() throws Exception {
    var resolver = new JsonApiRequestContextArgumentResolver();
    Method method =
        JsonApiRequestContextTest.ResourceController.class.getMethod(
            "handle", JsonApiRequestContext.class, jakarta.servlet.http.HttpServletRequest.class);
    var parameter = new MethodParameter(method, 0);
    assertTrue(resolver.supportsParameter(parameter));
    assertFalse(resolver.supportsParameter(new MethodParameter(method, 1)));
    var unpreparedRequest = new ServletWebRequest(new MockHttpServletRequest());
    assertThrows(
        IllegalStateException.class,
        () -> resolver.resolveArgument(parameter, null, unpreparedRequest, null));
    var nativeRequest =
        (NativeWebRequest)
            java.lang.reflect.Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[] {NativeWebRequest.class},
                (proxy, invoked, arguments) -> null);
    assertThrows(
        IllegalStateException.class,
        () -> resolver.resolveArgument(parameter, null, nativeRequest, null));
  }
}
