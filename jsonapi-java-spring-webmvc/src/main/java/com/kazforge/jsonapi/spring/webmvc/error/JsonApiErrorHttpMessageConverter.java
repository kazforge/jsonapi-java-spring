package com.kazforge.jsonapi.spring.webmvc.error;

import com.kazforge.jsonapi.api.JsonApi;
import com.kazforge.jsonapi.core.model.JsonApiDocument;
import com.kazforge.jsonapi.document.DocumentEnvelope;
import com.kazforge.jsonapi.spring.webmvc.JsonApiEndpoint;
import com.kazforge.jsonapi.spring.webmvc.document.JsonApiDocumentResponseWriter;
import com.kazforge.jsonapi.spring.webmvc.internal.JsonApiHandlers;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.HandlerMapping;

/**
 * Write-only converter for {@link JsonApiErrorResponse} on {@link JsonApiEndpoint} handlers.
 *
 * <p>Register with {@code HttpMessageConverters.ServerBuilder#addCustomConverter} ahead of default
 * converters. Scoping checks the originally matched {@link JsonApiEndpoint} handler, so
 * relationship endpoints and unannotated {@code @ExceptionHandler} methods are supported without a
 * parsed request context, while ordinary MVC handlers are ignored. Delegates serialization and
 * response header handling to {@link JsonApiDocumentResponseWriter} using Spring's selected HTTP
 * status.
 */
public final class JsonApiErrorHttpMessageConverter
    implements HttpMessageConverter<JsonApiErrorResponse> {
  private static final MediaType JSON_API = MediaType.parseMediaType("application/vnd.api+json");
  private final JsonApiDocumentResponseWriter writer;

  public JsonApiErrorHttpMessageConverter(JsonApi runtime) {
    writer = new JsonApiDocumentResponseWriter(runtime);
  }

  @Override
  public boolean canRead(Class<?> clazz, @Nullable MediaType mediaType) {
    return false;
  }

  @Override
  public boolean canWrite(Class<?> clazz, @Nullable MediaType mediaType) {
    // ResponseEntity can preselect a conflicting content type before advice runs.
    return clazz == JsonApiErrorResponse.class && isJsonApiHandler();
  }

  @Override
  public List<MediaType> getSupportedMediaTypes() {
    return List.of(JSON_API);
  }

  @Override
  public JsonApiErrorResponse read(
      Class<? extends JsonApiErrorResponse> clazz, HttpInputMessage inputMessage) {
    throw new HttpMessageNotReadableException(
        "JSON:API error responses are write-only", inputMessage);
  }

  @Override
  public void write(
      JsonApiErrorResponse body, @Nullable MediaType contentType, HttpOutputMessage outputMessage)
      throws IOException {
    if (!(outputMessage instanceof ServletServerHttpResponse output)) {
      throw new HttpMessageNotWritableException("JSON:API errors require a servlet response");
    }
    DocumentEnvelope envelope = body.envelope();
    JsonApiDocument document =
        new JsonApiDocument(
            null,
            body.errors(),
            envelope.meta(),
            envelope.jsonapi(),
            envelope.links(),
            null,
            Map.of());
    writer.write(document, output.getServletResponse().getStatus(), output.getServletResponse());
  }

  private static boolean isJsonApiHandler() {
    if (!(RequestContextHolder.getRequestAttributes()
        instanceof ServletRequestAttributes attributes)) {
      return false;
    }
    return JsonApiHandlers.isEndpoint(
        attributes.getRequest().getAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE));
  }
}
