package com.kazforge.jsonapi.spring.webmvc.internal;

import com.kazforge.jsonapi.core.model.ErrorObject;
import com.kazforge.jsonapi.core.model.ErrorSource;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;

/** Internal Spring-owned protocol errors; Core diagnostics retain their own codes. */
public enum ProtocolError {
  INVALID_REQUEST_DOCUMENT(
      HttpStatus.BAD_REQUEST, "INVALID_REQUEST_DOCUMENT", "Invalid JSON:API resource document"),
  INVALID_REQUEST(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Invalid request"),
  RESPONSE_SERIALIZATION_FAILED(
      HttpStatus.INTERNAL_SERVER_ERROR,
      "RESPONSE_SERIALIZATION_FAILED",
      HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase()),
  RESOURCE_BINDING_FAILED(
      HttpStatus.INTERNAL_SERVER_ERROR,
      "RESOURCE_BINDING_FAILED",
      HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase()),
  NOT_ACCEPTABLE(
      HttpStatus.NOT_ACCEPTABLE, "NOT_ACCEPTABLE", HttpStatus.NOT_ACCEPTABLE.getReasonPhrase()),
  UNSUPPORTED_MEDIA_TYPE(
      HttpStatus.UNSUPPORTED_MEDIA_TYPE,
      "UNSUPPORTED_MEDIA_TYPE",
      HttpStatus.UNSUPPORTED_MEDIA_TYPE.getReasonPhrase());

  private final HttpStatus status;
  private final String code;
  private final String title;

  ProtocolError(HttpStatus status, String code, String title) {
    this.status = status;
    this.code = code;
    this.title = title;
  }

  public HttpStatus status() {
    return status;
  }

  public ErrorObject error() {
    return error(null);
  }

  public String title() {
    return title;
  }

  public ErrorObject error(@Nullable ErrorSource source) {
    return ErrorObject.builder()
        .status(Integer.toString(status.value()))
        .code(code)
        .title(title)
        .source(source)
        .build();
  }
}
