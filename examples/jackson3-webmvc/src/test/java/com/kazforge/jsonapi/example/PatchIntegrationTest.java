package com.kazforge.jsonapi.example;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;
import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import tools.jackson.databind.json.JsonMapper;

@NullMarked
class PatchIntegrationTest {
  private static final String JSON_API = "application/vnd.api+json";
  private AnnotationConfigWebApplicationContext context;
  private MockMvc mvc;

  @BeforeEach
  void setup() {
    context = new AnnotationConfigWebApplicationContext();
    context.setServletContext(new MockServletContext());
    context.register(ConsumerConfiguration.class);
    context.refresh();
    mvc = MockMvcBuilders.webAppContextSetup(context).build();
  }

  @AfterEach
  void close() {
    context.close();
  }

  @Test
  void bindsPresenceDtoAndUsesEachRequestsRouteIdentity() throws Exception {
    for (String id : List.of("42", "43")) {
      var response =
          mvc.perform(
                  patch("/articles/" + id)
                      .contentType(JSON_API)
                      .content(
                          """
          {"data":{"type":"articles","id":"%s","attributes":{"title":null}}}
          """
                              .formatted(id)))
              .andExpect(status().isOk())
              .andExpect(content().contentType(JSON_API))
              .andReturn()
              .getResponse();
      var data =
          JsonMapper.builder().build().readTree(response.getContentAsByteArray()).path("data");
      assertEquals(id, data.path("id").asString());
      assertTrue(data.at("/attributes/title").isNull());
    }
  }

  @Test
  void returnsTheUpdatedArticle() throws Exception {
    var response =
        mvc.perform(
                patch("/articles/42")
                    .contentType(JSON_API)
                    .content(
                        """
        {"data":{"type":"articles","id":"42","attributes":{"title":"Updated"}}}
        """))
            .andExpect(status().isOk())
            .andExpect(content().contentType(JSON_API))
            .andReturn()
            .getResponse();
    var body = JsonMapper.builder().build().readTree(response.getContentAsByteArray());
    assertEquals("articles", body.at("/data/type").asString());
    assertEquals("42", body.at("/data/id").asString());
    assertEquals("Updated", body.at("/data/attributes/title").asString());
  }

  @Test
  void bindsOnlyPrimaryChangesWhenIncludedIsPresent() throws Exception {
    var response =
        mvc.perform(
                patch("/articles/42")
                    .contentType(JSON_API)
                    .content(
                        """
        {"data":{"type":"articles","id":"42","relationships":{
          "editor":{"data":{"type":"people","id":"9"}}}},
         "included":[{"type":"people","id":"9","attributes":{"unknown":"not a PATCH DTO"}}]}
        """))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse();
    var body = JsonMapper.builder().build().readTree(response.getContentAsByteArray());
    assertEquals("Hello JSON:API", body.at("/data/attributes/title").asString());
    assertEquals("9", body.at("/data/relationships/editor/data/id").asString());
    assertFalse(body.has("included"));
  }

  @ParameterizedTest
  @CsvSource({"people,42", "articles,43"})
  void identityConflictsAre409(String type, String id) throws Exception {
    assertError(
        "/articles/42",
        "{\"data\":{\"type\":\"%s\",\"id\":\"%s\"}}".formatted(type, id),
        409,
        "ENDPOINT_IDENTITY_MISMATCH");
  }

  @Test
  void translatesReadMappingAndSpringValidationFailures() throws Exception {
    assertError("/articles/42", "{private", 400, "MALFORMED_JSON");
    assertError("/articles/42", "{\"data\":[]}", 400, "AGGREGATE_VALIDATION");
    assertError(
        "/articles/42",
        """
        {"data":{"type":"articles","id":"42","attributes":{"private":1}}}
        """,
        400,
        "UNKNOWN_PATCH_MEMBER");
    assertError(
        "/articles/42",
        """
        {"data":{"type":"articles","id":"42","attributes":{"title":" "}}}
        """,
        400,
        "INVALID_REQUEST");
  }

  @Test
  void ordinaryHandlersKeepTheirOwnBodyHandling() throws Exception {
    var response =
        mvc.perform(
                patch("/ordinary")
                    .contentType(JSON_API)
                    .content(
                        """
        {"id":"42","title":"ordinary DTO"}
        """))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse();
    var body = JsonMapper.builder().build().readTree(response.getContentAsByteArray());
    assertEquals("ordinary DTO", body.path("title").asString());
    assertFalse(body.has("data"));
  }

  private void assertError(String path, String json, int status, String code) throws Exception {
    var response =
        mvc.perform(patch(path).contentType(JSON_API).content(json))
            .andExpect(status().is(status))
            .andExpect(content().contentType(JSON_API))
            .andReturn()
            .getResponse();
    var error =
        JsonMapper.builder().build().readTree(response.getContentAsByteArray()).at("/errors/0");
    assertEquals(code, error.path("code").asString());
    assertFalse(error.has("detail"));
    assertFalse(response.getContentAsString().contains("private"));
  }
}
