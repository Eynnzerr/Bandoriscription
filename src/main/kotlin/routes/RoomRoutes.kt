package com.eynnzerr.routes

import com.eynnzerr.data.RoomRepository
import com.eynnzerr.data.UserRepository
import com.eynnzerr.model.RoomResponse
import com.eynnzerr.model.UpdateInviteCodeRequest
import com.eynnzerr.model.UploadRoomRequest
import com.eynnzerr.model.VerifyInviteCodeRequest
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.roomRoutes() {
    val userRepository = UserRepository()
    val roomRepository = RoomRepository()

    authenticate("auth-jwt") {
        route("/room") {
            post("/upload") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal!!.payload.getClaim("userId").asString()

                val request = call.receive<UploadRoomRequest>()
                val success = roomRepository.saveRoom(userId, request.roomInfo)

                if (success) {
                    call.respond(HttpStatusCode.OK, mapOf("message" to "房间信息已保存"))
                } else {
                    call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "保存失败"))
                }
            }

            post("/verify-invite-code") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal!!.payload.getClaim("userId").asString()

                val request = call.receive<VerifyInviteCodeRequest>()

                // 检查黑名单
                if (userRepository.isInBlackList(request.targetUserId, userId)) {
                    call.respond(HttpStatusCode.OK, RoomResponse(
                        success = false,
                        message = "您已被房主拉黑"
                    ))
                    return@post
                }

                val inviteCode = userRepository.getInviteCode(request.targetUserId)

                if (inviteCode == request.inviteCode) {
                    val roomInfo = roomRepository.getRoom(request.targetUserId)
                    if (roomInfo != null) {
                        call.respond(HttpStatusCode.OK, RoomResponse(
                            success = true,
                            roomInfo = roomInfo
                        )
                        )
                    } else {
                        call.respond(HttpStatusCode.OK, RoomResponse(
                            success = false,
                            message = "房间不存在"
                        ))
                    }
                } else {
                    call.respond(HttpStatusCode.OK, RoomResponse(
                        success = false,
                        message = "邀请码错误"
                    ))
                }
            }

            post("/invite-code") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal!!.payload.getClaim("userId").asString()

                val request = call.receive<UpdateInviteCodeRequest>()

                val success = userRepository.updateInviteCode(userId, request.inviteCode)

                if (success) {
                    call.respond(HttpStatusCode.OK, mapOf("message" to "邀请码已更新"))
                } else {
                    call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "更新失败"))
                }
            }

            post("/remove") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal!!.payload.getClaim("userId").asString()

                val success = roomRepository.deleteRoom(userId)

                if (success) {
                    call.respond(HttpStatusCode.OK, mapOf("message" to "房间已删除"))
                } else {
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "房间不存在"))
                }
            }
        }

        route("/blacklist") {
            post("/{blockedUserId}") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal!!.payload.getClaim("userId").asString()
                val blockedUserId = call.parameters["blockedUserId"] ?: return@post call.respond(HttpStatusCode.BadRequest)

                val success = userRepository.addToBlackList(userId, blockedUserId)

                if (success) {
                    call.respond(HttpStatusCode.OK, mapOf("message" to "已加入黑名单"))
                } else {
                    call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "操作失败"))
                }
            }

            post("/{blockedUserId}") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal!!.payload.getClaim("userId").asString()
                val blockedUserId = call.parameters["blockedUserId"] ?: return@post call.respond(HttpStatusCode.BadRequest)

                val success = userRepository.removeFromBlackList(userId, blockedUserId)

                if (success) {
                    call.respond(HttpStatusCode.OK, mapOf("message" to "已移出黑名单"))
                } else {
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "记录不存在"))
                }
            }
        }

        route("/whitelist") {
            post("/{allowedUserId}") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal!!.payload.getClaim("userId").asString()
                val allowedUserId = call.parameters["allowedUserId"] ?: return@post call.respond(HttpStatusCode.BadRequest)

                val success = userRepository.addToWhiteList(userId, allowedUserId)

                if (success) {
                    call.respond(HttpStatusCode.OK, mapOf("message" to "已加入白名单"))
                } else {
                    call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "操作失败"))
                }
            }
        }
    }
}
