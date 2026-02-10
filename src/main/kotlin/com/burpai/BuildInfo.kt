package com.burpai

import java.util.Properties

data class BuildInfo(
    val buildTime: String,
    val buildNumber: String,
    val previousSummary: String,
    val currentSummary: String
) {
    fun asLoadBlock(): String {
        return buildString {
            appendLine("[")
            appendLine(buildTime)
            appendLine(buildNumber)
            appendLine(previousSummary)
            appendLine(currentSummary)
            append("]")
        }
    }

    companion object {
        fun load(): BuildInfo {
            val props = Properties()
            BuildInfo::class.java.getResourceAsStream("/build-info.properties")?.use(props::load)

            return BuildInfo(
                buildTime = props.getProperty("buildTime") ?: "unknown",
                buildNumber = props.getProperty("buildNumber") ?: "0",
                previousSummary = props.getProperty("previousSummary") ?: "No previous summary.",
                currentSummary = props.getProperty("currentSummary") ?: "No current summary."
            )
        }
    }
}
