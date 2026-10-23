package com.rjbiermann.giffyviewer.core.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role

/**
 * Shared empty state (PLAN §9 gap #8): dark fill, one line (+ optional hint).
 * onClick (issue #61): when set, the whole card becomes a retry target
 * (role Button); null leaves the card inert for all other callers.
 * onClickLabel (issue #63): the semantics action label, so a card that opens
 * sign-in instead of retrying announces what it will actually do.
 */
@Composable
fun EmptyState(
    message: String,
    modifier: Modifier = Modifier,
    hint: String? = null,
    onClick: (() -> Unit)? = null,
    onClickLabel: String = "Retry",
) {
    Box(
        modifier =
            if (onClick != null) {
                modifier.clickable(onClickLabel = onClickLabel, role = Role.Button) { onClick() }
            } else {
                modifier
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(message, style = MaterialTheme.typography.titleMedium)
            hint?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
