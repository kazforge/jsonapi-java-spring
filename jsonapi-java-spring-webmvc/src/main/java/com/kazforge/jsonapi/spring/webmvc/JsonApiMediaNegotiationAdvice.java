package com.kazforge.jsonapi.spring.webmvc;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * Sets the negotiated JSON:API {@code Content-Type} and merged {@code Vary: Accept} before the
 * response body is written.
 *
 * <p>Applies only to {@link JsonApiEndpoint}-annotated handlers with a negotiated content type
 * stored by {@link JsonApiMediaNegotiationInterceptor}. Ordinary endpoints are untouched.
 */
@ControllerAdvice
public class JsonApiMediaNegotiationAdvice implements ResponseBodyAdvice<Object> {

  @Override
  public boolean supports(
      MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
    Method method = returnType.getMethod();
    if (method != null && method.isAnnotationPresent(JsonApiEndpoint.class)) {
      return true;
    }
    Class<?> containing = returnType.getContainingClass();
    return containing != null && containing.isAnnotationPresent(JsonApiEndpoint.class);
  }

  @Override
  public @Nullable Object beforeBodyWrite(
      @Nullable Object body,
      MethodParameter returnType,
      MediaType selectedContentType,
      Class<? extends HttpMessageConverter<?>> selectedConverterType,
      ServerHttpRequest request,
      ServerHttpResponse response) {
    String negotiated = negotiatedContentType(request);
    if (negotiated == null || !isJsonApiHandler(returnType)) {
      return body;
    }
    HttpHeaders headers = response.getHeaders();
    headers.setContentType(MediaType.parseMediaType(negotiated));
    mergeVary(headers);
    return body;
  }

  private static boolean isJsonApiHandler(MethodParameter returnType) {
    Method method = returnType.getMethod();
    if (method != null && method.isAnnotationPresent(JsonApiEndpoint.class)) {
      return true;
    }
    Class<?> containing = returnType.getContainingClass();
    return containing != null && containing.isAnnotationPresent(JsonApiEndpoint.class);
  }

  private static @Nullable String negotiatedContentType(ServerHttpRequest request) {
    if (request instanceof ServletServerHttpRequest servletRequest) {
      Object value =
          servletRequest
              .getServletRequest()
              .getAttribute(JsonApiMediaNegotiation.NEGOTIATED_ATTRIBUTE);
      if (value instanceof String negotiated) {
        return negotiated;
      }
    }
    return null;
  }

  private static void mergeVary(HttpHeaders headers) {
    List<String> vary = new ArrayList<>(headers.getVary());
    for (String token : vary) {
      if ("*".equals(token) || HttpHeaders.ACCEPT.equalsIgnoreCase(token)) {
        return;
      }
    }
    vary.add(HttpHeaders.ACCEPT);
    headers.setVary(vary);
  }
}
