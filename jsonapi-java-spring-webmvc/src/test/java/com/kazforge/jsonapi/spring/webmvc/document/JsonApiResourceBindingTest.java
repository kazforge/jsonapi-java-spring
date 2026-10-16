package com.kazforge.jsonapi.spring.webmvc.document;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.kazforge.jsonapi.api.JsonApi;
import com.kazforge.jsonapi.api.JsonApiDocuments;
import com.kazforge.jsonapi.api.JsonApiResources;
import com.kazforge.jsonapi.api.ResourceWriteOptions;
import com.kazforge.jsonapi.core.model.JsonApiDocument;
import com.kazforge.jsonapi.core.model.JsonApiObject;
import com.kazforge.jsonapi.core.model.Link;
import com.kazforge.jsonapi.core.model.Links;
import com.kazforge.jsonapi.core.model.Meta;
import com.kazforge.jsonapi.core.validation.PrimaryDataContext;
import com.kazforge.jsonapi.diagnostic.CodecFailureCategory;
import com.kazforge.jsonapi.diagnostic.JsonApiDocumentReadException;
import com.kazforge.jsonapi.diagnostic.JsonApiMappingException;
import com.kazforge.jsonapi.diagnostic.MappingDiagnostic;
import com.kazforge.jsonapi.diagnostic.SourceLocation;
import com.kazforge.jsonapi.spring.webmvc.JsonApiEndpoint;
import com.kazforge.jsonapi.spring.webmvc.error.JsonApiProtocolExceptionResolver;
import com.kazforge.jsonapi.spring.webmvc.media.JsonApiMediaNegotiationAdvice;
import com.kazforge.jsonapi.spring.webmvc.media.JsonApiMediaNegotiationInterceptor;
import com.kazforge.jsonapi.spring.webmvc.request.JsonApiRequestContextArgumentResolver;
import com.kazforge.jsonapi.spring.webmvc.request.JsonApiRequestContextInterceptor;
import jakarta.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageConverters;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.BindingResult;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@NullMarked
class JsonApiResourceBindingTest {
  private Recording runtime;
  private AnnotationConfigWebApplicationContext context;
  private MockMvc mvc;

  @BeforeEach
  void setup() {
    context = new AnnotationConfigWebApplicationContext();
    context.setServletContext(new MockServletContext());
    context.register(JsonApiTestConfiguration.class);
    context.refresh();
    runtime = context.getBean(Recording.class);
    Controller.lastCreated = null;
    Controller.validations = 0;
    Controller.invalidCalls = 0;
    Controller.bindingResultCalls = 0;
    OrdinaryConverter.readCalls = 0;
    OrdinaryConverter.writeCalls = 0;
    OrdinaryConverter.writtenValues.clear();
    ArrayFallbackConverter.readCalls = 0;
    mvc = MockMvcBuilders.webAppContextSetup(context).build();
  }

  @AfterEach
  void closeContext() {
    context.close();
  }

  private static String errorCode(JsonApiDocument document) {
    return java.util.Objects.requireNonNull(
        java.util.Objects.requireNonNull(document.errors()).getFirst().code());
  }

