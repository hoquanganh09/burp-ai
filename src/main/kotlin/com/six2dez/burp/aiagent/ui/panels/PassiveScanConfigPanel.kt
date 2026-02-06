package com.six2dez.burp.aiagent.ui.panels

import com.six2dez.burp.aiagent.ui.UiTheme
import java.awt.BorderLayout
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JComboBox
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JSpinner
import javax.swing.border.EmptyBorder
import javax.swing.border.LineBorder

class PassiveScanConfigPanel(
    private val sectionPanel: (String, String, JComponent) -> JPanel,
    private val formGrid: () -> JPanel,
    private val addRowFull: (JPanel, String, JComponent) -> Unit,
    private val addRowPair: (JPanel, String, JComponent, String, JComponent) -> Unit,
    private val addSpacerRow: (JPanel, Int) -> Unit,
    private val passiveAiEnabled: JComponent,
    private val passiveAiScopeOnly: JCheckBox,
    private val passiveAiRateSpinner: JSpinner,
    private val passiveAiMaxSizeSpinner: JSpinner,
    private val passiveAiMinSeverityCombo: JComboBox<*>,
    private val passiveAiStatusLabel: JLabel,
    private val passiveAiViewFindings: JButton,
    private val scannerTriageButton: JButton,
    private val passiveAiResetStats: JButton
) : ConfigPanel {
    override fun build(): JPanel {
        val body = JPanel(BorderLayout())
        body.background = UiTheme.Colors.surface
        body.border = EmptyBorder(6, 8, 8, 8)

        passiveAiEnabled.font = UiTheme.Typography.body
        passiveAiEnabled.background = UiTheme.Colors.surface
        passiveAiEnabled.foreground = UiTheme.Colors.onSurface
        passiveAiEnabled.toolTipText = "Automatically analyze proxy traffic using AI and create Burp issues for findings."

        passiveAiScopeOnly.font = UiTheme.Typography.body
        passiveAiScopeOnly.background = UiTheme.Colors.surface
        passiveAiScopeOnly.foreground = UiTheme.Colors.onSurface
        passiveAiScopeOnly.toolTipText = "Only analyze requests that are in the defined target scope."

        passiveAiRateSpinner.font = UiTheme.Typography.body
        passiveAiRateSpinner.toolTipText = "Minimum seconds between AI analyses (rate limiting)."

        passiveAiMaxSizeSpinner.font = UiTheme.Typography.body
        passiveAiMaxSizeSpinner.toolTipText = "Maximum response size in KB to analyze."

        passiveAiMinSeverityCombo.font = UiTheme.Typography.body
        passiveAiMinSeverityCombo.background = UiTheme.Colors.surface
        passiveAiMinSeverityCombo.toolTipText = "Only report findings at or above this severity level."

        passiveAiStatusLabel.font = UiTheme.Typography.body
        passiveAiStatusLabel.foreground = UiTheme.Colors.onSurfaceVariant

        passiveAiViewFindings.font = UiTheme.Typography.label
        passiveAiViewFindings.background = UiTheme.Colors.surface
        passiveAiViewFindings.foreground = UiTheme.Colors.primary
        passiveAiViewFindings.border = EmptyBorder(6, 10, 6, 10)
        passiveAiViewFindings.isFocusPainted = false

        scannerTriageButton.font = UiTheme.Typography.label
        scannerTriageButton.background = UiTheme.Colors.surface
        scannerTriageButton.foreground = UiTheme.Colors.primary
        scannerTriageButton.border = EmptyBorder(6, 10, 6, 10)
        scannerTriageButton.isFocusPainted = false

        passiveAiResetStats.font = UiTheme.Typography.label
        passiveAiResetStats.background = UiTheme.Colors.surface
        passiveAiResetStats.foreground = UiTheme.Colors.primary
        passiveAiResetStats.border = LineBorder(UiTheme.Colors.outline, 1, true)
        passiveAiResetStats.isFocusPainted = false

        val grid = formGrid()
        addRowFull(grid, "Enable scanner", passiveAiEnabled)
        addSpacerRow(grid, 4)
        addRowFull(grid, "In-scope only", passiveAiScopeOnly)
        addSpacerRow(grid, 4)
        addRowPair(grid, "Rate limit (sec)", passiveAiRateSpinner, "Max size (KB)", passiveAiMaxSizeSpinner)
        addSpacerRow(grid, 4)
        addRowFull(grid, "Min severity", passiveAiMinSeverityCombo)
        addSpacerRow(grid, 8)
        addRowFull(grid, "Status", passiveAiStatusLabel)
        addSpacerRow(grid, 4)

        val actionsPanel = JPanel()
        actionsPanel.layout = BoxLayout(actionsPanel, BoxLayout.X_AXIS)
        actionsPanel.background = UiTheme.Colors.surface
        actionsPanel.add(passiveAiViewFindings)
        actionsPanel.add(Box.createRigidArea(java.awt.Dimension(8, 0)))
        actionsPanel.add(scannerTriageButton)
        actionsPanel.add(Box.createRigidArea(java.awt.Dimension(8, 0)))
        actionsPanel.add(passiveAiResetStats)
        addRowFull(grid, "Actions", actionsPanel)

        body.add(grid, BorderLayout.CENTER)
        return sectionPanel(
            "AI Passive Scanner",
            "Automatically analyze proxy traffic for vulnerabilities (XSS, SQLi, IDOR, BOLA, BAC, etc.)",
            body
        )
    }
}
