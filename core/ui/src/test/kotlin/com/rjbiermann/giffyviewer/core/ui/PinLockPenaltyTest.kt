package com.rjbiermann.giffyviewer.core.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Brute-force slowdown (PLAN §3/§9): after a wrong guess the keypad must stay
 * deaf for the penalty window. The old flow cleared the entry only AFTER the
 * delay, so ⌫ (still active on a 4-char entry) + one digit re-fired a fresh
 * guess instantly — the 15s penalty throttled nothing.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class PinLockPenaltyTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `keys are ignored while a wrong guess is being penalised`() {
        var attempts = 0
        rule.setContent {
            GiffyTheme {
                PinLockScreen(
                    onUnlock = {},
                    verifyPin = {
                        attempts++
                        false
                    },
                )
            }
        }
        // Keys carry their digit as the a11y label (glyph keys get real names).
        listOf("1", "2", "3", "4").forEach { digit -> rule.onNodeWithContentDescription(digit).performClick() }
        rule.waitForIdle()
        assertEquals(1, attempts)

        // Backspace + a digit must not start a second guess during the penalty.
        rule.onNodeWithContentDescription("Delete last digit").performClick()
        rule.onNodeWithContentDescription("9").performClick()
        rule.waitForIdle()
        assertEquals(1, attempts)
    }
}
