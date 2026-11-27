package com.kazforge.jsonapi.bootconsumer;

import com.kazforge.jsonapi.annotation.JsonApiAttribute;
import com.kazforge.jsonapi.annotation.JsonApiId;
import com.kazforge.jsonapi.annotation.JsonApiResource;

@JsonApiResource(type = "boxes")
public record NullableBox<T>(@JsonApiId String id, @JsonApiAttribute T value) {}
