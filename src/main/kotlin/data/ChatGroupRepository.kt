package com.eynnzerr.data

import com.eynnzerr.model.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import java.time.Duration
import java.time.LocalDateTime
import java.util.UUID
import org.jetbrains.exposed.sql.alias
import org.jetbrains.exposed.sql.count

class ChatGroupRepository {

    private fun resultRowToChatGroup(row: ResultRow) = ChatGroup(
        id = row[ChatGroups.id],
        ownerId = row[ChatGroups.ownerId],
        createdAt = row[ChatGroups.createdAt].toString(),
        lastActivityAt = row[ChatGroups.lastActivityAt].toString(),
        name = row[ChatGroups.name],
    )

    private fun resultRowToChatMessage(row: ResultRow) = ChatMessage(
        id = row[ChatMessages.id].value,
        groupId = row[ChatMessages.groupId],
        userId = row[ChatMessages.userId],
        content = row[ChatMessages.content],
        username = row[ChatMessages.username],
        avatar = row[ChatMessages.avatar],
        createdAt = row[ChatMessages.createdAt].toString()
    )

    private fun resultRowToSimpleUserInfo(row: ResultRow) = OwnerInfo(
        id = row[ChatGroupMembers.userId],
        name = row[ChatGroupMembers.username],
        avatar = row[ChatGroupMembers.avatar],
    )

    suspend fun createChatGroup(ownerId: String, info: CreateChatRequest): ChatGroup? = DatabaseFactory.dbQuery {
        // Check if user already owns a group
        if (ChatGroups.selectAll().where { ChatGroups.ownerId eq ownerId }.singleOrNull() != null) {
            return@dbQuery null
        }

        val newGroupId = UUID.randomUUID().toString()
        val insertStatement = ChatGroups.insert {
            it[id] = newGroupId
            it[this.ownerId] = ownerId
            it[createdAt] = LocalDateTime.now()
            it[lastActivityAt] = LocalDateTime.now()
            it[name] = info.roomName
        }

        val chatGroup = insertStatement.resultedValues?.singleOrNull()?.let(::resultRowToChatGroup)

        // Add owner as the first member
        if (chatGroup != null) {
            ChatGroupMembers.insert {
                it[groupId] = chatGroup.id
                it[userId] = ownerId
                it[joinedAt] = LocalDateTime.now()
                it[username] = info.ownerName
                it[avatar] = info.ownerAvatar
            }
        }
        chatGroup
    }

    suspend fun getChatGroup(groupId: String): ChatGroup? = DatabaseFactory.dbQuery {
        ChatGroups.selectAll().where { ChatGroups.id eq groupId }
            .singleOrNull()
            ?.let(::resultRowToChatGroup)
    }

    suspend fun getAllChatGroupsWithDetails(): List<ChatGroupDetails> = DatabaseFactory.dbQuery {
        val ownerDetails = ChatGroupMembers.alias("owner_details")
        val memberCount = ChatGroupMembers.userId.count().alias("member_count")

        ChatGroups
            .join(
                ownerDetails,
                JoinType.INNER,
                additionalConstraint = { (ChatGroups.id eq ownerDetails[ChatGroupMembers.groupId]) and (ChatGroups.ownerId eq ownerDetails[ChatGroupMembers.userId]) }
            )
            .join(
                ChatGroupMembers,
                JoinType.LEFT,
                onColumn = ChatGroups.id,
                otherColumn = ChatGroupMembers.groupId
            )
            .select(
                ChatGroups.id,
                ChatGroups.name,
                ChatGroups.createdAt,
                ChatGroups.lastActivityAt,
                ChatGroups.ownerId,
                ownerDetails[ChatGroupMembers.username],
                ownerDetails[ChatGroupMembers.avatar],
                memberCount
            )
            .groupBy(
                ChatGroups.id,
                ownerDetails[ChatGroupMembers.username],
                ownerDetails[ChatGroupMembers.avatar]
            )
            .map { row ->
                ChatGroupDetails(
                    id = row[ChatGroups.id],
                    name = row[ChatGroups.name],
                    owner = OwnerInfo(
                        id = row[ChatGroups.ownerId],
                        name = row[ownerDetails[ChatGroupMembers.username]],
                        avatar = row[ownerDetails[ChatGroupMembers.avatar]]
                    ),
                    memberCount = row[memberCount],
                    createdAt = row[ChatGroups.createdAt].toString(),
                    lastActivityAt = row[ChatGroups.lastActivityAt].toString()
                )
            }
    }

    suspend fun getChatGroupByOwnerId(ownerId: String): ChatGroup? = DatabaseFactory.dbQuery {
        ChatGroups.selectAll()
            .where(ChatGroups.ownerId eq ownerId)
            .singleOrNull()
            ?.let(::resultRowToChatGroup)
    }
    
    suspend fun getChatGroupForUser(userId: String): ChatGroup? = DatabaseFactory.dbQuery {
        (ChatGroups innerJoin ChatGroupMembers)
            .select(ChatGroups.columns)
            .where { ChatGroupMembers.userId eq userId }
            .singleOrNull()
            ?.let(::resultRowToChatGroup)
    }

    suspend fun isUserInAnyGroup(userId: String): Boolean = DatabaseFactory.dbQuery {
        ChatGroupMembers.selectAll().where { ChatGroupMembers.userId eq userId }.singleOrNull() != null
    }

