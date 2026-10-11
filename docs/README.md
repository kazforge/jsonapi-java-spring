# Repository documentation

These documents are the canonical maintainer record for `jsonapi-java-spring`.
Code and build configuration establish what is implemented; repository documentation
explains the current boundaries and the durable decisions behind them.

| Document | Ownership |
| --- | --- |
| [Architecture](architecture.md) | Current repository/product boundaries, module ownership, and compatibility policy. |
| [Architecture decision records](adr/README.md) | Decision rationale and history, not a feature guide or implementation journal. |
| [Contributing](../CONTRIBUTING.md) | Development, quality checks, and the same-PR documentation maintenance rule. |

User-facing Spring installation, compatibility details, wiring, and tutorials belong
to [KAZ-210](https://linear.app/kazforge/issue/KAZ-210). They are not implemented by
this maintainer documentation baseline. When those docs exist, this index should link
to them; compatibility/version tables should have one canonical location rather than
be repeated in architecture docs or ADRs.

The [jsonapi-java guide](https://jsonapi.kazforge.com/) remains canonical for
framework-neutral JSON:API concepts and behavior. Spring documentation should link
to it, not duplicate it. Linear and PR discussions provide work context, but are not
the canonical architecture record.
