package com.kazforge.jsonapi.spring.webmvc.request;

import jakarta.servlet.http.HttpServletRequest;
import org.jspecify.annotations.Nullable;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/** Injects the prepared context into a {@link JsonApiRequestContext} controller parameter. */
public class JsonApiRequestContextArgumentResolver implements HandlerMethodArgumentResolver {
  @Override
  public boolean supportsParameter(MethodParameter parameter) {
    return parameter.getParameterType() == JsonApiRequestContext.class;
  }

  @Override
  public JsonApiRequestContext resolveArgument(
      MethodParameter parameter,
      @Nullable ModelAndViewContainer container,
      NativeWebRequest webRequest,
      @Nullable WebDataBinderFactory binderFactory) {
    HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
    JsonApiRequestContext context = request == null ? null : JsonApiRequestContext.find(request);
    if (context == null) {
      throw new IllegalStateException(
          "JsonApiRequestContext requires an opted-in handler and its request interceptor");
    }
    return context;
  }
}
