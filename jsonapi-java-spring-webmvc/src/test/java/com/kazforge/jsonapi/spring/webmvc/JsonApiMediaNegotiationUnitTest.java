package com.kazforge.jsonapi.spring.webmvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Direct coverage for negotiation inputs that are impractical to produce through MockMvc: absent or
 * blank header enumerations and multiple header lines.
 */
class JsonApiMediaNegotiationUnitTest {

  private static final String JSON_API = JsonApiMediaNegotiation.JSON_API_VALUE;

  @Test
  void absentAcceptHeadersDefaultToJsonApi() {
    assertEquals(JSON_API, JsonApiMediaNegotiation.negotiateContentType(null));
  }

  @Test
  void emptyAcceptHeadersDefaultToJsonApi() {
    assertEquals(
        JSON_API, JsonApiMediaNegotiation.negotiateContentType(Collections.emptyEnumeration()));
  }

  @Test
  void blankAcceptHeaderDefaultsToJsonApi() {
    assertEquals(JSON_API, negotiate("   "));
  }

  @Test
  void multipleAcceptHeaderLinesAreCombined() {
    assertEquals(JSON_API, negotiate("application/json", JSON_API));
  }

  @Test
  void highestQualityAcceptableAlternativeWins() {
    assertEquals(JSON_API, negotiate(JSON_API + ";q=0.2", "application/json;q=0.9"));
  }

  @Test
  void equalSpecificityQualityBreaksTie() {
    assertEquals(JSON_API, negotiate("application/*;q=0.5, application/*;q=0.9"));
  }

  @Test
  void explicitExclusionBeatsWildcardQuality() {
    assertNull(negotiate(JSON_API + ";q=0, */*;q=1"));
  }

  @Test
  void suffixWildcardDoesNotMatch() {
    assertNull(negotiate("application/*+xml"));
  }

  @Test
  void blankContentTypeValueIsValid() {
    assertEquals(
        JsonApiMediaNegotiation.Validity.VALID, JsonApiMediaNegotiation.checkContentType("   "));
  }

  @Test
  void nonJsonApiContentTypeIsUnsupported() {
    assertEquals(
        JsonApiMediaNegotiation.Validity.UNSUPPORTED,
        JsonApiMediaNegotiation.checkContentType("application/json"));
  }

  @Test
  void jsonApiContentTypeWithDisallowedParameterIsUnsupported() {
    assertEquals(
        JsonApiMediaNegotiation.Validity.UNSUPPORTED,
        JsonApiMediaNegotiation.checkContentType(JSON_API + ";charset=\"utf-8\""));
  }

  @Test
  void jsonApiContentTypeWithUnsupportedExtensionIsUnsupported() {
    assertEquals(
        JsonApiMediaNegotiation.Validity.UNSUPPORTED,
        JsonApiMediaNegotiation.checkContentType(
            JSON_API + ";ext=\"https://example.com/ext/atomic\""));
  }

  @Test
  void jsonApiContentTypeWithUnknownProfileIsValid() {
    assertEquals(
        JsonApiMediaNegotiation.Validity.VALID,
        JsonApiMediaNegotiation.checkContentType(
            JSON_API + ";profile=\"https://example.com/profiles/timestamps\""));
  }

  private static String negotiate(String... headerValues) {
    Enumeration<String> headers = Collections.enumeration(List.of(headerValues));
    return JsonApiMediaNegotiation.negotiateContentType(headers);
  }
}
