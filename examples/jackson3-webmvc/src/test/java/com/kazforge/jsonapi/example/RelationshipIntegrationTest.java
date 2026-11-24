package com.kazforge.jsonapi.example;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.kazforge.jsonapi.api.JsonApi;
import com.kazforge.jsonapi.core.aggregate.ValidationContext;
import com.kazforge.jsonapi.core.model.ErrorObject;
import com.kazforge.jsonapi.core.model.Meta;
import com.kazforge.jsonapi.core.model.ResourceIdentifier;
import com.kazforge.jsonapi.core.validation.PrimaryDataContext;
import com.kazforge.jsonapi.core.validation.ValidationRuleCode;
import com.kazforge.jsonapi.diagnostic.CodecFailureCategory;
import com.kazforge.jsonapi.diagnostic.JsonApiDocumentReadException;
import com.kazforge.jsonapi.document.DocumentReadContext;
import com.kazforge.jsonapi.document.PrimaryDataKind;
import com.kazforge.jsonapi.spring.webmvc.JsonApiEndpoint;
import com.kazforge.jsonapi.spring.webmvc.document.JsonApiDocumentResponseWriter;
import com.kazforge.jsonapi.spring.webmvc.document.JsonApiToOneRelationship;
import com.kazforge.jsonapi.spring.webmvc.error.JsonApiErrorResponse;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import tools.jackson.databind.json.JsonMapper;

class RelationshipIntegrationTest {
  private static final String JSON_API = "application/vnd.api+json";
  private static final JsonMapper MAPPER = JsonMapper.builder().build();
  private AnnotationConfigWebApplicationContext context;
  private MockMvc mvc;
  private LinkageController controller;

  @BeforeEach
  void setup() {
    context = new AnnotationConfigWebApplicationContext();
    context.setServletContext(new MockServletContext());
    context.register(
        ConsumerConfiguration.class,
        LinkageController.class,
        AdvancedController.class,
        OrdinaryNullController.class);
    context.refresh();
    controller = context.getBean(LinkageController.class);
    mvc = MockMvcBuilders.webAppContextSetup(context).build();
  }

  @AfterEach
  void close() {
    context.close();
  }

