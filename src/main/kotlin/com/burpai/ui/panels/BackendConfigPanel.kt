package com.burpai.ui.panels

import com.burpai.ui.UiTheme
import com.burpai.ui.components.CardPanel
import com.burpai.util.HeaderParser
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.util.concurrent.TimeUnit
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextField
import javax.swing.JTextArea
import javax.swing.JScrollPane
import javax.swing.JPasswordField
import javax.swing.JButton
import javax.swing.BoxLayout
import javax.swing.JComboBox
import javax.swing.DefaultComboBoxModel
import javax.swing.DefaultListModel
import javax.swing.JList
import javax.swing.JOptionPane
import javax.swing.SwingUtilities
import javax.swing.border.EmptyBorder
import javax.swing.border.LineBorder
import com.burpai.ui.components.ToggleSwitch
import com.burpai.config.AgentSettingsRepository
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

data class BackendConfigState(
    val codexCmd: String = "",
    val geminiCmd: String = "",
    val opencodeCmd: String = "",
    val claudeCmd: String = "",
    val ollamaCliCmd: String = "",
    val ollamaModel: String = "",
    val ollamaUrl: String = "",
    val ollamaServeCmd: String = "",
    val ollamaAutoStart: Boolean = false,
    val ollamaApiKey: String = "",
    val ollamaHeaders: String = "",
    val ollamaTimeoutSeconds: String = "",
    val lmStudioUrl: String = "",
    val lmStudioModel: String = "",
    val lmStudioTimeoutSeconds: String = "",
    val lmStudioServerCmd: String = "",
    val lmStudioAutoStart: Boolean = true,
    val lmStudioApiKey: String = "",
    val lmStudioHeaders: String = "",
    val openAiCompatUrl: String = "",
    val openAiCompatUseOpenRouter: Boolean = true,
    val openAiCompatModels: List<String> = emptyList(),
    val openAiCompatModel: String = "",
    val openAiCompatApiKey: String = "",
    val openAiCompatHeaders: String = "",
    val openAiCompatTimeoutSeconds: String = ""
)

