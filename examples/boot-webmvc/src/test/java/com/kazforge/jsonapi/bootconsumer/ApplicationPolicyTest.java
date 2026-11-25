package com.kazforge.jsonapi.bootconsumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.kazforge.jsonapi.api.JsonApi;
import com.kazforge.jsonapi.example.boot.ArticleApplication;
import com.kazforge.jsonapi.example.boot.Articles;
import com.kazforge.jsonapi.representation.IncludePolicy;
import com.kazforge.jsonapi.representation.RelationshipAllowance;
import com.kazforge.jsonapi.representation.RepresentationPolicy;
import com.kazforge.jsonapi.spring.webmvc.error.JsonApiErrorResponse;
import com.kazforge.jsonapi.spring.webmvc.error.JsonApiProtocolExceptionResolver;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(
    classes = ArticleApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({ApplicationPolicyTest.Policy.class, ApplicationPolicyTest.ValidationAdvice.class})
class ApplicationPolicyTest {
  private static final String API = "application/vnd.api+json";
  @Autowired WebApplicationContext context;
  @Autowired JsonMapper mapper;
  private MockMvc mvc;

  @BeforeEach
  void setup() {
    mvc = MockMvcBuilders.webAppContextSetup(context).build();
  }

  @Test
  void typedQueryPreservesRequestedIncludesAndSparseFields() throws Exception {
    var result =
        mvc.perform(
                get("/articles")
                    .queryParam("page[size]", "1")
                    .queryParam("include", "author")
                    .queryParam("fields[articles]", "author"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse();
    var doc = mapper.readTree(result.getContentAsByteArray());
    assertFalse(doc.at("/data/0").has("attributes"));
    assertEquals("7", doc.at("/data/0/relationships/author/data/id").asString());
    assertEquals("Ada", doc.at("/included/0/attributes/name").asString());
  }

  @Test
  void customValidatorAndApplicationAdviceHandleOnlySuppliedInvalidValues() throws Exception {
    String data =
        """
        {"data":{"type":"articles","id":"42"%s}}
        """;
    mvc.perform(patch("/articles/42").contentType(API).content(data.formatted("")))
        .andExpect(status().isOk());
    var result =
        mvc.perform(
                patch("/articles/42")
                    .contentType(API)
                    .content(data.formatted(",\"attributes\":{\"title\":null}")))
            .andExpect(status().is(422))
            .andExpect(content().contentType(API))
            .andReturn()
            .getResponse();
    var error = mapper.readTree(result.getContentAsByteArray()).at("/errors/0");
    assertEquals("INVALID_FIELD", error.path("code").asString());
    assertEquals("/data/attributes/title", error.at("/source/pointer").asString());
  }

  @Test
  void applicationProtocolResolverHandlesPatchIdentityConflict(
      @Value("${local.server.port}") int port) throws Exception {
    var request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/articles/42"))
            .header("Content-Type", API)
            .header("Accept", API)
            .method(
                "PATCH",
                HttpRequest.BodyPublishers.ofString(
                    """
                    {"data":{"type":"articles","id":"43"}}
                    """))
            .build();
    try (var client = HttpClient.newHttpClient()) {
      var response = client.send(request, HttpResponse.BodyHandlers.ofString());
      assertEquals(409, response.statusCode(), response::body);
      assertEquals(API, response.headers().firstValue("Content-Type").orElseThrow());
      assertEquals(
          "ENDPOINT_IDENTITY_MISMATCH",
          mapper.readTree(response.body()).at("/errors/0/code").asString());
    }
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class Policy {
    @Bean
    JsonApiProtocolExceptionResolver protocolResolver(JsonApi runtime) {
      return new JsonApiProtocolExceptionResolver(runtime);
    }

    @Bean
    RepresentationPolicy representation() {
      return RepresentationPolicy.defaults()
          .withIncludePolicy(
              IncludePolicy.allowing(Set.of(RelationshipAllowance.of("articles", "author"))));
    }

    @Bean
    LocalValidatorFactoryBean validator() {
      return new LocalValidatorFactoryBean();
    }
  }

  @RestControllerAdvice(assignableTypes = Articles.class)
  static class ValidationAdvice {
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @SuppressWarnings("unused") // Invoked by MVC's exception-handler resolver.
    ResponseEntity<JsonApiErrorResponse> invalid(
        HttpServletRequest request, MethodArgumentNotValidException failure)
        throws MethodArgumentNotValidException {
      return ResponseEntity.status(422)
          .body(JsonApiErrorResponse.fromValidation(request, failure).orElseThrow(() -> failure));
    }
  }
}
