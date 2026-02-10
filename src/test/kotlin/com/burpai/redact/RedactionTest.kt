package com.burpai.redact

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RedactionTest {

    @Test
    fun strictModeStripsCookiesTokensAndHosts() {
        val input = """
            GET / HTTP/1.1
            Host: example.com
            Cookie: a=b
            Authorization: Bearer abc.def.ghi

        """.trimIndent()

        val policy = RedactionPolicy.fromMode(PrivacyMode.STRICT)
        val output = Redaction.apply(input, policy, stableHostSalt = "salt")

        assertTrue(output.contains("Cookie: [STRIPPED]"))
        assertTrue(output.contains("Authorization: [REDACTED]"))
        assertTrue(output.contains("Host: host-"))
    }

    @Test
    fun hostAnonymizationIsStablePerSalt() {
        val a = Redaction.anonymizeHost("example.com", "salt-a")
        val b = Redaction.anonymizeHost("example.com", "salt-a")
        val c = Redaction.anonymizeHost("example.com", "salt-b")

        assertEquals(a, b)
        assertTrue(a != c)
    }
}

