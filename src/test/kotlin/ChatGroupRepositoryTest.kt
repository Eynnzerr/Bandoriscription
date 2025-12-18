package com.eynnzerr

import com.eynnzerr.data.ChatGroupRepository
import com.eynnzerr.data.DatabaseFactory
import com.eynnzerr.di.appModule
import com.eynnzerr.model.ChatGroupMembers
import com.eynnzerr.model.ChatGroups
import com.eynnzerr.model.ChatMessages
import com.eynnzerr.model.Users
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.test.KoinTest
import org.koin.test.inject
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class ChatGroupRepositoryTest : KoinTest {

    private val chatGroupRepository by inject<ChatGroupRepository>()

    private val testOwnerId = "test_owner_1"
    private val testUserId1 = "test_user_1"
    private val testUserId2 = "test_user_2"

    @Before
    fun setup() {
        startKoin {
            modules(appModule)
        }
        DatabaseFactory.init(true) // init with test config, in-memory h2

        transaction {
            SchemaUtils.create(Users, ChatGroups, ChatGroupMembers, ChatMessages)
            // Insert test user data for owner/members as foreign keys reference it
            Users.insert { it[id] = testOwnerId }
            Users.insert { it[id] = testUserId1 }
            Users.insert { it[id] = testUserId2 }
        }
    }

    @After
    fun teardown() {
        stopKoin()
        transaction {
            SchemaUtils.drop(ChatMessages, ChatGroupMembers, ChatGroups, Users)
        }
    }

    @Test
    fun `createChatGroup should create a new group and add owner as member`() = runBlocking {
        val chatGroup = newSuspendedTransaction {
            chatGroupRepository.createChatGroup(testOwnerId)
        }
        assertNotNull(chatGroup)
        assertEquals(testOwnerId, chatGroup.ownerId)

        newSuspendedTransaction {
            assertTrue(chatGroupRepository.isUserInAnyGroup(testOwnerId))
            val members = chatGroupRepository.getChatGroupMembers(chatGroup.id)
            assertEquals(1, members.size)
            assertTrue(members.contains(testOwnerId))
        }
    }

    @Test
    fun `createChatGroup should return null if owner already has a group`() = runBlocking {
        newSuspendedTransaction {
            chatGroupRepository.createChatGroup(testOwnerId)
        }
        val secondGroup = newSuspendedTransaction {
            chatGroupRepository.createChatGroup(testOwnerId)
        }
        assertNull(secondGroup)
    }

    @Test
    fun `getChatGroup should return group by id`() = runBlocking {
        val createdGroup = newSuspendedTransaction {
            chatGroupRepository.createChatGroup(testOwnerId)
        }
        assertNotNull(createdGroup)

        val fetchedGroup = newSuspendedTransaction {
            chatGroupRepository.getChatGroup(createdGroup.id)
        }
        assertEquals(createdGroup.id, fetchedGroup?.id)
        assertEquals(createdGroup.ownerId, fetchedGroup?.ownerId)
    }

    @Test
    fun `getChatGroupByOwnerId should return group by owner id`() = runBlocking {
        val createdGroup = newSuspendedTransaction {
            chatGroupRepository.createChatGroup(testOwnerId)
        }
        assertNotNull(createdGroup)

        val fetchedGroup = newSuspendedTransaction {
            chatGroupRepository.getChatGroupByOwnerId(testOwnerId)
        }
        assertEquals(createdGroup.id, fetchedGroup?.id)
        assertEquals(createdGroup.ownerId, fetchedGroup?.ownerId)
    }

    @Test
    fun `isUserInAnyGroup should return true if user is member`() = runBlocking {
        val chatGroup = newSuspendedTransaction {
            chatGroupRepository.createChatGroup(testOwnerId)
        }
        assertNotNull(chatGroup)

        newSuspendedTransaction {
            assertTrue(chatGroupRepository.isUserInAnyGroup(testOwnerId))
            assertFalse(chatGroupRepository.isUserInAnyGroup(testUserId1))
        }
    }

    @Test
    fun `joinChatGroup should add user to group`() = runBlocking {
        val chatGroup = newSuspendedTransaction {
            chatGroupRepository.createChatGroup(testOwnerId)
        }
        assertNotNull(chatGroup)

        val success = newSuspendedTransaction {
            chatGroupRepository.joinChatGroup(chatGroup.id, testUserId1, 8)
        }
        assertTrue(success)

        newSuspendedTransaction {
            assertTrue(chatGroupRepository.isUserInAnyGroup(testUserId1))
            val members = chatGroupRepository.getChatGroupMembers(chatGroup.id)
            assertEquals(2, members.size)
            assertTrue(members.contains(testUserId1))
        }
    }

    @Test
    fun `joinChatGroup should fail if user is already in another group`() = runBlocking {
        val group1 = newSuspendedTransaction {
            chatGroupRepository.createChatGroup(testOwnerId)
        }
        assertNotNull(group1)

        val success1 = newSuspendedTransaction {
            chatGroupRepository.joinChatGroup(group1.id, testUserId1, 8)
        }
        assertTrue(success1)

        val group2Owner = "test_owner_2"
        newSuspendedTransaction { Users.insert { it[id] = group2Owner } }
        val group2 = newSuspendedTransaction {
            chatGroupRepository.createChatGroup(group2Owner)
        }
        assertNotNull(group2)

        val success2 = newSuspendedTransaction {
            chatGroupRepository.joinChatGroup(group2.id, testUserId1, 8)
        }
        assertFalse(success2)
    }

    @Test
    fun `joinChatGroup should fail if group is full`() = runBlocking {
        val chatGroup = newSuspendedTransaction {
            chatGroupRepository.createChatGroup(testOwnerId) // owner is 1st member
        }
        assertNotNull(chatGroup)

        val smallMaxMembers = 2
        newSuspendedTransaction { chatGroupRepository.joinChatGroup(chatGroup.id, testUserId1, smallMaxMembers) } // 2nd member

        val success = newSuspendedTransaction {
            chatGroupRepository.joinChatGroup(chatGroup.id, testUserId2, smallMaxMembers) // try to add 3rd member
        }
        assertFalse(success)
    }

    @Test
    fun `leaveChatGroup should remove user from group`() = runBlocking {
        val chatGroup = newSuspendedTransaction {
            chatGroupRepository.createChatGroup(testOwnerId)
        }
        assertNotNull(chatGroup)
        newSuspendedTransaction { chatGroupRepository.joinChatGroup(chatGroup.id, testUserId1, 8) }

        val success = newSuspendedTransaction {
            chatGroupRepository.leaveChatGroup(chatGroup.id, testUserId1)
        }
        assertTrue(success)

        newSuspendedTransaction {
            assertFalse(chatGroupRepository.isUserInAnyGroup(testUserId1))
            val members = chatGroupRepository.getChatGroupMembers(chatGroup.id)
            assertEquals(1, members.size) // Only owner remains
        }
    }

    @Test
    fun `disbandChatGroup should delete group and all members and messages`() = runBlocking {
        val chatGroup = newSuspendedTransaction {
            chatGroupRepository.createChatGroup(testOwnerId)
        }
        assertNotNull(chatGroup)
        newSuspendedTransaction { chatGroupRepository.joinChatGroup(chatGroup.id, testUserId1, 8) }
        newSuspendedTransaction { chatGroupRepository.addChatMessage(chatGroup.id, testOwnerId, "Hello") }

        val success = newSuspendedTransaction {
            chatGroupRepository.disbandChatGroup(chatGroup.id)
        }
        assertTrue(success)

        newSuspendedTransaction {
            assertNull(chatGroupRepository.getChatGroup(chatGroup.id))
            assertFalse(chatGroupRepository.isUserInAnyGroup(testOwnerId))
            assertFalse(chatGroupRepository.isUserInAnyGroup(testUserId1))
            assertTrue(chatGroupRepository.getChatMessages(chatGroup.id, 10, null).isEmpty())
        }
    }

    @Test
    fun `removeMemberFromChatGroup should remove a specific member`() = runBlocking {
        val chatGroup = newSuspendedTransaction {
            chatGroupRepository.createChatGroup(testOwnerId)
        }
        assertNotNull(chatGroup)
        newSuspendedTransaction { chatGroupRepository.joinChatGroup(chatGroup.id, testUserId1, 8) }

        val success = newSuspendedTransaction {
            chatGroupRepository.removeMemberFromChatGroup(chatGroup.id, testUserId1)
        }
        assertTrue(success)

        newSuspendedTransaction {
            assertFalse(chatGroupRepository.isUserInAnyGroup(testUserId1))
            val members = chatGroupRepository.getChatGroupMembers(chatGroup.id)
            assertEquals(1, members.size)
            assertTrue(members.contains(testOwnerId))
        }
    }

    @Test
    fun `addChatMessage and getChatMessages should work correctly`() = runBlocking {
        val chatGroup = newSuspendedTransaction {
            chatGroupRepository.createChatGroup(testOwnerId)
        }
        assertNotNull(chatGroup)
        newSuspendedTransaction { chatGroupRepository.joinChatGroup(chatGroup.id, testUserId1, 8) }

        val ownerUsername = "Owner"
        val ownerAvatar = "avatar_url_owner"
        val user1Username = "UserOne"
        val user1Avatar = "avatar_url_user1"

        val msg1 = newSuspendedTransaction { chatGroupRepository.addChatMessage(chatGroup.id, testOwnerId, "Message 1", ownerUsername, ownerAvatar) }
        val msg2 = newSuspendedTransaction { chatGroupRepository.addChatMessage(chatGroup.id, testUserId1, "Message 2", user1Username, user1Avatar) }
        val msg3 = newSuspendedTransaction { chatGroupRepository.addChatMessage(chatGroup.id, testOwnerId, "Message 3", ownerUsername, ownerAvatar) }

        assertNotNull(msg1)
        assertNotNull(msg2)
        assertNotNull(msg3)

        newSuspendedTransaction {
            val allMessages = chatGroupRepository.getChatMessages(chatGroup.id, 10, null)
            assertEquals(3, allMessages.size)
            assertEquals(msg1.content, allMessages[0].content)
            assertEquals(msg2.content, allMessages[1].content)
            assertEquals(msg3.content, allMessages[2].content)
            assertEquals(ownerUsername, allMessages[0].username)
            assertEquals(ownerAvatar, allMessages[0].avatar)
            assertEquals(user1Username, allMessages[1].username)
            assertEquals(user1Avatar, allMessages[1].avatar)

            val messagesBeforeMsg3 = chatGroupRepository.getChatMessages(chatGroup.id, 10, msg3.id)
            assertEquals(2, messagesBeforeMsg3.size)
            assertEquals(msg1.content, messagesBeforeMsg3[0].content)
            assertEquals(msg2.content, messagesBeforeMsg3[1].content)

            val messagesLimit1 = chatGroupRepository.getChatMessages(chatGroup.id, 1, null)
            assertEquals(1, messagesLimit1.size)
            assertEquals(msg3.content, messagesLimit1[0].content)
        }
    }

    @Test
    fun `getChatGroupForUser should return group if user is member`() = runBlocking {
        val chatGroup = newSuspendedTransaction {
            chatGroupRepository.createChatGroup(testOwnerId)
        }
        assertNotNull(chatGroup)
        newSuspendedTransaction { chatGroupRepository.joinChatGroup(chatGroup.id, testUserId1, 8) }

        val groupForOwner = newSuspendedTransaction { chatGroupRepository.getChatGroupForUser(testOwnerId) }
        assertNotNull(groupForOwner)
        assertEquals(chatGroup.id, groupForOwner.id)

        val groupForMember = newSuspendedTransaction { chatGroupRepository.getChatGroupForUser(testUserId1) }
        assertNotNull(groupForMember)
        assertEquals(chatGroup.id, groupForMember.id)

        val groupForNonMember = newSuspendedTransaction { chatGroupRepository.getChatGroupForUser(testUserId2) }
        assertNull(groupForNonMember)
    }

    @Test
    fun `getChatGroupMemberCount should return correct count`() = runBlocking {
        val chatGroup = newSuspendedTransaction {
            chatGroupRepository.createChatGroup(testOwnerId)
        }
        assertNotNull(chatGroup)
        newSuspendedTransaction {
            assertEquals(1, chatGroupRepository.getChatGroupMemberCount(chatGroup.id))
        }
        newSuspendedTransaction { chatGroupRepository.joinChatGroup(chatGroup.id, testUserId1, 8) }
        newSuspendedTransaction {
            assertEquals(2, chatGroupRepository.getChatGroupMemberCount(chatGroup.id))
        }
    }
}
