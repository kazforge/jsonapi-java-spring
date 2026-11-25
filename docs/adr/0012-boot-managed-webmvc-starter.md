# ADR 0012: Boot-managed WebMVC starter

Status: Accepted

Date: 2026-11-25

## Context

A Boot servlet application should need only the starter, its resource DTOs and marked
controllers. Standalone WebMVC users still choose their own runtime and MVC wiring.

## Decision

Publish `com.kazforge:jsonapi-java-spring-boot-starter` as one Java library on the
same release train as WebMVC. It includes native auto-configuration, the same-version
adapter, Core's released Jackson 3 runtime and Boot's WebMVC and validation starters.
Use Boot's dependency platform. Compile against 4.1.2 (Framework 7.0.10, Jackson 3.1.7);
exercise the same application on 4.1.3 (7.0.11, 3.1.8). Boot 4.1.1 manages Jackson below
Core 0.5.1's baseline. These are compatibility targets; a release also requires a
patched runtime graph. This choice does not raise the standalone adapter's 7.0.9 floor.

Activate through `AutoConfiguration.imports` in Boot-managed servlet MVC. Marked
handlers reuse the standalone request pipeline. Register converters through Boot's
server builder, not as global converter beans. Defer to existing converter beans,
which Boot itself collects. Keep the protocol resolver inside MVC's resolver chain
after application exception handlers; do not register it a second time with the servlet.
Application-owned MVC (`@EnableWebMvc`) keeps its wiring. For fully manual registrations
in Boot-managed MVC, exclude `JsonApiWebMvcAutoConfiguration` as a unit.

Build `JsonApi` from Boot's configured `JsonMapper`; apply an optional
`RepresentationPolicy` bean, otherwise use Core defaults. Never automatically enable
include traversal. For example an application may supply:

```java
@Bean
RepresentationPolicy representation() {
    return RepresentationPolicy.defaults().withIncludePolicy(
        IncludePolicy.allowing(Set.of(RelationshipAllowance.of("articles", "author"))));
}
```

An application-supplied `JsonApi` is authoritative, including its representation
policy. Its create and typed PATCH operations must support source attribution.
The starter contributes `PatchPresenceValueExtractor` through Jakarta Validation's
service loader, including for an application-owned `LocalValidatorFactoryBean`.
Standalone WebMVC does not publish that service entry.

Resource PATCH identity belongs to **WebMVC**. On a marked PATCH handler,
`@JsonApiPatchTarget("articles")` binds the declared resource type to the matched
`{id}`; `idVariable` selects a different route variable. The explicit converter
callback remains authoritative when supplied. Never infer identity from the body,
DTO type or URL structure. Missing identity fails before reading the document.

## Consequences

The framework-neutral Core and standalone WebMVC acquire no Boot dependency. The
unpublished Boot app exercises the default and application-owned policies. On resource
GET/HEAD, only an explicitly requested unknown or denied include becomes a safe
`include` parameter error. The writer runs **after** the handler: it cannot authorize a
mutation, undo one or guarantee that retrying is safe. A mutation handler requiring
preconditions must check them before changing state. Its response-writing failures
remain server errors.

One root version, release PR, tag and Central bundle cover both signed publications.
Each primary JAR has its own SBOM and attestation. Depending on the starter already
selects matching WebMVC, so there is no separate alignment BOM or auto-configuration artifact.
