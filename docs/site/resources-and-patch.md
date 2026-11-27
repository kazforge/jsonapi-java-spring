---
title: "Resources and PATCH"
description: "Return resource collections, add top-level metadata and links with JsonApiResponse, and handle partial updates with PatchPresence and @JsonApiPatchTarget."
---

# Read collections, add top-level metadata, and handle partial updates {#resources-and-patch}

## Resource collections and bodyless responses {#collections-and-bodyless-responses}

Add `GET /articles` to the [first `ArticleController`](getting-started.md#implement-a-first-controller).
Returning a `List<Article>` (or any `Iterable<Article>`) writes a JSON:API collection
(`"data": [...]`, or `"data": []` when empty):

```java
@GetMapping
public List<Article> list() {
    return articleService.findAll();
}
```

Returning `ResponseEntity.noContent().build()` (`204 No Content`) or a plain Java `null` produces a
bodyless HTTP response.

!!! note "Reading relationship linkage on `POST` create requests"
    In [Getting started](getting-started.md#define-the-resource-dtos), `Article` declared
    `@JsonApiRelationship @Nullable Person author` so response writes can optionally include the
    loaded `Person` resource. If a `POST /articles` request body also supplies initial relationship
    linkage under `data.relationships.author.data`, use a create DTO whose relationship field is
    `ResourceIdentifier` (`@JsonApiRelationship @Nullable ResourceIdentifier author`). Built-in
    request binding reads relationship linkage as identifiers rather than hydrating related DTOs;
    see the [Core Resources guide](https://jsonapi.kazforge.com/core/0.5.1/resources/#write-relationships-read-linkage).

## Top-level metadata and links (`JsonApiResponse<T>`) {#top-level-metadata-and-links}

To add top-level `meta`, `links`, or an explicit `jsonapi` object, wrap the non-null resource or
collection in `JsonApiResponse<T>`, optionally inside a `ResponseEntity`. Replace the `list()`
method above with this version for the same `GET /articles` route:

```java
import com.kazforge.jsonapi.core.model.Link;
import com.kazforge.jsonapi.core.model.Links;
import com.kazforge.jsonapi.core.model.Meta;
import com.kazforge.jsonapi.spring.webmvc.document.JsonApiResponse;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;

@GetMapping
public JsonApiResponse<List<Article>> list() {
    List<Article> articles = articleService.findAll();
    return JsonApiResponse.of(articles)
        .withMeta(Meta.of(Map.of("total", articles.size())))
        .withLinks(Links.ofLinks(Map.of("self", new Link.StringLink("/articles"))));
}
```

This serializes the primary `data` array alongside top-level `"meta": {"total": ...}` and
`"links": {"self": "/articles"}`, while still applying the request's `include` and `fields[TYPE]`
selection.

## Partial updates with `PatchPresence<T>` {#partial-updates-with-patchpresence}

In a JSON:API `PATCH` request, a client sends only the members it wants to modify. Binding a partial
update into an ordinary DTO cannot distinguish an **omitted** member (leave unchanged) from an
**explicit JSON `null`** (clear the value).

Declare a dedicated PATCH DTO for `articles` whose `@JsonApiId` stays unwrapped (`String id`) and
whose patchable attributes and relationships are wrapped in `PatchPresence<T>`
(`com.kazforge.jsonapi.patch.PatchPresence`):

```java
import com.kazforge.jsonapi.annotation.JsonApiAttribute;
import com.kazforge.jsonapi.annotation.JsonApiId;
import com.kazforge.jsonapi.annotation.JsonApiRelationship;
import com.kazforge.jsonapi.annotation.JsonApiResource;
import com.kazforge.jsonapi.core.model.ResourceIdentifier;
import com.kazforge.jsonapi.patch.PatchPresence;
import jakarta.validation.constraints.NotBlank;

@JsonApiResource(type = "articles")
public record ArticlePatch(
    @JsonApiId String id,
    @JsonApiAttribute PatchPresence<@NotBlank String> title,
    @JsonApiRelationship PatchPresence<ResourceIdentifier> author) {}
```

Each `PatchPresence<T>` component binds to one of three states:

| Request JSON member | Bound `PatchPresence<T>` state | Meaning |
| --- | --- | --- |
| Omitted from JSON | `PatchPresence.Omitted` | Keep the existing value |
| Explicit JSON `null` | `PatchPresence.Present` with `value() == null` | Clear the value (or reject if required, such as `@NotBlank` on `title`) |
| Supplied JSON value | `PatchPresence.Present` with converted `value()` | Replace with the supplied value |

See the [Core PATCH guide](https://jsonapi.kazforge.com/core/0.5.1/patches/) for nested structured
attributes, container replacement rules, and to-many relationship linkage updates.

## Declare trusted route identity (`@JsonApiPatchTarget`) {#declare-trusted-route-identity}

JSON:API requires a `PATCH` request's `data.type` and `data.id` to match the endpoint URL. Never
trust the request body alone to decide which persisted record to modify.

Annotate the `@PatchMapping` method with `@JsonApiPatchTarget("<type>")`
(`com.kazforge.jsonapi.spring.webmvc.JsonApiPatchTarget`). Before reading the request body,
`JsonApiResourceHttpMessageConverter` resolves the addressed resource ID from Spring's matched
`{id}` URI template variable and validates the incoming document against that `EndpointIdentity`:

```java
import com.kazforge.jsonapi.spring.webmvc.JsonApiPatchTarget;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;

// Inside @RestController @RequestMapping("/articles") @JsonApiEndpoint ArticleController:
@PatchMapping("/{id}")
@JsonApiPatchTarget("articles")
public Article update(@Valid @RequestBody ArticlePatch patch) {
    return articleService.update(patch.id(), patch.title(), patch.author());
}
```

`@JsonApiPatchTarget` declares the target identity for a PATCH method; the controller or method must
still be opted in with `@JsonApiEndpoint`.

Inside your application service, pattern-match on `PatchPresence` to apply only the supplied changes:

```java
// Illustrative application service logic
Article current = findById(id);

String nextTitle = switch (titlePatch) {
    case PatchPresence.Omitted<String> ignored -> current.title();
    case PatchPresence.Present<String>(var value) -> value;
};

Person nextAuthor = switch (authorPatch) {
    case PatchPresence.Omitted<ResourceIdentifier> ignored -> current.author();
    case PatchPresence.Present<ResourceIdentifier>(var identifier) ->
        identifier == null ? null : findPerson(identifier);
};
```

Send a partial update that changes `title` and omits `author`:

```http
PATCH /articles/42
Content-Type: application/vnd.api+json
Accept: application/vnd.api+json

{"data":{"type":"articles","id":"42","attributes":{"title":"Revised JSON:API guide"}}}
```

Here `patch.title()` is `Present("Revised JSON:API guide")` and `patch.author()` is `Omitted`, so
the existing author (`Ada`) is preserved. A PATCH handler may return the updated `Article`
(`200 OK`) or `ResponseEntity<Void>` (`204 No Content`).

Automatic protocol and validation checks on `PATCH`:

- **Route identity mismatch (`409 Conflict`):** If the request body's `data.type` is not
  `"articles"` or `data.id` does not match the URL's `{id}` (`"42"`), document validation fails
  before DTO binding with code `ENDPOINT_IDENTITY_MISMATCH`.
- **Unknown PATCH members (`400 Bad Request`):** Supplying an attribute or relationship not declared
  on `ArticlePatch` fails with code `UNKNOWN_PATCH_MEMBER`.
- **Presence-aware `@Valid` checks (`400 Bad Request`):** With `PatchPresenceValueExtractor`
  registered (automatic in Spring Boot; configured on `LocalValidatorFactoryBean` in standalone
  WebMVC), omitting `title` passes validation, whereas sending `"title": null` or `"title": " "`
  fails `@NotBlank` with `INVALID_FIELD` and `source.pointer: "/data/attributes/title"`.

### Custom route variable names (`idVariable`) {#custom-route-variable-names}

By default, `@JsonApiPatchTarget` reads the `"id"` URI template variable (`idVariable = "id"`). When
a route uses a different variable name—such as a nested route where `{id}` identifies a parent
resource—specify `idVariable` explicitly. This example belongs in a controller without the
class-level `/articles` prefix:

```java
@PatchMapping("/groups/{id}/articles/{articleId}")
@JsonApiPatchTarget(value = "articles", idVariable = "articleId")
public ResponseEntity<Void> updateGroupArticle(@Valid @RequestBody ArticlePatch patch) {
    articleService.update(patch.id(), patch.title(), patch.author());
    return ResponseEntity.noContent().build();
}
```

### Programmatic route identity callback {#programmatic-route-identity-callback}

For centralized route identity, use a [converter callback](configuration.md#custom-converter-and-programmatic-patch-identity).
It takes precedence over `@JsonApiPatchTarget`. A resource PATCH endpoint with no resolvable
identity fails with `500` before the adapter reads the request body.

## Low-level `PatchCommand<T>` binding {#low-level-patch-commands}

To inspect changes without defining a PATCH DTO, replace the `update(ArticlePatch)` handler
for `PATCH /articles/{id}` with this `PatchCommand<Article>` version:

```java
import com.kazforge.jsonapi.patch.PatchCommand;

@PatchMapping("/{id}")
@JsonApiPatchTarget("articles")
public ResponseEntity<Void> updateWithCommand(@RequestBody PatchCommand<Article> command) {
    articleService.applyChanges(command.identity(), command.changes());
    return ResponseEntity.noContent().build();
}
```

The resource type argument (`Article`) must be a single concrete `@JsonApiResource` class; raw
`PatchCommand`, wildcards, unresolved type variables, and collection targets fail as configuration
errors. See the [Core PATCH guide](https://jsonapi.kazforge.com/core/0.5.1/patches/#low-level-commands)
for inspecting `PatchChange` entries.

**Next:** [Bind query parameters and include related resources](queries-and-representation.md).
