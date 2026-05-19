package io.legado.app.ui.book.read.content

import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.help.config.AppConfig
import io.legado.app.model.AiSummaryState
import android.util.Log
import io.legado.app.ui.book.read.content.api.AiApiFactory
import io.legado.app.utils.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import splitties.init.appCtx
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

object AiSummaryProvider {

    private const val cacheFolderName = "zhanweifu_cache"
    private val cacheDir: File by lazy { appCtx.externalFiles.getFile(cacheFolderName) }

    fun getDialogContent(book: Book, bookChapter: BookChapter): String? {
        val file = cacheDir.getFile(book.getFolderName(), bookChapter.getFileName())
        if (file.exists()) {
            return file.readText()
        }
        return null
    }

    fun saveDialogContent(book: Book, bookChapter: BookChapter, content: String) {
        cacheDir.getFile(book.getFolderName(), bookChapter.getFileName())
            .createFileIfNotExist().writeText(content)
    }

    fun deleteDialogContent(book: Book, bookChapter: BookChapter) {
        val file = cacheDir.getFile(book.getFolderName(), bookChapter.getFileName())
        if (file.exists()) {
            file.delete()
        }
    }

    fun getAiSummaryFromCache(book: Book, chapter: BookChapter): String? {
        val file = cacheDir.getFile(book.getFolderName(), chapter.getFileName() + "_ai_summary")
        Log.d("AiSummary", "获取AI摘要缓存 章节 '${chapter.title}', 路径: ${file.absolutePath}")
        return if (file.exists()) {
            Log.d("AiSummary", "缓存存在，返回内容。")
            file.readText()
        } else {
            Log.d("AiSummary", "缓存不存在。")
            null
        }
    }

    fun saveAiSummaryToCache(book: Book, chapter: BookChapter, summary: String) {
        val file = cacheDir.getFile(book.getFolderName(), chapter.getFileName() + "_ai_summary")
        Log.d("AiSummary", "保存AI摘要缓存 章节 '${chapter.title}', 路径: ${file.absolutePath}")
        file.createFileIfNotExist().writeText(summary)
    }

    fun delAiSummaryCache(book: Book, chapter: BookChapter) {
        val file = cacheDir.getFile(book.getFolderName(), chapter.getFileName() + "_ai_summary")
        if (file.exists()) {
            file.delete()
        }
    }

    suspend fun getAiSummary(
        content: String,
        onResponse: (String) -> Unit,
        onFinish: () -> Unit,
        onError: (String) -> Unit
    ) {
        val activeProfile = AppConfig.getActiveProfile()
        if (activeProfile == null) {
            onError.invoke("请先在设置中创建并激活一个AI配置方案")
            onFinish.invoke()
            return
        }

        if (activeProfile.apiKey.isEmpty() || activeProfile.apiUrl.isEmpty()) {
            onError.invoke("当前激活的AI方案缺少API Key或URL")
            onFinish.invoke()
            return
        }

        val wordCount = content.length
        Log.d("AiSummary", "开始生成AI摘要，请求字数：${wordCount}")

        val newContent = "${content}\n\n本章${wordCount}字左右"

        val client = OkHttpClient.Builder()
            .connectTimeout(120, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .build()

        try {
            // 使用工厂和策略模式
            val api = AiApiFactory.create(activeProfile.apiFormat)
            val request = api.createRequest(
                activeProfile.apiKey,
                activeProfile.apiUrl,
                activeProfile.modelId,
                activeProfile.systemPrompt,
                newContent
            )

            withContext(Dispatchers.IO) {
                client.newCall(request).execute().use {
                    if (!it.isSuccessful) {
                        throw IOException("Unexpected code ${it.body?.string()}")
                    }
                    api.handleStreamResponse(it, onResponse, onFinish, onError)
                }
            }
        } catch (e: IOException) {
            Log.e("getAiSummary", e.stackTraceToString())
            withContext(Dispatchers.Main) {
                onError.invoke("请求失败: ${e.message}")
                onFinish.invoke()
            }
        }
    }

    fun clearAllAiSummaryCache() {
        if (cacheDir.exists()) {
            cacheDir.deleteRecursively()
        }
        AiSummaryState.inProgress.clear()
    }
}
