#!/usr/bin/env bash
set -euo pipefail

case "${1:-}" in
  bundle)
    version="${2:?version required}"
    [[ "$version" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]
    base="jsonapi-java-spring-webmvc-$version"
    directory="com/kazforge/jsonapi-java-spring-webmvc/$version"
    stage=build/staging-deploy
    # An explicit inventory prevents examples or stale versions entering the bundle.
    for suffix in .jar -sources.jar -javadoc.jar .pom .module -cyclonedx.json; do
      test -s "$stage/$directory/$base$suffix"
      test -s "$stage/$directory/$base$suffix.asc"
    done
    rm -f build/central-bundle.zip
    (cd "$stage" && zip -qr ../central-bundle.zip "$directory")
    ;;
  upload)
    mode="${2:?mode required}"
    [[ "$mode" = AUTOMATIC || "$mode" = USER_MANAGED ]]
    test -n "${CENTRAL_USERNAME:-}"
    test -n "${CENTRAL_PASSWORD:-}"
    token=$(printf '%s:%s' "$CENTRAL_USERNAME" "$CENTRAL_PASSWORD" | base64 | tr -d '\n')
    if [ "${GITHUB_ACTIONS:-}" = true ]; then printf '::add-mask::%s\n' "$token"; fi
    api=https://central.sonatype.com/api/v1/publisher
    id=$(curl --fail-with-body --silent --show-error \
      -H "Authorization: Bearer $token" -F bundle=@build/central-bundle.zip \
      "$api/upload?publishingType=$mode")
    [[ "$id" =~ ^[a-fA-F0-9-]{36}$ ]]
    printf 'Central deployment: %s (%s)\n' "$id" "$mode"
    # Retain the ID for recovery if validation times out or the runner stops.
    if [ -n "${GITHUB_STEP_SUMMARY:-}" ]; then
      printf 'Central deployment: `%s` (%s)\n' "$id" "$mode" >> "$GITHUB_STEP_SUMMARY"
    fi
    for ((attempt=0; attempt<120; attempt++)); do
      result=$(curl --fail-with-body --silent --show-error -X POST \
        -H "Authorization: Bearer $token" "$api/status?id=$id")
      state=$(jq -er .deploymentState <<< "$result")
      case "$state" in
        VALIDATED)
          if [ "$mode" = USER_MANAGED ]; then
            curl --fail-with-body --silent --show-error -X DELETE \
              -H "Authorization: Bearer $token" "$api/deployment/$id"
            printf 'Validation succeeded; rehearsal deployment dropped.\n'
            exit 0
          fi
          ;;
        PUBLISHED)
          test "$mode" = AUTOMATIC
          exit 0
          ;;
        FAILED) printf '%s\n' "$result" >&2; exit 1 ;;
        PENDING|VALIDATING|PUBLISHING) ;;
        *) printf 'Unexpected Central state: %s\n' "$state" >&2; exit 1 ;;
      esac
      sleep 10
    done
    printf 'Timed out; inspect deployment %s in Central before retrying.\n' "$id" >&2
    exit 1
    ;;
  *) printf 'Usage: %s bundle VERSION | upload AUTOMATIC|USER_MANAGED\n' "$0" >&2; exit 2 ;;
esac
