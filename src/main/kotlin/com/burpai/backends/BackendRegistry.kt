package com.burpai.backends

import burp.api.montoya.MontoyaApi
import com.burpai.backends.cli.CodexCliBackendFactory
import com.burpai.backends.cli.GeminiCliBackendFactory
import com.burpai.backends.cli.OpenCodeCliBackendFactory
import com.burpai.backends.cli.ClaudeCliBackendFactory
import com.burpai.backends.lmstudio.LmStudioBackendFactory
import com.burpai.backends.ollama.OllamaBackendFactory
import com.burpai.backends.openai.OpenAiCompatibleBackendFactory
import java.io.File
import java.net.URLClassLoader
import java.util.ServiceLoader
import java.util.concurrent.ConcurrentHashMap

class BackendRegistry(private val api: MontoyaApi) {
    private val backends = ConcurrentHashMap<String, AiBackend>()

    private val externalBackendDir = File(System.getProperty("user.home"), ".burp-ai-agent/backends").also { it.mkdirs() }

    init {
        reload()
    }

    fun reload() {
        backends.clear()

        // Built-ins (same extension JAR)
        val builtIns = ServiceLoader.load(AiBackendFactory::class.java).toList()
        if (builtIns.isEmpty()) {
            api.logging().logToOutput("No AiBackendFactory found via ServiceLoader; falling back to built-ins.")
            listOf(
                CodexCliBackendFactory(),
                GeminiCliBackendFactory(),
                OpenCodeCliBackendFactory(),
                ClaudeCliBackendFactory(),
                LmStudioBackendFactory(),
                OllamaBackendFactory(),
                OpenAiCompatibleBackendFactory()
            ).forEach { f ->
                val b = f.create()
                backends[b.id] = b
            }
        } else {
            builtIns.forEach { f ->
                val b = f.create()
                backends[b.id] = b
            }
        }

        // Optional drop-in backend JARs
        loadExternalBackendJars()

        api.logging().logToOutput("Backends available: ${listBackendIds().joinToString(", ")}")
    }

    fun get(id: String): AiBackend? = backends[id]

    fun listBackendIds(): List<String> = backends.values
        .sortedBy { it.displayName }
        .map { it.id }

    private fun loadExternalBackendJars() {
        val jars = externalBackendDir.listFiles { f -> f.isFile && f.extension.lowercase() == "jar" }?.toList().orEmpty()
        if (jars.isEmpty()) return

        try {
            val cl = URLClassLoader(jars.map { it.toURI().toURL() }.toTypedArray(), this::class.java.classLoader)
            ServiceLoader.load(AiBackendFactory::class.java, cl).forEach { f ->
                val b = f.create()
                backends[b.id] = b
            }
            api.logging().logToOutput("Loaded external backend JARs: ${jars.joinToString { it.name }}")
        } catch (e: Exception) {
            api.logging().logToError("Failed loading external backend JARs: ${e.message}")
        }
    }
}

