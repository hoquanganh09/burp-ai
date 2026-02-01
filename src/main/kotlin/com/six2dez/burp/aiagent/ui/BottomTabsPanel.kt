package com.six2dez.burp.aiagent.ui

import java.awt.BorderLayout
import java.awt.FlowLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTabbedPane
import javax.swing.border.EmptyBorder
import javax.swing.border.LineBorder

class BottomTabsPanel(private val settingsPanel: SettingsPanel) {
    val root: JComponent = JPanel(BorderLayout())

    private val tabbedPane = JTabbedPane()
    private val saveButton = JButton("Save settings")
    private val restoreButton = JButton("Restore defaults")

    init {
        root.background = UiTheme.Colors.surface

        tabbedPane.background = UiTheme.Colors.surface
        tabbedPane.foreground = UiTheme.Colors.onSurface
        tabbedPane.border = EmptyBorder(0, 0, 0, 0)

        tabbedPane.addTab("AI Backend", settingsPanel.generalTabComponent())
        tabbedPane.addTab("AI Passive Scanner", settingsPanel.passiveScannerTabComponent())
        tabbedPane.addTab("AI Active Scanner", settingsPanel.activeScannerTabComponent())
        tabbedPane.addTab("MCP Server", settingsPanel.mcpTabComponent())
        tabbedPane.addTab("Burp Integration", settingsPanel.burpIntegrationTabComponent())
        tabbedPane.addTab("Prompt Templates", settingsPanel.promptsTabComponent())
        tabbedPane.addTab("Privacy & Logging", settingsPanel.privacyTabComponent())
        tabbedPane.addTab("Help", settingsPanel.helpTabComponent())

        saveButton.font = UiTheme.Typography.label
        saveButton.background = UiTheme.Colors.primary
        saveButton.foreground = UiTheme.Colors.onPrimary
        saveButton.isOpaque = true
        saveButton.border = EmptyBorder(8, 14, 8, 14)
        saveButton.isFocusPainted = false
        saveButton.addActionListener {
            settingsPanel.saveSettings()
        }

        restoreButton.font = UiTheme.Typography.label
        restoreButton.background = UiTheme.Colors.surface
        restoreButton.foreground = UiTheme.Colors.primary
        restoreButton.isOpaque = true
        restoreButton.border = LineBorder(UiTheme.Colors.outline, 1, true)
        restoreButton.isFocusPainted = false
        restoreButton.addActionListener {
            settingsPanel.restoreDefaultsWithConfirmation()
        }

        val buttonPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 12, 6))
        buttonPanel.background = UiTheme.Colors.surface
        buttonPanel.border = EmptyBorder(4, 12, 8, 12)
        buttonPanel.add(restoreButton)
        buttonPanel.add(saveButton)

        root.add(tabbedPane, BorderLayout.CENTER)
        root.add(buttonPanel, BorderLayout.SOUTH)

        settingsPanel.setDialogParent(root)
    }
}
