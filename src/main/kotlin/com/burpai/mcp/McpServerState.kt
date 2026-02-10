package com.burpai.mcp

sealed class McpServerState {
    data object Starting : McpServerState()
    data object Running : McpServerState()
    data object Stopping : McpServerState()
    data object Stopped : McpServerState()
    data class Failed(val exception: Throwable) : McpServerState()
}

