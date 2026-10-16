package com.kazforge.jsonapi.spring.webmvc.error;

import com.kazforge.jsonapi.api.JsonApi;
import com.kazforge.jsonapi.core.model.ErrorObject;
import com.kazforge.jsonapi.core.model.ErrorSource;
import com.kazforge.jsonapi.core.model.JsonApiDocument;
import com.kazforge.jsonapi.diagnostic.JsonApiDocumentReadException;
import com.kazforge.jsonapi.diagnostic.JsonApiMappingException;
import com.kazforge.jsonapi.query.JsonApiQueryException;
import com.kazforge.jsonapi.spring.webmvc.JsonApiEndpoint;
import com.kazforge.jsonapi.spring.webmvc.document.JsonApiDocumentResponseWriter;
import com.kazforge.jsonapi.spring.webmvc.internal.ProtocolError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageConversionException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;

/**
 * Safe protocol failure translation for opted-in handlers, including interceptor failures before
 * request context exists. Register before Spring's default resolvers to handle negotiation errors.
 * Application exception handlers may precede this resolver. Unrecognized failures are untouched.
 */
public final class JsonApiProtocolExceptionResolver implements HandlerExceptionResolver {
  private final JsonApiDocumentResponseWriter writer;

  public JsonApiProtocolExceptionResolver(JsonApi runtime) {
    writer = new JsonApiDocumentResponseWriter(runtime);
  }

  @Override
  public @Nullable ModelAndView resolveException(
      HttpServletRequest request,
      HttpServletResponse response,
      @Nullable Object handler,
      Exception exception) {
    if (!(handler instanceof HandlerMethod method)
        || (method.getMethodAnnotation(JsonApiEndpoint.class) == null
            && !method.getBeanType().isAnnotationPresent(JsonApiEndpoint.class))) {
      return null;
    }
    ProtocolFailure error = classify(exception);
    if (error == null) {
      return null;
    }
    try {
      writer.write(JsonApiDocument.withError(error.error()), error.status().value(), response);
    } catch (IOException transportFailure) {
      // Do not retry writes or delegate a partially written response to another renderer.
      if (!response.isCommitted()) {
        response.resetBuffer();
        response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        response.setContentLength(0);
      }
    }
    return new ModelAndView();
  }

  private static @Nullable ProtocolFailure classify(Exception exception) {
    return switch (exception) {
      case JsonApiQueryException query -> queryFailure(query);
      case JsonApiDocumentReadException read -> documentFailure(read);
      case HttpMessageNotReadableException read -> unreadableFailure(read);
      case MethodArgumentNotValidException ignored -> failure(ProtocolError.INVALID_REQUEST);
      case HttpMessageNotWritableException ignored ->
          failure(ProtocolError.RESPONSE_SERIALIZATION_FAILED);
      case HttpMessageConversionException ignored -> failure(ProtocolError.RESOURCE_BINDING_FAILED);
      case HttpMediaTypeNotAcceptableException ignored ->
          mediaFailure(ProtocolError.NOT_ACCEPTABLE, HttpHeaders.ACCEPT);
      case HttpMediaTypeNotSupportedException ignored ->
          mediaFailure(ProtocolError.UNSUPPORTED_MEDIA_TYPE, HttpHeaders.CONTENT_TYPE);
      default -> null;
    };
  }

  private static ProtocolFailure queryFailure(JsonApiQueryException query) {
    String parameter = query.parameterName();
    return badRequest(
        query.diagnostic(),
        "Invalid query parameter",
        parameter == null ? null : ErrorSource.ofParameter(parameter));
  }

  private static ProtocolFailure documentFailure(JsonApiDocumentReadException read) {
    return badRequest(read.category(), "Invalid JSON:API document", null);
  }

  private static ProtocolFailure unreadableFailure(HttpMessageNotReadableException read) {
    return switch (read.getCause()) {
      case JsonApiDocumentReadException documentRead -> documentFailure(documentRead);
      case JsonApiMappingException mapping ->
          badRequest(mapping.diagnostic(), ProtocolError.INVALID_REQUEST_DOCUMENT.title(), null);
      case null, default -> failure(ProtocolError.INVALID_REQUEST_DOCUMENT);
    };
  }

  private static ProtocolFailure badRequest(
      Enum<?> diagnostic, String title, @Nullable ErrorSource source) {
    HttpStatus status = HttpStatus.BAD_REQUEST;
    ErrorObject error =
        ErrorObject.builder()
            .status(Integer.toString(status.value()))
            .code(diagnostic.name())
            .title(title)
            .source(source)
            .build();
    return new ProtocolFailure(status, error);
  }

  private static ProtocolFailure failure(ProtocolError error) {
    return new ProtocolFailure(error.status(), error.error());
  }

  private static ProtocolFailure mediaFailure(ProtocolError error, String header) {
    return new ProtocolFailure(
        error.status(), error.error(ErrorSource.builder().header(header).build()));
  }

  private record ProtocolFailure(HttpStatus status, ErrorObject error) {}
}
