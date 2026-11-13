package com.kazforge.jsonapi.spring.webmvc.error;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.kazforge.jsonapi.api.JsonApi;
import com.kazforge.jsonapi.core.model.*;
import com.kazforge.jsonapi.core.validation.PrimaryDataContext;
import com.kazforge.jsonapi.spring.webmvc.JsonApiEndpoint;
import com.kazforge.jsonapi.spring.webmvc.document.JsonApiResourceHttpMessageConverter;
import com.kazforge.jsonapi.spring.webmvc.media.*;
import com.kazforge.jsonapi.spring.webmvc.request.JsonApiRequestContextInterceptor;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageConverters;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.mock.http.MockHttpOutputMessage;
import org.springframework.mock.web.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.config.annotation.*;

@NullMarked
class JsonApiApplicationErrorTest {
  private static final String JSON_API = "application/vnd.api+json";
  private AnnotationConfigWebApplicationContext context;
  private JsonApiProtocolTest.RecordingRuntime runtime;
  private MockMvc mvc;

  @BeforeEach
  void setup() {
    context = new AnnotationConfigWebApplicationContext();
    context.setServletContext(new MockServletContext());
    context.register(Config.class);
    context.refresh();
    runtime = context.getBean(JsonApiProtocolTest.RecordingRuntime.class);
    mvc = MockMvcBuilders.webAppContextSetup(context).build();
  }

  @AfterEach
  void close() {
    context.close();
    RequestContextHolder.resetRequestAttributes();
  }

  @Test
  void directReturnPreservesExplicitStatusHeadersErrorsAndEnvelope() throws Exception {
    mvc.perform(get("/errors/missing").accept(JSON_API))
        .andExpect(status().isNotFound())
        .andExpect(content().contentType(JSON_API))
        .andExpect(header().string("Vary", "Accept"));
    assertEquals(List.of(ErrorObject.ofTitle("Not found")), runtime.last.errors());
    assertNull(runtime.last.data());
    assertNull(runtime.last.included());
    assertNull(runtime.last.jsonapi());

    mvc.perform(get("/errors/complete"))
        .andExpect(status().isConflict())
        .andExpect(content().contentType(JSON_API))
        .andExpect(header().string("X-Trace", "retained"))
        .andExpect(header().stringValues("Set-Cookie", "first=1", "second=2"))
        .andExpect(header().string("WWW-Authenticate", "Bearer"));
    assertEquals(Controller.COMPLETE, runtime.error());
    assertEquals(Meta.of(Map.of("trace", "public")), runtime.last.meta());
    assertEquals(Controller.TOP_LINKS, runtime.last.links());
    assertEquals(JsonApiObject.empty(), runtime.last.jsonapi());
  }

  @Test
  void unannotatedAdviceRelationshipAndAsyncUseOriginalHandlerWithoutLeakingMessage()
      throws Exception {
    var response =
        mvc.perform(get("/errors/conflict")).andExpect(status().isConflict()).andReturn();
    assertEquals("ARTICLE_LOCKED", runtime.error().code());
    assertNull(runtime.error().detail());
    assertFalse(response.getResponse().getContentAsString().contains("secret"));

    mvc.perform(get("/method-error")).andExpect(status().isForbidden());
    assertNull(runtime.error().status());

    mvc.perform(get("/errors/relationship")).andExpect(status().isForbidden());

    var pending = mvc.perform(get("/errors/async")).andReturn();
    assertTrue(pending.getRequest().isAsyncStarted());
    pending.getAsyncResult();
    mvc.perform(asyncDispatch(pending)).andExpect(status().isConflict());
    assertEquals("ARTICLE_LOCKED", runtime.error().code());

    int calls = runtime.calls;
    mvc.perform(get("/ordinary").accept("text/plain"))
        .andExpect(status().isOk())
        .andExpect(content().string("ordinary"));
    assertEquals(calls, runtime.calls);
  }

