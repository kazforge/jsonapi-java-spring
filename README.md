# jsonapi-java-spring

The separate Spring companion to [jsonapi-java](https://github.com/kazforge/jsonapi-java),
with its own repository and release train. This repository currently provides the build
baseline only; no Spring runtime modules are implemented yet.

The first planned module is `jsonapi-java-spring-webmvc`, a Jackson-major-neutral
WebMVC foundation. Spring modules will consume released jsonapi-java artifacts from
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

Licensed under [Apache-2.0](LICENSE).
