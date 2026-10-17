# Security policy

Maintainers own vulnerability triage and merge protection. Dependabot Alerts provide dependency
vulnerability visibility; Renovate is the sole dependency-update and security-fix PR bot. CodeQL
provides code vulnerability findings; SonarCloud remains a separate quality gate. Inspect GitHub's
Security tab and native Actions results when assessing these signals.

Dependency Review blocks introduced High/Critical dependency vulnerabilities. Merge protection must
block High/Critical CodeQL security findings. Maintainers triage Moderate/Low findings without an
automatic merge block, verify applicable fixes, and document the rationale for dismissed alerts.

Dependency graph generation and review use stock GitHub/Gradle capabilities and do not guarantee
complete snapshots. Investigate native failures and snapshot warnings; a successful review alone
does not establish complete dependency coverage. Dependency verification remains enabled.
