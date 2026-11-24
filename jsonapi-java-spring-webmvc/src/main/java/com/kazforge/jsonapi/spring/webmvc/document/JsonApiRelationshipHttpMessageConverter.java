package com.kazforge.jsonapi.spring.webmvc.document;

import com.kazforge.jsonapi.api.JsonApi;
import com.kazforge.jsonapi.core.model.JsonApiDocument;
import com.kazforge.jsonapi.core.model.ResourceIdentifier;
import com.kazforge.jsonapi.core.validation.PrimaryDataContext;
import com.kazforge.jsonapi.diagnostic.JsonApiDocumentReadException;
import com.kazforge.jsonapi.diagnostic.JsonApiMappingException;
import com.kazforge.jsonapi.diagnostic.MappingDiagnostic;
import com.kazforge.jsonapi.query.JsonApiQueryException;
import com.kazforge.jsonapi.query.QueryDiagnostic;
import com.kazforge.jsonapi.spring.webmvc.error.JsonApiErrorResponse;
import com.kazforge.jsonapi.spring.webmvc.request.JsonApiRequestContext;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Objects;
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

/**
 * Linkage-only binding for {@code @JsonApiEndpoint(primaryDataContext = RELATIONSHIP)} handlers.
 *
 * <p>Register ahead of Spring's defaults alongside the resource and error converters. Request
 * cardinality comes from the resolved controller parameter: {@link JsonApiToOneRelationship} for
 * PATCH replacement/clear, or {@code List<ResourceIdentifier>} for PATCH replacement, POST
 * additions, and DELETE removals. Other request declarations or methods are configuration errors.
 * Each body is read once through the caller-supplied neutral relationship facet.
 *
 * <p>Responses accept a {@link ResourceIdentifier}, {@link JsonApiToOneRelationship}, or {@code
 * List<ResourceIdentifier>}. Plain null and bodyless responses retain Spring semantics. Requested
 * nonempty include paths cannot be fulfilled by this linkage-only path and produce 400.
 * Applications own query support policy and may use explicit document transport for advanced
 * responses. No DTO hydration, validation attribution, or persistence mutation occurs here.
 */
