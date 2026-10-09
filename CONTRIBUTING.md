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
to resolve build tooling. Publication/signing and Spring behavior are separate work.

Files use UTF-8 and LF, except Windows `.bat`/`.cmd` scripts use CRLF. Keep the wrapper
JAR committed and update its distribution checksum whenever updating Gradle.
