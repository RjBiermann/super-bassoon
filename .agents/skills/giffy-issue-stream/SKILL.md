---
name: giffy-issue-stream
description: "Work GitHub issues as clustered streams: triage, design docs with file:line evidence, jj workspace lanes, per-module gates, merge, close with evidence comments. Use when implementing one or more GitHub issues, planning a multi-issue batch, or orchestrating parallel lanes."
---

# Giffy Viewer — Issue → Stream Workflow

Proven shape (batch 24: 27 issues closed in one orchestrated pass). Scale down for a
single issue — the gates and evidence rules always apply.

## 1. Triage

- Read each issue; map to the owning AGENTS doc (player → AGENTS-PLAYER, feeds/UI →
  AGENTS-APP, network → AGENTS-NETWORK…). Reject duplicates and verified-not-bugs first
  (see giffy-device-gate §2 for emulator-artifact discrimination).
- Cluster into streams by module overlap (e.g. A player, B feeds, C TV, D search…).
  Disjoint file clusters can run as parallel lanes; overlapping ones sequence.

## 2. Design before code

- One `DESIGN-<stream>.md` per stream: objective, file:line evidence from the current
  code, approach, acceptance conditions, explicit non-goals.
- Trace the entry point through the layers owning each invariant before editing.

## 3. Lanes (jj)

- One jj workspace per stream (`jj workspace add ../ws-<stream> -r @`), one writer per
  workspace. Name each lane with a bookmark before switching.
- Pitfalls (paid for in batch 24): jj workspaces snapshot lazily — run `jj st` INSIDE a
  workspace before using it as a bookmark target or the push backs an empty commit;
  `jj abandon` on a workspace's @ diverges bookmarks — resolve with
  `jj bookmark set <name> -r <survivor>`.
- Push backup bookmarks at EVERY milestone (`stream-<x>-wip`); never force main, never
  move tags.

## 4. Gates (per touched module, per harvest)

- compile + ktlint + detekt + unit tests + assembleDebug both apps; RAM-serialized
  (`free -h` first, `--no-daemon --max-workers=2`, one heavy op at a time).
- **Device passes earn their keep**: batch 24's static-green code hid 3 crash-class bugs
  (NavType crashes) that only device verification caught. UI/behavior claims get a device
  gate per giffy-device-gate.
- Regression tests for each fixed bug (the one-minimal-test rule).

## 5. Close

- Merge lanes into main via jj; verify reachability; clean up worktrees.
- Close each issue with an evidence comment (what shipped, how verified, file refs).
- Write the session paragraph into `AGENTS.md` (what landed, lessons, what remains).
