---
title: "Getting started"
description: "Install jsonapi-java-spring in Spring Boot or standalone Spring WebMVC and build your first JSON:API controller."
---

# Install and create your first JSON:API endpoint {#getting-started}

Build a controller that accepts `POST /articles` and serves `GET /articles/{id}`.
Choose your setup below; both use the same resource records and controller.

## 1. Set up your application {#configure-the-application}

=== "Spring Boot"

    In a Spring Boot **4.1.x** (minimum **4.1.2**) servlet MVC application, add the starter:

    <!-- x-release-please-start-version -->

    === "Gradle Kotlin DSL"

        ```kotlin
        dependencies {
            implementation("com.kazforge:jsonapi-java-spring-boot-starter:0.7.0")
        }
        ```

    === "Maven"

        ```xml
        <dependency>
            <groupId>com.kazforge</groupId>
            <artifactId>jsonapi-java-spring-boot-starter</artifactId>
            <version>0.7.0</version>
        </dependency>
        ```

    <!-- x-release-please-end -->

    The starter includes WebMVC, validation, and the Core **0.5.1** Jackson 3 runtime. It builds
    `JsonApi` from Boot's `JsonMapper` and installs the endpoint and PATCH validation support.
    Its default converters go on the server builder only; `RestClient` and `RestTemplate` are
    unaffected. No `WebMvcConfigurer` is needed. See [Configuration](configuration.md#spring-boot-auto-configuration)
    if you supply your own converters or runtime.

=== "Standalone WebMVC"

    In a Spring Framework **7.0.x** (minimum **7.0.9**) servlet MVC application, add the adapter:

    <!-- x-release-please-start-version -->

    === "Gradle Kotlin DSL"

        ```kotlin
        dependencies {
            implementation("com.kazforge:jsonapi-java-spring-webmvc:0.7.0")
        }
        ```

    === "Maven"

        ```xml
        <dependency>
            <groupId>com.kazforge</groupId>
            <artifactId>jsonapi-java-spring-webmvc</artifactId>
            <version>0.7.0</version>
        </dependency>
        ```

    <!-- x-release-please-end -->

    Also add `com.kazforge:jsonapi-java-jackson3:0.5.1` for the
    [Core runtime](https://jsonapi.kazforge.com/core/0.5.1/getting-started/) used below, and
    a Jakarta Validation 3.1 provider such as Hibernate Validator for `@Valid`. Hibernate Validator's
    default message interpolator needs a Jakarta EL implementation. If your servlet container does
    not provide one, add `org.glassfish.expressly:expressly` at runtime, as the standalone example does.

    Expose `JsonApi` as a bean and register the MVC components:

    ```java
    import com.kazforge.jsonapi.api.JsonApi;
    import com.kazforge.jsonapi.jackson3.JsonApiJackson3;
    import com.kazforge.jsonapi.spring.webmvc.document.JsonApiRelationshipHttpMessageConverter;
    import com.kazforge.jsonapi.spring.webmvc.document.JsonApiResourceHttpMessageConverter;
    import com.kazforge.jsonapi.spring.webmvc.error.JsonApiErrorHttpMessageConverter;
    import com.kazforge.jsonapi.spring.webmvc.error.JsonApiProtocolExceptionResolver;
    import com.kazforge.jsonapi.spring.webmvc.media.JsonApiMediaNegotiationAdvice;
    import com.kazforge.jsonapi.spring.webmvc.media.JsonApiMediaNegotiationInterceptor;
    import com.kazforge.jsonapi.spring.webmvc.request.JsonApiQueryParamsArgumentResolver;
    import com.kazforge.jsonapi.spring.webmvc.request.JsonApiRequestContextArgumentResolver;
    import com.kazforge.jsonapi.spring.webmvc.request.JsonApiRequestContextInterceptor;
    import com.kazforge.jsonapi.spring.webmvc.validation.PatchPresenceValueExtractor;
    import java.util.List;
    import org.springframework.context.annotation.Bean;
    import org.springframework.context.annotation.Configuration;
    import org.springframework.http.converter.HttpMessageConverters;
    import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
    import org.springframework.web.method.support.HandlerMethodArgumentResolver;
    import org.springframework.web.servlet.HandlerExceptionResolver;
    import org.springframework.web.servlet.config.annotation.EnableWebMvc;
    import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
    import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
    import tools.jackson.databind.json.JsonMapper;

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    public class JsonApiWebMvcConfig implements WebMvcConfigurer {
        private final JsonApi runtime;

        public JsonApiWebMvcConfig(JsonApi runtime) {
            this.runtime = runtime;
        }

        @Bean
        public static JsonApi jsonApi() {
            return JsonApiJackson3.jsonApi(JsonMapper.builder().build());
        }

        @Bean
        public JsonApiMediaNegotiationAdvice jsonApiMediaNegotiationAdvice() {
            return new JsonApiMediaNegotiationAdvice();
        }

        @Override
        public void configureMessageConverters(HttpMessageConverters.ServerBuilder builder) {
            builder.addCustomConverter(new JsonApiErrorHttpMessageConverter(runtime));
            builder.addCustomConverter(new JsonApiRelationshipHttpMessageConverter(runtime));
            builder.addCustomConverter(new JsonApiResourceHttpMessageConverter(runtime));
        }

        @Override
        public LocalValidatorFactoryBean getValidator() {
            var validator = new LocalValidatorFactoryBean();
            validator.setConfigurationInitializer(
                configuration -> configuration.addValueExtractor(new PatchPresenceValueExtractor()));
            return validator;
        }

        @Override
        public void addInterceptors(InterceptorRegistry registry) {
            // Negotiate media types before parsing the query.
            registry.addInterceptor(new JsonApiMediaNegotiationInterceptor());
            registry.addInterceptor(new JsonApiRequestContextInterceptor());
        }

        @Override
        public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
            resolvers.add(new JsonApiRequestContextArgumentResolver());
            resolvers.add(new JsonApiQueryParamsArgumentResolver());
        }

        @Override
        public void extendHandlerExceptionResolvers(List<HandlerExceptionResolver> resolvers) {
            // With standard @EnableWebMvc, application @ExceptionHandler methods run first.
            resolvers.add(1, new JsonApiProtocolExceptionResolver(runtime));
        }
    }
    ```

## 2. Define the resource DTOs {#define-the-resource-dtos}

Define `Person` and `Article` in separate source files:

```java
import com.kazforge.jsonapi.annotation.JsonApiAttribute;
import com.kazforge.jsonapi.annotation.JsonApiId;
import com.kazforge.jsonapi.annotation.JsonApiRelationship;
import com.kazforge.jsonapi.annotation.JsonApiResource;
import jakarta.validation.constraints.NotBlank;
import org.jspecify.annotations.Nullable;

@JsonApiResource(type = "people")
public record Person(
    @JsonApiId String id,
    @JsonApiAttribute String name) {}

@JsonApiResource(type = "articles")
public record Article(
    @JsonApiId @Nullable String id,
    @JsonApiAttribute @NotBlank @Nullable String title,
    @JsonApiRelationship @Nullable Person author) {}
```

`Article.id` is nullable so clients can omit it when creating an article. The server assigns
the ID before returning the resource. See the
[Core resources guide](https://jsonapi.kazforge.com/core/0.5.1/resources/) for mapping details.

## 3. Implement a first JSON:API controller {#implement-a-first-controller}

Annotate a controller class or individual handler methods with `@JsonApiEndpoint`:

```java
import com.kazforge.jsonapi.spring.webmvc.JsonApiEndpoint;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/articles")
@JsonApiEndpoint
public class ArticleController {
    private final ArticleService articleService;

    public ArticleController(ArticleService articleService) {
        this.articleService = articleService;
    }

    @PostMapping
    public ResponseEntity<Article> create(@Valid @RequestBody Article input) {
        Article created = articleService.create(input.title());
        return ResponseEntity.created(URI.create("/articles/" + created.id()))
            .body(created);
    }

    @GetMapping("/{id}")
    public Article get(@PathVariable("id") String id) {
        return articleService.findById(id);
    }
}
```

Throughout this guide, `ArticleService` (`articleService`) is illustrative application code that
owns ID allocation, authorization, and persistence.

!!! warning "Do not set `consumes` or `produces` on `@JsonApiEndpoint` mappings"
    Spring evaluates `@RequestMapping(consumes = ..., produces = ...)` conditions during handler
    matching—before `JsonApiMediaNegotiationInterceptor` runs. Leaving `consumes` and `produces`
    unset lets the adapter enforce JSON:API 1.1 `ext` and `profile` media-type parameter rules
    (`415 Unsupported Media Type` and `406 Not Acceptable`).

Send a create request to `POST /articles`:

```http
POST /articles
Content-Type: application/vnd.api+json
Accept: application/vnd.api+json

{"data":{"type":"articles","attributes":{"title":"Working with JSON:API"}}}
```

Suppose `articleService.create(...)` assigns ID `"42"` and links author `"7"` (`Ada`). The endpoint
responds with `201 Created`, `Location: /articles/42`, `Content-Type: application/vnd.api+json`, and
`Vary: Accept`:

```json
{
  "data": {
    "type": "articles",
    "id": "42",
    "attributes": {
      "title": "Working with JSON:API"
    },
    "relationships": {
      "author": {
        "data": {
          "type": "people",
          "id": "7"
        }
      }
    }
  }
}
```

Fetching `GET /articles/42` with `Accept: application/vnd.api+json` returns `200 OK` with the same
JSON:API resource document.

## Complete runnable examples {#complete-runnable-examples}

<!-- x-release-please-start-version -->

- [Spring Boot WebMVC consumer (`examples/boot-webmvc`)](https://github.com/kazforge/jsonapi-java-spring/tree/v0.7.0/examples/boot-webmvc)
- [Standalone Jackson 3 WebMVC consumer (`examples/jackson3-webmvc`)](https://github.com/kazforge/jsonapi-java-spring/tree/v0.7.0/examples/jackson3-webmvc)

<!-- x-release-please-end -->

**Next:** [Return a collection and update an article with PATCH](resources-and-patch.md).
