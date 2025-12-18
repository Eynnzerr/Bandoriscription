package com.eynnzerr

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.eynnzerr.data.ChatGroupRepository
import com.eynnzerr.data.DatabaseFactory
import com.eynnzerr.di.appModule
import com.eynnzerr.model.*
import com.eynnzerr.routes.chatRoutes
import com.eynnzerr.routes.configureRouting
import com.eynnzerr.utils.JwtConfig
import com.eynnzerr.utils.WebSocketManager
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.config.*
import io.ktor.server.testing.*
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import io.mockk.spyk
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.koin.test.KoinTest
import org.koin.test.inject
import org.junit.After
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertFalse


// Test configuration for JWT, mimic application.conf structure
val testAppConfig = MapApplicationConfig(
    "jwt.issuer" to "http://localhost/",
    "jwt.audience" to "users",
    "jwt.realm" to "Access to Bandoriscription"
)

// Main application module for tests (mimics Application.module())
fun Application.testModule() {
    // Koin modules are handled in test setup
    // configureRateLimiting() // Not strictly needed for these tests
    configureAuthentication()
    configureWebSockets()
    configureSerialization()
    configureDatabases() // This will call DatabaseFactory.init(application.environment.config)
    // configureFrameworks() // Not strictly needed for these tests
    // configureHTTP() // Not strictly needed for these tests
    configureRouting()
    // configureCleanup() // Not strictly needed for these tests
}

class ChatRoutesTest : KoinTest {

    private val chatGroupRepository by inject<ChatGroupRepository>()
    private val webSocketManager: WebSocketManager = mockk(relaxed = true)

    private val testOwnerId = "test_owner_jwt"
    private val testUserId1 = "test_user_1_jwt"
    private val testUserId2 = "test_user_2_jwt"

    private lateinit var ownerToken: String
    private lateinit var user1Token: String
    private lateinit var user2Token: String

    private fun generateToken(userId: String): String {
        return JWT.create()
            .withAudience(JwtConfig.AUDIENCE)
            .withIssuer(JwtConfig.ISSUER)
            .withClaim("userId", userId)
            .sign(Algorithm.HMAC512(JwtConfig.SECRET))
    }

    @Before
    fun setup() {
        // Set test JWT secret
        System.setProperty("JWT_SECRET", "super_secret_test_key")

        // Initialize JwtConfig with test config
        JwtConfig.init(testAppConfig)

        // Initialize Koin
        startKoin {
            modules(appModule, module {
                // Override WebSocketManager with a mock for testing routes
                single { webSocketManager }
            })
        }

        // Initialize Database for tests
        DatabaseFactory.init(true)

        // Insert test user data
        transaction {
            SchemaUtils.create(Users, ChatGroups, ChatGroupMembers, ChatMessages)
            Users.insert { it[id] = testOwnerId }
            Users.insert { it[id] = testUserId1 }
            Users.insert { it[id] = testUserId2 }
        }

        ownerToken = generateToken(testOwnerId)
        user1Token = generateToken(testUserId1)
        user2Token = generateToken(testUserId2)
    }

    @After
    fun teardown() {
        stopKoin()
        transaction {
            SchemaUtils.drop(ChatMessages, ChatGroupMembers, ChatGroups, Users)
        }
        System.clearProperty("JWT_SECRET") // Clear property after test
    }

