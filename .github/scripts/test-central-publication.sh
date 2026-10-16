#!/usr/bin/env bash
set -euo pipefail

# No network or credentials: exercise the actual client with deterministic responses.
curl() {
  case "${*: -1}" in
    *'/upload?'*) printf '12345678-1234-1234-1234-123456789abc' ;;
    *'/status?'*) printf '{"deploymentState":"%s"}' "$TEST_STATE" ;;
    *'/deployment/'*) test "$TEST_STATE" = VALIDATED ;;
    *) return 1 ;;
  esac
}
export -f curl
export CENTRAL_USERNAME=test CENTRAL_PASSWORD=test
export TEST_STATE=VALIDATED
bash .github/scripts/central-publication.sh upload USER_MANAGED
export TEST_STATE=PUBLISHED
bash .github/scripts/central-publication.sh upload AUTOMATIC
if bash .github/scripts/central-publication.sh upload USER_MANAGED; then
  printf 'Rehearsal unexpectedly accepted public publication\n' >&2
  exit 1
fi
export TEST_STATE=FAILED
if bash .github/scripts/central-publication.sh upload AUTOMATIC; then exit 1; fi
export TEST_STATE=UNKNOWN
if bash .github/scripts/central-publication.sh upload AUTOMATIC; then exit 1; fi
if bash .github/scripts/central-publication.sh upload invalid; then exit 1; fi
printf 'Central client success and failure scenarios passed\n'
