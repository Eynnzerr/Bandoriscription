package com.eynnzerr.model

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime


import org.jetbrains.exposed.sql.ReferenceOption

object ChatGroupMembers : Table("chat_group_members") {
    val groupId = varchar("group_id", 36).references(ChatGroups.id, onDelete = ReferenceOption.CASCADE)
    val userId = varchar("user_id", 128).uniqueIndex().references(Users.id, onDelete = ReferenceOption.CASCADE)
    val joinedAt = datetime("joined_at")

    override val primaryKey = PrimaryKey(groupId, userId)
}

@kotlinx.serialization.Serializable
data class ChatGroupMember(
    val groupId: String,
    val userId: String,
    val joinedAt: String
)
