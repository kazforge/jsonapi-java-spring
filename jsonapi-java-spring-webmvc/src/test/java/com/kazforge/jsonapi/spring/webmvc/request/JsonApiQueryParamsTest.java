package com.kazforge.jsonapi.spring.webmvc.request;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.kazforge.jsonapi.query.JsonApiQuery;
import com.kazforge.jsonapi.query.JsonApiQueryParser;
import com.kazforge.jsonapi.spring.webmvc.JsonApiEndpoint;
import com.kazforge.jsonapi.spring.webmvc.internal.QueryBindingFailure;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.MethodParameter;
import org.springframework.format.support.DefaultFormattingConversionService;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.Validator;
import org.springframework.web.bind.annotation.BindParam;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.servlet.HandlerInterceptor;

@NullMarked
class JsonApiQueryParamsTest {
  private final JsonApiQueryParamsArgumentResolver resolver =
      new JsonApiQueryParamsArgumentResolver();

  @Test
  void usesPreparedQueryEvenWhenServletAndRawInputsDisagree() throws Exception {
    var controller = new Controller();
    var mvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setCustomArgumentResolvers(resolver, new JsonApiRequestContextArgumentResolver())
            .addInterceptors(
                new JsonApiRequestContextInterceptor(),
                new HandlerInterceptor() {
                  @Override
                  public boolean preHandle(
                      HttpServletRequest req, HttpServletResponse res, Object handler) {
                    ((MockHttpServletRequest) req).setQueryString("sort=%ZZ");
                    return true;
                  }
                })
            .build();
    mvc.perform(
            get("/query")
                .queryParam("tag", "a,b", "", "a,b")
                .queryParam("include", "")
                .queryParam("fields[items]", "")
                .param("other", "body"))
        .andExpect(status().isOk());
    Query bound = Objects.requireNonNull(controller.bound);
    assertSame(Objects.requireNonNull(controller.context).query(), bound.requested());
    assertEquals(List.of("a,b", "", "a,b"), bound.tags());
    assertNull(bound.term());
    assertTrue(bound.requested().selection().includeRequested());
    assertEquals(List.of(), bound.requested().selection().fieldsets().get("items"));
    mvc.perform(get("/query").queryParam("filter[term]", "")).andExpect(status().isOk());
    Query empty = Objects.requireNonNull(controller.bound);
    assertEquals("", empty.term());
    assertEquals(List.of(), empty.tags());
    assertFalse(empty.requested().selection().includeRequested());
  }

  @Test
  void pojoUsesTheConfiguredMvcConversionServiceOnItsConstructorOnly() throws Exception {
    var service = new DefaultFormattingConversionService();
    service.addConverter(String.class, Integer.class, text -> Integer.parseInt(text, 16));
    var controller = new Controller();
    var mvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setCustomArgumentResolvers(resolver)
            .setConversionService(service)
            .addInterceptors(new JsonApiRequestContextInterceptor())
            .build();
    mvc.perform(get("/hex").queryParam("page[n]", "ff"))
        .andExpect(status().isOk())
        .andExpect(content().string("255"));
    var rejected =
        assertThrows(
            ServletException.class, () -> mvc.perform(get("/hex").queryParam("number", "100")));
    assertInstanceOf(QueryBindingFailure.class, rejected.getCause());
  }

  @Test
  void containerElementValidationMapsBackToTheRepeatedKey() {
    var controller = new Controller();
    var mvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setCustomArgumentResolvers(resolver)
            .addInterceptors(new JsonApiRequestContextInterceptor())
            .build();
    var failure =
        assertThrows(
            ServletException.class,
            () -> mvc.perform(get("/tags").queryParam("filter[tag]", "one", "")));
    var errors = assertInstanceOf(QueryBindingFailure.class, failure.getCause()).errors();
    assertEquals("filter[tag]", Objects.requireNonNull(errors.getFirst().source()).parameter());
  }

  @Test
  void objectViolationHasNoParameterEvenWhenInputWasSupplied() {
    var validator = Validator.forInstanceOf(Tags.class, (tags, errors) -> errors.reject("private"));
    var mvc =
        MockMvcBuilders.standaloneSetup(new Controller())
            .setCustomArgumentResolvers(resolver)
            .setValidator(validator)
            .addInterceptors(new JsonApiRequestContextInterceptor())
            .build();
    var failure =
        assertThrows(
            ServletException.class,
            () -> mvc.perform(get("/tags").queryParam("filter[tag]", "one")));
    var error = assertInstanceOf(QueryBindingFailure.class, failure.getCause()).errors().getFirst();
    assertNull(error.source());
    assertNull(error.detail());
  }

  @ParameterizedTest
  @ValueSource(
      classes = {
        UnmarkedInput.class,
        DuplicateKeys.class,
        DuplicateQuery.class,
        Wildcards.class,
        NestedInput.class,
        Primitive.class,
        Reserved.class,
        StringBuilder.class // Multiple public constructors.
      })
  void invalidDeclarationsAreConfigurationErrorsEvenWithoutInput(Class<?> type) {
    var inputs = new QueryArguments(new JsonApiQueryParser().parseRaw(""));
    var conversion = new DefaultFormattingConversionService();
    assertThrows(IllegalStateException.class, () -> inputs.construct(type, conversion));
  }

  @Test
  void requiresExplicitQueryOptInAndPreparedContext() throws Exception {
    Method method = Controller.class.getMethod("query", Query.class, JsonApiRequestContext.class);
    var query = new MethodParameter(method, 0);
    assertTrue(resolver.supportsParameter(query));
    assertFalse(resolver.supportsParameter(new MethodParameter(method, 1)));
    assertThrows(
        IllegalStateException.class,
        () ->
            resolver.resolveArgument(
                query, null, new ServletWebRequest(new MockHttpServletRequest()), null));
  }

  record Query(
      @BindParam("filter[term]") @Nullable String term,
      @BindParam("tag") List<String> tags,
      JsonApiQuery requested) {}

  record Tags(@BindParam("filter[tag]") List<@NotBlank String> values) {}

  static final class Hex {
    private Integer number;

    public Hex(@BindParam("page[n]") Integer number) {
      setNumber(number);
    }

    public void setNumber(Integer number) {
      this.number = number;
    }
  }

  @JsonApiEndpoint
  @RestController
  static class Controller {
    @Nullable Query bound;
    @Nullable JsonApiRequestContext context;

    @GetMapping("/query")
    public void query(@JsonApiQueryParams Query query, JsonApiRequestContext context) {
      this.bound = query;
      this.context = context;
    }

    @GetMapping("/hex")
    public String hex(@JsonApiQueryParams Hex input) {
      return input.number.toString();
    }

    @GetMapping("/tags")
    public String tags(@Valid @JsonApiQueryParams Tags input) {
      return String.join(",", input.values());
    }
  }

  record UnmarkedInput(String value) {}

  record DuplicateKeys(@BindParam("q") Integer a, @BindParam("q") String b) {}

  record DuplicateQuery(JsonApiQuery a, JsonApiQuery b) {}

  record Wildcards(@BindParam("q") List<? extends Number> values) {}

  record NestedInput(@BindParam("q") Tags tags) {}

  record Primitive(@BindParam("q") int value) {}

  record Reserved(@BindParam("sort") String sort) {}
}
