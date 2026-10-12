package com.kazforge.jsonapi.spring.webmvc.request;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kazforge.jsonapi.core.validation.PrimaryDataContext;
import com.kazforge.jsonapi.query.JsonApiQueryException;
import com.kazforge.jsonapi.query.QueryDiagnostic;
import com.kazforge.jsonapi.query.SortField;
import com.kazforge.jsonapi.spring.webmvc.JsonApiEndpoint;
import com.kazforge.jsonapi.spring.webmvc.media.JsonApiMediaNegotiationAdvice;
import com.kazforge.jsonapi.spring.webmvc.media.JsonApiMediaNegotiationInterceptor;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

class JsonApiRequestContextTest {
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    mvc =
        MockMvcBuilders.standaloneSetup(
                new ResourceController(), new LinkageController(), new MixedController())
            .addInterceptors(
                new JsonApiMediaNegotiationInterceptor(), new JsonApiRequestContextInterceptor())
            .setCustomArgumentResolvers(new JsonApiRequestContextArgumentResolver())
            .setControllerAdvice(new JsonApiMediaNegotiationAdvice())
            .build();
  }

  @Test
  void asyncRedispatchRetainsQueryState() throws Exception {
    var pending = mvc.perform(get("/async").queryParam("include", "author")).andReturn();
    assertTrue(pending.getRequest().isAsyncStarted());
    var first = context(pending);
    pending.getAsyncResult();
    var completed = mvc.perform(asyncDispatch(pending)).andExpect(status().isOk()).andReturn();
    assertSame(first.query(), context(completed).query());
  }

  @Test
  void selectionsRetainNeutralValues() throws Exception {
    var context =
        context(
            mvc.perform(
                    get("/resource")
                        .queryParam("include", "author,comments.author")
                        .queryParam("fields[articles]", "title,author")
                        .queryParam("sort", "-created,title"))
                .andExpect(status().isOk())
                .andReturn());
    assertEquals(
        List.of(SortField.descending("created"), SortField.ascending("title")),
        context.query().sort());
    assertEquals(2, context.representationSelection().includePaths().size());
    assertEquals(
        List.of("title", "author"), context.representationSelection().fieldsets().get("articles"));
    assertSame(context.query().selection(), context.representationSelection());
  }

  @Test
  void emptySelectionsDifferFromAbsentAndRequestsAreIsolated() throws Exception {
    var empty =
        context(
            mvc.perform(
                    get("/resource").queryParam("include", "").queryParam("fields[articles]", ""))
                .andReturn());
    var absent = context(mvc.perform(get("/resource")).andReturn());
    assertTrue(empty.representationSelection().includeRequested());
    assertEquals(List.of(), empty.representationSelection().fieldsets().get("articles"));
    assertFalse(absent.representationSelection().includeRequested());
    assertTrue(absent.representationSelection().fieldsets().isEmpty());
    assertNotSame(empty, absent);
  }

  @Test
  void opaqueValuesPreserveOrderAndDecodeExactlyOnceWithoutBodyParameters() throws Exception {
    var result =
        mvc.perform(
                post("/resource")
                    .with(
                        request -> {
                          request.setQueryString(
                              "page[cursor]=a%2Bb&page[cursor]=%2520&filter[x]=a+b&custom=z&other=q&custom=");
                          request.addParameter("bodyOnly", "ignored");
                          return request;
                        }))
            .andExpect(status().isOk())
            .andReturn();
    var query = context(result).query();
    assertEquals(List.of("a+b", "%20"), query.page().get("page[cursor]"));
    assertEquals(List.of("a b"), query.filter().get("filter[x]"));
    assertEquals(List.of("custom", "other"), List.copyOf(query.unprocessed().keySet()));
    assertEquals(List.of("z", ""), query.unprocessed().get("custom"));
    var unprocessed = query.unprocessed();
    List<String> values = List.of();
    assertThrows(UnsupportedOperationException.class, () -> unprocessed.put("x", values));
  }

  @ParameterizedTest
  @CsvSource({"POST", "PATCH", "DELETE", "GET"})
  void methodAndRoleAreIndependent(String verb) throws Exception {
    for (String path : List.of("/resource", "/linkage")) {
      var result =
          mvc.perform(request(HttpMethod.valueOf(verb), path))
              .andExpect(status().isOk())
              .andReturn();
      var context = context(result);
      assertEquals(HttpMethod.valueOf(verb), context.method());
      assertEquals(
          path.equals("/resource") ? PrimaryDataContext.RESOURCE : PrimaryDataContext.RELATIONSHIP,
          context.primaryDataContext());
    }
  }

  @Test
  void methodMetadataReplacesControllerMetadata() throws Exception {
    assertEquals(
        PrimaryDataContext.RESOURCE,
        context(mvc.perform(get("/override")).andReturn()).primaryDataContext());
    assertEquals(
        PrimaryDataContext.RELATIONSHIP,
        context(mvc.perform(get("/mixed/api")).andReturn()).primaryDataContext());
    var result =
        mvc.perform(get("/mixed/plain").queryParam("sort", ""))
            .andExpect(status().isOk())
            .andReturn();
    assertNull(JsonApiRequestContext.find(result.getRequest()));
  }

  @ParameterizedTest
  @CsvSource({
    "include=author&include=comments, INVALID_PARAMETER_CARDINALITY, include",
    "fields=title, INVALID_PARAMETER_SHAPE, fields",
    "'fields[articles]=title,', INVALID_FIELDSET_SYNTAX, fields[articles]",
    "include=author..name, INVALID_INCLUDE_SYNTAX, include",
    "sort=, INVALID_SORT_SYNTAX, sort",
    "sort=title&sort=id, INVALID_PARAMETER_CARDINALITY, sort",
    "include=%ZZ, MALFORMED_ENCODING, include"
  })
  void diagnosticsAreNotTranslated(String input, QueryDiagnostic diagnostic, String parameter) {
    ServletException failure =
        assertThrows(
            ServletException.class,
            () ->
                mvc.perform(
                    get("/resource")
                        .with(
                            request -> {
                              request.setQueryString(input);
                              return request;
                            })));
    var cause = assertInstanceOf(JsonApiQueryException.class, failure.getCause());
    assertEquals(diagnostic, cause.diagnostic());
    assertEquals(parameter, cause.parameterName());
  }

  @Test
  void negotiationRejectsBeforeQueryParsing() throws Exception {
    var result =
        mvc.perform(get("/resource").queryParam("sort", "").header("Accept", "application/json"))
            .andExpect(status().isNotAcceptable())
            .andReturn();
    assertNull(JsonApiRequestContext.find(result.getRequest()));
    mvc.perform(
            post("/resource").queryParam("sort", "").contentType("application/json").content("{}"))
        .andExpect(status().isUnsupportedMediaType());
  }

  private static JsonApiRequestContext context(MvcResult result) {
    return assertInstanceOf(
        JsonApiRequestContext.class, JsonApiRequestContext.find(result.getRequest()));
  }

  @RestController
  @JsonApiEndpoint
  static class ResourceController {
    @RequestMapping("/async")
    public java.util.concurrent.Callable<String> async(JsonApiRequestContext context) {
      return () -> "{}";
    }

    @RequestMapping("/resource")
    public String handle(JsonApiRequestContext context, HttpServletRequest request) {
      assertSame(context, JsonApiRequestContext.find(request));
      return "{}";
    }
  }

  @RestController
  @JsonApiEndpoint(primaryDataContext = PrimaryDataContext.RELATIONSHIP)
  static class LinkageController {
    @RequestMapping("/linkage")
    public String handle(JsonApiRequestContext context) {
      return "{}";
    }

    @RequestMapping("/override")
    @JsonApiEndpoint
    public String override(JsonApiRequestContext context) {
      return "{}";
    }
  }

  @RestController
  static class MixedController {
    @RequestMapping("/mixed/api")
    @JsonApiEndpoint(primaryDataContext = PrimaryDataContext.RELATIONSHIP)
    public String api(JsonApiRequestContext context) {
      return "{}";
    }

    @RequestMapping("/mixed/plain")
    public String plain() {
      return "{}";
    }
  }
}