  @Test
  void requestCreateUsesCoreStreamOperationAndValidationRunsAfterBinding() throws Exception {
    mvc.perform(post("/resources").contentType("application/vnd.api+json").content("create-body"))
        .andExpect(status().isOk())
        .andExpect(content().contentType("application/vnd.api+json"));
    assertEquals(1, runtime.reads);
    assertEquals(Dto.class, runtime.readType);
    assertEquals("create-body", runtime.readBody);
    assertEquals("create-body", java.util.Objects.requireNonNull(Controller.lastCreated).name());
    assertEquals(1, Controller.validations);

    var invalid =
        mvc.perform(post("/invalid").contentType("application/vnd.api+json").content("invalid"))
            .andExpect(status().isBadRequest())
            .andReturn();
    assertFalse(invalid.getResponse().getContentAsString().contains("invalid"));
    assertEquals(2, runtime.reads);
    assertEquals(0, Controller.invalidCalls);

    mvc.perform(post("/binding-result").contentType("application/vnd.api+json").content("invalid"))
        .andExpect(status().isOk());
    assertEquals(3, runtime.reads);
    assertEquals(1, Controller.bindingResultCalls);

    var bulk =
        mvc.perform(post("/bulk").contentType("application/vnd.api+json").content("[]"))
            .andExpect(status().isBadRequest())
            .andReturn();
    assertFalse(bulk.getResponse().getContentAsString().contains("List"));
    assertEquals(3, runtime.reads);

    var bulkArray =
        mvc.perform(post("/bulk-array").contentType("application/vnd.api+json").content("[]"))
            .andExpect(status().isBadRequest())
            .andReturn();
    assertFalse(bulkArray.getResponse().getContentAsString().contains("Dto"));
    assertEquals(0, ArrayFallbackConverter.readCalls);
    assertEquals(3, runtime.reads);
  }

  @Test
  void mapsOnlyKnownInputDiagnosticsToSafeClientErrors() throws Exception {
    for (MappingDiagnostic diagnostic :
        List.of(
            MappingDiagnostic.RESOURCE_TYPE_MISMATCH,
            MappingDiagnostic.RELATIONSHIP_CARDINALITY_MISMATCH,
            MappingDiagnostic.IDENTIFIER_CONVERSION_FAILED)) {
      runtime.mappingDiagnostic = diagnostic;
      var response =
          mvc.perform(
                  post("/resources")
                      .contentType("application/vnd.api+json")
                      .content("private input"))
              .andExpect(status().isBadRequest())
              .andReturn();
      assertFalse(response.getResponse().getContentAsString().contains("private"));
      assertEquals(diagnostic.name(), errorCode(runtime.error));
    }

    runtime.mappingDiagnostic = MappingDiagnostic.MISSING_RESOURCE_ANNOTATION;
    var configurationFailure =
        mvc.perform(
                post("/resources").contentType("application/vnd.api+json").content("private input"))
            .andExpect(status().isInternalServerError())
            .andReturn();
    assertFalse(configurationFailure.getResponse().getContentAsString().contains("private"));
    assertEquals("RESOURCE_BINDING_FAILED", errorCode(runtime.error));
  }

  @Test
  void resolvesInheritedGenericRequestTypesAgainstConcreteController() throws Exception {
    mvc.perform(post("/generic").contentType("application/vnd.api+json").content("generic-body"))
        .andExpect(status().isOk());
    assertEquals(1, runtime.reads);
    assertEquals("generic-body", runtime.readBody);
    ParameterizedType delegated = assertInstanceOf(ParameterizedType.class, runtime.readType);
    assertEquals(GenericDto.class, delegated.getRawType());
    assertArrayEquals(new Type[] {String.class}, delegated.getActualTypeArguments());
  }

  @Test
  void writesSingletonAndCollectionValuesThroughActualMvcConverter() throws Exception {
    mvc.perform(get("/one").accept("application/vnd.api+json").queryParam("include", "author"))
        .andExpect(status().isOk())
        .andExpect(content().string("resource-document"));
    assertEquals("writeOne", runtime.writes.getLast());
    assertEquals(new Dto("one"), runtime.writtenValues.getLast());
    assertTrue(runtime.options.getLast().selection().includeRequested());
    assertNull(runtime.options.getLast().envelope().jsonapi());

    mvc.perform(get("/many").accept("application/vnd.api+json"))
        .andExpect(status().isOk())
        .andExpect(content().string("collection-document"));
    assertEquals("writeMany", runtime.writes.getLast());
    assertEquals(List.of(new Dto("many")), runtime.writtenValues.getLast());
    assertFalse(runtime.options.getLast().selection().includeRequested());
    assertNull(runtime.options.getLast().envelope().jsonapi());
    mvc.perform(get("/empty").accept("application/vnd.api+json")).andExpect(status().isOk());
    assertEquals("writeMany", runtime.writes.getLast());
    assertEquals(List.of(), runtime.writtenValues.getLast());
  }

