package com.kazforge.jsonapi.spring.webmvc.internal;

import com.kazforge.jsonapi.spring.webmvc.JsonApiEndpoint;
import org.jspecify.annotations.Nullable;
import org.springframework.web.method.HandlerMethod;

/** Shared {@link JsonApiEndpoint} lookup for Spring MVC handlers. */
public final class JsonApiHandlers {
  private JsonApiHandlers() {}

  public static @Nullable JsonApiEndpoint findEndpoint(@Nullable Object handler) {
    if (!(handler instanceof HandlerMethod method)) {
      return null;
    }
    JsonApiEndpoint endpoint = method.getMethodAnnotation(JsonApiEndpoint.class);
    return endpoint != null ? endpoint : method.getBeanType().getAnnotation(JsonApiEndpoint.class);
  }

  public static boolean isEndpoint(@Nullable Object handler) {
    return findEndpoint(handler) != null;
  }
}
