package com.eynnzerr.model

import kotlinx.serialization.Serializable

@Serializable
data class BlacklistRequest(val blockedUserId: String)

@Serializable
data class WhitelistRequest(val allowedUserId: String)

@Serializable
data class JoinChatRequest(val ownerId: String)

@Serializable
data class RemoveMemberRequest(val userId: String)