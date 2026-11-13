package com.kazforge.jsonapi.spring.webmvc.error;

import com.kazforge.jsonapi.core.model.ErrorObject;
import com.kazforge.jsonapi.core.model.JsonApiObject;
import com.kazforge.jsonapi.core.model.Links;
import com.kazforge.jsonapi.core.model.Meta;
import com.kazforge.jsonapi.document.DocumentEnvelope;
import java.util.List;
import java.util.Objects;

/**
 * Application-owned JSON:API errors with optional top-level document members.
 *
 * <p>Return from a {@code @JsonApiEndpoint} controller or an application-owned
 * {@code @ExceptionHandler} (typically via {@code ResponseEntity<JsonApiErrorResponse>}) with
 * {@link JsonApiErrorHttpMessageConverter} registered. Spring owns the HTTP status and headers;
 * supplied error members and explicit envelope values are written as-is.
 */
public record JsonApiErrorResponse(List<ErrorObject> errors, DocumentEnvelope envelope) {
  public JsonApiErrorResponse {
    errors = List.copyOf(errors);
    Objects.requireNonNull(envelope, "envelope");
  }

  public static JsonApiErrorResponse of(ErrorObject error) {
    return of(List.of(error));
  }

  public static JsonApiErrorResponse of(List<ErrorObject> errors) {
    return new JsonApiErrorResponse(errors, DocumentEnvelope.empty());
  }

  public JsonApiErrorResponse withMeta(Meta meta) {
    return new JsonApiErrorResponse(errors, envelope.withMeta(meta));
  }

  public JsonApiErrorResponse withLinks(Links links) {
    return new JsonApiErrorResponse(errors, envelope.withLinks(links));
  }

  public JsonApiErrorResponse withJsonApi(JsonApiObject jsonapi) {
    return new JsonApiErrorResponse(errors, envelope.withJsonApi(jsonapi));
  }
}
