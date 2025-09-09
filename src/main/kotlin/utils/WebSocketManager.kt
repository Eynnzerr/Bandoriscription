package com.eynnzerr.utils

import com.eynnzerr.model.RoomAccessRequest
import com.eynnzerr.model.RoomAccessResponse
import com.eynnzerr.model.WebSocketActions
import com.eynnzerr.model.WebSocketResponse
import io.ktor.websocket.*
import kotlinx.coroutines.channels.Channel
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap

object WebSocketManager {
    private val connections = ConcurrentHashMap<String, WebSocketSession>()
    private val pendingRequests = ConcurrentHashMap<String, Channel<WebSocketResponse<RoomAccessResponse>>>()

    fun addConnection(userId: String, session: WebSocketSession) {
        connections[userId] = session
    }

    fun removeConnection(userId: String) {
        connections.remove(userId)
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
            targetSession.send(Json.encodeToString(requestMessage))
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
