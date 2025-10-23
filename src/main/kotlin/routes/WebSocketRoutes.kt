package com.eynnzerr.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.eynnzerr.data.RoomRepository
import com.eynnzerr.data.UserRepository
import com.eynnzerr.model.*
import com.eynnzerr.utils.JwtConfig
import com.eynnzerr.utils.WebSocketManager
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.koin.ktor.ext.inject
import org.slf4j.LoggerFactory

fun Route.webSocketRoutes() {
    val logger = LoggerFactory.getLogger("WebSocketRoutes")
    val userRepository by inject<UserRepository>()
    val roomRepository by inject<RoomRepository>()

    webSocket("/connect") {
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
        logger.info("New connection for user {}", userId)

        try {
            incoming.consumeEach { frame ->
                if (frame is Frame.Text) {
                    val text = frame.readText()
                    logger.info("Received request from user {}: {}", userId, text)
                    val request = Json.decodeFromString<WebSocketRequest<JsonElement>>(text)

                    when (request.action) {
                        WebSocketActions.REQUEST_ACCESS -> {
                            val requestData = Json.decodeFromJsonElement(RoomAccessRequest.serializer(), request.data!!)

                            // 检查黑名单
                            if (userRepository.isInBlackList(requestData.targetUserId, userId)) {
                                val response = WebSocketResponse(
                                    status = "success",
                                    action = WebSocketActions.ACCESS_RESULT,
                                    response = RoomAccessResponse(
                                        requestId = requestData.requestId,
                                        approved = false,
                                        roomNumber = "",
                                        message = "您已被房主拉黑"
                                    )
                                )
                                send(Json.encodeToString(response))
                                return@consumeEach
                            }

                            // 检查白名单
                            if (userRepository.isInWhiteList(requestData.targetUserId, userId)) {
                                val roomInfo = roomRepository.getRoom(requestData.targetUserId)
                                val response = WebSocketResponse(
                                    status = "success",
                                    action = WebSocketActions.ACCESS_RESULT,
                                    response = RoomAccessResponse(
                                        requestId = requestData.requestId,
                                        approved = true,
                                        roomNumber = roomInfo?.roomNumber ?: "",
                                        message = "白名单用户自动通过"
                                    )
                                )
                                send(Json.encodeToString(response))
                                return@consumeEach
                            }

                            // 发送请求给目标用户
                            val responseChannel = WebSocketManager.sendAccessRequest(requestData.targetUserId, requestData)

                            // 等待响应，超时时间30秒
                            val response = withTimeoutOrNull(30000) {
                                responseChannel.receive()
                            }

                            if (response != null) {
                                send(Json.encodeToString(response))
                            } else {
                                val timeoutResponse = WebSocketResponse(
                                    status = "success",
                                    action = WebSocketActions.ACCESS_RESULT,
                                    response = RoomAccessResponse(
                                        requestId = requestData.requestId,
                                        approved = false,
                                        roomNumber = "",
                                        message = "请求超时，房主未响应"
                                    )
                                )
                                send(Json.encodeToString(timeoutResponse))
                            }
                        }

                        WebSocketActions.RESPOND_ACCESS -> {
                            val responseData = Json.decodeFromJsonElement(RoomAccessResponse.serializer(), request.data!!)
                            if (responseData.approved) {
                                val roomInfo = roomRepository.getRoom(userId)
                                val approvedResponse = WebSocketResponse(
                                    status = "success",
                                    action = WebSocketActions.ACCESS_RESULT,
                                    response = responseData.copy(
                                        approved = true,
                                        roomNumber = roomInfo?.roomNumber ?: "",
                                        message = "房主同意了你的请求"
                                    )
                                )
                                WebSocketManager.handleAccessResponse(responseData.requestId, approvedResponse)
                            } else {
                                val deniedResponse = WebSocketResponse(
                                    status = "success",
                                    action = WebSocketActions.ACCESS_RESULT,
                                    response = responseData.copy(
                                        approved = false,
                                        message = "房主拒绝了您的请求"
                                    )
                                )
                                WebSocketManager.handleAccessResponse(responseData.requestId, deniedResponse)
                            }
                        }

                        else -> {
                            val errorResponse = WebSocketResponse(
                                status = "failure",
                                action = WebSocketActions.ERROR,
                                response = "不支持的操作"
                            )
                            send(Json.encodeToString(errorResponse))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            logger.error("WebSocket error for user $userId: ${e.localizedMessage}")
        } finally {
            WebSocketManager.removeConnection(userId)
        }
    }
}