  @Test
  void rawStringAndByteResponsesBypassResourceSerialization() throws Exception {
    int resourceWrites = runtime.writes.size();
    mvc.perform(get("/plain-string").accept("application/vnd.api+json"))
        .andExpect(status().isOk())
        .andExpect(content().string("plain"));
    mvc.perform(get("/raw-bytes").accept("application/vnd.api+json"))
        .andExpect(status().isOk())
        .andExpect(content().bytes("bytes".getBytes(StandardCharsets.UTF_8)));
    assertEquals(resourceWrites, runtime.writes.size());
  }

  @Test
  void responseEntitiesPreserveHeadersAndUnwrapResourceEnvelopes() throws Exception {
    mvc.perform(get("/entity").accept("application/vnd.api+json"))
        .andExpect(status().isCreated())
        .andExpect(header().string("Location", "/resources/7"))
        .andExpect(header().string("X-Trace", "kept"))
        .andExpect(header().string("Vary", "Accept"))
        .andExpect(content().contentType("application/vnd.api+json"));

    mvc.perform(get("/entity-list").accept("application/vnd.api+json"))
        .andExpect(status().isAccepted())
        .andExpect(header().string("X-Trace", "list"));
    assertEquals("writeMany", runtime.writes.getLast());

    mvc.perform(get("/wrapped-entity").accept("application/vnd.api+json"))
        .andExpect(status().isAccepted());
    assertEquals("writeOne", runtime.writes.getLast());
    assertEquals(new Dto("wrapped"), runtime.writtenValues.getLast());
    assertEquals(
        "entity",
        java.util.Objects.requireNonNull(runtime.options.getLast().envelope().meta())
            .members()
            .get("source"));
    assertEquals(JsonApiObject.ofVersion("1.1"), runtime.options.getLast().envelope().jsonapi());
  }

  @Test
  void paginationEnvelopeLeavesRuntimeDefaultsIntact() throws Exception {
    mvc.perform(get("/page").accept("application/vnd.api+json")).andExpect(status().isOk());
    assertEquals(
        "7",
        java.util.Objects.requireNonNull(runtime.options.getLast().envelope().meta())
            .members()
            .get("page"));
    Links pageLinks =
        java.util.Objects.requireNonNull(runtime.options.getLast().envelope().links());
    assertTrue(pageLinks.links().containsKey("self"));
    assertTrue(pageLinks.links().containsKey("next"));
    assertNull(runtime.options.getLast().envelope().jsonapi());
  }

  @Test
  void resourceWritesResetUncommittedOutputAndSkipCommittedResponses() throws Exception {
    mvc.perform(get("/writer-selected").accept("application/vnd.api+json"))
        .andExpect(status().isOk())
        .andExpect(content().string("resource-document"))
        .andExpect(content().contentType("application/vnd.api+json"))
        .andExpect(header().string("X-Pending", "preserved"));
    assertEquals(new Dto("writer-selected"), runtime.writtenValues.getLast());
    int writesBeforeCommitted = runtime.writes.size();
    mvc.perform(get("/committed").accept("application/vnd.api+json"))
        .andExpect(status().isOk())
        .andExpect(content().string("already-written"));
    assertEquals(writesBeforeCommitted, runtime.writes.size());
  }

