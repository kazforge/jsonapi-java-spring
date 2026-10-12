package com.kazforge.jsonapi.spring.webmvc.media;

import com.kazforge.jsonapi.spring.webmvc.JsonApiEndpoint;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Enforces JSON:API 1.1 media negotiation for {@link JsonApiEndpoint}-annotated handlers.
 *
 * <p>Validates the request {@code Content-Type} (415 when a body has no valid JSON:API media type)
 * and negotiates {@code Accept} (406 when no acceptable representation remains). Handlers without
 * {@link JsonApiEndpoint} are ignored, so ordinary Spring MVC endpoints are unaffected. {@link
 * JsonApiMediaNegotiationAdvice} applies the negotiated response media type.
 *
 * <p>Consumers register this interceptor in {@code WebMvcConfigurer#addInterceptors} alongside the
 * {@link JsonApiMediaNegotiationAdvice} bean. Annotated mappings must not declare {@code consumes}
 * or {@code produces}: handler mapping applies those before this interceptor and would respond with
 * its own 415/406 instead of JSON:API negotiation.
 */
public class JsonApiMediaNegotiationInterceptor implements HandlerInterceptor {

  @Override
  public boolean preHandle(
      HttpServletRequest request, HttpServletResponse response, Object handler) {
    if (!isJsonApiHandler(handler)) {
      return true;
    }
    if (hasBody(request) && !hasValidContentType(request)) {
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
    // Committing the negotiated type makes message conversion use it directly, so a malformed
    // alternative elsewhere in Accept cannot fail content negotiation downstream.
    response.setContentType(negotiated);
    mergeVary(response);
    return true;
  }

  static boolean isJsonApiHandler(Object handler) {
    if (!(handler instanceof HandlerMethod method)) {
      return false;
    }
    return method.getMethodAnnotation(JsonApiEndpoint.class) != null
        || method.getBeanType().isAnnotationPresent(JsonApiEndpoint.class);
  }

  /**
   * Whether the request carries a body. A positive {@code Content-Length} or any {@code
   * Transfer-Encoding} (including composite values such as {@code gzip, chunked}) indicates a body;
   * a body with neither, such as raw HTTP/2 DATA frames, is not detected here.
   */
  static boolean hasBody(HttpServletRequest request) {
    return request.getContentLengthLong() > 0
        || StringUtils.hasText(request.getHeader(HttpHeaders.TRANSFER_ENCODING));
  }

  private static boolean hasValidContentType(HttpServletRequest request) {
    String contentType = request.getContentType();
    return contentType != null && JsonApiMediaNegotiation.isValidContentType(contentType);
  }

  /** Adds {@code Accept} to {@code Vary} unless a broader value is already present. */
  static void mergeVary(HttpHeaders headers) {
    headers.put(HttpHeaders.VARY, withAccept(headers.getOrDefault(HttpHeaders.VARY, List.of())));
  }

  /** Adds {@code Accept} to the servlet response {@code Vary} header. */
  static void mergeVary(HttpServletResponse response) {
    response.setHeader(
        HttpHeaders.VARY, String.join(", ", withAccept(response.getHeaders(HttpHeaders.VARY))));
  }

  private static List<String> withAccept(Collection<String> varyValues) {
    List<String> vary = new ArrayList<>(varyValues);
    for (String value : vary) {
      for (String token : value.split(",")) {
        String trimmed = token.trim();
        if ("*".equals(trimmed) || HttpHeaders.ACCEPT.equalsIgnoreCase(trimmed)) {
          return vary;
        }
      }
    }
    vary.add(HttpHeaders.ACCEPT);
    return vary;
  }
}
