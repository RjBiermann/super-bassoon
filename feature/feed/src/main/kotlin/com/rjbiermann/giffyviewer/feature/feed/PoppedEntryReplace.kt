package com.rjbiermann.giffyviewer.feature.feed

import androidx.navigation.NavController
import kotlin.reflect.KClass

/**
 * Issue #60 (GH): the "popped-entry replace" host seam — push [route] while
 * popping the TOPMOST entry of route class [popped] (inclusive). AGENTS-APP.md
 * "Player cluster opens are route pushes now": the player hands its stack slot
 * to the opened feed so BACK lands the covered feed at its scroll position.
 *
 * NOT `popUpTo(backStackEntry.id)`: `NavBackStackEntry.id` is a per-entry UUID
 * String, and `popUpTo(String)` matches DESTINATION routes — a UUID never
 * matches, popBackStackInternal logs "Ignoring popBackStack … not found on the
 * current back stack" and the player entry stays alive under the pushed feed
 * (BACK then relaunched the stale player, which pages the shared
 * activity-scoped FeedViewModel — already re-keyed to the opened feed — and
 * played the wrong feed). The KClass overload matches the topmost destination
 * of that route class; every call site sits ON the entry being replaced.
 * Regression: PlayerClusterReplaceNavTest.
 */
fun <P : Any> NavController.navigateReplacing(
    route: Any,
    popped: KClass<P>,
) {
    navigate(route) {
        popUpTo(popped) {
            inclusive = true
        }
    }
}
