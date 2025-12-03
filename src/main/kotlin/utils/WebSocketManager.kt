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
                    session.send(Json.encodeToString(message))
                    true
                } catch (e: Exception) {
                    // Log error sending message
                    false
                }
            }
        } else {
            false // User not online
        }
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
