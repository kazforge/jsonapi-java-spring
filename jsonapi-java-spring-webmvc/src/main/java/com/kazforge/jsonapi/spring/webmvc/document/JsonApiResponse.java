package com.kazforge.jsonapi.spring.webmvc.document;

import com.kazforge.jsonapi.core.model.JsonApiObject;
import com.kazforge.jsonapi.core.model.Links;
import com.kazforge.jsonapi.core.model.Meta;
import com.kazforge.jsonapi.document.DocumentEnvelope;
import java.util.Objects;

/**
 * A response containing a non-null resource or collection value and optional top-level document
 * members.
 *
 * <p>Absent members preserve runtime defaults; ordinary resource responses need no wrapper.
 */
public record JsonApiResponse<T>(T value, DocumentEnvelope envelope) {
  public JsonApiResponse {
    Objects.requireNonNull(value, "value");
    Objects.requireNonNull(envelope, "envelope");
  }

  public static <T> JsonApiResponse<T> of(T value) {
    return new JsonApiResponse<>(value, DocumentEnvelope.empty());
  }

  public JsonApiResponse<T> withMeta(Meta meta) {
    return new JsonApiResponse<>(value, envelope.withMeta(meta));
  }

  public JsonApiResponse<T> withLinks(Links links) {
    return new JsonApiResponse<>(value, envelope.withLinks(links));
  }

  public JsonApiResponse<T> withJsonApi(JsonApiObject jsonapi) {
    return new JsonApiResponse<>(value, envelope.withJsonApi(jsonapi));
  }
}
