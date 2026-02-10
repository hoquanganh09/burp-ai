package com.burpai.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class McpSettingsTest {
    @Test
    fun roundTripToolToggles() {
        val input = mapOf(
            "http1_request" to false,
            "url_encode" to true
        )
        val serialized = McpSettings.serializeToolToggles(input)
        val parsed = McpSettings.parseToolToggles(serialized)
        assertEquals(input, parsed)
    }

    @Test
    fun tokenGenerationProducesNonEmptyValue() {
        val token = McpSettings.generateToken()
        assertTrue(token.isNotBlank())
        assertTrue(token.length >= 32)
    }
}

