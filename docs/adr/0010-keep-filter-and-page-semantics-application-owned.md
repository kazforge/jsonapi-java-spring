# ADR 0010: Keep filter and page semantics application-owned

Status: Accepted

Date: 2026-11-24

## Context

Today a WebMVC controller can read `JsonApiRequestContext.query()`. As described in
[ADR 0004](0004-operation-aware-request-and-query-context.md), Core parses the raw query
once. `JsonApiQuery.filter()` and `page()` expose exact decoded parameter names and their
values, with repeated values in request order. The resource writer already uses the
request's `RepresentationSelection` for includes and sparse fieldsets. A collection
controller can supply its own pagination links and meta through `JsonApiResponse`.

Consider two application controllers using those contracts. Today the mapping from query
values to each application's record is explicit:

- **Article search:** `filter[status]=PUBLISHED&page[number]=2&page[size]=20` supplies
  `ArticleSearch(Status status, int number, int size)`. The article service matches the
  status, numbers pages from one, caps the size, and returns a total for the response meta.
- **Event feed:** `filter[kind]=CREATE&filter[kind]=UPDATE&page[after]=evt42` supplies
  `EventFeed(List<EventKind> kinds, String after)`. The feed service matches either kind,
  resumes after the position identified by its cursor, and supplies a next link when more
  events exist. It need not count events or assign page numbers.

## Decision

Keep `filter[...]` and `page[...]` opaque in Core. The examples share the work of reading
named values and converting them to Java types. They do not share a filter predicate,
page calculation, or response metadata schema. A shared model would impose one
application's choices on the other.

Future opt-in WebMVC binding should use Spring conversion and validation to populate
application-owned records or POJOs from the existing `JsonApiQuery`. This is a design
constraint for a future controller API; automatic query DTO binding is not available today.

## Consequences

Core remains the only parser and the source of parameter names, ordering, sort tokens,
and representation selection. Binding does not override the selection passed to the writer.
Applications define supported filters, pagination, authorization, and query execution.
Typed binding needs no new public Core API. Reconsider only if multiple integrations
demonstrate the same semantics that the existing query values cannot express.
