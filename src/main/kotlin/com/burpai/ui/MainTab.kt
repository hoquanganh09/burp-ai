package com.burpai.ui

import burp.api.montoya.MontoyaApi
import burp.api.montoya.http.message.HttpRequestResponse
import com.burpai.audit.AuditLogger
import com.burpai.backends.BackendRegistry
import com.burpai.config.AgentSettingsRepository
import com.burpai.context.ContextCapture
import com.burpai.mcp.McpSupervisor
import com.burpai.supervisor.AgentSupervisor
import com.burpai.ui.components.DependencyBanner
import com.burpai.ui.components.CardPanel
import com.burpai.ui.components.ToggleSwitch
import java.awt.Dialog
import java.awt.BorderLayout
import java.awt.Dimension
import java.net.URI
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JOptionPane
import javax.swing.JScrollPane
import javax.swing.JTabbedPane
import javax.swing.JTextArea
import javax.swing.SwingUtilities
import javax.swing.Timer
import javax.swing.JButton
import javax.swing.JDialog
import javax.swing.border.EmptyBorder
import kotlin.concurrent.thread

class MainTab(
    private val api: MontoyaApi,
    private val backends: BackendRegistry,
    private val supervisor: AgentSupervisor,
    private val audit: AuditLogger,
    private val mcpSupervisor: McpSupervisor,
    private val passiveAiScanner: com.burpai.scanner.PassiveAiScanner,
    private val activeAiScanner: com.burpai.scanner.ActiveAiScanner
) {
    val root: JComponent = JPanel(BorderLayout())
    private lateinit var settingsPanel: SettingsPanel
    private lateinit var chatPanel: ChatPanel
    private lateinit var bottomTabsPanel: BottomTabsPanel
    private var settingsDialog: JDialog? = null

    private val mcpToggle = ToggleSwitch()
    private val passiveToggle = ToggleSwitch()
    private val activeToggle = ToggleSwitch()
    private val backendPicker = javax.swing.JComboBox<String>()
    private val backendLabel = JLabel("Backend")
    private val mcpLabel = JLabel("MCP")
    private val mcpStatusLabel = JLabel("MCP: -")

    private val statusLabel = JLabel("Idle")
    private val sessionLabel = JLabel("Session: -")
    private val settingsRepo = AgentSettingsRepository(api)
    private val mcpStatusTimer = Timer(1000) {
        updateMcpBadge()
        updateMcpControls()
    }
    private val baseTabCaption = "BurpAI"
    private var tabbedPane: JTabbedPane? = null
    private var attentionActive = false
    private val dependencyBanner =
        DependencyBanner("MCP Server must be enabled. Toggle MCP to enable AI features.")
    private var syncingToggles = false
    private val settingsButton = JButton("Settings Studio")
    private val summarizeSiteMapButton = JButton("Summarize Entire Site Map")
    private val dashboardRequestsLabel = JLabel("Scanned requests: 0")
    private val dashboardIssuesLabel = JLabel("Vulns: High 0 | Medium 0 | Low 0")
    private val dashboardUpdatedLabel = JLabel("Updated: -")
    private val dashboardRefreshing = AtomicBoolean(false)
    private val summaryInProgress = AtomicBoolean(false)
    private val summaryOutputArea = JTextArea()
    private val summaryStatusLabel = JLabel("Ready")
    private val workspaceTabs = JTabbedPane()
    private val siteSummaryTab = JPanel(BorderLayout())
    private val dashboardTimer = Timer(2500) { refreshDashboard() }

    init {
        settingsPanel = SettingsPanel(api, backends, supervisor, audit, mcpSupervisor, passiveAiScanner, activeAiScanner)
        bottomTabsPanel = BottomTabsPanel(settingsPanel)
        chatPanel = ChatPanel(
            api = api,
            supervisor = supervisor,
            getSettings = { settingsPanel.currentSettings() },
            applySettings = { settings ->
                settingsRepo.save(settings)
                supervisor.applySettings(settings)
            },
            validateBackend = { validateBackendCommand(it) },
            ensureBackendReady = { ensureBackendReady(it) },
            showError = { showError(it) },
            onStatusChanged = { refreshStatus() },
            onResponseReady = { notifyResponseReady() }
        )
        chatPanel.root.background = UiTheme.Colors.cardBackground
        chatPanel.sessionsComponent().background = UiTheme.Colors.cardBackground
        root.background = UiTheme.Colors.surface

        val title = JLabel("BurpAI")
        title.font = UiTheme.Typography.headline
        title.foreground = UiTheme.Colors.onSurface

        val subtitle = JLabel("Terminal-first workflows with privacy controls and audit logging.")
        subtitle.font = UiTheme.Typography.body
        subtitle.foreground = UiTheme.Colors.onSurfaceVariant

        settingsButton.font = UiTheme.Typography.label
        settingsButton.background = UiTheme.Colors.primary
        settingsButton.foreground = UiTheme.Colors.onPrimary
        settingsButton.isOpaque = true
        settingsButton.border = EmptyBorder(8, 14, 8, 14)
        settingsButton.isFocusPainted = false
        settingsButton.addActionListener { openSettingsStudio() }
        val moddedByLabel = JLabel("moded by 0x4nh8ii").apply {
            font = UiTheme.Typography.body.deriveFont((UiTheme.Typography.body.size - 1).toFloat())
            foreground = UiTheme.Colors.onSurfaceVariant
            horizontalAlignment = JLabel.RIGHT
        }

        val titleBox = JPanel()
        titleBox.layout = BoxLayout(titleBox, BoxLayout.Y_AXIS)
        titleBox.isOpaque = false
        titleBox.add(title)
        titleBox.add(javax.swing.Box.createRigidArea(Dimension(0, 4)))
        titleBox.add(subtitle)

        val headerActions = JPanel()
        headerActions.layout = BoxLayout(headerActions, BoxLayout.Y_AXIS)
        headerActions.isOpaque = false
        settingsButton.alignmentX = java.awt.Component.RIGHT_ALIGNMENT
        moddedByLabel.alignmentX = java.awt.Component.RIGHT_ALIGNMENT
        headerActions.add(settingsButton)
        headerActions.add(javax.swing.Box.createRigidArea(Dimension(0, 6)))
        headerActions.add(moddedByLabel)

        mcpLabel.font = UiTheme.Typography.body
        mcpLabel.foreground = UiTheme.Colors.onSurfaceVariant
        backendLabel.font = UiTheme.Typography.body
        backendLabel.foreground = UiTheme.Colors.onSurfaceVariant
        backendPicker.font = UiTheme.Typography.body
        backendPicker.background = UiTheme.Colors.comboBackground
        backendPicker.foreground = UiTheme.Colors.comboForeground
        backendPicker.border = javax.swing.border.LineBorder(UiTheme.Colors.outline, 1, true)
        backendPicker.model = javax.swing.DefaultComboBoxModel(backends.listBackendIds().toTypedArray())
        backendPicker.selectedItem = settingsRepo.load().preferredBackendId
        backendPicker.addActionListener {
            val selected = backendPicker.selectedItem as? String ?: "openai-compatible"
            settingsPanel.setPreferredBackend(selected)
        }

        val initialSettings = settingsRepo.load()
        mcpToggle.isSelected = initialSettings.mcpSettings.enabled
        passiveToggle.isSelected = initialSettings.passiveAiEnabled
        activeToggle.isSelected = initialSettings.activeAiEnabled
        mcpToggle.toolTipText = "Enable MCP server."
        passiveToggle.toolTipText = "Enable AI passive scanner."
        activeToggle.toolTipText = "Enable AI active scanner."
        val hero = CardPanel(BorderLayout(), 18).apply {
            border = EmptyBorder(18, 20, 18, 20)
            add(titleBox, BorderLayout.CENTER)
            add(headerActions, BorderLayout.EAST)
        }
        val commandBar = buildCommandBar()
        val dashboardBar = buildDashboardBar()
        buildSiteSummaryTab()

        val north = JPanel()
        north.layout = BoxLayout(north, BoxLayout.Y_AXIS)
        north.background = UiTheme.Colors.surface
        north.border = EmptyBorder(12, 12, 8, 12)
        north.add(hero)
        north.add(javax.swing.Box.createRigidArea(Dimension(0, 10)))
        north.add(commandBar)
        north.add(javax.swing.Box.createRigidArea(Dimension(0, 8)))
        north.add(dashboardBar)
        north.add(javax.swing.Box.createRigidArea(Dimension(0, 8)))
        north.add(dependencyBanner)

        val leftColumn = JPanel(BorderLayout()).apply {
            background = UiTheme.Colors.surface
            border = EmptyBorder(0, 12, 12, 12)
            add(wrapInCard(chatPanel.sessionsComponent()), BorderLayout.CENTER)
        }

        workspaceTabs.font = UiTheme.Typography.body
        workspaceTabs.background = UiTheme.Colors.surface
        workspaceTabs.foreground = UiTheme.Colors.onSurface
        workspaceTabs.addTab("Chat", wrapInCard(chatPanel.root))
        workspaceTabs.addTab("Site Summary", wrapInCard(siteSummaryTab))

        val mainContent = javax.swing.JSplitPane(
            javax.swing.JSplitPane.HORIZONTAL_SPLIT,
            leftColumn,
            workspaceTabs
        )
        mainContent.resizeWeight = 0.22
        mainContent.setDividerLocation(0.22)
        mainContent.border = EmptyBorder(0, 0, 0, 0)
        mainContent.dividerSize = 6

        root.add(north, BorderLayout.NORTH)
        root.add(mainContent, BorderLayout.CENTER)

        wireActions()
        renderStatus()
        refreshDashboard()
        mcpStatusTimer.start()
        dashboardTimer.start()
    }

    private fun buildCommandBar(): JComponent {
        styleStatusLabel(mcpStatusLabel)
        styleStatusLabel(statusLabel)
        sessionLabel.font = UiTheme.Typography.body
        sessionLabel.foreground = UiTheme.Colors.onSurfaceVariant
        summarizeSiteMapButton.font = UiTheme.Typography.label
        summarizeSiteMapButton.isFocusPainted = false
        summarizeSiteMapButton.toolTipText = "Summarize endpoints and attack surface from the entire Burp Site Map."
        summarizeSiteMapButton.addActionListener { summarizeEntireSiteMap() }

        val bar = CardPanel(BorderLayout(), 16)
        val content = JPanel(java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 18, 6))
        content.isOpaque = false

        backendPicker.preferredSize = Dimension(220, backendPicker.preferredSize.height)

        val passiveLabel = JLabel("Passive").apply {
            font = UiTheme.Typography.body
            foreground = UiTheme.Colors.onSurfaceVariant
        }
        val activeLabel = JLabel("Active").apply {
            font = UiTheme.Typography.body
            foreground = UiTheme.Colors.onSurfaceVariant
        }

        content.add(buildBarGroup("MCP", mcpToggle, mcpStatusLabel))
        content.add(buildBarGroup("Scanners", passiveLabel, passiveToggle, activeLabel, activeToggle))
        content.add(buildBarGroup("Backend", backendPicker))
        content.add(buildBarGroup("Status", statusLabel))
        content.add(buildBarGroup("Session", sessionLabel))
        content.add(buildBarGroup("Workspace", summarizeSiteMapButton))

        bar.add(content, BorderLayout.CENTER)
        return bar
    }

    private fun buildBarGroup(title: String, vararg components: JComponent): JComponent {
        val group = JPanel()
        group.layout = BoxLayout(group, BoxLayout.Y_AXIS)
        group.isOpaque = false
        val label = JLabel(title)
        label.font = UiTheme.Typography.label
        label.foreground = UiTheme.Colors.onSurfaceVariant
        val row = JPanel(java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 8, 0))
        row.isOpaque = false
        components.forEach { row.add(it) }
        group.add(label)
        group.add(javax.swing.Box.createRigidArea(Dimension(0, 4)))
        group.add(row)
        return group
    }

    private fun buildDashboardBar(): JComponent {
        val title = JLabel("Smart Dashboard").apply {
            font = UiTheme.Typography.label
            foreground = UiTheme.Colors.onSurface
        }
        dashboardRequestsLabel.font = UiTheme.Typography.body
        dashboardRequestsLabel.foreground = UiTheme.Colors.onSurfaceVariant
        dashboardIssuesLabel.font = UiTheme.Typography.body
        dashboardIssuesLabel.foreground = UiTheme.Colors.onSurfaceVariant
        dashboardUpdatedLabel.font = UiTheme.Typography.body.deriveFont((UiTheme.Typography.body.size - 1).toFloat())
        dashboardUpdatedLabel.foreground = UiTheme.Colors.onSurfaceVariant

        val metrics = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
            add(dashboardRequestsLabel)
            add(javax.swing.Box.createRigidArea(Dimension(0, 3)))
            add(dashboardIssuesLabel)
        }

        val left = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
            add(title)
            add(javax.swing.Box.createRigidArea(Dimension(0, 6)))
            add(metrics)
        }

        val right = JPanel(BorderLayout()).apply {
            isOpaque = false
            add(dashboardUpdatedLabel, BorderLayout.NORTH)
        }

        return CardPanel(BorderLayout(), 14).apply {
            border = EmptyBorder(10, 14, 10, 14)
            add(left, BorderLayout.CENTER)
            add(right, BorderLayout.EAST)
        }
    }

    private fun buildSiteSummaryTab() {
        val title = JLabel("Site Map Summary").apply {
            font = UiTheme.Typography.title
            foreground = UiTheme.Colors.onSurface
        }
        val subtitle = JLabel("Summarize technology, attack surface, and top endpoints from Burp Site Map.").apply {
            font = UiTheme.Typography.body
            foreground = UiTheme.Colors.onSurfaceVariant
        }
        summaryStatusLabel.font = UiTheme.Typography.body
        summaryStatusLabel.foreground = UiTheme.Colors.onSurfaceVariant

        val runButton = JButton("Run Summary").apply {
            font = UiTheme.Typography.label
            isFocusPainted = false
            addActionListener { summarizeEntireSiteMap() }
        }
        val clearButton = JButton("Clear").apply {
            font = UiTheme.Typography.label
            isFocusPainted = false
            addActionListener { summaryOutputArea.text = "" }
        }

        val titleStack = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
            add(title)
            add(javax.swing.Box.createRigidArea(Dimension(0, 4)))
            add(subtitle)
            add(javax.swing.Box.createRigidArea(Dimension(0, 4)))
            add(summaryStatusLabel)
        }

        val actionStack = JPanel(java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 8, 0)).apply {
            isOpaque = false
            add(runButton)
            add(clearButton)
        }

        val header = JPanel(BorderLayout()).apply {
            isOpaque = false
            border = EmptyBorder(12, 14, 8, 14)
            add(titleStack, BorderLayout.CENTER)
            add(actionStack, BorderLayout.EAST)
        }

        summaryOutputArea.font = UiTheme.Typography.mono
        summaryOutputArea.background = UiTheme.Colors.inputBackground
        summaryOutputArea.foreground = UiTheme.Colors.inputForeground
        summaryOutputArea.isEditable = false
        summaryOutputArea.lineWrap = true
        summaryOutputArea.wrapStyleWord = true
        summaryOutputArea.border = EmptyBorder(10, 10, 10, 10)
        summaryOutputArea.text = "Run summary to analyze the current Burp Site Map."

        val scroll = JScrollPane(summaryOutputArea).apply {
            border = EmptyBorder(0, 14, 14, 14)
            viewport.background = UiTheme.Colors.inputBackground
            horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
            verticalScrollBar.unitIncrement = 16
        }

        siteSummaryTab.background = UiTheme.Colors.cardBackground
        siteSummaryTab.add(header, BorderLayout.NORTH)
        siteSummaryTab.add(scroll, BorderLayout.CENTER)
    }

    private data class EndpointAggregate(
        val host: String,
        val path: String,
        val methods: MutableSet<String> = linkedSetOf(),
        val params: MutableSet<String> = linkedSetOf(),
        val statuses: MutableSet<Int> = linkedSetOf(),
        var hits: Int = 0,
        var authSignals: Int = 0,
        var businessSignals: Int = 0,
        var score: Int = 0
    )

    private data class HostAggregate(
        var hits: Int = 0,
        var endpointCount: Int = 0,
        var paramCount: Int = 0,
        var authEndpoints: Int = 0
    )

    private data class SiteMapDigest(
        val totalRequests: Int,
        val inScopeRequests: Int,
        val endpointCount: Int,
        val digestText: String
    )

    private fun summarizeEntireSiteMap() {
        if (mcpSupervisor.status() !is com.burpai.mcp.McpServerState.Running) {
            JOptionPane.showMessageDialog(
                root,
                "Enable MCP Server to summarize the Site Map.",
                "AI Agent",
                JOptionPane.WARNING_MESSAGE
            )
            return
        }
        if (!summaryInProgress.compareAndSet(false, true)) {
            summaryStatusLabel.text = "Summary already running..."
            workspaceTabs.selectedIndex = 1
            return
        }

        SwingUtilities.invokeLater {
            summarizeSiteMapButton.isEnabled = false
            summaryOutputArea.text = ""
            summaryStatusLabel.text = "Collecting Site Map..."
            workspaceTabs.selectedIndex = 1
        }

        thread(name = "BurpAI-SiteMapSummary", isDaemon = true) {
            try {
                val entries = api.siteMap().requestResponses()
                if (entries.isEmpty()) {
                    finishSiteSummary("Site Map is empty. Browse target traffic first.", isError = true)
                    return@thread
                }

                val digest = collectSiteMapDigest(entries)
                val settings = settingsPanel.currentSettings()
                val validationError = validateBackendCommand(settings)
                if (validationError != null) {
                    finishSiteSummary(validationError, isError = true)
                    return@thread
                }
                if (!ensureBackendReady(settings)) {
                    finishSiteSummary("Backend is not ready for Site Map summary.", isError = true)
                    return@thread
                }

                settingsRepo.save(settings)
                supervisor.applySettings(settings)
                if (!supervisor.startOrAttach(settings.preferredBackendId)) {
                    finishSiteSummary(supervisor.lastStartError() ?: "Failed to start selected backend.", isError = true)
                    return@thread
                }

                SwingUtilities.invokeLater {
                    summaryStatusLabel.text = "Streaming summary (${digest.totalRequests} entries, ${digest.endpointCount} endpoints)..."
                }

                val prompt = buildSiteMapPrompt(digest)
                supervisor.send(
                    text = prompt,
                    contextJson = null,
                    privacyMode = settings.privacyMode,
                    determinismMode = settings.determinismMode,
                    onChunk = { chunk -> appendSiteSummaryChunk(chunk) },
                    onComplete = { error ->
                        if (error != null) {
                            finishSiteSummary(error.message ?: "Unknown summary error.", isError = true)
                        } else {
                            finishSiteSummary("Summary completed.", isError = false)
                        }
                    }
                )
            } catch (e: Exception) {
                finishSiteSummary(e.message ?: "Unexpected summary failure.", isError = true)
            }
        }
    }

    private fun appendSiteSummaryChunk(chunk: String) {
        SwingUtilities.invokeLater {
            summaryOutputArea.append(chunk)
            summaryOutputArea.caretPosition = summaryOutputArea.document.length
        }
    }

    private fun finishSiteSummary(message: String, isError: Boolean) {
        summaryInProgress.set(false)
        SwingUtilities.invokeLater {
            summarizeSiteMapButton.isEnabled = true
            val timestamp = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
            summaryStatusLabel.text = if (isError) {
                "Summary failed at $timestamp"
            } else {
                "Summary completed at $timestamp"
            }
            if (isError) {
                if (summaryOutputArea.text.isBlank()) {
                    summaryOutputArea.text = "[Error] $message"
                } else {
                    summaryOutputArea.append("\n\n[Error] $message")
                }
                summaryOutputArea.caretPosition = summaryOutputArea.document.length
            }
            refreshDashboard()
        }
    }

    private fun collectSiteMapDigest(entries: List<HttpRequestResponse>): SiteMapDigest {
        val endpointMap = linkedMapOf<String, EndpointAggregate>()
        val hostMap = linkedMapOf<String, HostAggregate>()
        val techHints = linkedMapOf<String, Int>()
        val businessCandidates = linkedSetOf<String>()
        var inScopeRequests = 0
        var sampledBodies = 0

        for (entry in entries) {
            val request = entry.request()
            val rawUrl = request.url()
            val inScope = runCatching { api.scope().isInScope(rawUrl) }.getOrDefault(false)
            if (inScope) inScopeRequests++

            val uri = runCatching { URI(rawUrl) }.getOrNull()
            val host = (uri?.host ?: request.httpService()?.host() ?: "unknown").lowercase(Locale.ROOT)
            val path = normalizePath(uri?.path ?: "/")
            val method = request.method().uppercase(Locale.ROOT)
            val key = "$host|$path"
            val endpoint = endpointMap.getOrPut(key) { EndpointAggregate(host = host, path = path) }

            endpoint.hits += 1
            endpoint.methods.add(method)
            request.parameters().forEach { param ->
                val name = param.name().trim()
                if (name.isNotBlank()) endpoint.params.add(name.take(48))
            }

            val status = runCatching { entry.response()?.statusCode()?.toInt() ?: 0 }.getOrDefault(0)
            if (status > 0) endpoint.statuses.add(status)

            val pathLower = path.lowercase(Locale.ROOT)
            val hasAuthHeader = request.headers().any { h ->
                val headerName = h.name().lowercase(Locale.ROOT)
                headerName == "authorization" || headerName == "cookie" || headerName == "x-api-key" || headerName == "x-auth-token"
            }
            if (hasAuthHeader || status == 401 || status == 403 || isPotentialAuthPath(pathLower)) {
                endpoint.authSignals += 1
            }
            if (isPotentialBusinessFlow(pathLower)) {
                endpoint.businessSignals += 1
                businessCandidates.add("$method https://$host$path")
            }

            val response = entry.response()
            if (response != null) {
                response.headers().forEach { header ->
                    collectTechHintsFromHeader(header.name(), header.value(), techHints)
                }
                if (sampledBodies < 120) {
                    collectTechHintsFromBody(response.toString(), techHints)
                    sampledBodies += 1
                }
            }
        }

        endpointMap.values.forEach { endpoint ->
            endpoint.score = endpointRiskScore(endpoint)
            val hostStat = hostMap.getOrPut(endpoint.host) { HostAggregate() }
            hostStat.hits += endpoint.hits
            hostStat.endpointCount += 1
            hostStat.paramCount += endpoint.params.size
            if (endpoint.authSignals > 0) hostStat.authEndpoints += 1
        }

        val topTechHints = techHints.entries.sortedByDescending { it.value }.take(14)
        val topHosts = hostMap.entries.sortedByDescending { it.value.endpointCount }.take(12)
        val topEndpoints = endpointMap.values.sortedByDescending { it.score }.take(60)
        val endpointInventory = endpointMap.values.sortedByDescending { it.score }.take(320)
        val businessList = businessCandidates.take(30)

        val digest = buildString {
            appendLine("Site Map digest:")
            appendLine("- Total captured entries: ${entries.size}")
            appendLine("- In-scope entries: $inScopeRequests")
            appendLine("- Unique normalized endpoints: ${endpointMap.size}")
            appendLine()
            appendLine("Detected tech stack hints:")
            if (topTechHints.isEmpty()) {
                appendLine("- No strong hints detected.")
            } else {
                topTechHints.forEach { appendLine("- ${it.key} (${it.value})") }
            }
            appendLine()
            appendLine("Largest attack surface by host:")
            if (topHosts.isEmpty()) {
                appendLine("- No host data.")
            } else {
                topHosts.forEach { (host, stat) ->
                    appendLine("- $host | endpoints=${stat.endpointCount} | params=${stat.paramCount} | auth-like endpoints=${stat.authEndpoints}")
                }
            }
            appendLine()
            appendLine("Top endpoints by risk score:")
            if (topEndpoints.isEmpty()) {
                appendLine("- No endpoints available.")
            } else {
                topEndpoints.take(30).forEach { endpoint ->
                    val methods = endpoint.methods.sorted().joinToString(",")
                    appendLine("- ${methods} https://${endpoint.host}${endpoint.path} | hits=${endpoint.hits} | params=${endpoint.params.size} | authSignals=${endpoint.authSignals} | score=${endpoint.score}")
                }
            }
            appendLine()
            appendLine("Potential business logic flows:")
            if (businessList.isEmpty()) {
                appendLine("- No obvious business logic candidates from URL patterns.")
            } else {
                businessList.forEach { appendLine("- $it") }
            }
            appendLine()
            appendLine("Endpoint inventory (for AI triage):")
            endpointInventory.forEach { endpoint ->
                val methods = endpoint.methods.sorted().joinToString(",")
                val statuses = endpoint.statuses.sorted().take(4).joinToString("/")
                val params = endpoint.params.sorted().take(8).joinToString(",")
                appendLine("- ${methods} https://${endpoint.host}${endpoint.path} | hits=${endpoint.hits} | status=${statuses.ifBlank { "-" }} | params=[${params}] | score=${endpoint.score}")
            }
        }

        return SiteMapDigest(
            totalRequests = entries.size,
            inScopeRequests = inScopeRequests,
            endpointCount = endpointMap.size,
            digestText = digest
        )
    }

    private fun buildSiteMapPrompt(digest: SiteMapDigest): String {
        return """
            Always answer in English.
            You are a senior web penetration tester. Analyze the provided Burp Site Map digest and produce:
            1) Tech stack detected (high-confidence first).
            2) Largest attack surface summary (hosts/endpoints/params/auth hotspots).
            3) Top 10 endpoints to test first, each with one practical reason.
            4) Potential business logic issues to investigate next.
            Keep the output concise and actionable (bullet points, no table).

            Site Map Digest:
            ${digest.digestText}
        """.trimIndent()
    }

    private fun refreshDashboard() {
        if (!dashboardRefreshing.compareAndSet(false, true)) return
        thread(name = "BurpAI-DashboardRefresh", isDaemon = true) {
            try {
                val passiveStatus = passiveAiScanner.getStatus()
                val activeStatus = activeAiScanner.getStatus()
                val totalScanned = passiveStatus.requestsAnalyzed + activeStatus.scansCompleted

                var high = 0
                var medium = 0
                var low = 0
                api.siteMap().issues().forEach { issue ->
                    when (issue.severity()?.name?.uppercase(Locale.ROOT)) {
                        "CRITICAL", "HIGH" -> high += 1
                        "MEDIUM" -> medium += 1
                        "LOW", "INFORMATION", "INFO" -> low += 1
                    }
                }

                val timestamp = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))

                SwingUtilities.invokeLater {
                    dashboardRequestsLabel.text =
                        "Scanned requests: $totalScanned (Passive ${passiveStatus.requestsAnalyzed} | Active ${activeStatus.scansCompleted})"
                    dashboardIssuesLabel.text = "Vulns: High $high | Medium $medium | Low $low"
                    dashboardUpdatedLabel.text = "Updated: $timestamp"
                }
            } catch (_: Exception) {
                // Keep dashboard resilient if Burp APIs are transient.
            } finally {
                dashboardRefreshing.set(false)
            }
        }
    }

    private fun normalizePath(path: String): String {
        val trimmed = path.ifBlank { "/" }
        val compact = trimmed
            .replace(Regex("/\\d+"), "/{id}")
            .replace(Regex("/[a-fA-F0-9]{8,}"), "/{id}")
            .replace(Regex("/{2,}"), "/")
        return if (compact.length <= 140) compact else compact.take(137) + "..."
    }

    private fun endpointRiskScore(endpoint: EndpointAggregate): Int {
        val path = endpoint.path.lowercase(Locale.ROOT)
        var score = endpoint.hits
        score += endpoint.params.size * 3
        score += endpoint.authSignals * 4
        score += endpoint.businessSignals * 5
        if (path.contains("admin") || path.contains("internal")) score += 6
        if (path.contains("api")) score += 3
        if (path.contains("upload") || path.contains("import") || path.contains("export")) score += 4
        if (endpoint.statuses.any { it in 500..599 }) score += 2
        if (endpoint.methods.contains("POST") || endpoint.methods.contains("PUT") || endpoint.methods.contains("PATCH")) score += 2
        return score
    }

    private fun isPotentialAuthPath(pathLower: String): Boolean {
        return listOf(
            "/login", "/signin", "/logout", "/auth", "/token",
            "/oauth", "/sso", "/session", "/password", "/reset", "/mfa", "/2fa"
        ).any { pathLower.contains(it) }
    }

    private fun isPotentialBusinessFlow(pathLower: String): Boolean {
        return listOf(
            "/checkout", "/payment", "/cart", "/order", "/invoice", "/refund",
            "/wallet", "/transfer", "/subscription", "/plan", "/coupon", "/promo",
            "/redeem", "/approve", "/role", "/permission", "/invite"
        ).any { pathLower.contains(it) }
    }

    private fun collectTechHintsFromHeader(name: String, value: String, hints: MutableMap<String, Int>) {
        fun bump(key: String) {
            hints[key] = (hints[key] ?: 0) + 1
        }

        val n = name.lowercase(Locale.ROOT)
        val v = value.lowercase(Locale.ROOT)
        when (n) {
            "server" -> when {
                v.contains("nginx") -> bump("Nginx")
                v.contains("apache") -> bump("Apache")
                v.contains("iis") -> bump("Microsoft IIS")
                v.contains("cloudflare") -> bump("Cloudflare")
                v.contains("caddy") -> bump("Caddy")
                v.contains("envoy") -> bump("Envoy")
            }
            "x-powered-by" -> when {
                v.contains("express") -> bump("Node.js / Express")
                v.contains("php") -> bump("PHP")
                v.contains("asp.net") -> bump("ASP.NET")
                v.contains("next.js") -> bump("Next.js")
            }
            "x-generator" -> when {
                v.contains("wordpress") -> bump("WordPress")
                v.contains("drupal") -> bump("Drupal")
            }
            "set-cookie" -> when {
                v.contains("laravel_session") -> bump("Laravel")
                v.contains("jsessionid") -> bump("Java Servlet")
                v.contains("phpsessid") -> bump("PHP Session")
                v.contains("asp.net_sessionid") -> bump("ASP.NET Session")
            }
            "cf-ray", "cf-cache-status" -> bump("Cloudflare")
            "x-amz-cf-id", "x-cache" -> if (v.contains("cloudfront")) bump("AWS CloudFront")
            "x-vercel-id" -> bump("Vercel")
        }
    }

    private fun collectTechHintsFromBody(body: String, hints: MutableMap<String, Int>) {
        fun bump(key: String) {
            hints[key] = (hints[key] ?: 0) + 1
        }

        val sample = body.take(3000).lowercase(Locale.ROOT)
        if ("wp-content" in sample || "wordpress" in sample) bump("WordPress")
        if ("__next" in sample || "nextjs" in sample) bump("Next.js")
        if ("nuxt" in sample) bump("Nuxt.js")
        if ("react" in sample) bump("React")
        if ("angular" in sample) bump("Angular")
        if ("vue" in sample) bump("Vue.js")
        if ("laravel" in sample) bump("Laravel")
        if ("django" in sample) bump("Django")
        if ("spring" in sample) bump("Spring")
        if ("graphql" in sample) bump("GraphQL")
        if ("swagger" in sample || "openapi" in sample) bump("OpenAPI / Swagger")
    }

    private fun notifyResponseReady() {
        SwingUtilities.invokeLater {
            val pane = ensureTabPaneAttached() ?: return@invokeLater
            if (pane.selectedComponent == root) return@invokeLater
            setAttention(true)
        }
    }

    private fun ensureTabPaneAttached(): JTabbedPane? {
        if (tabbedPane != null) return tabbedPane
        val pane = findParentTabbedPane() ?: return null
        tabbedPane = pane
        pane.addChangeListener {
            if (pane.selectedComponent == root) {
                setAttention(false)
            }
        }
        return pane
    }

    private fun findParentTabbedPane(): JTabbedPane? {
        var current: java.awt.Container? = root.parent as? java.awt.Container
        while (current != null) {
            if (current is JTabbedPane) return current
            current = current.parent as? java.awt.Container
        }
        return null
    }

    private fun setAttention(active: Boolean) {
        val pane = ensureTabPaneAttached() ?: return
        val index = pane.indexOfComponent(root)
        if (index < 0) return
        val title = if (active) "$baseTabCaption *" else baseTabCaption
        if (pane.getTitleAt(index) != title) {
            pane.setTitleAt(index, title)
        }
        attentionActive = active
    }

    private fun wireActions() {
        settingsPanel.onMcpEnabledChanged = mcpSync@{ enabled ->
            if (syncingToggles) return@mcpSync
            syncingToggles = true
            mcpToggle.isSelected = enabled
            syncingToggles = false
            val settings = settingsPanel.currentSettings()
            val updated = settings.copy(mcpSettings = settings.mcpSettings.copy(enabled = enabled))
            settingsRepo.save(updated)
            mcpSupervisor.applySettings(updated.mcpSettings, updated.privacyMode, updated.determinismMode)
            renderStatus()
        }
        settingsPanel.onPassiveAiEnabledChanged = passiveSync@{ enabled ->
            if (syncingToggles) return@passiveSync
            syncingToggles = true
            passiveToggle.isSelected = enabled
            syncingToggles = false
            settingsRepo.save(settingsPanel.currentSettings())
            renderStatus()
        }
        settingsPanel.onActiveAiEnabledChanged = activeSync@{ enabled ->
            if (syncingToggles) return@activeSync
            syncingToggles = true
            activeToggle.isSelected = enabled
            syncingToggles = false
            settingsRepo.save(settingsPanel.currentSettings())
            renderStatus()
        }

        mcpToggle.addActionListener {
            if (syncingToggles) return@addActionListener
            val enabled = mcpToggle.isSelected
            syncingToggles = true
            settingsPanel.setMcpEnabled(enabled)
            syncingToggles = false
            val settings = settingsPanel.currentSettings()
            val updated = settings.copy(mcpSettings = settings.mcpSettings.copy(enabled = enabled))
            settingsRepo.save(updated)
            mcpSupervisor.applySettings(updated.mcpSettings, updated.privacyMode, updated.determinismMode)
            renderStatus()
        }
        passiveToggle.addActionListener {
            if (syncingToggles) return@addActionListener
            val enabled = passiveToggle.isSelected
            syncingToggles = true
            settingsPanel.setPassiveAiEnabled(enabled)
            syncingToggles = false
            settingsRepo.save(settingsPanel.currentSettings())
            renderStatus()
        }
        activeToggle.addActionListener {
            if (syncingToggles) return@addActionListener
            val enabled = activeToggle.isSelected
            syncingToggles = true
            settingsPanel.setActiveAiEnabled(enabled)
            syncingToggles = false
            settingsRepo.save(settingsPanel.currentSettings())
            renderStatus()
        }
    }

    private fun renderStatus() {
        SwingUtilities.invokeLater {
            val chatStatus = chatPanel.runtimeSummary()
            if (chatStatus.sessionId != null) {
                statusLabel.text = "Status: ${chatStatus.state} | Backend: ${chatStatus.backendId ?: "-"}"
                sessionLabel.text = "Session: ${chatStatus.sessionTitle ?: chatStatus.sessionId}"
                updateStatusColor(chatStatus.state)
            } else {
                val s = supervisor.status()
                statusLabel.text = "Status: ${s.state} | Backend: ${s.backendId ?: "-"}"
                val sessionId = supervisor.currentSessionId() ?: "-"
                sessionLabel.text = "Session: $sessionId"
                updateStatusColor(s.state)
            }
            updateMcpControls()
            updateMcpBadge()
            chatPanel.refreshPrivacyMode()
        }
    }

    private fun wrapInCard(component: JComponent): JComponent {
        val card = CardPanel(BorderLayout())
        card.add(component, BorderLayout.CENTER)
        return card
    }

    fun currentSettings() = settingsRepo.load()

    fun currentSessionId(): String? = supervisor.currentSessionId()

    fun openChatWithContext(capture: ContextCapture, promptTemplate: String, actionName: String) {
        chatPanel.startSessionFromContext(capture, promptTemplate, actionName)
    }

    fun refreshStatus() {
        renderStatus()
    }

    private fun updateMcpControls() {
        val mcpState = mcpSupervisor.status()
        val running = mcpState is com.burpai.mcp.McpServerState.Running
        val busy = mcpState is com.burpai.mcp.McpServerState.Starting ||
            mcpState is com.burpai.mcp.McpServerState.Stopping
        mcpToggle.isEnabled = !busy
        backendPicker.isEnabled = running && !busy
        if (running) {
            dependencyBanner.hideBanner()
        } else {
            dependencyBanner.showBanner()
        }
        chatPanel.setMcpAvailable(running)
    }

    private fun updateMcpBadge() {
        val state = mcpSupervisor.status()
        val text = when (state) {
            is com.burpai.mcp.McpServerState.Running -> "MCP: Running"
            is com.burpai.mcp.McpServerState.Starting -> "MCP: Starting"
            is com.burpai.mcp.McpServerState.Stopping -> "MCP: Stopping"
            is com.burpai.mcp.McpServerState.Failed -> {
                if (isBindFailure(state.exception)) "MCP: Port in use" else "MCP: Error"
            }
            else -> "MCP: Stopped"
        }
        mcpStatusLabel.text = text
        mcpStatusLabel.background = when (state) {
            is com.burpai.mcp.McpServerState.Running -> UiTheme.Colors.statusRunning
            is com.burpai.mcp.McpServerState.Failed -> UiTheme.Colors.statusCrashed
            is com.burpai.mcp.McpServerState.Starting -> UiTheme.Colors.statusTerminal
            is com.burpai.mcp.McpServerState.Stopping -> UiTheme.Colors.statusTerminal
            else -> UiTheme.Colors.outlineVariant
        }
    }

    private fun isBindFailure(exception: Throwable): Boolean {
        var current: Throwable? = exception
        while (current != null) {
            if (current is java.net.BindException) return true
            current = current.cause
        }
        return false
    }

    private fun styleStatusLabel(label: JLabel) {
        label.font = UiTheme.Typography.body
        label.isOpaque = true
        label.border = EmptyBorder(4, 8, 4, 8)
        label.foreground = UiTheme.Colors.onSurface
        label.background = UiTheme.Colors.outlineVariant
    }

    private fun updateStatusColor(state: String) {
        val color = when (state) {
            "Running" -> UiTheme.Colors.statusRunning
            "Ready" -> UiTheme.Colors.statusRunning
            "Sending" -> UiTheme.Colors.statusTerminal
            "Crashed" -> UiTheme.Colors.statusCrashed
            "Error" -> UiTheme.Colors.statusCrashed
            "Blocked" -> UiTheme.Colors.outlineVariant
            // Terminal status removed
            else -> UiTheme.Colors.outlineVariant
        }
        statusLabel.background = color
    }

    private fun openSettingsStudio() {
        if (settingsDialog == null) {
            val owner = SwingUtilities.getWindowAncestor(root)
            val dialog = JDialog(owner, "BurpAI Settings Studio", Dialog.ModalityType.MODELESS)
            dialog.contentPane = bottomTabsPanel.root
            dialog.minimumSize = Dimension(860, 620)
            dialog.setSize(1024, 720)
            dialog.setLocationRelativeTo(owner)
            settingsDialog = dialog
        }
        settingsDialog?.isVisible = true
        settingsDialog?.toFront()
    }

    internal fun validateBackendCommand(settings: com.burpai.config.AgentSettings): String? {
        return when (settings.preferredBackendId) {
            "codex-cli" -> if (settings.codexCmd.isBlank()) "Codex command is empty." else null
            "gemini-cli" -> if (settings.geminiCmd.isBlank()) "Gemini command is empty." else null
            "opencode-cli" -> {
                when {
                    settings.opencodeCmd.isBlank() -> "OpenCode command is empty."
                    isWindows() && looksLikeBareExe(settings.opencodeCmd) ->
                        "OpenCode command looks like a bare .exe. If installed via npm, use 'opencode' (without .exe) or a full path to opencode.cmd."
                    else -> null
                }
            }
            "claude-cli" -> if (settings.claudeCmd.isBlank()) "Claude command is empty." else null
            "ollama" -> if (settings.ollamaCliCmd.isBlank()) "Ollama CLI command is empty." else null
            "lmstudio" -> if (settings.lmStudioUrl.isBlank()) "LM Studio URL is empty." else null
            "openai-compatible" -> {
                val url = if (settings.openAiCompatibleUseOpenRouter) {
                    AgentSettingsRepository.defaultOpenAiCompatUrl()
                } else {
                    settings.openAiCompatibleUrl
                }
                when {
                    !settings.openAiCompatibleUseOpenRouter && url.isBlank() -> "OpenAI-compatible URL is empty."
                    settings.openAiCompatibleModel.isBlank() -> "OpenAI-compatible model is empty."
                    else -> null
                }
            }
            else -> "Unsupported backend: ${settings.preferredBackendId}"
        }
    }

    private fun looksLikeBareExe(cmd: String): Boolean {
        val trimmed = cmd.trim()
        if (!trimmed.lowercase().endsWith(".exe")) return false
        return !trimmed.contains("\\") && !trimmed.contains("/")
    }

    private fun isWindows(): Boolean {
        val os = System.getProperty("os.name").lowercase()
        return os.contains("win")
    }

    internal fun showError(message: String) {
        SwingUtilities.invokeLater {
            JOptionPane.showMessageDialog(
                root,
                message,
                "AI Agent",
                JOptionPane.ERROR_MESSAGE
            )
        }
    }

    internal fun ensureOllamaReadyIfNeeded(settings: com.burpai.config.AgentSettings): Boolean {
        if (settings.preferredBackendId != "ollama") return true
        if (supervisor.isOllamaHealthy(settings)) return true
        val result = JOptionPane.showConfirmDialog(
            root,
            "Ollama is not running. Start it now?",
            "AI Agent",
            JOptionPane.YES_NO_OPTION
        )
        if (result != JOptionPane.YES_OPTION) return false
        if (!settings.ollamaAutoStart) {
            showError("Auto-start for Ollama is disabled in settings.")
            return false
        }
        val ok = supervisor.startOllamaService(settings)
        if (!ok) showError("Failed to start Ollama. Check the command in settings.")
        return ok
    }

    internal fun ensureLmStudioReadyIfNeeded(settings: com.burpai.config.AgentSettings): Boolean {
        if (settings.preferredBackendId != "lmstudio") return true
        if (supervisor.isLmStudioHealthy(settings)) return true

        if (settings.lmStudioAutoStart) {
            val ok = supervisor.startLmStudioService(settings)
            if (!ok) showError("Failed to auto-start LM Studio. Check the command in settings.")
            return ok
        }

        val result = JOptionPane.showConfirmDialog(
            root,
            "LM Studio is not running. Start it now?",
            "AI Agent",
            JOptionPane.YES_NO_OPTION
        )
        if (result != JOptionPane.YES_OPTION) return false
        
        val ok = supervisor.startLmStudioService(settings)
        if (!ok) showError("Failed to start LM Studio. Check the command in settings.")
        return ok
    }

    private fun ensureBackendReady(settings: com.burpai.config.AgentSettings): Boolean {
        return when (settings.preferredBackendId) {
            "ollama" -> ensureOllamaReadyIfNeeded(settings)
            "lmstudio" -> ensureLmStudioReadyIfNeeded(settings)
            else -> true
        }
    }
}
