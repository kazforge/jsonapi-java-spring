package com.kazforge.jsonapi.spring.webmvc;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a controller or handler method as a JSON:API endpoint subject to JSON:API 1.1 media
 * negotiation.
 *
 * <p>Apply to JSON:API controllers only. The negotiation interceptor and response advice ignore
 * handlers without this annotation, so ordinary Spring MVC endpoints remain unaffected.
 *
 * <p>Do not narrow {@code consumes} or {@code produces} on annotated mappings. Handler-mapping
 * {@code consumes}/{@code produces} checks run before the negotiation interceptor, so declaring
 * them would bypass JSON:API {@code ext}/{@code profile} handling with Spring's default 415/406
 * behavior.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface JsonApiEndpoint {}
