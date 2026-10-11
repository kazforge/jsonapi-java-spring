package com.kazforge.jsonapi.spring.webmvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Direct coverage for negotiation inputs that are impractical to produce through MockMvc: absent or
 * blank header enumerations, multiple header lines, and quoted parameter values containing commas.
 */
class JsonApiMediaNegotiationUnitTest {

  private static final String JSON_API = JsonApiMediaNegotiation.JSON_API_VALUE;

  @Test
  void blankContentTypeValueIsValid() {
    assertNull(JsonApiMediaNegotiation.validateContentType("   "));
  }

  @Test
  void nonJsonApiContentTypeIsUnsupported() {
    assertEquals("unsupported", JsonApiMediaNegotiation.validateContentType("application/json"));
  }

  @Test
  void jsonApiContentTypeWithDisallowedParameterIsUnsupported() {
    assertEquals(
        "unsupported",
        JsonApiMediaNegotiation.validateContentType(JSON_API + ";charset=\"utf-8\""));
  }

  @Test
  void jsonApiContentTypeWithUnknownProfileIsValid() {
    assertNull(
        JsonApiMediaNegotiation.validateContentType(
            JSON_API + ";profile=\"https://example.com/profiles/timestamps\""));
  }

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
  void commaInsideQuotedExtensionParameterDoesNotSplit() {
    assertEquals(
        JSON_API,
        negotiate(JSON_API + ";ext=\"https://example.com/a,https://example.com/b\", " + JSON_API));
  }

  @Test
  void commaInsideQuotedProfileParameterDoesNotSplit() {
    assertEquals(
        JSON_API, negotiate(JSON_API + ";profile=\"https://example.com/a,https://example.com/b\""));
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
  void explicitExclusionBeatsWildcardQuality() {
    assertNull(negotiate(JSON_API + ";q=0, */*;q=1"));
  }

  @Test
  void mostSpecificRangeDecidesQuality() {
    assertNull(negotiate("application/*;q=1, " + JSON_API + ";q=0"));
  }

  private static String negotiate(String... headerValues) {
    Enumeration<String> headers = Collections.enumeration(List.of(headerValues));
    return JsonApiMediaNegotiation.negotiateContentType(headers);
  }
}
