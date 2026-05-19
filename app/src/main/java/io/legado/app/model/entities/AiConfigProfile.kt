package io.legado.app.model.entities

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class AiConfigProfile(
    var name: String,
    var apiKey: String = "",
    var apiUrl: String = "",
    var apiFormat: String = "openai",
    var modelId: String = "",
    var systemPrompt: String = "请总结以下内容："
) : Parcelable
