package com.six2dez.burp.aiagent.agents

import com.six2dez.burp.aiagent.backends.BackendDiagnostics
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

data class AgentProfile(
    val global: String,
    val sections: Map<String, String>
)

object AgentProfileLoader {
    private val defaultBaseDir: Path = Paths.get(
        System.getProperty("user.home"),
        ".burp-ai-agent",
        "AGENTS"
    )
    @Volatile
    internal var baseDirOverride: Path? = null
    private val bundledProfiles = listOf(
        "pentester.md",
        "bughunter.md",
        "auditor.md"
    )

    @Volatile
    private var cachedProfile: AgentProfile? = null

    @Volatile
    private var cachedPath: Path? = null

    @Volatile
    private var cachedModified: Long = -1L

    fun setActiveProfile(profileName: String) {
        val normalized = normalizeProfileName(profileName)
        if (normalized.isBlank()) return
        try {
            val baseDir = baseDir()
            Files.createDirectories(baseDir)
            val defaultFile = baseDir.resolve("default")
            Files.writeString(defaultFile, normalized)
            invalidateCache()
        } catch (e: Exception) {
            BackendDiagnostics.logError("Failed to set AGENTS profile: ${e.message}")
        }
    }

    fun listAvailableProfiles(): List<String> {
        ensureBundledProfilesInstalled()
        return try {
            val baseDir = baseDir()
            Files.createDirectories(baseDir)
            Files.list(baseDir).use { stream ->
                stream
                    .filter { Files.isRegularFile(it) }
                    .map { it.fileName.toString() }
                    .filter { it.lowercase().endsWith(".md") }
                    .map { name ->
                        if (name.lowercase().endsWith(".md")) name.dropLast(3) else name
                    }
                    .distinct()
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .toList()
            }
        } catch (e: Exception) {
            BackendDiagnostics.logError("Failed to list AGENTS profiles: ${e.message}")
            emptyList()
        }
    }

    fun validateProfile(profileName: String, availableTools: Set<String>): List<String> {
        val path = resolveProfilePathByName(profileName) ?: return emptyList()
        val text = try {
            Files.readString(path)
        } catch (e: Exception) {
            BackendDiagnostics.logError("Failed to read AGENTS profile for validation: ${path}. ${e.message}")
            return emptyList()
        }
        val referencedTools = extractReferencedTools(text)
        if (referencedTools.isEmpty()) return emptyList()
        val missing = referencedTools
            .filter { it !in availableTools }
            .sorted()
        if (missing.isEmpty()) return emptyList()
        return missing.map { "Profile references MCP tool '$it' but it is disabled or unavailable." }
    }

    fun ensureBundledProfilesInstalled() {
        try {
            val baseDir = baseDir()
            Files.createDirectories(baseDir)
            val loader = AgentProfileLoader::class.java.classLoader
            for (profile in bundledProfiles) {
                val target = baseDir.resolve(profile)
                if (Files.exists(target)) continue
                val resourcePath = "AGENTS/$profile"
                val stream = loader.getResourceAsStream(resourcePath) ?: continue
                stream.use { input ->
                    Files.copy(input, target)
                }
            }
        } catch (e: Exception) {
            BackendDiagnostics.logError("Failed to install bundled AGENTS profiles: ${e.message}")
        }
    }

    fun buildInstructionBlock(actionName: String?): String? {
        val profile = loadProfile() ?: return null
        val sections = mutableListOf<String>()
        if (profile.global.isNotBlank()) {
            sections.add(profile.global.trim())
        }
        val sectionKey = sectionKeyForAction(actionName)
        val section = profile.sections[sectionKey]
            ?: profile.sections["DEFAULT"]
        if (!section.isNullOrBlank()) {
            sections.add(section.trim())
        }
        if (sections.isEmpty()) return null
        return buildString {
            appendLine("System instructions (AGENTS):")
            append(sections.joinToString("\n\n").trim())
        }.trim()
    }

    private fun loadProfile(): AgentProfile? {
        val profilePath = resolveProfilePath() ?: return null
        val modified = try {
            Files.getLastModifiedTime(profilePath).toMillis()
        } catch (e: Exception) {
            BackendDiagnostics.logError("Failed to read AGENTS profile timestamp: ${profilePath}. ${e.message}")
            -1L
        }
        if (profilePath == cachedPath && modified == cachedModified) {
            return cachedProfile
        }

        val text = try {
            Files.readString(profilePath)
        } catch (e: Exception) {
            BackendDiagnostics.logError("Failed to read AGENTS profile: ${profilePath}. ${e.message}")
            return null
        }
        val parsed = parseProfile(text)
        cachedProfile = parsed
        cachedPath = profilePath
        cachedModified = modified
        return parsed
    }

    private fun resolveProfilePath(): Path? {
        val baseDir = baseDir()
        val defaultFile = baseDir.resolve("default")
        val profileName = if (Files.isRegularFile(defaultFile)) {
            try {
                Files.readString(defaultFile).trim()
            } catch (e: Exception) {
                BackendDiagnostics.logError("Failed to read AGENTS default profile marker: ${e.message}")
                ""
            }
        } else {
            ""
        }
        val candidate = if (profileName.isNotBlank()) {
            baseDir.resolve(profileName)
        } else {
            baseDir.resolve("pentester.md")
        }
        return if (Files.isRegularFile(candidate)) candidate else null
    }

    private fun resolveProfilePathByName(profileName: String?): Path? {
        val baseDir = baseDir()
        val normalized = normalizeProfileName(profileName.orEmpty())
        if (normalized.isBlank()) return null
        val candidate = baseDir.resolve(normalized)
        return if (Files.isRegularFile(candidate)) candidate else null
    }

