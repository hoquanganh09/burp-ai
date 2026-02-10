package com.burpai.prompt

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PromptTemplateLibraryTest {

    @Test
    fun options_include_all_required_templates() {
        assertEquals(
            listOf(
                PromptTemplateLibrary.VULN_ANALYSIS_ONLY,
                PromptTemplateLibrary.EXPLOIT_STEPS,
                PromptTemplateLibrary.BUSINESS_LOGIC_CHECK,
                PromptTemplateLibrary.FULL_TECHNICAL_REPORT,
                PromptTemplateLibrary.CUSTOM
            ),
            PromptTemplateLibrary.options
        )
    }

    @Test
    fun custom_profile_uses_custom_text() {
        val custom = "Return only bullet points."
        val resolved = PromptTemplateLibrary.resolveInstruction(
            selectedProfile = PromptTemplateLibrary.CUSTOM,
            customTemplate = custom
        )
        assertEquals(custom, resolved)
    }

    @Test
    fun preset_profile_returns_non_blank_instruction() {
        val resolved = PromptTemplateLibrary.resolveInstruction(
            selectedProfile = PromptTemplateLibrary.EXPLOIT_STEPS,
            customTemplate = ""
        )
        assertTrue(resolved.isNotBlank())
    }
}