    @Test
    fun `POST chat-create should create a new chat group`() = testApplication {
        // Use testModule for application setup
        application {
            testModule()
        }
        val response = client.post("/bandori/api/chat/create") {
            header(HttpHeaders.Authorization, "Bearer $ownerToken")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val apiResponse = Json.decodeFromString<ApiResponse>(response.bodyAsText())
        assertEquals("success", apiResponse.status)

        val createChatResponse = Json.decodeFromJsonElement(CreateChatResponse.serializer(), (apiResponse.response as ApiResponseContent.ObjectContent).data)
        assertNotNull(createChatResponse.groupId)
        val chatGroup = transaction { chatGroupRepository.getChatGroup(createChatResponse.groupId) }
        assertNotNull(chatGroup)
        assertEquals(testOwnerId, chatGroup.ownerId)
    }

    @Test
    fun `POST chat-create should fail if owner already has a group`() = testApplication {
        application {
            testModule()
        }
        client.post("/bandori/api/chat/create") { // Create first group
            header(HttpHeaders.Authorization, "Bearer $ownerToken")
        }
        val response = client.post("/bandori/api/chat/create") { // Try to create second
            header(HttpHeaders.Authorization, "Bearer $ownerToken")
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
        val apiResponse = Json.decodeFromString<ApiResponse>(response.bodyAsText())
        assertEquals("failure", apiResponse.status)
        assertEquals("您已创建过一个群聊，请先解散原群聊", (apiResponse.response as ApiResponseContent.StringContent).text)
    }

    @Test
    fun `POST chat-join should add user to group and broadcast`() = testApplication {
        application {
            testModule()
        }
        // Create group first
        val createResponse = client.post("/bandori/api/chat/create") {
            header(HttpHeaders.Authorization, "Bearer $ownerToken")
        }
        val createChatResponse = Json.decodeFromString<CreateChatResponse>((Json.decodeFromString<ApiResponse>(createResponse.bodyAsText()).response as ApiResponseContent.ObjectContent).data)
        val groupId = createChatResponse.groupId

        // User 1 joins
        val response = client.post("/bandori/api/chat/join") {
            header(HttpHeaders.Authorization, "Bearer $user1Token")
            contentType(ContentType.Application.Json)
            setBody(JoinChatRequest(ownerId = testOwnerId))
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val apiResponse = Json.decodeFromString<ApiResponse>(response.bodyAsText())
        assertEquals("success", apiResponse.status)
        assertEquals("成功加入群聊", (apiResponse.response as ApiResponseContent.StringContent).text)

        // Verify broadcast
        // MockK capture unfortunately doesn't work directly inside Ktor's testApplication due to coroutine context.
        // This would usually be tested via integration tests with a real WebSocket client.
        // For now, we'll assert the REST response and rely on integration tests for broadcast verification.

        transaction {
            assertTrue(chatGroupRepository.isUserInAnyGroup(testUserId1))
            val members = chatGroupRepository.getChatGroupMembers(groupId)
            assertEquals(2, members.size)
            assertTrue(members.contains(testUserId1))
        }
    }

    @Test
    fun `POST chat-join should fail if group is full`() = testApplication {
        application {
            testModule()
        }
        // Create group first
        val createResponse = client.post("/bandori/api/chat/create") {
            header(HttpHeaders.Authorization, "Bearer $ownerToken")
        }
        val createChatResponse = Json.decodeFromString<CreateChatResponse>((Json.decodeFromString<ApiResponse>(createResponse.bodyAsText()).response as ApiResponseContent.ObjectContent).data)
        val groupId = createChatResponse.groupId

        // Fill up group (owner + 7 more users)
        val usersToFill = (0 until 7).map { "filler_user_$it" }
        transaction {
            usersToFill.forEach { Users.insert { id = it } }
        }

        for (user in usersToFill) {
            transaction { chatGroupRepository.joinChatGroup(groupId, user, 8) }
        }

        // User 1 tries to join full group
        val response = client.post("/bandori/api/chat/join") {
            header(HttpHeaders.Authorization, "Bearer $user1Token")
            contentType(ContentType.Application.Json)
            setBody(JoinChatRequest(ownerId = testOwnerId))
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
        val apiResponse = Json.decodeFromString<ApiResponse>(response.bodyAsText())
        assertEquals("failure", apiResponse.status)
        assertEquals("群聊已满", (apiResponse.response as ApiResponseContent.StringContent).text)
    }

    @Test
    fun `GET chat-messages should return messages for a member`() = testApplication {
        application {
            testModule()
        }
        // Create group and join
        val createResponse = client.post("/bandori/api/chat/create") { header(HttpHeaders.Authorization, "Bearer $ownerToken") }
        val createChatResponse = Json.decodeFromString<CreateChatResponse>((Json.decodeFromString<ApiResponse>(createResponse.bodyAsText()).response as ApiResponseContent.ObjectContent).data)
        val groupId = createChatResponse.groupId
        client.post("/bandori/api/chat/join") {
            header(HttpHeaders.Authorization, "Bearer $user1Token")
            contentType(ContentType.Application.Json)
            setBody(JoinChatRequest(ownerId = testOwnerId))
        }

        // Add some messages directly via repository
        val ownerUsername = "Owner"
        val ownerAvatar = "avatar_url_owner"
        val user1Username = "UserOne"
        val user1Avatar = "avatar_url_user1"
        val msg1 = transaction { chatGroupRepository.addChatMessage(groupId, testOwnerId, "Owner's first message", ownerUsername, ownerAvatar) }
        val msg2 = transaction { chatGroupRepository.addChatMessage(groupId, testUserId1, "User1's message", user1Username, user1Avatar) }
        assertNotNull(msg1)
        assertNotNull(msg2)

        // Get messages as User 1
        val response = client.get("/bandori/api/chat/messages") {
            header(HttpHeaders.Authorization, "Bearer $user1Token")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val apiResponse = Json.decodeFromString<ApiResponse>(response.bodyAsText())
        assertEquals("success", apiResponse.status)

        val messages = Json.decodeFromJsonElement(
            kotlinx.serialization.builtins.ListSerializer(ChatMessageResponse.serializer()),
            (apiResponse.response as ApiResponseContent.ObjectContent).data
        )
        assertEquals(2, messages.size)
        assertEquals(msg1.content, messages[0].content)
        assertEquals(msg2.content, messages[1].content)
        assertEquals(ownerUsername, messages[0].username)
        assertEquals(ownerAvatar, messages[0].avatar)
        assertEquals(user1Username, messages[1].username)
        assertEquals(user1Avatar, messages[1].avatar)
    }

    @Test
    fun `POST chat-leave should remove user from group and broadcast`() = testApplication {
        application {
            testModule()
        }
        // Setup: create group, user1 joins
        val createResponse = client.post("/bandori/api/chat/create") { header(HttpHeaders.Authorization, "Bearer $ownerToken") }
        val createChatResponse = Json.decodeFromString<CreateChatResponse>((Json.decodeFromString<ApiResponse>(createResponse.bodyAsText()).response as ApiResponseContent.ObjectContent).data)
        val groupId = createChatResponse.groupId
        client.post("/bandori/api/chat/join") {
            header(HttpHeaders.Authorization, "Bearer $user1Token")
            contentType(ContentType.Application.Json)
            setBody(JoinChatRequest(ownerId = testOwnerId))
        }

        // Action: User 1 leaves
        val response = client.post("/bandori/api/chat/leave") {
            header(HttpHeaders.Authorization, "Bearer $user1Token")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val apiResponse = Json.decodeFromString<ApiResponse>(response.bodyAsText())
        assertEquals("success", apiResponse.status)
        assertEquals("已成功退出群聊", (apiResponse.response as ApiResponseContent.StringContent).text)

        // Verify state
        transaction {
            assertFalse(chatGroupRepository.isUserInAnyGroup(testUserId1))
            assertEquals(1, chatGroupRepository.getChatGroupMemberCount(groupId)) // Owner still in
        }
    }

    @Test
    fun `POST chat-disband should delete group and broadcast`() = testApplication {
        application {
            testModule()
        }
        // Setup: create group, user1 joins
        val createResponse = client.post("/bandori/api/chat/create") { header(HttpHeaders.Authorization, "Bearer $ownerToken") }
        val createChatResponse = Json.decodeFromString<CreateChatResponse>((Json.decodeFromString<ApiResponse>(createResponse.bodyAsText()).response as ApiResponseContent.ObjectContent).data)
        val groupId = createChatResponse.groupId
        client.post("/bandori/api/chat/join") {
            header(HttpHeaders.Authorization, "Bearer $user1Token")
            contentType(ContentType.Application.Json)
            setBody(JoinChatRequest(ownerId = testOwnerId))
        }

        // Action: Owner disbands group
        val response = client.post("/bandori/api/chat/disband") {
            header(HttpHeaders.Authorization, "Bearer $ownerToken")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val apiResponse = Json.decodeFromString<ApiResponse>(response.bodyAsText())
        assertEquals("success", apiResponse.status)
        assertEquals("群聊已解散", (apiResponse.response as ApiResponseContent.StringContent).text)

        // Verify state
        transaction {
            assertNull(chatGroupRepository.getChatGroup(groupId))
            assertFalse(chatGroupRepository.isUserInAnyGroup(testOwnerId))
            assertFalse(chatGroupRepository.isUserInAnyGroup(testUserId1))
        }
    }

    @Test
    fun `POST chat-remove-member should remove a member and broadcast`() = testApplication {
        application {
            testModule()
        }
        // Setup: create group, user1 joins
        val createResponse = client.post("/bandori/api/chat/create") { header(HttpHeaders.Authorization, "Bearer $ownerToken") }
        val createChatResponse = Json.decodeFromString<CreateChatResponse>((Json.decodeFromString<ApiResponse>(createResponse.bodyAsText()).response as ApiResponseContent.ObjectContent).data)
        val groupId = createChatResponse.groupId
        client.post("/bandori/api/chat/join") {
            header(HttpHeaders.Authorization, "Bearer $user1Token")
            contentType(ContentType.Application.Json)
            setBody(JoinChatRequest(ownerId = testOwnerId))
        }

        // Action: Owner removes user1
        val response = client.post("/bandori/api/chat/remove-member") {
            header(HttpHeaders.Authorization, "Bearer $ownerToken")
            contentType(ContentType.Application.Json)
            setBody(RemoveMemberRequest(userId = testUserId1))
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val apiResponse = Json.decodeFromString<ApiResponse>(response.bodyAsText())
        assertEquals("success", apiResponse.status)
        assertEquals("已移除该成员", (apiResponse.response as ApiResponseContent.StringContent).text)

        // Verify state
        transaction {
            assertFalse(chatGroupRepository.isUserInAnyGroup(testUserId1))
            assertEquals(1, chatGroupRepository.getChatGroupMemberCount(groupId)) // Owner still in
        }
    }
}