package com.burpai.mcp.tools

import burp.api.montoya.scanner.ScanTask
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object ScannerTaskRegistry {
    private val idToTask: MutableMap<String, ScanTask> = ConcurrentHashMap()

    fun put(task: ScanTask): String {
        val id = UUID.randomUUID().toString()
        idToTask[id] = task
        return id
    }

    fun get(id: String): ScanTask? = idToTask[id]

    fun remove(id: String): ScanTask? = idToTask.remove(id)

    fun clear() {
        idToTask.clear()
    }
}

