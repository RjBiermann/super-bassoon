package com.rjbiermann.giffyviewer.core.datastore

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * PIN storage format. A 4-digit PIN is a 10 000-value space, so with the
 * DataStore file readable (root/backup) a single salted SHA-256 is exhaustible
 * in milliseconds. PBKDF2 with a per-install random salt and [ITERATIONS]
 * rounds makes every guess cost a full derivation.
 *
 * Stored value: `pbkdf2$<iterations>$<saltHex>$<hashHex>`.
 * Legacy values are a bare SHA-256 of `pin + static salt`; they still verify so
 * an existing PIN keeps working and are upgraded on the next successful unlock.
 *
 * Pure JVM (no Android APIs) — the caller runs it off the main thread.
 */
internal object PinHash {
    private const val PREFIX = "pbkdf2"
    private const val ITERATIONS = 120_000

    /** Upper bound for a stored record — well above [ITERATIONS] so an
     *  upgraded future round count stays valid, far below a hang. */
    private const val MAX_ITERATIONS = 1_000_000
    private const val KEY_BITS = 256
    private const val SALT_BYTES = 16
    private const val LEGACY_SALT = "giffy_viewer_pin"
    private const val DELIMITER = '$'

    fun create(pin: String): String {
        val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
        return listOf(PREFIX, ITERATIONS.toString(), salt.toHex(), derive(pin, salt, ITERATIONS)).joinToString("$DELIMITER")
    }

    /** True when [pin] matches [stored], in either the KDF or the legacy format. */
    fun verify(
        pin: String,
        stored: String,
    ): Boolean {
        val parts = stored.split(DELIMITER)
        if (parts.size == 4 && parts[0] == PREFIX) {
            // Bounded: a corrupt/edited record with iterations = 0 threw out of
            // PBEKeySpec (crashing the lock screen) and a huge one hung the
            // verifying thread.
            val iterations = parts[1].toIntOrNull()?.takeIf { it in 1..MAX_ITERATIONS } ?: return false
            val salt = parts[2].hexToBytes() ?: return false
            val expected = parts[3].toByteArray()
            return MessageDigest.isEqual(derive(pin, salt, iterations).toByteArray(), expected)
        }
        return MessageDigest.isEqual(legacy(pin).toByteArray(), stored.toByteArray())
    }

    /** True when [stored] predates the KDF — the caller re-hashes after a match. */
    fun needsUpgrade(stored: String): Boolean = !stored.startsWith(PREFIX)

    private fun derive(
        pin: String,
        salt: ByteArray,
        iterations: Int,
    ): String =
        SecretKeyFactory
            .getInstance("PBKDF2WithHmacSHA1")
            .generateSecret(PBEKeySpec(pin.toCharArray(), salt, iterations, KEY_BITS))
            .encoded
            .toHex()

    private fun legacy(pin: String): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest((pin + LEGACY_SALT).toByteArray())
            .toHex()

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private fun String.hexToBytes(): ByteArray? {
        if (isEmpty() || length % 2 != 0) return null
        val out = ByteArray(length / 2)
        for (i in out.indices) {
            out[i] = substring(i * 2, i * 2 + 2).toIntOrNull(16)?.toByte() ?: return null
        }
        return out
    }
}
