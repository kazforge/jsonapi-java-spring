package com.kazforge.jsonapi.spring.webmvc;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.http.MediaType;
import org.springframework.util.MimeTypeUtils;
import org.springframework.util.StringUtils;

/**
 * JSON:API 1.1 media-type rules used by {@link JsonApiMediaNegotiationInterceptor}.
 *
 * <p>No extensions are supported, so any requested {@code ext} URI is unsupported, while unknown
 * {@code profile} URIs are ignored.
 */
final class JsonApiMediaNegotiation {

  static final String JSON_API_VALUE = "application/vnd.api+json";

  static final MediaType JSON_API = MediaType.parseMediaType(JSON_API_VALUE);

  private static final String EXT = "ext";

  private static final String PROFILE = "profile";

  private static final String QUALITY = "q";

  private JsonApiMediaNegotiation() {}

  /** Whether a request body's JSON:API {@code Content-Type} value is acceptable. */
  static boolean isValidContentType(String contentTypeValue) {
    if (contentTypeValue.isBlank()) {
      return false;
    }
    MediaType contentType;
    try {
      contentType = MediaType.parseMediaType(contentTypeValue);
    } catch (IllegalArgumentException ex) {
      return false;
    }
    if (!JSON_API.includes(contentType.removeQualityValue())) {
      return false;
    }
    for (String name : contentType.getParameters().keySet()) {
      if (!EXT.equalsIgnoreCase(name) && !PROFILE.equalsIgnoreCase(name)) {
        return false;
      }
    }
    return !hasUnsupportedExtension(contentType.getParameter(EXT));
  }

  /**
   * Returns the response media type when the request accepts a JSON:API representation, or {@code
   * null} when the server must respond 406. Accept ranking follows RFC 9110 precedence: the most
   * specific matching range decides, and quality values break ties.
   */
  static @Nullable String negotiateContentType(Enumeration<String> acceptHeaders) {
    List<String> rawValues = new ArrayList<>();
    while (acceptHeaders.hasMoreElements()) {
      rawValues.add(acceptHeaders.nextElement());
    }
    if (rawValues.stream().noneMatch(StringUtils::hasText)) {
      return JSON_API_VALUE;
    }
    List<MediaType> matching = new ArrayList<>();
    for (String rawValue : rawValues) {
      for (MediaType range : parseAcceptRange(rawValue)) {
        if (matchesJsonApi(range)) {
          matching.add(range);
        }
      }
    }
    MediaType mostSpecific = mostPreferred(matching);
    return mostSpecific != null && mostSpecific.getQualityValue() > 0.0 ? JSON_API_VALUE : null;
  }

  /**
   * Parses one Accept header value. Spring tokenizes alternatives quote-aware; each alternative is
   * parsed on its own so a single malformed alternative never discards valid alternatives in the
   * same header.
   */
  private static List<MediaType> parseAcceptRange(String rawValue) {
    List<MediaType> ranges = new ArrayList<>();
    for (String token : MimeTypeUtils.tokenize(rawValue)) {
      try {
        ranges.add(MediaType.parseMediaType(token));
      } catch (IllegalArgumentException ex) {
        // A malformed alternative is ignored, per JSON:API and general HTTP client behavior.
      }
    }
    return ranges;
  }

  /**
   * Whether the range can select the JSON:API media type. Spring's {@code includes} covers wildcard
   * ranges and suffix patterns such as {@code application/*+xml}; JSON:API parameters are validated
   * separately and never restrict the match.
   */
  private static boolean matchesJsonApi(MediaType range) {
    return range.includes(JSON_API)
        && !hasDisallowedParameter(range)
        && !hasUnsupportedExtension(range.getParameter(EXT));
  }

  private static @Nullable MediaType mostPreferred(List<MediaType> ranges) {
    MediaType preferred = null;
    for (MediaType range : ranges) {
      if (preferred == null || isPreferred(range, preferred)) {
        preferred = range;
      }
    }
    return preferred;
  }

  /**
   * RFC 9110 precedence: specificity first, then quality, ignoring the quality parameter itself.
   */
  private static boolean isPreferred(MediaType candidate, MediaType current) {
    MediaType strippedCandidate = candidate.removeQualityValue();
    MediaType strippedCurrent = current.removeQualityValue();
    if (strippedCandidate.isMoreSpecific(strippedCurrent)) {
      return true;
    }
    if (strippedCandidate.isLessSpecific(strippedCurrent)) {
      return false;
    }
    return candidate.getQualityValue() > current.getQualityValue();
  }

  private static boolean hasDisallowedParameter(MediaType range) {
    for (String name : range.getParameters().keySet()) {
      if (!EXT.equalsIgnoreCase(name)
          && !PROFILE.equalsIgnoreCase(name)
          && !QUALITY.equalsIgnoreCase(name)) {
        return true;
      }
    }
    return false;
  }

  private static boolean hasUnsupportedExtension(@Nullable String extValue) {
    // No extensions are supported, so any listed URI is unsupported.
    return extValue != null && !extValue.isBlank();
  }
}
