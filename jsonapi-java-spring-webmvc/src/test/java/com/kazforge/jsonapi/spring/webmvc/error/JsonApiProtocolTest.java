package com.kazforge.jsonapi.spring.webmvc.error;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.kazforge.jsonapi.api.*;
import com.kazforge.jsonapi.core.model.*;
import com.kazforge.jsonapi.diagnostic.*;
import com.kazforge.jsonapi.document.DocumentReadContext;
import com.kazforge.jsonapi.mapping.MappedDocument;
import com.kazforge.jsonapi.spring.webmvc.JsonApiEndpoint;
import com.kazforge.jsonapi.spring.webmvc.document.JsonApiDocumentResponseWriter;
import com.kazforge.jsonapi.spring.webmvc.media.*;
import com.kazforge.jsonapi.spring.webmvc.request.*;
import java.io.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.*;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.HandlerMethod;

class JsonApiProtocolTest {
  private final RecordingRuntime runtime = new RecordingRuntime();
  private MockMvc mvc;

  @BeforeEach
  void setup() {
    mvc =
        MockMvcBuilders.standaloneSetup(new Controller(), new AdvancedController(runtime))
            .addInterceptors(
                new JsonApiMediaNegotiationInterceptor(), new JsonApiRequestContextInterceptor())
            .setCustomArgumentResolvers(new JsonApiRequestContextArgumentResolver())
            .setControllerAdvice(new JsonApiMediaNegotiationAdvice())
            .setHandlerExceptionResolvers(new JsonApiProtocolExceptionResolver(runtime))
            .build();
  }

