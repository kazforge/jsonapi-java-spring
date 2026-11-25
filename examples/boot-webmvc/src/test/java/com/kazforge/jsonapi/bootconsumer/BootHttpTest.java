package com.kazforge.jsonapi.bootconsumer;

import static org.junit.jupiter.api.Assertions.*;

import com.kazforge.jsonapi.core.model.ResourceIdentifier;
import com.kazforge.jsonapi.core.validation.PrimaryDataContext;
import com.kazforge.jsonapi.example.boot.ArticleApplication;
import com.kazforge.jsonapi.example.boot.Articles.Article;
import com.kazforge.jsonapi.patch.PatchCommand;
import com.kazforge.jsonapi.spring.webmvc.JsonApiEndpoint;
import com.kazforge.jsonapi.spring.webmvc.JsonApiPatchTarget;
import com.kazforge.jsonapi.spring.webmvc.document.JsonApiToOneRelationship;
import jakarta.validation.Valid;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringBootVersion;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(
    classes = ArticleApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    useMainMethod = SpringBootTest.UseMainMethod.ALWAYS)
@Import({BootHttpTest.Linkage.class, BootHttpTest.Commands.class, BootHttpTest.Ordinary.class})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BootHttpTest {
  private static final String API = "application/vnd.api+json";
  private static final String JSON = "application/json";
  private static final String PATCH =
      """
      {"data":{"type":"articles","id":"42","attributes":{"title":"Updated"}}}
      """;
  private final HttpClient client = HttpClient.newHttpClient();

  @Value("${local.server.port}")
  int port;

  @Autowired JsonMapper mapper;

  @AfterAll
  void closeClient() {
    client.close();
  }

  @Test
  void createsAndReadsResourcesWithTheSelectedBootRuntime() throws Exception {
    assertEquals(System.getProperty("boot.expectedVersion"), SpringBootVersion.getVersion());
    var created = exchange("POST", "/articles", PATCH, API, API);
    assertEquals(201, created.statusCode(), created::body);
    assertEquals("/articles/42", created.headers().firstValue("Location").orElseThrow());
    assertEquals("Updated", body(created).at("/data/attributes/title").asString());
    var get = exchange("GET", "/articles/42", "", API, API);
    assertEquals("42", body(get).at("/data/id").asString());
    assertEquals(API, get.headers().firstValue("Content-Type").orElseThrow());
    assertTrue(get.headers().allValues("Vary").contains("Accept"));
    var empty = exchange("GET", "/articles?page%5Bsize%5D=0", "", API, API);
    assertEquals(mapper.readTree("[]"), body(empty).path("data"));
  }

  @Test
  void selectsOnlyProvenIncludeErrorsOnReads() throws Exception {
    for (String include : List.of("author", "unknown")) {
      var response = exchange("GET", "/articles/42?include=" + include, "", API, API);
      assertEquals(400, response.statusCode(), response::body);
      assertEquals("include", body(response).at("/errors/0/source/parameter").asString());
    }
    // The controller has already accepted this create. Do not imply a retryable input error.
    var mutation = exchange("POST", "/articles?include=author", PATCH, API, API);
    assertEquals(500, mutation.statusCode(), mutation::body);
    assertEquals("RESPONSE_SERIALIZATION_FAILED", body(mutation).at("/errors/0/code").asString());
  }

  @Test
  void validatesPatchPresenceAndTrustedRouteIdentity() throws Exception {
    var updated = exchange("PATCH", "/articles/42", PATCH, API, API);
    assertEquals("Updated", body(updated).at("/data/attributes/title").asString());
    var omitted =
        exchange(
            "PATCH",
            "/articles/42",
            """
            {"data":{"type":"articles","id":"42"}}
            """,
            API,
            API);
    assertEquals("News", body(omitted).at("/data/attributes/title").asString());
    var invalid = exchange("PATCH", "/articles/42", PATCH.replace("\"Updated\"", "null"), API, API);
    assertEquals(400, invalid.statusCode(), invalid::body);
    assertEquals("/data/attributes/title", body(invalid).at("/errors/0/source/pointer").asString());
    var ok = exchange("PATCH", "/groups/other/articles/42", PATCH, API, API);
    assertEquals(204, ok.statusCode(), ok::body);
    assertEquals("", ok.body());
    var conflict = exchange("PATCH", "/groups/42/articles/other", PATCH, API, API);
    assertEquals(409, conflict.statusCode(), conflict::body);
    assertEquals("ENDPOINT_IDENTITY_MISMATCH", body(conflict).at("/errors/0/code").asString());
  }

  @Test
  void negotiatesOnlyOnMarkedHandlers() throws Exception {
    assertEquals(406, exchange("GET", "/articles/42", "", API, JSON).statusCode());
    assertEquals(415, exchange("POST", "/articles", PATCH, JSON, API).statusCode());
    var ordinary = exchange("POST", "/ordinary", "{\"id\":\"7\",\"title\":\"Plain\"}", JSON, JSON);
    assertEquals(200, ordinary.statusCode(), ordinary::body);
    assertEquals("Plain", body(ordinary).path("title").asString());
    assertFalse(body(ordinary).has("data"));
    assertFalse(ordinary.headers().allValues("Vary").contains("Accept"));
    var invalid = exchange("POST", "/ordinary", "{\"title\":\"\"}", JSON, JSON);
    assertEquals(400, invalid.statusCode());
    assertFalse(body(invalid).has("errors"));
  }

  @Test
  void readsLinkageAndAcceptsAnExplicitToOneClear() throws Exception {
    assertEquals("7", body(exchange("GET", "/linkage", "", API, API)).at("/data/0/id").asString());
    var clear = exchange("PATCH", "/linkage/one", "{\"data\":null}", API, API);
    assertEquals(200, clear.statusCode());
    assertTrue(body(clear).path("data").isNull());
  }

  @ParameterizedTest
  @ValueSource(strings = {"PATCH", "POST", "DELETE"})
  void bindsToManyMutationBodies(String method) throws Exception {
    var response =
        exchange(
            method,
            "/linkage",
            """
            {"data":[{"type":"people","id":"9"}]}
            """,
            API,
            API);
    assertEquals(200, response.statusCode(), response::body);
    assertEquals("9", body(response).at("/data/0/id").asString());
  }

  private JsonNode body(HttpResponse<String> response) {
    return mapper.readTree(response.body());
  }

  private HttpResponse<String> exchange(
      String method, String path, String body, String contentType, String accept) throws Exception {
    var request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .header("Accept", accept)
            .method(
                method,
                body.isEmpty()
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(body));
    if (!body.isEmpty()) {
      request.header("Content-Type", contentType);
    }
    return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
  }

  @RestController
  @JsonApiEndpoint(primaryDataContext = PrimaryDataContext.RELATIONSHIP)
  static class Linkage {
    @GetMapping("/linkage")
    List<ResourceIdentifier> linkage() {
      return List.of(ResourceIdentifier.of("people", "7"));
    }

    @PatchMapping("/linkage/one")
    JsonApiToOneRelationship clear(@RequestBody JsonApiToOneRelationship value) {
      return value;
    }

    @RequestMapping(
        path = "/linkage",
        method = {RequestMethod.PATCH, RequestMethod.POST, RequestMethod.DELETE})
    List<ResourceIdentifier> change(@RequestBody List<ResourceIdentifier> value) {
      return value;
    }
  }

  @RestController
  @JsonApiEndpoint
  static class Commands {
    @PatchMapping("/groups/{id}/articles/{articleId}")
    @JsonApiPatchTarget(value = "articles", idVariable = "articleId")
    ResponseEntity<Void> command(@RequestBody PatchCommand<Article> command) {
      assertEquals("42", command.identity());
      return ResponseEntity.noContent().build();
    }
  }

  @RestController
  static class Ordinary {
    @PostMapping("/ordinary")
    Article ordinary(@Valid @RequestBody Article article) {
      return article;
    }
  }
}
