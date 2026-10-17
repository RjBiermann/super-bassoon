package com.rjbiermann.giffyviewer.feature.settings

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rjbiermann.giffyviewer.core.ui.GiffyTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Issue #50 stage 2: SettingsScreen section() headers carry heading semantics
 * for TalkBack heading navigation (issue #51).
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class SectionHeadingSemanticsTest {
    @get:Rule
    val rule = createComposeRule()

    private val headingCount: Int
        get() =
            rule
                .onAllNodes(
                    SemanticsMatcher("is heading") {
                        it.config.getOrNull(SemanticsProperties.Heading) != null
                    },
                ).fetchSemanticsNodes()
                .size

    @Test
    fun `section header carries heading semantics`() {
        rule.setContent {
            GiffyTheme {
                LazyColumn {
                    section("Blocked creators", listOf("@a", "@b"), onRemove = {})
                }
            }
        }
        rule.onNodeWithText("Blocked creators").assertExists()
        assertEquals(1, headingCount)
    }

    @Test
    fun `empty section renders no heading`() {
        rule.setContent {
            GiffyTheme {
                LazyColumn {
                    section("Blocked keywords", emptyList(), onRemove = {})
                }
            }
        }
        assertEquals(0, headingCount)
    }
}
