package com.burpai.ui.components

import com.burpai.ui.UiTheme
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import javax.swing.JPanel
import javax.swing.border.EmptyBorder

class CardPanel(layout: java.awt.LayoutManager? = java.awt.BorderLayout(), private val radius: Int = 16) : JPanel(layout) {
    init {
        isOpaque = false
        background = UiTheme.Colors.cardBackground
        border = EmptyBorder(12, 12, 12, 12)
    }

    override fun paintComponent(g: Graphics) {
        val g2 = g.create() as Graphics2D
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2.color = background
        g2.fillRoundRect(0, 0, width - 1, height - 1, radius, radius)
        g2.color = UiTheme.Colors.cardBorder
        g2.drawRoundRect(0, 0, width - 1, height - 1, radius, radius)
        g2.dispose()
        super.paintComponent(g)
    }
}
