package com.rjbiermann.giffyviewer.core.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Parser + refresh-shape checks for TokenStore's companion helpers (JVM-pure). */
class TokenStoreTest {
    @Test
    fun `parseRefreshResponse accepts only a JWT id_token`() {
        assertEquals("x.y.z", TokenStore.parseRefreshResponse("""{"id_token":"x.y.z"}"""))
        assertNull(TokenStore.parseRefreshResponse("""{"access_token":"x.y.z"}"""))
        assertNull(TokenStore.parseRefreshResponse("""{"error":"invalid_grant"}"""))
    }

    @Test
    fun `looksLikeJwt accepts base64url-only 3-part tokens`() {
        assertTrue(TokenStore.looksLikeJwt("aB9-x_Y.aB9-x_Y.aB9-x_Y"))
        assertFalse(TokenStore.looksLikeJwt("a.b"))
        assertFalse(TokenStore.looksLikeJwt("a.b.c d"))
        assertFalse(TokenStore.looksLikeJwt("a.b..c"))
    }

    @Test
    fun `pkce pair is url-safe and state unique`() {
        val a = TokenStore.newPkce()
        val b = TokenStore.newPkce()
        assertTrue(a.verifier.length in 43..128)
        assertTrue(a.verifier.all { it.isDigit() || it.isLowerCase() })
        assertTrue(a.challenge.all { it.isLetterOrDigit() || it == '-' || it == '_' })
        assertFalse(a.challenge.endsWith('='))
        assertEquals(16, a.state.length)
        assertTrue(a.verifier != b.verifier && a.state != b.state)
    }

    @Test
    fun `authorizeUrl carries pkce params`() {
        val pkce = TokenStore.Pkce("v".repeat(64), "challenge123", "state123")
        val url = TokenStore.authorizeUrl(pkce)
        assertTrue(
            url.startsWith(
                TokenStore.OAUTH_REDIRECT_URI.take(8) + TokenStore.OAUTH_TOKEN_URL.drop(8).substringBefore("/oauth2/token"),
            ),
        )
        assertTrue(url.contains("client_id=" + TokenStore.OAUTH_CLIENT_ID))
        assertTrue(url.contains("response_type=code"))
        assertTrue(url.contains("code_challenge=challenge123"))
        assertTrue(url.contains("code_challenge_method=S256"))
        assertTrue(url.contains("state=state123"))
    }

    @Test
    fun `extractCode reads code only from our redirect with matching state`() {
        val ok =
            TokenStore.OAUTH_REDIRECT_URI +
                "/?code=abc.Xyz&scope=openid%20profile&state=s1"
        assertEquals("abc.Xyz", TokenStore.extractCode(ok, "s1"))
        assertNull(TokenStore.extractCode(ok, "wrong"))
        assertNull(
            TokenStore.extractCode(
                "https://evil.example/?code=abc.Xyz&state=s1",
                "s1",
            ),
        )
        assertNull(
            TokenStore.extractCode(
                TokenStore.OAUTH_REDIRECT_URI + "/?state=s1",
                "s1",
            ),
        )
    }

    @Test
    fun `parseTokenBundle requires JWT id_token and keeps refresh`() {
        val body =
            """{"access_token":"a","id_token":"a.b.c","refresh_token":"rt","expires_in":86400,"scope":"openid offline"}"""
        val bundle = TokenStore.parseTokenBundle(body)!!
        assertEquals("a.b.c", bundle.idToken)
        assertEquals("rt", bundle.refreshToken)
        assertNull(TokenStore.parseTokenBundle("""{"id_token":"bad"}"""))
        assertNull(TokenStore.parseTokenBundle("not json"))
    }

    @Test
    fun `parseTokenBundle tolerates missing refresh_token`() {
        val bundle = TokenStore.parseTokenBundle("""{"id_token":"a.b.c"}""")!!
        assertNull(bundle.refreshToken)
    }

    /** Fabricated payload (base64url unpadded) — mirrors the live id_token shape. */
    private val payloadB64 =
        TokenStore
            .newPkce()
            .let { _ ->
                kotlin.io.encoding.Base64
                    .UrlSafe
                    .withPadding(kotlin.io.encoding.Base64.PaddingOption.ABSENT)
                    .encode("""{"preferred_username":"testuser","sub":"kp_x"}""".encodeToByteArray())
            }

    @Test
    fun `usernameFromJwt reads the live-verified preferred_username claim`() {
        assertEquals("testuser", TokenStore.usernameFromJwt("h.$payloadB64.sig"))
        assertNull(TokenStore.usernameFromJwt("h." +
            kotlin.io.encoding.Base64
                .UrlSafe
                .withPadding(kotlin.io.encoding.Base64.PaddingOption.ABSENT)
                .encode("""{"sub":"kp_x"}""".encodeToByteArray()) + ".sig"))
        assertNull(TokenStore.usernameFromJwt("garbage"))
        assertNull(TokenStore.usernameFromJwt("a." + "".padEnd(4, 'x') + ".c"))
    }
}
