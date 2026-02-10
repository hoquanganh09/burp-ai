package com.burpai.ui

import burp.api.montoya.MontoyaApi
import com.burpai.agents.AgentProfileLoader
import com.burpai.config.AgentSettings
import com.burpai.context.ContextCapture
import com.burpai.mcp.McpRequestLimiter
import com.burpai.mcp.McpToolCatalog
import com.burpai.mcp.McpToolContext
import com.burpai.mcp.tools.McpToolExecutor
import com.burpai.prompt.PromptTemplateLibrary
import com.burpai.redact.PrivacyMode
import com.burpai.supervisor.AgentSupervisor
import com.burpai.ui.components.ActionCard
import com.burpai.ui.components.CardPanel
import com.burpai.ui.components.PrivacyPill
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.net.URI
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.BoxLayout
import javax.swing.JScrollPane
import javax.swing.JTextArea
import javax.swing.ListSelectionModel
import javax.swing.SwingUtilities
import javax.swing.border.EmptyBorder
import javax.swing.event.ListDataEvent
import javax.swing.event.ListDataListener

class ChatPanel(
    private val api: MontoyaApi,
    private val supervisor: AgentSupervisor,
    private val getSettings: () -> AgentSettings,
    private val applySettings: (AgentSettings) -> Unit,
    private val validateBackend: (AgentSettings) -> String?,
    private val ensureBackendReady: (AgentSettings) -> Boolean,
    private val showError: (String) -> Unit,
    private val onStatusChanged: () -> Unit,
    private val onResponseReady: () -> Unit
) {
    data class RuntimeSummary(
        val state: String,
        val backendId: String?,
        val sessionTitle: String?,
        val sessionId: String?
    )

    val root: JComponent = JPanel(BorderLayout())
    private val sessionsModel = DefaultListModel<ChatSession>()
    private val sessionsList = JList(sessionsModel)
    private val sessionsPanel = JPanel(BorderLayout())
    private val sessionsCard = JPanel(CardLayout())
    private val chatCards = JPanel(CardLayout())
    private val sendBtn = JButton("Send")
    private val clearChatBtn = JButton("Clear Chat")
    private val toolsBtn = JButton("Tools")
    private val inputArea = JTextArea(3, 24)
    private val newSessionBtn = JButton("New Session")
    private val privacyPill = PrivacyPill()
    private val sessionPanels = linkedMapOf<String, SessionPanel>()
    private val sessionStates = linkedMapOf<String, ToolSessionState>()
    private val sessionsById = linkedMapOf<String, ChatSession>()
    private var mcpAvailable = true
    private val emptyStatePanel = CardPanel(BorderLayout())
    private val emptyTitle = JLabel("Start your first session")
    private val emptySubtitle = JLabel("Create a chat and begin analyzing traffic.")
    private val emptyButton = JButton("Create Session")
    private val chatEmptyPanel = CardPanel(BorderLayout())
    private val chatEmptyTitle = JLabel("No active session")
    private val chatEmptySubtitle = JLabel("Select a session or create a new one to begin.")
    private val chatEmptyButton = JButton("New Session")
    @Volatile
    private var chatRuntimeState: String = "Idle"

    init {
        root.background = UiTheme.Colors.surface

        sessionsList.selectionMode = ListSelectionModel.SINGLE_SELECTION
        sessionsList.font = UiTheme.Typography.aiText
        sessionsList.cellRenderer = ChatSessionRenderer()
        sessionsList.background = UiTheme.Colors.cardBackground
        sessionsList.foreground = UiTheme.Colors.onSurface
        sessionsList.addListSelectionListener {
            val selected = sessionsList.selectedValue ?: return@addListSelectionListener
            showSession(selected.id)
        }
        sessionsList.addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mousePressed(e: java.awt.event.MouseEvent) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    val index = sessionsList.locationToIndex(e.point)
                    if (index != -1) {
                        sessionsList.selectedIndex = index
                        showSessionContextMenu(e.component, e.x, e.y)
                    }
                }
            }
        })

        clearChatBtn.font = UiTheme.Typography.aiButton
        clearChatBtn.margin = java.awt.Insets(6, 8, 6, 8)
        clearChatBtn.isFocusPainted = false
        clearChatBtn.addActionListener { clearCurrentChat() }

        toolsBtn.font = UiTheme.Typography.aiButton
        toolsBtn.margin = java.awt.Insets(6, 8, 6, 8)
        toolsBtn.isFocusPainted = false
        toolsBtn.toolTipText = "Browse and invoke MCP tools. Select a tool to insert /tool <id> {} into input. Fill JSON args and Send to execute."
        toolsBtn.addActionListener { showToolsMenu() }

        newSessionBtn.font = UiTheme.Typography.aiButton
        newSessionBtn.margin = java.awt.Insets(6, 8, 6, 8)
        newSessionBtn.isFocusPainted = false
        newSessionBtn.addActionListener { createSession("Chat ${sessionsModel.size + 1}") }

        val listScroll = JScrollPane(sessionsList)
        listScroll.border = EmptyBorder(12, 12, 12, 12)
        listScroll.preferredSize = Dimension(200, 400)
        listScroll.viewport.background = UiTheme.Colors.cardBackground
        listScroll.horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
        listScroll.verticalScrollBar.unitIncrement = 16

        val listHeader = JPanel(BorderLayout())
        listHeader.isOpaque = true
        listHeader.background = UiTheme.Colors.cardBackground
        val listTitle = JLabel("Sessions")
        listTitle.font = UiTheme.Typography.aiHeader
        listTitle.foreground = UiTheme.Colors.onSurfaceVariant
        listHeader.add(listTitle, BorderLayout.WEST)
        listHeader.add(newSessionBtn, BorderLayout.EAST)
        listHeader.border = EmptyBorder(10, 12, 10, 12)

        buildEmptyState()
        buildChatEmptyState()
        sessionsCard.isOpaque = false
        sessionsCard.add(listScroll, "list")
        sessionsCard.add(emptyStatePanel, "empty")

        sessionsPanel.add(listHeader, BorderLayout.NORTH)
        sessionsPanel.add(sessionsCard, BorderLayout.CENTER)
        sessionsPanel.background = UiTheme.Colors.cardBackground
        sessionsModel.addListDataListener(object : ListDataListener {
            override fun intervalAdded(e: ListDataEvent) = updateEmptyState()
            override fun intervalRemoved(e: ListDataEvent) = updateEmptyState()
            override fun contentsChanged(e: ListDataEvent) = updateEmptyState()
        })
        updateEmptyState()

        chatCards.isOpaque = false
        chatCards.background = UiTheme.Colors.cardBackground
        chatCards.add(chatEmptyPanel, "empty")

        val chatContainer = JPanel(BorderLayout())
        chatContainer.background = UiTheme.Colors.cardBackground
        chatContainer.add(chatCards, BorderLayout.CENTER)
        chatContainer.add(inputPanel(), BorderLayout.SOUTH)

        root.add(chatContainer, BorderLayout.CENTER)
    }

    fun sessionsComponent(): JComponent = sessionsPanel

    fun runtimeSummary(): RuntimeSummary {
        val selected = sessionsList.selectedValue
        return if (selected == null) {
            RuntimeSummary(
                state = if (mcpAvailable) "Idle" else "Blocked",
                backendId = null,
                sessionTitle = null,
                sessionId = null
            )
        } else {
            RuntimeSummary(
                state = chatRuntimeState,
                backendId = selected.backendId,
                sessionTitle = selected.title,
                sessionId = selected.id
            )
        }
    }

    fun setMcpAvailable(available: Boolean) {
        mcpAvailable = available
        updateChatAvailability()
        syncStateWithContext()
    }

    private fun saveAsBurpNote(noteText: String): String? {
        val payload = noteText.trim()
        if (payload.isBlank()) return null

        val history = runCatching { api.proxy().history().toList() }.getOrNull().orEmpty()
        if (history.isEmpty()) return null

        val target = history.asReversed().firstOrNull { item ->
            val url = item.request()?.url() ?: return@firstOrNull false
            runCatching { api.scope().isInScope(url) }.getOrDefault(false)
        } ?: history.lastOrNull()
        if (target == null) return null

        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        val noteBlock = "[BurpAI $timestamp]\n${payload.take(12000)}"
        val existing = runCatching { target.annotations().notes() }.getOrNull().orEmpty().trim()
        val merged = if (existing.isBlank()) noteBlock else "$existing\n\n$noteBlock"
        runCatching { target.annotations().setNotes(merged.take(32000)) }
        return runCatching { target.request()?.url() }.getOrNull()
    }

    fun refreshPrivacyMode() {
        updatePrivacyPill()
    }

    private fun buildEmptyState() {
        emptyTitle.font = UiTheme.Typography.aiHeader
        emptyTitle.foreground = UiTheme.Colors.onSurface
        emptySubtitle.font = UiTheme.Typography.aiText
        emptySubtitle.foreground = UiTheme.Colors.onSurfaceVariant
        emptyButton.font = UiTheme.Typography.aiButton
        emptyButton.background = UiTheme.Colors.primary
        emptyButton.foreground = UiTheme.Colors.onPrimary
        emptyButton.isOpaque = true
        emptyButton.isFocusPainted = false
        emptyButton.border = EmptyBorder(6, 8, 6, 8)
        emptyButton.margin = java.awt.Insets(6, 8, 6, 8)
        emptyButton.addActionListener { createSession("Chat ${sessionsModel.size + 1}") }

        val stack = JPanel()
        stack.layout = BoxLayout(stack, BoxLayout.Y_AXIS)
        stack.isOpaque = false
        stack.add(emptyTitle)
        stack.add(javax.swing.Box.createRigidArea(Dimension(0, 8)))
        stack.add(emptySubtitle)
        stack.add(javax.swing.Box.createRigidArea(Dimension(0, 8)))
        stack.add(emptyButton)

        emptyStatePanel.add(stack, BorderLayout.CENTER)
    }

    private fun buildChatEmptyState() {
        chatEmptyTitle.font = UiTheme.Typography.aiHeader
        chatEmptyTitle.foreground = UiTheme.Colors.onSurface
        chatEmptySubtitle.font = UiTheme.Typography.aiText
        chatEmptySubtitle.foreground = UiTheme.Colors.onSurfaceVariant
        chatEmptyButton.font = UiTheme.Typography.aiButton
        chatEmptyButton.background = UiTheme.Colors.primary
        chatEmptyButton.foreground = UiTheme.Colors.onPrimary
        chatEmptyButton.isOpaque = true
        chatEmptyButton.isFocusPainted = false
        chatEmptyButton.border = EmptyBorder(6, 8, 6, 8)
        chatEmptyButton.margin = java.awt.Insets(6, 8, 6, 8)
        chatEmptyButton.addActionListener { createSession("Chat ${sessionsModel.size + 1}") }

        val stack = JPanel()
        stack.layout = BoxLayout(stack, BoxLayout.Y_AXIS)
        stack.isOpaque = false
        stack.add(chatEmptyTitle)
        stack.add(javax.swing.Box.createRigidArea(Dimension(0, 8)))
        stack.add(chatEmptySubtitle)
        stack.add(javax.swing.Box.createRigidArea(Dimension(0, 8)))
        stack.add(chatEmptyButton)

        chatEmptyPanel.add(stack, BorderLayout.CENTER)
    }

    private fun updateEmptyState() {
        val showEmpty = sessionsModel.isEmpty()
        val layout = sessionsCard.layout as CardLayout
        layout.show(sessionsCard, if (showEmpty) "empty" else "list")
        val chatLayout = chatCards.layout as CardLayout
        if (showEmpty) {
            chatLayout.show(chatCards, "empty")
        } else {
            val selected = sessionsList.selectedValue
            if (selected != null) {
                chatLayout.show(chatCards, selected.id)
            } else if (sessionsModel.size > 0) {
                sessionsList.selectedIndex = 0
                val fallback = sessionsList.selectedValue
                if (fallback != null) {
                    chatLayout.show(chatCards, fallback.id)
                }
            }
        }
        syncStateWithContext()
    }

    private fun updateChatAvailability() {
        sendBtn.isEnabled = mcpAvailable
        clearChatBtn.isEnabled = mcpAvailable
        toolsBtn.isEnabled = mcpAvailable
        inputArea.isEnabled = mcpAvailable
        newSessionBtn.isEnabled = mcpAvailable
    }

    fun startSessionFromContext(capture: ContextCapture, promptTemplate: String, actionName: String) {
        updatePrivacyPill()
        val uri = extractUriFromContext(capture)
        val title = if (uri.isNullOrBlank()) actionName else "$actionName: $uri"
        val session = createSession(title)
        val panel = sessionPanels[session.id] ?: return
        val prompt = promptTemplate.trim().ifBlank { "Analyze the provided context." }
        val state = sessionStates[session.id] ?: ToolSessionState()
        val actionCard = buildActionCard(capture, actionName, prompt, session.id, state)
        panel.addComponent(actionCard)
        panel.addMessage(
            role = "You",
            text = prompt
        )
        sendMessage(
            sessionId = session.id,
            userText = prompt,
            contextJson = capture.contextJson,
            allowToolCalls = state.toolsMode,
            actionName = actionName
        )
    }
    private fun inputPanel(): JPanel {
        val panel = JPanel(BorderLayout())
        panel.background = UiTheme.Colors.cardBackground
        panel.border = EmptyBorder(12, 12, 12, 12)

        inputArea.lineWrap = true
        inputArea.wrapStyleWord = true
        inputArea.font = UiTheme.Typography.aiMono
        inputArea.background = UiTheme.Colors.inputBackground
        inputArea.foreground = UiTheme.Colors.inputForeground
        inputArea.border = javax.swing.border.LineBorder(UiTheme.Colors.outline, 1, true)
        inputArea.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                if (e.keyCode == KeyEvent.VK_ENTER && (e.isControlDown || e.isMetaDown)) {
                    e.consume()
                    sendFromInput()
                }
            }
        })

        sendBtn.font = UiTheme.Typography.aiButton
        sendBtn.margin = java.awt.Insets(6, 8, 6, 8)
        sendBtn.isFocusPainted = false
        sendBtn.addActionListener { sendFromInput() }
        updatePrivacyPill()

        val inputScroll = JScrollPane(inputArea)
        inputScroll.border = EmptyBorder(0, 0, 0, 0)
        inputScroll.viewport.background = UiTheme.Colors.inputBackground
        inputScroll.horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
        inputScroll.verticalScrollBar.unitIncrement = 16
        panel.add(inputScroll, BorderLayout.CENTER)
        val actions = JPanel()
        actions.layout = FlowLayout(FlowLayout.RIGHT, 8, 0)
        actions.background = UiTheme.Colors.cardBackground
        actions.border = EmptyBorder(8, 0, 0, 0)
        actions.add(privacyPill)
        actions.add(toolsBtn)
        actions.add(clearChatBtn)
        actions.add(sendBtn)
        panel.add(actions, BorderLayout.SOUTH)
        return panel
    }

    private fun sendFromInput() {
        val text = inputArea.text.trim()
        if (text.isBlank()) return
        val session = sessionsList.selectedValue ?: createSession("Chat ${sessionsModel.size + 1}")
        val panel = sessionPanels[session.id] ?: return
        val state = sessionStates.getOrPut(session.id) { ToolSessionState() }
        val settings = getSettings()
        updatePrivacyPill()
        if (handleToolCommand(text, session.id, panel, state, settings)) {
            inputArea.text = ""
            return
        }
        panel.addMessage("You", text)
        inputArea.text = ""
        sendMessage(
            session.id,
            text,
            contextJson = null,
            allowToolCalls = state.toolsMode,
            actionName = "Chat"
        )
    }

    private fun sendMessage(
        sessionId: String,
        userText: String,
        contextJson: String?,
        allowToolCalls: Boolean,
        actionName: String? = null
    ) {
        val settings = getSettings()
        val session = sessionsById[sessionId]
        val backendId = session?.backendId ?: settings.preferredBackendId
        val effectiveSettings = if (backendId == settings.preferredBackendId) {
            settings
        } else {
            // Validate/readiness checks must run against the backend pinned to this session.
            settings.copy(preferredBackendId = backendId)
        }
        updatePrivacyPill()
        if (!ensureBackendReady(effectiveSettings)) {
            setChatRuntimeState("Blocked")
            return
        }
        val error = validateBackend(effectiveSettings)
        if (error != null) {
            setChatRuntimeState("Blocked")
            showError(error)
            return
        }

        applySettings(settings)
        setChatRuntimeState("Sending")

        val sessionPanel = sessionPanels[sessionId] ?: run {
            setChatRuntimeState("Error")
            return
        }
        val assistant = sessionPanel.addStreamingMessage("AI")
        val state = sessionStates.getOrPut(sessionId) { ToolSessionState() }
        val toolContext = if (state.toolsMode) buildToolContext(settings, sessionId) else null
        val toolPreamble = if (state.toolsMode) buildToolPreamble(toolContext, state, mutateState = true) else null
        val prompt = buildContextPayload(settings, userText, contextJson, actionName)
        val finalPrompt = listOfNotNull(
            toolPreamble?.takeIf { it.isNotBlank() },
            prompt.takeIf { it.isNotBlank() }
        ).joinToString("\n\n")

        val responseBuffer = StringBuilder()
        supervisor.sendChat(
            chatSessionId = sessionId,
            backendId = backendId,
            text = finalPrompt,
            contextJson = contextJson,
            privacyMode = settings.privacyMode,
            determinismMode = settings.determinismMode,
            onChunk = { chunk ->
                responseBuffer.append(chunk)
                SwingUtilities.invokeLater { assistant.append(chunk) }
            },
            onComplete = { err ->
                if (err != null) {
                    setChatRuntimeState("Error")
                    SwingUtilities.invokeLater { assistant.append("\n[Error] ${err.message}") }
                } else {
                    setChatRuntimeState("Ready")
                    SwingUtilities.invokeLater {
                        assistant.append("\n")
                        onResponseReady()
                    }
                    if (allowToolCalls && state.toolsMode && toolContext != null) {
                        maybeExecuteToolCall(
                            sessionId = sessionId,
                            userText = userText,
                            responseText = responseBuffer.toString(),
                            context = toolContext,
                            settings = settings
                        )
                    }
                }
            }
        )
    }

    private fun createSession(title: String): ChatSession {
        val id = "chat-" + UUID.randomUUID().toString()
        val backendId = getSettings().preferredBackendId
        val session = ChatSession(id, title, System.currentTimeMillis(), backendId)
        sessionsModel.addElement(session)
        sessionsById[id] = session
        updateEmptyState()

        val panel = SessionPanel()
        sessionPanels[id] = panel
        sessionStates[id] = ToolSessionState()
        chatCards.add(panel.root, id)
        sessionsList.selectedIndex = sessionsModel.size - 1
        showSession(id)
        return session
    }

    private fun showSessionContextMenu(comp: java.awt.Component, x: Int, y: Int) {
        val selected = sessionsList.selectedValue ?: return
        val menu = javax.swing.JPopupMenu()
        
        val renameItem = javax.swing.JMenuItem("Rename")
        renameItem.addActionListener { renameSession(selected) }
        
        val deleteItem = javax.swing.JMenuItem("Delete")
        deleteItem.addActionListener { deleteSession(selected) }
        
        menu.add(renameItem)
        menu.add(deleteItem)
        menu.show(comp, x, y)
    }

    private fun renameSession(session: ChatSession) {
        val newName = javax.swing.JOptionPane.showInputDialog(
            root, 
            "Enter new session name:", 
            session.title
        )
        if (!newName.isNullOrBlank()) {
            val index = sessionsModel.indexOf(session)
            if (index != -1) {
                // To update the list view we need to replace the element or trigger update
                // Since ChatSession is immutable (data class), we create a copy
                val updated = session.copy(title = newName.trim())
                sessionsModel.set(index, updated)
                sessionsById[session.id] = updated
            }
        }
    }

    private fun deleteSession(session: ChatSession) {
        val confirm = javax.swing.JOptionPane.showConfirmDialog(
            root,
            "Delete session '${session.title}'?",
            "Delete Session",
            javax.swing.JOptionPane.YES_NO_OPTION
        )
        if (confirm == javax.swing.JOptionPane.YES_OPTION) {
            supervisor.removeChatSession(session.id)
            val removedPanel = sessionPanels.remove(session.id)
            if (removedPanel != null) {
                chatCards.remove(removedPanel.root)
            }
            sessionStates.remove(session.id)
            sessionsById.remove(session.id)
            sessionsModel.removeElement(session)
            updateEmptyState()
            if (!sessionsModel.isEmpty() && sessionsList.isSelectionEmpty) {
                sessionsList.selectedIndex = sessionsModel.size - 1
            }
            syncStateWithContext()
        }
    }

    private fun showToolsMenu() {
        val menu = javax.swing.JPopupMenu()
        val tools = McpToolCatalog.all()
        val settings = getSettings()
        val enabledTools = McpToolCatalog.mergeWithDefaults(settings.mcpSettings.toolToggles)
        val unsafeEnabled = settings.mcpSettings.unsafeEnabled

        tools.groupBy { it.category }.forEach { (category, categoryTools) ->
            val submenu = javax.swing.JMenu(category)
            categoryTools.sortedBy { it.title }.forEach { tool ->
                val isEnabled = enabledTools[tool.id] == true
                val isUnsafe = tool.unsafeOnly
                val canRun = isEnabled && (!isUnsafe || unsafeEnabled)
                
                val item = javax.swing.JMenuItem(tool.title)
                item.isEnabled = canRun
                item.toolTipText = tool.description
                item.addActionListener {
                    inputArea.text = "/tool ${tool.id} {}"
                    inputArea.requestFocusInWindow()
                }
                submenu.add(item)
            }
            if (submenu.itemCount > 0) {
                menu.add(submenu)
            }
        }
        menu.show(toolsBtn, 0, toolsBtn.height)
    }

    private fun clearCurrentChat() {
        val selected = sessionsList.selectedValue ?: return
        val confirm = javax.swing.JOptionPane.showConfirmDialog(
            root,
            "Are you sure you want to clear this chat history?",
            "Clear Chat",
            javax.swing.JOptionPane.YES_NO_OPTION
        )
        if (confirm != javax.swing.JOptionPane.YES_OPTION) return

        val panel = sessionPanels[selected.id] ?: return
        panel.clearMessages()
        supervisor.removeChatSession(selected.id)
        val state = sessionStates[selected.id]
        if (state != null) {
            state.toolCatalogSent = false
        }
        setChatRuntimeState("Ready")
    }
    private fun buildActionCard(
        capture: ContextCapture,
        actionName: String,
        promptText: String,
        sessionId: String,
        state: ToolSessionState
    ): ActionCard {
        val source = extractSourceFromPreview(capture.previewText)
        val target = extractHostFromContext(capture) ?: "Unknown"
        val summary = privacySummary(getSettings().privacyMode)
        val payload = buildContextPayload(getSettings(), promptText, capture.contextJson, actionName)
        val toolContext = if (state.toolsMode) buildToolContext(getSettings(), sessionId) else null
        val toolPreamble = if (state.toolsMode) buildToolPreamble(toolContext, state, mutateState = false) else null
        val finalPayload = if (!toolPreamble.isNullOrBlank()) {
            toolPreamble + "\n\n" + payload
        } else {
            payload
        }
        return ActionCard(
            actionName = actionName,
            source = "Source: $source",
            target = "Target: $target",
            privacySummary = summary,
            payloadPreview = finalPayload
        )
    }

    private fun buildActionCard(capture: ContextCapture, actionName: String): ActionCard {
        return buildActionCard(
            capture = capture,
            actionName = actionName,
            promptText = "Analyze the provided context.",
            sessionId = "preview",
            state = ToolSessionState()
        )
    }

    private fun buildContextPayload(
        settings: AgentSettings,
        userText: String,
        contextJson: String?,
        actionName: String?
    ): String {
        val agentBlock = AgentProfileLoader.buildInstructionBlock(actionName)
        val templateInstruction = PromptTemplateLibrary.resolveInstruction(
            selectedProfile = settings.promptTemplateProfile,
            customTemplate = settings.promptTemplateCustom
        )
        val templateBlock = templateInstruction.takeIf { it.isNotBlank() }?.let {
            "Response template:\n$it"
        }
        val base = if (contextJson.isNullOrBlank()) {
            userText
        } else {
            buildString {
                appendLine(userText)
                appendLine()
                appendLine("Context (JSON):")
                append(contextJson)
            }
        }
        return listOfNotNull(
            agentBlock?.takeIf { it.isNotBlank() },
            templateBlock,
            base.takeIf { it.isNotBlank() }
        ).joinToString("\n\n")
    }

    private fun extractSourceFromPreview(preview: String): String {
        val line = preview.lineSequence().firstOrNull { it.trim().startsWith("Kind:") }
        return line?.substringAfter("Kind:")?.trim().orEmpty().ifBlank { "Context" }
    }

    private fun extractHostFromContext(capture: ContextCapture): String? {
        return try {
            val root = Json.parseToJsonElement(capture.contextJson).jsonObject
            val items = root["items"]?.jsonArray ?: return null
            items.asSequence().mapNotNull { item ->
                val obj = item.jsonObject
                val affectedHost = obj["affectedHost"]?.jsonPrimitive?.contentOrNull
                if (!affectedHost.isNullOrBlank()) return@mapNotNull affectedHost
                val url = obj["url"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                runCatching { URI(url).host }.getOrNull() ?: url
            }.firstOrNull()
        } catch (_: Exception) {
            null
        }
    }
    
    /**
     * Extract a representative URI from context for session title
     * Returns: METHOD path (e.g., "GET /api/users/123")
     */
    private fun extractUriFromContext(capture: ContextCapture): String? {
        return try {
            val root = Json.parseToJsonElement(capture.contextJson).jsonObject
            val items = root["items"]?.jsonArray ?: return null
            items.asSequence().mapNotNull { item ->
                val obj = item.jsonObject
                val url = obj["url"]?.jsonPrimitive?.contentOrNull
                val method = obj["method"]?.jsonPrimitive?.contentOrNull ?: "GET"
                
                if (url != null) {
                    val uri = runCatching { URI(url) }.getOrNull()
                    if (uri != null) {
                        val path = uri.path?.takeIf { it.isNotBlank() } ?: "/"
                        val query = uri.query?.let { "?${it.take(30)}${if (it.length > 30) "..." else ""}" } ?: ""
                        // Truncate path if too long
                        val displayPath = if (path.length > 50) "...${path.takeLast(47)}" else path
                        "$method $displayPath$query"
                    } else {
                        "$method $url"
                    }
                } else {
                    null
                }
            }.firstOrNull()
        } catch (_: Exception) {
            null
        }
    }

    private fun privacySummary(mode: PrivacyMode): String {
        return when (mode) {
            PrivacyMode.STRICT -> "Privacy: STRICT (cookies stripped, tokens redacted, hosts anonymized)"
            PrivacyMode.BALANCED -> "Privacy: BALANCED (cookies stripped, tokens redacted)"
            PrivacyMode.OFF -> "Privacy: OFF (no redaction)"
        }
    }

    private fun updatePrivacyPill() {
        privacyPill.updateMode(getSettings().privacyMode)
    }

    private fun showSession(id: String) {
        val layout = chatCards.layout as java.awt.CardLayout
        layout.show(chatCards, id)
        syncStateWithContext()
        onStatusChanged()
    }

    private fun syncStateWithContext() {
        if (!mcpAvailable) {
            setChatRuntimeState("Blocked")
            return
        }
        if (sessionsModel.isEmpty()) {
            setChatRuntimeState("Idle")
            return
        }
        if (chatRuntimeState == "Idle" || chatRuntimeState == "Blocked") {
            setChatRuntimeState("Ready")
        }
    }

    private fun setChatRuntimeState(state: String) {
        if (chatRuntimeState == state) return
        chatRuntimeState = state
        onStatusChanged()
    }


    data class ChatSession(val id: String, val title: String, val createdAt: Long, val backendId: String) {
        override fun toString(): String = title
    }

    private data class ToolSessionState(
        var toolsMode: Boolean = true,
        var toolCatalogSent: Boolean = false
    )

    private class ChatSessionRenderer : javax.swing.DefaultListCellRenderer() {
        override fun getListCellRendererComponent(
            list: JList<*>,
            value: Any?,
            index: Int,
            isSelected: Boolean,
            cellHasFocus: Boolean
        ): java.awt.Component {
            val label = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus) as JLabel
            if (value !is ChatSession) {
                label.border = EmptyBorder(8, 10, 8, 10)
                return label
            }

            val panel = JPanel()
            panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
            panel.border = EmptyBorder(8, 10, 8, 10)
            panel.isOpaque = true
            panel.background = if (isSelected) list.selectionBackground else list.background

            val titleLabel = JLabel(value.title)
            titleLabel.font = UiTheme.Typography.aiHeader
            titleLabel.foreground = if (isSelected) list.selectionForeground else list.foreground
            titleLabel.isOpaque = false

            val backendLabel = JLabel(value.backendId)
            backendLabel.font = UiTheme.Typography.aiStatus
            backendLabel.foreground = if (isSelected) list.selectionForeground else UiTheme.Colors.onSurfaceVariant
            backendLabel.isOpaque = false

            panel.add(titleLabel)
            panel.add(backendLabel)
            return panel
        }
    }
    private inner class SessionPanel {
        val root: JComponent = JPanel(BorderLayout())
        private val messages = object : JPanel(), javax.swing.Scrollable {
            override fun getPreferredScrollableViewportSize(): Dimension = preferredSize

            override fun getScrollableUnitIncrement(
                visibleRect: java.awt.Rectangle,
                orientation: Int,
                direction: Int
            ): Int = 16

            override fun getScrollableBlockIncrement(
                visibleRect: java.awt.Rectangle,
                orientation: Int,
                direction: Int
            ): Int = (visibleRect.height * 0.85).toInt().coerceAtLeast(24)

            override fun getScrollableTracksViewportWidth(): Boolean = true

            override fun getScrollableTracksViewportHeight(): Boolean = false
        }
        private val scroll = JScrollPane(messages)

        init {
            root.background = UiTheme.Colors.surface
            messages.layout = javax.swing.BoxLayout(messages, javax.swing.BoxLayout.Y_AXIS)
            messages.background = UiTheme.Colors.surface
            scroll.border = EmptyBorder(12, 12, 12, 12)
            scroll.background = UiTheme.Colors.surface
            scroll.horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
            scroll.verticalScrollBar.unitIncrement = 16
            root.add(scroll, BorderLayout.CENTER)
        }

        fun addMessage(role: String, text: String) {
            val message = ChatMessagePanel(role, text, onSaveNote = ::saveAsBurpNote)
            normalizeMessageComponent(message.root)
            messages.add(message.root)
            messages.add(javax.swing.Box.createRigidArea(Dimension(0, 8)))
            refreshScroll()
        }

        fun addComponent(component: JComponent) {
            normalizeMessageComponent(component)
            messages.add(component)
            messages.add(javax.swing.Box.createRigidArea(Dimension(0, 8)))
            refreshScroll()
        }

        fun addStreamingMessage(role: String): StreamingMessage {
            val message = ChatMessagePanel(role, "", onSaveNote = ::saveAsBurpNote)
            normalizeMessageComponent(message.root)
            messages.add(message.root)
            messages.add(javax.swing.Box.createRigidArea(Dimension(0, 8)))
            refreshScroll()
            return StreamingMessage(message)
        }

        fun clearMessages() {
            messages.removeAll()
            messages.revalidate()
            messages.repaint()
        }

        private fun refreshScroll() {
            messages.revalidate()
            SwingUtilities.invokeLater {
                val scrollBar = scroll.verticalScrollBar
                scrollBar.value = scrollBar.maximum
            }
        }

        private fun normalizeMessageComponent(component: JComponent) {
            component.alignmentX = javax.swing.JComponent.LEFT_ALIGNMENT
            component.maximumSize = Dimension(Int.MAX_VALUE, Int.MAX_VALUE)
        }
    }

    private class StreamingMessage(private val message: ChatMessagePanel) {
        private var firstChunk = true
        fun append(text: String) {
            if (firstChunk) {
                message.hideSpinner()
                firstChunk = false
            }
            message.append(text)
        }
    }

    private class ChatMessagePanel(
        private val role: String,
        initialText: String,
        private val onSaveNote: ((String) -> String?)? = null
    ) {
        private val isUser = role == "You"
        private val showSpinner = !isUser && initialText.isEmpty()
        val root: JComponent = JPanel()
        private val editorPane = object : javax.swing.JEditorPane() {
            override fun getPreferredSize(): java.awt.Dimension {
                // Calculate preferred height based on current width
                val width = if (parent != null && parent.width > 0) parent.width else 400
                setSize(width, Short.MAX_VALUE.toInt())
                val prefSize = super.getPreferredSize()
                return java.awt.Dimension(width, prefSize.height.coerceAtLeast(20))
            }
            
            override fun getMaximumSize(): java.awt.Dimension {
                val pref = preferredSize
                return java.awt.Dimension(Int.MAX_VALUE, pref.height)
            }
        }
        private val rawText = StringBuilder(initialText)
        private val copyBtn = JButton("⧉ Copy")
        private val saveNoteBtn = JButton("✎ Save as Note")
        private val spinnerLabel = JLabel("Thinking...")
        private var spinnerTimer: javax.swing.Timer? = null
        private val spinnerFrames = listOf("⠋", "⠙", "⠹", "⠸", "⠼", "⠴", "⠦", "⠧", "⠇", "⠏")
        private var spinnerIndex = 0
        private val bubble: JPanel

        init {
            root.layout = BorderLayout()
            root.isOpaque = false
            root.border = EmptyBorder(6, 8, 6, 8)

            bubble = JPanel(BorderLayout())
            bubble.isOpaque = true
            bubble.background = UiTheme.Colors.surface
            bubble.border = javax.swing.BorderFactory.createCompoundBorder(
                javax.swing.BorderFactory.createLineBorder(UiTheme.Colors.outline, 1),
                EmptyBorder(10, 12, 10, 12)
            )

            val header = JPanel(BorderLayout())
            header.isOpaque = false
            header.border = EmptyBorder(0, 0, 8, 0)

            val label = JLabel(role)
            label.font = UiTheme.Typography.aiHeader
            label.foreground = UiTheme.Colors.primary
            header.add(label, BorderLayout.WEST)

            // Spinner setup
            spinnerLabel.font = UiTheme.Typography.aiText
            spinnerLabel.foreground = UiTheme.Colors.onSurfaceVariant
            spinnerLabel.isVisible = showSpinner

            editorPane.contentType = "text/html"
            editorPane.isEditable = false
            editorPane.background = UiTheme.Colors.inputBackground
            editorPane.border = EmptyBorder(6, 8, 6, 8)
            editorPane.isVisible = !showSpinner
            updateHtml()

            val contentPanel = JPanel(BorderLayout())
            contentPanel.isOpaque = false
            contentPanel.add(spinnerLabel, BorderLayout.NORTH)
            contentPanel.add(editorPane, BorderLayout.CENTER)

            copyBtn.font = UiTheme.Typography.aiButton
            copyBtn.isFocusPainted = false
            copyBtn.margin = java.awt.Insets(6, 8, 6, 8)
            copyBtn.border = javax.swing.BorderFactory.createLineBorder(UiTheme.Colors.outline)
            copyBtn.background = UiTheme.Colors.surface
            copyBtn.foreground = UiTheme.Colors.onSurface
            copyBtn.addActionListener {
                val clipboard = java.awt.Toolkit.getDefaultToolkit().systemClipboard
                clipboard.setContents(java.awt.datatransfer.StringSelection(rawText.toString()), null)
            }

            saveNoteBtn.font = UiTheme.Typography.aiButton
            saveNoteBtn.isFocusPainted = false
            saveNoteBtn.margin = java.awt.Insets(6, 8, 6, 8)
            saveNoteBtn.border = javax.swing.BorderFactory.createLineBorder(UiTheme.Colors.outline)
            saveNoteBtn.background = UiTheme.Colors.surface
            saveNoteBtn.foreground = UiTheme.Colors.onSurface
            saveNoteBtn.isVisible = !isUser && onSaveNote != null
            saveNoteBtn.addActionListener {
                val savedUrl = runCatching { onSaveNote?.invoke(rawText.toString()) }.getOrNull()
                val (message, type) = if (!savedUrl.isNullOrBlank()) {
                    "Saved note to latest proxy entry:\n$savedUrl" to javax.swing.JOptionPane.INFORMATION_MESSAGE
                } else {
                    "No suitable proxy history entry found to attach this note." to javax.swing.JOptionPane.WARNING_MESSAGE
                }
                javax.swing.JOptionPane.showMessageDialog(root, message, "BurpAI Note", type)
            }

            val actionRow = JPanel(FlowLayout(FlowLayout.RIGHT, 8, 0)).apply {
                isOpaque = false
                border = EmptyBorder(8, 0, 0, 0)
                add(copyBtn)
                if (!isUser && onSaveNote != null) add(saveNoteBtn)
            }

            bubble.add(header, BorderLayout.NORTH)
            bubble.add(contentPanel, BorderLayout.CENTER)
            bubble.add(actionRow, BorderLayout.SOUTH)

            // Full width layout - bubble takes all available space
            root.add(bubble, BorderLayout.CENTER)
            
            // Add resize listener to recalculate height when width changes
            root.addComponentListener(object : java.awt.event.ComponentAdapter() {
                override fun componentResized(e: java.awt.event.ComponentEvent?) {
                    SwingUtilities.invokeLater {
                        editorPane.revalidate()
                        bubble.revalidate()
                        root.revalidate()
                    }
                }
            })

            if (showSpinner) {
                spinnerTimer = javax.swing.Timer(100) {
                    spinnerIndex = (spinnerIndex + 1) % spinnerFrames.size
                    spinnerLabel.text = "${spinnerFrames[spinnerIndex]} Thinking..."
                }
                spinnerTimer?.start()
            }
        }

        fun hideSpinner() {
            spinnerTimer?.stop()
            spinnerTimer = null
            spinnerLabel.isVisible = false
            editorPane.isVisible = true
        }

        fun append(text: String) {
            rawText.append(text)
            updateHtml()
        }

        private fun updateHtml() {
            val isDark = UiTheme.isDarkTheme
            editorPane.text = MarkdownRenderer.toHtml(rawText.toString(), isDark = isDark)
            // Revalidate to adjust size
            SwingUtilities.invokeLater {
                editorPane.revalidate()
                bubble.revalidate()
                root.revalidate()
            }
        }
    }

    private fun handleToolCommand(
        text: String,
        sessionId: String,
        panel: SessionPanel,
        state: ToolSessionState,
        settings: AgentSettings
    ): Boolean {
        val trimmed = text.trim()
        if (trimmed == "/tools") {
            val context = buildToolContext(settings, sessionId)
            val list = McpToolExecutor.describeTools(context, includeSchemas = true)
            panel.addMessage("Tools", list)
            state.toolsMode = true
            state.toolCatalogSent = true
            return true
        }
        if (trimmed.startsWith("/tool ")) {
            val parts = trimmed.removePrefix("/tool ").trim()
            val split = parts.split(" ", limit = 2)
            val toolName = split.getOrNull(0).orEmpty()
            if (toolName.isBlank()) {
                panel.addMessage("System", "Usage: /tool <name> <json>")
                return true
            }
            val argsJson = split.getOrNull(1)
            val context = buildToolContext(settings, sessionId)
            val result = McpToolExecutor.executeTool(toolName, argsJson, context)
            panel.addMessage("Tool result: $toolName", result)
            state.toolsMode = true
            state.toolCatalogSent = state.toolCatalogSent || argsJson != null
            return true
        }
        return false
    }

    private fun maybeExecuteToolCall(
        sessionId: String,
        userText: String,
        responseText: String,
        context: McpToolContext,
        settings: AgentSettings
    ) {
        val call = extractToolCall(responseText) ?: return
        val panel = sessionPanels[sessionId] ?: return
        val result = McpToolExecutor.executeTool(call.tool, call.argsJson, context)
        panel.addMessage("Tool result: ${call.tool}", result)
        val followup = buildString {
            appendLine("Tool result for ${call.tool}:")
            appendLine(result)
            appendLine()
            appendLine("User request:")
            appendLine(userText)
            appendLine()
            appendLine("Provide the final response using the tool result.")
        }.trim()
        sendMessage(
            sessionId,
            followup,
            contextJson = null,
            allowToolCalls = false,
            actionName = "Tool Followup"
        )
    }

    private data class ToolCall(val tool: String, val argsJson: String?)

    private fun extractToolCall(text: String): ToolCall? {
        val toolBlock = extractToolBlockJson(text)
        if (toolBlock != null) {
            val call = parseToolJson(toolBlock)
            if (call != null) return call
        }
        val trimmed = text.trim()
        if (trimmed.startsWith("{") && trimmed.endsWith("}") && trimmed.contains("\"tool\"")) {
            return parseToolJson(trimmed)
        }
        return null
    }

    private fun extractToolBlockJson(text: String): String? {
        val regex = Regex("```tool\\s*([\\s\\S]*?)\\s*```", RegexOption.IGNORE_CASE)
        val match = regex.find(text) ?: return null
        val payload = match.groupValues.getOrNull(1)?.trim().orEmpty()
        if (!payload.startsWith("{") || !payload.endsWith("}")) return null
        return payload
    }

    private fun parseToolJson(jsonText: String): ToolCall? {
        return try {
            val element = Json.parseToJsonElement(jsonText)
            val obj = element.jsonObject
            val tool = obj["tool"]?.jsonPrimitive?.content ?: return null
            val args = obj["args"]?.toString()
            ToolCall(tool, args)
        } catch (_: Exception) {
            null
        }
    }

    private fun buildToolPreamble(
        context: McpToolContext?,
        state: ToolSessionState,
        mutateState: Boolean
    ): String? {
        if (context == null) return null
        val header = "Tool mode is enabled. If you need a tool, include a fenced code block " +
            "with language 'tool' that contains only the JSON call, then wait. " +
            "After the tool result, respond in clear natural language or markdown."
        if (state.toolCatalogSent) return header
        if (mutateState) {
            state.toolCatalogSent = true
        }
        val list = McpToolExecutor.describeTools(context, includeSchemas = false)
        return header + "\n\n" + list
    }

    private fun buildToolContext(settings: AgentSettings, sessionId: String): McpToolContext {
        val toggles = McpToolCatalog.mergeWithDefaults(settings.mcpSettings.toolToggles)
        return McpToolContext(
            api = api,
            privacyMode = settings.privacyMode,
            determinismMode = settings.determinismMode,
            hostSalt = sessionId,
            toolToggles = toggles,
            unsafeEnabled = settings.mcpSettings.unsafeEnabled,
            unsafeTools = McpToolCatalog.unsafeToolIds(),
            limiter = McpRequestLimiter(settings.mcpSettings.maxConcurrentRequests),
            edition = api.burpSuite().version().edition(),
            maxBodyBytes = settings.mcpSettings.maxBodyBytes
        )
    }
}
