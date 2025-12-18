package com.eynnzerr.model

import kotlinx.serialization.Serializable

@Serializable
data class BlacklistRequest(val blockedUserId: String)

@Serializable
data class WhitelistRequest(val allowedUserId: String)

@Serializable
data class  CreateChatRequest(
    val roomName: String,
    val ownerName: String,
    val ownerAvatar: String,
)

@Serializable
data class JoinChatRequest(
    val ownerId: String,
    val username: String,
    val avatar: String,
)

@Serializable
data class MessageRequest(val limit: Int, val before: Long)

@Serializable
data class RemoveMemberRequest(val userId: String)