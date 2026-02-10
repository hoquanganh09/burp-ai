package com.burpai.redact

object PromptRedactor {
    fun redactSensitiveData(text: String): String {
        return text
            .replace(Regex("(?i)Authorization:\\s*Bearer\\s+\\S+"), "Authorization: Bearer [REDACTED]")
            .replace(Regex("(?i)cookie:\\s*[^\\n]+"), "Cookie: [REDACTED]")
            .replace(Regex("(?i)api[_-]?key\\s*[=:]\\s*\\S+"), "api_key=[REDACTED]")
            .replace(Regex("\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\\b"), "[EMAIL REDACTED]")
    }

    fun redactIfNeeded(text: String, privacyMode: PrivacyMode): String {
        return if (privacyMode == PrivacyMode.OFF) text else redactSensitiveData(text)
    }
}
