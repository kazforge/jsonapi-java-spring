package com.kazforge.jsonapi.example;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.kazforge.jsonapi.core.model.*;
import com.kazforge.jsonapi.jackson3.JsonApiJackson3;
import com.kazforge.jsonapi.spring.webmvc.JsonApiEndpoint;
import com.kazforge.jsonapi.spring.webmvc.error.JsonApiErrorResponse;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class ApplicationErrorIntegrationTest {
  private static final String JSON_API = "application/vnd.api+json";
  private final JsonMapper mapper = JsonMapper.builder().build();

  @Test
  void transportsControllerAndAdviceErrorsThroughConsumerConfiguration() throws Exception {
    try (var context = application(false)) {
      MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).build();
      var complete =
          mvc.perform(get("/application-errors/complete").accept(JSON_API))
              .andExpect(status().isConflict())
              .andExpect(content().contentType(JSON_API))
              .andExpect(header().string("Vary", "Accept"))
              .andExpect(header().string("X-Trace", "public"))
              .andReturn();
      JsonNode body = body(complete);
      JsonNode error = body.path("errors").get(0);
      assertEquals("409", error.path("status").asString());
      assertEquals("ARTICLE_LOCKED", error.path("code").asString());
      assertEquals("/data/attributes/title", error.at("/source/pointer").asString());
      assertEquals("public", body.at("/meta/trace").asString());
      assertEquals("/application-errors/complete", body.at("/links/self/href").asString());
      assertFalse(body.has("data"));
      assertFalse(body.has("jsonapi"));

      var advice =
          mvc.perform(get("/application-errors/failure"))
              .andExpect(status().isConflict())
              .andExpect(content().contentType(JSON_API))
              .andReturn();
      assertEquals("ARTICLE_LOCKED", body(advice).at("/errors/0/code").asString());
      assertFalse(advice.getResponse().getContentAsString().contains("secret"));

      var invalid =
          mvc.perform(get("/application-errors/invalid"))
              .andExpect(status().isInternalServerError())
              .andReturn();
      assertEquals("RESPONSE_SERIALIZATION_FAILED", body(invalid).at("/errors/0/code").asString());
      assertFalse(invalid.getResponse().getContentAsString().contains("private"));
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void resourceDefaultAndExplicitErrorEnvelopesKeepTheirOwnContracts(boolean versionDefault)
      throws Exception {
    try (var context = application(versionDefault)) {
      MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).build();
      var resource = body(mvc.perform(get("/articles/42")).andExpect(status().isOk()).andReturn());
      assertEquals(versionDefault, resource.has("jsonapi"));
      if (versionDefault) {
        assertEquals("1.1", resource.at("/jsonapi/version").asString());
      }
      var missing =
          body(
              mvc.perform(get("/application-errors/missing"))
                  .andExpect(status().isNotFound())
                  .andReturn());
      assertFalse(missing.has("jsonapi"));
      var explicit =
          body(
              mvc.perform(get("/application-errors/explicit"))
                  .andExpect(status().isBadRequest())
                  .andReturn());
      assertEquals("1.0", explicit.at("/jsonapi/version").asString());
      assertEquals("explicit", explicit.at("/jsonapi/meta/policy").asString());
    }
  }

  private AnnotationConfigWebApplicationContext application(boolean versionDefault) {
    var builder = JsonApiJackson3.builder(mapper);
    if (versionDefault) {
      builder.jsonApiVersion("1.1");
    }
    var runtime = builder.build();
    var context = new AnnotationConfigWebApplicationContext();
    context.setServletContext(new MockServletContext());
    context.register(ConsumerConfiguration.class, ErrorController.class, ApplicationAdvice.class);
    context.addBeanFactoryPostProcessor(factory -> factory.registerSingleton("jsonApi", runtime));
    context.refresh();
    return context;
  }

  private JsonNode body(MvcResult result) {
    return mapper.readTree(result.getResponse().getContentAsByteArray());
  }

  @JsonApiEndpoint
  @RestController
  static class ErrorController {
    @GetMapping("/application-errors/{kind}")
    ResponseEntity<JsonApiErrorResponse> error(@PathVariable("kind") String kind) {
      return switch (kind) {
        case "missing" ->
            ResponseEntity.status(404)
                .body(JsonApiErrorResponse.of(ErrorObject.ofTitle("Not found")));
        case "complete" ->
            ResponseEntity.status(409)
                .header("X-Trace", "public")
                .body(
                    JsonApiErrorResponse.of(
                            ErrorObject.builder()
                                .status("409")
                                .code("ARTICLE_LOCKED")
                                .title("Article is locked")
                                .source(
                                    ErrorSource.builder().pointer("/data/attributes/title").build())
                                .build())
                        .withMeta(Meta.of(Map.of("trace", "public")))
                        .withLinks(
                            Links.ofLinks(
                                Map.of(
                                    "self",
                                    Link.ObjectLink.ofHref("/application-errors/complete")))));
        case "explicit" ->
            ResponseEntity.badRequest()
                .body(
                    JsonApiErrorResponse.of(ErrorObject.ofTitle("Explicit"))
                        .withJsonApi(
                            new JsonApiObject(
                                "1.0",
                                null,
                                null,
                                Meta.of(Map.of("policy", "explicit")),
                                Map.of())));
        case "failure" -> throw new ArticleLocked();
        case "invalid" ->
            ResponseEntity.badRequest()
                .body(
                    JsonApiErrorResponse.of(
                        ErrorObject.builder()
                            .title("Invalid caller document")
                            .additionalMember("trace", "private")
                            .build()));
        default -> throw new IllegalArgumentException("Unknown test case");
      };
    }
  }

  static class ArticleLocked extends RuntimeException {
    ArticleLocked() {
      super("secret internal state");
    }
  }

  @RestControllerAdvice(assignableTypes = ErrorController.class)
  static class ApplicationAdvice {
    @ExceptionHandler(ArticleLocked.class)
    @SuppressWarnings("unused") // Invoked by Spring's exception-handler resolver.
    ResponseEntity<JsonApiErrorResponse> locked() {
      return ResponseEntity.status(409)
          .body(
              JsonApiErrorResponse.of(
                  ErrorObject.builder().code("ARTICLE_LOCKED").title("Article is locked").build()));
    }
  }
}
