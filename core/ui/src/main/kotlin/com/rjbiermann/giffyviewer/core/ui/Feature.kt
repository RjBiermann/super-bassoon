package com.rjbiermann.giffyviewer.core.ui

/** Product feature gates — one place each, no config plumbing
 *  (no config for fixed values). */
object Feature {
    /** Custom feeds (#10): the builder + management surfaces are disabled
     *  for now; DB rows and the BLOCKED-feed filter stage keep working
     *  underneath (re-enable = flip this flag). */
    const val CUSTOM_FEEDS = false
}
