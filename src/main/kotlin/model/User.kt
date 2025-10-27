package com.eynnzerr.model

import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime

object Users : Table("users") {
    val id = varchar("id", 128)
    val inviteCode = varchar("invite_code", 128).nullable()
    val createdAt = datetime("created_at")
    val updatedAt = datetime("updated_at")

    override val primaryKey = PrimaryKey(id)
}

object BlackList : Table("black_list") {
    val userId = varchar("user_id", 128)
    val blockedUserId = varchar("blocked_user_id", 128)
    val createdAt = datetime("created_at")

    override val primaryKey = PrimaryKey(userId, blockedUserId)
}

object WhiteList : Table("white_list") {
    val userId = varchar("user_id", 128)
    val allowedUserId = varchar("allowed_user_id", 128)
    val createdAt = datetime("created_at")

    override val primaryKey = PrimaryKey(userId, allowedUserId)
}

@Serializable
data class UserRegisterRequest(
    val userId: String,
    val originalToken: String
)

@Serializable
data class UserRegisterResponse(
    val token: String,
    val expiresIn: Long
)

@Serializable
data class UpdateInviteCodeRequest(
    val inviteCode: String
)