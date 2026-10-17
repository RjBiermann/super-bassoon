---
name: giffy-device-gate
description: "Giffy Viewer emulator/device testing recipes: boot, install, evidence hierarchy (XML dumps over screenshots), wedge-vs-bug discrimination, clean resets, signed-in gates, input quirks, teardown. Use when verifying UI on device, debugging crashes, installing APKs, running soaks, or whenever a bug report might be an emulator artifact."
---

# Giffy Viewer — Device Gate Recipes

Operational recipes for verifying Giffy Viewer on the local emulators. Depth lives in
`AGENTS.md` (session lessons) and `AGENTS-APP.md`; this skill is the checklist you run.

## 0. Before any emulator work

- Run `free -h` first. The emulator wants ~2–4 GB; never stack it on a build. Stop gradle
  daemons (`./gradlew --stop`) before emulator work.
- **One emulator per host** (shared adb server `:5037` with the sibling workspace). If
  `emul-start` refuses, use the running emulator's serial — never kill or bypass it.
- Lifecycle: `emul-start [avd]` → `emul-wait [timeout]` (returns serial) → `emul-install [apk] [serial]`
  → work → `emul-stop` (pkill qemu-system) at teardown. Default AVD `dev` (API-35);
  TV: `tv_1080p` (created via `avdmanager -d tv_1080p` with the google_apis;x86_64 image).

## 1. Evidence hierarchy (rank matters)

1. **XML dump** (`adb exec-out uiautomator dump /dev/tty`) — primary evidence for IDs,
   text, focus, bounds. Deterministic and cheap.
2. **Screencap / pixel sampling** — for visual claims (colors, focus rings, AMOLED black,
   aspect ratios). Screencap is 1080x2340; `input tap` needs ORIGINAL coords, not the
   scaled coords a viewer shows.
3. **Screenshot as attachment** — last resort; each one burns against the ~30-image
   provider cap (saturation fails with `Too many images in request`).

Known evidence traps:
- **Dumps lag navigation** — a dump right after a screen change can show the previous
  screen. Re-dump after a short settle, or use screencap as state evidence.
- **A11y masking** — full-screen uiautomator dumps come up text-empty while the PlayerView
  interop is foreground; BACK restores. Not an app bug.
- **Mid-walk a11y labels lie** during settled inline autoplay — pixel hashes are the
  evidence there (batch-26 lesson).

## 2. Wedge-vs-bug discrimination (do this BEFORE diagnosing player issues)

Signature: ALL video surfaces black while playback keeps advancing (progress bar moves,
MediaCodec active). This is the documented AVD decoder wedge, not an app bug.

Discriminate: `adb shell screenrecord` 4s → size tells the truth (KBs = black frames,
MBs = real content) + look for `MediaCodec: rendring output error -32` in logcat.
Then one `am force-stop` + relaunch: frames return instantly = wedge. **Never open a
player regression from this signature alone.**

Decoder env: `ANDROID_EMU_MEDIA_DECODER_CUDA=0` + `..._CUDA_HEVC=0` must be in the
systemd USER environment (`systemctl --user set-environment` +
`~/.config/environment.d/90-emulator-decode.conf`) — shell exports do NOT reach the
`android` CLI's `systemd-run --user` scope.

## 3. Clean state

- Fresh profile: `adb shell pm clear <pkg>` — pkg is `com.rjbiermann.giffyviewer`
  (mobile) or `com.rjbiermann.giffyviewer.tv` (TV). Mobile activity path is
  `.mobile.MainActivity` (the class is nested under the app namespace).
- **A "feed is blank" report from a test device may be the stale
  `orientation_filter=horizontal` pref** — pool is all-portrait, filter is honest.
  `pm clear` + fresh age gate restores the grid. Not a bug.
- **TV AVD restart does NOT wipe data** — check DataStore before trusting "fresh profile"
  assumptions. A mid-test DB push can zero the main file: force-stop + `pm clear` is the
  clean reset.
- Emulator cold start: wait 45s+ after the first Enter before judging a grid empty.

## 4. Signed-in device gates (token bundles)

- Use the **DebugSignIn** seam: launch extra `giffy_debug_bundle` (`feature/auth/
  .../DebugSignIn.kt`), `BuildConfig.DEBUG`-only. Tokens NEVER go in source.
- Write bundles to `/tmp` only, mode 0600, delete after the session.
- id_tokens expire in ~1h; manual curl checks fail with TokenExpired — the app's silent
  refresh keeps itself signed in. Never probe the refresh grant (rotation rule).

## 5. Input quirks

- Held keys: `adb shell input keyevent --duration <ms>` — `-t` is NOT a valid flag and
  degrades to an instant tap.
- A shared `TextField` on TV TRAPS D-pad focus — a TV-hosted list screen MUST have
  `requestInitialFocus` on its first focusable.
- An action button INSIDE a full-row focusable is unreachable by directional search —
  side-by-side focusables instead.
- `am start` mobile: `com.rjbiermann.giffyviewer/.mobile.MainActivity`
  (TV: `com.rjbiermann.giffyviewer.tv/.TvMainActivity`).

## 6. Teardown & logging

- Goal: 0 FATAL exceptions per session. Grep logcat for FATAL before declaring success.
- Teardown: `emul-stop` + `./gradlew --stop`; confirm nothing long-lived remains.
- New device lessons go into `AGENTS.md` / `AGENTS-APP.md` (the canonical lesson log),
  not just the chat transcript.
