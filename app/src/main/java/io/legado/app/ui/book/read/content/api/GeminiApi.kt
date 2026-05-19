package io.legado.app.ui.book.read.content.api

import android.util.Log
import com.google.gson.stream.JsonReader
import io.legado.app.utils.GSON
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException

class GeminiApi : IAiApi {

    override fun createRequest(
        apiKey: String,
        apiUrl: String,
        model: String,
        systemPrompt: String,
        content: String
    ): Request {
        // For Gemini, the model is part of the URL, and the key is a parameter.
        // Example URL: https://generativelanguage.googleapis.com/v1beta/models/gemini-pro:streamGenerateContent?key=YOUR_API_KEY
        val fullUrl = "$apiUrl/models/$model:streamGenerateContent?key=$apiKey"

        val mediaType = "application/json; charset=utf-8".toMediaType()

        // Gemini combines system and user prompts.
        val combinedContent = "$systemPrompt\n\n$content"
        val parts = listOf(mapOf("text" to combinedContent))
        val contents = listOf(mapOf("parts" to parts))
        val requestBodyMap = mapOf("contents" to contents)

        val requestBody = GSON.toJson(requestBodyMap).toRequestBody(mediaType)

        return Request.Builder()
            .url(fullUrl)
            .post(requestBody)
            .build()
    }

    override suspend fun handleStreamResponse(
        response: Response,
        onResponse: (String) -> Unit,
        onFinish: () -> Unit,
        onError: (String) -> Unit
    ) {
        val source = response.body?.source() ?: run {
            withContext(Dispatchers.Main) {
                onError.invoke("Response body is null")
                onFinish.invoke()
            }
            return
        }
        try {
            while (!source.exhausted()) {
                val line = source.readUtf8Line() ?: continue
                // Gemini stream might send data in chunks that are not perfectly line-separated JSON
                // but in practice, each meaningful part is often on its own line.
                // We remove the "data: " prefix if it exists, for compatibility.
                val jsonString = line.removePrefix("data:").trim()
                if (jsonString.isEmpty()) continue

                try {
                    val chunk = GSON.fromJson<Map<String, Any>>(jsonString, object : com.google.gson.reflect.TypeToken<Map<String, Any>>() {}.type)
                    val candidates = chunk["candidates"] as? List<*>
                    val candidate = candidates?.firstOrNull() as? Map<*, *>
                    val content = candidate?.get("content") as? Map<*, *>
                    val parts = content?.get("parts") as? List<*>
                    val part = parts?.firstOrNull() as? Map<*, *>
                    val text = part?.get("text") as? String
                    if (!text.isNullOrEmpty()) {
                        withContext(Dispatchers.Main) {
                            onResponse.invoke(text)
                        }
                    }
                } catch (e: Exception) {
                    Log.w("AiSummary-Gemini", "JSON parsing error in stream: ${e.message} for line: $jsonString")
                }
            }
        } catch (e: IOException) {
            Log.e("AiSummary-Gemini", "Stream reading error: ${e.stackTraceToString()}")
            withContext(Dispatchers.Main) {
                onError.invoke("读取数据流失败: ${e.message}")
            }
        } finally {
            withContext(Dispatchers.Main) {
                onFinish.invoke()
            }
        }
    }
}
