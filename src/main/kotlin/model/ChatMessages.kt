package com.eynnzerr.model

import org.jetbrains.exposed.dao.id.LongIdTable

import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.javatime.datetime

object ChatMessages : LongIdTable("chat_messages") {
    val groupId = varchar("group_id", 36)
    val userId = varchar("user_id", 128)
    val content = text("content")
    val username = varchar("username", 255)
    val avatar = text("avatar")
    val createdAt = datetime("created_at")
}

@Serializable
data class ChatMessage(
    val id: Long,
    val groupId: String,
    val userId: String,
    val content: String,
    val username: String,
    val avatar: String,
    val createdAt: String
)
