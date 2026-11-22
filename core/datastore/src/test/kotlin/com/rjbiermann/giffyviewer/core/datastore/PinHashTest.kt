package com.rjbiermann.giffyviewer.core.datastore

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PIN storage (2026-11 review): a 4-digit space must cost a full derivation per
 * guess, and an existing SHA-256-hashed PIN must keep unlocking (then upgrade).
 */
class PinHashTest {
    /** SHA-256("1234" + "giffy_viewer_pin") — the pre-KDF stored format. */
    private val legacy1234 = "661bf29af2654b123728bb60066dd62b8cd88647ca5e019e64c5809e3fc25a5f"

    @Test
    fun `kdf round trip accepts only the right pin`() {
        val stored = PinHash.create("1234")
        assertTrue(PinHash.verify("1234", stored))
        assertFalse(PinHash.verify("1235", stored))
    }

    @Test
    fun `same pin hashes differently per install (random salt)`() {
        val a = PinHash.create("1234")
        val b = PinHash.create("1234")
        assertNotEquals(a, b)
        assertTrue(PinHash.verify("1234", a))
        assertTrue(PinHash.verify("1234", b))
    }

    @Test
    fun `legacy hash verifies and is flagged for upgrade`() {
        assertTrue(PinHash.verify("1234", legacy1234))
        assertFalse(PinHash.verify("4321", legacy1234))
        assertTrue(PinHash.needsUpgrade(legacy1234))
        assertFalse(PinHash.needsUpgrade(PinHash.create("1234")))
    }

    @Test
    fun `malformed stored values never unlock`() {
        listOf("", "pbkdf2", "pbkdf2\$abc\$00\$00", "pbkdf2\$1\$zz\$00", "deadbeef").forEach { stored ->
            assertFalse("stored=$stored", PinHash.verify("1234", stored))
        }
    }

    @Test
    fun `out-of-range iteration counts are rejected instead of crashing or hanging`() {
        // iterations = 0 threw IllegalArgumentException out of PBEKeySpec.
        listOf("0", "-1", "999999999").forEach { iterations ->
            val stored = "pbkdf2\$$iterations\$00112233445566778899aabbccddeeff\$00"
            assertFalse("iterations=$iterations", PinHash.verify("1234", stored))
        }
    }
}
