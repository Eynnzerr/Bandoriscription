package com.eynnzerr.data

import com.eynnzerr.model.RoomInfo
import com.eynnzerr.model.Rooms
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import java.time.LocalDateTime

class RoomRepository {
    suspend fun saveRoom(userId: String, roomInfo: RoomInfo): Boolean = DatabaseFactory.dbQuery {
        val encryptedInfo = Json.encodeToString(roomInfo)

        val existing = Rooms.selectAll()
            .where { Rooms.userId eq userId }
            .singleOrNull()

        if (existing != null) {
            Rooms.update({ Rooms.userId eq userId }) {
                it[roomNumber] = roomInfo.roomNumber
                it[this.encryptedInfo] = encryptedInfo
                it[updatedAt] = LocalDateTime.now()
            } > 0
        } else {
            val insertStatement = Rooms.insert {
                it[this.userId] = userId
                it[roomNumber] = roomInfo.roomNumber
                it[this.encryptedInfo] = encryptedInfo
                it[createdAt] = LocalDateTime.now()
                it[updatedAt] = LocalDateTime.now()
            }
            insertStatement.resultedValues?.singleOrNull() != null
        }
    }

    suspend fun getRoom(userId: String): RoomInfo? = DatabaseFactory.dbQuery {
        Rooms.selectAll()
            .where { Rooms.userId eq userId }
            .singleOrNull()
            ?.let {
                Json.decodeFromString<RoomInfo>(it[Rooms.encryptedInfo])
            }
    }

    suspend fun deleteRoom(userId: String): Boolean = DatabaseFactory.dbQuery {
        Rooms.deleteWhere { Rooms.userId eq userId } > 0
    }
}
