package com.eynnzerr.model

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime

import java.util.UUID

object ChatGroups : Table("chat_groups") {
    val id = varchar("id", 36) // Use varchar for UUID directly
    val ownerId = varchar("owner_id", 128).uniqueIndex() // UNIQUE constraint
    val createdAt = datetime("created_at")

    override val primaryKey = PrimaryKey(id)
}

@kotlinx.serialization.Serializable
data class ChatGroup(
    val id: String,
    val ownerId: String,
    val createdAt: String
)
