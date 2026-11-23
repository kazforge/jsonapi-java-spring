package com.kazforge.jsonapi.spring.webmvc.internal;

import com.kazforge.jsonapi.mapping.BoundResource;
import com.kazforge.jsonapi.spring.webmvc.request.JsonApiRequestContext;
import jakarta.servlet.http.HttpServletRequest;
import org.jspecify.annotations.Nullable;

/** Source attribution for the DTO returned by a successful bind in the current dispatch. */
public record JsonApiBodyBinding(JsonApiRequestContext dispatch, BoundResource<?> bound) {
  private static final String ATTRIBUTE = JsonApiBodyBinding.class.getName();

  public static Object remember(
      @Nullable HttpServletRequest request,
      @Nullable JsonApiRequestContext dispatch,
      BoundResource<?> bound) {
    Object resource = bound.resource();
    if (request != null && dispatch != null && dispatch == JsonApiRequestContext.find(request)) {
      request.setAttribute(ATTRIBUTE, new JsonApiBodyBinding(dispatch, bound));
    }
    return resource;
  }

  public static @Nullable JsonApiBodyBinding find(
      HttpServletRequest request, @Nullable Object target) {
    return request.getAttribute(ATTRIBUTE) instanceof JsonApiBodyBinding binding
            && binding.dispatch() == JsonApiRequestContext.find(request)
            && target != null
            && binding.bound().resource() == target
        ? binding
        : null;
  }

  public static void clear(HttpServletRequest request) {
    request.removeAttribute(ATTRIBUTE);
  }
}
