package com.burpai.mcp.tools

import burp.api.montoya.collaborator.CollaboratorClient
import java.util.concurrent.ConcurrentHashMap

object CollaboratorRegistry {
    private val clients = ConcurrentHashMap<String, CollaboratorClient>()

    fun put(key: String, client: CollaboratorClient) {
        clients[key] = client
    }

    fun get(key: String): CollaboratorClient? = clients[key]
}

