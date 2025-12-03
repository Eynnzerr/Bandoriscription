package com.eynnzerr.model

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime


object ChatGroupMembers : Table("chat_group_members") {
    val groupId = varchar("group_id", 36)
    val userId = varchar("user_id", 128).uniqueIndex() // UNIQUE constraint
    val joinedAt = datetime("joined_at")

    override val primaryKey = PrimaryKey(groupId, userId)
}

@kotlinx.serialization.Serializable
data class ChatGroupMember(
    val groupId: String,
    val userId: String,
    val joinedAt: String
)
