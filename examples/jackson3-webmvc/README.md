# Jackson 3 WebMVC consumer

A non-published, stateless Spring Framework application configuration using the local
WebMVC adapter and Maven Central's `com.kazforge:jsonapi-java-jackson3:0.4.0`.
It uses Java 21, the catalog's current Spring 7.0 patch, and the runtime's published
Jackson 3.1.7 dependency baseline. No Boot or servlet container is required to run the tests.

```shell
./gradlew :examples:jackson3-webmvc:build
```

[`ConsumerConfiguration`](src/main/java/com/kazforge/jsonapi/example/ConsumerConfiguration.java)
shows the application-owned `JsonApi` bean and explicit MVC registration. Only runtime
construction knows Jackson; the adapter receives the neutral `JsonApi` contract.
Register this configuration in your servlet application's WebMVC context. The example
does not supply a server launcher or persistence implementation.

[`ArticleController`](src/main/java/com/kazforge/jsonapi/example/ArticleController.java)
handles article creation, reads, and presence-aware updates using the same resource model.
Annotated mappings deliberately omit `consumes` and `produces`, leaving JSON:API
negotiation to the adapter. For example:

```http
POST /articles
Content-Type: application/vnd.api+json
Accept: application/vnd.api+json

{"data":{"type":"articles","attributes":{"title":"Hello"}}}
```

The response is `201 Created`, with `Location: /articles/42`, `Vary: Accept`, and
`application/vnd.api+json` resource data containing ID `42` and title `Hello`.
The fixed server ID is illustrative only; applications own allocation and persistence.
An explicitly supplied ID is preserved. GET `/articles/{id}`, `/articles`, and
`/articles/empty` demonstrate singleton, list, and empty-list responses.
The unmarked `/ordinary` endpoint retains Spring's ordinary JSON handling for GET and
echoes plain `Article` JSON on PATCH.

## Updating an article

The same application configuration registers route-derived identity resolution and a
Spring validator for `ArticlePatch`. The controller applies the bound changes to the
sample article and returns its JSON:API representation:

```http
PATCH /articles/42
Content-Type: application/vnd.api+json
Accept: application/vnd.api+json

{"data":{"type":"articles","id":"42","attributes":{"title":"Updated"}}}
```

The response is `200 OK` with title `Updated`. Omitted `title` or `editor` members retain
their sample values; explicit null clears them. A non-null title must be nonblank.
The `editor` relationship accepts resource linkage; `included` resources do not become
additional updates. Route/body identity conflicts produce `409` errors.

This demo is stateless: each PATCH starts from the same snapshot returned by GET, and
subsequent GETs do not reflect POST or PATCH responses. A real application would load
and persist the article around the same presence-aware update logic.

The context-backed MockMvc suite uses the real published runtime, not a recording
substitute. It exercises the production configuration and controllers for create,
read, update, validation, and ordinary MVC.
It checks representative `400`/`406`/`409`/`415` error documents. It is a consumer smoke test,
not another Core conformance suite or Spring compatibility matrix.
