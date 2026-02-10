package com.burpai.ui.components

import com.burpai.ui.UiTheme
import java.awt.BorderLayout
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.border.EmptyBorder
import javax.swing.border.MatteBorder

class DependencyBanner(message: String) : JPanel(BorderLayout()) {
    private val label = JLabel(message)

    init {
        background = UiTheme.Colors.cardBackground
        border = MatteBorder(0, 4, 0, 0, UiTheme.Colors.statusTerminal)
        label.foreground = UiTheme.Colors.onSurface
        label.font = UiTheme.Typography.body
        label.border = EmptyBorder(8, 12, 8, 12)
        add(label, BorderLayout.CENTER)
        isVisible = false
    }

    fun showBanner() {
        isVisible = true
    }

    fun hideBanner() {
        isVisible = false
    }
}
