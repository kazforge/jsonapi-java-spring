package com.kazforge.jsonapi.spring.webmvc;

import com.kazforge.jsonapi.core.validation.PrimaryDataContext;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a controller or handler method for JSON:API 1.1 media negotiation and operation-aware
 * request/query context.
 *
 * <p>Apply to JSON:API controllers only. The negotiation interceptor ignores handlers without this
 * annotation, so ordinary Spring MVC endpoints remain unaffected.
 *
 * <p>Do not narrow {@code consumes} or {@code produces} on annotated mappings. Handler-mapping
 * {@code consumes}/{@code produces} checks run before the negotiation interceptor, so declaring
 * them would bypass JSON:API {@code ext}/{@code profile} handling with Spring's default 415/406
 * behavior.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface JsonApiEndpoint {
  /**
   * Endpoint role, independent of the HTTP method. Related-resource URLs use {@code RESOURCE};
   * dedicated relationship-linkage URLs use {@code RELATIONSHIP}. A method annotation replaces
   * controller metadata, including this member's default.
   */
  PrimaryDataContext primaryDataContext() default PrimaryDataContext.RESOURCE;
}
