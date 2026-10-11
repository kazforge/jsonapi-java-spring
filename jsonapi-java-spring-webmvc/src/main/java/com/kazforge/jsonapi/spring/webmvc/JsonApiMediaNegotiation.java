package com.kazforge.jsonapi.spring.webmvc;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.http.MediaType;

/**
 * JSON:API 1.1 media-type negotiation rules used by {@link JsonApiMediaNegotiationInterceptor}.
 *
 * <p>Package-private and deliberately small: this slice supports no extensions, so any requested
 * {@code ext} URI is unsupported, while unknown {@code profile} URIs are ignored. Accept ranking
 * follows RFC 9110 precedence: the most specific viable media range decides whether the JSON:API
 * media type is acceptable, including {@code q=0} exclusions.
 */
final class JsonApiMediaNegotiation {

  static final String JSON_API_VALUE = "application/vnd.api+json";

  static final MediaType JSON_API = MediaType.parseMediaType(JSON_API_VALUE);

  private static final String UNSUPPORTED = "unsupported";

  private JsonApiMediaNegotiation() {}

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
      return UNSUPPORTED;
    }
    if (!"application".equalsIgnoreCase(contentType.getType())
        || !JSON_API.getSubtype().equalsIgnoreCase(contentType.getSubtype())) {
      return UNSUPPORTED;
    }
    for (String name : contentType.getParameters().keySet()) {
      if (!"ext".equalsIgnoreCase(name) && !"profile".equalsIgnoreCase(name)) {
        return UNSUPPORTED;
      }
    }
    if (hasUnsupportedExtension(contentType.getParameter("ext"))) {
      return UNSUPPORTED;
    }
    return null;
  }

  /**
   * Negotiates the response {@code Content-Type} from {@code Accept} header values. Returns {@code
   * null} when no acceptable representation remains and the server must respond 406.
   *
   * <p>Invalid alternatives are ignored; unknown profiles are ignored. A JSON:API alternative
   * modified by a parameter other than {@code ext}, {@code profile} or {@code q} is not a JSON:API
   * representation and lets less specific ranges decide. Quality values apply, so an explicit
   * {@code q=0} JSON:API range makes the response unacceptable even when a wildcard range is also
   * present.
   */
  static @Nullable String negotiateContentType(@Nullable Enumeration<String> acceptHeaders) {
    List<String> rawValues = acceptHeaderValues(acceptHeaders);
    if (rawValues.stream().allMatch(String::isBlank)) {
      return JSON_API_VALUE;
    }
    return isAcceptable(parseMediaRanges(rawValues)) ? JSON_API_VALUE : null;
  }

  private static List<String> acceptHeaderValues(@Nullable Enumeration<String> acceptHeaders) {
    List<String> rawValues = new ArrayList<>();
    if (acceptHeaders != null) {
      while (acceptHeaders.hasMoreElements()) {
        rawValues.add(acceptHeaders.nextElement());
      }
    }
    return rawValues;
  }

  private static List<MediaType> parseMediaRanges(List<String> rawValues) {
    List<MediaType> ranges = new ArrayList<>();
    for (String rawValue : rawValues) {
      for (String token : splitOutsideQuotes(rawValue)) {
        try {
          ranges.add(MediaType.parseMediaType(token));
        } catch (IllegalArgumentException ex) {
          // Malformed alternatives are ignored, per JSON:API 1.1 and the approved plan.
        }
      }
    }
    return ranges;
  }

  /**
   * Splits an Accept header value on commas that separate media ranges, preserving commas inside
   * quoted parameter values such as {@code ext="a,b"}.
   */
  private static List<String> splitOutsideQuotes(String headerValue) {
    List<String> tokens = new ArrayList<>();
    StringBuilder token = new StringBuilder();
    boolean inQuotes = false;
    for (int index = 0; index < headerValue.length(); index++) {
      char current = headerValue.charAt(index);
      if (current == '"') {
        inQuotes = !inQuotes;
      }
      if (current == ',' && !inQuotes) {
        addToken(tokens, token);
        token.setLength(0);
      } else {
        token.append(current);
      }
    }
    addToken(tokens, token);
    return tokens;
  }

  private static void addToken(List<String> tokens, StringBuilder token) {
    String trimmed = token.toString().trim();
    if (!trimmed.isEmpty()) {
      tokens.add(trimmed);
    }
  }

  /**
   * Applies RFC 9110 precedence: the most specific viable media range matching the JSON:API media
   * type determines whether it is acceptable. A JSON:API range carrying other parameters, or only
   * unsupported extensions, is not a viable JSON:API representation, so it steps aside for wildcard
   * ranges, which never carry JSON:API parameters.
   */
  private static boolean isAcceptable(List<MediaType> ranges) {
    List<MediaType> viable = new ArrayList<>();
    for (MediaType range : ranges) {
      if (matchesJsonApi(range)) {
        viable.add(range);
      }
    }
    if (viable.isEmpty()) {
      return false;
    }
    MediaType mostSpecific = viable.get(0);
    for (MediaType range : viable) {
      if (isMoreSpecific(range, mostSpecific)) {
        mostSpecific = range;
      }
    }
    return mostSpecific.getQualityValue() > 0.0;
  }

  /** Whether the range can select the JSON:API media type, ignoring its quality value. */
  private static boolean matchesJsonApi(MediaType range) {
    if ("application".equalsIgnoreCase(range.getType())
        && !range.isWildcardSubtype()
        && JSON_API.getSubtype().equalsIgnoreCase(range.getSubtype())) {
      return !hasDisallowedParameter(range) && !hasUnsupportedExtension(range.getParameter("ext"));
    }
    return range.isWildcardType()
        || ("application".equalsIgnoreCase(range.getType()) && range.isWildcardSubtype());
  }

  /**
   * RFC 9110 precedence: a fully specified type beats any wildcard range; among ranges of equal
   * type/subtype specificity, more specified parameters and then a higher quality value win.
   */
  private static boolean isMoreSpecific(MediaType candidate, MediaType current) {
    int candidateSpecificity = specificity(candidate);
    int currentSpecificity = specificity(current);
    if (candidateSpecificity != currentSpecificity) {
      return candidateSpecificity > currentSpecificity;
    }
    if (candidate.getParameters().size() != current.getParameters().size()) {
      return candidate.getParameters().size() > current.getParameters().size();
    }
    return candidate.getQualityValue() > current.getQualityValue();
  }

  private static int specificity(MediaType range) {
    if (range.isWildcardType()) {
      return 1;
    }
    if (range.isWildcardSubtype()) {
      return 2;
    }
    return 3;
  }

  private static boolean hasDisallowedParameter(MediaType range) {
    for (String name : range.getParameters().keySet()) {
      if (!"ext".equalsIgnoreCase(name)
          && !"profile".equalsIgnoreCase(name)
          && !"q".equalsIgnoreCase(name)) {
        return true;
      }
    }
    return false;
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
