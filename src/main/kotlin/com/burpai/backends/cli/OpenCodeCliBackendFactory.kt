package com.burpai.backends.cli

import com.burpai.backends.AiBackend
import com.burpai.backends.AiBackendFactory

class OpenCodeCliBackendFactory : AiBackendFactory {
    override fun create(): AiBackend = CliBackend(
        id = "opencode-cli",
        displayName = "OpenCode CLI"
    )
}

