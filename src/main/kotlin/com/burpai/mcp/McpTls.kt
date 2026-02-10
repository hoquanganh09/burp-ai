package com.burpai.mcp

import com.burpai.config.McpSettings
import io.netty.handler.ssl.util.SelfSignedCertificate
import java.io.File
import java.io.FileOutputStream
import java.security.KeyStore

data class McpTlsMaterial(
    val keyStore: KeyStore,
    val password: CharArray,
    val keyAlias: String
)

object McpTls {
    fun resolve(settings: McpSettings): McpTlsMaterial? {
        val keystorePath = settings.tlsKeystorePath.trim()
        if (keystorePath.isBlank()) return null

        val password = settings.tlsKeystorePassword.toCharArray()
        val keystoreFile = File(keystorePath)

        if (!keystoreFile.exists()) {
            if (!settings.tlsAutoGenerate) return null
            generateSelfSigned(keystoreFile, password)
        }

        val keyStore = KeyStore.getInstance("PKCS12")
        keystoreFile.inputStream().use { input ->
            keyStore.load(input, password)
        }

        val alias = keyStore.aliases().toList().firstOrNull() ?: "mcp"
        return McpTlsMaterial(keyStore = keyStore, password = password, keyAlias = alias)
    }

    private fun generateSelfSigned(keystoreFile: File, password: CharArray) {
        keystoreFile.parentFile?.mkdirs()
        val ssc = SelfSignedCertificate("burp-mcp")
        val keyStore = KeyStore.getInstance("PKCS12")
        keyStore.load(null, password)
        keyStore.setKeyEntry("mcp", ssc.key(), password, arrayOf(ssc.cert()))
        FileOutputStream(keystoreFile).use { out ->
            keyStore.store(out, password)
        }
        cleanupSelfSigned(ssc)
    }

    private fun cleanupSelfSigned(cert: SelfSignedCertificate) {
        try {
            cert.delete()
        } catch (_: Exception) {
        }
    }
}