public final class JsonApiRelationshipHttpMessageConverter
    implements GenericHttpMessageConverter<Object> {
  private static final System.Logger LOGGER =
      System.getLogger(JsonApiRelationshipHttpMessageConverter.class.getName());
  private static final MediaType JSON_API = MediaType.parseMediaType("application/vnd.api+json");
  private final JsonApi runtime;

  public JsonApiRelationshipHttpMessageConverter(JsonApi runtime) {
    this.runtime = Objects.requireNonNull(runtime, "runtime");
  }

  @Override
  public boolean canRead(Class<?> clazz, @Nullable MediaType mediaType) {
    return canRead(clazz, clazz, mediaType);
  }

  @Override
  public boolean canRead(
      @Nullable Type type, @Nullable Class<?> contextClass, @Nullable MediaType mediaType) {
    return relationshipContext() != null
        && isApplicationBody(resolve(type, contextClass))
        && mediaType != null
        && JSON_API.isCompatibleWith(mediaType);
  }

  @Override
  public Object read(Class<?> clazz, HttpInputMessage input) throws IOException {
    return read(clazz, clazz, input);
  }

  @Override
  public Object read(@Nullable Type type, @Nullable Class<?> contextClass, HttpInputMessage input)
      throws IOException {
    JsonApiRequestContext context = relationshipContext();
    if (context == null) {
      throw new HttpMessageConversionException("No JSON:API relationship request context");
    }
    Type target = resolve(type, contextClass);
    boolean one = target == JsonApiToOneRelationship.class;
    boolean many = isIdentifierList(target);
    boolean patch = HttpMethod.PATCH.equals(context.method());
    if (!(one && patch)
        && !(many
            && (patch
                || HttpMethod.POST.equals(context.method())
                || HttpMethod.DELETE.equals(context.method())))) {
      throw new HttpMessageConversionException(
          "Relationship request requires to-one PATCH or to-many PATCH/POST/DELETE");
    }
    requireLinkageSelection(context);
    try {
      return one
          ? new JsonApiToOneRelationship(runtime.relationships().readToOne(input.getBody()))
          : runtime.relationships().readToMany(input.getBody());
    } catch (JsonApiDocumentReadException failure) {
      throw new HttpMessageNotReadableException(
          "Invalid JSON:API relationship document", failure, input);
    } catch (JsonApiMappingException failure) {
      if (failure.diagnostic() == MappingDiagnostic.RESOURCE_TYPE_MISMATCH
          || failure.diagnostic() == MappingDiagnostic.RELATIONSHIP_CARDINALITY_MISMATCH) {
        throw new HttpMessageNotReadableException(
            "Invalid JSON:API relationship document", failure, input);
      }
      throw bindingFailure(failure);
    } catch (RuntimeException failure) {
      throw bindingFailure(failure);
    }
  }

  private static HttpMessageConversionException bindingFailure(RuntimeException failure) {
    LOGGER.log(
        System.Logger.Level.WARNING, "JSON:API relationship request binding failed", failure);
    return new HttpMessageConversionException("Relationship request binding failed", failure);
  }

  @Override
  public boolean canWrite(Class<?> clazz, @Nullable MediaType mediaType) {
    return canWrite(clazz, clazz, mediaType);
  }

  @Override
  public boolean canWrite(
      @Nullable Type type, @Nullable Class<?> valueClass, @Nullable MediaType mediaType) {
    return relationshipContext() != null
        && isApplicationBody(resolve(type, valueClass))
        && (valueClass == null || isApplicationBody(valueClass));
  }

  @Override
  public List<MediaType> getSupportedMediaTypes() {
    return List.of(JSON_API);
  }

  @Override
  public void write(Object body, @Nullable MediaType contentType, HttpOutputMessage output)
      throws IOException {
    write(body, body.getClass(), contentType, output);
  }

  @Override
  public void write(
      Object body, @Nullable Type type, @Nullable MediaType contentType, HttpOutputMessage output)
      throws IOException {
    JsonApiRequestContext context = relationshipContext();
    if (context == null) {
      throw new HttpMessageNotWritableException("No JSON:API relationship request context");
    }
    ServletRequestAttributes attributes = currentAttributes();
    var response = attributes == null ? null : attributes.getResponse();
    if (response != null && (response.isCommitted() || response.getStatus() == 204)) {
      return;
    }
    requireLinkageSelection(context);
    ByteArrayOutputStream serialized = new ByteArrayOutputStream();
    try {
      switch (body) {
        case JsonApiToOneRelationship(var identifier) ->
            runtime.relationships().writeToOne(identifier, serialized);
        case ResourceIdentifier identifier ->
            runtime.relationships().writeToOne(identifier, serialized);
        case List<?> values when isIdentifierList(type) -> {
          // Check every element before delegating; invalid server values are not input errors.
          List<ResourceIdentifier> identifiers =
              values.stream().map(ResourceIdentifier.class::cast).toList();
          runtime.relationships().writeToMany(identifiers, serialized);
        }
        default -> throw new IllegalStateException("Unsupported relationship response type");
      }
    } catch (RuntimeException failure) {
      LOGGER.log(
          System.Logger.Level.WARNING,
          "JSON:API relationship response serialization failed",
          failure);
      throw new HttpMessageNotWritableException(
          "Could not serialize JSON:API relationship response", failure);
    }
    JsonApiBodyWriter.write(serialized, output, response);
  }

  private static void requireLinkageSelection(JsonApiRequestContext context) {
    if (!context.representationSelection().includePaths().isEmpty()) {
      throw new JsonApiQueryException(
          QueryDiagnostic.DISALLOWED_INCLUDE_PATH,
          "include",
          "Conventional relationship binding does not support included resources");
    }
  }

  private static boolean isIdentifierList(@Nullable Type type) {
    return type instanceof ParameterizedType parameterized
        && parameterized.getRawType() == List.class
        && parameterized.getActualTypeArguments()[0] == ResourceIdentifier.class;
  }

  private static boolean isApplicationBody(@Nullable Type type) {
    Class<?> raw = type == null ? null : ResolvableType.forType(type).resolve();
    return raw != null
        && raw != byte[].class
        && !CharSequence.class.isAssignableFrom(raw)
        && !JsonApiDocument.class.isAssignableFrom(raw)
        && raw != JsonApiErrorResponse.class;
  }

  private static @Nullable Type resolve(@Nullable Type type, @Nullable Class<?> contextClass) {
    return type == null || contextClass == null
        ? type
        : GenericTypeResolver.resolveType(type, contextClass);
  }

  private static @Nullable JsonApiRequestContext relationshipContext() {
    ServletRequestAttributes attributes = currentAttributes();
    JsonApiRequestContext context =
        attributes == null ? null : JsonApiRequestContext.find(attributes.getRequest());
    return context != null && context.primaryDataContext() == PrimaryDataContext.RELATIONSHIP
        ? context
        : null;
  }

  private static @Nullable ServletRequestAttributes currentAttributes() {
    return RequestContextHolder.getRequestAttributes()
            instanceof ServletRequestAttributes attributes
        ? attributes
        : null;
  }
}
