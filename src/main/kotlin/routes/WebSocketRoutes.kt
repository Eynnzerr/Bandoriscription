package com.eynnzerr.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.eynnzerr.data.RoomRepository
import com.eynnzerr.data.UserRepository
import com.eynnzerr.model.MessageType
import com.eynnzerr.model.RoomAccessRequest
import com.eynnzerr.model.RoomAccessResponse
import com.eynnzerr.model.WebSocketMessage
import com.eynnzerr.utils.JwtConfig
import com.eynnzerr.utils.WebSocketManager
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json

fun Route.webSocketRoutes() {
    val userRepository = UserRepository()
    val roomRepository = RoomRepository()

    webSocket("/ws") {
        val token = call.request.headers["Authorization"]?.removePrefix("Bearer ")
        if (token == null) {
            close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "未授权"))
            return@webSocket
        }

        val userId = try {
            val verifier = JWT.require(Algorithm.HMAC256(JwtConfig.SECRET))
                .withAudience(JwtConfig.AUDIENCE)
                .withIssuer(JwtConfig.ISSUER)
                .build()
            val jwt = verifier.verify(token)
            jwt.getClaim("userId").asString()
        } catch (e: Exception) {
            close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Token无效"))
            return@webSocket
        }

        WebSocketManager.addConnection(userId, this)

        try {
            incoming.consumeEach { frame ->
                if (frame is Frame.Text) {
                    val text = frame.readText()
                    val message = Json.decodeFromString<WebSocketMessage>(text)

                    when (message.type) {
                        MessageType.REQUEST_ACCESS -> {
                            val request = Json.decodeFromString<RoomAccessRequest>(message.payload)

                            // 检查黑名单
                            if (userRepository.isInBlackList(request.targetUserId, userId)) {
                                val response = RoomAccessResponse(
                                    requestId = request.requestId,
                                    approved = false,
                                    message = "您已被房主拉黑"
                                )
                                send(Json.encodeToString(WebSocketMessage(
                                    type = MessageType.ACCESS_RESULT,
                                    payload = Json.encodeToString(response)
                                )))
                                return@consumeEach
                            }

                            // 检查白名单
                            if (userRepository.isInWhiteList(request.targetUserId, userId)) {
                                val roomInfo = roomRepository.getRoom(request.targetUserId)
                                val response = RoomAccessResponse(
                                    requestId = request.requestId,
                                    approved = true,
                                    roomInfo = roomInfo,
                                    message = "白名单用户自动通过"
                                )
                                send(Json.encodeToString(WebSocketMessage(
                                    type = MessageType.ACCESS_RESULT,
                                    payload = Json.encodeToString(response)
                                )))
                                return@consumeEach
                            }

                            // 发送请求给目标用户
                            val responseChannel = WebSocketManager.sendAccessRequest(request.targetUserId, request)

                            // 等待响应，超时时间30秒
                            val response = withTimeoutOrNull(30000) {
                                responseChannel.receive()
                            }

                            if (response != null) {
                                send(Json.encodeToString(WebSocketMessage(
                                    type = MessageType.ACCESS_RESULT,
                                    payload = Json.encodeToString(response)
                                )))
                            } else {
                                val timeoutResponse = RoomAccessResponse(
                                    requestId = request.requestId,
                                    approved = false,
                                    message = "请求超时，房主未响应"
                                )
                                send(Json.encodeToString(WebSocketMessage(
                                    type = MessageType.ACCESS_RESULT,
                                    payload = Json.encodeToString(timeoutResponse)
                                )))
                            }
                        }

                        MessageType.APPROVE_ACCESS -> {
                            val response = Json.decodeFromString<RoomAccessResponse>(message.payload)
                            val roomInfo = roomRepository.getRoom(userId)
                            val approvedResponse = response.copy(
                                approved = true,
                                roomInfo = roomInfo
                            )
                            WebSocketManager.handleAccessResponse(response.requestId, approvedResponse)
                        }

                        MessageType.DENY_ACCESS -> {
                            val response = Json.decodeFromString<RoomAccessResponse>(message.payload)
                            val deniedResponse = response.copy(
                                approved = false,
                                message = "房主拒绝了您的请求"
                            )
                            WebSocketManager.handleAccessResponse(response.requestId, deniedResponse)
                        }

                        else -> {
                            send(Json.encodeToString(WebSocketMessage(
                                type = MessageType.ERROR,
                                payload = "不支持的消息类型"
                            )))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            println("WebSocket error for user $userId: ${e.localizedMessage}")
        } finally {
            WebSocketManager.removeConnection(userId)
        }
    }
}
