package com.kazforge.jsonapi.spring.webmvc.document;

import jakarta.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;

/** Shared buffered converter output, preserving application-owned status and headers. */
final class JsonApiBodyWriter {
  private static final MediaType JSON_API = MediaType.parseMediaType("application/vnd.api+json");

  private JsonApiBodyWriter() {}

  static void write(
      ByteArrayOutputStream body, HttpOutputMessage output, @Nullable HttpServletResponse response)
      throws IOException {
    if (response != null) {
      if (response.isCommitted()) {
        return;
      }
      int status = response.getStatus();
      Map<String, List<String>> headers = new LinkedHashMap<>();
      for (String name : response.getHeaderNames()) {
        headers.put(name, List.copyOf(response.getHeaders(name)));
      }
      // A full reset also clears a previously selected writer; resetBuffer does not.
      response.reset();
      response.setStatus(status);
      headers.forEach((name, values) -> values.forEach(value -> response.addHeader(name, value)));
      response.setCharacterEncoding((String) null);
      response.setHeader(HttpHeaders.CONTENT_TYPE, JSON_API.toString());
    }
    output.getHeaders().setContentType(JSON_API);
    output.getHeaders().setContentLength(body.size());
    output.getBody().write(body.toByteArray());
  }
}
