# jsonapi-java-spring

The separate Spring companion to [jsonapi-java](https://github.com/kazforge/jsonapi-java),
with its own repository and release train.

The first module is `jsonapi-java-spring-webmvc`, a Jackson-neutral WebMVC adapter providing
opt-in JSON:API media negotiation, operation-aware request/query context, safe protocol errors,
and conventional resource DTO binding through public neutral contracts. Applications supply their
`JsonApi` runtime; the adapter does not execute queries or select application/domain error
semantics.
The adapter targets Core 0.4.0. Configure `JsonApiResourceHttpMessageConverter` through Spring 7's
`WebMvcConfigurer#configureMessageConverters(HttpMessageConverters.ServerBuilder)` to bind resource
POST bodies and serialize DTOs; `JsonApiResponse<T>` adds optional top-level metadata, links, or a
JSON:API object. The converter uses the request's opted-in resource context and leaves relationship and
ordinary MVC handlers alone.
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

The [Jackson 3 WebMVC consumer example](examples/jackson3-webmvc/README.md) demonstrates
explicit application wiring and real-runtime HTTP integration against published Core 0.4.0.
See [release readiness](docs/release.md) for the verified baseline and release boundary.

See the [security policy](docs/security.md) for vulnerability triage and merge protection.

Licensed under [Apache-2.0](LICENSE).