  @Test
  void mixedAndEmptyErrorsKeepExplicitHttpStatusAndErrorValues() throws Exception {
    for (String path : List.of("/errors/multiple", "/errors/broad")) {
      mvc.perform(get(path)).andExpect(status().isUnprocessableContent());
      assertEquals(
          List.of("404", "409", "absent"),
          Objects.requireNonNull(runtime.last.errors()).stream()
              .map(e -> e.status() == null ? "absent" : e.status())
              .toList());
    }
    mvc.perform(get("/errors/empty")).andExpect(status().isBadRequest());
    assertEquals(List.of(), runtime.last.errors());
  }

  @Test
  void converterContractAndResourceConverterExclusion() throws Exception {
    var converter = new JsonApiErrorHttpMessageConverter(runtime);
    assertEquals(List.of(MediaType.parseMediaType(JSON_API)), converter.getSupportedMediaTypes());
    assertFalse(converter.canWrite(JsonApiErrorResponse.class, null));
    assertFalse(converter.canRead(JsonApiErrorResponse.class, null));
    var input = new MockHttpInputMessage(new byte[0]);
    assertThrows(
        HttpMessageNotReadableException.class,
        () -> converter.read(JsonApiErrorResponse.class, input));
    var errors = JsonApiErrorResponse.of(List.of());
    var output = new MockHttpOutputMessage();
    assertThrows(
        HttpMessageNotWritableException.class, () -> converter.write(errors, null, output));

    var request = new MockHttpServletRequest("POST", "/errors/missing");
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    assertFalse(converter.canWrite(JsonApiErrorResponse.class, null));
    request.setAttribute(
        HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE,
        new HandlerMethod(
            new MethodController(), MethodController.class.getDeclaredMethod("forbidden")));
    assertTrue(converter.canWrite(JsonApiErrorResponse.class, MediaType.APPLICATION_JSON));
    assertFalse(converter.canWrite(String.class, null));
    request.setAttribute(
        HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE,
        new HandlerMethod(
            new MethodController(), MethodController.class.getDeclaredMethod("ordinary")));
    assertFalse(converter.canWrite(JsonApiErrorResponse.class, null));

    new JsonApiRequestContextInterceptor()
        .preHandle(
            request,
            new MockHttpServletResponse(),
            new HandlerMethod(new Controller(), Controller.class.getDeclaredMethod("missing")));
    var resourceConverter = new JsonApiResourceHttpMessageConverter(runtime);
    assertFalse(resourceConverter.canWrite(JsonApiErrorResponse.class, null, null));
    assertFalse(resourceConverter.canWrite(Object.class, JsonApiErrorResponse.class, null));
    assertFalse(
        resourceConverter.canRead(JsonApiErrorResponse.class, MediaType.parseMediaType(JSON_API)));
  }

  @Configuration(proxyBeanMethods = false)
  @EnableWebMvc
  static class Config implements WebMvcConfigurer {
    private final JsonApi runtime;

    Config(JsonApi runtime) {
      this.runtime = runtime;
    }

    @Bean
    static JsonApiProtocolTest.RecordingRuntime runtime() {
      var runtime = new JsonApiProtocolTest.RecordingRuntime();
      runtime.json = "{\"errors\":[]}";
      return runtime;
    }

    @Bean
    Controller controller() {
      return new Controller();
    }

    @Bean
    MethodController methodController() {
      return new MethodController();
    }

    @Bean
    ApplicationAdvice applicationAdvice() {
      return new ApplicationAdvice();
    }

    @Bean
    JsonApiMediaNegotiationAdvice mediaAdvice() {
      return new JsonApiMediaNegotiationAdvice();
    }

    @Override
    public void configureMessageConverters(HttpMessageConverters.ServerBuilder builder) {
      // Deliberately reverse library converter order to prove ownership is type-based.
      builder.addCustomConverter(new JsonApiResourceHttpMessageConverter(runtime));
      builder.addCustomConverter(new JsonApiErrorHttpMessageConverter(runtime));
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
      registry.addInterceptor(new JsonApiMediaNegotiationInterceptor());
      registry.addInterceptor(new JsonApiRequestContextInterceptor());
    }

    @Override
    public void extendHandlerExceptionResolvers(List<HandlerExceptionResolver> resolvers) {
      resolvers.addFirst(new JsonApiProtocolExceptionResolver(runtime));
    }
  }

