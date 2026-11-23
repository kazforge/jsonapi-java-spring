package com.kazforge.jsonapi.spring.webmvc.request;

import com.kazforge.jsonapi.query.JsonApiQuery;
import com.kazforge.jsonapi.query.JsonApiQueryParser;
import com.kazforge.jsonapi.spring.webmvc.JsonApiEndpoint;
import com.kazforge.jsonapi.spring.webmvc.internal.JsonApiBodyBinding;
import com.kazforge.jsonapi.spring.webmvc.internal.JsonApiHandlers;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpMethod;
import org.springframework.web.servlet.AsyncHandlerInterceptor;

/**
 * Prepares query-only request state for opted-in handlers, without reading the request body.
 *
 * <p>Register after the media negotiation interceptor in {@code WebMvcConfigurer#addInterceptors}.
 * Register {@link JsonApiRequestContextArgumentResolver} in {@code addArgumentResolvers} to inject
 * the context into controllers. Query failures retain their neutral diagnostics; this interceptor
 * does not choose an HTTP error response or endpoint unsupported-query policy.
 */
public class JsonApiRequestContextInterceptor implements AsyncHandlerInterceptor {
  private static final String QUERY_ATTRIBUTE = JsonApiQuery.class.getName() + ".webmvc";
  private final JsonApiQueryParser parser = new JsonApiQueryParser();

  @Override
  public boolean preHandle(
      HttpServletRequest request, HttpServletResponse response, Object handler) {
    JsonApiBodyBinding.clear(request);
    prepareContext(request, handler);
    return true;
  }

  @Override
  public void afterCompletion(
      HttpServletRequest request,
      HttpServletResponse response,
      Object handler,
      @Nullable Exception ex) {
    JsonApiBodyBinding.clear(request);
  }

  @Override
  public void afterConcurrentHandlingStarted(
      HttpServletRequest request, HttpServletResponse response, Object handler) {
    JsonApiBodyBinding.clear(request);
  }

  private void prepareContext(HttpServletRequest request, Object handler) {
    request.removeAttribute(JsonApiRequestContext.ATTRIBUTE);
    JsonApiEndpoint endpoint = JsonApiHandlers.findEndpoint(handler);
    if (endpoint == null) {
      return;
    }
    String rawQuery = request.getQueryString();
    String input = rawQuery == null ? "" : rawQuery;
    Object cached = request.getAttribute(QUERY_ATTRIBUTE);
    JsonApiQuery query;
    if (cached instanceof CachedQuery(var cachedInput, var cachedQuery)
        && cachedInput.equals(input)) {
      query = cachedQuery;
    } else {
      query = parser.parseRaw(input);
      request.setAttribute(QUERY_ATTRIBUTE, new CachedQuery(input, query));
    }
    request.setAttribute(
        JsonApiRequestContext.ATTRIBUTE,
        new JsonApiRequestContext(
            HttpMethod.valueOf(request.getMethod()), endpoint.primaryDataContext(), query));
  }

  private record CachedQuery(String input, JsonApiQuery query) {}
}
