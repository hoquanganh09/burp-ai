package com.six2dez.burp.aiagent.ui.panels

import com.six2dez.burp.aiagent.ui.UiTheme
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel

class McpConfigPanel(
    private val sectionPanel: (String, String, JComponent) -> JPanel,
    private val formGrid: () -> JPanel,
    private val addRowFull: (JPanel, String, JComponent) -> Unit,
    private val addRowPair: (JPanel, String, JComponent, String, JComponent) -> Unit,
    private val addSpacerRow: (JPanel, Int) -> Unit,
    private val mcpEnabled: JComponent,
    private val mcpHost: JComponent,
    private val mcpPort: JComponent,
    private val mcpExternal: JComponent,
    private val mcpStdio: JComponent,
    private val mcpTlsEnabled: JComponent,
    private val mcpTlsAuto: JComponent,
    private val mcpKeystorePath: JComponent,
    private val mcpKeystorePassword: JComponent,
    private val mcpMaxConcurrent: JComponent,
    private val mcpMaxBodyMb: JComponent,
    private val mcpUnsafe: JComponent,
    private val tokenPanelFactory: () -> JPanel,
    private val quickActionsFactory: () -> JPanel
) : ConfigPanel {
    override fun build(): JPanel {
        val body = JPanel(BorderLayout())
        body.background = UiTheme.Colors.surface
        val wrapper = sectionPanel(
            "MCP Server",
            "Built-in MCP server (SSE + optional stdio bridge).",
            body
        )

        val grid = formGrid()
        addRowFull(grid, "Enabled", mcpEnabled)
        addSpacerRow(grid, 4)
        addRowPair(grid, "Host", mcpHost, "Port", mcpPort)
        addSpacerRow(grid, 4)
        addRowPair(grid, "External access", mcpExternal, "Stdio bridge", mcpStdio)
        addSpacerRow(grid, 4)
        addRowPair(grid, "TLS enabled", mcpTlsEnabled, "Auto-generate TLS", mcpTlsAuto)
        addSpacerRow(grid, 4)
        addRowFull(grid, "TLS keystore path", mcpKeystorePath)
        addSpacerRow(grid, 4)
        addRowFull(grid, "TLS keystore password", mcpKeystorePassword)
        addSpacerRow(grid, 4)
        addRowFull(grid, "Token", tokenPanelFactory())
        addSpacerRow(grid, 4)
        addRowFull(grid, "Quick actions", quickActionsFactory())
        addSpacerRow(grid, 4)
        addRowFull(grid, "Max concurrent requests", mcpMaxConcurrent)
        addSpacerRow(grid, 4)
        addRowFull(grid, "Max body size (MB)", mcpMaxBodyMb)
        addSpacerRow(grid, 4)
        addRowFull(grid, "Unsafe mode", mcpUnsafe)
        addSpacerRow(grid, 6)

        val container = JPanel(BorderLayout())
        container.background = UiTheme.Colors.surface
        container.add(grid, BorderLayout.NORTH)
        body.add(container, BorderLayout.CENTER)
        return wrapper
    }
}
