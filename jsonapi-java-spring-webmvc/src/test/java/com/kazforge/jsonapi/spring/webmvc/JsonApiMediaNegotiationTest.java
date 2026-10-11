package com.kazforge.jsonapi.spring.webmvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * MockMvc regression tests for JSON:API media negotiation across the real MVC lifecycle: handler
 * mapping, the interceptor, message conversion, and response header handling.
 */
class JsonApiMediaNegotiationTest {

  private static final String JSON_API = "application/vnd.api+json";
  private static final String EXT = "https://example.com/ext/atomic";
  private static final String TOKEN_EXT = "https://example.com/ext/tokens";
  private static final String PROFILE = "https://example.com/profiles/timestamps";

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(
                new JsonApiController(),
                new MixedController(),
                new PlainController(),
                new VaryController())
            .addInterceptors(new JsonApiMediaNegotiationInterceptor())
            .build();
  }

  @Test
  void getWithoutAcceptDefaultsToJsonApi() throws Exception {
    mockMvc
        .perform(get("/api/articles"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(JSON_API))
        .andExpect(header().string(HttpHeaders.VARY, HttpHeaders.ACCEPT));
  }

  @Test
  void getWithJsonApiAcceptSucceeds() throws Exception {
    mockMvc
        .perform(get("/api/articles").header(HttpHeaders.ACCEPT, JSON_API))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(JSON_API));
  }

  @Test
  void getResponseBodyIsWritten() throws Exception {
    mockMvc
        .perform(get("/api/articles").header(HttpHeaders.ACCEPT, JSON_API))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(JSON_API))
        .andExpect(content().string("{\"data\":[]}"));
  }

  @Test
  void getWithWildcardAcceptSucceeds() throws Exception {
    mockMvc
        .perform(get("/api/articles").header(HttpHeaders.ACCEPT, "*/*"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(JSON_API));
  }

  @Test
  void getWithApplicationWildcardSucceeds() throws Exception {
    mockMvc
        .perform(get("/api/articles").header(HttpHeaders.ACCEPT, "application/*"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(JSON_API));
  }

  @Test
  void getWithTextWildcardIsNotAcceptable() throws Exception {
    mockMvc
        .perform(get("/api/articles").header(HttpHeaders.ACCEPT, "text/*"))
        .andExpect(status().isNotAcceptable());
  }

  @Test
  void getWithBlankAcceptDefaultsToJsonApi() throws Exception {
    mockMvc
        .perform(get("/api/articles").header(HttpHeaders.ACCEPT, ""))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(JSON_API));
  }

  @Test
  void getIgnoresEmptyAcceptAlternative() throws Exception {
    mockMvc
        .perform(get("/api/articles").header(HttpHeaders.ACCEPT, JSON_API + ","))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(JSON_API));
  }

  @Test
  void getWithJsonOnlyAcceptIsNotAcceptable() throws Exception {
    mockMvc
        .perform(get("/api/articles").header(HttpHeaders.ACCEPT, "application/json"))
        .andExpect(status().isNotAcceptable());
  }

  @Test
  void notAcceptableMergesVary() throws Exception {
    mockMvc
        .perform(get("/api/articles").header(HttpHeaders.ACCEPT, "application/json"))
        .andExpect(status().isNotAcceptable())
        .andExpect(header().string(HttpHeaders.VARY, HttpHeaders.ACCEPT));
  }

  @Test
  void getIgnoresInvalidAlternativeWhenValidRemains() throws Exception {
    mockMvc
        .perform(get("/api/articles").header(HttpHeaders.ACCEPT, "application/json, " + JSON_API))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(JSON_API));
  }

  @Test
  void getWithOnlyInvalidAcceptIsNotAcceptable() throws Exception {
    mockMvc
        .perform(get("/api/articles").header(HttpHeaders.ACCEPT, "not-a-media-type!!!"))
        .andExpect(status().isNotAcceptable());
  }

  @Test
  void getIgnoresJsonApiWithDisallowedParameterWhenAlternativeRemains() throws Exception {
    mockMvc
        .perform(
            get("/api/articles")
                .header(HttpHeaders.ACCEPT, JSON_API + ";charset=\"utf-8\", " + JSON_API))
        .andExpect(status().isOk());
  }

  @Test
  void getWithOnlyDisallowedParameterIsNotAcceptable() throws Exception {
    mockMvc
        .perform(get("/api/articles").header(HttpHeaders.ACCEPT, JSON_API + ";charset=\"utf-8\""))
        .andExpect(status().isNotAcceptable());
  }

  @Test
  void getIgnoresUnsupportedExtensionWhenAlternativeRemains() throws Exception {
    mockMvc
        .perform(
            get("/api/articles")
                .header(HttpHeaders.ACCEPT, JSON_API + ";ext=\"" + EXT + "\", " + JSON_API))
        .andExpect(status().isOk());
  }

  @Test
  void getWithOnlyUnsupportedExtensionIsNotAcceptable() throws Exception {
    mockMvc
        .perform(get("/api/articles").header(HttpHeaders.ACCEPT, JSON_API + ";ext=\"" + EXT + "\""))
        .andExpect(status().isNotAcceptable());
  }

  @Test
  void getWithUnsupportedExtensionFallsBackToWildcard() throws Exception {
    mockMvc
        .perform(
            get("/api/articles")
                .header(HttpHeaders.ACCEPT, JSON_API + ";ext=\"" + EXT + "\";q=1, */*;q=0.5"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(JSON_API));
  }

  @Test
  void getIgnoresUnknownProfile() throws Exception {
    mockMvc
        .perform(
            get("/api/articles")
                .header(HttpHeaders.ACCEPT, JSON_API + ";profile=\"" + PROFILE + "\""))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(JSON_API));
  }

  @Test
  void getPreservesQuotedCommaInsideExtensionParameter() throws Exception {
    mockMvc
        .perform(
            get("/api/articles")
                .header(
                    HttpHeaders.ACCEPT,
                    JSON_API + ";ext=\" " + TOKEN_EXT + "," + TOKEN_EXT + "\", " + JSON_API))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(JSON_API));
  }

  @Test
  void getRespectsQualityPreference() throws Exception {
    mockMvc
        .perform(
            get("/api/articles")
                .header(HttpHeaders.ACCEPT, "application/json, " + JSON_API + ";q=0.9"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(JSON_API));
  }

  @Test
  void getWithZeroQualityIsNotAcceptable() throws Exception {
    mockMvc
        .perform(get("/api/articles").header(HttpHeaders.ACCEPT, JSON_API + ";q=0"))
        .andExpect(status().isNotAcceptable());
  }

  @Test
  void explicitZeroQualityIsNotOverriddenByWildcard() throws Exception {
    mockMvc
        .perform(get("/api/articles").header(HttpHeaders.ACCEPT, JSON_API + ";q=0, */*;q=1"))
        .andExpect(status().isNotAcceptable());
  }

  @Test
  void leastSpecificWildcardLosesToParameterizedRange() throws Exception {
    mockMvc
        .perform(
            get("/api/articles")
                .header(HttpHeaders.ACCEPT, "*/*;q=0.5, " + JSON_API + ";profile=\"x\";q=0.9"))
        .andExpect(status().isOk());
  }

  @Test
  void getFallsBackToWildcardWhenJsonApiUnacceptable() throws Exception {
    mockMvc
        .perform(
            get("/api/articles")
                .header(HttpHeaders.ACCEPT, JSON_API + ";charset=\"utf-8\";q=0.9, */*;q=0.5"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(JSON_API));
  }

  @Test
  void specificRangeOverridesWildcardQuality() throws Exception {
    mockMvc
        .perform(
            get("/api/articles")
                .header(HttpHeaders.ACCEPT, "application/*;q=0.5, " + JSON_API + ";q=0.9"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(JSON_API));
  }

  @Test
  void getPrefersSpecificTypeOverWildcard() throws Exception {
    mockMvc
        .perform(get("/api/articles").header(HttpHeaders.ACCEPT, "application/*, " + JSON_API))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(JSON_API));
  }

  @Test
  void postWithValidContentTypeSucceeds() throws Exception {
    mockMvc
        .perform(
            post("/api/articles")
                .contentType(MediaType.parseMediaType(JSON_API))
                .content("{\"data\":[]}")
                .header(HttpHeaders.ACCEPT, JSON_API))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(JSON_API));
  }

  @Test
  void postWithoutContentTypeIsUnsupported() throws Exception {
    mockMvc
        .perform(
            post("/api/articles").content("{\"data\":[]}").header(HttpHeaders.ACCEPT, JSON_API))
        .andExpect(status().isUnsupportedMediaType());
  }

  @Test
  void postWithoutBodySkipsContentTypeValidation() throws Exception {
    mockMvc
        .perform(
            post("/api/articles")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.ACCEPT, JSON_API))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(JSON_API));
  }

  @Test
  void postWithJsonContentTypeIsUnsupported() throws Exception {
    mockMvc
        .perform(
            post("/api/articles").contentType(MediaType.APPLICATION_JSON).content("{\"data\":[]}"))
        .andExpect(status().isUnsupportedMediaType());
  }

  @Test
  void postWithDisallowedContentTypeParameterIsUnsupported() throws Exception {
    mockMvc
        .perform(
            post("/api/articles")
                .header(HttpHeaders.CONTENT_TYPE, JSON_API + ";charset=\"utf-8\"")
                .content("{\"data\":[]}"))
        .andExpect(status().isUnsupportedMediaType());
  }

  @Test
  void postWithUnsupportedExtensionIsUnsupported() throws Exception {
    mockMvc
        .perform(
            post("/api/articles")
                .header(HttpHeaders.CONTENT_TYPE, JSON_API + ";ext=\"" + EXT + "\"")
                .content("{\"data\":[]}"))
        .andExpect(status().isUnsupportedMediaType());
  }

  @Test
  void postWithUnknownProfileSucceeds() throws Exception {
    mockMvc
        .perform(
            post("/api/articles")
                .header(HttpHeaders.CONTENT_TYPE, JSON_API + ";profile=\"" + PROFILE + "\"")
                .content("{\"data\":[]}")
                .header(HttpHeaders.ACCEPT, JSON_API))
        .andExpect(status().isOk());
  }

  @Test
  void postWithInvalidContentTypeIsUnsupported() throws Exception {
    mockMvc
        .perform(
            post("/api/articles")
                .header(HttpHeaders.CONTENT_TYPE, "not-a-media-type!!!")
                .content("{\"data\":[]}"))
        .andExpect(status().isUnsupportedMediaType());
  }

  @Test
  void methodLevelAnnotationIsNegotiated() throws Exception {
    mockMvc
        .perform(get("/mixed/resource").header(HttpHeaders.ACCEPT, JSON_API))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(JSON_API));
  }

  @Test
  void ordinaryEndpointIgnoresJsonApiNegotiation() throws Exception {
    mockMvc
        .perform(get("/plain/resource").header(HttpHeaders.ACCEPT, "application/json"))
        .andExpect(status().isOk())
        .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/json"));
  }

  @Test
  void ordinaryEndpointIgnoresUnacceptableJsonApiAccept() throws Exception {
    mockMvc
        .perform(get("/plain/resource").header(HttpHeaders.ACCEPT, JSON_API + ";q=0, */*"))
        .andExpect(status().isOk())
        .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/json"));
  }

  @Test
  void ordinaryMethodOnMixedControllerIsUnaffected() throws Exception {
    mockMvc
        .perform(get("/mixed/plain").header(HttpHeaders.ACCEPT, "application/json"))
        .andExpect(status().isOk())
        .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/json"));
  }

  @Test
  void jsonApiEndpointRejectsOrdinaryAccept() throws Exception {
    mockMvc
        .perform(get("/mixed/resource").header(HttpHeaders.ACCEPT, "application/json"))
        .andExpect(status().isNotAcceptable());
  }

  @Test
  void existingVaryIsPreserved() throws Exception {
    mockMvc
        .perform(get("/api/vary").header(HttpHeaders.ACCEPT, JSON_API))
        .andExpect(status().isOk())
        .andExpect(header().string(HttpHeaders.VARY, "Accept-Encoding, Accept"));
  }

  @Test
  void wildcardVaryIsPreserved() throws Exception {
    mockMvc
        .perform(get("/api/vary-star").header(HttpHeaders.ACCEPT, JSON_API))
        .andExpect(status().isOk())
        .andExpect(header().string(HttpHeaders.VARY, "*"));
  }

  @Test
  void existingAcceptVaryIsNotDuplicated() throws Exception {
    mockMvc
        .perform(get("/api/vary-accept").header(HttpHeaders.ACCEPT, JSON_API))
        .andExpect(status().isOk())
        .andExpect(header().string(HttpHeaders.VARY, HttpHeaders.ACCEPT));
  }

  @Test
  void responseEntityEndpointGetsNegotiationHeaders() throws Exception {
    mockMvc
        .perform(get("/api/entity").header(HttpHeaders.ACCEPT, JSON_API))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(JSON_API))
        .andExpect(header().string(HttpHeaders.VARY, "Accept-Encoding, Accept"));
  }

  @JsonApiEndpoint
  @RestController
  static class JsonApiController {
    @GetMapping("/api/articles")
    public String list() {
      return "{\"data\":[]}";
    }

    @PostMapping("/api/articles")
    public String create() {
      return "{\"data\":[]}";
    }
  }

  @RestController
  static class MixedController {
    @JsonApiEndpoint
    @GetMapping("/mixed/resource")
    public String jsonApi() {
      return "{\"data\":[]}";
    }

    @GetMapping(value = "/mixed/plain", produces = "application/json")
    public String plain() {
      return "{\"ok\":true}";
    }
  }

  @RestController
  static class PlainController {
    @GetMapping(value = "/plain/resource", produces = "application/json")
    public String plain() {
      return "{\"ok\":true}";
    }
  }

  @JsonApiEndpoint
  @RestController
  static class VaryController {
    @GetMapping("/api/vary")
    public ResponseEntity<String> vary() {
      return ResponseEntity.ok().header(HttpHeaders.VARY, "Accept-Encoding").body("{\"data\":[]}");
    }

    @GetMapping("/api/vary-star")
    public ResponseEntity<String> varyStar() {
      return ResponseEntity.ok().header(HttpHeaders.VARY, "*").body("{\"data\":[]}");
    }

    @GetMapping("/api/vary-accept")
    public ResponseEntity<String> varyAccept() {
      return ResponseEntity.ok().header(HttpHeaders.VARY, HttpHeaders.ACCEPT).body("{\"data\":[]}");
    }

    @GetMapping("/api/entity")
    public ResponseEntity<String> entity() {
      return ResponseEntity.ok().header(HttpHeaders.VARY, "Accept-Encoding").body("{\"data\":[]}");
    }
  }
}
