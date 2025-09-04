package com.eynnzerr.model

import com.eynnzerr.utils.ResponseContentSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class ApiResponse(
    val status: String,
    @Serializable(with = ResponseContentSerializer::class)
    val response: ApiResponseContent
)

@Serializable
sealed class ApiResponseContent {
    @Serializable
    data class StringContent(val text: String) : ApiResponseContent()

    @Serializable
    data class ObjectContent(val data: JsonElement) : ApiResponseContent()
}
