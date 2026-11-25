package com.kazforge.jsonapi.example;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.kazforge.jsonapi.annotation.*;
import com.kazforge.jsonapi.query.JsonApiQuery;
import com.kazforge.jsonapi.query.SortField;
import com.kazforge.jsonapi.spring.webmvc.JsonApiEndpoint;
import com.kazforge.jsonapi.spring.webmvc.request.JsonApiQueryParams;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.BindParam;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import tools.jackson.databind.json.JsonMapper;

class QueryIntegrationTest {
  private static final String JSON_API = "application/vnd.api+json";
  private final JsonMapper mapper = JsonMapper.builder().build();
  private final AnnotationConfigWebApplicationContext context =
      new AnnotationConfigWebApplicationContext();
  private MockMvc mvc;
  private SearchController controller;

  @BeforeEach
  void setup() {
    context.setServletContext(new MockServletContext());
    context.register(ConsumerConfiguration.class, SearchController.class);
    context.refresh();
    controller = context.getBean(SearchController.class);
    mvc = MockMvcBuilders.webAppContextSetup(context).build();
  }

  @AfterEach
  void close() {
    context.close();
  }

  @Test
  void numberedSearchBindsTypedValuesAndWritesTheRequestedCompoundDocument() throws Exception {
    var response =
        mvc.perform(
                get("/search")
                    .queryParam("filter[status]", "PUBLISHED")
                    .queryParam("page[number]", "2")
                    .queryParam("page[size]", "20")
                    .queryParam("sort", "-title,id")
                    .queryParam("include", "author")
                    .queryParam("fields[articles]", "title,author")
                    .queryParam("fields[people]", "name"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(JSON_API))
            .andReturn()
            .getResponse();
    var query = Objects.requireNonNull(controller.search);
    assertEquals(Status.PUBLISHED, query.status());
    assertEquals(2, query.number());
    assertEquals(20, query.size());
    assertEquals(
        List.of(SortField.descending("title"), SortField.ascending("id")),
        query.requested().sort());
    var body = mapper.readTree(response.getContentAsByteArray());
    assertEquals(
        mapper.readTree(
            """
            {"type":"articles","id":"42","attributes":{"title":"News"},
             "relationships":{"author":{"data":{"type":"people","id":"7"}}}}
            """),
        body.at("/data/0"));
    assertEquals(
        mapper.readTree(
            """
            [{"type":"people","id":"7","attributes":{"name":"Ada"}}]
            """),
        body.path("included"));
  }

  @Test
  void cursorFeedReceivesRepeatedEnumValuesAndAnOpaqueOnceDecodedToken() throws Exception {
    mvc.perform(
            get("/events")
                .with(request -> {
                  request.setQueryString(
                      "filter[kind]=CREATE&filter[kind]=UPDATE&filter[kind]=CREATE&page[after]=a%252Fb%2B");
                  request.addParameter("filter[kind]", "form-value");
                  return request;
                }))
        .andExpect(status().isOk());
    assertEquals(
        new EventFeed(List.of(Kind.CREATE, Kind.UPDATE, Kind.CREATE), "a%2Fb+"), controller.feed);
  }

  @ParameterizedTest
  @CsvSource({
    "page[size]=private,INVALID_QUERY_PARAMETER,page[size]",
    "page[size]=,INVALID_QUERY_PARAMETER,page[size]",
    "page[size]=0&page[number]=2,INVALID_QUERY_PARAMETER,page[size]",
    "page[size]=2&page[size]=2,INVALID_PARAMETER_CARDINALITY,page[size]",
    "filter[status]=private,INVALID_QUERY_PARAMETER,filter[status]",
    "filter[owner.name]=private,UNSUPPORTED_QUERY_PARAMETER,filter[owner.name]",
    "filter[owner][id]=private,UNSUPPORTED_QUERY_PARAMETER,filter[owner][id]",
    "class.module=private,UNSUPPORTED_QUERY_PARAMETER,class.module",
    "'',INVALID_QUERY_PARAMETER,"
  })
  void rejectsBadInputWithOnlyProvenSources(String raw, String code, String parameter)
      throws Exception {
    var result =
        mvc.perform(
                get("/search")
                    .with(r -> {
                      r.setQueryString(raw);
                      return r;
                    }))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentType(JSON_API))
            .andReturn();
    var errors = mapper.readTree(result.getResponse().getContentAsByteArray()).path("errors");
    assertEquals(1, errors.size());
    var error = errors.get(0);
    assertEquals(code, error.path("code").asString());
    if (parameter == null) {
      assertFalse(error.has("source"));
    } else {
      assertEquals(parameter, error.at("/source/parameter").asString());
    }
    assertFalse(result.getResponse().getContentAsString().contains("private"));
    assertNull(controller.search);
  }

  @Test
  void anExplicitEmptyCursorFailsValidationWithItsExternalKey() throws Exception {
    var result =
        mvc.perform(
                get("/events").queryParam("filter[kind]", "CREATE").queryParam("page[after]", ""))
            .andExpect(status().isBadRequest())
            .andReturn();
    assertEquals(
        "page[after]",
        mapper
            .readTree(result.getResponse().getContentAsByteArray())
            .at("/errors/0/source/parameter")
            .asString());
  }

  enum Status {
    DRAFT,
    PUBLISHED
  }

  enum Kind {
    CREATE,
    UPDATE
  }

  record ArticleQuery(
      @BindParam("filter[status]") Status status,
      @BindParam("page[number]") @NotNull Integer number,
      @BindParam("page[size]") @Min(value = 1, message = "private") Integer size,
      JsonApiQuery requested) {}

  record EventFeed(
      @BindParam("filter[kind]") List<Kind> kinds,
      @BindParam("page[after]") @NotBlank String after) {}

  @JsonApiResource(type = "articles")
  public record ArticleView(
      @JsonApiId String id,
      @JsonApiAttribute String title,
      @JsonApiAttribute String summary,
      @JsonApiRelationship @Nullable Person author) {}

  @JsonApiResource(type = "people")
  public record Person(
      @JsonApiId String id, @JsonApiAttribute String name, @JsonApiAttribute String bio) {}

  @RestController
  @JsonApiEndpoint
  static class SearchController {
    @Nullable ArticleQuery search;
    @Nullable EventFeed feed;

    @GetMapping("/search")
    public List<ArticleView> search(@Valid @JsonApiQueryParams ArticleQuery query) {
      search = query;
      var loadAuthor =
          query.requested().selection().includePaths().stream()
              .anyMatch(path -> path.dotted().equals("author"));
      return List.of(
          new ArticleView(
              "42",
              "News",
              "Long summary",
              loadAuthor ? new Person("7", "Ada", "Long bio") : null));
    }

    @GetMapping("/events")
    public void events(@Valid @JsonApiQueryParams EventFeed query) {
      feed = query;
    }
  }
}
