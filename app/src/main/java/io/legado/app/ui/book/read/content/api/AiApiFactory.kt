package io.legado.app.ui.book.read.content.api

object AiApiFactory {
    fun create(apiFormat: String): IAiApi {
        return when (apiFormat) {
            "gemini" -> GeminiApi()
            else -> OpenAiApi() // Default to OpenAI
        }
    }
}
