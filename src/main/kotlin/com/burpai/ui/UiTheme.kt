package com.burpai.ui

import java.awt.Color
import java.awt.Font
import java.awt.GraphicsEnvironment
import javax.swing.UIManager
import kotlin.math.roundToInt

object UiTheme {
    val isDarkTheme: Boolean
        get() {
            val bg = Colors.surface
            // Simple luminance calculation
            val luminance = (0.299 * bg.red + 0.587 * bg.green + 0.114 * bg.blue) / 255
            return luminance < 0.5
        }

    object Colors {
        val primary: Color get() = Color(0x1F8A70)
        val onPrimary: Color get() = Color.WHITE
        val surface: Color get() = Color(0xF4F1EA)
        val onSurface: Color get() = Color(0x1F2328)
        val onSurfaceVariant: Color get() = Color(0x5E6A75)
        val outline: Color get() = Color(0xD7CDC0)
        val outlineVariant: Color get() = Color(0xE8E0D6)
        val statusRunning: Color get() = Color(0x1F8A70)
        val statusCrashed: Color get() = Color(0xC94C4C)
        val statusTerminal: Color get() = Color(0xE1A400)
        val inputBackground: Color get() = Color(0xFFFFFF)
        val inputForeground: Color get() = Color(0x1F2328)
        val comboBackground: Color get() = Color(0xFFFFFF)
        val comboForeground: Color get() = Color(0x1F2328)
        val cardBackground: Color get() = Color(0xFFFCF7)
        val cardBorder: Color get() = Color(0xE5DCD0)
    }

    object Typography {
        private val baseFont: Font get() = UIManager.getFont("Label.font") ?: Font("SansSerif", Font.PLAIN, 14)
        private val baseSize: Int get() = baseFont.size
        private val availableFonts: Set<String> by lazy {
            GraphicsEnvironment.getLocalGraphicsEnvironment().availableFontFamilyNames.toSet()
        }

        private fun pickFamily(vararg names: String, fallback: String): String {
            for (name in names) {
                if (availableFonts.contains(name)) return name
            }
            return fallback
        }

        private val sansFamily = pickFamily(
            "Space Grotesk",
            "IBM Plex Sans",
            "Manrope",
            "Segoe UI",
            fallback = baseFont.family
        )
        private val monoFamily = pickFamily(
            "JetBrains Mono",
            "Cascadia Mono",
            "Fira Code",
            "Consolas",
            fallback = "Monospaced"
        )

        val headline: Font get() = Font(sansFamily, Font.BOLD, (baseSize * 1.7f).roundToInt())
        val title: Font get() = Font(sansFamily, Font.BOLD, (baseSize * 1.2f).roundToInt())
        val body: Font get() = Font(sansFamily, Font.PLAIN, baseSize)
        val label: Font get() = Font(sansFamily, Font.BOLD, baseSize)
        val mono: Font get() = Font(monoFamily, Font.PLAIN, baseSize)
    }
}
