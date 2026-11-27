---
title: "Validation and errors"
description: "Validate JSON:API request bodies and query parameters, understand default protocol errors, and return application error documents."
---

# Validate inputs and return safe JSON:API error documents {#validation-and-errors}

On `@JsonApiEndpoint` handlers, the integration translates media-negotiation, query-parsing,
document-read, and `@Valid` failures into safe JSON:API error documents, and lets controllers and
`@RestControllerAdvice` return application-owned `JsonApiErrorResponse` documents.

## Request-body validation (`@Valid @RequestBody`) {#request-body-validation}

Add `@Valid` (`jakarta.validation.Valid`) to `@RequestBody` resource create (`POST`) and typed
`PATCH` DTO parameters.

### Validating `PatchPresence<T>` members {#validating-patchpresence-members}

`PatchPresenceValueExtractor`
(`com.kazforge.jsonapi.spring.webmvc.validation.PatchPresenceValueExtractor`) integrates
`PatchPresence<T>` with Jakarta Validation 3.1:

- **Spring Boot:** The starter supplies the extractor automatically, including when the application
  defines a custom `LocalValidatorFactoryBean`.
- **Standalone Spring WebMVC:** Register `new PatchPresenceValueExtractor()` on
  `LocalValidatorFactoryBean` in `WebMvcConfigurer#getValidator()` (see the **Standalone WebMVC**
  tab in [Getting started](getting-started.md#configure-the-application)).

How extraction works on a typed PATCH DTO such as `ArticlePatch`:

- **`PatchPresence.Omitted`:** Emits nothing to the validator, so constraints on omitted members are
  skipped.
- **`PatchPresence.Present(value)`:** Extracts `value`—including explicit `null`—and validates
  type-use constraints (such as `PatchPresence<@NotBlank String> title` or
  `PatchPresence<@NotNull ResourceIdentifier> author`) as well as cascaded nested objects
  (`PatchPresence<@Valid Section>`).
- **Class-level and nested atomic rules:** Class-level constraints on the PATCH DTO can inspect
  `PatchPresence` components directly (for example, to require at least one non-omitted change), and
  missing fields inside a supplied atomic object replacement still validate normally.

### Default validation error responses and source pointers {#default-validation-error-responses}

When `@Valid @RequestBody` fails on a bound create or typed PATCH DTO,
`JsonApiProtocolExceptionResolver` responds with HTTP **`400 Bad Request`**,
`Content-Type: application/vnd.api+json`, and `Vary: Accept`:

- Property violations emit code `INVALID_FIELD` and title `"Invalid field value"`.
- A violation on the resource object itself emits code `INVALID_RESOURCE`, title `"Invalid resource"`,
  and pointer `"/data"`.
- For a field violation, `source.pointer` identifies the submitted member when its location is
  known. A missing nested member can point to its nearest supplied ancestor: if the client sends
  `"section": {}` without a required child, the pointer can be `/data/attributes/section`.
  If no location can be established, as with an omitted top-level attribute, the error has no `source`.
- **No sensitive leakage:** Constraint messages, Spring binding codes, and rejected input values are
  never included in the response.

For example, sending `"title": null` in `PATCH /articles/42` against `ArticlePatch` returns:

```json
{
  "errors": [
    {
      "code": "INVALID_FIELD",
      "title": "Invalid field value",
      "source": {
        "pointer": "/data/attributes/title"
      }
    }
  ]
}
```

Generated body-validation errors omit the optional per-error `"status"` member so application advice
can override the HTTP response status without rebuilding the error objects (see
[Overriding the body-validation HTTP status](#overriding-the-body-validation-http-status)).

## Query parameter validation (`@Valid @JsonApiQueryParams`) {#query-parameter-validation}

Adding `@Valid` to a `@JsonApiQueryParams` parameter validates the constructed query DTO with Spring
MVC's validator. Violations on a supplied parameter (or an element of a repeated
`List<@NotBlank String>` parameter) produce HTTP **`400 Bad Request`** with `source.parameter`
identifying the exact query parameter:

```json
{
  "errors": [
    {
      "status": "400",
      "code": "INVALID_QUERY_PARAMETER",
      "title": "Invalid query parameter",
      "source": {
        "parameter": "page[size]"
      }
    }
  ]
}
```

Violations on missing parameters (such as `@NotNull` when `page[number]` was omitted) and
class-level query constraints omit `source`. For non-record POJO query DTOs, compile with
`-parameters` and use matching constructor parameter and property names so field violations map back
to their `@BindParam` keys.

## Default protocol error reference {#default-protocol-error-reference}

`JsonApiProtocolExceptionResolver` translates known protocol and input failures on
`@JsonApiEndpoint` handlers into JSON:API error documents:

| HTTP status | Failure condition | Error `code` | `source` member |
| --- | --- | --- | --- |
| `400 Bad Request` | Malformed JSON or invalid JSON:API document structure | Core [`CodecFailureCategory`](https://jsonapi.kazforge.com/core/0.5.1/diagnostics/#what-failed) (`MALFORMED_JSON`, `UNEXPECTED_TOKEN`, `DUPLICATE_MEMBER`, `LOCAL_VALIDATION`, `AGGREGATE_VALIDATION`) | Omitted |
| `400 Bad Request` | Unknown PATCH member, resource type mismatch, relationship cardinality mismatch, or identifier conversion failure | Core [`MappingDiagnostic`](https://jsonapi.kazforge.com/core/0.5.1/diagnostics/#what-failed) (`UNKNOWN_PATCH_MEMBER`, `RESOURCE_TYPE_MISMATCH`, `RELATIONSHIP_CARDINALITY_MISMATCH`, `IDENTIFIER_CONVERSION_FAILED`) or `INVALID_REQUEST_DOCUMENT` | Omitted |
| `400 Bad Request` | Invalid query syntax, repeated `include`/`sort`/`fields`, or disallowed `include` on resource `GET`/`HEAD` or relationship linkage | Core [`QueryDiagnostic`](https://jsonapi.kazforge.com/core/0.5.1/diagnostics/#what-failed) (e.g. `INVALID_FIELDSET_SYNTAX`, `INVALID_PARAMETER_CARDINALITY`, `DISALLOWED_INCLUDE_PATH`) | `source.parameter` when decoded |
| `400 Bad Request` | Undeclared opaque query parameter on `@JsonApiQueryParams` | `UNSUPPORTED_QUERY_PARAMETER` | `source.parameter` |
| `400 Bad Request` | `@JsonApiQueryParams` conversion failure or `@Valid` violation | `INVALID_QUERY_PARAMETER` | `source.parameter` when supplied |
| `400 Bad Request` | `@Valid @RequestBody` create or typed PATCH violation | `INVALID_FIELD` or `INVALID_RESOURCE` | `source.pointer` when proven |
| `400 Bad Request` | Other validation failure on an opted-in handler | `INVALID_REQUEST` | Omitted |
| `406 Not Acceptable` | `Accept` header cannot be satisfied by bare `application/vnd.api+json` | `NOT_ACCEPTABLE` | `source.header: "Accept"` |
| `409 Conflict` | Resource `PATCH` body `type` or `id` conflicts with endpoint route identity | `ENDPOINT_IDENTITY_MISMATCH` | Omitted |
| `415 Unsupported Media Type` | Request body does not use valid `application/vnd.api+json` | `UNSUPPORTED_MEDIA_TYPE` | `source.header: "Content-Type"` |
| `500 Internal Server Error` | Server/endpoint configuration or mapping failure during request binding | `RESOURCE_BINDING_FAILED` | Omitted |
| `500 Internal Server Error` | Response serialization failure (including disallowed `include` after a `POST`/`PATCH` mutation handler) | `RESPONSE_SERIALIZATION_FAILED` | Omitted |

Application exceptions outside these categories follow ordinary Spring exception handling.

## Application and domain errors (`JsonApiErrorResponse`) {#application-and-domain-errors}

Controllers and `@RestControllerAdvice` can return `ResponseEntity<JsonApiErrorResponse>` (or a
`@ResponseStatus`-annotated method returning `JsonApiErrorResponse`) containing Core `ErrorObject`
instances:

```java
import com.kazforge.jsonapi.core.model.ErrorObject;
import com.kazforge.jsonapi.spring.webmvc.error.JsonApiErrorResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = ArticleController.class)
public class ArticleErrorAdvice {
    @ExceptionHandler(ArticleNotFoundException.class)
    public ResponseEntity<JsonApiErrorResponse> handleNotFound() {
        ErrorObject error = ErrorObject.builder()
            .status("404")
            .code("ARTICLE_NOT_FOUND")
            .title("Article not found")
            .build();
        return ResponseEntity.status(404)
            .body(JsonApiErrorResponse.of(error));
    }
}
```

Here `ArticleNotFoundException` is an illustrative application exception thrown by `ArticleService`.

- Use `JsonApiErrorResponse.of(error)` or `JsonApiErrorResponse.of(List.of(...))`, and chain
  `.withMeta(...)`, `.withLinks(...)`, or `.withJsonApi(...)` when top-level document envelope
  members are needed.
- Spring owns the HTTP status and response headers. `JsonApiErrorHttpMessageConverter` serializes
  the supplied `ErrorObject` values as-is without inferring or rewriting `error.status`.
- `JsonApiErrorHttpMessageConverter` checks whether the originally matched handler is annotated with
  `@JsonApiEndpoint` (covering both resource and relationship endpoints, even when the advice class
  itself is not annotated with `@JsonApiEndpoint`), and leaves ordinary MVC endpoints alone.

## Overriding the body-validation HTTP status {#overriding-the-body-validation-http-status}

Because `JsonApiProtocolExceptionResolver` is ordered **after** Spring's
`ExceptionHandlerExceptionResolver`, an application `@RestControllerAdvice` can intercept
`MethodArgumentNotValidException` and call `JsonApiErrorResponse.fromValidation(request, failure)`
to reuse the same safe, source-attributed error objects with a custom status such as
`422 Unprocessable Content`:

```java
import com.kazforge.jsonapi.spring.webmvc.error.JsonApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = ArticleController.class)
public class ValidationAdvice {
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<JsonApiErrorResponse> handleInvalidBody(
            HttpServletRequest request,
            MethodArgumentNotValidException failure)
            throws MethodArgumentNotValidException {
        JsonApiErrorResponse errors = JsonApiErrorResponse.fromValidation(request, failure)
            .orElseThrow(() -> failure);
        return ResponseEntity.status(422).body(errors);
    }
}
```

`fromValidation` returns a non-empty `Optional` only when `failure` targets the create or typed
PATCH DTO instance bound in the current dispatch. Re-throwing `failure` when `fromValidation`
returns empty delegates unrelated validation failures to the remaining exception-resolver chain.

**Next:** [Customize the runtime and MVC configuration](configuration.md).
