package com.burpai.backends

object BackendDiagnostics {
    @Volatile
    var output: ((String) -> Unit)? = null

    @Volatile
    var error: ((String) -> Unit)? = null

    fun log(message: String) {
        try {
            output?.invoke(message)
        } catch (_: Exception) {
            System.err.println(message)
        }
        if (output == null) {
            System.err.println(message)
        }
    }

    fun logError(message: String) {
        try {
            error?.invoke(message)
        } catch (_: Exception) {
            System.err.println(message)
        }
        if (error == null) {
            System.err.println(message)
        }
    }
}

