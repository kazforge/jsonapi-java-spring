package com.kazforge.jsonapi.spring.webmvc;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares the trusted target of an opted-in resource PATCH method. The ID is taken from Spring's
 * matched URI variables before the body is read. This annotation does not opt a controller into
 * JSON:API handling.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface JsonApiPatchTarget {
  /** JSON:API resource type addressed by this route. */
  String value();

  /** Name of the URI template variable containing the addressed resource ID. */
  String idVariable() default "id";
}
