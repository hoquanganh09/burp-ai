package com.burpai.context

import burp.api.montoya.MontoyaApi
import burp.api.montoya.http.message.HttpRequestResponse
import burp.api.montoya.scanner.audit.issues.AuditIssue
import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.burpai.redact.Redaction
import com.burpai.redact.RedactionPolicy
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

class ContextCollector(private val api: MontoyaApi) {
    private val mapper = JsonMapper.builder()
        .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
        .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
        .build()
        .registerKotlinModule()

    fun fromRequestResponses(rr: List<HttpRequestResponse>, options: ContextOptions): ContextCapture {
        val policy = RedactionPolicy.fromMode(options.privacyMode)
        val items = rr.map { item ->
            val request = runCatching { item.request() }.getOrNull()
            val req = runCatching { request?.toString() }.getOrNull().orEmpty().ifBlank { "<no request>" }
            val resp = runCatching { item.response()?.toString() }.getOrNull()

            val redactedReq = Redaction.apply(req, policy, stableHostSalt = options.hostSalt)
            val redactedResp = resp?.let { Redaction.apply(it, policy, stableHostSalt = options.hostSalt) }
            val requestUrl = runCatching { request?.url() }.getOrNull().orEmpty().ifBlank { "unknown" }
            val requestMethod = runCatching { request?.method() }.getOrNull().orEmpty().ifBlank { "UNKNOWN" }

            HttpItem(
                tool = null,
                url = requestUrl,
                method = requestMethod,
                request = redactedReq,
                response = redactedResp
            )
        }.let { list ->
            if (options.deterministic) list.sortedBy { stableKey(it) } else list
        }

        val env = BurpContextEnvelope(
            capturedAtEpochMs = System.currentTimeMillis(),
            items = items
        )

        val json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(env)
        val preview = buildPreview(items.size, "HTTP selection", policy, options.deterministic)

        return ContextCapture(contextJson = json, previewText = preview)
    }

    fun fromAuditIssues(issues: List<AuditIssue>, options: ContextOptions): ContextCapture {
        val policy = RedactionPolicy.fromMode(options.privacyMode)
        val items = issues.map { i ->
            val host = i.httpService()?.host()
            AuditIssueItem(
                name = i.name(),
                severity = i.severity()?.name,
                confidence = i.confidence()?.name,
                detail = i.detail(),
                remediation = i.remediation(),
                affectedHost = host?.let {
                    if (policy.anonymizeHosts) Redaction.anonymizeHost(it, options.hostSalt) else it
                }
            )
        }.let { list ->
            if (options.deterministic) list.sortedBy { stableKey(it) } else list
        }

        val env = BurpContextEnvelope(
            capturedAtEpochMs = System.currentTimeMillis(),
            items = items
        )

        val json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(env)
        val preview = buildPreview(items.size, "Scanner findings", policy, options.deterministic)

        return ContextCapture(contextJson = json, previewText = preview)
    }

    private fun buildPreview(count: Int, kind: String, policy: RedactionPolicy, deterministic: Boolean): String {
        return """
            Kind: $kind
            Items: $count
            Redaction:
              - Cookie stripping: ${policy.stripCookies}
              - Token redaction: ${policy.redactTokens}
              - Host anonymization: ${policy.anonymizeHosts}
            Deterministic: $deterministic
        """.trimIndent()
    }

    private fun stableKey(item: HttpItem): String {
        val base = listOf(item.url, item.method, hashOf(item.request)).joinToString("|")
        return base
    }

    private fun stableKey(item: AuditIssueItem): String {
        val base = listOf(item.name, item.severity, item.affectedHost, hashOf(item.detail ?: "")).joinToString("|")
        return base
    }

    private fun hashOf(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(StandardCharsets.UTF_8))
        return digest.take(8).joinToString("") { "%02x".format(it) }
    }
}

