package com.burpai.alerts

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

object Alerting {
    private val client = OkHttpClient()

    fun sendWebhook(webhookUrl: String, text: String) {
        val json = """{"text":${escapeJson(text)}}"""
        val req = Request.Builder()
            .url(webhookUrl)
            .post(json.toRequestBody("application/json".toMediaType()))
            .build()
        client.newCall(req).execute().close()
    }

    fun shutdownClient() {
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
    }

    private fun escapeJson(s: String): String =
        "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\""
}