  @Test
  void asyncRedispatchAndOrdinarySameDtoRequestsDoNotLeakContext() throws Exception {
    var pending =
        mvc.perform(
                get("/async")
                    .accept("application/vnd.api+json")
                    .queryParam("include", "author")
                    .queryParam("fields[articles]", "title"))
            .andReturn();
    assertTrue(pending.getRequest().isAsyncStarted());
    pending.getAsyncResult();
    mvc.perform(asyncDispatch(pending)).andExpect(status().isOk());
    assertEquals("writeOne", runtime.writes.getLast());
    assertTrue(runtime.options.getLast().selection().includeRequested());
    assertEquals(
        "author", runtime.options.getLast().selection().includePaths().getFirst().dotted());
    assertEquals(
        List.of("title"), runtime.options.getLast().selection().fieldsets().get("articles"));

    mvc.perform(get("/one").accept("application/vnd.api+json")).andExpect(status().isOk());
    assertFalse(runtime.options.getLast().selection().includeRequested());
    assertTrue(runtime.options.getLast().selection().includePaths().isEmpty());
    assertTrue(runtime.options.getLast().selection().fieldsets().isEmpty());

    int resourceReads = runtime.reads;
    int resourceWrites = runtime.writes.size();
    for (String contentType : List.of("application/json", "application/vnd.api+json")) {
      for (String accept : List.of("application/json", "*/*", "application/vnd.api+json")) {
        mvc.perform(post("/ordinary").contentType(contentType).accept(accept).content("ordinary"))
            .andExpect(status().isOk())
            .andExpect(content().string("ordinary"))
            .andExpect(
                content()
                    .contentType(
                        accept.equals("application/vnd.api+json")
                            ? "application/vnd.api+json"
                            : "application/json"));
      }
    }
    assertEquals(6, OrdinaryConverter.readCalls);
    assertEquals(6, OrdinaryConverter.writeCalls);
    assertEquals(
        List.of("ordinary", "ordinary", "ordinary", "ordinary", "ordinary", "ordinary"),
        List.copyOf(OrdinaryConverter.writtenValues));
    assertEquals(resourceReads, runtime.reads);
    assertEquals(resourceWrites, runtime.writes.size());

    int relationshipReads = OrdinaryConverter.readCalls;
    mvc.perform(
            post("/relationship")
                .contentType("application/vnd.api+json")
                .accept("application/vnd.api+json")
                .content("relationship"))
        .andExpect(status().isOk())
        .andExpect(content().string("relationship"))
        .andExpect(content().contentType("application/vnd.api+json"));
    assertEquals(relationshipReads + 1, OrdinaryConverter.readCalls);
    assertEquals(resourceReads, runtime.reads);
    assertEquals(resourceWrites, runtime.writes.size());

    mvc.perform(post("/resources").contentType("application/vnd.api+json").content("next"))
        .andExpect(status().isOk());
    assertEquals(resourceReads + 1, runtime.reads);
  }

  @Test
  void documentReadAndResourceSerializationFailuresAreSafe() throws Exception {
    runtime.failRead = true;
    var invalid =
        mvc.perform(post("/resources").contentType("application/vnd.api+json").content("secret"))
            .andExpect(status().isBadRequest())
            .andReturn();
    assertFalse(invalid.getResponse().getContentAsString().contains("secret"));
    assertEquals(
        "MALFORMED_JSON",
        java.util.Objects.requireNonNull(runtime.error.errors()).getFirst().code());

    runtime.failWrite = true;
    var failed = mvc.perform(get("/one").accept("application/vnd.api+json")).andReturn();
    assertEquals(500, failed.getResponse().getStatus());
    assertEquals("application/vnd.api+json", failed.getResponse().getContentType());
    assertFalse(failed.getResponse().getContentAsString().contains("secret"));
    assertEquals(
        "RESPONSE_SERIALIZATION_FAILED",
        java.util.Objects.requireNonNull(runtime.error.errors()).getFirst().code());
  }

  @Configuration(proxyBeanMethods = false)
  @EnableWebMvc
  static class JsonApiTestConfiguration implements WebMvcConfigurer {
    @org.springframework.beans.factory.annotation.Autowired Recording recording;

