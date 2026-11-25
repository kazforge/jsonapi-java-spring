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
import com.kazforge.jsonapi.mapping.BoundResource;
import com.kazforge.jsonapi.patch.PatchCommand;
import com.kazforge.jsonapi.query.JsonApiQueryException;
import com.kazforge.jsonapi.query.QueryDiagnostic;
import com.kazforge.jsonapi.spring.webmvc.JsonApiPatchTarget;
import com.kazforge.jsonapi.spring.webmvc.error.JsonApiErrorResponse;
import com.kazforge.jsonapi.spring.webmvc.internal.JsonApiBodyBinding;
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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;
import org.springframework.core.GenericTypeResolver;
import org.springframework.core.ResolvableType;
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
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;

/**
 * Binds resource DTOs only for an opted-in resource handler dispatch.
 *
 * <p>POST uses resource creation binding. PATCH uses typed PATCH DTO binding, with patchable
 * members declared as {@code PatchPresence<T>}; an explicit {@code PatchCommand<T>} request body
 * instead selects command binding for the fully resolved resource type {@code T}. Resource PATCH
 * requires request-scoped endpoint identity from {@link JsonApiPatchTarget} or a constructor
 * callback. Neither path applies mutations or independently binds included resources.
 * Relationship-linkage handlers are not claimed.
 *
 * <p>Create and typed PATCH require a runtime supporting Core's source-aware operations. The bound
 * DTO is returned directly to Spring; only the current dispatch retains its attribution for
 * request-body validation errors. A failed binding is not retried.
 */
public final class JsonApiResourceHttpMessageConverter
    implements GenericHttpMessageConverter<Object> {
  private static final System.Logger LOGGER =
      System.getLogger(JsonApiResourceHttpMessageConverter.class.getName());
  private static final MediaType JSON_API = MediaType.parseMediaType("application/vnd.api+json");
  private final JsonApi runtime;
  private final Function<HttpServletRequest, @Nullable EndpointIdentity> endpointIdentity;

  /**
   * Creates a converter using the matched method's {@link JsonApiPatchTarget} for resource PATCH.
   */
  public JsonApiResourceHttpMessageConverter(JsonApi runtime) {
    this(runtime, JsonApiResourceHttpMessageConverter::matchedIdentity);
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

  private static @Nullable EndpointIdentity matchedIdentity(HttpServletRequest request) {
    if (!(request.getAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE)
        instanceof HandlerMethod method)) {
      return null;
    }
    JsonApiPatchTarget target = method.getMethodAnnotation(JsonApiPatchTarget.class);
    if (target != null
        && !target.value().isBlank()
        && request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE)
            instanceof Map<?, ?> variables
        && variables.get(target.idVariable()) instanceof String id
        && !id.isBlank()) {
      return new EndpointIdentity(target.value(), id);
    }
    return null;
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
    HttpServletRequest request = currentServletRequest();
    JsonApiRequestContext context = null;
    boolean patch = false;
    try {
      if (request != null) {
        context = JsonApiRequestContext.find(request);
        JsonApiBodyBinding.clear(request);
        if (context != null && HttpMethod.PATCH.equals(context.method())) {
          patch = true;
          return readPatch(type, contextClass, inputMessage, request, context);
        }
      }
      return JsonApiBodyBinding.remember(
          request, context, readCreate(type, contextClass, inputMessage));
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

  private BoundResource<?> readCreate(
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
        ? runtime.resources().readCreateWithSources(body, clazz)
        : runtime.resources().readCreateWithSources(body, targetType);
  }

  private Object readPatch(
      @Nullable Type type,
      @Nullable Class<?> contextClass,
      HttpInputMessage inputMessage,
      HttpServletRequest request,
      JsonApiRequestContext context)
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
    EndpointIdentity identity =
        Objects.requireNonNull(
            endpointIdentity.apply(request), "PATCH endpoint identity is required");
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
        : JsonApiBodyBinding.remember(
            request, context, runtime.patches().bindPatchWithSources(document, bindingType));
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
      if (isRejectedReadInclude(context, failure)) {
        throw new JsonApiQueryException(
            QueryDiagnostic.DISALLOWED_INCLUDE_PATH, "include", "Unsupported resource include");
      }
      LOGGER.log(
          System.Logger.Level.WARNING, "JSON:API resource response serialization failed", failure);
      throw new HttpMessageNotWritableException(
          "Could not serialize JSON:API resource response", failure);
    }
    JsonApiBodyWriter.write(serialized, outputMessage, servletResponse);
  }

  private static boolean isRejectedReadInclude(
      JsonApiRequestContext context, RuntimeException failure) {
    return (HttpMethod.GET.equals(context.method()) || HttpMethod.HEAD.equals(context.method()))
        && !context.representationSelection().includePaths().isEmpty()
        && failure instanceof JsonApiMappingException mapping
        && (mapping.diagnostic() == MappingDiagnostic.DENIED_RELATIONSHIP_INCLUDE
            || mapping.diagnostic() == MappingDiagnostic.INVALID_INCLUDE_PATH);
  }

  private static boolean supportsBodyType(@Nullable Type type, @Nullable Class<?> valueClass) {
    Type resolved = resolveTargetType(type, valueClass);
    Class<?> raw = resolved == null ? null : ResolvableType.forType(resolved).resolve();
    if (raw == null
        || CharSequence.class.isAssignableFrom(raw)
        || raw.isArray()
        || raw == JsonApiErrorResponse.class
        || valueClass == JsonApiErrorResponse.class
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
        && raw != JsonApiErrorResponse.class
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

  private static @Nullable HttpServletResponse currentServletResponse() {
    if (RequestContextHolder.getRequestAttributes()
        instanceof ServletRequestAttributes attributes) {
      return attributes.getResponse();
    }
    return null;
  }

  private static @Nullable JsonApiRequestContext currentContext() {
    HttpServletRequest request = currentServletRequest();
    return request == null ? null : JsonApiRequestContext.find(request);
  }

  private static @Nullable HttpServletRequest currentServletRequest() {
    return RequestContextHolder.getRequestAttributes()
            instanceof ServletRequestAttributes attributes
        ? attributes.getRequest()
        : null;
  }
}
