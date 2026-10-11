# Architecture decision records

ADRs preserve durable architectural decisions and their rationale.
[Architecture](../architecture.md) describes current state; ADRs do not replace it.
Use an ADR for consequential product, dependency, compatibility, or module-boundary
decisions, not every implementation choice, work log, or copy of a Linear ticket.

## Convention

Use sequential four-digit numbering and a short descriptive filename:
`0001-decision-title.md`, `0002-decision-title.md`, and so on. Choose the next unused
number, resolve concurrent numbering conflicts before merge, and never reuse or
renumber an existing record. Add each record to the index below.

Each record contains a title, status, date, and three sections: **Context**,
**Decision**, and **Consequences**. Use statuses `Proposed`, `Accepted`, `Rejected`,
or `Superseded`. The date records when the decision is documented, not an inferred
historical acceptance date. An accepted constraint may precede implementation;
state that distinction explicitly.

Keep records short and decision-focused. A material replacement gets a new ADR;
mark the old record `Superseded`, link to the replacement, and link back from the
new record. Preserve the original rationale rather than silently rewriting it.
Correcting typos or broken links does not require a new ADR.

Update current-state docs and create/supersede an ADR in the same implementation PR
when warranted, as required by [Contributing](../../CONTRIBUTING.md#repository-documentation).
User-facing setup/tutorials are separate documentation, owned by
[KAZ-210](https://linear.app/kazforge/issue/KAZ-210).

## Index

| ADR | Status |
| --- | --- |
| [0001 — Separate Spring companion product](0001-separate-spring-companion-product.md) | Accepted |
| [0002 — Neutral WebMVC adapter boundary](0002-neutral-webmvc-adapter-boundary.md) | Accepted |
