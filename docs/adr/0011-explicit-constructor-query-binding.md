# ADR 0011: Bind typed queries by exact constructor inputs

Status: Accepted

Date: 2026-11-25

## Context

[ADR 0010](0010-keep-filter-and-page-semantics-application-owned.md) leaves opaque query
semantics with the application. Controllers need enum, numeric and repeated values without
reimplementing conversion or treating decoded keys as Spring bean property paths.

## Decision

On an opted-in endpoint, `@JsonApiQueryParams` selects a separately registered argument resolver.
It reads only the current `JsonApiRequestContext.query()`. A record uses its canonical constructor;
a POJO must have a single public constructor. Spring's `@BindParam` declares the exact opaque
key on each input. A dot or bracket in a key is literal. One unannotated `JsonApiQuery`
parameter can receive the original query; the controller can inspect requested sort, includes and
fieldsets without changing the selection used by the writer.

A declared input is a Spring simple reference type (excluding `Class`) with MVC string conversion,
or `List<T>` of one concrete such type. A missing scalar is null; a missing list is empty.
Strings retain explicit empties. Other supplied values must convert to a non-null result.
Repeated scalars are invalid. Lists convert each occurrence independently in order, retaining
duplicates and never splitting a single occurrence at commas.

Undeclared opaque keys, incorrect cardinality and invalid input produce safe JSON:API 400s.
`@Valid` validates the constructed DTO. Errors on directly mapped properties or list elements
identify the exact supplied key. For POJOs, compile with `-parameters` and use corresponding
property names for validation attribution. Missing inputs, class violations and unrecognized
paths have no source. Bad DTO declarations and constructor failures are application errors.

## Consequences

Numbered article searches can bind a status and page numbers; event feeds can bind repeated
kinds and an opaque continuation token through the same MVC conversion service. There is no
second parser, mutable property binding or additional representation model. Controllers needing
different cardinalities or custom input rules can continue to take `JsonApiRequestContext`.
