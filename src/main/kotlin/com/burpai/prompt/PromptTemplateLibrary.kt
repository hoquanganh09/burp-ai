package com.burpai.prompt

object PromptTemplateLibrary {
    const val VULN_ANALYSIS_ONLY = "Vuln Analysis Only"
    const val EXPLOIT_STEPS = "Exploit Steps"
    const val BUSINESS_LOGIC_CHECK = "Business Logic Check"
    const val FULL_TECHNICAL_REPORT = "Full Technical Report"
    const val CUSTOM = "Custom"

    val options: List<String> = listOf(
        VULN_ANALYSIS_ONLY,
        EXPLOIT_STEPS,
        BUSINESS_LOGIC_CHECK,
        FULL_TECHNICAL_REPORT,
        CUSTOM
    )

    private val presetInstructions: Map<String, String> = mapOf(
        VULN_ANALYSIS_ONLY to (
            "Template mode: Vulnerability analysis only. " +
                "Return only security findings with evidence and severity. " +
                "Do not include long background explanation."
            ),
        EXPLOIT_STEPS to (
            "Template mode: Exploit steps. " +
                "For each finding, provide reproducible PoC steps, payload ideas, and expected verification output."
            ),
        BUSINESS_LOGIC_CHECK to (
            "Template mode: Business logic check. " +
                "Prioritize workflow abuse, authorization gaps, race conditions, and state-transition flaws."
            ),
        FULL_TECHNICAL_REPORT to (
            "Template mode: Full technical report. " +
                "Return structured output with summary, findings, evidence, impact, exploitability, and remediation."
            )
    )

    fun resolveInstruction(selectedProfile: String, customTemplate: String): String {
        val normalized = selectedProfile.trim().ifBlank { VULN_ANALYSIS_ONLY }
        if (normalized == CUSTOM) {
            return customTemplate.trim()
        }
        return presetInstructions[normalized].orEmpty()
    }
}
