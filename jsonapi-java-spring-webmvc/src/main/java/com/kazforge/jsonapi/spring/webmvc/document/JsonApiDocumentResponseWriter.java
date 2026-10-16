package com.kazforge.jsonapi.spring.webmvc.document;

import com.kazforge.jsonapi.api.JsonApi;
import com.kazforge.jsonapi.core.model.JsonApiDocument;
import com.kazforge.jsonapi.spring.webmvc.internal.ProtocolError;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.http.HttpHeaders;

/**
 * Explicit advanced document response transport through an application-supplied neutral runtime.
 * This is not an ordinary DTO binder. Runtime document defaults are preserved by delegation.
 */
public final class JsonApiDocumentResponseWriter {
  private static final System.Logger LOGGER =
      System.getLogger(JsonApiDocumentResponseWriter.class.getName());
  private final JsonApi runtime;

  public JsonApiDocumentResponseWriter(JsonApi runtime) {
    this.runtime = Objects.requireNonNull(runtime, "runtime");
  }

  /**
   * Writes a document with an explicit HTTP status. Serialization completes before response
   * mutation. Serialization failures produce a safe 500; a failure writing that error produces a
   * bodyless 500. Uncommitted output mode is reset, preserving headers, so even a previously
   * selected non-UTF-8 servlet writer cannot recode the body. Committed responses are not
   * rewritten, and transport I/O failures propagate.
   */
  public void write(JsonApiDocument document, int status, HttpServletResponse response)
      throws IOException {
    Objects.requireNonNull(document, "document");
    if (status < 100 || status > 599) {
      throw new IllegalArgumentException("HTTP status must be between 100 and 599");
    }
    if (response.isCommitted()) {
      return;
    }
    byte[] body;
    int selectedStatus = status;
    try {
      body = serialize(document);
    } catch (RuntimeException failure) {
      LOGGER.log(System.Logger.Level.WARNING, "JSON:API response serialization failed", failure);
      selectedStatus = ProtocolError.RESPONSE_SERIALIZATION_FAILED.status().value();
      try {
        body =
            serialize(
                JsonApiDocument.withError(ProtocolError.RESPONSE_SERIALIZATION_FAILED.error()));
      } catch (RuntimeException errorFailure) {
        LOGGER.log(
            System.Logger.Level.WARNING, "JSON:API error serialization failed", errorFailure);
        body = new byte[0];
      }
    }
    resetOutput(response);
    response.setStatus(selectedStatus);
    // A servlet filter may have set a charset; JSON:API does not permit that media parameter.
    response.setCharacterEncoding((String) null);
    response.setHeader(HttpHeaders.CONTENT_TYPE, "application/vnd.api+json");
    mergeVary(response);
    response.setContentLength(body.length);
    response.getOutputStream().write(body);
  }

  private byte[] serialize(JsonApiDocument document) {
    return runtime.documents().write(document).getBytes(StandardCharsets.UTF_8);
  }

  private static void resetOutput(HttpServletResponse response) {
    Map<String, List<String>> headers = new LinkedHashMap<>();
    for (String name : response.getHeaderNames()) {
      headers.put(name, List.copyOf(response.getHeaders(name)));
    }
    // resetBuffer does not clear the selected writer/stream. A full reset does; retain headers
    // such as CORS, cookies and Vary, then overwrite body-specific metadata for the new response.
    response.reset();
    headers.forEach((name, values) -> values.forEach(value -> response.addHeader(name, value)));
  }

  private static void mergeVary(HttpServletResponse response) {
    for (String value : response.getHeaders(HttpHeaders.VARY)) {
      for (String token : value.split(",")) {
        if ("*".equals(token.trim()) || "Accept".equalsIgnoreCase(token.trim())) {
          return;
        }
      }
    }
    response.addHeader(HttpHeaders.VARY, HttpHeaders.ACCEPT);
  }
}
