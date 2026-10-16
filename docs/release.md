# Spring release lifecycle

Spring is independently versioned and released. Core artifacts are published separately;
the Spring release must consume an already published baseline from Maven Central.
Version semantics and the no-BOM decision are recorded in
[ADR 0006](adr/0006-independent-spring-release-lifecycle.md).

## Maintainer setup

Install the release GitHub App on this repository. Set repository variable
`RELEASE_APP_CLIENT_ID` and secret `RELEASE_APP_PRIVATE_KEY`; the App needs contents,
pull requests and issues write access. App-created release PRs must run normal CI.

Configure the `release` GitHub environment with required maintainer approval and
deployment restricted to `main`. Set `SIGNING_KEY` (ASCII-armored private OpenPGP
key), `SIGNING_PASSWORD` (empty for an unencrypted key), `CENTRAL_USERNAME` and
`CENTRAL_PASSWORD` (Central Portal user-token credentials) as environment secrets.
The public signing key must be discoverable by Central and the account must own
the `com.kazforge` namespace. Never put credentials in Gradle files or logs.

## Release and rehearsal

The repository-local [Release workflow](../.github/workflows/release.yml) maintains
a release PR on pushes to main. An empty initial manifest and explicit initial
version bootstrap 0.1.0; no existing public release is assumed. Review its version,
changelog, manifest and Gradle version together. Internal-only changes do not
justify merging an accumulated release PR.

Before the first public cut, leave the release PR unmerged. Manually dispatch the
workflow from main with an unpublished release version. After environment approval,
it builds both Spring selections, stages signed publications, attests the primary
JAR, and uploads a `USER_MANAGED` deployment. It waits for `VALIDATED`, then drops
that deployment. This is a real authenticated Central validation, not publication.

Only when product readiness and documentation are approved, merge the release PR.
release-please creates the tag and GitHub Release; the publication job builds that
exact tag, signs and stages publications, creates provenance/SBOM attestations, and
uploads to Central with `AUTOMATIC`. It waits for `PUBLISHED`. GitHub Release creation
alone is not evidence of Central availability: check Central and a clean external
consumer before announcing the release.

The publication contains the main, sources and Javadoc JARs, POM, Gradle metadata,
and a signed `cyclonedx.json` classifier. Its SBOM covers only WebMVC production
compile/runtime dependencies, not tests or the Jackson consumer example. GitHub
attestations bind the primary JAR digest to provenance and that SBOM; verify with
`gh attestation verify <jar> --repo kazforge/jsonapi-java-spring`.

For unsigned local inspection run `./gradlew publish -Pversion=0.1.0`; this stages
files only under `build/staging-deploy`. For signed inspection supply signing
properties via environment variables and add `-Prelease`; missing signing material
must fail. No local Maven repository or Core source checkout is required.

## Local automation checks

CI runs the credential-free Central client tests and verifies generated POM/Gradle
metadata for both Spring selections. To repeat version-policy checks locally, install
release-please into disposable tooling space (not the product dependency graph):

```shell
npm install --prefix /tmp/spring-release-policy --ignore-scripts --no-audit --no-fund release-please@17.3.0
NODE_PATH=/tmp/spring-release-policy/node_modules node .github/scripts/test-release-policy.cjs
bash .github/scripts/test-central-publication.sh
```

## Recovery

The workflow records the Central deployment ID in its summary. On timeout or runner
failure inspect that ID before retrying; upload success is not publication success.
Drop a validated rehearsal in the Portal if cleanup did not finish. Failed deployments
are retained for diagnosis. Do not blindly retry uploads or overwrite a published
version. Fix rejected unpublished bundles before retrying; published corrections need
a new version. Re-run a failed publication job rather than the entire release workflow:
release-please will not recreate an existing release. Never move public tags.

## Verified consumer baseline

The non-published [Jackson 3 WebMVC example](../examples/jackson3-webmvc/README.md)
verifies Core **0.4.0**: neutral API/query dependencies and the real Jackson 3 runtime
use the same catalog version. Its context-backed MockMvc tests exercise ID-less and
explicit-ID POST create, DTO/list responses, JSON:API negotiation, status/headers,
protocol errors, and unmarked MVC behavior. This is one consumer baseline, not a
multi-Core-version compatibility promise.

## Before a public Spring 0.x release

- Run `./gradlew clean spotlessCheck build` to verify the adapter at its minimum Spring
  version and the consumer at its separately fixed current Spring version.
- Run `./gradlew :jsonapi-java-spring-webmvc:clean :jsonapi-java-spring-webmvc:build -PspringTestVersion=current`
  to verify adapter forward compatibility without another consumer matrix leg.
- Check resolved consumer dependencies with
  `./gradlew :examples:jackson3-webmvc:dependencies --configuration runtimeClasspath`.
  Core artifacts must resolve to 0.4.0 from Maven Central, without `mavenLocal()` or substitution.
- Check adapter dependencies and eventual publication metadata: only neutral Core
  contracts belong in the Spring artifact; the example and Jackson runtime must not leak
   into it. Generate POM and Gradle metadata for both Spring selections; both must
   retain Core 0.4.0 and the minimum Spring dependencies without test BOM constraints.
- Require repository formatting, compiler/nullness, tests, coverage, and CI Sonar gates.

The adapter remains at the Spring 7.0.0 production compatibility floor. Applications
should use the current supported patch. Neither this example nor Core's version
determines the Spring artifact's release number.
