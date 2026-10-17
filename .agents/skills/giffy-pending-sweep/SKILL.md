---
name: giffy-pending-sweep
description: "Triage the parked/blocked/deferred/spec'd-not-scheduled items across all AGENTS-*.md docs and turn them into a verified work batch. Use when the user asks to 'work the pending items', 'review the docs and implement', plan a batch, or asks what is left to do."
---

# Giffy Viewer — Pending-Items Sweep

The AGENTS-*.md files are the spec. Each carries parked/deferred/blocked lists that
accumulate per session. This skill turns them into an actionable, de-duplicated batch.

## Procedure

1. **Harvest**: scan `AGENTS.md` + all `AGENTS-*.md` for: *parked*, *deferred*, *blocked*,
   *spec'd-not-scheduled*, *no-guess*, *pending*, *still open*, *re-evaluate*.
2. **Classify each item**:
   - **build-now** — has a numbered spec or clear owner, no external dependency.
   - **blocked-on-user** — needs a fresh token bundle, SIGNING secrets, a product decision.
     Name exactly what unblocks it.
   - **decided-skip** — explicitly rejected with a cost reason (minimap, RateLimitBus
     indicator, two-column…). Do not resurrect without a new ask.
   - **stale-already-built** — later sessions shipped it; the older mention is stale.
     VERIFY against code before believing either direction.
3. **Staleness check (mandatory)**: for every "not built" claim, grep the code for the
   feature before building it. History: "server collections" was fully built while docs
   still called it open; "TV vertical fullscreen-fill" same. Grep the owning module first.
4. **Gate conditions**: items blocked on a live check (e.g. "does the upstream 1-col feed
   autoplay inline?") get the check FIRST via the giffy-live-probe skill — never build
   past an open gate.
5. **Batch plan**: order build-now items by file-cluster overlap (one writer per cluster);
   state per item: change → verify gate (compile + ktlint + detekt + unit tests per
   touched module, device gate where UI/behavior claims are made).
6. **Execute + close the loop**: after landing, UPDATE the AGENTS docs — move items from
   pending to done with evidence, correct stale notes. An un-updated doc recreates the
   same sweep next session.

## Rules inherited from the repo

- No-guess rule: unverifiable API shapes stay blocked (Followers page row shape, niches
  suggest param) — never invent endpoints or DTOs.
- Fallbacks are spec'd, not scheduled: design the branch, don't build until live
  verification proves the primary path needs it.
- Simplicity first: minimum code, no speculative abstractions; one minimal self-check
  test for non-trivial logic.
