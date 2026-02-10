package com.burpai.backends.cli

import com.burpai.backends.AiBackend
import com.burpai.backends.AiBackendFactory

class ClaudeCliBackendFactory : AiBackendFactory {
    override fun create(): AiBackend = CliBackend(
        id = "claude-cli",
        displayName = "Claude Code"
    )
}

