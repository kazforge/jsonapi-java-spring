# Contributing

Install JDK 21; toolchains are not downloaded automatically. Use the committed Gradle
wrapper rather than a system Gradle installation.

```shell
./gradlew spotlessApply
./gradlew clean spotlessCheck build
```

For a deliberately cache-free final verification, append `--no-build-cache`.

## Build conventions

`settings.gradle.kts` owns module membership. The root has no production sources.
New Java library modules apply `jsonapi-java-spring-library`; this includes formatting,
Java 21 compilation/testing, JUnit Jupiter, Javadoc, JSpecify, Error Prone/NullAway,
and JaCoCo. Versions belong in `gradle/libs.versions.toml`, shared with `build-logic`.

Mark production packages with JSpecify `@NullMarked` in `package-info.java`, and use
explicit `@Nullable` where necessary. Missing explicit null-marking and nullness
violations fail production compilation.

Executable library modules must meet fixed 80% line and 80% branch coverage through
`check`. XML and HTML reports are generated under each module's `build/reports/jacoco`.
Missing coverage data fails the check when production classes exist. Do not change
thresholds to fit measured coverage. A module with no executable production code may
explicitly disable `jacocoTestCoverageVerification`, with a documented justification;
this also exempts it from the missing-data guard. There are no exemptions initially.

Normal dependencies resolve only from Maven Central. Do not introduce `mavenLocal()`,
local jsonapi-java composites, or source substitution. The Plugin Portal is used only
to resolve build tooling. Publication/signing and consumer verification are separate work.

WebMVC production compilation and default tests use Spring 7.0.0. Run
`./gradlew clean build -PspringTestVersion=current` to select the catalog's current stable
7.0 patch for tests only. The default is `minimum`; other values are rejected. Both paths
run in the CI matrix; only minimum runs Sonar. Production dependencies remain at the floor.
Minimum version changes require a maintainer decision; current-test patches may be automated.

The non-published `examples/jackson3-webmvc` module reuses library quality conventions,
including the unchanged coverage thresholds, but supplies application-owned Jackson 3
wiring. It always uses the current Spring BOM, independently of `springTestVersion`.
CI's minimum leg runs the full root build (including this consumer); its current leg
runs `:jsonapi-java-spring-webmvc:build -PspringTestVersion=current` only.

## Repository documentation

Update [architecture](docs/architecture.md) in the same PR when changing repository
boundaries, module ownership, compatibility, or other durable contracts. Add or
supersede an [ADR](docs/adr/README.md) for consequential architectural decisions;
routine fixes do not need one.

## SonarCloud

The root config targets SonarCloud organization `kazemek`, project
`kazforge_jsonapi-java-spring`, bound to this GitHub repository. Use CI-based analysis
in SonarCloud (disable automatic analysis) and keep the repository's `SONAR_TOKEN`
secret authorized to analyze this project.

Like `jsonapi-java`, the Sonar plugin/version lives in the version catalog, configuration
lives at the root, coverage comes from existing JaCoCo XML reports, and the Java 21 CI
path runs `sonar` after the build, waits for the Quality Gate, then runs the reused
`check-new-code-issues.sh --list` check. It fails closed on API/JSON errors or any
unresolved issue in the new-code period, not on historical/global issue totals.

Scans run on same-repository PRs as a pre-merge gate, with the issue check scoped to
the PR number, and on pushes to `main` using the default-branch new-code period.
Fork PRs explicitly skip all Sonar steps and receive no Sonar credentials. Tokens are
scoped to the trusted Sonar steps, not the normal build. No privileged PR follow-up
workflow is introduced. Normal
`./gradlew clean build` never runs Sonar or needs a token; formatting, compiler/nullness,
tests, and JaCoCo's 80/80 verification remain authoritative. Jackson-specific CPD/source
exclusions, CycloneDX, publication/signing, and unrelated security tooling are not copied.
Modules with executable production code supply coverage reports through the library convention.

Files use UTF-8 and LF, except Windows `.bat`/`.cmd` scripts use CRLF. Keep the wrapper
JAR committed and update its distribution checksum whenever updating Gradle.
