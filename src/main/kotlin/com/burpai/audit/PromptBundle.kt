package com.burpai.audit

data class PromptBundle(
    val createdAtEpochMs: Long,
    val sessionId: String,
    val backendId: String,
    val backendConfig: com.burpai.backends.BackendLaunchConfig,
    val promptText: String,
    val promptSha256: String,
    val contextJson: String?,
    val contextSha256: String?,
    val privacyMode: String,
    val determinismMode: Boolean
)

