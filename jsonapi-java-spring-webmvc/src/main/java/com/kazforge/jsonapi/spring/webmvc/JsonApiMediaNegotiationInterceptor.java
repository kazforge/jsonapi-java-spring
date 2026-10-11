package com.kazforge.jsonapi.spring.webmvc;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Enforces JSON:API 1.1 media negotiation for {@link JsonApiEndpoint}-annotated handlers.
 *
 * <p>Validates {@code Content-Type} on requests with a body (415 on mismatch) and negotiates {@code
 * Accept} (406 when nothing acceptable remains). Handlers without {@link JsonApiEndpoint} are
 * ignored. On success the negotiated content type is stored as a request attribute for {@link
 * JsonApiMediaNegotiationAdvice}, which sets response headers before the body is written.
 *
 * <p>Register via {@code WebMvcConfigurer#addInterceptors} alongside the advice bean. Annotated
 * mappings must not declare {@code consumes}/{@code produces}: those checks run in handler mapping
 * before this interceptor and would bypass JSON:API handling.
 */
public class JsonApiMediaNegotiationInterceptor implements HandlerInterceptor {

  @Override
  public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
      throws Exception {
    if (!isJsonApiHandler(handler)) {
      return true;
    }
    if (hasBody(request)
        && JsonApiMediaNegotiation.validateContentType(request.getContentType()) != null) {
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
    request.setAttribute(JsonApiMediaNegotiation.NEGOTIATED_ATTRIBUTE, negotiated);
    return true;
  }

  private static boolean isJsonApiHandler(Object handler) {
    if (!(handler instanceof HandlerMethod method)) {
      return false;
    }
    return method.getMethodAnnotation(JsonApiEndpoint.class) != null
        || method.getBeanType().isAnnotationPresent(JsonApiEndpoint.class);
  }

  private static boolean hasBody(HttpServletRequest request) {
    if (request.getContentType() == null) {
      return false;
    }
    long length = request.getContentLengthLong();
    if (length == 0) {
      return false;
    }
    return true;
  }

  static void mergeVary(HttpServletResponse response) {
    Collection<String> existing = response.getHeaders(HttpHeaders.VARY);
    List<String> tokens = new ArrayList<>();
    for (String value : existing) {
      if (value == null) {
        continue;
      }
      for (String token : value.split(",")) {
        String trimmed = token.trim();
        if (!trimmed.isEmpty()) {
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
