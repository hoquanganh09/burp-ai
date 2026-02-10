package com.burpai.ui

import burp.api.montoya.MontoyaApi
import burp.api.montoya.http.message.HttpRequestResponse
import burp.api.montoya.scanner.audit.issues.AuditIssue
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
import java.util.concurrent.atomic.AtomicLong
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JOptionPane
import javax.swing.JScrollPane
import javax.swing.JProgressBar
import javax.swing.JSplitPane
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
    private val backendStatusLabel = JLabel("Backend: -")
    private val summaryAgeLabel = JLabel("Last summary: never")
    private val settingsRepo = AgentSettingsRepository(api)
    private val disposed = AtomicBoolean(false)
    private val mcpStatusTimer = Timer(1000) {
        if (disposed.get()) return@Timer
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
    private val summarizeSiteMapButton = JButton("\uD83E\uDDE0 Summarize Entire Site Map")
    private val dashboardRequestsLabel = JLabel("Scanned requests: 0")
    private val dashboardProgressBar = JProgressBar(0, 100)
    private val dashboardHighLabel = JLabel("HIGH 0")
    private val dashboardMediumLabel = JLabel("MEDIUM 0")
    private val dashboardLowLabel = JLabel("LOW 0")
    private val dashboardLastSummaryLabel = JLabel("Last summary: never")
    private val dashboardUpdatedLabel = JLabel("Updated: -")
    private val dashboardRefreshing = AtomicBoolean(false)
    private val summaryInProgress = AtomicBoolean(false)
    private val summaryOutputArea = JTextArea()
    private val summaryStatusLabel = JLabel("Ready")
    private val lastSummaryCompletedAt = AtomicLong(0L)
    private val workspaceTabs = JTabbedPane()
    private val siteSummaryTab = JPanel(BorderLayout())
    private val dashboardTimer = Timer(2500) {
        if (disposed.get()) return@Timer
        refreshDashboard()
    }

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
        subtitle.font = UiTheme.Typography.aiText
        subtitle.foreground = UiTheme.Colors.onSurfaceVariant

        settingsButton.font = UiTheme.Typography.aiButton
        settingsButton.background = UiTheme.Colors.primary
        settingsButton.foreground = UiTheme.Colors.onPrimary
        settingsButton.isOpaque = true
        settingsButton.border = EmptyBorder(6, 8, 6, 8)
        settingsButton.margin = java.awt.Insets(6, 8, 6, 8)
        settingsButton.isFocusPainted = false
        settingsButton.addActionListener { openSettingsStudio() }
        val moddedByLabel = JLabel("moded by 0x4nh8ii").apply {
            font = UiTheme.Typography.aiStatus
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

        mcpLabel.font = UiTheme.Typography.aiText
        mcpLabel.foreground = UiTheme.Colors.onSurfaceVariant
        backendLabel.font = UiTheme.Typography.aiText
        backendLabel.foreground = UiTheme.Colors.onSurfaceVariant
        backendPicker.font = UiTheme.Typography.aiText
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
            border = EmptyBorder(12, 12, 12, 12)
            add(titleBox, BorderLayout.CENTER)
            add(headerActions, BorderLayout.EAST)
        }
        val commandBar = buildCommandBar()
        val dashboardPanel = buildDashboardPanel()
        buildSiteSummaryTab()

        val north = JPanel()
        north.layout = BoxLayout(north, BoxLayout.Y_AXIS)
        north.background = UiTheme.Colors.surface
        north.border = EmptyBorder(12, 12, 8, 12)
        north.add(hero)
        north.add(javax.swing.Box.createRigidArea(Dimension(0, 8)))
        north.add(commandBar)
        north.add(javax.swing.Box.createRigidArea(Dimension(0, 8)))
        north.add(dependencyBanner)

        val leftColumn = JPanel(BorderLayout()).apply {
            background = UiTheme.Colors.surface
            border = EmptyBorder(0, 10, 10, 10)
            add(wrapInCard(chatPanel.sessionsComponent()), BorderLayout.CENTER)
        }

        workspaceTabs.font = UiTheme.Typography.aiText
        workspaceTabs.background = UiTheme.Colors.surface
        workspaceTabs.foreground = UiTheme.Colors.onSurface
        workspaceTabs.addTab("Chat", wrapInCard(chatPanel.root))
        workspaceTabs.addTab("Site Summary", wrapInCard(siteSummaryTab))

        val centerRightSplit = JSplitPane(
            JSplitPane.HORIZONTAL_SPLIT,
            workspaceTabs,
            wrapInCard(dashboardPanel)
        ).apply {
            resizeWeight = 0.78
            setDividerLocation(0.78)
            border = EmptyBorder(0, 0, 0, 0)
            dividerSize = 12
        }

        val mainContent = JSplitPane(
            JSplitPane.HORIZONTAL_SPLIT,
            leftColumn,
            centerRightSplit
        )
        mainContent.resizeWeight = 0.22
        mainContent.setDividerLocation(0.22)
        mainContent.border = EmptyBorder(0, 0, 0, 0)
        mainContent.dividerSize = 12

        root.add(north, BorderLayout.NORTH)
        root.add(mainContent, BorderLayout.CENTER)
        root.add(buildStatusBar(), BorderLayout.SOUTH)

        wireActions()
        renderStatus()
        refreshDashboard()
        mcpStatusTimer.start()
        dashboardTimer.start()
    }

    private fun buildCommandBar(): JComponent {
        styleStatusLabel(mcpStatusLabel)
        summarizeSiteMapButton.font = UiTheme.Typography.aiButton
        summarizeSiteMapButton.margin = java.awt.Insets(6, 8, 6, 8)
        summarizeSiteMapButton.isFocusPainted = false
        summarizeSiteMapButton.toolTipText = "Analyze all in-scope hosts"
        summarizeSiteMapButton.addActionListener { summarizeEntireSiteMap() }

        val bar = CardPanel(BorderLayout(), 16)
        val content = JPanel(java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 16, 8))
        content.isOpaque = false

        backendPicker.preferredSize = Dimension(220, backendPicker.preferredSize.height)

        val passiveLabel = JLabel("Passive").apply {
            font = UiTheme.Typography.aiText
            foreground = UiTheme.Colors.onSurfaceVariant
        }
        val activeLabel = JLabel("Active").apply {
            font = UiTheme.Typography.aiText
            foreground = UiTheme.Colors.onSurfaceVariant
        }

        content.add(buildBarGroup("MCP", mcpToggle, mcpStatusLabel))
        content.add(buildBarGroup("Scanners", passiveLabel, passiveToggle, activeLabel, activeToggle))
        content.add(buildBarGroup("Backend", backendPicker))
        content.add(buildBarGroup("Workspace", summarizeSiteMapButton))

        bar.add(content, BorderLayout.CENTER)
        return bar
    }

    private fun buildBarGroup(title: String, vararg components: JComponent): JComponent {
        val group = JPanel()
        group.layout = BoxLayout(group, BoxLayout.Y_AXIS)
        group.isOpaque = false
        val label = JLabel(title)
        label.font = UiTheme.Typography.aiText.deriveFont(java.awt.Font.BOLD, 12f)
        label.foreground = UiTheme.Colors.onSurfaceVariant
        val row = JPanel(java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 8, 0))
        row.isOpaque = false
        components.forEach { row.add(it) }
        group.add(label)
        group.add(javax.swing.Box.createRigidArea(Dimension(0, 8)))
        group.add(row)
        return group
    }

    private fun buildDashboardPanel(): JComponent {
        val title = JLabel("Smart Dashboard").apply {
            font = UiTheme.Typography.aiHeader
            foreground = UiTheme.Colors.onSurface
        }
        dashboardRequestsLabel.font = UiTheme.Typography.aiText
        dashboardRequestsLabel.foreground = UiTheme.Colors.onSurfaceVariant
        dashboardProgressBar.isStringPainted = true
        dashboardProgressBar.value = 0
        dashboardProgressBar.string = "0 analyzed"
        dashboardProgressBar.foreground = UiTheme.Colors.primary
        dashboardProgressBar.background = UiTheme.Colors.outlineVariant
        dashboardProgressBar.border = javax.swing.border.LineBorder(UiTheme.Colors.outline, 1, true)
        dashboardHighLabel.font = UiTheme.Typography.aiText.deriveFont(java.awt.Font.BOLD, 12f)
        dashboardHighLabel.foreground = java.awt.Color(0xB3261E)
        dashboardMediumLabel.font = UiTheme.Typography.aiText.deriveFont(java.awt.Font.BOLD, 12f)
        dashboardMediumLabel.foreground = java.awt.Color(0xC46E00)
        dashboardLowLabel.font = UiTheme.Typography.aiText.deriveFont(java.awt.Font.BOLD, 12f)
        dashboardLowLabel.foreground = java.awt.Color(0x1E7D3C)
        dashboardLastSummaryLabel.font = UiTheme.Typography.aiText
        dashboardLastSummaryLabel.foreground = UiTheme.Colors.onSurfaceVariant
        dashboardUpdatedLabel.font = UiTheme.Typography.aiStatus
        dashboardUpdatedLabel.foreground = UiTheme.Colors.onSurfaceVariant

        val metrics = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
            add(dashboardRequestsLabel)
            add(javax.swing.Box.createRigidArea(Dimension(0, 8)))
            add(dashboardProgressBar)
            add(javax.swing.Box.createRigidArea(Dimension(0, 8)))
            add(dashboardHighLabel)
            add(javax.swing.Box.createRigidArea(Dimension(0, 8)))
            add(dashboardMediumLabel)
            add(javax.swing.Box.createRigidArea(Dimension(0, 8)))
            add(dashboardLowLabel)
            add(javax.swing.Box.createRigidArea(Dimension(0, 8)))
            add(dashboardLastSummaryLabel)
            add(javax.swing.Box.createRigidArea(Dimension(0, 8)))
            add(dashboardUpdatedLabel)
        }

        val left = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
            add(title)
            add(javax.swing.Box.createRigidArea(Dimension(0, 8)))
            add(metrics)
        }

        return CardPanel(BorderLayout(), 14).apply {
            border = EmptyBorder(12, 12, 12, 12)
            add(left, BorderLayout.CENTER)
        }
    }

    private fun buildStatusBar(): JComponent {
        styleStatusLabel(statusLabel)
        statusLabel.text = "Idle"

        backendStatusLabel.font = UiTheme.Typography.aiStatus
        backendStatusLabel.foreground = UiTheme.Colors.onSurfaceVariant
        sessionLabel.font = UiTheme.Typography.aiStatus
        sessionLabel.foreground = UiTheme.Colors.onSurfaceVariant
        summaryAgeLabel.font = UiTheme.Typography.aiStatus
        summaryAgeLabel.foreground = UiTheme.Colors.onSurfaceVariant

        val left = JPanel(java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 12, 0)).apply {
            isOpaque = false
            add(JLabel("State:").apply {
                font = UiTheme.Typography.aiStatus.deriveFont(java.awt.Font.BOLD, 11f)
                foreground = UiTheme.Colors.onSurfaceVariant
            })
            add(statusLabel)
            add(backendStatusLabel)
            add(sessionLabel)
        }
        val right = JPanel(java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 12, 0)).apply {
            isOpaque = false
            add(summaryAgeLabel)
        }

        return CardPanel(BorderLayout(), 12).apply {
            border = EmptyBorder(8, 12, 8, 12)
            add(left, BorderLayout.WEST)
            add(right, BorderLayout.EAST)
        }
    }

    private fun buildSiteSummaryTab() {
        val title = JLabel("Site Map Summary").apply {
            font = UiTheme.Typography.aiHeader
            foreground = UiTheme.Colors.onSurface
        }
        val subtitle = JLabel("Summarize technology, attack surface, and top endpoints from Burp Site Map.").apply {
            font = UiTheme.Typography.aiText
            foreground = UiTheme.Colors.onSurfaceVariant
        }
        summaryStatusLabel.font = UiTheme.Typography.aiStatus
        summaryStatusLabel.foreground = UiTheme.Colors.onSurfaceVariant

        val runButton = JButton("\uD83E\uDDE0 Run Summary").apply {
            font = UiTheme.Typography.aiButton
            margin = java.awt.Insets(6, 8, 6, 8)
            isFocusPainted = false
            addActionListener { summarizeEntireSiteMap() }
        }
        val clearButton = JButton("Clear").apply {
            font = UiTheme.Typography.aiButton
            margin = java.awt.Insets(6, 8, 6, 8)
            isFocusPainted = false
            addActionListener { summaryOutputArea.text = "" }
        }

        val titleStack = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
            add(title)
            add(javax.swing.Box.createRigidArea(Dimension(0, 8)))
            add(subtitle)
            add(javax.swing.Box.createRigidArea(Dimension(0, 8)))
            add(summaryStatusLabel)
        }

        val actionStack = JPanel(java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 8, 0)).apply {
            isOpaque = false
            add(runButton)
            add(clearButton)
        }

        val header = JPanel(BorderLayout()).apply {
            isOpaque = false
            border = EmptyBorder(12, 12, 8, 12)
            add(titleStack, BorderLayout.CENTER)
            add(actionStack, BorderLayout.EAST)
        }

        summaryOutputArea.font = UiTheme.Typography.aiMono
        summaryOutputArea.background = UiTheme.Colors.inputBackground
        summaryOutputArea.foreground = UiTheme.Colors.inputForeground
        summaryOutputArea.isEditable = false
        summaryOutputArea.lineWrap = true
        summaryOutputArea.wrapStyleWord = true
        summaryOutputArea.border = EmptyBorder(12, 12, 12, 12)
        summaryOutputArea.text = "Run summary to analyze the current Burp Site Map."

        val scroll = JScrollPane(summaryOutputArea).apply {
            border = EmptyBorder(0, 12, 12, 12)
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
        if (disposed.get()) return
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
            if (disposed.get()) return@thread
            try {
                val entries = safeSiteMapRequestResponses()
                if (entries == null) {
                    finishSiteSummary("Site Map API is unavailable in current Burp context.", isError = true)
                    return@thread
                }
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
                    onChunk = { chunk ->
                        if (disposed.get()) return@send
                        appendSiteSummaryChunk(chunk)
                    },
                    onComplete = { error ->
                        if (disposed.get()) return@send
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
        if (disposed.get()) return
        SwingUtilities.invokeLater {
            if (disposed.get()) return@invokeLater
            summaryOutputArea.append(chunk)
            summaryOutputArea.caretPosition = summaryOutputArea.document.length
        }
    }

    private fun finishSiteSummary(message: String, isError: Boolean) {
        if (disposed.get()) return
        summaryInProgress.set(false)
        SwingUtilities.invokeLater {
            if (disposed.get()) return@invokeLater
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
            } else {
                lastSummaryCompletedAt.set(System.currentTimeMillis())
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
            val request = runCatching { entry.request() }.getOrNull() ?: continue
            val rawUrl = runCatching { request.url() }.getOrNull() ?: continue
            val inScope = runCatching { api.scope().isInScope(rawUrl) }.getOrDefault(false)
            if (inScope) inScopeRequests++

            val uri = runCatching { URI(rawUrl) }.getOrNull()
            val host = (uri?.host ?: request.httpService()?.host() ?: "unknown").lowercase(Locale.ROOT)
            val path = normalizePath(uri?.path ?: "/")
            val method = runCatching { request.method() }.getOrDefault("GET").uppercase(Locale.ROOT)
            val key = "$host|$path"
            val endpoint = endpointMap.getOrPut(key) { EndpointAggregate(host = host, path = path) }

            endpoint.hits += 1
            endpoint.methods.add(method)
            runCatching { request.parameters().toList() }.getOrDefault(emptyList()).forEach { param ->
                val name = param.name().trim()
                if (name.isNotBlank()) endpoint.params.add(name.take(48))
            }

            val status = runCatching { entry.response()?.statusCode()?.toInt() ?: 0 }.getOrDefault(0)
            if (status > 0) endpoint.statuses.add(status)

            val pathLower = path.lowercase(Locale.ROOT)
            val hasAuthHeader = runCatching { request.headers().toList() }.getOrDefault(emptyList()).any { h ->
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
        if (disposed.get()) return
        if (!dashboardRefreshing.compareAndSet(false, true)) return
        thread(name = "BurpAI-DashboardRefresh", isDaemon = true) {
            if (disposed.get()) {
                dashboardRefreshing.set(false)
                return@thread
            }
            try {
                val passiveStatus = passiveAiScanner.getStatus()
                val activeStatus = activeAiScanner.getStatus()
                val totalScanned = passiveStatus.requestsAnalyzed + activeStatus.scansCompleted

                var high = 0
                var medium = 0
                var low = 0
                safeSiteMapIssues().forEach { issue ->
                    when (issue.severity()?.name?.uppercase(Locale.ROOT)) {
                        "CRITICAL", "HIGH" -> high += 1
                        "MEDIUM" -> medium += 1
                        "LOW", "INFORMATION", "INFO" -> low += 1
                    }
                }

                val timestamp = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
                val summaryAge = formatRelativeSummaryAge(lastSummaryCompletedAt.get())

                SwingUtilities.invokeLater {
                    if (disposed.get()) return@invokeLater
                    dashboardRequestsLabel.text =
                        "Scanned requests: $totalScanned (Passive ${passiveStatus.requestsAnalyzed} | Active ${activeStatus.scansCompleted})"
                    updateScanProgress(totalScanned)
                    dashboardHighLabel.text = "HIGH $high"
                    dashboardMediumLabel.text = "MEDIUM $medium"
                    dashboardLowLabel.text = "LOW $low"
                    dashboardLastSummaryLabel.text = "Last summary: $summaryAge"
                    summaryAgeLabel.text = "Last summary: $summaryAge"
                    dashboardUpdatedLabel.text = "Updated: $timestamp"
                }
            } catch (_: Throwable) {
                // Keep dashboard resilient if Burp APIs are transient or unavailable.
                SwingUtilities.invokeLater {
                    if (disposed.get()) return@invokeLater
                    val timestamp = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
                    dashboardHighLabel.text = "HIGH 0"
                    dashboardMediumLabel.text = "MEDIUM 0"
                    dashboardLowLabel.text = "LOW 0"
                    dashboardUpdatedLabel.text = "Updated: $timestamp"
                }
            } finally {
                dashboardRefreshing.set(false)
            }
        }
    }

    private fun updateScanProgress(totalScanned: Int) {
        val safeTotal = totalScanned.coerceAtLeast(0)
        val step = 50
        val max = ((safeTotal / step) + 1) * step
        dashboardProgressBar.maximum = max.coerceAtLeast(step)
        dashboardProgressBar.value = safeTotal.coerceAtMost(dashboardProgressBar.maximum)
        dashboardProgressBar.string = "$safeTotal analyzed"
    }

    private fun formatRelativeSummaryAge(timestamp: Long): String {
        if (timestamp <= 0L) return "never"
        val deltaMs = (System.currentTimeMillis() - timestamp).coerceAtLeast(0L)
        val seconds = deltaMs / 1000
        return when {
            seconds < 10 -> "just now"
            seconds < 60 -> "${seconds}s ago"
            seconds < 3600 -> "${seconds / 60} min ago"
            else -> "${seconds / 3600} h ago"
        }
    }

    private fun safeSiteMapRequestResponses(): List<HttpRequestResponse>? {
        val siteMap = runCatching { api.siteMap() }.getOrNull() ?: return null
        // Materialize to a snapshot inside runCatching because Montoya may return lazy proxies.
        return runCatching { siteMap.requestResponses().toList() }.getOrNull()
    }

    private fun safeSiteMapIssues(): List<AuditIssue> {
        val siteMap = runCatching { api.siteMap() }.getOrNull() ?: return emptyList()
        // Materialize to a snapshot inside runCatching because Montoya may return lazy proxies.
        return runCatching { siteMap.issues().toList() }.getOrNull().orEmpty()
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
                statusLabel.text = chatStatus.state
                backendStatusLabel.text = "Backend: ${chatStatus.backendId ?: "-"}"
                sessionLabel.text = "Session: ${chatStatus.sessionTitle ?: chatStatus.sessionId}"
                updateStatusColor(chatStatus.state)
            } else {
                val s = supervisor.status()
                statusLabel.text = s.state
                backendStatusLabel.text = "Backend: ${s.backendId ?: "-"}"
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
        label.font = UiTheme.Typography.aiStatus
        label.isOpaque = true
        label.border = EmptyBorder(3, 8, 3, 8)
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
        if (disposed.get()) return
        if (settingsDialog == null) {
            val owner = SwingUtilities.getWindowAncestor(root)
            val dialog = JDialog(owner, "BurpAI Settings Studio", Dialog.ModalityType.MODELESS)
            dialog.contentPane = bottomTabsPanel.root
            dialog.minimumSize = Dimension(860, 620)
            dialog.setSize(1024, 720)
            dialog.setLocationRelativeTo(owner)
            dialog.defaultCloseOperation = JDialog.DISPOSE_ON_CLOSE
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

    fun dispose() {
        if (!disposed.compareAndSet(false, true)) return
        runCatching { mcpStatusTimer.stop() }
        runCatching { dashboardTimer.stop() }
        runCatching {
            settingsDialog?.isVisible = false
            settingsDialog?.dispose()
            settingsDialog = null
        }
        runCatching { settingsPanel.dispose() }
        runCatching { chatPanel.dispose() }
    }
}
