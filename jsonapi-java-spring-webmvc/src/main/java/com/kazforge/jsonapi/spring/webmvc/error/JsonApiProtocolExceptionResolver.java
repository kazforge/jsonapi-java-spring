package com.kazforge.jsonapi.spring.webmvc.error;

import com.kazforge.jsonapi.api.JsonApi;
import com.kazforge.jsonapi.core.model.ErrorObject;
import com.kazforge.jsonapi.core.model.ErrorSource;
import com.kazforge.jsonapi.core.model.JsonApiDocument;
import com.kazforge.jsonapi.diagnostic.JsonApiDocumentReadException;
import com.kazforge.jsonapi.query.JsonApiQueryException;
import com.kazforge.jsonapi.spring.webmvc.JsonApiEndpoint;
import com.kazforge.jsonapi.spring.webmvc.document.JsonApiDocumentResponseWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
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
    ErrorObject error = translate(exception);
    if (error == null) {
      return null;
    }
    try {
      writer.write(
          JsonApiDocument.withError(error),
          Integer.parseInt(Objects.requireNonNull(error.status())),
          response);
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

  private static @Nullable ErrorObject translate(Exception exception) {
    if (exception instanceof JsonApiQueryException query) {
      return ErrorObject.builder()
          .status("400")
          .code(query.diagnostic().name())
          .title("Invalid query parameter")
          .source(
              query.parameterName() == null ? null : ErrorSource.ofParameter(query.parameterName()))
          .build();
    }
    if (exception instanceof JsonApiDocumentReadException read) {
      return ErrorObject.builder()
          .status("400")
          .code(read.category().name())
          .title("Invalid JSON:API document")
          .build();
    }
    if (exception instanceof HttpMediaTypeNotAcceptableException) {
      return ErrorObject.builder()
          .status("406")
          .code("NOT_ACCEPTABLE")
          .title("Not Acceptable")
          .source(ErrorSource.builder().header("Accept").build())
          .build();
    }
    if (exception instanceof HttpMediaTypeNotSupportedException) {
      return ErrorObject.builder()
          .status("415")
          .code("UNSUPPORTED_MEDIA_TYPE")
          .title("Unsupported Media Type")
          .source(ErrorSource.builder().header("Content-Type").build())
          .build();
    }
    return null;
  }
}
