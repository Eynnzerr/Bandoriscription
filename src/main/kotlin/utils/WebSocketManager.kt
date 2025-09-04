package com.eynnzerr.utils

import com.eynnzerr.model.MessageType
import com.eynnzerr.model.RoomAccessRequest
import com.eynnzerr.model.RoomAccessResponse
import com.eynnzerr.model.WebSocketMessage
import io.ktor.websocket.*
import kotlinx.coroutines.channels.Channel
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap

object WebSocketManager {
    private val connections = ConcurrentHashMap<String, WebSocketSession>()
    private val pendingRequests = ConcurrentHashMap<String, Channel<RoomAccessResponse>>()

    fun addConnection(userId: String, session: WebSocketSession) {
        connections[userId] = session
    }

    fun removeConnection(userId: String) {
        connections.remove(userId)
    }

    suspend fun sendAccessRequest(targetUserId: String, request: RoomAccessRequest): Channel<RoomAccessResponse> {
        val responseChannel = Channel<RoomAccessResponse>(1)
        pendingRequests[request.requestId] = responseChannel

        val targetSession = connections[targetUserId]
        if (targetSession != null) {
            val message = WebSocketMessage(
                type = MessageType.ACCESS_REQUEST_RECEIVED,
                payload = Json.encodeToString(request)
            )
            targetSession.send(Json.encodeToString(message))
        } else {
            responseChannel.send(
                RoomAccessResponse(
                    requestId = request.requestId,
                    approved = false,
                    message = "目标用户不在线"
                )
            )
            pendingRequests.remove(request.requestId)
        }

        return responseChannel
    }

    suspend fun handleAccessResponse(requestId: String, response: RoomAccessResponse) {
        val channel = pendingRequests.remove(requestId)
        channel?.send(response)
    }

    fun isUserOnline(userId: String): Boolean = connections.containsKey(userId)
}
