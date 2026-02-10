package com.burpai.backends.openai

import com.burpai.backends.AiBackend
import com.burpai.backends.AiBackendFactory

class OpenAiCompatibleBackendFactory : AiBackendFactory {
    override fun create(): AiBackend = OpenAiCompatibleBackend()
}

