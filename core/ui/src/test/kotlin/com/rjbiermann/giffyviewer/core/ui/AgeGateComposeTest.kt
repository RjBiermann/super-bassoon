package com.rjbiermann.giffyviewer.core.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Issue #50 stage 2: first Compose UI tests on the JVM rung — the AgeGate
 * gate → unlock flow under Robolectric (no emulator; runs in `check`).
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class AgeGateComposeTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `gate renders title copy and both actions`() {
        rule.setContent {
            GiffyTheme {
                AgeGate(onConfirmed = {}, onExit = {})
            }
        }
        rule.onNodeWithText("Giffy Viewer").assertIsDisplayed()
        rule.onNodeWithText("I am 18 or older — Enter").assertIsDisplayed()
        rule.onNodeWithText("Exit (leaves app)").assertIsDisplayed()
    }

    @Test
    fun `enter confirms and remembered state survives recomposition`() {
        // Host-owned unlock state (the shells own it in production); `tick`
        // read in composition forces a host recomposition after confirmation
        // to prove the remembered `confirmed` state persists — no state loss.
        val tick = mutableIntStateOf(0)
        rule.setContent {
            GiffyTheme {
                var confirmed by remember { mutableStateOf(false) }
                if (confirmed) {
                    Box(modifier = Modifier, contentAlignment = Alignment.Center) {
                        Text("Home (tick=${tick.intValue})")
                    }
                } else {
                    AgeGate(onConfirmed = { confirmed = true }, onExit = {})
                }
            }
        }
        rule.onNodeWithText("I am 18 or older — Enter").performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("Home (tick=0)").fetchSemanticsNodes().size == 1 }
        rule.onNodeWithText("Home (tick=0)").assertIsDisplayed()
        rule.runOnIdle { tick.intValue++ }
        rule.onNodeWithText("Home (tick=1)").assertIsDisplayed()
    }

    @Test
    fun `exit fires the leave-app path`() {
        var exited = false
        rule.setContent {
            GiffyTheme {
                AgeGate(onConfirmed = {}, onExit = { exited = true })
            }
        }
        rule.onNodeWithText("Exit (leaves app)").performClick()
        rule.waitForIdle()
        assertTrue(exited)
        assertEquals(0, rule.onAllNodesWithText("Home").fetchSemanticsNodes().size)
    }
}
