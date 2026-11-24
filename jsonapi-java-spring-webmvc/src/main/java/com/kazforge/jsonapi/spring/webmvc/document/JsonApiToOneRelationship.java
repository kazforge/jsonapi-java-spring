package com.kazforge.jsonapi.spring.webmvc.document;

import com.kazforge.jsonapi.core.model.ResourceIdentifier;
import org.jspecify.annotations.Nullable;

/**
 * Explicit to-one linkage, including an empty relationship.
 *
 * <p>Use as a relationship PATCH {@code @RequestBody} so explicit {@code data: null} is a non-null
 * clear operation and Spring still rejects a missing required body. For responses, identifiers need
 * no wrapper; {@code new JsonApiToOneRelationship(null)} emits {@code {"data":null}}. A plain Java
 * null response remains bodyless. This value does not resolve or mutate related resources.
 */
public record JsonApiToOneRelationship(@Nullable ResourceIdentifier identifier) {}
