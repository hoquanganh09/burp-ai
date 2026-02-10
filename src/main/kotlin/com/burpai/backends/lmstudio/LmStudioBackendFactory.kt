package com.burpai.backends.lmstudio

import com.burpai.backends.AiBackend
import com.burpai.backends.AiBackendFactory

class LmStudioBackendFactory : AiBackendFactory {
    override fun create(): AiBackend = LmStudioBackend()
}

