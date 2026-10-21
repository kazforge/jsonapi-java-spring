package com.kazforge.jsonapi.spring.webmvc.document;

import com.kazforge.jsonapi.api.JsonApi;
import com.kazforge.jsonapi.api.ResourceWriteOptions;
import com.kazforge.jsonapi.core.aggregate.ValidationContext;
import com.kazforge.jsonapi.core.model.JsonApiDocument;
import com.kazforge.jsonapi.core.validation.DocumentUsage;
import com.kazforge.jsonapi.core.validation.EndpointIdentity;
import com.kazforge.jsonapi.core.validation.PrimaryDataContext;
import com.kazforge.jsonapi.diagnostic.JsonApiDocumentReadException;
import com.kazforge.jsonapi.diagnostic.JsonApiMappingException;
import com.kazforge.jsonapi.diagnostic.MappingDiagnostic;
import com.kazforge.jsonapi.document.DocumentEnvelope;
import com.kazforge.jsonapi.document.DocumentReadContext;
import com.kazforge.jsonapi.document.PrimaryDataKind;
import com.kazforge.jsonapi.patch.PatchCommand;
import com.kazforge.jsonapi.spring.webmvc.request.JsonApiRequestContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;
import org.springframework.core.GenericTypeResolver;
import org.springframework.core.ResolvableType;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.GenericHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConversionException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Binds resource DTOs only for an opted-in resource handler dispatch.
 *
 * <p>POST uses resource creation binding. PATCH uses typed PATCH DTO binding, with patchable
 * members declared as {@code PatchPresence<T>}; an explicit {@code PatchCommand<T>} request body
 * instead selects command binding for the fully resolved resource type {@code T}. Resource PATCH
 * requires request-scoped endpoint identity through the constructor callback. Neither path applies
 * mutations or independently binds included resources. Relationship-linkage handlers are not
 * claimed.
 */
public final class JsonApiResourceHttpMessageConverter
    implements GenericHttpMessageConverter<Object> {
  private static final System.Logger LOGGER =
      System.getLogger(JsonApiResourceHttpMessageConverter.class.getName());
  private static final MediaType JSON_API = MediaType.parseMediaType("application/vnd.api+json");
  private final JsonApi runtime;
  private final Function<HttpServletRequest, @Nullable EndpointIdentity> endpointIdentity;

  /**
   * Creates a converter for resource creation and responses; PATCH requires an identity callback.
   */
  public JsonApiResourceHttpMessageConverter(JsonApi runtime) {
    this(runtime, request -> null);
  }

  /**
   * Creates a converter with application-owned route identity resolution for resource PATCH.
   *
   * <p>The callback runs once per PATCH body read, never for POST or responses. It must return the
   * addressed resource's type and ID from trusted route state, not from the body. A null result is
   * a server configuration failure, not permission to skip identity validation. No request state is
   * retained by the converter or configured on the runtime.
   */
  public JsonApiResourceHttpMessageConverter(
      JsonApi runtime, Function<HttpServletRequest, @Nullable EndpointIdentity> endpointIdentity) {
    this.runtime = Objects.requireNonNull(runtime, "runtime");
    this.endpointIdentity = Objects.requireNonNull(endpointIdentity, "endpointIdentity");
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
        && (HttpMethod.POST.equals(context.method()) || HttpMethod.PATCH.equals(context.method()))
        && context.primaryDataContext() == PrimaryDataContext.RESOURCE
        && supportsRequestTarget(type, contextClass)
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
    JsonApiRequestContext context = currentContext();
    boolean patch = context != null && HttpMethod.PATCH.equals(context.method());
    try {
      if (patch) {
        return readPatch(type, contextClass, inputMessage);
      }
      return readCreate(type, contextClass, inputMessage);
    } catch (JsonApiDocumentReadException failure) {
      throw new HttpMessageNotReadableException(
          "Invalid JSON:API resource document", failure, inputMessage);
    } catch (JsonApiMappingException failure) {
      if (isClientInputDiagnostic(failure.diagnostic())
          || (patch && failure.diagnostic() == MappingDiagnostic.UNKNOWN_PATCH_MEMBER)) {
        throw new HttpMessageNotReadableException(
            "Invalid JSON:API resource document", failure, inputMessage);
      }
      LOGGER.log(System.Logger.Level.WARNING, "JSON:API resource request mapping failed", failure);
      throw new HttpMessageConversionException("Resource request binding failed", failure);
    } catch (HttpMessageConversionException failure) {
      throw failure;
    } catch (RuntimeException failure) {
      LOGGER.log(System.Logger.Level.WARNING, "JSON:API resource request binding failed", failure);
      throw new HttpMessageConversionException("Resource request binding failed", failure);
    }
  }

  private Object readCreate(
      @Nullable Type type, @Nullable Class<?> contextClass, HttpInputMessage inputMessage)
      throws IOException {
    Type targetType = resolveTargetType(type, contextClass);
    if (targetType != null && isBulkType(targetType)) {
      throw new HttpMessageNotReadableException(
          "Bulk resource creation is not supported", inputMessage);
    }
    if (targetType == null || hasUnresolvedType(targetType)) {
      throw new IllegalStateException("Unresolved resource request type");
    }
    if (!supportsRequestTarget(targetType, null)) {
      throw new HttpMessageNotReadableException(
          "Unsupported JSON:API resource request target", inputMessage);
    }
    InputStream body = inputMessage.getBody();
    return targetType instanceof Class<?> clazz
        ? runtime.resources().readCreate(body, clazz)
        : runtime.resources().readCreate(body, targetType);
  }

  private Object readPatch(
      @Nullable Type type, @Nullable Class<?> contextClass, HttpInputMessage inputMessage)
      throws IOException {
    Type targetType = resolveTargetType(type, contextClass);
    if (targetType == null
        || hasUnresolvedType(targetType)
        || hasWildcard(targetType)
        || isBulkType(targetType)
        || !supportsRequestTarget(targetType, null)) {
      throw new IllegalStateException("Unsupported or unresolved PATCH request type");
    }
    boolean command = ResolvableType.forType(targetType).resolve() == PatchCommand.class;
    Type bindingType = targetType;
    if (command) {
      bindingType = ResolvableType.forType(targetType).getGeneric(0).getType();
      if (isBulkType(bindingType)) {
        throw new IllegalStateException("PatchCommand requires a single resource type");
      }
    }
    ServletRequestAttributes attributes =
        (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
    EndpointIdentity identity =
        Objects.requireNonNull(
            endpointIdentity.apply(attributes.getRequest()), "PATCH endpoint identity is required");
    ValidationContext validation =
        ValidationContext.defaults()
            .withDocumentUsage(DocumentUsage.UPDATE_REQUEST)
            .withExpectedEndpointIdentity(identity);
    JsonApiDocument document =
        runtime
            .documents()
            .read(
                inputMessage.getBody(),
                new DocumentReadContext(validation, PrimaryDataKind.RESOURCE));
    return command
        ? runtime.patches().bindCommand(document, bindingType)
        : runtime.patches().bindPatch(document, bindingType);
  }

  private static boolean hasWildcard(Type type) {
    if (type instanceof WildcardType) {
      return true;
    }
    if (type instanceof ParameterizedType parameterized) {
      for (Type argument : parameterized.getActualTypeArguments()) {
        if (hasWildcard(argument)) {
          return true;
        }
      }
    }
    return false;
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

  private static boolean supportsRequestTarget(
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
