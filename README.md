# jsonapi-java-spring

The separate Spring companion to [jsonapi-java](https://github.com/kazforge/jsonapi-java),
with its own repository and release train.

The first module is `jsonapi-java-spring-webmvc`, a Jackson-neutral
WebMVC adapter providing opt-in JSON:API media negotiation and operation-aware request/query
context, safe protocol error responses, and explicit advanced document response transport
through public neutral contracts. Applications supply their `JsonApi` runtime; the adapter
does not bind DTOs, execute queries, or select application/domain error semantics.
Spring modules consume released jsonapi-java artifacts from
Maven Central, not a sibling source checkout.

## Development

Install JDK 21 and use the committed wrapper:

```shell
./gradlew clean build
./gradlew spotlessApply
./gradlew spotlessCheck
```

`build` includes formatting checks and validation of the reusable build conventions.
See [CONTRIBUTING.md](CONTRIBUTING.md) for the module quality policy.

See [repository architecture and decisions](docs/README.md).

Licensed under [Apache-2.0](LICENSE).
