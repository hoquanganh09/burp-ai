package com.burpai.mcp.schema

import burp.api.montoya.proxy.ProxyHttpRequestResponse
import burp.api.montoya.proxy.ProxyWebSocketMessage
import burp.api.montoya.scanner.audit.issues.AuditIssue
import burp.api.montoya.websocket.Direction
import kotlinx.serialization.Serializable

fun AuditIssue.toSerializableForm(): IssueDetails {
    val service = runCatching { httpService() }.getOrNull()
    val severityValue = runCatching { severity()?.name }.getOrNull()
        ?.let { runCatching { AuditIssueSeverity.valueOf(it) }.getOrNull() }
        ?: AuditIssueSeverity.INFORMATION
    val confidenceValue = runCatching { confidence()?.name }.getOrNull()
        ?.let { runCatching { AuditIssueConfidence.valueOf(it) }.getOrNull() }
        ?: AuditIssueConfidence.TENTATIVE
    val definitionValue = runCatching { definition() }.getOrNull()

    return IssueDetails(
        name = runCatching { name() }.getOrNull(),
        detail = runCatching { detail() }.getOrNull(),
        remediation = runCatching { remediation() }.getOrNull(),
        httpService = service?.let {
            HttpService(
                host = runCatching { it.host() }.getOrDefault("unknown"),
                port = runCatching { it.port() }.getOrDefault(0),
                secure = runCatching { it.secure() }.getOrDefault(false)
            )
        },
        baseUrl = runCatching { baseUrl() }.getOrNull(),
        severity = severityValue,
        confidence = confidenceValue,
        requestResponses = runCatching { requestResponses().map { rr -> rr.toSerializableForm() } }.getOrDefault(emptyList()),
        collaboratorInteractions = runCatching { collaboratorInteractions() }.getOrDefault(emptyList()).map {
            Interaction(
                interactionId = runCatching { it.id().toString() }.getOrDefault("unknown"),
                timestamp = runCatching { it.timeStamp().toString() }.getOrDefault("unknown")
            )
        },
        definition = AuditIssueDefinition(
            id = runCatching { definitionValue?.name() }.getOrNull().orEmpty().ifBlank { "unknown" },
            background = runCatching { definitionValue?.background() }.getOrNull(),
            remediation = runCatching { definitionValue?.remediation() }.getOrNull(),
            typeIndex = runCatching { definitionValue?.typeIndex() }.getOrNull() ?: 0,
        )
    )
}

fun burp.api.montoya.http.message.HttpRequestResponse.toSerializableForm(): HttpRequestResponse {
    return HttpRequestResponse(
        request = request()?.toString() ?: "<no request>",
        response = response()?.toString() ?: "<no response>",
        notes = runCatching { annotations().notes() }.getOrNull()
    )
}

fun ProxyHttpRequestResponse.toSerializableForm(): HttpRequestResponse {
    return HttpRequestResponse(
        request = request()?.toString() ?: "<no request>",
        response = response()?.toString() ?: "<no response>",
        notes = runCatching { annotations().notes() }.getOrNull()
    )
}

fun ProxyWebSocketMessage.toSerializableForm(): WebSocketMessage {
    return WebSocketMessage(
        payload = payload()?.toString() ?: "<no payload>",
        direction =
            if (direction() == Direction.CLIENT_TO_SERVER)
                WebSocketMessageDirection.CLIENT_TO_SERVER
            else
                WebSocketMessageDirection.SERVER_TO_CLIENT,
        notes = runCatching { annotations().notes() }.getOrNull()
    )
}

fun burp.api.montoya.http.message.HttpRequestResponse.toSiteMapEntry(): SiteMapEntry {
    val req = request()
    return SiteMapEntry(
        url = req?.url() ?: "<no url>",
        request = req?.toString() ?: "<no request>",
        response = response()?.toString() ?: "<no response>"
    )
}

@Serializable
data class IssueDetails(
    val name: String?,
    val detail: String?,
    val remediation: String?,
    val httpService: HttpService?,
    val baseUrl: String?,
    val severity: AuditIssueSeverity,
    val confidence: AuditIssueConfidence,
    val requestResponses: List<HttpRequestResponse>,
    val collaboratorInteractions: List<Interaction>,
    val definition: AuditIssueDefinition
)

@Serializable
data class HttpService(
    val host: String,
    val port: Int,
    val secure: Boolean
)

@Serializable
enum class AuditIssueSeverity {
    HIGH,
    MEDIUM,
    LOW,
    INFORMATION,
    FALSE_POSITIVE;
}

@Serializable
enum class AuditIssueConfidence {
    CERTAIN,
    FIRM,
    TENTATIVE
}

@Serializable
data class HttpRequestResponse(
    val request: String?,
    val response: String?,
    val notes: String?
)

@Serializable
data class Interaction(
    val interactionId: String,
    val timestamp: String
)

@Serializable
data class AuditIssueDefinition(
    val id: String,
    val background: String?,
    val remediation: String?,
    val typeIndex: Int
)

@Serializable
enum class WebSocketMessageDirection {
    CLIENT_TO_SERVER,
    SERVER_TO_CLIENT
}

@Serializable
data class WebSocketMessage(
    val payload: String?,
    val direction: WebSocketMessageDirection,
    val notes: String?
)

@Serializable
data class SiteMapEntry(
    val url: String,
    val request: String,
    val response: String
)

