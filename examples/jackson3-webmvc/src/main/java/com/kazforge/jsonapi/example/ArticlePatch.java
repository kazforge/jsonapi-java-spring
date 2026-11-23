package com.kazforge.jsonapi.example;

import com.kazforge.jsonapi.annotation.JsonApiAttribute;
import com.kazforge.jsonapi.annotation.JsonApiId;
import com.kazforge.jsonapi.annotation.JsonApiRelationship;
import com.kazforge.jsonapi.annotation.JsonApiResource;
import com.kazforge.jsonapi.core.model.ResourceIdentifier;
import com.kazforge.jsonapi.patch.PatchPresence;
import jakarta.validation.constraints.Pattern;

/** Omitted members preserve the current value; present members replace it, including with null. */
@JsonApiResource(type = "articles")
public record ArticlePatch(
    @JsonApiId String id,
    @JsonApiAttribute PatchPresence<@Pattern(regexp = "(?s).*\\P{javaWhitespace}.*") String> title,
    @JsonApiRelationship PatchPresence<ResourceIdentifier> editor) {}
