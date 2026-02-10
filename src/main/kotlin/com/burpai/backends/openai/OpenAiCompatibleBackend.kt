package com.burpai.backends.openai

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.burpai.backends.AgentConnection
import com.burpai.backends.AiBackend
import com.burpai.backends.BackendDiagnostics
import com.burpai.backends.BackendLaunchConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.EOFException
import java.net.Proxy
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class OpenAiCompatibleBackend : AiBackend {
    override val id: String = "openai-compatible"
    override val displayName: String = "Generic (OpenAI-compatible)"

    private val mapper = ObjectMapper().registerKotlinModule()

    override fun launch(config: BackendLaunchConfig): AgentConnection {
        val baseUrl = config.baseUrl?.trimEnd('/') ?: ""
        val model = config.model?.ifBlank { "" } ?: ""
        val timeoutSeconds = (config.requestTimeoutSeconds ?: 120L).coerceIn(30L, 3600L)
        val client = buildClient(timeoutSeconds)
        return OpenAiCompatibleConnection(
            client = client,
            mapper = mapper,
            baseUrl = baseUrl,
            model = model,
            headers = config.headers,
            determinismMode = config.determinismMode,
            sessionId = config.sessionId,
            debugLog = { BackendDiagnostics.log("[openai-compatible] $it") },
            errorLog = { BackendDiagnostics.logError("[openai-compatible] $it") }
        )
    }

    private fun buildClient(timeoutSeconds: Long): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(java.time.Duration.ofSeconds(10))
            .writeTimeout(java.time.Duration.ofSeconds(30))
            .readTimeout(java.time.Duration.ofSeconds(timeoutSeconds))
            .callTimeout(java.time.Duration.ofSeconds(timeoutSeconds))
            .proxy(Proxy.NO_PROXY)
            .build()
    }

    private class OpenAiCompatibleConnection(
        private val client: OkHttpClient,
        private val mapper: ObjectMapper,
        private val baseUrl: String,
        private val model: String,
        private val headers: Map<String, String>,
        private val determinismMode: Boolean,
        private val sessionId: String?,
        private val debugLog: (String) -> Unit,
        private val errorLog: (String) -> Unit
    ) : AgentConnection {
        private val alive = AtomicBoolean(true)
        private val exec = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "openai-compatible-connection").apply { isDaemon = true }
        }
        private val conversationHistory = mutableListOf<Map<String, String>>()
        private val maxHistoryMessages = 20

        override fun isAlive(): Boolean = alive.get()

        override fun send(text: String, onChunk: (String) -> Unit, onComplete: (Throwable?) -> Unit) {
            if (!isAlive()) {
                onComplete(IllegalStateException("Connection closed"))
                return
            }

            exec.submit {
                try {
                    appendMessage("user", text)
                    val maxAttempts = 6
                    var attempt = 0
                    var lastError: Exception? = null
                    while (attempt < maxAttempts) {
                        if (!isAlive()) {
                            onComplete(IllegalStateException("Connection closed"))
                            return@submit
                        }
                        try {
                            val content = sendStreamedRequest(onChunk)
                            if (content.isBlank()) {
                                throw IllegalStateException("OpenAI-compatible response content was empty")
                            }
                            debugLog("response <- ${content.take(200)}")
                            appendMessage("assistant", content)
                            onComplete(null)
                            return@submit
                        } catch (e: Exception) {
                            lastError = e
                            if (!isRetryableConnectionError(e) || attempt == maxAttempts - 1) {
                                throw e
                            }
                            val delayMs = retryDelayMs(attempt)
                            debugLog("retrying in ${delayMs}ms after: ${e.message}")
                            try {
                                Thread.sleep(delayMs)
                            } catch (_: InterruptedException) {
                                Thread.currentThread().interrupt()
                                throw e
                            }
                            attempt++
                        }
                    }
                    if (lastError != null) {
                        throw lastError
                    }
                } catch (e: Exception) {
                    errorLog("exception: ${e.message}")
                    onComplete(e)
                } finally {
                    if (!isAlive()) {
                        exec.shutdownNow()
                    }
                }
            }
        }

        override fun stop() {
            alive.set(false)
            exec.shutdownNow()
        }

        private fun sendStreamedRequest(onChunk: (String) -> Unit): String {
            val messages = synchronized(conversationHistory) { conversationHistory.toList() }
            val payload = mapOf(
                "model" to model,
                "messages" to messages,
                "stream" to true,
                "temperature" to if (determinismMode) 0.0 else 0.7
            )

            val json = mapper.writeValueAsString(payload)
            val endpointUrl = buildChatCompletionsUrl(baseUrl)
            val req = Request.Builder()
                .url(endpointUrl)
                .post(json.toRequestBody("application/json".toMediaType()))
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream")
                .apply {
                    headers.forEach { (name, value) ->
                        header(name, value)
                    }
                    if (!sessionId.isNullOrBlank()) {
                        header("X-Session-Id", sessionId)
                    }
                }
                .build()

            debugLog("request -> ${req.url} (stream=true)")
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    val bodyText = resp.body?.string().orEmpty()
                    errorLog("HTTP ${resp.code}: ${bodyText.take(500)}")
                    throw IllegalStateException("OpenAI-compatible HTTP ${resp.code}: $bodyText")
                }

                val body = resp.body ?: throw IllegalStateException("OpenAI-compatible response body was empty")
                return if (isSseResponse(resp)) {
                    readSseContent(body, onChunk)
                } else {
                    val raw = body.string()
                    if (raw.isBlank()) {
                        throw IllegalStateException("OpenAI-compatible response body was empty")
                    }
                    val content = extractContent(mapper.readTree(raw))
                    if (content.isNotBlank()) {
                        onChunk(content)
                    }
                    content
                }
            }
        }

        private fun readSseContent(body: ResponseBody, onChunk: (String) -> Unit): String {
            val aggregate = StringBuilder()
            val source = body.source()
            val eventBuffer = StringBuilder()
            var completed = false

            while (isAlive() && !source.exhausted() && !completed) {
                val line = source.readUtf8Line() ?: break
                if (line.isBlank()) {
                    completed = consumeSseEvent(eventBuffer.toString(), aggregate, onChunk)
                    eventBuffer.setLength(0)
                    continue
                }
                if (!line.startsWith("data:")) continue
                if (eventBuffer.isNotEmpty()) {
                    eventBuffer.append('\n')
                }
                eventBuffer.append(line.removePrefix("data:").trimStart())
            }

            if (!completed && eventBuffer.isNotEmpty()) {
                consumeSseEvent(eventBuffer.toString(), aggregate, onChunk)
            }
            return aggregate.toString()
        }

        private fun consumeSseEvent(
            payload: String,
            aggregate: StringBuilder,
            onChunk: (String) -> Unit
        ): Boolean {
            val trimmed = payload.trim()
            if (trimmed.isBlank()) return false
            if (trimmed == "[DONE]") return true

            return try {
                val chunk = extractContent(mapper.readTree(trimmed))
                if (chunk.isNotBlank()) {
                    aggregate.append(chunk)
                    onChunk(chunk)
                }
                false
            } catch (e: Exception) {
                debugLog("ignored SSE chunk parse error: ${e.message}")
                false
            }
        }

        private fun extractContent(node: com.fasterxml.jackson.databind.JsonNode): String {
            val choices = node.path("choices")
            if (!choices.isArray) return ""
            val out = StringBuilder()
            choices.forEach { choice ->
                val deltaContent = choice.path("delta").path("content").asText("")
                val messageContent = choice.path("message").path("content").asText("")
                val textContent = choice.path("text").asText("")
                val part = when {
                    deltaContent.isNotBlank() -> deltaContent
                    messageContent.isNotBlank() -> messageContent
                    textContent.isNotBlank() -> textContent
                    else -> ""
                }
                if (part.isNotBlank()) {
                    out.append(part)
                }
            }
            return out.toString()
        }

        private fun isSseResponse(resp: Response): Boolean {
            val contentType = resp.header("Content-Type").orEmpty().lowercase()
            return contentType.contains("text/event-stream")
        }

        private fun appendMessage(role: String, content: String) {
            synchronized(conversationHistory) {
                conversationHistory.add(mapOf("role" to role, "content" to content))
                while (conversationHistory.size > maxHistoryMessages) {
                    conversationHistory.removeAt(0)
                }
            }
        }

        private fun isRetryableConnectionError(e: Exception): Boolean {
            if (e is EOFException) return true
            if (e is java.net.ConnectException || e is java.net.SocketTimeoutException) return true
            if (e is java.net.SocketException) return true
            val msg = e.message?.lowercase().orEmpty()
            return msg.contains("failed to connect") ||
                msg.contains("connection refused") ||
                msg.contains("timeout") ||
                msg.contains("unexpected end of stream") ||
                msg.contains("stream was reset") ||
                msg.contains("end of input")
        }

        private fun retryDelayMs(attempt: Int): Long {
            return when (attempt) {
                0 -> 500
                1 -> 1000
                2 -> 1500
                3 -> 2000
                4 -> 3000
                else -> 4000
            }
        }

        private fun buildChatCompletionsUrl(baseUrl: String): String {
            val trimmed = baseUrl.trimEnd('/')
            val lower = trimmed.lowercase()
            if (lower.endsWith("/chat/completions")) return trimmed
            if (versionedEndpointRegex.matches(trimmed)) return trimmed
            if (versionedBaseRegex.matches(trimmed)) return "$trimmed/chat/completions"
            return "$trimmed/v1/chat/completions"
        }
    }

    private companion object {
        private val versionedBaseRegex = Regex(".*/v\\d+$", RegexOption.IGNORE_CASE)
        private val versionedEndpointRegex = Regex(".*/v\\d+/chat/completions$", RegexOption.IGNORE_CASE)
    }
}

