package com.burpai.backends.cli

import com.burpai.backends.AiBackend
import com.burpai.backends.AiBackendFactory

class CodexCliBackendFactory : AiBackendFactory {
    override fun create(): AiBackend = CliBackend(
        id = "codex-cli",
        displayName = "Codex CLI"
    )
}

