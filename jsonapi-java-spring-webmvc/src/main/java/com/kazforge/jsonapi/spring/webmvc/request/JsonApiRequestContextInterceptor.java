package com.kazforge.jsonapi.spring.webmvc.request;

import com.kazforge.jsonapi.query.JsonApiQuery;
import com.kazforge.jsonapi.query.JsonApiQueryParser;
import com.kazforge.jsonapi.spring.webmvc.JsonApiEndpoint;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Prepares query-only request state for opted-in handlers, without reading the request body.
 *
 * <p>Register after the media negotiation interceptor in {@code WebMvcConfigurer#addInterceptors}.
 * Register {@link JsonApiRequestContextArgumentResolver} in {@code addArgumentResolvers} to inject
 * the context into controllers. Query failures retain their neutral diagnostics; this interceptor
 * does not choose an HTTP error response or endpoint unsupported-query policy.
 */
public class JsonApiRequestContextInterceptor implements HandlerInterceptor {
  private static final String QUERY_ATTRIBUTE = JsonApiQuery.class.getName() + ".webmvc";
  private final JsonApiQueryParser parser = new JsonApiQueryParser();

  @Override
  public boolean preHandle(
      HttpServletRequest request, HttpServletResponse response, Object handler) {
    request.removeAttribute(JsonApiRequestContext.ATTRIBUTE);
    if (!(handler instanceof HandlerMethod method)) {
      return true;
    }
    JsonApiEndpoint endpoint = method.getMethodAnnotation(JsonApiEndpoint.class);
    if (endpoint == null) {
      endpoint = method.getBeanType().getAnnotation(JsonApiEndpoint.class);
    }
    if (endpoint == null) {
      return true;
    }
    String rawQuery = request.getQueryString();
    String input = rawQuery == null ? "" : rawQuery;
    Object cached = request.getAttribute(QUERY_ATTRIBUTE);
    JsonApiQuery query;
    if (cached instanceof CachedQuery parsed && parsed.input().equals(input)) {
      query = parsed.query();
    } else {
      query = parser.parseRaw(input);
      request.setAttribute(QUERY_ATTRIBUTE, new CachedQuery(input, query));
    }
    request.setAttribute(
        JsonApiRequestContext.ATTRIBUTE,
        new JsonApiRequestContext(
            HttpMethod.valueOf(request.getMethod()), endpoint.primaryDataContext(), query));
    return true;
  }

  private record CachedQuery(String input, JsonApiQuery query) {}
}