    @Bean
    Recording recording() {
      return new Recording();
    }

    @Bean
    JsonApi runtime(Recording recording) {
      return recording.runtime;
    }

    @Bean
    Controller controller() {
      return new Controller();
    }

    @Bean
    OrdinaryController ordinaryController() {
      return new OrdinaryController();
    }

    @Bean
    ConcreteGenericController genericController() {
      return new ConcreteGenericController();
    }

    @Bean
    JsonApiMediaNegotiationAdvice advice() {
      return new JsonApiMediaNegotiationAdvice();
    }

    @Override
    public void configureMessageConverters(HttpMessageConverters.ServerBuilder builder) {
      builder.addCustomConverter(new JsonApiResourceHttpMessageConverter(recording.runtime));
      builder.addCustomConverter(new OrdinaryConverter());
      builder.addCustomConverter(new ArrayFallbackConverter());
    }

    @Override
    public void addInterceptors(
        org.springframework.web.servlet.config.annotation.InterceptorRegistry registry) {
      registry.addInterceptor(new JsonApiMediaNegotiationInterceptor());
      registry.addInterceptor(new JsonApiRequestContextInterceptor());
    }

    @Override
    public void addArgumentResolvers(
        List<org.springframework.web.method.support.HandlerMethodArgumentResolver> resolvers) {
      resolvers.add(new JsonApiRequestContextArgumentResolver());
    }

    @Override
    public Validator getValidator() {
      return new DtoValidator();
    }

    @Override
    public void extendHandlerExceptionResolvers(List<HandlerExceptionResolver> resolvers) {
      resolvers.addFirst(new JsonApiProtocolExceptionResolver(recording.runtime));
    }
  }

  @JsonApiEndpoint
  @RestController
  static class Controller {
    static @Nullable Dto lastCreated;
    static int validations;
    static int invalidCalls;
    static int bindingResultCalls;

    @PostMapping("/resources")
    Dto create(@RequestBody @Validated Dto dto) {
      validations++;
      lastCreated = dto;
      return dto;
    }

    @PostMapping("/invalid")
    Dto invalid(@RequestBody @Validated Dto dto) {
      invalidCalls++;
      return dto;
    }

    @PostMapping("/binding-result")
    Dto bindingResult(@RequestBody @Validated Dto dto, BindingResult errors) {
      if (errors.hasErrors()) {
        bindingResultCalls++;
      }
      return dto;
    }

    @PostMapping("/bulk")
    void bulk(@RequestBody List<Dto> dto) {
      assertNotNull(dto);
    }

    @PostMapping("/bulk-array")
    void bulkArray(@RequestBody Dto[] dto) {
      assertNotNull(dto);
    }

    @PostMapping("/relationship")
    @JsonApiEndpoint(primaryDataContext = PrimaryDataContext.RELATIONSHIP)
    Dto relationship(@RequestBody Dto dto) {
      return dto;
    }

    @GetMapping("/one")
    Dto one() {
      return new Dto("one");
    }

    @GetMapping("/many")
    List<Dto> many() {
      return List.of(new Dto("many"));
    }

    @GetMapping("/empty")
    List<Dto> empty() {
      return List.of();
    }

    @GetMapping("/plain-string")
    String plainString() {
      return "plain";
    }

    @GetMapping("/raw-bytes")
    byte[] rawBytes() {
      return "bytes".getBytes(StandardCharsets.UTF_8);
    }

    @GetMapping("/entity")
    ResponseEntity<Dto> entity() {
      return ResponseEntity.status(HttpStatus.CREATED)
          .contentType(MediaType.parseMediaType("application/json;charset=UTF-16"))
          .location(java.net.URI.create("/resources/7"))
          .header("X-Trace", "kept")
          .body(new Dto("entity"));
    }

