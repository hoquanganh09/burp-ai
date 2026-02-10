package com.burpai.ui

object MarkdownRenderer {

    fun toHtml(text: String, isDark: Boolean): String {
        var html = escapeHtml(text)
        html = renderCodeBlocks(html, isDark)
        html = renderInlineCode(html, isDark)
        html = html.replace(Regex("\\*\\*(.*?)\\*\\*")) { "<b>${it.groupValues[1]}</b>" }
        html = html.replace(Regex("\\*(.*?)\\*")) { "<i>${it.groupValues[1]}</i>" }
        html = highlightSeverityTags(html, isDark)
        html = html.replace("\n", "<br>")

        val textColor = if (isDark) "#e6e6e6" else "#202020"
        val fontFamily = UiTheme.Typography.aiMono.family

        return """
            <html>
            <body style='font-family: $fontFamily; color: $textColor; font-size: 13px; margin: 0; padding: 0; overflow-wrap: anywhere; word-break: break-word;'>
            <div style='line-height: 1.45; overflow-wrap: anywhere; word-break: break-word;'>
            $html
            </div>
            </body>
            </html>
        """.trimIndent()
    }

    private fun escapeHtml(text: String): String {
        return text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
    }

    private fun renderCodeBlocks(input: String, isDark: Boolean): String {
        return input.replace(Regex("```([a-zA-Z0-9_-]*)\\n?([\\s\\S]*?)```")) { m ->
            val language = m.groupValues[1].ifBlank { "text" }.lowercase()
            val codeBody = m.groupValues[2]
            val highlighted = syntaxHighlight(codeBody, isDark)
            val bg = if (isDark) "#1F2430" else "#F5F1EA"
            val border = if (isDark) "#3E4658" else "#D8CEBF"
            val labelBg = if (isDark) "#2A3142" else "#E9E2D5"
            val labelFg = if (isDark) "#C7D2E0" else "#5A6370"
            """
            <div style='margin: 6px 0;'>
              <div style='display: inline-block; background: $labelBg; color: $labelFg; border: 1px solid $border; border-bottom: none; border-radius: 6px 6px 0 0; padding: 2px 8px; font-family: ${UiTheme.Typography.aiButton.family}; font-size: 11px;'>$language</div>
              <pre style='margin: 0; background-color: $bg; border: 1px solid $border; border-radius: 0 6px 6px 6px; padding: 10px; font-family: ${UiTheme.Typography.aiMono.family}; font-size: 13px; white-space: pre-wrap; overflow-wrap: anywhere; word-break: break-word;'><code>$highlighted</code></pre>
            </div>
            """.trimIndent()
        }
    }

    private fun renderInlineCode(input: String, isDark: Boolean): String {
        val bg = if (isDark) "#3A4151" else "#EDE6DA"
        val fg = if (isDark) "#F0F4FB" else "#2A3240"
        return input.replace(Regex("`([^`]+)`")) { m ->
            "<code style='background-color: $bg; color: $fg; padding: 2px 4px; border-radius: 4px; font-family: ${UiTheme.Typography.aiMono.family}; font-size: 13px; white-space: pre-wrap; overflow-wrap: anywhere; word-break: break-word;'>${m.groupValues[1]}</code>"
        }
    }

    private fun highlightSeverityTags(input: String, isDark: Boolean): String {
        val highFg = if (isDark) "#FFE2DE" else "#FFFFFF"
        val medFg = if (isDark) "#FFF2DF" else "#FFFFFF"
        val lowFg = if (isDark) "#E4F7EA" else "#FFFFFF"
        var out = input
        out = out.replace("[HIGH]", "<span style='background:#B3261E;color:$highFg;font-weight:700;padding:1px 6px;border-radius:4px;'>[HIGH]</span>")
        out = out.replace("[MEDIUM]", "<span style='background:#C46E00;color:$medFg;font-weight:700;padding:1px 6px;border-radius:4px;'>[MEDIUM]</span>")
        out = out.replace("[LOW]", "<span style='background:#1E7D3C;color:$lowFg;font-weight:700;padding:1px 6px;border-radius:4px;'>[LOW]</span>")
        return out
    }

    private fun syntaxHighlight(code: String, isDark: Boolean): String {
        val keywordColor = if (isDark) "#8EC5FF" else "#0A4A9E"
        val stringColor = if (isDark) "#8BD5A6" else "#1F7A3E"
        val commentColor = if (isDark) "#9AA5B1" else "#6A7480"
        val numberColor = if (isDark) "#F5C17A" else "#9A4E00"

        var out = code
        out = out.replace(Regex("(?m)(//.*$|#.*$|--\\s.*$)")) {
            "<span style='color:$commentColor;'>${it.value}</span>"
        }
        out = out.replace(Regex("(&quot;.*?&quot;|'[^']*')")) {
            "<span style='color:$stringColor;'>${it.value}</span>"
        }

        val keywords = listOf(
            "select", "from", "where", "union", "insert", "update", "delete",
            "drop", "alter", "create", "table", "into", "values", "join",
            "if", "else", "for", "while", "return", "function", "class",
            "const", "let", "var", "true", "false", "null", "and", "or"
        ).joinToString("|")
        out = out.replace(Regex("(?i)\\b($keywords)\\b")) {
            "<span style='color:$keywordColor;font-weight:700;'>${it.value}</span>"
        }
        out = out.replace(Regex("\\b\\d+\\b")) {
            "<span style='color:$numberColor;'>${it.value}</span>"
        }
        return out
    }
}

