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
accepts ordinary `@RequestBody Article` values and returns DTOs or typed lists.
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
An explicitly supplied ID is preserved. GET `/articles/42`, `/articles`, and
`/articles/empty` demonstrate singleton, list, and empty-list responses.
The unmarked `/ordinary` endpoint retains Spring's ordinary JSON handling.

The context-backed MockMvc suite uses the real published runtime, not a recording
substitute. It checks create binding, resource responses, headers, ordinary MVC,
and representative `400`/`406`/`415` error documents. It is a consumer smoke test,
not another Core conformance suite or Spring compatibility matrix.
