package com.eynnzerr.model

import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime


import org.jetbrains.exposed.sql.ReferenceOption

object ChatGroupMembers : Table("chat_group_members") {
    val groupId = varchar("group_id", 36).references(ChatGroups.id, onDelete = ReferenceOption.CASCADE)
    val userId = varchar("user_id", 128).uniqueIndex().references(Users.id, onDelete = ReferenceOption.CASCADE)
    val joinedAt = datetime("joined_at")
    val username = varchar("username", 255)
    val avatar = text("avatar")

    override val primaryKey = PrimaryKey(groupId, userId)
}

@Serializable
data class ChatGroupMember(
    val groupId: String,
    val userId: String,
    val joinedAt: String,
    val username: String,
    val avatar: String,
)
