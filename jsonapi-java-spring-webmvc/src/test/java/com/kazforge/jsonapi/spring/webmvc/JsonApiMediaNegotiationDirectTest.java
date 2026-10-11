package com.kazforge.jsonapi.spring.webmvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Covers negotiation edges unreachable through MVC dispatch: non-handler interception, blank or
 * absent headers, and advice behavior without stored negotiation state.
 */
class JsonApiMediaNegotiationDirectTest {

  private final JsonApiMediaNegotiationInterceptor interceptor =
      new JsonApiMediaNegotiationInterceptor();
  private final JsonApiMediaNegotiationAdvice advice = new JsonApiMediaNegotiationAdvice();

  @Test
  void nonHandlerMethodIsIgnored() throws Exception {
    assertTrue(
        interceptor.preHandle(
            new MockHttpServletRequest(), new MockHttpServletResponse(), new Object()));
  }

  @Test
  void nullContentTypeIsValid() {
    assertNull(JsonApiMediaNegotiation.validateContentType(null));
  }

  @Test
  void blankContentTypeIsValid() {
    assertNull(JsonApiMediaNegotiation.validateContentType("  "));
  }

  @Test
  void nonJsonApiContentTypeIsUnsupported() {
    assertEquals("unsupported", JsonApiMediaNegotiation.validateContentType("text/plain"));
  }

  @Test
  void nullAcceptHeadersDefaultToJsonApi() {
    assertEquals(
        JsonApiMediaNegotiation.JSON_API_VALUE, JsonApiMediaNegotiation.negotiateContentType(null));
  }

  @Test
  void emptyAcceptHeadersDefaultToJsonApi() {
    assertEquals(
        JsonApiMediaNegotiation.JSON_API_VALUE,
        JsonApiMediaNegotiation.negotiateContentType(Collections.emptyEnumeration()));
  }

  @Test
  void varyIsAddedWhenAbsent() {
    MockHttpServletResponse response = new MockHttpServletResponse();
    JsonApiMediaNegotiationInterceptor.mergeVary(response);
    assertEquals(HttpHeaders.ACCEPT, response.getHeader(HttpHeaders.VARY));
  }

  @Test
  void existingVaryValuesAreMerged() {
    MockHttpServletResponse response = new MockHttpServletResponse();
    response.setHeader(HttpHeaders.VARY, "Accept-Encoding, ");
    JsonApiMediaNegotiationInterceptor.mergeVary(response);
    assertEquals("Accept-Encoding, Accept", response.getHeader(HttpHeaders.VARY));
  }

  @Test
  void existingAcceptVaryIsKept() {
    MockHttpServletResponse response = new MockHttpServletResponse();
    response.setHeader(HttpHeaders.VARY, HttpHeaders.ACCEPT);
    JsonApiMediaNegotiationInterceptor.mergeVary(response);
    assertEquals(HttpHeaders.ACCEPT, response.getHeader(HttpHeaders.VARY));
  }

  @Test
  void wildcardVaryIsKept() {
    MockHttpServletResponse response = new MockHttpServletResponse();
    response.setHeader(HttpHeaders.VARY, "*");
    JsonApiMediaNegotiationInterceptor.mergeVary(response);
    assertEquals("*", response.getHeader(HttpHeaders.VARY));
  }

  @Test
  void supportsAnnotatedHandler() throws NoSuchMethodException {
    assertTrue(supports(new MethodParameter(AnnotatedController.class.getMethod("direct"), -1)));
  }

  @Test
  void rejectsPlainHandler() throws NoSuchMethodException {
    assertFalse(supports(new MethodParameter(PlainFixture.class.getMethod("plain"), -1)));
  }

  @Test
  void supportsConstructorParameterOnAnnotatedType() throws NoSuchMethodException {
    assertTrue(
        supports(new MethodParameter(AnnotatedController.class.getDeclaredConstructor(), -1)));
  }

  @Test
  void rejectsConstructorParameterOnPlainType() throws NoSuchMethodException {
    assertFalse(supports(new MethodParameter(PlainFixture.class.getDeclaredConstructor(), -1)));
  }

  @Test
  void bodyPassesThroughWithoutNegotiatedState() throws NoSuchMethodException {
    MockHttpServletRequest servletRequest = new MockHttpServletRequest();
    String body = "{\"data\":[]}";
    assertSame(
        body,
        advice.beforeBodyWrite(
            body,
            new MethodParameter(AnnotatedController.class.getMethod("direct"), -1),
            null,
            StringHttpMessageConverter.class,
            new ServletServerHttpRequest(servletRequest),
            new ServletServerHttpResponse(new MockHttpServletResponse())));
  }

  @Test
  void bodyPassesThroughForPlainHandler() throws NoSuchMethodException {
    MockHttpServletRequest servletRequest = new MockHttpServletRequest();
    servletRequest.setAttribute(
        JsonApiMediaNegotiation.NEGOTIATED_ATTRIBUTE, JsonApiMediaNegotiation.JSON_API_VALUE);
    MockHttpServletResponse servletResponse = new MockHttpServletResponse();
    String body = "{\"ok\":true}";
    assertSame(
        body,
        advice.beforeBodyWrite(
            body,
            new MethodParameter(PlainFixture.class.getMethod("plain"), -1),
            null,
            StringHttpMessageConverter.class,
            new ServletServerHttpRequest(servletRequest),
            new ServletServerHttpResponse(servletResponse)));
    assertNull(servletResponse.getHeader(HttpHeaders.CONTENT_TYPE));
  }

  @Test
  void negotiatedBodySetsHeadersBeforeWrite() throws NoSuchMethodException {
    MockHttpServletRequest servletRequest = new MockHttpServletRequest();
    servletRequest.setAttribute(
        JsonApiMediaNegotiation.NEGOTIATED_ATTRIBUTE, JsonApiMediaNegotiation.JSON_API_VALUE);
    MockHttpServletResponse servletResponse = new MockHttpServletResponse();
    ServerHttpRequest request = new ServletServerHttpRequest(servletRequest);
    ServerHttpResponse response = new ServletServerHttpResponse(servletResponse);
    String body = "{\"data\":[]}";
    assertSame(
        body,
        advice.beforeBodyWrite(
            body,
            new MethodParameter(AnnotatedController.class.getMethod("direct"), -1),
            null,
            StringHttpMessageConverter.class,
            request,
            response));
    response.close();
    assertEquals(
        JsonApiMediaNegotiation.JSON_API_VALUE,
        servletResponse.getHeader(HttpHeaders.CONTENT_TYPE));
    assertEquals(HttpHeaders.ACCEPT, servletResponse.getHeader(HttpHeaders.VARY));
  }

  private boolean supports(MethodParameter parameter) {
    return advice.supports(parameter, StringHttpMessageConverter.class);
  }

  @JsonApiEndpoint
  @RestController
  static class AnnotatedController {
    @GetMapping("/direct")
    public String direct() {
      return "{}";
    }
  }

  @RestController
  static class PlainFixture {
    @GetMapping(value = "/direct-plain", produces = "application/json")
    public String plain() {
      return "{}";
    }
  }
}
