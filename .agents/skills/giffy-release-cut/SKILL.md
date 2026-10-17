---
name: giffy-release-cut
description: "Cut a Giffy Viewer release safely: cut-release.yml workflow, tag discipline, versionCode guards, signature verification, release smoke. Use when the user asks to release, cut a version, tag, or when release CI fails."
---

# Giffy Viewer — Release Runbook

## Tag discipline (an incident already happened here)

- A hand-cut tag once MOVED the already-published v0.3.0 and silently re-released it.
  Tags are now cut ONLY via the `cut-release.yml` `workflow_dispatch`. Local
  `jj tag set`/`--allow-move` for tags is RETIRED for pushing.
- Before ANY tag-related push: `git tag --list 'v*'` + `gh release list` — verify the
  target tag is free. Never move an existing tag; released assets are immutable.
- Manual escape hatch (only while CI is down): verify the tag is free first, then cut,
  and record the deviation.

## The pipeline

1. `gh workflow run cut-release.yml -f bump=minor|major|patch` on default-branch HEAD.
2. Guards it runs for you: next vX.Y.Z computed from the highest existing tag;
   packed-semver versionCode (maj*10000+min*100+pat) must strictly increase; tag must not
   exist locally, on origin, or as a GitHub release; create-only push (no force);
   concurrency group serializes cuts.
3. `cut-release.yml` chains `release.yml` on the new tag (actions:write + remote-tag
   verified dispatch). This chaining exists because a tag pushed with `GITHUB_TOKEN` does
   NOT fire other workflows' push triggers — if a release doesn't start, dispatch it
   manually: `gh workflow run release.yml --ref vX.Y.Z` (HEAD-guard passes by
   construction when the tag sits on default-branch HEAD).

## Post-release verification

- Assets present: `GiffyViewer-mobile-<v>.apk` + `GiffyViewer-tv-<v>.apk`, R8-shrunk,
  disclaimer body.
- Signature: download both APKs, verify the SAME release certificate
  (`apksigner verify --print-certs`, expect SHA-256 `84048555…9eea5` — reuse-forever key).
- versionCode matches the tag math.

## Release smoke (before or right after a cut)

- Both release APKs build, install FRESH (`pm clear` first), and live-smoke on device:
  age gate → home renders (mobile + TV). R8 keep-rule changes additionally require
  re-running the release smoke (the rule files' own gate).

## Known noise (do not chase)

- Dependabot "Dependency Submission" submits gradle tooling deps (logback, bouncycastle,
  jose4j…) that don't exist in app build files → red Dependabot Updates runs + alert
  FAILs. Harmless to build/release; unresolved by decision, not oversight.
