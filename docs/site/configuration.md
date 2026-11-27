---
title: "Configuration"
description: "Customize Spring Boot auto-configuration, override runtimes and converters, inspect JsonApiRequestContext, and write raw JsonApiDocument responses."
---

# Customize runtimes, policies, converters, and MVC wiring {#configuration}

## Spring Boot auto-configuration and bean overrides {#spring-boot-auto-configuration}

In Boot-managed servlet MVC, the starter installs its **default** resource, relationship, and
error converters through `ServerHttpMessageConvertersCustomizer`. They do not affect `RestClient`
or `RestTemplate`.

Declaring a converter bean of one of those types (for example, `@Bean JsonApiResourceHttpMessageConverter`)
suppresses **only the matching starter customizer**. Boot collects `HttpMessageConverter` beans for
both its server and client builders, so Boot-configured clients may receive that bean too. For a
server-only replacement, use a [server customizer](#custom-converter-and-programmatic-patch-identity)
without exposing the converter as a bean.

| To customize | What to provide |
| --- | --- |
| Representation | A `RepresentationPolicy` bean for the default runtime; [include paths](queries-and-representation.md#include-related-resources) are denied by default. |
| Runtime | A `JsonApi` bean to replace the runtime built from Boot's `JsonMapper`. |
| Protocol errors | A `JsonApiProtocolExceptionResolver` bean. The starter uses it in the MVC resolver chain after application `@ExceptionHandler` methods. |
| Document writer or media negotiation advice | A bean of type `JsonApiDocumentResponseWriter` or `JsonApiMediaNegotiationAdvice` replaces the corresponding default. |

## Custom `JsonApi` runtime {#custom-jsonapi-runtime}

In Spring Boot, property naming strategies (such as `spring.jackson.property-naming-strategy`),
custom serializers/deserializers, and modules configured on Boot's
`tools.jackson.databind.json.JsonMapper` are inherited automatically by the default `JsonApi` bean.

Declare an explicit `@Bean JsonApi` when you need Core runtime settings beyond
`RepresentationPolicy`—such as a custom `IdentifierConverter`, `RelationshipLinkageMapper`,
`ResourceDecoratorRegistry`, or a default `jsonApiVersion("1.1")` on resource writes (see Core's
[Configuration guide](https://jsonapi.kazforge.com/core/0.5.1/configuration/)):

```java
import com.kazforge.jsonapi.api.JsonApi;
import com.kazforge.jsonapi.jackson3.JsonApiJackson3;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

@Configuration(proxyBeanMethods = false)
public class CustomRuntimeConfig {
    @Bean
    public JsonApi jsonApi(JsonMapper mapper) {
        return JsonApiJackson3.builder(mapper)
            .jsonApiVersion("1.1")
            .build();
    }
}
```

When you supply `JsonApi`, the starter does not apply a separate `RepresentationPolicy` bean to it.
To keep that policy, inject it into this factory method and call `.representationPolicy(policy)`
on the builder.
Custom `JsonApi` implementations must support Core's source-aware `readCreateWithSources` and
`bindPatchWithSources` operations for conventional resource create and typed PATCH binding.

## Custom converter and programmatic PATCH identity {#custom-converter-and-programmatic-patch-identity}

Most applications should annotate resource PATCH methods with `@JsonApiPatchTarget` (see
[Resources and PATCH](resources-and-patch.md#declare-trusted-route-identity)).

To resolve identity centrally, construct the converter with a callback instead. This example
recognizes only the article route; it takes the ID from the matched path, never from the body:

```java
import com.kazforge.jsonapi.api.JsonApi;
import com.kazforge.jsonapi.core.validation.EndpointIdentity;
import com.kazforge.jsonapi.spring.webmvc.document.JsonApiResourceHttpMessageConverter;
import java.util.Map;
import org.springframework.web.servlet.HandlerMapping;

// In your configuration class:
private static JsonApiResourceHttpMessageConverter articleConverter(JsonApi runtime) {
    return new JsonApiResourceHttpMessageConverter(runtime, request -> {
        Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        Object variables = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if ("/articles/{id}".equals(pattern) && variables instanceof Map<?, ?> path
                && path.get("id") instanceof String id && !id.isBlank()) {
            return new EndpointIdentity("articles", id);
        }
        return null;
    });
}
```

=== "Spring Boot"

    To keep the replacement server-only, declare a customizer bean. A customizer alone does
    not suppress the starter's default, so replace that converter **in the list** rather than
    adding a second converter for the same requests:

    ```java
    import org.springframework.boot.http.converter.autoconfigure.ServerHttpMessageConvertersCustomizer;
    import org.springframework.context.annotation.Bean;

    @Bean
    ServerHttpMessageConvertersCustomizer articleConverters(JsonApi runtime) {
        var replacement = articleConverter(runtime);
        return builder -> builder.configureMessageConvertersList(converters ->
            converters.replaceAll(converter -> converter instanceof JsonApiResourceHttpMessageConverter
                ? replacement : converter));
    }
    ```

    If client visibility is acceptable, expose the result of `articleConverter(runtime)` as a
    `JsonApiResourceHttpMessageConverter` bean instead of registering the customizer.

=== "Standalone WebMVC"

    In the [standalone setup](getting-started.md#configure-the-application), replace the resource
    converter registration in `configureMessageConverters` with:

    ```java
    builder.addCustomConverter(articleConverter(runtime));
    ```

The callback takes precedence over `@JsonApiPatchTarget` for every resource `PATCH` body read.
Handle each supported route; returning `null` produces a `500` configuration error.

## Manual WebMVC ownership in Spring Boot {#manual-webmvc-ownership}

- **Full `@EnableWebMvc` control:** Adding `@EnableWebMvc` to a Spring Boot application disables
  Boot's `WebMvcAutoConfiguration`, which also causes `JsonApiWebMvcAutoConfiguration` to back off.
  Register the JSON:API WebMVC components manually as shown in
  the **Standalone WebMVC** tab in [Getting started](getting-started.md#configure-the-application).
- **Keeping Boot MVC while wiring JSON:API manually:** To keep Boot's `WebMvcAutoConfiguration`
  active while registering the JSON:API components yourself, exclude
  `JsonApiWebMvcAutoConfiguration` as a unit:

```java
import com.kazforge.jsonapi.spring.boot.JsonApiWebMvcAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(exclude = JsonApiWebMvcAutoConfiguration.class)
public class Application {}
```

## Inspecting request context directly (`JsonApiRequestContext`) {#inspecting-request-context-directly}

To inspect the parsed query directly, replace your `GET /articles` handler with this version.
Other opted-in handlers can also accept `JsonApiRequestContext` or look it up with
`JsonApiRequestContext.find(httpServletRequest)`:

```java
import com.kazforge.jsonapi.spring.webmvc.request.JsonApiRequestContext;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;

@GetMapping
public List<Article> list(JsonApiRequestContext context) {
    var selection = context.representationSelection();
    var sort = context.query().sort();
    var rawFilters = context.query().filter();
    return articleService.findAll(rawFilters, sort, selection);
}
```

- `context.method()` returns Spring's `HttpMethod`.
- `context.primaryDataContext()` returns `PrimaryDataContext.RESOURCE` or
  `PrimaryDataContext.RELATIONSHIP`.
- `context.query()` returns the immutable `JsonApiQuery` (`selection()`, `sort()`, `filter()`,
  `page()`, `unprocessed()`).

## Advanced document responses (`JsonApiDocumentResponseWriter`) {#advanced-document-responses}

To distinguish an absent author from an empty HTTP body, write the document explicitly. The
writer serializes the document you give it. Your handler decides which query features it
supports and applies the requested fields when building the resource.

This endpoint returns `{"data":null}` for an article with no author. Otherwise, it returns a
`people` resource with `name` by default. `?fields[people]=` selects only `type` and `id`.
No include paths are supported: `?include=unknown` returns `400` with `source.parameter: "include"`.

```java
import com.kazforge.jsonapi.core.model.Attributes;
import com.kazforge.jsonapi.core.model.DocumentData;
import com.kazforge.jsonapi.core.model.JsonApiDocument;
import com.kazforge.jsonapi.core.model.ResourceObject;
import com.kazforge.jsonapi.query.JsonApiQueryException;
import com.kazforge.jsonapi.query.QueryDiagnostic;
import com.kazforge.jsonapi.spring.webmvc.JsonApiEndpoint;
import com.kazforge.jsonapi.spring.webmvc.document.JsonApiDocumentResponseWriter;
import com.kazforge.jsonapi.spring.webmvc.request.JsonApiRequestContext;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@JsonApiEndpoint
public class ArticleAuthorController {
    private final ArticleService articleService;
    private final JsonApiDocumentResponseWriter documentWriter;

    public ArticleAuthorController(
            ArticleService articleService,
            JsonApiDocumentResponseWriter documentWriter) {
        this.articleService = articleService;
        this.documentWriter = documentWriter;
    }

    @GetMapping("/articles/{id}/author")
    public void getAuthor(
            @PathVariable("id") String id,
            JsonApiRequestContext context,
            HttpServletResponse response) throws IOException {
        var selection = context.representationSelection();
        if (!selection.includePaths().isEmpty()) {
            throw new JsonApiQueryException(
                QueryDiagnostic.DISALLOWED_INCLUDE_PATH, "include", "Author has no includable relationships");
        }

        Person author = articleService.findById(id).author();
        DocumentData data = DocumentData.NullData.INSTANCE;
        if (author != null) {
            var person = ResourceObject.of("people", author.id());
            var fields = selection.fieldsets().get("people");
            if (fields == null || fields.contains("name")) {
                person = person.withAttributes(Attributes.ofAttributes(Map.of("name", author.name())));
            }
            data = new DocumentData.SingleResource(person);
        }

        // An explicit ?include= requests an empty included array.
        List<ResourceObject> included = selection.includeRequested() ? List.of() : null;
        var document = new JsonApiDocument(data, null, null, null, null, included, Map.of());
        documentWriter.write(document, HttpServletResponse.SC_OK, response);
    }
}
```

Boot supplies the writer bean. In standalone WebMVC, expose `new JsonApiDocumentResponseWriter(runtime)`
as a bean. The writer sets `Content-Type: application/vnd.api+json` and `Vary: Accept` and returns
a safe `500` if serialization fails. See Core's
[Relationships and documents guide](https://jsonapi.kazforge.com/core/0.5.1/relationships-and-documents/#preserve-document-state)
for other document shapes.
