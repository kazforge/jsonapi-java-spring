package com.kazforge.jsonapi.example;

import com.kazforge.jsonapi.annotation.JsonApiAttribute;
import com.kazforge.jsonapi.annotation.JsonApiId;
import com.kazforge.jsonapi.annotation.JsonApiRelationship;
import com.kazforge.jsonapi.annotation.JsonApiResource;
import com.kazforge.jsonapi.core.model.ResourceIdentifier;
import org.jspecify.annotations.Nullable;

/** A create request may omit the ID; resource responses always supply one. */
@JsonApiResource(type = "articles")
public record Article(
    @JsonApiId @Nullable String id,
    @JsonApiAttribute @Nullable String title,
    @JsonApiRelationship @Nullable ResourceIdentifier editor) {
  public Article(@Nullable String id, @Nullable String title) {
    this(id, title, null);
  }
}
