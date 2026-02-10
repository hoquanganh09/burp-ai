package com.burpai.backends.ollama

import com.burpai.backends.AiBackend
import com.burpai.backends.AiBackendFactory

class OllamaBackendFactory : AiBackendFactory {
    override fun create(): AiBackend = OllamaBackend()
}

