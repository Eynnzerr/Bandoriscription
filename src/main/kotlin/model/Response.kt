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

@Serializable
data class CreateChatResponse(val groupId: String)

@Serializable
data class UserInfo(
    val id: String
)

@Serializable
data class ChatMessageResponse(
    val id: Long,
    val senderId: String,
    val content: String,
    val username: String,
    val avatar: String,
    val createdAt: String
)

@Serializable
data class OwnerInfo(
    val id: String,
    val name: String,
    val avatar: String
)

@Serializable
data class ChatGroupDetails(
    val id: String,
    val name: String,
    val owner: OwnerInfo,
    val memberCount: Long,
    val createdAt: String,
    val lastActivityAt: String
)

@Serializable
data class AllChatGroups(
    val chatGroups: List<ChatGroupDetails>,
)

@Serializable
data class ChatGroupChange(
    val chatGroups: List<ChatGroupDetails>,
    val changeStatus: GroupChangeStatus,
)

enum class GroupChangeStatus {
    UPSERTED,
    REMOVED
}