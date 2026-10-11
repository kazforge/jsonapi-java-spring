package com.kazforge.jsonapi.spring.webmvc;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

/**
 * Enforces JSON:API 1.1 media negotiation for {@link JsonApiEndpoint}-annotated handlers.
 *
 * <p>Validates {@code Content-Type} on requests with a body (415 on mismatch), negotiates {@code
 * Accept} (406 when nothing acceptable remains), and sets the negotiated response {@code
 * Content-Type} together with {@code Vary: Accept} before the body is written. Handlers without
 * {@link JsonApiEndpoint} are ignored, so ordinary Spring MVC endpoints are unaffected.
 *
 * <p>Register via {@code WebMvcConfigurer#addInterceptors}. Annotated mappings must not declare
 * {@code consumes}/{@code produces}: those checks run in handler mapping before this interceptor
 * and would bypass JSON:API handling.
 */
public class JsonApiMediaNegotiationInterceptor implements HandlerInterceptor {

  @Override
  public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
      throws Exception {
    if (!isJsonApiHandler(handler)) {
      return true;
    }
    if (hasBody(request)
        && (request.getContentType() == null
            || JsonApiMediaNegotiation.validateContentType(request.getContentType()) != null)) {
      response.setStatus(HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE);
      return false;
    }
    String negotiated =
        JsonApiMediaNegotiation.negotiateContentType(request.getHeaders(HttpHeaders.ACCEPT));
    if (negotiated == null) {
      mergeVary(response);
      response.setStatus(HttpServletResponse.SC_NOT_ACCEPTABLE);
      return false;
    }
    return true;
  }

  @Override
  public void postHandle(
      HttpServletRequest request,
      HttpServletResponse response,
      Object handler,
      @Nullable ModelAndView modelAndView) {
    if (!isJsonApiHandler(handler)) {
      return;
    }
    mergeVary(response);
    response.setContentType(JsonApiMediaNegotiation.JSON_API_VALUE);
  }

  private static boolean isJsonApiHandler(Object handler) {
    if (!(handler instanceof HandlerMethod method)) {
      return false;
    }
    return method.getMethodAnnotation(JsonApiEndpoint.class) != null
        || method.getBeanType().isAnnotationPresent(JsonApiEndpoint.class);
  }

  /**
   * Whether the request carries a body. Requests without a body carry no {@code Content-Length} (or
   * a zero length), so there is no {@code Content-Type} to validate.
   */
  private static boolean hasBody(HttpServletRequest request) {
    return request.getContentLengthLong() > 0;
  }

  private static void mergeVary(HttpServletResponse response) {
    Collection<String> existing = response.getHeaders(HttpHeaders.VARY);
    List<String> tokens = new ArrayList<>();
    for (String value : existing) {
      for (String token : value.split(",")) {
        String trimmed = token.trim();
        if (!trimmed.isEmpty() && !tokens.contains(trimmed)) {
          tokens.add(trimmed);
        }
      }
    }
    for (String token : tokens) {
      if ("*".equals(token) || HttpHeaders.ACCEPT.equalsIgnoreCase(token)) {
        return;
      }
    }
    tokens.add(HttpHeaders.ACCEPT);
    response.setHeader(HttpHeaders.VARY, String.join(", ", tokens));
  }
}
