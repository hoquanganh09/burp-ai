package com.burpai.ui

import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTabbedPane
import javax.swing.border.EmptyBorder
import javax.swing.border.LineBorder

class BottomTabsPanel(private val settingsPanel: SettingsPanel) {
    val root: JComponent = JPanel(BorderLayout())

    private val primaryTabs = JTabbedPane()
    private val saveButton = JButton("Save settings")
    private val restoreButton = JButton("Restore defaults")

    init {
        root.background = UiTheme.Colors.surface
        root.border = EmptyBorder(10, 10, 10, 10)

        val header = buildHeader()
        val content = buildContentTabs()
        val footer = buildFooter()

        root.add(header, BorderLayout.NORTH)
        root.add(content, BorderLayout.CENTER)
        root.add(footer, BorderLayout.SOUTH)

        settingsPanel.setDialogParent(root)
    }

    private fun buildHeader(): JComponent {
        val wrapper = JPanel(BorderLayout())
        wrapper.background = UiTheme.Colors.surface
        wrapper.border = EmptyBorder(4, 4, 10, 4)

        val title = JLabel("Settings Studio")
        title.font = UiTheme.Typography.title
        title.foreground = UiTheme.Colors.onSurface

        val subtitle = JLabel("Fine-tune your agent, scanners, and privacy controls.")
        subtitle.font = UiTheme.Typography.body
        subtitle.foreground = UiTheme.Colors.onSurfaceVariant

        val stack = JPanel()
        stack.layout = javax.swing.BoxLayout(stack, javax.swing.BoxLayout.Y_AXIS)
        stack.isOpaque = false
        stack.add(title)
        stack.add(javax.swing.Box.createRigidArea(Dimension(0, 4)))
        stack.add(subtitle)

        wrapper.add(stack, BorderLayout.CENTER)
        return wrapper
    }

    private fun buildContentTabs(): JComponent {
        styleTabs(primaryTabs)
        primaryTabs.addTab("Backend", buildBackendWorkspace())
        primaryTabs.addTab("Privacy & Logging", settingsPanel.privacyTabComponent())
        primaryTabs.addTab("Prompt Templates", settingsPanel.promptsTabComponent())
        primaryTabs.addTab("Passive & Active Scanner", buildScannerWorkspace())
        return primaryTabs
    }

    private fun buildBackendWorkspace(): JComponent {
        val tabs = JTabbedPane()
        styleTabs(tabs)
        tabs.addTab("AI Backend", settingsPanel.generalTabComponent())
        tabs.addTab("MCP Server", settingsPanel.mcpTabComponent())
        tabs.addTab("Burp Integration", settingsPanel.burpIntegrationTabComponent())
        tabs.addTab("Help", settingsPanel.helpTabComponent())
        return tabs
    }

    private fun buildScannerWorkspace(): JComponent {
        val tabs = JTabbedPane()
        styleTabs(tabs)
        tabs.addTab("Passive Scanner", settingsPanel.passiveScannerTabComponent())
        tabs.addTab("Active Scanner", settingsPanel.activeScannerTabComponent())
        return tabs
    }

    private fun styleTabs(tabs: JTabbedPane) {
        tabs.font = UiTheme.Typography.body
        tabs.background = UiTheme.Colors.surface
        tabs.foreground = UiTheme.Colors.onSurface
        tabs.border = LineBorder(UiTheme.Colors.outlineVariant, 1, true)
        tabs.isOpaque = true
    }

    private fun buildFooter(): JComponent {
        saveButton.font = UiTheme.Typography.label
        saveButton.background = UiTheme.Colors.primary
        saveButton.foreground = UiTheme.Colors.onPrimary
        saveButton.isOpaque = true
        saveButton.border = EmptyBorder(8, 14, 8, 14)
        saveButton.isFocusPainted = false
        saveButton.addActionListener { settingsPanel.saveSettings() }

        restoreButton.font = UiTheme.Typography.label
        restoreButton.background = UiTheme.Colors.surface
        restoreButton.foreground = UiTheme.Colors.primary
        restoreButton.isOpaque = true
        restoreButton.border = LineBorder(UiTheme.Colors.outline, 1, true)
        restoreButton.isFocusPainted = false
        restoreButton.addActionListener { settingsPanel.restoreDefaultsWithConfirmation() }

        val buttonPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 12, 6))
        buttonPanel.background = UiTheme.Colors.surface
        buttonPanel.border = EmptyBorder(8, 0, 0, 0)
        buttonPanel.add(restoreButton)
        buttonPanel.add(saveButton)
        return buttonPanel
    }
}
