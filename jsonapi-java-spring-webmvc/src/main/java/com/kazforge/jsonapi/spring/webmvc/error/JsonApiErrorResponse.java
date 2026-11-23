package com.kazforge.jsonapi.spring.webmvc.error;

import com.kazforge.jsonapi.core.model.ErrorObject;
import com.kazforge.jsonapi.core.model.JsonApiDocument;
import com.kazforge.jsonapi.core.model.JsonApiObject;
import com.kazforge.jsonapi.core.model.Links;
import com.kazforge.jsonapi.core.model.Meta;
import com.kazforge.jsonapi.document.DocumentEnvelope;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.web.bind.MethodArgumentNotValidException;

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

  /**
   * Builds the same safe, attributed errors as the default protocol resolver for a validation
   * failure on the originally matched JSON:API create or typed PATCH body.
   *
   * <p>Call from application exception advice during the current dispatch. Returns empty unless the
   * exception refers to that handler's body parameter and the same DTO instance the converter bound
   * in this dispatch. Generated errors omit their optional {@code status} member so advice can
   * select an HTTP status with {@code ResponseEntity} without changing the body.
   */
  public static Optional<JsonApiErrorResponse> fromValidation(
      HttpServletRequest request, MethodArgumentNotValidException error) {
    return JsonApiValidationErrors.fromValidation(request, error);
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

  JsonApiDocument toDocument() {
    return new JsonApiDocument(
        null, errors, envelope.meta(), envelope.jsonapi(), envelope.links(), null, Map.of());
  }
}
