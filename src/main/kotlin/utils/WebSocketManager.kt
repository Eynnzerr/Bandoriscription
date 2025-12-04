package com.eynnzerr.utils

import com.eynnzerr.model.RoomAccessRequest
import com.eynnzerr.model.RoomAccessResponse
import com.eynnzerr.model.WebSocketActions
import com.eynnzerr.model.WebSocketResponse
import io.ktor.websocket.*
import kotlinx.coroutines.channels.Channel
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap

import com.eynnzerr.data.ChatGroupRepository
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import org.slf4j.LoggerFactory

import com.eynnzerr.model.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.serializer

class WebSocketManager(private val chatGroupRepository: ChatGroupRepository) {
    private val connections = ConcurrentHashMap<String, WebSocketSession>()
    private val pendingRequests = ConcurrentHashMap<String, Channel<WebSocketResponse<RoomAccessResponse>>>()
    private val logger = LoggerFactory.getLogger("WebSocketManager")

    fun addConnection(userId: String, session: WebSocketSession) {
        connections[userId] = session
    }

    fun removeConnection(userId: String) {
        connections.remove(userId)
    }

    suspend fun sendMessageToUser(userId: String, message: WebSocketResponse<out Any>): Boolean {
        val session = connections[userId]
        return if (session != null) {
            withContext(Dispatchers.IO) {
                try {
                    val jsonString = encodeWebSocketResponse(message)
                    session.send(jsonString)
                    true
                } catch (e: Exception) {
                    logger.error("Error sending message to user id {}: {}", userId, e.message)
                    false
                }
            }
        } else {
            false // User not online
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T: Any> encodeWebSocketResponse(message: WebSocketResponse<T>): String {
        val serializer = when (val payload = message.response) {
            is NewChatMessagePayload -> NewChatMessagePayload.serializer() as KSerializer<T>
            is UserJoinedChatPayload -> UserJoinedChatPayload.serializer() as KSerializer<T>
            is UserLeftChatPayload -> UserLeftChatPayload.serializer() as KSerializer<T>
            is NewOwnerPayload -> NewOwnerPayload.serializer() as KSerializer<T>
            is ChatDisbandedPayload -> ChatDisbandedPayload.serializer() as KSerializer<T>
            is RoomAccessRequest -> RoomAccessRequest.serializer() as KSerializer<T>
            is RoomAccessResponse -> RoomAccessResponse.serializer() as KSerializer<T>
            is String -> String.serializer() as KSerializer<T>
            else -> throw IllegalArgumentException("Unknown payload type for WebSocketResponse: ${payload::class.simpleName}")
        }
        val responseSerializer = WebSocketResponse.serializer(serializer)
        return Json.encodeToString(responseSerializer, message)
    }

    suspend fun broadcastToChatGroup(groupId: String, message: WebSocketResponse<out Any>) {
        val members = chatGroupRepository.getChatGroupMembers(groupId)

        supervisorScope {
            members.forEach { userId ->
                launch(Dispatchers.IO) {
                    try {
                        sendMessageToUser(userId, message)
                    } catch (e: Exception) {
                        logger.error("Failed to send to $userId", e)
                    }
                }
            }
        }
    }

    suspend fun sendAccessRequest(targetUserId: String, request: RoomAccessRequest): Channel<WebSocketResponse<RoomAccessResponse>> {
        val responseChannel = Channel<WebSocketResponse<RoomAccessResponse>>(1)
        pendingRequests[request.requestId] = responseChannel

        val targetSession = connections[targetUserId]
        if (targetSession != null) {
            val requestMessage = WebSocketResponse(
                status = "success",
                action = WebSocketActions.ACCESS_REQUEST_RECEIVED,
                response = request
            )
            sendMessageToUser(targetUserId, requestMessage)
        } else {
            responseChannel.send(
                WebSocketResponse(
                    status = "failure",
                    action = WebSocketActions.ACCESS_RESULT,
                    response = RoomAccessResponse(
                        requestId = request.requestId,
                        approved = false,
                        message = "目标用户不在线"
                    )
                )
            )
            pendingRequests.remove(request.requestId)
        }

        return responseChannel
    }

    suspend fun handleAccessResponse(requestId: String, response: WebSocketResponse<RoomAccessResponse>) {
        val channel = pendingRequests.remove(requestId)
        channel?.send(response)
    }

    fun isUserOnline(userId: String): Boolean = connections.containsKey(userId)
}