  @JsonApiEndpoint
  @RestController
  static class Controller {
    static final Links LINKS =
        Links.ofLinks(Map.of("about", Link.ObjectLink.ofHref("https://example.com/errors/locked")));
    static final Links TOP_LINKS =
        Links.ofLinks(Map.of("self", Link.ObjectLink.ofHref("/errors/complete")));
    static final ErrorObject COMPLETE =
        ErrorObject.builder()
            .id("public-id")
            .status("409")
            .code("ARTICLE_LOCKED")
            .title("Locked")
            .detail("Unlock the article first")
            .source(
                ErrorSource.builder()
                    .pointer("/data/attributes/title")
                    .parameter("title")
                    .header("If-Match")
                    .build())
            .links(LINKS)
            .meta(Meta.of(Map.of("retryable", false)))
            .additionalMember("trace", "public")
            .build();

    @RequestMapping("/errors/missing")
    ResponseEntity<JsonApiErrorResponse> missing() {
      return ResponseEntity.status(404)
          .body(JsonApiErrorResponse.of(ErrorObject.ofTitle("Not found")));
    }

    @GetMapping("/errors/conflict")
    String conflict() {
      throw new DomainFailure();
    }

    @GetMapping("/errors/multiple")
    ResponseEntity<JsonApiErrorResponse> multiple() {
      return ResponseEntity.status(422)
          .body(
              JsonApiErrorResponse.of(
                  List.of(
                      ErrorObject.builder().status("404").build(),
                      ErrorObject.builder().status("409").build(),
                      ErrorObject.ofTitle("No status"))));
    }

    @GetMapping("/errors/broad")
    ResponseEntity<Object> broad() {
      return ResponseEntity.status(422).body(multiple().getBody());
    }

    @GetMapping("/errors/empty")
    ResponseEntity<JsonApiErrorResponse> empty() {
      return ResponseEntity.badRequest().body(JsonApiErrorResponse.of(List.of()));
    }

    @GetMapping("/errors/complete")
    ResponseEntity<JsonApiErrorResponse> complete() {
      return ResponseEntity.status(409)
          .contentType(MediaType.parseMediaType("application/json;charset=UTF-16"))
          .header("X-Trace", "retained")
          .header("Set-Cookie", "first=1", "second=2")
          .header("WWW-Authenticate", "Bearer")
          .body(
              JsonApiErrorResponse.of(COMPLETE)
                  .withMeta(Meta.of(Map.of("trace", "public")))
                  .withLinks(TOP_LINKS)
                  .withJsonApi(JsonApiObject.empty()));
    }

    @JsonApiEndpoint(primaryDataContext = PrimaryDataContext.RELATIONSHIP)
    @GetMapping("/errors/relationship")
    @ResponseStatus(HttpStatus.FORBIDDEN)
    JsonApiErrorResponse relationship() {
      return JsonApiErrorResponse.of(ErrorObject.ofTitle("Forbidden"));
    }

    @GetMapping("/errors/async")
    Callable<String> async() {
      return () -> {
        throw new DomainFailure();
      };
    }
  }

  @RestController
  static class MethodController {
    @JsonApiEndpoint
    @GetMapping("/method-error")
    @ResponseStatus(HttpStatus.FORBIDDEN)
    JsonApiErrorResponse forbidden() {
      return JsonApiErrorResponse.of(ErrorObject.ofTitle("Forbidden"));
    }

    @GetMapping("/ordinary")
    String ordinary() {
      return "ordinary";
    }
  }

  static class DomainFailure extends RuntimeException {
    DomainFailure() {
      super("secret internal details");
    }
  }

  @RestControllerAdvice
  static class ApplicationAdvice {
    @ExceptionHandler(DomainFailure.class)
    @SuppressWarnings("unused") // Invoked by Spring's exception-handler resolver.
    ResponseEntity<JsonApiErrorResponse> conflict() {
      return ResponseEntity.status(409)
          .body(
              JsonApiErrorResponse.of(
                  ErrorObject.builder()
                      .status("409")
                      .code("ARTICLE_LOCKED")
                      .title("Article is locked")
                      .build()));
    }
  }
}
