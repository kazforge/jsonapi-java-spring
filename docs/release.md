# Spring release lifecycle

Spring releases independently from Core. Version semantics and the no-BOM decision
are recorded in [ADR 0006](adr/0006-independent-spring-release-lifecycle.md).

## Release flow

1. The [Release workflow](../.github/workflows/release.yml) runs on main.
   release-please maintains a release PR with the root version, manifest and changelog,
   starting at 0.1.0. Review these together and require green minimum/current CI and Sonar.
2. Merge the release PR only when product readiness and documentation are approved.
   This creates the `v<version>` tag and GitHub Release.
3. The publish job checks out that tag, builds and signs publications, attests primary
   JAR provenance and its CycloneDX SBOM, then uploads the bundle for automatic Central publication.

Publish `com.kazforge:jsonapi-java-spring-webmvc` and `com.kazforge:jsonapi-java-spring-boot-starter`
in one bundle. Each gets sources, Javadocs, POM, Gradle metadata, a signed `cyclonedx.json`
classifier and a primary JAR attested against its own SBOM. Each SBOM covers that
artifact's production dependencies, not tests or examples. The adapter stays
Jackson-neutral and consumes the published Core API/query baseline in the catalog; Spring's
version is independent.
The first release requires Spring Framework 7.0.9 or later in the supported 7.0.x line,
both as the supported minimum and the declared WebMVC API dependency. See
[compatibility](architecture.md#compatibility) for the minimum/current test policy.

Before announcing availability, verify Central publication, signatures and GitHub
attestations for both artifacts, then test two clean external consumers using only Central:
a Boot app with the starter and a standalone MVC app with its chosen runtime.
Stage the versioned starter setup guide in the release PR. A GitHub Release or successful
upload alone does not confirm Central availability.

## Recovery

- Release PR conflict: resolve against release-please's proposal and re-review the version.
- Missing tag or GitHub Release after merge: re-run the Release workflow; do not tag manually.
- Failed publication: inspect the Central deployment before retrying; re-run only the
  failed publish job. Re-running the whole workflow skips an existing release.
- Published artifacts and tags are immutable. Corrections require a new Spring version.