    private fun normalizeProfileName(name: String): String {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return ""
        return if (trimmed.endsWith(".md", ignoreCase = true)) trimmed else "$trimmed.md"
    }

    private fun invalidateCache() {
        cachedProfile = null
        cachedPath = null
        cachedModified = -1L
    }

    private fun baseDir(): Path {
        return baseDirOverride ?: defaultBaseDir
    }

    internal fun setBaseDirForTests(path: Path?) {
        baseDirOverride = path
        invalidateCache()
    }

    private fun sectionKeyForAction(actionName: String?): String {
        if (actionName.isNullOrBlank()) {
            return "CHAT"
        }
        val normalized = actionName.trim().uppercase()
        val mapped = when (normalized) {
            "FIND VULNERABILITIES" -> "REQUEST_ANALYSIS"
            "ANALYZE REQUEST" -> "REQUEST_ANALYSIS"
            "ANALYZE THIS REQUEST" -> "ANALYZE_REQUEST"
            "QUICK RECON" -> "ANALYZE_REQUEST"
            "QUICK TRIAGE" -> "ANALYZE_REQUEST"
            "SUMMARIZE REQUEST/RESPONSE" -> "REQUEST_SUMMARY"
            "EXPLAIN HEADERS" -> "HEADERS"
            "EXPLAIN JS" -> "JS_ANALYSIS"
            "LOGIN SEQUENCE" -> "LOGIN_SEQUENCE"
            "ACCESS CONTROL" -> "ACCESS_CONTROL"
            "ANALYZE THIS ISSUE" -> "ISSUE_ANALYSIS"
            "ISSUE ANALYSIS" -> "ISSUE_ANALYSIS"
            "GENERATE POC & VALIDATE" -> "POC"
            "POC & VALIDATION" -> "POC"
            "POC STEPS" -> "POC"
            "IMPACT & SEVERITY" -> "ISSUE_IMPACT"
            "ANALYZE IMPACT" -> "ISSUE_IMPACT"
            "FULL REPORT" -> "FULL_REPORT"
            "FULL VULN REPORT" -> "FULL_REPORT"
            "ESCALATION PATHS" -> "ESCALATION_PATHS"
            "CHAT" -> "CHAT"
            else -> null
        }
        if (mapped != null) return mapped
        return actionName.trim()
            .replace(Regex("[^A-Za-z0-9]+"), "_")
            .trim('_')
            .uppercase()
    }

    private fun parseProfile(text: String): AgentProfile {
        val sections = LinkedHashMap<String, StringBuilder>()
        var current = "GLOBAL"
        sections[current] = StringBuilder()

        val lines = text.replace("\r\n", "\n").replace("\r", "\n").split("\n")
        val headerRegex = Regex("^\\s*\\[([A-Za-z0-9_\\-]+)\\]\\s*$")
        for (line in lines) {
            val match = headerRegex.matchEntire(line)
            if (match != null) {
                current = match.groupValues[1].uppercase()
                sections.putIfAbsent(current, StringBuilder())
                continue
            }
            sections.getOrPut(current) { StringBuilder() }.appendLine(line)
        }

        val global = sections["GLOBAL"]?.toString().orEmpty().trim()
        val parsedSections = sections
            .filterKeys { it != "GLOBAL" }
            .mapValues { it.value.toString().trim() }
            .filterValues { it.isNotBlank() }
        return AgentProfile(global = global, sections = parsedSections)
    }

    private fun extractReferencedTools(text: String): Set<String> {
        val tools = linkedSetOf<String>()
        val lines = text.replace("\r\n", "\n").replace("\r", "\n").lines()
        val toolListHeaderPattern = Regex("^\\s*Available\\s+MCP\\s+Tools\\s*:\\s*$", RegexOption.IGNORE_CASE)
        val sectionHeaderPattern = Regex("^\\s*\\[[A-Za-z0-9_\\-]+]\\s*$")
        val titledHeaderPattern = Regex("^\\s*[A-Z][A-Z\\s_\\-]{2,}\\s*:\\s*$")
        val bulletEntryPattern = Regex("^\\s*[-*]\\s*([^:]+)\\s*:\\s*.*$")
        val toolTokenPattern = Regex("[a-z][a-z0-9_\\-]*", RegexOption.IGNORE_CASE)
        val slashToolPattern = Regex("/tool\\s+([a-z0-9_\\-]+)", RegexOption.IGNORE_CASE)
        val jsonToolPattern = Regex("\"tool\"\\s*:\\s*\"([a-z0-9_\\-]+)\"", RegexOption.IGNORE_CASE)
        var inExplicitToolList = false
        for (line in lines) {
            if (toolListHeaderPattern.matches(line)) {
                inExplicitToolList = true
                continue
            }
            if (inExplicitToolList) {
                if (line.isBlank() || sectionHeaderPattern.matches(line) || titledHeaderPattern.matches(line)) {
                    inExplicitToolList = false
                } else {
                    bulletEntryPattern.find(line)?.groupValues?.getOrNull(1)?.let { toolExpr ->
                        toolTokenPattern.findAll(toolExpr).forEach { token ->
                            tools.add(token.value.lowercase())
                        }
                    }
                }
            }
            slashToolPattern.findAll(line).forEach { m -> tools.add(m.groupValues[1].lowercase()) }
            jsonToolPattern.findAll(line).forEach { m -> tools.add(m.groupValues[1].lowercase()) }
        }
        return tools
    }
}
