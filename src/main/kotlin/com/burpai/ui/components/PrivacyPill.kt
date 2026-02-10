package com.burpai.ui.components

import com.burpai.redact.PrivacyMode
import com.burpai.ui.UiTheme
import java.awt.Color
import java.awt.Font
import javax.swing.JLabel
import javax.swing.border.EmptyBorder

class PrivacyPill : JLabel() {
    private val strictColor = Color(0x1B9E5A)
    private val balancedColor = Color(0xF9A825)
    private val offColor = Color(0xB3261E)

    init {
        font = UiTheme.Typography.label.deriveFont(Font.PLAIN, UiTheme.Typography.label.size2D)
        border = EmptyBorder(4, 10, 4, 10)
        isOpaque = true
        updateMode(PrivacyMode.OFF)
    }

    fun updateMode(mode: PrivacyMode) {
        when (mode) {
            PrivacyMode.STRICT -> {
                isVisible = true
                text = "STRICT"
                background = strictColor
                foreground = Color.WHITE
                toolTipText = "STRICT mode strips cookies, redacts tokens, and anonymizes hosts."
            }
            PrivacyMode.BALANCED -> {
                isVisible = true
                text = "BALANCED"
                background = balancedColor
                foreground = UiTheme.Colors.onSurface
                toolTipText = "BALANCED mode strips cookies and redacts tokens but keeps hosts."
            }
            PrivacyMode.OFF -> {
                text = ""
                isVisible = false
                toolTipText = "OFF mode sends raw traffic without redaction."
            }
        }
    }
}

