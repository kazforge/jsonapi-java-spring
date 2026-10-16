package com.kazforge.jsonapi.example;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.SpringVersion;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class ConsumerIntegrationTest {
  private static final String JSON_API = "application/vnd.api+json";
  private final JsonMapper mapper = JsonMapper.builder().build();
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
  void createsWithoutAnIdAndSerializesTheBoundAttributes() throws Exception {
    JsonNode body =
        body(
            mvc.perform(
                    post("/articles")
                        .contentType(JSON_API)
                        .accept(JSON_API)
                        .content(
                            """
            {"data":{"type":"articles","attributes":{"title":"Created without ID"}}}
            """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/articles/42"))
                .andExpect(header().string("Vary", "Accept"))
                .andExpect(content().contentType(JSON_API))
                .andReturn());
    assertEquals("articles", body.at("/data/type").asString());
    assertEquals("42", body.at("/data/id").asString());
    assertEquals("Created without ID", body.at("/data/attributes/title").asString());
  }

  @Test
  void preservesAnApplicationSuppliedId() throws Exception {
    JsonNode body =
        body(
            mvc.perform(
                    post("/articles")
                        .contentType(JSON_API)
                        .accept(JSON_API)
                        .content(
                            """
            {"data":{"type":"articles","id":"7","attributes":{"title":"Client ID"}}}
            """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/articles/7"))
                .andReturn());
    assertEquals("7", body.at("/data/id").asString());
    assertEquals("Client ID", body.at("/data/attributes/title").asString());
  }

  @Test
  void writesOrdinaryDtoAndListResponses() throws Exception {
    JsonNode one =
        body(
            mvc.perform(get("/articles/42").accept(JSON_API))
                .andExpect(status().isOk())
                .andExpect(content().contentType(JSON_API))
                .andExpect(header().string("Vary", "Accept"))
                .andReturn());
    assertEquals("Hello JSON:API", one.at("/data/attributes/title").asString());
    JsonNode many =
        body(mvc.perform(get("/articles").accept(JSON_API)).andExpect(status().isOk()).andReturn());
    assertTrue(many.path("data").isArray());
    assertEquals(one.path("data"), many.path("data").get(0));
    JsonNode empty =
        body(
            mvc.perform(get("/articles/empty").accept(JSON_API))
                .andExpect(status().isOk())
                .andReturn());
    assertTrue(empty.path("data").isArray());
    assertEquals(0, empty.path("data").size());
  }

  @Test
  void realRuntimeErrorsAreTransportedAsJsonApiDocuments() throws Exception {
    assertError(
        mvc.perform(post("/articles").contentType(JSON_API).content("{private"))
            .andExpect(status().isBadRequest())
            .andReturn(),
        "400");
    assertError(
        mvc.perform(get("/articles").accept("application/xml"))
            .andExpect(status().isNotAcceptable())
            .andReturn(),
        "406");
    assertError(
        mvc.perform(post("/articles").contentType("application/json").content("{}"))
            .andExpect(status().isUnsupportedMediaType())
            .andReturn(),
        "415");
  }

  @Test
  void leavesUnmarkedEndpointsToSpring() throws Exception {
    JsonNode ordinary =
        body(
            mvc.perform(get("/ordinary").accept("application/json"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/json"))
                .andReturn());
    assertEquals("ordinary MVC", ordinary.path("message").asString());
    assertFalse(ordinary.has("data"));
    assertEquals(System.getProperty("spring.consumerVersion"), SpringVersion.getVersion());
  }

  private JsonNode body(MvcResult result) {
    return mapper.readTree(result.getResponse().getContentAsByteArray());
  }

  private void assertError(MvcResult result, String status) throws Exception {
    assertEquals(JSON_API, result.getResponse().getContentType());
    JsonNode body = body(result);
    assertEquals(status, body.at("/errors/0/status").asString());
    assertFalse(body.has("data"));
    assertFalse(result.getResponse().getContentAsString().contains("private"));
  }
}
