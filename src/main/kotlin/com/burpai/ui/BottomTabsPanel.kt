package com.burpai.ui

import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import javax.swing.DefaultListCellRenderer
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.ListSelectionModel
import javax.swing.SwingConstants
import javax.swing.border.EmptyBorder
import javax.swing.border.LineBorder

class BottomTabsPanel(private val settingsPanel: SettingsPanel) {
    val root: JComponent = JPanel(BorderLayout())

    private val cards = JPanel(CardLayout())
    private val navList = JList<String>()
    private val saveButton = JButton("Save settings")
    private val restoreButton = JButton("Restore defaults")

    private val sections = listOf(
        "AI Backend" to settingsPanel.generalTabComponent(),
        "AI Passive Scanner" to settingsPanel.passiveScannerTabComponent(),
        "AI Active Scanner" to settingsPanel.activeScannerTabComponent(),
        "MCP Server" to settingsPanel.mcpTabComponent(),
        "Burp Integration" to settingsPanel.burpIntegrationTabComponent(),
        "Prompt Templates" to settingsPanel.promptsTabComponent(),
        "Privacy & Logging" to settingsPanel.privacyTabComponent(),
        "Help" to settingsPanel.helpTabComponent()
    )

    init {
        root.background = UiTheme.Colors.surface
        root.border = EmptyBorder(10, 10, 10, 10)

        val header = buildHeader()
        val nav = buildNavigation()
        val content = buildCards()
        val footer = buildFooter()

        val body = JPanel(BorderLayout())
        body.background = UiTheme.Colors.surface
        body.add(nav, BorderLayout.WEST)
        body.add(content, BorderLayout.CENTER)

        root.add(header, BorderLayout.NORTH)
        root.add(body, BorderLayout.CENTER)
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

    private fun buildNavigation(): JComponent {
        navList.model = javax.swing.DefaultListModel<String>().apply {
            sections.forEach { addElement(it.first) }
        }
        navList.selectionMode = ListSelectionModel.SINGLE_SELECTION
        navList.selectedIndex = 0
        navList.font = UiTheme.Typography.body
        navList.background = UiTheme.Colors.surface
        navList.foreground = UiTheme.Colors.onSurface
        navList.cellRenderer = NavRenderer()
        navList.addListSelectionListener { e ->
            if (e.valueIsAdjusting) return@addListSelectionListener
            val selected = navList.selectedValue ?: return@addListSelectionListener
            (cards.layout as CardLayout).show(cards, selected)
        }

        val scroll = JScrollPane(navList)
        scroll.border = LineBorder(UiTheme.Colors.outlineVariant, 1, true)
        scroll.viewport.background = UiTheme.Colors.surface
        scroll.preferredSize = Dimension(180, 300)
        scroll.horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
        scroll.verticalScrollBar.unitIncrement = 16
        return scroll
    }

    private fun buildCards(): JComponent {
        cards.background = UiTheme.Colors.surface
        sections.forEach { (label, component) ->
            cards.add(component, label)
        }
        (cards.layout as CardLayout).show(cards, sections.first().first)
        return cards
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

    private class NavRenderer : DefaultListCellRenderer() {
        override fun getListCellRendererComponent(
            list: JList<*>,
            value: Any?,
            index: Int,
            isSelected: Boolean,
            cellHasFocus: Boolean
        ): Component {
            val label = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus) as JLabel
            label.border = EmptyBorder(10, 12, 10, 12)
            label.horizontalAlignment = SwingConstants.LEFT
            label.font = UiTheme.Typography.body
            label.background = if (isSelected) UiTheme.Colors.outlineVariant else UiTheme.Colors.surface
            label.foreground = if (isSelected) UiTheme.Colors.onSurface else UiTheme.Colors.onSurfaceVariant
            return label
        }
    }
}
