package com.kazforge.jsonapi.spring.webmvc;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.http.MediaType;

/**
 * JSON:API 1.1 media-type negotiation rules shared by the interceptor and response advice.
 *
 * <p>Package-private. Not a generic negotiation framework and not an extension registry: this slice
 * supports no extensions, so any requested {@code ext} URI is unsupported, while unknown {@code
 * profile} URIs are ignored.
 */
final class JsonApiMediaNegotiation {

  static final String JSON_API_VALUE = "application/vnd.api+json";

  static final MediaType JSON_API = MediaType.parseMediaType(JSON_API_VALUE);

  static final String NEGOTIATED_ATTRIBUTE =
      JsonApiMediaNegotiation.class.getName() + ".negotiatedContentType";

  private JsonApiMediaNegotiation() {}

  static boolean isJsonApi(@Nullable MediaType candidate) {
    return candidate != null
        && "application".equalsIgnoreCase(candidate.getType())
        && "vnd.api+json".equalsIgnoreCase(candidate.getSubtype());
  }

  /**
   * Validates a {@code Content-Type} value for a request carrying a body. Returns {@code null} when
   * valid, {@code "unsupported"} when the request must fail with 415.
   */
  static @Nullable String validateContentType(@Nullable String contentTypeValue) {
    if (contentTypeValue == null || contentTypeValue.isBlank()) {
      return null;
    }
    final MediaType contentType;
    try {
      contentType = MediaType.parseMediaType(contentTypeValue);
    } catch (IllegalArgumentException ex) {
      return "unsupported";
    }
    if (!isJsonApi(contentType)) {
      return "unsupported";
    }
    for (String name : contentType.getParameters().keySet()) {
      if (!"ext".equalsIgnoreCase(name) && !"profile".equalsIgnoreCase(name)) {
        return "unsupported";
      }
    }
    if (hasUnsupportedExtension(contentType.getParameter("ext"))) {
      return "unsupported";
    }
    return null;
  }

  /**
   * Negotiates the response {@code Content-Type} from {@code Accept} header values. Returns {@code
   * null} when no acceptable representation remains and the server must respond 406.
   *
   * <p>Invalid or unsupported alternatives are skipped. Quality values apply, including {@code q=0}
   * meaning "not acceptable". Unknown profiles are ignored.
   */
  static @Nullable String negotiateContentType(@Nullable Enumeration<String> acceptHeaders) {
    if (acceptHeaders == null || !acceptHeaders.hasMoreElements()) {
      return JSON_API_VALUE;
    }
    List<String> rawValues = new ArrayList<>();
    while (acceptHeaders.hasMoreElements()) {
      rawValues.add(acceptHeaders.nextElement());
    }
    boolean sawAnyValue = false;
    List<MediaType> candidates = new ArrayList<>();
    for (String rawValue : rawValues) {
      if (rawValue == null || rawValue.isBlank()) {
        continue;
      }
      sawAnyValue = true;
      for (String token : rawValue.split(",")) {
        String trimmed = token.trim();
        if (trimmed.isEmpty()) {
          continue;
        }
        try {
          candidates.add(MediaType.parseMediaType(trimmed));
        } catch (IllegalArgumentException ex) {
          // Ignore invalid alternatives per the approved plan; keep scanning.
        }
      }
    }
    if (!sawAnyValue) {
      return JSON_API_VALUE;
    }
    if (candidates.isEmpty()) {
      return null;
    }
    // Most preferred first: quality wins, then specificity. Sort is stable, so equally
    // preferred alternatives keep their header order.
    candidates.sort(
        (left, right) -> {
          int quality = Double.compare(right.getQualityValue(), left.getQualityValue());
          if (quality != 0) {
            return quality;
          }
          if (left.isMoreSpecific(right)) {
            return -1;
          }
          if (left.isLessSpecific(right)) {
            return 1;
          }
          return 0;
        });
    for (MediaType candidate : candidates) {
      if (candidate.getQualityValue() == 0.0) {
        continue;
      }
      if (isJsonApi(candidate)) {
        if (hasDisallowedParameter(candidate)) {
          continue;
        }
        if (hasUnsupportedExtension(candidate.getParameter("ext"))) {
          continue;
        }
        return JSON_API_VALUE;
      }
      if (isWildcard(candidate)) {
        return JSON_API_VALUE;
      }
    }
    return null;
  }

  private static boolean hasDisallowedParameter(MediaType candidate) {
    for (String name : candidate.getParameters().keySet()) {
      if (!"ext".equalsIgnoreCase(name)
          && !"profile".equalsIgnoreCase(name)
          && !"q".equalsIgnoreCase(name)) {
        return true;
      }
    }
    return false;
  }

  private static boolean isWildcard(MediaType candidate) {
    return candidate.isWildcardType() || candidate.isWildcardSubtype();
  }

  private static boolean hasUnsupportedExtension(@Nullable String extValue) {
    if (extValue == null || extValue.isBlank()) {
      return false;
    }
    // No extensions are supported in this slice, so any listed URI is unsupported.
    String unquoted = extValue.trim();
    if (unquoted.length() >= 2 && unquoted.startsWith("\"") && unquoted.endsWith("\"")) {
      unquoted = unquoted.substring(1, unquoted.length() - 1);
    }
    return !unquoted.isBlank();
  }
}
