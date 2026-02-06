package com.six2dez.burp.aiagent.ui.panels

import javax.swing.JComponent
import javax.swing.JPanel

class PrivacyConfigPanel(
    private val sectionPanel: (String, String, JComponent) -> JPanel,
    private val formGrid: () -> JPanel,
    private val addRowFull: (JPanel, String, JComponent) -> Unit,
    private val addSpacerRow: (JPanel, Int) -> Unit,
    private val privacyMode: JComponent,
    private val auditEnabled: JComponent,
    private val autoRestart: JComponent,
    private val determinism: JComponent,
    private val rotateSaltBtn: JComponent,
    private val privacyWarning: JComponent,
    private val privacyActiveWarning: JComponent
) : ConfigPanel {
    override fun build(): JPanel {
        val grid = formGrid()
        addRowFull(grid, "Privacy mode", privacyMode)
        addSpacerRow(grid, 4)
        addRowFull(grid, "Audit logging", auditEnabled)
        addSpacerRow(grid, 4)
        addRowFull(grid, "Auto-restart", autoRestart)
        addSpacerRow(grid, 4)
        addRowFull(grid, "Determinism mode", determinism)
        addSpacerRow(grid, 4)
        addRowFull(grid, "Anonymization", rotateSaltBtn)
        addSpacerRow(grid, 8)
        addRowFull(grid, "Privacy warning", privacyWarning)
        addSpacerRow(grid, 4)
        addRowFull(grid, "Active scan warning", privacyActiveWarning)
        return sectionPanel(
            "Privacy & Logging",
            "Controls redaction and stable ordering of context.",
            grid
        )
    }
}
