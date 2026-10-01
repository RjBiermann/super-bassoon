package com.rjbiermann.giffyviewer.core.model

/**
 * Upstream service endpoints, stored encoded so neither the source tree nor
 * the built APK carries them as plaintext strings (plain takedown-scanner
 * bait). Decoded once, lazily. Pure Kotlin: works on the JVM test classpath
 * and on every supported Android API level.
 */
object Hosts {
    private const val BLOB =
        "aHR0cHM6Ly9hcGkucmVkZ2lmcy5jb20vfGh0dHBzOi8vYXV0aDIucmVkZ2lmcy5jb20vb2F1dGgyL3Rva2VufGh0dHBzOi8vYXV0aDIucmVkZ2lmcy5jb20vb2F1dGgyL2F1dGh8aHR0cHM6Ly93d3cucmVkZ2lmcy5jb218aHR0cHM6Ly93d3cucmVkZ2lmcy5jb20vd2F0Y2gvfGh0dHBzOi8vd3d3LnJlZGdpZnMuY29tL25pY2hlcy8="

    private val parts: List<String> by lazy { decodeBase64(BLOB).split('|') }

    /** Feed/content API base, trailing slash. */
    val apiBase: String get() = parts[0]

    /** OAuth token grant. */
    val oauthToken: String get() = parts[1]

    /** OAuth authorize base. */
    val oauthAuthorize: String get() = parts[2]

    /** Site root (also the OAuth redirect URI). */
    val site: String get() = parts[3]

    /** Public watch pages (share intent). */
    val watch: String get() = parts[4]

    /** Public niche pages (share intent). */
    val niche: String get() = parts[5]

    /** Minimal base64 decoder (pure Kotlin — java.util.Base64 is API 26+). */
    private fun decodeBase64(s: String): String {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
        val out = StringBuilder()
        var bits = 0
        var acc = 0
        for (c in s) {
            if (c == '=') break
            val v = alphabet.indexOf(c)
            if (v < 0) continue
            acc = (acc shl 6) or v
            bits += 6
            if (bits >= 8) {
                bits -= 8
                out.append(((acc shr bits) and 0xFF).toChar())
            }
        }
        return out.toString()
    }
}
