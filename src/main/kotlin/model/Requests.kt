package com.eynnzerr.model

import kotlinx.serialization.Serializable

@Serializable
data class RoomAccessRequest(
    val requestId: String,
    val requesterId: String,
    val targetUserId: String,
    val timestamp: Long
)

@Serializable
data class RoomAccessResponse(
    val requestId: String,
    val approved: Boolean,
    val roomInfo: RoomInfo? = null,
    val message: String? = null
)

@Serializable
data class WebSocketMessage(
    val type: MessageType,
    val payload: String
)

@Serializable
enum class MessageType {
    REQUEST_ACCESS,
    ACCESS_REQUEST_RECEIVED,
    APPROVE_ACCESS,
    DENY_ACCESS,
    ACCESS_RESULT,
    ERROR
}
