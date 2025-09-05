package com.eynnzerr.model


import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime

object Rooms : Table() {
    val id = integer("id").autoIncrement()
    val userId = varchar("user_id", 128)
    val roomNumber = varchar("room_number", 128)
    val encryptedInfo = text("encrypted_info")
    val createdAt = datetime("created_at")
    val updatedAt = datetime("updated_at")

    override val primaryKey = PrimaryKey(id)
}

@Serializable
data class RoomInfo(
    val roomNumber: String,
    val additionalInfo: Map<String, String> = emptyMap()
)

@Serializable
data class UploadRoomRequest(
    val roomNumber: String,
)

@Serializable
data class VerifyInviteCodeRequest(
    val targetUserId: String,
    val inviteCode: String
)

@Serializable
data class RoomResponse(
    val number: String,
)
