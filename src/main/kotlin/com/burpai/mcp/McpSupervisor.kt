package com.burpai.mcp

import burp.api.montoya.MontoyaApi
import com.burpai.config.McpSettings
import com.burpai.redact.PrivacyMode
import com.burpai.mcp.tools.ScannerTaskRegistry
import java.net.BindException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URI
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class McpSupervisor(
    private val api: MontoyaApi,
    private val serverManager: McpServerManager = KtorMcpServerManager(api),
    private val stdioBridge: McpStdioBridge = McpStdioBridge(api)
) {
    private val stateRef = AtomicReference<McpServerState>(McpServerState.Stopped)
    private val settingsRef = AtomicReference<McpSettings?>(null)
    private val privacyRef = AtomicReference(PrivacyMode.STRICT)
    private val determinismRef = AtomicReference(false)
    private val restartAttempts = AtomicInteger(0)
    private val externalDetected = AtomicReference(false)
    private val scheduler: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()

    fun applySettings(settings: McpSettings, privacyMode: PrivacyMode, determinismMode: Boolean) {
        settingsRef.set(settings)
        privacyRef.set(privacyMode)
        determinismRef.set(determinismMode)

        if (!settings.enabled) {
            stop()
            return
        }

        restartAttempts.set(0)
        startInternal(settings, privacyMode, determinismMode)

        if (settings.stdioEnabled) {
            stdioBridge.start(settings, privacyMode, determinismMode)
        } else {
            stdioBridge.stop()
        }
    }

    fun status(): McpServerState = stateRef.get()

    fun stop() {
        serverManager.stop { state ->
            stateRef.set(state)
        }
        if (externalDetected.getAndSet(false)) {
            requestRemoteShutdown()
        }
        stdioBridge.stop()
        ScannerTaskRegistry.clear()
    }

    fun shutdown() {
        serverManager.shutdown()
        stdioBridge.stop()
        scheduler.shutdown()
    }

    private fun startInternal(settings: McpSettings, privacyMode: PrivacyMode, determinismMode: Boolean) {
        serverManager.start(settings, privacyMode, determinismMode) { state ->
            stateRef.set(state)
            if (state is McpServerState.Failed) {
                handleFailure(state.exception)
            }
        }
    }

    private fun handleFailure(exception: Throwable) {
        val settings = settingsRef.get() ?: return
        if (!settings.enabled) return
        if (isBindException(exception)) {
            // Try to take over the existing server
            if (probeExistingServer(settings)) {
                // probeExistingServer attempted shutdown, now retry starting
                val attempt = restartAttempts.incrementAndGet()
                if (attempt <= 3) {
                    api.logging().logToOutput("Retrying MCP server start after shutdown request (attempt $attempt)...")
                    scheduler.schedule({
                        val current = settingsRef.get() ?: return@schedule
                        startInternal(current, privacyRef.get(), determinismRef.get())
                    }, 1, TimeUnit.SECONDS)
                    return
                }
            }
            api.logging().logToError("MCP server failed to bind on ${settings.host}:${settings.port}. Port may be in use by another application.")
            return
        }

        val attempt = restartAttempts.incrementAndGet()
        if (attempt > 4) {
            api.logging().logToError("MCP server failed repeatedly. Giving up after $attempt attempts: ${exception.message}")
            return
        }

        api.logging().logToError("MCP server failed. Restarting in 2s (attempt $attempt).")
        scheduler.schedule({
            val current = settingsRef.get() ?: return@schedule
            startInternal(current, privacyRef.get(), determinismRef.get())
        }, 2, TimeUnit.SECONDS)
    }

    private fun isBindException(exception: Throwable): Boolean {
        var current: Throwable? = exception
        while (current != null) {
            if (current is BindException) return true
            current = current.cause
        }
        return false
    }

    private fun probeExistingServer(settings: McpSettings): Boolean {
        return try {
            val scheme = if (settings.tlsEnabled) "https" else "http"
            val url = URI.create("$scheme://${settings.host}:${settings.port}/__mcp/health").toURL()
            val conn = openConnection(url, settings.tlsEnabled)
            conn.requestMethod = "GET"
            conn.connectTimeout = 800
            conn.readTimeout = 800
            conn.connect()
            val ok = conn.responseCode in 200..299 &&
                conn.getHeaderField("X-Burp-AI-Agent") == "mcp"
            conn.disconnect()
            
            if (ok) {
                // Server exists, try to shut it down and take over
                api.logging().logToOutput("Found existing MCP server, requesting shutdown to take over...")
                requestRemoteShutdownWithToken(settings)
                Thread.sleep(500) // Wait for shutdown
                false // Return false to trigger a new start
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }
    
    private fun requestRemoteShutdownWithToken(settings: McpSettings) {
        try {
            val scheme = if (settings.tlsEnabled) "https" else "http"
            val url = URI.create("$scheme://${settings.host}:${settings.port}/__mcp/shutdown").toURL()
            val conn = openConnection(url, settings.tlsEnabled)
            conn.requestMethod = "POST"
            conn.setRequestProperty("Authorization", "Bearer ${settings.token}")
            conn.connectTimeout = 500
            conn.readTimeout = 500
            conn.connect()
            conn.responseCode // trigger request
            conn.disconnect()
        } catch (_: Exception) {
            // Ignore - might be our own server or different token
        }
    }

    private fun requestRemoteShutdown() {
        val settings = settingsRef.get() ?: return
        try {
            val scheme = if (settings.tlsEnabled) "https" else "http"
            val url = URI.create("$scheme://${settings.host}:${settings.port}/__mcp/shutdown").toURL()
            val conn = openConnection(url, settings.tlsEnabled)
            conn.requestMethod = "POST"
            conn.connectTimeout = 800
            conn.readTimeout = 800
            conn.doOutput = true
            conn.setRequestProperty("Authorization", "Bearer ${settings.token}")
            conn.outputStream.use { }
            conn.disconnect()
        } catch (e: Exception) {
            api.logging().logToError("Failed to request MCP shutdown: ${e.message}")
        }
    }

    private fun openConnection(url: URL, tlsEnabled: Boolean): HttpURLConnection {
        val conn = url.openConnection() as HttpURLConnection
        if (tlsEnabled && conn is HttpsURLConnection) {
            val isLoopback = url.host.equals("localhost", ignoreCase = true) ||
                url.host.equals("127.0.0.1") ||
                url.host.equals("::1")

            if (isLoopback) {
                val trustAll = arrayOf<TrustManager>(object : X509TrustManager {
                    override fun getAcceptedIssuers() = emptyArray<java.security.cert.X509Certificate>()
                    override fun checkClientTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) = Unit
                    override fun checkServerTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) = Unit
                })
                val sslContext = SSLContext.getInstance("TLS")
                sslContext.init(null, trustAll, java.security.SecureRandom())
                conn.sslSocketFactory = sslContext.socketFactory
                conn.hostnameVerifier = HostnameVerifier { _, _ -> true }
            }
        }
        return conn
    }
}

