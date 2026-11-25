package com.kazforge.jsonapi.spring.webmvc.request;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Binds an opted-in handler's parsed query to a record or single-public-constructor DTO. Use
 * Spring's {@code @BindParam("filter[status]")} on constructor parameters for exact opaque keys. A
 * simple reference type takes one occurrence; {@code List<T>} takes all. An unannotated {@code
 * JsonApiQuery} argument receives the original sort and selection. Add {@code @Valid} to the
 * controller parameter for MVC validation.
 */
@Documented
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface JsonApiQueryParams {}
