package com.burpai.ui

object MarkdownRenderer {

    fun toHtml(text: String, isDark: Boolean): String {
        // Escape HTML special characters first
        var html = text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")

        // Code blocks (```language ... ```)
        // We use a non-greedy match for content
        html = html.replace(Regex("```([a-zA-Z0-9]*)\\n?([\\s\\S]*?)```")) { m ->
            val code = m.groupValues[2]
            "<pre style='background-color: ${if (isDark) "#2d2d2d" else "#f0f0f0"}; padding: 8px; font-family: Monospaced; font-size: 10px; white-space: pre-wrap; overflow-wrap: anywhere; word-break: break-word;'><code>$code</code></pre>"
        }

        // Inline code (`...`)
        html = html.replace(Regex("`([^`]+)`")) { m ->
            "<code style='background-color: ${if (isDark) "#3c3c3c" else "#e0e0e0"}; padding: 2px 4px; font-family: Monospaced; font-size: 10px; white-space: pre-wrap; overflow-wrap: anywhere; word-break: break-word;'>${m.groupValues[1]}</code>"
        }

        // Bold (**...**)
        html = html.replace(Regex("\\*\\*(.*?)\\*\\*")) { m ->
            "<b>${m.groupValues[1]}</b>"
        }

        // Italic (*...*)
        html = html.replace(Regex("\\*(.*?)\\*")) { m ->
            "<i>${m.groupValues[1]}</i>"
        }

        // Line breaks - preserve them, but handle double newlines as paragraph breaks
        html = html.replace("\n", "<br>")

        val textColor = if (isDark) "#e0e0e0" else "#202020"
        val fontFamily = "SansSerif"

        return """
            <html>
            <body style='font-family: $fontFamily; color: $textColor; font-size: 11px; margin: 0; padding: 0; overflow-wrap: anywhere; word-break: break-word;'>
            <div style='line-height: 1.4; overflow-wrap: anywhere; word-break: break-word;'>
            $html
            </div>
            </body>
            </html>
        """.trimIndent()
    }
}

