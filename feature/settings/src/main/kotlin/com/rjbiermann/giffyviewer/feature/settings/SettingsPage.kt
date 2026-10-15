package com.rjbiermann.giffyviewer.feature.settings

/**
 * Settings sub-pages (issue #17): the screen keeps today's flat GiffyScaffold
 * surface but swaps panel content inside it — root is the grouped list, each
 * entry opens one panel. No entry for root: the root is the page key
 * [ROOT_PAGE]; the enum entries map 1:1 to future "settings/{page}" routes
 * when the navigation wave extracts the panels into destinations.
 */
enum class SettingsPage(
    val title: String,
) {
    General("General"),
    Video("Video"),
    Feeds("Feeds & filters"),
    Blocked("Blocked"),
    Backup("Backup"),
    About("About"),
}

/** Page key for the root list (not an enum entry — see [SettingsPage]). */
internal const val ROOT_PAGE = "root"
