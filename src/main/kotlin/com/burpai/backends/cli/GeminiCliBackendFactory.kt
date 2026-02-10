package com.burpai.backends.cli

import com.burpai.backends.AiBackend
import com.burpai.backends.AiBackendFactory

class GeminiCliBackendFactory : AiBackendFactory {
    override fun create(): AiBackend = CliBackend(
        id = "gemini-cli",
        displayName = "Gemini CLI"
    )
}

