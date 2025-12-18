package com.eynnzerr.model

import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime
import org.jetbrains.exposed.sql.ReferenceOption

object ChatGroups : Table("chat_groups") {
    val id = varchar("id", 36) // Use varchar for UUID directly
    val ownerId = varchar("owner_id", 128).uniqueIndex().references(Users.id, onDelete = ReferenceOption.CASCADE)
    val createdAt = datetime("created_at")
    val lastActivityAt = datetime("last_activity_at")
    val name = varchar("name", 255)

    override val primaryKey = PrimaryKey(id)
}

@Serializable
data class ChatGroup(
    val id: String,
    val ownerId: String,
    val createdAt: String,
    val lastActivityAt: String,
    val name: String
)
