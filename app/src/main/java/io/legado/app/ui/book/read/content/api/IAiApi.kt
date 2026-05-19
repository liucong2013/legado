package io.legado.app.ui.book.read.content.api

import okhttp3.Request
import okhttp3.Response

interface IAiApi {
    // 根据输入参数，构建一个符合特定API格式的Request对象
    fun createRequest(apiKey: String, apiUrl: String, model: String, systemPrompt: String, content: String): Request

    // 处理从API返回的流式响应
    suspend fun handleStreamResponse(response: Response, onResponse: (String) -> Unit, onFinish: () -> Unit, onError: (String) -> Unit)
}
