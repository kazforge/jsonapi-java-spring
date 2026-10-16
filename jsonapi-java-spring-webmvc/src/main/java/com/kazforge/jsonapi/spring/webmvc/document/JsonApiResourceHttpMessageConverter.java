package com.kazforge.jsonapi.spring.webmvc.document;

import com.kazforge.jsonapi.api.JsonApi;
import com.kazforge.jsonapi.api.ResourceWriteOptions;
import com.kazforge.jsonapi.core.model.JsonApiDocument;
import com.kazforge.jsonapi.core.validation.PrimaryDataContext;
import com.kazforge.jsonapi.diagnostic.JsonApiDocumentReadException;
import com.kazforge.jsonapi.diagnostic.JsonApiMappingException;
import com.kazforge.jsonapi.diagnostic.MappingDiagnostic;
import com.kazforge.jsonapi.document.DocumentEnvelope;
import com.kazforge.jsonapi.spring.webmvc.request.JsonApiRequestContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.springframework.core.GenericTypeResolver;
import org.springframework.core.ResolvableType;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.GenericHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConversionException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** Binds resource DTOs only for an opted-in resource handler dispatch. */
public final class JsonApiResourceHttpMessageConverter
    implements GenericHttpMessageConverter<Object> {
  private static final System.Logger LOGGER =
      System.getLogger(JsonApiResourceHttpMessageConverter.class.getName());
  private static final MediaType JSON_API = MediaType.parseMediaType("application/vnd.api+json");
  private final JsonApi runtime;

  public JsonApiResourceHttpMessageConverter(JsonApi runtime) {
    this.runtime = Objects.requireNonNull(runtime, "runtime");
  }

  @Override
  public boolean canRead(Class<?> clazz, @Nullable MediaType mediaType) {
    return canRead(clazz, clazz, mediaType);
  }

  @Override
  public boolean canRead(
      @Nullable Type type, @Nullable Class<?> contextClass, @Nullable MediaType mediaType) {
    JsonApiRequestContext context = currentContext();
    return context != null
        && org.springframework.http.HttpMethod.POST.equals(context.method())
        && context.primaryDataContext() == PrimaryDataContext.RESOURCE
        && supportsCreateTarget(type, contextClass)
        && mediaType != null
        && JSON_API.isCompatibleWith(mediaType);
  }

  @Override
  public Object read(Class<?> clazz, HttpInputMessage inputMessage) throws IOException {
    return read(clazz, clazz, inputMessage);
  }

  @Override
  public Object read(
      @Nullable Type type, @Nullable Class<?> contextClass, HttpInputMessage inputMessage)
      throws IOException {
    Type targetType = resolveTargetType(type, contextClass);
    if (targetType == null || hasUnresolvedType(targetType) || isBulkType(targetType)) {
      if (targetType != null && isBulkType(targetType)) {
        throw new HttpMessageNotReadableException(
            "Bulk resource creation is not supported", inputMessage);
      }
      IllegalStateException failure = new IllegalStateException("Unresolved resource request type");
      LOGGER.log(
          System.Logger.Level.WARNING,
          "JSON:API resource request type could not be resolved",
          failure);
      throw new HttpMessageConversionException("Resource request binding failed", failure);
    }
    if (!supportsCreateTarget(targetType, null)) {
      throw new HttpMessageNotReadableException(
          "Unsupported JSON:API resource request target", inputMessage);
    }
    try {
      InputStream body = inputMessage.getBody();
      if (targetType instanceof Class<?> clazz) {
        return runtime.resources().readCreate(body, clazz);
      }
      return runtime.resources().readCreate(body, targetType);
    } catch (JsonApiDocumentReadException failure) {
      throw new HttpMessageNotReadableException(
          "Invalid JSON:API resource document", failure, inputMessage);
    } catch (JsonApiMappingException failure) {
      if (isClientInputDiagnostic(failure.diagnostic())) {
        throw new HttpMessageNotReadableException(
            "Invalid JSON:API resource document", failure, inputMessage);
      }
      LOGGER.log(System.Logger.Level.WARNING, "JSON:API resource request mapping failed", failure);
      throw new HttpMessageConversionException("Resource request binding failed", failure);
    } catch (RuntimeException failure) {
      LOGGER.log(System.Logger.Level.WARNING, "JSON:API resource request binding failed", failure);
      throw new HttpMessageConversionException("Resource request binding failed", failure);
    }
  }

  @Override
  public boolean canWrite(Class<?> clazz, @Nullable MediaType mediaType) {
    return canWrite(clazz, clazz, mediaType);
  }

  @Override
  public boolean canWrite(
      @Nullable Type type, @Nullable Class<?> valueClass, @Nullable MediaType mediaType) {
    JsonApiRequestContext context = currentContext();
    // ResponseEntity may set a conflicting content type before converter selection; advice runs
    // later.
    return context != null
        && context.primaryDataContext() == PrimaryDataContext.RESOURCE
        && supportsBodyType(type, valueClass);
  }

  @Override
  public List<MediaType> getSupportedMediaTypes() {
    return List.of(JSON_API);
  }

  @Override
  public void write(Object body, @Nullable MediaType contentType, HttpOutputMessage outputMessage)
      throws IOException {
    write(body, body.getClass(), contentType, outputMessage);
  }

  @Override
  public void write(
      Object body,
      @Nullable Type type,
      @Nullable MediaType contentType,
      HttpOutputMessage outputMessage)
      throws IOException {
    JsonApiRequestContext context = currentContext();
    if (context == null) {
      throw new HttpMessageNotWritableException("No JSON:API resource request context");
    }
    HttpServletResponse servletResponse = currentServletResponse();
    if (servletResponse != null && servletResponse.isCommitted()) {
      return;
    }
    Object value = body;
    DocumentEnvelope envelope = DocumentEnvelope.empty();
    if (body instanceof JsonApiResponse<?>(var responseValue, var responseEnvelope)) {
      value = responseValue;
      envelope = responseEnvelope;
    }
    ResourceWriteOptions options =
        new ResourceWriteOptions(envelope, context.representationSelection());
    ByteArrayOutputStream serialized = new ByteArrayOutputStream();
    try {
      if (value instanceof Iterable<?> values) {
        runtime.resources().writeMany(values, options, serialized);
      } else {
        runtime.resources().writeOne(value, options, serialized);
      }
    } catch (RuntimeException failure) {
      LOGGER.log(
          System.Logger.Level.WARNING, "JSON:API resource response serialization failed", failure);
      throw new HttpMessageNotWritableException(
          "Could not serialize JSON:API resource response", failure);
    }
    if (servletResponse != null && servletResponse.isCommitted()) {
      return;
    }
    if (servletResponse != null) {
      resetUncommittedResponse(servletResponse);
      servletResponse.setCharacterEncoding((String) null);
      servletResponse.setHeader(HttpHeaders.CONTENT_TYPE, JSON_API.toString());
    }
    outputMessage.getHeaders().setContentType(JSON_API);
    outputMessage.getHeaders().setContentLength(serialized.size());
    outputMessage.getBody().write(serialized.toByteArray());
  }

  private static boolean supportsBodyType(@Nullable Type type, @Nullable Class<?> valueClass) {
    Type resolved = resolveTargetType(type, valueClass);
    Class<?> raw = resolved == null ? null : ResolvableType.forType(resolved).resolve();
    if (raw == null
        || CharSequence.class.isAssignableFrom(raw)
        || raw.isArray()
        || JsonApiDocument.class.isAssignableFrom(raw)) {
      return false;
    }
    if (raw == JsonApiResponse.class) {
      Type valueType =
          resolved instanceof ParameterizedType p ? p.getActualTypeArguments()[0] : Object.class;
      return supportsBodyType(valueType, null);
    }
    return Iterable.class.isAssignableFrom(raw) || !raw.isPrimitive();
  }

  private static boolean supportsCreateTarget(
      @Nullable Type type, @Nullable Class<?> contextClass) {
    Type resolved = resolveTargetType(type, contextClass);
    Class<?> raw = resolved == null ? null : ResolvableType.forType(resolved).resolve();
    return raw != null
        && raw != byte[].class
        && !CharSequence.class.isAssignableFrom(raw)
        && !JsonApiDocument.class.isAssignableFrom(raw);
  }

  private static @Nullable Type resolveTargetType(
      @Nullable Type type, @Nullable Class<?> contextClass) {
    return type == null || contextClass == null
        ? type
        : GenericTypeResolver.resolveType(type, contextClass);
  }

  private static boolean hasUnresolvedType(Type type) {
    return type instanceof TypeVariable<?>
        || ResolvableType.forType(type).hasUnresolvableGenerics();
  }

  private static boolean isBulkType(Type type) {
    Class<?> raw = ResolvableType.forType(type).resolve();
    return raw != null && (Iterable.class.isAssignableFrom(raw) || raw.isArray());
  }

  private static boolean isClientInputDiagnostic(MappingDiagnostic diagnostic) {
    return switch (diagnostic) {
      case RESOURCE_TYPE_MISMATCH,
          RELATIONSHIP_CARDINALITY_MISMATCH,
          IDENTIFIER_CONVERSION_FAILED ->
          true;
      default -> false;
    };
  }

  private static void resetUncommittedResponse(HttpServletResponse response) {
    if (response.isCommitted()) {
      return;
    }
    int status = response.getStatus();
    Map<String, List<String>> headers = new LinkedHashMap<>();
    for (String name : response.getHeaderNames()) {
      headers.put(name, List.copyOf(response.getHeaders(name)));
    }
    response.reset();
    response.setStatus(status);
    headers.forEach((name, values) -> values.forEach(value -> response.addHeader(name, value)));
  }

  private static @Nullable HttpServletResponse currentServletResponse() {
    if (RequestContextHolder.getRequestAttributes()
        instanceof ServletRequestAttributes attributes) {
      return attributes.getResponse();
    }
    return null;
  }

  private static @Nullable JsonApiRequestContext currentContext() {
    if (!(RequestContextHolder.getRequestAttributes()
        instanceof ServletRequestAttributes attributes)) {
      return null;
    }
    HttpServletRequest request = attributes.getRequest();
    return JsonApiRequestContext.find(request);
  }
}