class BackendConfigPanel(
    initialState: BackendConfigState = BackendConfigState()
) : JPanel(BorderLayout()) {
    var onOpenCli: ((backendId: String, command: String) -> Unit)? = null
    private val cardLayout = CardLayout()
    private val cards = JPanel(cardLayout)

    private val codexCmd = JTextField(initialState.codexCmd)
    private val geminiCmd = JTextField(initialState.geminiCmd)
    private val opencodeCmd = JTextField(initialState.opencodeCmd)
    private val claudeCmd = JTextField(initialState.claudeCmd)
    private val ollamaCliCmd = JTextField(initialState.ollamaCliCmd)
    private val ollamaModel = JTextField(initialState.ollamaModel)
    private val ollamaUrl = JTextField(initialState.ollamaUrl)
    private val ollamaServeCmd = JTextField(initialState.ollamaServeCmd)
    private val ollamaAutoStart = ToggleSwitch(initialState.ollamaAutoStart)
    private val ollamaApiKey = JPasswordField(initialState.ollamaApiKey)
    private val ollamaHeaders = JTextArea(initialState.ollamaHeaders, 3, 20)
    private val ollamaTimeout = JTextField(initialState.ollamaTimeoutSeconds)
    private val lmStudioUrl = JTextField(initialState.lmStudioUrl)
    private val lmStudioModel = JTextField(initialState.lmStudioModel)
    private val lmStudioTimeout = JTextField(initialState.lmStudioTimeoutSeconds)
    private val lmStudioServeCmd = JTextField(initialState.lmStudioServerCmd)
    private val lmStudioAutoStart = ToggleSwitch(initialState.lmStudioAutoStart)
    private val lmStudioApiKey = JPasswordField(initialState.lmStudioApiKey)
    private val lmStudioHeaders = JTextArea(initialState.lmStudioHeaders, 3, 20)
    private val openAiCompatUseOpenRouter = ToggleSwitch(initialState.openAiCompatUseOpenRouter)
    private val openAiCompatUrl = JTextField(initialState.openAiCompatUrl)
    private val openAiCompatModelOptions = DefaultComboBoxModel<String>()
    private val openAiCompatModel = JComboBox<String>(openAiCompatModelOptions)
    private val openAiCompatModelListModel = DefaultListModel<String>()
    private val openAiCompatModelList = JList(openAiCompatModelListModel)
    private val openAiCompatApiKey = JPasswordField(initialState.openAiCompatApiKey)
    private val openAiCompatHeaders = JTextArea(initialState.openAiCompatHeaders, 3, 20)
    private val openAiCompatTimeout = JTextField(initialState.openAiCompatTimeoutSeconds)
    private var openAiCompatCustomUrl = initialState.openAiCompatUrl.ifBlank { DEFAULT_OPENROUTER_URL }
    private val openAiCompatAddModel = JButton("Add")
    private val openAiCompatEditModel = JButton("Edit")
    private val openAiCompatRemoveModel = JButton("Remove")
    private val openAiCompatTestButton = JButton("Test model API")
    private val openAiCompatTestStatus = JLabel("Not tested")
    private val openAiCompatModelListScroll = JScrollPane(openAiCompatModelList)

    init {
        background = UiTheme.Colors.surface
        cards.background = UiTheme.Colors.surface

        applyFieldStyle(codexCmd)
        applyFieldStyle(geminiCmd)
        applyFieldStyle(opencodeCmd)
        applyFieldStyle(claudeCmd)
        applyFieldStyle(ollamaCliCmd)
        applyFieldStyle(ollamaModel)
        applyFieldStyle(ollamaUrl)
        applyFieldStyle(ollamaServeCmd)
        applyFieldStyle(ollamaApiKey)
        applyAreaStyle(ollamaHeaders)
        applyFieldStyle(ollamaTimeout)
        applyFieldStyle(lmStudioUrl)
        applyFieldStyle(lmStudioModel)
        applyFieldStyle(lmStudioTimeout)
        applyFieldStyle(lmStudioServeCmd)
        applyFieldStyle(lmStudioApiKey)
        applyAreaStyle(lmStudioHeaders)
        applyFieldStyle(openAiCompatUrl)
        applyComboStyle(openAiCompatModel)
        applyFieldStyle(openAiCompatApiKey)
        applyAreaStyle(openAiCompatHeaders)
        applyFieldStyle(openAiCompatTimeout)
        setStableColumns(ollamaApiKey, 34)
        setStableColumns(lmStudioApiKey, 34)
        setStableColumns(openAiCompatUrl, 40)
        setStableColumns(openAiCompatApiKey, 40)
        openAiCompatModel.preferredSize = java.awt.Dimension(260, openAiCompatModel.preferredSize.height)
        openAiCompatModel.minimumSize = java.awt.Dimension(140, openAiCompatModel.preferredSize.height)
        applyListStyle(openAiCompatModelList)
        openAiCompatModelList.visibleRowCount = 6
        openAiCompatModelList.selectionMode = javax.swing.ListSelectionModel.SINGLE_SELECTION
        openAiCompatModelListScroll.border = LineBorder(UiTheme.Colors.outline, 1, true)
        openAiCompatModelListScroll.preferredSize = java.awt.Dimension(1, 96)
        configureVerticalScroll(openAiCompatModelListScroll)

        codexCmd.toolTipText = "Command used to launch Codex CLI."
        geminiCmd.toolTipText = "Command used to launch Gemini CLI."
        opencodeCmd.toolTipText = "Command used to launch OpenCode CLI with the model (e.g., opencode --model anthropic/claude-sonnet-4-5)."
        claudeCmd.toolTipText = "Command used to launch Claude Code CLI (e.g., claude)."
        ollamaCliCmd.toolTipText = "Command used to launch Ollama CLI with a model."
        ollamaModel.toolTipText = "Model name for Ollama HTTP backend. If empty, the CLI command is parsed."
        ollamaUrl.toolTipText = "Base URL for Ollama HTTP backend and health checks."
        ollamaServeCmd.toolTipText = "Command used to start the Ollama server."
        ollamaAutoStart.toolTipText = "Automatically start the Ollama server when needed."
        ollamaApiKey.toolTipText = "API key for Ollama-compatible servers (Authorization: Bearer ...)."
        ollamaHeaders.toolTipText = "Extra headers (one per line: Header: value)."
        ollamaTimeout.toolTipText = "Request timeout in seconds (30-3600)."
        lmStudioUrl.toolTipText = "Base URL for LM Studio OpenAI-compatible endpoint."
        lmStudioModel.toolTipText = "Model name sent to LM Studio."
        lmStudioTimeout.toolTipText = "Request timeout in seconds."
        lmStudioServeCmd.toolTipText = "Command used to start the LM Studio server."
        lmStudioAutoStart.toolTipText = "Automatically start the LM Studio server when needed."
        lmStudioApiKey.toolTipText = "API key for LM Studio-compatible servers (Authorization: Bearer ...)."
        lmStudioHeaders.toolTipText = "Extra headers (one per line: Header: value)."
        openAiCompatUrl.toolTipText = "Base URL for OpenAI-compatible HTTP endpoint (include /v1 or /v4 if required)."
        openAiCompatModel.toolTipText = "Model name sent to the provider."
        openAiCompatApiKey.toolTipText = "API key (Authorization: Bearer ...)."
        openAiCompatHeaders.toolTipText = "Extra headers (one per line: Header: value)."
        openAiCompatTimeout.toolTipText = "Request timeout in seconds."
        openAiCompatUseOpenRouter.toolTipText = "Use OpenRouter base URL and lock the endpoint."
        openAiCompatTestButton.toolTipText = "Send a small test request to verify the model works."

        setOpenAiCompatModels(initialState.openAiCompatModels, initialState.openAiCompatModel)
        openAiCompatUseOpenRouter.addActionListener { syncOpenAiCompatUrlState() }
        syncOpenAiCompatUrlState()

        openAiCompatAddModel.addActionListener { addModel() }
        openAiCompatEditModel.addActionListener { editModel() }
        openAiCompatRemoveModel.addActionListener { removeModel() }
        openAiCompatTestButton.addActionListener { runOpenAiCompatTest() }
        openAiCompatModelList.addListSelectionListener {
            if (!it.valueIsAdjusting) {
                val selected = openAiCompatModelList.selectedValue ?: return@addListSelectionListener
                openAiCompatModel.selectedItem = selected
            }
        }

        openAiCompatTestStatus.font = UiTheme.Typography.body
        openAiCompatTestStatus.foreground = UiTheme.Colors.onSurfaceVariant

        cards.add(buildSingleFieldPanelWithCli("Codex CLI command", codexCmd, "codex-cli") { codexCmd.text.trim() }, "codex-cli")
        cards.add(buildSingleFieldPanelWithCli("Gemini CLI command", geminiCmd, "gemini-cli") { geminiCmd.text.trim() }, "gemini-cli")
        cards.add(buildOpenCodePanel(), "opencode-cli")
        cards.add(buildSingleFieldPanelWithCli("Claude Code command", claudeCmd, "claude-cli") { claudeCmd.text.trim() }, "claude-cli")
        cards.add(buildOllamaPanel(), "ollama")
        cards.add(buildLmStudioPanel(), "lmstudio")
        cards.add(buildOpenAiCompatPanel(), "openai-compatible")

        add(cards, BorderLayout.CENTER)
    }

    fun setBackend(id: String) {
        cardLayout.show(cards, id)
    }

    fun currentBackendSettings(): BackendConfigState {
        val useOpenRouter = openAiCompatUseOpenRouter.isSelected
        val model = openAiCompatModel.selectedItem as? String ?: ""
        val url = if (useOpenRouter) DEFAULT_OPENROUTER_URL else openAiCompatUrl.text.trim()
        if (!useOpenRouter) {
            openAiCompatCustomUrl = url
        }
        val models = collectModelOptions(model)
        return BackendConfigState(
            codexCmd = codexCmd.text.trim(),
            geminiCmd = geminiCmd.text.trim(),
            opencodeCmd = opencodeCmd.text.trim(),
            claudeCmd = claudeCmd.text.trim(),
            ollamaCliCmd = ollamaCliCmd.text.trim(),
            ollamaModel = ollamaModel.text.trim(),
            ollamaUrl = ollamaUrl.text.trim(),
            ollamaServeCmd = ollamaServeCmd.text.trim(),
            ollamaAutoStart = ollamaAutoStart.isSelected,
            ollamaApiKey = String(ollamaApiKey.password).trim(),
            ollamaHeaders = ollamaHeaders.text.trim(),
            ollamaTimeoutSeconds = ollamaTimeout.text.trim(),
            lmStudioUrl = lmStudioUrl.text.trim(),
            lmStudioModel = lmStudioModel.text.trim(),
            lmStudioTimeoutSeconds = lmStudioTimeout.text.trim(),
            lmStudioServerCmd = lmStudioServeCmd.text.trim(),
            lmStudioAutoStart = lmStudioAutoStart.isSelected,
            lmStudioApiKey = String(lmStudioApiKey.password).trim(),
            lmStudioHeaders = lmStudioHeaders.text.trim(),
            openAiCompatUrl = url,
            openAiCompatUseOpenRouter = useOpenRouter,
            openAiCompatModels = models,
            openAiCompatModel = model.trim(),
            openAiCompatApiKey = String(openAiCompatApiKey.password).trim(),
            openAiCompatHeaders = openAiCompatHeaders.text.trim(),
            openAiCompatTimeoutSeconds = openAiCompatTimeout.text.trim()
        )
    }

    fun applyState(state: BackendConfigState) {
        codexCmd.text = state.codexCmd
        geminiCmd.text = state.geminiCmd
        opencodeCmd.text = state.opencodeCmd
        claudeCmd.text = state.claudeCmd
        ollamaCliCmd.text = state.ollamaCliCmd
        ollamaModel.text = state.ollamaModel
        ollamaUrl.text = state.ollamaUrl
        ollamaServeCmd.text = state.ollamaServeCmd
        ollamaAutoStart.isSelected = state.ollamaAutoStart
        ollamaApiKey.text = state.ollamaApiKey
        ollamaHeaders.text = state.ollamaHeaders
        ollamaTimeout.text = state.ollamaTimeoutSeconds
        lmStudioUrl.text = state.lmStudioUrl
        lmStudioModel.text = state.lmStudioModel
        lmStudioTimeout.text = state.lmStudioTimeoutSeconds
        lmStudioServeCmd.text = state.lmStudioServerCmd
        lmStudioAutoStart.isSelected = state.lmStudioAutoStart
        lmStudioApiKey.text = state.lmStudioApiKey
        lmStudioHeaders.text = state.lmStudioHeaders
        openAiCompatCustomUrl = state.openAiCompatUrl.ifBlank { DEFAULT_OPENROUTER_URL }
        openAiCompatUseOpenRouter.isSelected = state.openAiCompatUseOpenRouter
        setOpenAiCompatModels(state.openAiCompatModels, state.openAiCompatModel)
        syncOpenAiCompatUrlState()
        openAiCompatApiKey.text = state.openAiCompatApiKey
        openAiCompatHeaders.text = state.openAiCompatHeaders
        openAiCompatTimeout.text = state.openAiCompatTimeoutSeconds
    }

    private fun buildSingleFieldPanel(labelText: String, field: JComponent): JPanel {
        val panel = JPanel(GridBagLayout())
        panel.background = UiTheme.Colors.surface
        panel.border = EmptyBorder(4, 8, 0, 8)
        addRow(panel, 0, labelText, field)
        addVerticalFiller(panel, 1)
        return panel
    }

    private fun buildSingleFieldPanelWithCli(
        labelText: String,
        field: JComponent,
        backendId: String,
        commandProvider: () -> String
    ): JPanel {
        val panel = JPanel(GridBagLayout())
        panel.background = UiTheme.Colors.surface
        panel.border = EmptyBorder(4, 8, 0, 8)
        addRow(panel, 0, labelText, field)
        addButtonRow(panel, 1, buildOpenCliButton(backendId, commandProvider))
        addVerticalFiller(panel, 2)
        return panel
    }

    private fun buildOllamaPanel(): JPanel {
        val panel = JPanel(GridBagLayout())
        panel.background = UiTheme.Colors.surface
        panel.border = EmptyBorder(8, 8, 8, 8)
        var row = 0
        addRow(panel, row++, "Ollama CLI command", ollamaCliCmd)
        addButtonRow(panel, row++, buildOpenCliButton("ollama", { ollamaCliCmd.text.trim() }))
        addRow(panel, row++, "Ollama model", ollamaModel)
        addRow(panel, row++, "Ollama base URL", ollamaUrl)
        addRow(panel, row++, "Ollama API key (Bearer)", ollamaApiKey)
        addRow(panel, row++, "Ollama extra headers", verticalScroll(ollamaHeaders))
        addRow(panel, row++, "Ollama timeout (seconds)", ollamaTimeout)
        addRow(panel, row++, "Ollama serve command", ollamaServeCmd)
        addToggleRow(panel, row, "Auto-start Ollama server", ollamaAutoStart)
        return panel
    }

    private fun buildOpenCodePanel(): JPanel {
        val panel = JPanel(GridBagLayout())
        panel.background = UiTheme.Colors.surface
        panel.border = EmptyBorder(8, 8, 8, 8)
        addRow(panel, 0, "OpenCode CLI command", opencodeCmd)
        addButtonRow(panel, 1, buildOpenCliButton("opencode-cli", { opencodeCmd.text.trim() }))
        return panel
    }

    private fun buildLmStudioPanel(): JPanel {
        val panel = JPanel(GridBagLayout())
        panel.background = UiTheme.Colors.surface
        panel.border = EmptyBorder(8, 8, 8, 8)
        addRow(panel, 0, "LM Studio base URL", lmStudioUrl)
        addRow(panel, 1, "LM Studio model", lmStudioModel)
        addRow(panel, 2, "LM Studio timeout (seconds)", lmStudioTimeout)
        addRow(panel, 3, "LM Studio serve command", lmStudioServeCmd)
        addRow(panel, 4, "LM Studio API key (Bearer)", lmStudioApiKey)
        addRow(panel, 5, "LM Studio extra headers", verticalScroll(lmStudioHeaders))
        addToggleRow(panel, 6, "Auto-start LM Studio server", lmStudioAutoStart)
        return panel
    }

    private fun buildOpenAiCompatPanel(): JPanel {
        val panel = JPanel(BorderLayout())
        panel.background = UiTheme.Colors.surface
        panel.border = EmptyBorder(8, 2, 8, 2)

        val toggleRow = JPanel(FlowLayout(FlowLayout.LEFT, 8, 0))
        toggleRow.isOpaque = false
        toggleRow.alignmentX = LEFT_ALIGNMENT
        val toggleLabel = JLabel("Use OpenRouter (recommended)")
        toggleLabel.font = UiTheme.Typography.body
        toggleLabel.foreground = UiTheme.Colors.onSurface
        toggleRow.add(toggleLabel)
        toggleRow.add(openAiCompatUseOpenRouter)

        val connectionCard = CardPanel(BorderLayout(), 14)
        connectionCard.background = UiTheme.Colors.cardBackground
        val connectionGrid = JPanel(GridBagLayout())
        connectionGrid.isOpaque = false
        var row = 0
        addRow(connectionGrid, row++, "Base URL", openAiCompatUrl)
        addRow(connectionGrid, row++, "API key (Bearer)", openAiCompatApiKey)
        addRow(connectionGrid, row++, "Extra headers", verticalScroll(openAiCompatHeaders))
        addRow(connectionGrid, row++, "Timeout (seconds)", openAiCompatTimeout)
        addVerticalFiller(connectionGrid, row)
        connectionCard.add(connectionGrid, BorderLayout.CENTER)

        val modelCard = CardPanel(BorderLayout(), 14)
        modelCard.background = UiTheme.Colors.cardBackground
        val modelGrid = JPanel(GridBagLayout())
        modelGrid.isOpaque = false
        row = 0
        addRow(modelGrid, row++, "Model", buildModelRow())
        addRow(modelGrid, row++, "Saved models", openAiCompatModelListScroll)
        addRow(modelGrid, row++, "Test model API", buildTestPanel())
        addVerticalFiller(modelGrid, row)
        modelCard.add(modelGrid, BorderLayout.CENTER)

        val stacked = JPanel()
        stacked.layout = BoxLayout(stacked, BoxLayout.Y_AXIS)
        stacked.isOpaque = false
        stacked.alignmentX = LEFT_ALIGNMENT
        connectionCard.alignmentX = LEFT_ALIGNMENT
        modelCard.alignmentX = LEFT_ALIGNMENT
        connectionCard.maximumSize = Dimension(Int.MAX_VALUE, connectionCard.preferredSize.height)
        modelCard.maximumSize = Dimension(Int.MAX_VALUE, modelCard.preferredSize.height)
        stacked.add(connectionCard)
        stacked.add(javax.swing.Box.createRigidArea(Dimension(0, 10)))
        stacked.add(modelCard)

        val body = JPanel()
        body.layout = BoxLayout(body, BoxLayout.Y_AXIS)
        body.isOpaque = false
        body.add(toggleRow)
        body.add(javax.swing.Box.createRigidArea(Dimension(0, 10)))
        body.add(stacked)

        panel.add(body, BorderLayout.CENTER)
        return panel
    }

    private fun addRow(panel: JPanel, row: Int, labelText: String, field: JComponent) {
        val label = JLabel(labelText)
        label.font = UiTheme.Typography.body
        label.foreground = UiTheme.Colors.onSurface

        val labelConstraints = GridBagConstraints().apply {
            gridx = 0
            gridy = row
            anchor = GridBagConstraints.WEST
            insets = Insets(4, 0, 4, 10)
        }
        val fieldConstraints = GridBagConstraints().apply {
            gridx = 1
            gridy = row
            weightx = 1.0
            fill = GridBagConstraints.HORIZONTAL
            insets = Insets(4, 0, 4, 0)
        }
        panel.add(label, labelConstraints)
        panel.add(field, fieldConstraints)
    }

    private fun addButtonRow(panel: JPanel, row: Int, button: JButton) {
        val labelConstraints = GridBagConstraints().apply {
            gridx = 0
            gridy = row
            anchor = GridBagConstraints.WEST
            insets = Insets(4, 0, 4, 10)
        }
        val buttonConstraints = GridBagConstraints().apply {
            gridx = 1
            gridy = row
            anchor = GridBagConstraints.WEST
            insets = Insets(4, 0, 4, 0)
        }
        panel.add(JLabel(""), labelConstraints)
        panel.add(button, buttonConstraints)
    }

    private fun addToggleRow(panel: JPanel, row: Int, labelText: String, toggle: ToggleSwitch) {
        val label = JLabel(labelText)
        label.font = UiTheme.Typography.body
        label.foreground = UiTheme.Colors.onSurface
        val labelConstraints = GridBagConstraints().apply {
            gridx = 0
            gridy = row
            anchor = GridBagConstraints.WEST
            insets = Insets(6, 0, 4, 10)
        }
        val toggleConstraints = GridBagConstraints().apply {
            gridx = 1
            gridy = row
            anchor = GridBagConstraints.WEST
            insets = Insets(6, 0, 4, 0)
        }
        panel.add(label, labelConstraints)
        panel.add(toggle, toggleConstraints)
    }

    private fun addVerticalFiller(panel: JPanel, row: Int) {
        val filler = JPanel()
        filler.isOpaque = false
        val constraints = GridBagConstraints().apply {
            gridx = 0
            gridy = row
            gridwidth = 2
            weighty = 1.0
            fill = GridBagConstraints.VERTICAL
        }
        panel.add(filler, constraints)
    }

    private fun applyFieldStyle(field: JTextField) {
        field.font = UiTheme.Typography.mono
        field.border = LineBorder(UiTheme.Colors.outline, 1, true)
        field.background = UiTheme.Colors.inputBackground
        field.foreground = UiTheme.Colors.inputForeground
    }

    private fun setStableColumns(field: JTextField, columns: Int) {
        if (field.columns == 0) {
            field.columns = columns
        }
    }

    private fun verticalScroll(component: JComponent): JScrollPane {
        return configureVerticalScroll(JScrollPane(component))
    }

    private fun configureVerticalScroll(scroll: JScrollPane): JScrollPane {
        scroll.horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
        scroll.verticalScrollBar.unitIncrement = 16
        return scroll
    }

    private fun applyAreaStyle(area: JTextArea) {
        area.font = UiTheme.Typography.mono
        area.border = LineBorder(UiTheme.Colors.outline, 1, true)
        area.background = UiTheme.Colors.inputBackground
        area.foreground = UiTheme.Colors.inputForeground
        area.lineWrap = true
        area.wrapStyleWord = true
    }

    private fun applyComboStyle(combo: JComboBox<*>) {
        combo.font = UiTheme.Typography.mono
        combo.border = LineBorder(UiTheme.Colors.outline, 1, true)
        combo.background = UiTheme.Colors.comboBackground
        combo.foreground = UiTheme.Colors.comboForeground
    }

    private fun applyListStyle(list: JList<*>) {
        list.font = UiTheme.Typography.mono
        list.background = UiTheme.Colors.inputBackground
        list.foreground = UiTheme.Colors.inputForeground
        list.selectionBackground = UiTheme.Colors.outlineVariant
        list.selectionForeground = UiTheme.Colors.onSurface
    }

    private fun setOpenAiCompatModels(options: List<String>, selected: String) {
        openAiCompatModelOptions.removeAllElements()
        openAiCompatModelListModel.removeAllElements()
        val clean = options.map { it.trim() }.filter { it.isNotBlank() }
        val models = if (clean.isEmpty()) OPENROUTER_MODELS else clean
        models.forEach {
            openAiCompatModelOptions.addElement(it)
            openAiCompatModelListModel.addElement(it)
        }
        val resolved = selected.trim().ifBlank { models.firstOrNull().orEmpty() }
        if (resolved.isNotBlank() && (0 until openAiCompatModelOptions.size).none { openAiCompatModelOptions.getElementAt(it) == resolved }) {
            openAiCompatModelOptions.addElement(resolved)
            openAiCompatModelListModel.addElement(resolved)
        }
        openAiCompatModel.selectedItem = resolved
        if (resolved.isNotBlank()) {
            openAiCompatModelList.setSelectedValue(resolved, true)
        }
    }

    private fun collectModelOptions(selected: String): List<String> {
        val models = (0 until openAiCompatModelListModel.size)
            .map { openAiCompatModelListModel.getElementAt(it).trim() }
            .filter { it.isNotBlank() }
        val out = LinkedHashSet<String>()
        out.addAll(models)
        val chosen = selected.trim()
        if (chosen.isNotBlank()) out.add(chosen)
        if (out.isEmpty()) out.addAll(OPENROUTER_MODELS)
        return out.toList()
    }

    private fun buildModelRow(): JPanel {
        val panel = JPanel(FlowLayout(FlowLayout.LEFT, 8, 0))
        panel.isOpaque = false
        openAiCompatAddModel.font = UiTheme.Typography.body
        openAiCompatEditModel.font = UiTheme.Typography.body
        openAiCompatRemoveModel.font = UiTheme.Typography.body
        styleCompactButton(openAiCompatAddModel)
        styleCompactButton(openAiCompatEditModel)
        styleCompactButton(openAiCompatRemoveModel)
        panel.add(openAiCompatModel)
        panel.add(openAiCompatAddModel)
        panel.add(openAiCompatEditModel)
        panel.add(openAiCompatRemoveModel)
        return panel
    }

    private fun buildTestPanel(): JPanel {
        val panel = JPanel(FlowLayout(FlowLayout.LEFT, 8, 0))
        panel.isOpaque = false
        openAiCompatTestButton.font = UiTheme.Typography.body
        styleCompactButton(openAiCompatTestButton)
        panel.add(openAiCompatTestButton)
        panel.add(openAiCompatTestStatus)
        return panel
    }

    private fun styleCompactButton(button: JButton) {
        button.margin = Insets(2, 8, 2, 8)
        button.isFocusPainted = false
    }

    private fun addModel() {
        val input = JOptionPane.showInputDialog(this, "Enter model id:", "Add model", JOptionPane.PLAIN_MESSAGE)
        val model = input?.trim().orEmpty()
        if (model.isBlank()) return
        val exists = (0 until openAiCompatModelOptions.size).any { openAiCompatModelOptions.getElementAt(it) == model }
        if (!exists) {
            openAiCompatModelOptions.addElement(model)
            openAiCompatModelListModel.addElement(model)
        }
        openAiCompatModel.selectedItem = model
        openAiCompatModelList.setSelectedValue(model, true)
    }

    private fun editModel() {
        val selected = openAiCompatModel.selectedItem as? String ?: return
        val input = JOptionPane.showInputDialog(this, "Edit model id:", selected)
        val model = input?.trim().orEmpty()
        if (model.isBlank()) return
        val index = (0 until openAiCompatModelOptions.size).firstOrNull {
            openAiCompatModelOptions.getElementAt(it) == selected
        } ?: return
        openAiCompatModelOptions.removeElementAt(index)
        if (index < openAiCompatModelListModel.size) {
            openAiCompatModelListModel.removeElementAt(index)
        }
        val exists = (0 until openAiCompatModelOptions.size).any { openAiCompatModelOptions.getElementAt(it) == model }
        if (!exists) {
            openAiCompatModelOptions.insertElementAt(model, index.coerceAtMost(openAiCompatModelOptions.size))
            openAiCompatModelListModel.insertElementAt(model, index.coerceAtMost(openAiCompatModelListModel.size))
        }
        openAiCompatModel.selectedItem = model
        openAiCompatModelList.setSelectedValue(model, true)
    }

    private fun removeModel() {
        val selected = openAiCompatModel.selectedItem as? String ?: return
        if (openAiCompatModelOptions.size <= 1) {
            JOptionPane.showMessageDialog(this, "At least one model must remain.", "Remove model", JOptionPane.WARNING_MESSAGE)
            return
        }
        openAiCompatModelOptions.removeElement(selected)
        openAiCompatModelListModel.removeElement(selected)
        if (openAiCompatModelOptions.size > 0) {
            openAiCompatModel.selectedIndex = 0
            val next = openAiCompatModel.selectedItem as? String
            if (next != null) {
                openAiCompatModelList.setSelectedValue(next, true)
            }
        }
    }

    private fun runOpenAiCompatTest() {
        val state = currentBackendSettings()
        val baseUrl = if (state.openAiCompatUseOpenRouter) DEFAULT_OPENROUTER_URL else state.openAiCompatUrl
        if (baseUrl.isBlank()) {
            JOptionPane.showMessageDialog(this, "Base URL is empty.", "Model test", JOptionPane.WARNING_MESSAGE)
            return
        }
        if (state.openAiCompatModel.isBlank()) {
            JOptionPane.showMessageDialog(this, "Model is empty.", "Model test", JOptionPane.WARNING_MESSAGE)
            return
        }
        openAiCompatTestButton.isEnabled = false
        setTestStatus("Testing...", UiTheme.Colors.statusTerminal)
        Thread {
            val result = testOpenAiCompatRequest(state, baseUrl)
            SwingUtilities.invokeLater {
                openAiCompatTestButton.isEnabled = true
                if (result.first) {
                    setTestStatus("Success", UiTheme.Colors.statusRunning)
                } else {
                    setTestStatus("Failed", UiTheme.Colors.statusCrashed)
                    JOptionPane.showMessageDialog(this, result.second, "Model test failed", JOptionPane.ERROR_MESSAGE)
                }
            }
        }.start()
    }

    private fun testOpenAiCompatRequest(state: BackendConfigState, baseUrl: String): Pair<Boolean, String> {
        val timeoutSeconds = state.openAiCompatTimeoutSeconds.trim().toIntOrNull()?.coerceIn(5, 120) ?: 30
        val endpoint = buildOpenAiCompatEndpoint(baseUrl)
        val headers = HeaderParser.withBearerToken(
            state.openAiCompatApiKey,
            HeaderParser.parse(state.openAiCompatHeaders)
        )
        val bodyJson = buildTestPayload(state.openAiCompatModel)
        val client = OkHttpClient.Builder()
            .callTimeout(timeoutSeconds.toLong(), TimeUnit.SECONDS)
            .build()
        val body = bodyJson.toRequestBody("application/json".toMediaType())
        val requestBuilder = Request.Builder()
            .url(endpoint)
            .post(body)
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
        headers.forEach { (key, value) ->
            requestBuilder.header(key, value)
        }
        return try {
            client.newCall(requestBuilder.build()).execute().use { response ->
                if (response.isSuccessful) {
                    true to "HTTP ${response.code}"
                } else {
                    val snippet = response.body?.string()?.take(400).orEmpty()
                    false to "HTTP ${response.code}. ${snippet.ifBlank { "No response body." }}"
                }
            }
        } catch (e: Exception) {
            false to (e.message ?: "Request failed")
        }
    }

    private fun buildOpenAiCompatEndpoint(baseUrl: String): String {
        val trimmed = baseUrl.trim().trimEnd('/')
        val lower = trimmed.lowercase()
        return when {
            lower.endsWith("/v1/chat/completions") -> trimmed
            lower.endsWith("/chat/completions") -> trimmed
            lower.endsWith("/v1") -> "$trimmed/chat/completions"
            else -> "$trimmed/v1/chat/completions"
        }
    }

    private fun buildTestPayload(model: String): String {
        val safeModel = escapeJson(model)
        return """{"model":"$safeModel","messages":[{"role":"user","content":"ping"}],"max_tokens":1}"""
    }

    private fun escapeJson(value: String): String {
        val sb = StringBuilder()
        value.forEach { ch ->
            when (ch) {
                '\\' -> sb.append("\\\\")
                '"' -> sb.append("\\\"")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    private fun setTestStatus(text: String, color: Color) {
        openAiCompatTestStatus.text = text
        openAiCompatTestStatus.foreground = color
    }

    private fun syncOpenAiCompatUrlState() {
        val useOpenRouter = openAiCompatUseOpenRouter.isSelected
        if (useOpenRouter) {
            val current = openAiCompatUrl.text.trim()
            if (current.isNotBlank() && !current.equals(DEFAULT_OPENROUTER_URL, ignoreCase = true)) {
                openAiCompatCustomUrl = current
            }
            openAiCompatUrl.text = DEFAULT_OPENROUTER_URL
            openAiCompatUrl.isEditable = false
            openAiCompatUrl.isEnabled = false
        } else {
            openAiCompatUrl.isEnabled = true
            openAiCompatUrl.isEditable = true
            if (openAiCompatUrl.text.trim().equals(DEFAULT_OPENROUTER_URL, ignoreCase = true)) {
                openAiCompatUrl.text = openAiCompatCustomUrl
            }
        }
    }

    private fun buildOpenCliButton(backendId: String, commandProvider: () -> String): JButton {
        return JButton("Open CLI").apply {
            font = UiTheme.Typography.body
            toolTipText = "Open a terminal with the configured command and MCP tools access."
            addActionListener {
                onOpenCli?.invoke(backendId, commandProvider())
            }
        }
    }

    companion object {
        private val OPENROUTER_MODELS = AgentSettingsRepository.defaultOpenAiCompatModels()
        private val DEFAULT_OPENROUTER_URL: String = AgentSettingsRepository.defaultOpenAiCompatUrl()
    }

}

