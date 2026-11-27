---
title: "Relationship linkage"
description: "Implement JSON:API to-one and to-many relationship-linkage endpoints with PrimaryDataContext.RELATIONSHIP."
---

# Implement to-one and to-many relationship-linkage endpoints {#relationship-linkage}

On a relationship-linkage endpoint, primary `data` identifies the related resources. On a
related-resource endpoint, `data` contains the resources themselves.

## Endpoint roles: resource vs. relationship linkage {#endpoint-roles}

| URL pattern | `@JsonApiEndpoint` role | Primary `data` | Handled by |
| --- | --- | --- | --- |
| `/articles/{id}` | `PrimaryDataContext.RESOURCE` (default) | `Article` resource object | `JsonApiResourceHttpMessageConverter` |
| `/articles/{id}/author` | `PrimaryDataContext.RESOURCE` (default) | Related `Person` resource object | `JsonApiResourceHttpMessageConverter` |
| `/articles/{id}/relationships/author` | `PrimaryDataContext.RELATIONSHIP` | `ResourceIdentifier` linkage (`people` / `7`) or `null` | `JsonApiRelationshipHttpMessageConverter` |

Annotate relationship-linkage controllers or handler methods with
`@JsonApiEndpoint(primaryDataContext = PrimaryDataContext.RELATIONSHIP)`. A method-level
`@JsonApiEndpoint` replaces any class-level `@JsonApiEndpoint` metadata, including its
`primaryDataContext` default.

## To-one relationship linkage (`/articles/{id}/relationships/author`) {#to-one-relationship-linkage}

Use `JsonApiToOneRelationship`
(`com.kazforge.jsonapi.spring.webmvc.document.JsonApiToOneRelationship`) for to-one `PATCH` request
bodies and for `GET` responses that may be empty (`{"data":null}`). A to-one `GET` handler whose
relationship is always non-empty may also return `ResourceIdentifier`
(`com.kazforge.jsonapi.core.model.ResourceIdentifier`) directly.

```java
import com.kazforge.jsonapi.core.validation.PrimaryDataContext;
import com.kazforge.jsonapi.spring.webmvc.JsonApiEndpoint;
import com.kazforge.jsonapi.spring.webmvc.document.JsonApiToOneRelationship;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/articles/{id}/relationships/author")
@JsonApiEndpoint(primaryDataContext = PrimaryDataContext.RELATIONSHIP)
public class AuthorRelationshipController {
    private final ArticleService articleService;

    public AuthorRelationshipController(ArticleService articleService) {
        this.articleService = articleService;
    }

    @GetMapping
    public JsonApiToOneRelationship get(@PathVariable("id") String id) {
        return new JsonApiToOneRelationship(articleService.getAuthorIdentifier(id));
    }

    @PatchMapping
    public ResponseEntity<Void> replace(
            @PathVariable("id") String id,
            @RequestBody JsonApiToOneRelationship author) {
        articleService.setAuthorIdentifier(id, author.identifier());
        return ResponseEntity.noContent().build();
    }
}
```

To set the author, send its identifier:

```http
PATCH /articles/42/relationships/author
Content-Type: application/vnd.api+json

{"data":{"type":"people","id":"7"}}
```

To clear the author, send `{"data":null}`. The wrapper is present and `author.identifier()` is
`null`; an empty HTTP body instead fails with `400 Bad Request`. On `GET`,
`new JsonApiToOneRelationship(null)` writes `{"data":null}`, while Java `null` returns no body.

## To-many relationship linkage (`/articles/{id}/relationships/reviewers`) {#to-many-relationship-linkage}

Use `List<ResourceIdentifier>` for both `GET` responses and `PATCH`, `POST`, and `DELETE` request
bodies. The HTTP method determines the operation:

| HTTP method | JSON:API operation |
| --- | --- |
| `GET` | Read current linkage (`{"data":[...]}` or `{"data":[]}`) |
| `PATCH` | Replace all linkage (`{"data":[]}` clears the relationship) |
| `POST` | Add the supplied identifiers to the relationship |
| `DELETE` | Remove the supplied identifiers from the relationship (without deleting the related resources) |

```java
import com.kazforge.jsonapi.core.model.ResourceIdentifier;
import com.kazforge.jsonapi.core.validation.PrimaryDataContext;
import com.kazforge.jsonapi.spring.webmvc.JsonApiEndpoint;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/articles/{id}/relationships/reviewers")
@JsonApiEndpoint(primaryDataContext = PrimaryDataContext.RELATIONSHIP)
public class ReviewersRelationshipController {
    private final ArticleService articleService;

    public ReviewersRelationshipController(ArticleService articleService) {
        this.articleService = articleService;
    }

    @GetMapping
    public List<ResourceIdentifier> get(@PathVariable("id") String id) {
        return articleService.getReviewerIdentifiers(id);
    }

    @PatchMapping
    public ResponseEntity<Void> replace(
            @PathVariable("id") String id,
            @RequestBody List<ResourceIdentifier> reviewers) {
        articleService.setReviewerIdentifiers(id, reviewers);
        return ResponseEntity.noContent().build();
    }

    @PostMapping
    public ResponseEntity<Void> add(
            @PathVariable("id") String id,
            @RequestBody List<ResourceIdentifier> reviewers) {
        articleService.addReviewerIdentifiers(id, reviewers);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    public ResponseEntity<Void> remove(
            @PathVariable("id") String id,
            @RequestBody List<ResourceIdentifier> reviewers) {
        articleService.removeReviewerIdentifiers(id, reviewers);
        return ResponseEntity.noContent().build();
    }
}
```

```http
POST /articles/42/relationships/reviewers
Content-Type: application/vnd.api+json

{"data":[{"type":"people","id":"9"}]}
```

An empty to-many `GET` returns `200 OK` with `{"data":[]}`. Mutation handlers may return a bodyless
`204 No Content` (`ResponseEntity<Void>`) or `200 OK` with the updated linkage.

## Validation and `include` behavior on linkage endpoints {#linkage-validation-and-include-behavior}

- **Identifier validation (`400 Bad Request`):** Core preserves identifier `id`, supported `lid`,
  and per-identifier `meta`, and rejects missing `data`, invalid identifiers, duplicate identifiers
  in to-many arrays, cardinality mismatches (such as an array sent to a to-one handler or `null`
  sent to a to-many handler), and resource-only members (`attributes`, `relationships`, `links`) on
  primary identifiers.
- **Handler signature checks (`500 Internal Server Error`):** Unsupported method/target combinations
  (such as `POST` or `DELETE` with `JsonApiToOneRelationship`, `PUT`, raw `List`, or
  `@RequestBody ResourceIdentifier` on `PATCH`) fail closed as server configuration errors.
- **No included resources on conventional linkage binding (`400 Bad Request`):**
  `JsonApiRelationshipHttpMessageConverter` reads and writes minimal identifier documents only and
  cannot hydrate related DTOs. Any request with a non-empty `include` query parameter on this
  converter path is rejected before body binding or response serialization with
  `DISALLOWED_INCLUDE_PATH` and `source.parameter: "include"`.
- **Advanced relationship documents:** When a relationship endpoint must return top-level `links`,
  `meta`, or `included` resources, write a Core `JsonApiDocument` directly with
  `JsonApiDocumentResponseWriter` (see
  [Configuration](configuration.md#advanced-document-responses)).

**Next:** [Validate requests and return domain errors](validation-and-errors.md).