    @GetMapping("/page")
    JsonApiResponse<List<Dto>> page() {
      return JsonApiResponse.of(List.of(new Dto("page")))
          .withMeta(Meta.of(java.util.Map.of("page", "7")))
          .withLinks(
              Links.ofLinks(
                  Map.of(
                      "self", Link.ObjectLink.ofHref("/articles?page=2"),
                      "next", Link.ObjectLink.ofHref("/articles?page=3"))));
    }

    @GetMapping("/entity-list")
    ResponseEntity<List<Dto>> entityList() {
      return ResponseEntity.accepted()
          .header("X-Trace", "list")
          .body(List.of(new Dto("entity-list")));
    }

    @GetMapping("/wrapped-entity")
    ResponseEntity<JsonApiResponse<Dto>> wrappedEntity() {
      return ResponseEntity.accepted()
          .body(
              JsonApiResponse.of(new Dto("wrapped"))
                  .withMeta(Meta.of(Map.of("source", "entity")))
                  .withJsonApi(JsonApiObject.ofVersion("1.1")));
    }

    @GetMapping("/async")
    Callable<Dto> async() {
      return () -> new Dto("async");
    }

    @GetMapping("/writer-selected")
    Dto writerSelected(HttpServletResponse response) throws java.io.IOException {
      response.setCharacterEncoding("UTF-16");
      response.getWriter().write("discarded");
      response.setHeader("X-Pending", "preserved");
      return new Dto("writer-selected");
    }

    @GetMapping("/committed")
    Dto committed(HttpServletResponse response) throws java.io.IOException {
      response.getOutputStream().write("already-written".getBytes(StandardCharsets.UTF_8));
      response.flushBuffer();
      return new Dto("must-not-serialize");
    }
  }

  abstract static class GenericController<T> {
    @PostMapping("/generic")
    GenericDto<T> create(@RequestBody GenericDto<T> value) {
      return value;
    }
  }

  @JsonApiEndpoint
  @RestController
  static class ConcreteGenericController extends GenericController<String> {}

  @RestController
  static class OrdinaryController {
    @PostMapping("/ordinary")
    Dto ordinary(@RequestBody Dto dto) {
      return dto;
    }
  }

  record Dto(String name) {}

  record GenericDto<T>(T value) {}

  static class DtoValidator implements Validator {
    @Override
    public boolean supports(Class<?> clazz) {
      return Dto.class.isAssignableFrom(clazz);
    }

    @Override
    public void validate(Object target, Errors errors) {
      if (((Dto) target).name().equals("invalid")) {
        errors.reject("invalid", "invalid");
      }
    }
  }

  static class OrdinaryConverter
      extends org.springframework.http.converter.AbstractHttpMessageConverter<Dto> {
    static int readCalls;
    static int writeCalls;
    static final List<String> writtenValues = new ArrayList<>();

    OrdinaryConverter() {
      super(MediaType.APPLICATION_JSON, MediaType.ALL);
    }

    @Override
    protected boolean supports(Class<?> clazz) {
      return Dto.class.isAssignableFrom(clazz);
    }

    @Override
    protected Dto readInternal(Class<? extends Dto> clazz, HttpInputMessage input)
        throws java.io.IOException {
      String name = new String(input.getBody().readAllBytes(), StandardCharsets.UTF_8);
      readCalls++;
      return new Dto(name);
    }

    @Override
    protected void writeInternal(Dto dto, org.springframework.http.HttpOutputMessage output)
        throws java.io.IOException {
      writeCalls++;
      writtenValues.add(dto.name());
      output.getBody().write(dto.name().getBytes(StandardCharsets.UTF_8));
    }
  }

