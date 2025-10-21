package com.eynnzerr.data

import com.eynnzerr.model.BlackList
import com.eynnzerr.model.Users
import com.eynnzerr.model.WhiteList
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import java.time.LocalDateTime

class UserRepository {
    suspend fun createUser(userId: String, token: String): Boolean = DatabaseFactory.dbQuery {
        val insertStatement = Users.insert {
            it[id] = userId
            it[this.token] = token
            it[createdAt] = LocalDateTime.now()
            it[updatedAt] = LocalDateTime.now()
        }
        insertStatement.resultedValues?.singleOrNull() != null
    }

    suspend fun updateUser(userId: String, token: String): Boolean = DatabaseFactory.dbQuery {
        Users.update({ Users.id eq userId }) {
            it[this.token] = token
            it[updatedAt] = LocalDateTime.now()
        } > 0
    }

    suspend fun getUserById(userId: String): ResultRow? = DatabaseFactory.dbQuery {
        Users.selectAll()
            .where { Users.id eq userId }.singleOrNull()
    }

    suspend fun updateInviteCode(userId: String, inviteCode: String): Boolean = DatabaseFactory.dbQuery {
        Users.update({ Users.id eq userId }) {
            it[this.inviteCode] = inviteCode
            it[updatedAt] = LocalDateTime.now()
        } > 0
    }

    suspend fun getInviteCode(userId: String): String? = DatabaseFactory.dbQuery {
        Users.selectAll()
            .where { Users.id eq userId }
            .singleOrNull()
            ?.get(Users.inviteCode)
    }

    suspend fun addToBlackList(userId: String, blockedUserId: String): Boolean = DatabaseFactory.dbQuery {
        val insertStatement = BlackList.insert {
            it[this.userId] = userId
            it[this.blockedUserId] = blockedUserId
            it[createdAt] = LocalDateTime.now()
        }
        insertStatement.resultedValues?.singleOrNull() != null
    }

    suspend fun removeFromBlackList(userId: String, blockedUserId: String): Boolean = DatabaseFactory.dbQuery {
        BlackList.deleteWhere {
            (BlackList.userId eq userId) and (BlackList.blockedUserId eq blockedUserId)
        } > 0
    }

    suspend fun isInBlackList(userId: String, targetUserId: String): Boolean = DatabaseFactory.dbQuery {
        BlackList.selectAll()
            .where { (BlackList.userId eq userId) and (BlackList.blockedUserId eq targetUserId) }
            .count() > 0
    }

    suspend fun addToWhiteList(userId: String, allowedUserId: String): Boolean = DatabaseFactory.dbQuery {
        val insertStatement = WhiteList.insert {
            it[this.userId] = userId
            it[this.allowedUserId] = allowedUserId
            it[createdAt] = LocalDateTime.now()
        }
        insertStatement.resultedValues?.singleOrNull() != null
    }

    suspend fun removeFromWhiteList(userId: String, allowedUserId: String): Boolean = DatabaseFactory.dbQuery {
        WhiteList.deleteWhere {
            (WhiteList.userId eq userId) and (BlackList.blockedUserId eq allowedUserId)
        } > 0
    }

    suspend fun isInWhiteList(userId: String, targetUserId: String): Boolean = DatabaseFactory.dbQuery {
        WhiteList.selectAll()
            .where { (WhiteList.userId eq userId) and (WhiteList.allowedUserId eq targetUserId) }
            .count() > 0
    }

    suspend fun getBlacklist(userId: String): List<String> = DatabaseFactory.dbQuery {
        BlackList.selectAll()
            .where { BlackList.userId eq userId }
            .map { it[BlackList.blockedUserId] }
    }

    suspend fun getWhitelist(userId: String): List<String> = DatabaseFactory.dbQuery {
        WhiteList.selectAll()
            .where { WhiteList.userId eq userId }
            .map { it[WhiteList.allowedUserId] }
    }
}
