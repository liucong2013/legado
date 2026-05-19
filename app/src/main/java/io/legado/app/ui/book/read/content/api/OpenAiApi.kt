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

class OpenAiApi : IAiApi {

    override fun createRequest(
        apiKey: String,
        apiUrl: String,
        model: String,
        systemPrompt: String,
        content: String
    ): Request {
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val messages = mutableListOf<Map<String, String>>()
        messages.add(mapOf("role" to "system", "content" to systemPrompt))
        messages.add(mapOf("role" to "user", "content" to content))
        val requestBody = GSON.toJson(mapOf(
            "model" to model,
            "messages" to messages,
            "stream" to true
        )).toRequestBody(mediaType)

        return Request.Builder()
            .url(apiUrl)
            .post(requestBody)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Accept", "text/event-stream")
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
                if (line.startsWith("data:")) {
                    val data = line.substring(5).trim()
                    if (data == "[DONE]") {
                        break
                    }
                    try {
                        val reader = JsonReader(data.reader())
                        val chunk = GSON.fromJson<Map<String, Any>>(reader, object : com.google.gson.reflect.TypeToken<Map<String, Any>>() {}.type)
                        val choices = chunk["choices"] as? List<*>
                        val delta = choices?.firstOrNull() as? Map<*, *>
                        val content = delta?.get("delta") as? Map<*, *>
                        val text = content?.get("content") as? String
                        if (!text.isNullOrEmpty()) {
                            withContext(Dispatchers.Main) {
                                onResponse.invoke(text)
                            }
                        }
                    } catch (e: Exception) {
                        // Ignore parsing errors for now
                        Log.w("AiSummary-OpenAI", "JSON parsing error: ${e.message} for data: $data")
                    }
                }
            }
        } catch (e: IOException) {
            Log.e("AiSummary-OpenAI", "Stream reading error: ${e.stackTraceToString()}")
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