  @ParameterizedTest
  @CsvSource({
    "sort=,INVALID_SORT_SYNTAX,sort",
    "include=%ZZ,MALFORMED_ENCODING,include",
    "fields=title,INVALID_PARAMETER_SHAPE,fields",
    "%ZZ=x,MALFORMED_ENCODING,"
  })
  void queryErrorsAreSafe(String input, String code, String parameter) throws Exception {
    mvc.perform(
            get("/api")
                .with(
                    r -> {
                      r.setQueryString(input);
                      return r;
                    }))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentType("application/vnd.api+json"))
        .andExpect(header().string("Vary", "Accept"));
    ErrorObject error = runtime.error();
    assertEquals(code, error.code());
    assertEquals(parameter, error.source() == null ? null : error.source().parameter());
    assertNull(error.detail());
  }

  @Test
  void negotiationWinsOverInvalidQuery() throws Exception {
    mvc.perform(get("/api").queryParam("sort", "").header("Accept", "application/json"))
        .andExpect(status().isNotAcceptable())
        .andExpect(content().contentType("application/vnd.api+json"));
    assertEquals("Accept", Objects.requireNonNull(runtime.error().source()).header());
    mvc.perform(
            post("/api").queryParam("sort", "").contentType("application/json").content("secret"))
        .andExpect(status().isUnsupportedMediaType());
    assertEquals("Content-Type", Objects.requireNonNull(runtime.error().source()).header());
  }

  @Test
  void readFailureAndOrdinaryEndpoints() throws Exception {
    var result = mvc.perform(get("/read")).andExpect(status().isBadRequest()).andReturn();
    assertFalse(result.getResponse().getContentAsString().contains("secret"));
    assertEquals("MALFORMED_JSON", runtime.error().code());
    mvc.perform(get("/plain").queryParam("sort", "").accept("text/plain"))
        .andExpect(status().isOk())
        .andExpect(content().string("plain"));
    assertThrows(jakarta.servlet.ServletException.class, () -> mvc.perform(get("/domain")));
  }

  @Test
  void unknownAndUnmarkedFailuresAreNotClaimed() throws Exception {
    var resolver = new JsonApiProtocolExceptionResolver(runtime);
    var request = new MockHttpServletRequest();
    var response = new MockHttpServletResponse();
    var handler = new HandlerMethod(new Controller(), "api", JsonApiRequestContext.class);
    assertNull(
        resolver.resolveException(
            request, response, handler, new IllegalArgumentException("secret")));
    assertNull(
        resolver.resolveException(
            request,
            response,
            null,
            new JsonApiDocumentReadException(
                CodecFailureCategory.MALFORMED_JSON, "", SourceLocation.UNKNOWN, "secret")));
    assertNull(
        resolver.resolveException(
            request,
            response,
            new HandlerMethod(new Controller(), "plain"),
            new com.kazforge.jsonapi.query.JsonApiQueryException(
                com.kazforge.jsonapi.query.QueryDiagnostic.INVALID_SORT_SYNTAX, "sort", "secret")));
  }

  @Test
  void writerPreservesDocumentAndHandlesFailureWithoutRecursion() throws Exception {
    var writer = new JsonApiDocumentResponseWriter(runtime);
    var document = JsonApiDocument.withError(ErrorObject.ofTitle("Explicit advanced error"));
    var response = new MockHttpServletResponse();
    response.setHeader("Vary", "Accept-Encoding");
    writer.write(document, 409, response);
    assertSame(document, runtime.last);
    assertEquals(409, response.getStatus());
    assertTrue(response.getHeaders("Vary").contains("Accept-Encoding"));
    assertTrue(response.getContentAsString().contains("\"jsonapi\""));
    runtime.failures = 1;
    writer.write(document, 200, response);
    assertEquals(500, response.getStatus());
    assertEquals("RESPONSE_SERIALIZATION_FAILED", runtime.error().code());
    runtime.failures = 2;
    writer.write(document, 200, response);
    assertEquals(0, response.getContentAsByteArray().length);
    assertEquals(500, response.getStatus());
    assertThrows(IllegalArgumentException.class, () -> writer.write(document, 600, response));
    response.flushBuffer();
    int calls = runtime.calls;
    writer.write(document, 200, response);
    assertEquals(calls, runtime.calls);
  }

  @Test
  void writerPreservesExistingAcceptAndWildcardVary() throws Exception {
    for (String vary : List.of("accept", "*")) {
      var response = new MockHttpServletResponse();
      response.setHeader("Vary", vary);
      new JsonApiDocumentResponseWriter(runtime)
          .write(JsonApiDocument.withError(ErrorObject.ofTitle("x")), 400, response);
      assertEquals(List.of(vary), List.copyOf(response.getHeaders("Vary")));
    }
  }

  @Test
  void rawResponseAndAsyncFailureUseTheCombinedFoundation() throws Exception {
    mvc.perform(get("/raw").queryParam("include", "author"))
        .andExpect(status().isAccepted())
        .andExpect(content().contentType("application/vnd.api+json"))
        .andExpect(header().string("Vary", "Accept"));
    assertEquals("Advanced", runtime.error().title());
    var pending = mvc.perform(get("/async")).andReturn();
    assertTrue(pending.getRequest().isAsyncStarted());
    pending.getAsyncResult();
    mvc.perform(asyncDispatch(pending)).andExpect(status().isBadRequest());
    assertEquals("DUPLICATE_MEMBER", runtime.error().code());
  }

  @Test
  void explicitCharsetDoesNotPolluteProtocolContentType() throws Exception {
    var response = new MockHttpServletResponse();
    response.setCharacterEncoding("UTF-16");
    new JsonApiDocumentResponseWriter(runtime)
        .write(JsonApiDocument.withError(ErrorObject.ofTitle("x")), 400, response);
    assertEquals("application/vnd.api+json", response.getHeader("Content-Type"));
  }

  @ParameterizedTest
  @CsvSource({"UTF-8", "UTF-16", "ISO-8859-1"})
  void selectedWriterCannotRecodeProtocolErrors(String encoding) throws Exception {
    var response = new ModeAwareResponse();
    response.setCharacterEncoding(encoding);
    response.getWriter().write("discarded secret");
    response.setHeader("Access-Control-Allow-Origin", "https://example.com");
    response.setHeader("Vary", "Origin");
    response.addHeader("Set-Cookie", "first=1");
    response.addHeader("Set-Cookie", "second=2");
    response.setContentLength(999);
    runtime.json = "{\"errors\":[{\"title\":\"café 日本語\"}]}";
    var resolver = new JsonApiProtocolExceptionResolver(runtime);
    var handler = new HandlerMethod(new Controller(), "api", JsonApiRequestContext.class);
    assertNotNull(
        resolver.resolveException(
            new MockHttpServletRequest(),
            response,
            handler,
            new JsonApiDocumentReadException(
                CodecFailureCategory.MALFORMED_JSON, "", SourceLocation.UNKNOWN, "secret")));
    assertEquals(400, response.getStatus());
    assertArrayEquals(
        runtime.json.getBytes(java.nio.charset.StandardCharsets.UTF_8),
        response.getContentAsByteArray());
    assertEquals("application/vnd.api+json", response.getContentType());
    assertEquals(response.getContentAsByteArray().length, response.getContentLength());
    assertEquals("https://example.com", response.getHeader("Access-Control-Allow-Origin"));
    assertEquals(List.of("first=1", "second=2"), List.copyOf(response.getHeaders("Set-Cookie")));
    assertEquals(List.of("Origin", "Accept"), List.copyOf(response.getHeaders("Vary")));
  }

  @Test
  void committedWriterIsUntouchedAndTransportFailuresAreNotRetried() throws Exception {
    var response = new ModeAwareResponse();
    response.setCharacterEncoding("UTF-16");
    response.getWriter().write("existing");
    response.flushBuffer();
    int calls = runtime.calls;
    new JsonApiDocumentResponseWriter(runtime).write(runtime.last, 400, response);
    assertEquals(calls, runtime.calls);
    assertEquals(0, response.streamSelections);

    var broken =
        new ModeAwareResponse() {
          @Override
          public jakarta.servlet.ServletOutputStream getOutputStream() {
            super.getOutputStream();
            return new jakarta.servlet.ServletOutputStream() {
              @Override
              public boolean isReady() {
                return true;
              }

              @Override
              public void setWriteListener(jakarta.servlet.WriteListener listener) {}

              @Override
              public void write(int value) throws IOException {
                throw new IOException("transport failure");
              }
            };
          }
        };
    var resolver = new JsonApiProtocolExceptionResolver(runtime);
    assertNotNull(
        resolver.resolveException(
            new MockHttpServletRequest(),
            broken,
            new HandlerMethod(new Controller(), "api", JsonApiRequestContext.class),
            new JsonApiDocumentReadException(
                CodecFailureCategory.MALFORMED_JSON, "", SourceLocation.UNKNOWN, "secret")));
    assertEquals(1, broken.streamSelections);
    assertEquals(500, broken.getStatus());
    assertEquals(0, broken.getContentAsByteArray().length);
  }

  // Spring's mock does not enforce servlet writer/stream exclusion; this fixture does.
  static class ModeAwareResponse extends MockHttpServletResponse {
    private boolean writerSelected;
    private boolean streamSelected;
    int streamSelections;

    @Override
    public PrintWriter getWriter() throws UnsupportedEncodingException {
      if (streamSelected) {
        throw new IllegalStateException("Output stream already selected");
      }
      writerSelected = true;
      return super.getWriter();
    }

    @Override
    public jakarta.servlet.ServletOutputStream getOutputStream() {
      if (writerSelected) {
        throw new IllegalStateException("Writer already selected");
      }
      streamSelected = true;
      streamSelections++;
      return super.getOutputStream();
    }

    @Override
    public void reset() {
      super.reset();
      writerSelected = false;
      streamSelected = false;
    }
  }

  @JsonApiEndpoint
  @RestController
  static class AdvancedController {
    private final JsonApiDocumentResponseWriter writer;

    AdvancedController(JsonApi runtime) {
      writer = new JsonApiDocumentResponseWriter(runtime);
    }

    @GetMapping("/raw")
    public void raw(
        JsonApiRequestContext context, jakarta.servlet.http.HttpServletResponse response)
        throws IOException {
      assertTrue(context.representationSelection().includeRequested());
      writer.write(JsonApiDocument.withError(ErrorObject.ofTitle("Advanced")), 202, response);
    }

    @GetMapping("/async")
    public java.util.concurrent.Callable<String> async() {
      return () -> {
        throw new JsonApiDocumentReadException(
            CodecFailureCategory.DUPLICATE_MEMBER, "", SourceLocation.UNKNOWN, "secret");
      };
    }
  }

  @RestController
  static class Controller {
    @JsonApiEndpoint
    @RequestMapping("/api")
    public String api(JsonApiRequestContext context) {
      return "{}";
    }

    @JsonApiEndpoint
    @GetMapping("/read")
    public String read(jakarta.servlet.http.HttpServletResponse response) throws IOException {
      response.setCharacterEncoding("UTF-16");
      response.getWriter().write("discarded secret");
      throw new JsonApiDocumentReadException(
          CodecFailureCategory.MALFORMED_JSON, "", SourceLocation.UNKNOWN, "secret");
    }

    @GetMapping("/plain")
    public String plain() {
      return "plain";
    }

    @JsonApiEndpoint
    @GetMapping("/domain")
    public String domain() {
      throw new IllegalStateException("secret");
    }
  }

  // A neutral contract double: records the complete document and emits a fixed, runtime-owned
  // representation. Production is never responsible for JSON serialization or runtime defaults.
  static class RecordingRuntime implements JsonApi, JsonApiDocuments {
    JsonApiDocument last = JsonApiDocument.withError(ErrorObject.ofTitle("Initial"));
    int failures;
    int calls;
    String json = "{\"errors\":[{\"title\":\"Safe error\"}],\"jsonapi\":{\"version\":\"1.1\"}}";

    ErrorObject error() {
      return Objects.requireNonNull(last.errors()).getFirst();
    }

    @Override
    public JsonApiDocuments documents() {
      return this;
    }

    @Override
    public JsonApiResources resources() {
      throw new UnsupportedOperationException();
    }

    @Override
    public JsonApiRelationships relationships() {
      throw new UnsupportedOperationException();
    }

    @Override
    public JsonApiPatches patches() {
      throw new UnsupportedOperationException();
    }

    @Override
    public String write(JsonApiDocument document) {
      calls++;
      last = document;
      if (failures > 0) {
        failures--;
        throw new IllegalStateException("secret");
      }
      return json;
    }

    @Override
    public JsonApiDocument read(String json, DocumentReadContext context) {
      throw new UnsupportedOperationException();
    }

    @Override
    public JsonApiDocument read(InputStream json, DocumentReadContext context) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void write(JsonApiDocument document, OutputStream out) {
      throw new UnsupportedOperationException();
    }

    @Override
    public String write(MappedDocument document) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void write(MappedDocument document, OutputStream out) {
      throw new UnsupportedOperationException();
    }
  }
}
