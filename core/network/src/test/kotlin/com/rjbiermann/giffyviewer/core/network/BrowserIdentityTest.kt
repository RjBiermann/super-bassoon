package com.rjbiermann.giffyviewer.core.network

import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserIdentityTest {
    private val site = "https://www.redgifs.com"

    @Test
    fun `full browser header set is applied`() {
        val applied =
            BrowserIdentity
                .apply(
                    Request
                        .Builder()
                        .url("https://api.redgifs.com/v2/gifs/search")
                        .build(),
                    "461890711713879517",
                    site,
                )

        assertEquals(BrowserIdentity.USER_AGENT, applied.header("User-Agent"))
        assertEquals(BrowserIdentity.SEC_CH_UA, applied.header("Sec-Ch-Ua"))
        assertEquals("?1", applied.header("Sec-Ch-Ua-Mobile"))
        assertEquals("\"Android\"", applied.header("Sec-Ch-Ua-Platform"))
        assertEquals(BrowserIdentity.ACCEPT, applied.header("Accept"))
        assertEquals(BrowserIdentity.ACCEPT_LANGUAGE, applied.header("Accept-Language"))
        assertEquals(site, applied.header("Origin"))
        assertEquals("$site/", applied.header("Referer"))
        assertEquals("same-site", applied.header("Sec-Fetch-Site"))
        assertEquals("cors", applied.header("Sec-Fetch-Mode"))
        assertEquals("empty", applied.header("Sec-Fetch-Dest"))
        assertEquals("461890711713879517", applied.header("X-Session-Id"))
    }

    @Test
    fun `client hints are consistent with the UA version and platform`() {
        // Chrome major version must agree between UA and sec-ch-ua brand list.
        val uaMajor = Regex("""Chrome/(\d+)""").find(BrowserIdentity.USER_AGENT)!!.groupValues[1]
        val hintMajor = Regex("""\"Chromium\";v=\"(\d+)\"""").find(BrowserIdentity.SEC_CH_UA)!!.groupValues[1]
        assertEquals(uaMajor, hintMajor)
        // Android client → mobile hint ?1, platform "Android".
        assertTrue(BrowserIdentity.USER_AGENT.contains("Android"))
        assertEquals("?1", BrowserIdentity.SEC_CH_UA_MOBILE)
    }

    @Test
    fun `session ids are 19-digit numbers and unique`() {
        val a = BrowserIdentity.newSessionId()
        val b = BrowserIdentity.newSessionId()
        assertTrue("session id $a not 19 digits", a.length == 19 && a.all { it.isDigit() })
        assertTrue("session id $b not 19 digits", b.length == 19 && b.all { it.isDigit() })
        assertTrue("session ids collide", a != b)
    }

    @Test
    fun `existing headers are replaced, not duplicated`() {
        val request =
            Request
                .Builder()
                .url("https://api.redgifs.com/")
                .header("User-Agent", "stale-agent")
                .build()
        val applied = BrowserIdentity.apply(request, "1", site)
        assertEquals(1, applied.headers.names().count { it.equals("User-Agent", ignoreCase = true) })
    }
}
