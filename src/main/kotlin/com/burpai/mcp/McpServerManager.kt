package com.burpai.mcp

import com.burpai.config.McpSettings
import com.burpai.redact.PrivacyMode

interface McpServerManager {
    fun start(settings: McpSettings, privacyMode: PrivacyMode, determinismMode: Boolean, callback: (McpServerState) -> Unit)
    fun stop(callback: (McpServerState) -> Unit)
    fun shutdown()
}

