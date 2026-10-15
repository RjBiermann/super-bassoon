package com.rjbiermann.giffyviewer.tv

/**
 * Issue #13 §4.3: D-pad focus restore across a nav entry's covered window.
 * Nav restores SCROLL for free (saveable list/grid states, per entry); focus
 * state is composition-transient, so each focusable-list destination saves
 * its last-focused card as a saveable and the entry re-arms it on re-entry —
 * once per appearance (the re-arm latch at the call sites is deliberately
 * NOT saveable: focus moves thereafter must not re-trigger a grab).
 *
 * Pure decision the wiring consumes (FocusRestoreTest covers it): the card
 * the FocusRequester attaches to and the restore effect scrolls to. A saved
 * card that still exists (item count covers it) is restored; a fresh entry
 * (no saved position) or a regrown-shorter pool falls back to card 0.
 */
internal fun focusTargetFor(
    savedFocusItem: Int,
    itemCount: Int,
): Int = savedFocusItem.takeIf { it in 0 until itemCount } ?: 0