  static class ArrayFallbackConverter
      extends org.springframework.http.converter.AbstractHttpMessageConverter<Dto[]> {
    static int readCalls;

    ArrayFallbackConverter() {
      super(MediaType.APPLICATION_JSON, MediaType.ALL);
    }

    @Override
    protected boolean supports(Class<?> clazz) {
      return Dto[].class.isAssignableFrom(clazz);
    }

    @Override
    protected Dto[] readInternal(Class<? extends Dto[]> clazz, HttpInputMessage input)
        throws java.io.IOException {
      input.getBody().readAllBytes();
      readCalls++;
      return new Dto[0];
    }

    @Override
    protected void writeInternal(Dto[] dtos, org.springframework.http.HttpOutputMessage output)
        throws java.io.IOException {
      output.getBody().write("array".getBytes(StandardCharsets.UTF_8));
    }
  }

  static class Recording {
    final List<String> writes = new ArrayList<>();
    final List<Object> writtenValues = new ArrayList<>();
    final List<ResourceWriteOptions> options = new ArrayList<>();
    int reads;
    boolean failRead;
    boolean failWrite;
    @Nullable MappingDiagnostic mappingDiagnostic;
    JsonApiDocument error =
        JsonApiDocument.withError(com.kazforge.jsonapi.core.model.ErrorObject.ofTitle("initial"));
    @Nullable Type readType;
    String readBody = "";
    final JsonApiResources resources;
    final JsonApiDocuments documents;
    final JsonApi runtime;

    Recording() {
      resources =
          (JsonApiResources)
              Proxy.newProxyInstance(
                  JsonApiResources.class.getClassLoader(),
                  new Class<?>[] {JsonApiResources.class},
                  (proxy, method, args) ->
                      switch (method.getName()) {
                        case "readCreate" -> {
                          reads++;
                          if (mappingDiagnostic != null) {
                            throw new JsonApiMappingException(
                                mappingDiagnostic, Dto.class, null, "private mapping details");
                          }
                          if (failRead) {
                            throw new JsonApiDocumentReadException(
                                CodecFailureCategory.MALFORMED_JSON,
                                "",
                                SourceLocation.UNKNOWN,
                                "secret");
                          }
                          Object target = args[1];
                          readType = (Type) target;
                          readBody =
                              new String(
                                  ((InputStream) args[0]).readAllBytes(), StandardCharsets.UTF_8);
                          if (target instanceof ParameterizedType parameterized
                              && parameterized.getRawType() == GenericDto.class) {
                            yield new GenericDto<>("resolved-generic");
                          }
                          yield new Dto(readBody);
                        }
                        case "writeOne", "writeMany" -> {
                          writes.add(method.getName());
                          if (failWrite) {
                            throw new IllegalStateException("secret");
                          }
                          writtenValues.add(args[0]);
                          ResourceWriteOptions opts = (ResourceWriteOptions) args[1];
                          options.add(opts);
                          ((ByteArrayOutputStream) args[2])
                              .write(
                                  (method.getName().equals("writeOne")
                                          ? "resource-document"
                                          : "collection-document")
                                      .getBytes(StandardCharsets.UTF_8));
                          yield null;
                        }
                        default -> throw new UnsupportedOperationException(method.getName());
                      });
      documents =
          (JsonApiDocuments)
              Proxy.newProxyInstance(
                  JsonApiDocuments.class.getClassLoader(),
                  new Class<?>[] {JsonApiDocuments.class},
                  (proxy, method, args) -> {
                    if (method.getName().equals("write") && args[0] instanceof JsonApiDocument) {
                      error = (JsonApiDocument) args[0];
                      return "{\"errors\":[{\"title\":\"Safe binding error\"}]}";
                    }
                    throw new UnsupportedOperationException(method.getName());
                  });
      runtime =
          (JsonApi)
              Proxy.newProxyInstance(
                  JsonApi.class.getClassLoader(),
                  new Class<?>[] {JsonApi.class},
                  (proxy, method, args) -> {
                    if (method.getName().equals("resources")) return resources;
                    if (method.getName().equals("documents")) return documents;
                    throw new UnsupportedOperationException(method.getName());
                  });
    }
  }
}
