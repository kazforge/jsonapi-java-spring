package com.kazforge.jsonapi.spring.webmvc.media;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * Direct coverage for negotiation inputs that are impractical to produce through MockMvc: absent or
 * blank header enumerations, multiple header lines and invalid alternatives.
 */
class JsonApiMediaNegotiationUnitTest {

  private static final String JSON_API = JsonApiMediaNegotiation.JSON_API_VALUE;

  @Test
  void absentAcceptHeadersDefaultToJsonApi() {
    assertEquals(JSON_API, negotiate());
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
  void malformedAlternativeDoesNotDiscardValidAlternative() {
    assertEquals(JSON_API, negotiate("not-a-media-type!!!, " + JSON_API));
    assertEquals(JSON_API, negotiate(JSON_API + ", not-a-media-type!!!"));
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
  void blankContentTypeValueIsInvalid() {
    assertFalse(JsonApiMediaNegotiation.isValidContentType("   "));
  }

  @Test
  void nonJsonApiContentTypeIsInvalid() {
    assertFalse(JsonApiMediaNegotiation.isValidContentType("application/json"));
  }

  @Test
  void jsonApiContentTypeWithDisallowedParameterIsInvalid() {
    assertFalse(JsonApiMediaNegotiation.isValidContentType(JSON_API + ";charset=\"utf-8\""));
  }

  @Test
  void jsonApiContentTypeWithUnsupportedExtensionIsInvalid() {
    assertFalse(
        JsonApiMediaNegotiation.isValidContentType(
            JSON_API + ";ext=\"https://example.com/ext/atomic\""));
  }

  @Test
  void jsonApiContentTypeWithUnknownProfileIsValid() {
    assertTrue(
        JsonApiMediaNegotiation.isValidContentType(
            JSON_API + ";profile=\"https://example.com/profiles/timestamps\""));
  }

  @Test
  void compositeTransferEncodingIsABody() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("Transfer-Encoding", "gzip, chunked");
    assertTrue(JsonApiMediaNegotiationInterceptor.hasBody(request));
  }

  @Test
  void positiveContentLengthIsABody() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setContent(new byte[] {1});
    assertTrue(JsonApiMediaNegotiationInterceptor.hasBody(request));
  }

  @Test
  void requestWithoutBodyHasNoBody() {
    assertFalse(JsonApiMediaNegotiationInterceptor.hasBody(new MockHttpServletRequest()));
  }

  private static String negotiate(String... headerValues) {
    Enumeration<String> headers = Collections.enumeration(List.of(headerValues));
    return JsonApiMediaNegotiation.negotiateContentType(headers);
  }
}
