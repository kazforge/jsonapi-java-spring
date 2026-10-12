package com.kazforge.jsonapi.spring.webmvc.request;

import com.kazforge.jsonapi.core.validation.PrimaryDataContext;
import com.kazforge.jsonapi.query.JsonApiQuery;
import com.kazforge.jsonapi.representation.RepresentationSelection;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpMethod;

/**
 * Immutable operation and requested query state for one JSON:API handler dispatch.
 *
 * <p>The method and endpoint role are independent: relationship POST/DELETE are linkage mutations,
 * not resource creation/deletion. This value neither binds bodies nor executes queries. Route
 * identity, authorization, and effective representation policy remain application-owned.
 */
public record JsonApiRequestContext(
    HttpMethod method, PrimaryDataContext primaryDataContext, JsonApiQuery query) {
  static final String ATTRIBUTE = JsonApiRequestContext.class.getName();

  public JsonApiRequestContext {
    Objects.requireNonNull(method, "method");
    Objects.requireNonNull(primaryDataContext, "primaryDataContext");
    Objects.requireNonNull(query, "query");
  }

  /** Returns the parser's neutral representation selection, not an authorization decision. */
  public RepresentationSelection representationSelection() {
    return query.selection();
  }

  /** Returns the current dispatch's context, or null if no JSON:API context was prepared. */
  public static @Nullable JsonApiRequestContext find(HttpServletRequest request) {
    Object value = request.getAttribute(ATTRIBUTE);
    return value instanceof JsonApiRequestContext context ? context : null;
  }
}
