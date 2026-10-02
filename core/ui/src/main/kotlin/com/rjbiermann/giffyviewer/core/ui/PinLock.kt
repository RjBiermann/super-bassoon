package com.rjbiermann.giffyviewer.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * App-lock PIN screen (PLAN §3 age gate + §9 lock). Shared mobile/TV: keys are
 * plain focusable buttons so the TV D-pad walks the grid with no extra
 * machinery; the first key takes initial focus. Brute-force slowdown (3+
 * wrong tries → 15 s) matches the mobile audit.
 */
@Composable
fun PinLockScreen(
    onUnlock: () -> Unit,
    verifyPin: suspend (String) -> Boolean,
) {
    val scope = rememberCoroutineScope()
    var entry by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    var fails by remember { mutableIntStateOf(0) }
    val firstKey = remember { FocusRequester() }
    val haptics = LocalHapticFeedback.current

    LaunchedEffect(Unit) { firstKey.requestFocus() }

    fun verify(pin: String) {
        scope.launch {
            val ok = verifyPin(pin)
            if (ok) {
                onUnlock()
            } else {
                fails++
                wrong = true
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                kotlinx.coroutines.delay(if (fails >= 3) 15_000L else 400L)
                entry = ""
                wrong = false
            }
        }
    }

    fun onDigit(d: Char) {
        if (entry.length >= 4) return
        entry += d
        if (entry.length == 4) verify(entry)
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(top = 96.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            if (wrong) "Wrong PIN" else "Enter PIN",
            color =
                if (wrong) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onBackground
                },
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(16.dp))
        // A11y (audit finding 5): entry progress announced as digit count,
        // not by dot color alone.
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.semantics { contentDescription = "${entry.length} of 4 digits entered" },
        ) {
            repeat(4) { i ->
                Box(
                    modifier =
                        Modifier
                            .size(14.dp)
                            .background(
                                if (i < entry.length) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outline
                                },
                                CircleShape,
                            ),
                )
            }
        }
        Spacer(Modifier.height(32.dp))
        val rows = listOf(listOf('1', '2', '3'), listOf('4', '5', '6'), listOf('7', '8', '9'), listOf('⌫', '0', '✓'))
        var keyIndex = 0
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                row.forEach { key ->
                    val requester = if (keyIndex == 0) Modifier.focusRequester(firstKey) else Modifier
                    keyIndex++
                    OutlinedButton(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.KeyboardTap)
                            when {
                                key == '⌫' -> if (entry.isNotEmpty()) entry = entry.dropLast(1)
                                // ✓ = submit now (audit: dead key); a 4th digit
                                // still auto-fires the same verify.
                                key == '✓' -> if (entry.length == 4) verify(entry)
                                else -> onDigit(key)
                            }
                        },
                        modifier = requester.then(Modifier.size(72.dp)),
                    ) {
                        Text(
                            key.toString(),
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                            modifier =
                                Modifier.clearAndSetSemantics {
                                    // A11y (audit finding 11): glyph keys get real
                                    // names instead of raw "backspace"/"check" reads.
                                    contentDescription =
                                        when (key) {
                                            '⌫' -> "Delete last digit"
                                            '✓' -> "Submit PIN"
                                            else -> key.toString()
                                        }
                                },
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}