  @Test
  void getWritesIdentifiersExplicitNullAndEmptyLists() throws Exception {
    mvc.perform(get("/linkage/one"))
        .andExpect(status().isOk())
        .andExpect(content().contentType(JSON_API))
        .andExpect(header().string("Vary", "Accept"))
        .andExpect(content().string("{\"data\":{\"type\":\"people\",\"id\":\"1\"}}"));
    mvc.perform(get("/linkage/empty-one"))
        .andExpect(status().isOk())
        .andExpect(content().string("{\"data\":null}"));
    mvc.perform(get("/linkage/empty-many"))
        .andExpect(status().isOk())
        .andExpect(content().string("{\"data\":[]}"));
    var body =
        mvc.perform(get("/linkage/many"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertEquals(2, MAPPER.readTree(body).path("data").size());
  }

  @Test
  void requiredBodyDistinguishesClearFromMissingBodyAndMissingData() throws Exception {
    mvc.perform(patch("/linkage/one").contentType(JSON_API).content("{\"data\":null}"))
        .andExpect(status().isOk())
        .andExpect(content().string("{\"data\":null}"));
    assertEquals(1, controller.calls);
    assertNotNull(controller.lastOne);
    assertNull(controller.lastOne.identifier());
    mvc.perform(patch("/linkage/one").contentType(JSON_API))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentType(JSON_API));
    for (String body : List.of("{}", "{\"meta\":{}}", "{\"errors\":[]}")) {
      mvc.perform(patch("/linkage/one").contentType(JSON_API).content(body))
          .andExpect(status().isBadRequest());
    }
    assertEquals(1, controller.calls);
    mvc.perform(patch("/linkage/optional").contentType(JSON_API))
        .andExpect(status().isNoContent())
        .andExpect(content().string(""));
    mvc.perform(patch("/linkage/optional").contentType(JSON_API).content("{\"data\":null}"))
        .andExpect(status().isOk())
        .andExpect(content().string("{\"data\":null}"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"PATCH", "POST", "DELETE"})
  void collectionMutationsKeepEmptyAndNonemptyCollectionsAndApplicationStatus(String method)
      throws Exception {
    for (String data : List.of("[]", "[{\"type\":\"people\",\"id\":\"1\"}]")) {
      mvc.perform(
              request(method, "/linkage/many")
                  .contentType(JSON_API)
                  .content("{\"data\":" + data + "}"))
          .andExpect(status().isOk())
          .andExpect(header().string("X-Operation", method))
          .andExpect(content().string("{\"data\":" + data + "}"));
    }
    assertEquals(2, controller.calls);
    assertEquals(1, controller.lastMany.size());
    mvc.perform(
            request(method, "/linkage/no-content").contentType(JSON_API).content("{\"data\":[]}"))
        .andExpect(status().isNoContent())
        .andExpect(content().string(""));
  }

  @Test
  void preservesIdentifierMetadataAndBothIdentities() throws Exception {
    String identifier =
        """
        {"type":"people","id":"1","lid":"local","meta":{"rank":2,"nested":{"ok":true}}}
        """;
    for (String path : List.of("/linkage/one", "/linkage/many")) {
      String data = path.endsWith("many") ? "[" + identifier + "]" : identifier;
      var response =
          mvc.perform(patch(path).contentType(JSON_API).content("{\"data\":" + data + "}"))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse();
      assertEquals(
          MAPPER.readTree("{\"data\":" + data + "}"),
          MAPPER.readTree(response.getContentAsByteArray()));
    }
    var identifierValue =
        Objects.requireNonNull(Objects.requireNonNull(controller.lastOne).identifier());
    assertEquals("local", identifierValue.lid());
    assertEquals(Meta.of(Map.of("rank", 2, "nested", Map.of("ok", true))), identifierValue.meta());
  }

  @ParameterizedTest
  @CsvSource({
    "PATCH,/linkage/one",
    "PATCH,/linkage/many",
    "POST,/linkage/many",
    "DELETE,/linkage/many"
  })
  void rejectsInvalidIdentifiersAndMissingData(String method, String path) throws Exception {
    for (String identifier :
        List.of(
            "{\"type\":\"people\"}",
            "{\"id\":\"1\"}",
            "{\"type\":\"people\",\"id\":1}",
            "{\"type\":\"people\",\"id\":null}",
            "{\"type\":\"people\",\"lid\":\"local\"}")) {
      String data = path.endsWith("many") ? "[" + identifier + "]" : identifier;
      mvc.perform(request(method, path).contentType(JSON_API).content("{\"data\":" + data + "}"))
          .andExpect(status().isBadRequest())
          .andExpect(content().contentType(JSON_API));
    }
    for (String body : List.of("{}", "{\"meta\":{}}", "{\"errors\":[]}", "{private")) {
      var response =
          mvc.perform(request(method, path).contentType(JSON_API).content(body))
              .andExpect(status().isBadRequest())
              .andExpect(content().contentType(JSON_API))
              .andReturn()
              .getResponse();
      assertFalse(response.getContentAsString().contains("private"));
    }
    assertEquals(0, controller.calls);
  }

  @ParameterizedTest
  @ValueSource(strings = {"attributes", "relationships", "links"})
  void releasedCoreRejectsResourceOnlyMembersWithExactDiagnostic(String member) throws Exception {
    for (String method : List.of("PATCH", "POST", "DELETE")) {
      for (String path : List.of("/linkage/one", "/linkage/many")) {
        if (!method.equals("PATCH") && path.endsWith("one")) continue;
        String identifier = "{\"type\":\"people\",\"id\":\"1\",\"" + member + "\":{}}";
        boolean many = path.endsWith("many");
        String data = many ? "[" + identifier + "]" : identifier;
        var result =
            mvc.perform(
                    request(method, path).contentType(JSON_API).content("{\"data\":" + data + "}"))
                .andExpect(status().isBadRequest())
                .andReturn();
        var failure =
            assertInstanceOf(
                JsonApiDocumentReadException.class,
                Objects.requireNonNull(result.getResolvedException()).getCause());
        assertEquals(CodecFailureCategory.LOCAL_VALIDATION, failure.category());
        assertEquals(ValidationRuleCode.PRIMARY_DATA_CONTEXT_MISMATCH, failure.ruleCode());
        assertEquals((many ? "/data/0/" : "/data/") + member, failure.jsonPointer());
        assertFalse(result.getResponse().getContentAsString().contains("pointer"));
      }
    }
    assertEquals(0, controller.calls);
  }

  @ParameterizedTest
  @ValueSource(strings = {"PATCH", "POST", "DELETE"})
  void collectionMutationsRejectNoncollectionsNullElementsAndDuplicates(String method)
      throws Exception {
    for (String data :
        List.of(
            "null",
            "{\"type\":\"people\",\"id\":\"1\"}",
            "[null]",
            "[{\"type\":\"people\",\"id\":\"1\"},{\"type\":\"people\",\"id\":\"1\"}]",
            "42",
            "\"private\"")) {
      mvc.perform(
              request(method, "/linkage/many")
                  .contentType(JSON_API)
                  .content("{\"data\":" + data + "}"))
          .andExpect(status().isBadRequest());
    }
    assertEquals(0, controller.calls);
  }

  @Test
  void oneRejectsArraysAndMisconfiguredHandlersFailAsServerErrors() throws Exception {
    for (String data : List.of("[]", "[{\"type\":\"people\",\"id\":\"1\"}]")) {
      mvc.perform(patch("/linkage/one").contentType(JSON_API).content("{\"data\":" + data + "}"))
          .andExpect(status().isBadRequest());
    }
    for (MockHttpServletRequestBuilder request :
        List.of(
            post("/linkage/wrong-one"),
            delete("/linkage/wrong-one"),
            put("/linkage/many"),
            patch("/linkage/wrong-target"),
            patch("/linkage/raw-list"))) {
      mvc.perform(request.contentType(JSON_API).content("{\"data\":[]}"))
          .andExpect(status().isInternalServerError())
          .andExpect(content().contentType(JSON_API));
    }
    assertEquals(0, controller.calls);
  }

  @Test
  void negotiationIncludesAndApplicationErrorsUseExistingPipeline() throws Exception {
    mvc.perform(get("/linkage/one").accept("text/plain")).andExpect(status().isNotAcceptable());
    mvc.perform(patch("/linkage/one").contentType("application/json").content("{\"data\":null}"))
        .andExpect(status().isUnsupportedMediaType());
    for (MockHttpServletRequestBuilder request :
        List.of(
            get("/linkage/one?include=author"),
            patch("/linkage/one?include=author").contentType(JSON_API).content("{\"data\":null}"),
            delete("/linkage/no-content?include=author")
                .contentType(JSON_API)
                .content("{\"data\":[]}"))) {
      var response =
          mvc.perform(request)
              .andExpect(status().isBadRequest())
              .andExpect(content().contentType(JSON_API))
              .andReturn()
              .getResponse();
      assertEquals(
          "include",
          MAPPER
              .readTree(response.getContentAsByteArray())
              .at("/errors/0/source/parameter")
              .asString());
    }
    assertEquals(0, controller.calls);
    mvc.perform(get("/linkage/one?include=")).andExpect(status().isOk());
    mvc.perform(get("/linkage/error"))
        .andExpect(status().isForbidden())
        .andExpect(content().string("{\"errors\":[{\"code\":\"FORBIDDEN\"}]}"));
  }

  @Test
  void ordinaryNullResourcePatchAndRelatedResourceGetRemainIsolated() throws Exception {
    mvc.perform(get("/linkage/plain-null"))
        .andExpect(status().isOk())
        .andExpect(content().string(""));
    mvc.perform(get("/ordinary-null")).andExpect(status().isOk()).andExpect(content().string(""));
    mvc.perform(get("/resource-null")).andExpect(status().isOk()).andExpect(content().string(""));
    var resource =
        mvc.perform(get("/linkage/related")).andExpect(status().isOk()).andReturn().getResponse();
    assertEquals(
        "Hello JSON:API",
        MAPPER.readTree(resource.getContentAsByteArray()).at("/data/attributes/title").asString());
    mvc.perform(patch("/articles/42").contentType(JSON_API).content("{\"data\":null}"))
        .andExpect(status().isBadRequest());
    mvc.perform(
            patch("/articles/42")
                .contentType(JSON_API)
                .content(
                    "{\"data\":{\"type\":\"articles\",\"id\":\"42\",\"attributes\":{\"title\":\"ok\"}}}"))
        .andExpect(status().isOk());
  }

  @Test
  void advancedDocumentTransportRetainsApplicationOwnedIncludeSupport() throws Exception {
    var response =
        mvc.perform(get("/linkage/advanced?include=person"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(JSON_API))
            .andReturn()
            .getResponse();
    var body = MAPPER.readTree(response.getContentAsByteArray());
    assertEquals(1, body.path("included").size());
    assertEquals("1", body.at("/included/0/id").asString());
    assertEquals("application", body.at("/meta/source").asString());
    assertEquals("/linkage/advanced", body.at("/links/self").asString());
  }

  private static MockHttpServletRequestBuilder request(String method, String path) {
    return org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request(
        org.springframework.http.HttpMethod.valueOf(method), path);
  }

  @RestController
  @JsonApiEndpoint(primaryDataContext = PrimaryDataContext.RELATIONSHIP)
  static class LinkageController {
    int calls;
    @Nullable JsonApiToOneRelationship lastOne;
    List<ResourceIdentifier> lastMany = List.of();

    @GetMapping("/linkage/one")
    ResourceIdentifier one() {
      return ResourceIdentifier.of("people", "1");
    }

    @GetMapping("/linkage/empty-one")
    JsonApiToOneRelationship emptyOne() {
      return new JsonApiToOneRelationship(null);
    }

    @GetMapping("/linkage/empty-many")
    List<ResourceIdentifier> emptyMany() {
      return List.of();
    }

    @GetMapping("/linkage/many")
    List<ResourceIdentifier> many() {
      return List.of(one(), ResourceIdentifier.of("people", "2"));
    }

    @PatchMapping("/linkage/one")
    JsonApiToOneRelationship replaceOne(@RequestBody JsonApiToOneRelationship value) {
      calls++;
      lastOne = value;
      return value;
    }

    @PatchMapping("/linkage/optional")
    ResponseEntity<JsonApiToOneRelationship> optional(
        @RequestBody(required = false) @Nullable JsonApiToOneRelationship value) {
      return value == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(value);
    }

    @PatchMapping("/linkage/many")
    ResponseEntity<List<ResourceIdentifier>> replace(@RequestBody List<ResourceIdentifier> value) {
      return mutation(value, "PATCH");
    }

    @PostMapping("/linkage/many")
    ResponseEntity<List<ResourceIdentifier>> add(@RequestBody List<ResourceIdentifier> value) {
      return mutation(value, "POST");
    }

    @DeleteMapping("/linkage/many")
    ResponseEntity<List<ResourceIdentifier>> remove(@RequestBody List<ResourceIdentifier> value) {
      return mutation(value, "DELETE");
    }

    private ResponseEntity<List<ResourceIdentifier>> mutation(
        List<ResourceIdentifier> value, String operation) {
      calls++;
      lastMany = value;
      return ResponseEntity.ok().header("X-Operation", operation).body(value);
    }

    @RequestMapping(
        path = "/linkage/no-content",
        method = {RequestMethod.PATCH, RequestMethod.POST, RequestMethod.DELETE})
    ResponseEntity<Void> noContent(@RequestBody List<ResourceIdentifier> value) {
      calls++;
      return ResponseEntity.noContent().build();
    }

    @RequestMapping(
        path = "/linkage/wrong-one",
        method = {RequestMethod.POST, RequestMethod.DELETE})
    JsonApiToOneRelationship wrongOne(@RequestBody JsonApiToOneRelationship value) {
      return replaceOne(value);
    }

    @PutMapping("/linkage/many")
    List<ResourceIdentifier> wrongMethod(@RequestBody List<ResourceIdentifier> value) {
      calls++;
      return value;
    }

    @PatchMapping("/linkage/wrong-target")
    ResourceIdentifier wrongTarget(@RequestBody ResourceIdentifier value) {
      calls++;
      return value;
    }

    @SuppressWarnings("rawtypes")
    @PatchMapping("/linkage/raw-list")
    List wrongList(@RequestBody List value) {
      calls++;
      return value;
    }

    @GetMapping("/linkage/error")
    ResponseEntity<JsonApiErrorResponse> error() {
      return ResponseEntity.status(403)
          .body(JsonApiErrorResponse.of(ErrorObject.builder().code("FORBIDDEN").build()));
    }

    @GetMapping("/linkage/plain-null")
    @Nullable ResourceIdentifier plainNull() {
      return null;
    }

    @JsonApiEndpoint
    @GetMapping("/linkage/related")
    Article related() {
      return new Article("42", "Hello JSON:API");
    }

    @JsonApiEndpoint
    @GetMapping("/resource-null")
    @Nullable Article resourceNull() {
      return null;
    }
  }

  @RestController
  static class OrdinaryNullController {
    @GetMapping("/ordinary-null")
    @Nullable ResourceIdentifier nullBody() {
      return null;
    }
  }

  @RestController
  @JsonApiEndpoint(primaryDataContext = PrimaryDataContext.RELATIONSHIP)
  static class AdvancedController {
    private final JsonApi runtime;

    AdvancedController(JsonApi runtime) {
      this.runtime = runtime;
    }

    @GetMapping("/linkage/advanced")
    void advanced(HttpServletResponse response) throws IOException {
      // This application explicitly owns include support and builds the advanced representation.
      var document =
          runtime
              .documents()
              .read(
                  """
                  {"data":{"type":"people","id":"1"},
                   "links":{"self":"/linkage/advanced"},"meta":{"source":"application"},
                   "included":[{"type":"people","id":"1","attributes":{"name":"A"}}]}
                  """,
                  DocumentReadContext.of(
                      ValidationContext.defaults()
                          .withPrimaryDataContext(PrimaryDataContext.RELATIONSHIP),
                      PrimaryDataKind.RESOURCE_IDENTIFIER));
      new JsonApiDocumentResponseWriter(runtime).write(document, 200, response);
    }
  }
}
