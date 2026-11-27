package com.kazforge.jsonapi.spring.webmvc.document;

import com.kazforge.jsonapi.core.model.JsonApiObject;
import com.kazforge.jsonapi.core.model.Links;
import com.kazforge.jsonapi.core.model.Meta;
import com.kazforge.jsonapi.document.DocumentEnvelope;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * A response containing an explicitly nullable single resource value and optional top-level
 * document members.
 *
 * <p>A null {@code value} writes a present primary {@code data} member with a JSON {@code null}
 * value. It does not mean bodyless: a plain Java {@code null} return keeps ordinary Spring bodyless
 * semantics and an application-owned {@code 204 No Content} response stays bodyless. A collection
 * is not a nullable single resource; use an ordinary collection response for resource arrays.
 *
 * <p>Absent envelope members preserve runtime defaults; ordinary resource responses need no
 * wrapper.
 */
public record JsonApiNullableResponse<T>(@Nullable T value, DocumentEnvelope envelope) {
  public JsonApiNullableResponse {
    Objects.requireNonNull(envelope, "envelope");
  }

  public static <T> JsonApiNullableResponse<T> of(@Nullable T value) {
    return new JsonApiNullableResponse<>(value, DocumentEnvelope.empty());
  }

  public JsonApiNullableResponse<T> withMeta(Meta meta) {
    return new JsonApiNullableResponse<>(value, envelope.withMeta(meta));
  }

  public JsonApiNullableResponse<T> withLinks(Links links) {
    return new JsonApiNullableResponse<>(value, envelope.withLinks(links));
  }

  public JsonApiNullableResponse<T> withJsonApi(JsonApiObject jsonapi) {
    return new JsonApiNullableResponse<>(value, envelope.withJsonApi(jsonapi));
  }
}