    suspend fun joinChatGroup(groupId: String, userId: String, maxMembers: Int, info: JoinChatRequest): Boolean = DatabaseFactory.dbQuery {
        // Check if user is already in this or any other group
        if (ChatGroupMembers.selectAll().where { ChatGroupMembers.userId eq userId }.singleOrNull() != null) {
            return@dbQuery false // User is already in a group
        }

        // Check if group exists and is not full
        val groupExists = ChatGroups.selectAll().where { ChatGroups.id eq groupId }.singleOrNull() != null
        if (!groupExists) return@dbQuery false // Group does not exist

        val currentMembers = ChatGroupMembers.selectAll().where { ChatGroupMembers.groupId eq groupId }.count()
        if (currentMembers >= maxMembers) {
            return@dbQuery false // Group is full
        }

        ChatGroupMembers.insert {
            it[this.groupId] = groupId
            it[this.userId] = userId
            it[joinedAt] = LocalDateTime.now()
            it[username] = info.username
            it[avatar] = info.avatar
        }.insertedCount > 0
    }

    suspend fun leaveChatGroup(groupId: String, userId: String): Boolean = DatabaseFactory.dbQuery {
        ChatGroupMembers.deleteWhere { (ChatGroupMembers.groupId eq groupId) and (ChatGroupMembers.userId eq userId) } > 0
    }

    suspend fun removeMemberFromChatGroup(groupId: String, userId: String): Boolean = DatabaseFactory.dbQuery {
        ChatGroupMembers.deleteWhere { (ChatGroupMembers.groupId eq groupId) and (ChatGroupMembers.userId eq userId) } > 0
    }

    suspend fun disbandChatGroup(groupId: String): Boolean = DatabaseFactory.dbQuery {
        // Deleting the group will cascade delete members and messages
        ChatGroups.deleteWhere { ChatGroups.id eq groupId } > 0
    }

    suspend fun addChatMessage(groupId: String, userId: String, content: String, username: String, avatar: String): ChatMessage? = DatabaseFactory.dbQuery {
        // Ensure user is a member of the group
        if (ChatGroupMembers.selectAll().where { (ChatGroupMembers.groupId eq groupId) and (ChatGroupMembers.userId eq userId) }.singleOrNull() == null) {
            return@dbQuery null // User is not a member of this group
        }

        val insertStatement = ChatMessages.insert {
            it[this.groupId] = groupId
            it[this.userId] = userId
            it[this.content] = content
            it[this.username] = username
            it[this.avatar] = avatar
            it[createdAt] = LocalDateTime.now()
        }
        // Update last_activity_at for the chat group
        ChatGroups.update({ ChatGroups.id eq groupId }) {
            it[lastActivityAt] = LocalDateTime.now()
        }
        insertStatement.resultedValues?.singleOrNull()?.let(::resultRowToChatMessage)
    }

    suspend fun getChatMessages(groupId: String, limit: Int, beforeMessageId: Long?): List<ChatMessage> = DatabaseFactory.dbQuery {
        val query = ChatMessages.selectAll().where { ChatMessages.groupId eq groupId }
            .orderBy(ChatMessages.createdAt to SortOrder.DESC, ChatMessages.id to SortOrder.DESC)
            .limit(limit)

        if (beforeMessageId != null) {
            query.andWhere { ChatMessages.id less beforeMessageId }
        }

        query.map(::resultRowToChatMessage).reversed() // Display in ascending order by time
    }

    suspend fun getChatGroupMembers(groupId: String): List<String> = DatabaseFactory.dbQuery {
        ChatGroupMembers.selectAll().where { ChatGroupMembers.groupId eq groupId }
            .map { it[ChatGroupMembers.userId] }
    }

    suspend fun getChatGroupMemberCount(groupId: String): Long = DatabaseFactory.dbQuery {
        ChatGroupMembers.selectAll()
            .where { ChatGroupMembers.groupId eq groupId }
            .count()
    }

    suspend fun getChatGroupMembersSortedByJoinDate(groupId: String): List<String> = DatabaseFactory.dbQuery {
        ChatGroupMembers.selectAll().where { ChatGroupMembers.groupId eq groupId }
            .orderBy(ChatGroupMembers.joinedAt, SortOrder.ASC)
            .map { it[ChatGroupMembers.userId] }
    }

    suspend fun updateGroupOwner(groupId: String, newOwnerId: String): Boolean = DatabaseFactory.dbQuery {
        ChatGroups.update({ ChatGroups.id eq groupId }) {
            it[ownerId] = newOwnerId
        } > 0
    }

    suspend fun findInactiveChatGroups(timeout: Duration): List<ChatGroup> = DatabaseFactory.dbQuery {
        val cutoff = LocalDateTime.now().minus(timeout)
        ChatGroups.selectAll().where { ChatGroups.lastActivityAt less cutoff }
            .map(::resultRowToChatGroup)
    }

    suspend fun getUserSimpleInfo(userId: String): OwnerInfo? = DatabaseFactory.dbQuery {
        ChatGroupMembers
            .select(
                ChatGroupMembers.userId,
                ChatGroupMembers.username,
                ChatGroupMembers.avatar
            )
            .where { ChatGroupMembers.userId eq userId }
            .singleOrNull()?.let(::resultRowToSimpleUserInfo)
    }
}
