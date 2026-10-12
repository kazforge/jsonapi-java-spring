package com.kazforge.jsonapi.spring.webmvc;

import java.lang.reflect.Method;
import org.jspecify.annotations.Nullable;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * Applies negotiated JSON:API response headers to {@link JsonApiEndpoint} handlers.
 *
 * <p>Runs immediately before the message converter writes the body, which is late enough to
 * override the {@code Content-Type} that a {@code ResponseEntity} or {@code @ResponseBody} handler
 * selected. Handlers without {@link JsonApiEndpoint} are untouched.
 *
 * <p>Consumers register this advice as a bean, alongside {@link JsonApiMediaNegotiationInterceptor}
 * in {@code WebMvcConfigurer#addInterceptors}.
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
    return returnType.getContainingClass().isAnnotationPresent(JsonApiEndpoint.class);
  }

  @Override
  public @Nullable Object beforeBodyWrite(
      @Nullable Object body,
      MethodParameter returnType,
      MediaType selectedContentType,
      Class<? extends HttpMessageConverter<?>> selectedConverterType,
      ServerHttpRequest request,
      ServerHttpResponse response) {
    HttpHeaders headers = response.getHeaders();
    headers.setContentType(JsonApiMediaNegotiation.JSON_API);
    JsonApiMediaNegotiationInterceptor.mergeVary(headers);
    return body;
  }
}
