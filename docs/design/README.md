# docs/design — working design docs

Issue/stream-scoped design and investigation docs live here, nowhere else (root is for
`AGENTS-*.md` only). A doc earns a slot by being **read** — if nothing in the tree points
at it, it does not belong here.

## Naming

`DESIGN-<issue-number>.md` for one issue (`DESIGN-47s3.md` when an issue ships in slices),
`DESIGN-<a>-<b>.md` for a coupled pair, `DESIGN-<stream|topic>.md` for a multi-issue stream
or a cross-cutting spec. No date prefixes; the issue number/stream name is the key.

## Lifecycle

1. **Design phase** — write before code: objective, `file:line` evidence from the current
   tree, approach, acceptance conditions, explicit non-goals. Live-probe results
   (row shapes, caps, auth behaviour) go in verbatim; they are the doc's main asset.
2. **Implementation** — flip the status header as work lands (`landed` on <commit range>,
   device-gate evidence, what was NOT exercised).
3. **Harvest, then delete** — when the issue closes, move what must survive upstream:
   - durable rules/invariants → the matching `AGENTS-*.md` (§10 doc hygiene: rules, not story)
   - chronology and gate evidence → the issue comment + `docs/SESSION-LOG.md`
   - anything code keeps citing → either inline it in the code comment, or keep the doc
   Removing a doc is normal, not loss: `jj log -- docs/design/<file>` recovers the text.
   Deleted here already: `DESIGN-59`, `-60`, `-62`, `-63`, `-64`, `-feeds`, `-player-quality`,
   `-tv-player`.

## Keep-criterion

Keep a doc only while one of these holds:

- code/tests cite it (e.g. `Theme.kt` → `DESIGN-theme-stream.md §1.1`), or
- it describes work still **unbuilt** (a shape decision the next implementer must follow).

A landed doc with zero citations is rot: its status line goes stale and it competes with
`AGENTS-*.md` for authority. Delete it.

## Current

| Doc | Why it is still here |
|---|---|
| `DESIGN-theme-stream.md` | Issue #7 theme spec; code cites §1.1/§3.1 tokens |
| `DESIGN-47s3.md` | Issue #47 slice 3; `GifsApi.kt` cites the previews row shape |
| `DESIGN-47SORTS.md` | Issue #47 sort remainder; `NichesViewModel/Screen` + sort test cite it |
| `DESIGN-search.md` | Issue #57; `SearchViewModel` + its test cite hyp 3 |
| `DESIGN-A11YFIX.md` | Issue #51 log; `OverlayContrast.kt` + test cite the finding |
| `DESIGN-65-66.md` | #65/#66 paging shape F2/F3 — designed, **not built** (`AGENTS.md`) |
