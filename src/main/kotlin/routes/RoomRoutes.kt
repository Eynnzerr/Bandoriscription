package com.eynnzerr.routes

import com.eynnzerr.data.RoomRepository
import com.eynnzerr.data.UserRepository
import com.eynnzerr.model.*
import com.eynnzerr.utils.respondFailure
import com.eynnzerr.utils.respondSuccess
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

fun Route.roomRoutes() {
    val userRepository by inject<UserRepository>()
    val roomRepository by inject<RoomRepository>()

    authenticate("auth-jwt") {
        route("/room") {
            post("/upload") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal!!.payload.getClaim("userId").asString()

                val request = call.receive<UploadRoomRequest>()
                val room = RoomInfo(
                    roomNumber = request.roomNumber,
                )
                val success = roomRepository.saveRoom(userId, room)

                if (success) {
                    call.respondSuccess("实际车牌已转存")
                } else {
                    call.respondFailure("实际车牌转存加密服务器失败")
                }
            }

            post("/verify-invite-code") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal!!.payload.getClaim("userId").asString()

                val request = call.receive<VerifyInviteCodeRequest>()

                // 检查黑名单
                if (userRepository.isInBlackList(request.targetUserId, userId)) {
                    call.respondFailure("您已被房主拉黑")
                    return@post
                }

                val inviteCode = userRepository.getInviteCode(request.targetUserId)

                if (inviteCode == request.inviteCode) {
                    val roomInfo = roomRepository.getRoom(request.targetUserId)
                    if (roomInfo != null) {
                        call.respondSuccess(RoomResponse(roomInfo.roomNumber))
                    } else {
                        call.respondFailure("房间不存在")
                    }
                } else {
                    call.respondFailure("邀请码错误")
                }
            }

            post("/invite-code") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal!!.payload.getClaim("userId").asString()

                val request = call.receive<UpdateInviteCodeRequest>()

                val success = userRepository.updateInviteCode(userId, request.inviteCode)

                if (success) {
                    call.respondSuccess("邀请码已更新")
                } else {
                    call.respondFailure("更新失败")
                }
            }

            post("/remove") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal!!.payload.getClaim("userId").asString()

                val success = roomRepository.deleteRoom(userId)

                if (success) {
                    call.respondSuccess("房间已删除")
                } else {
                    call.respondFailure("房间不存在")
                }
            }
        }

        route("/blacklist") {
            post("/add") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal!!.payload.getClaim("userId").asString()
                val request = call.receive<BlacklistRequest>()

                val success = userRepository.addToBlackList(userId, request.blockedUserId)

                if (success) {
                    call.respondSuccess("已加入黑名单")
                } else {
                    call.respondFailure("操作失败")
                }
            }

            post("/remove") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal!!.payload.getClaim("userId").asString()
                val request = call.receive<BlacklistRequest>()

                val success = userRepository.removeFromBlackList(userId, request.blockedUserId)

                if (success) {
                    call.respondSuccess("已移出黑名单")
                } else {
                    call.respondFailure("记录不存在")
                }
            }
        }

        route("/whitelist") {
            post("/add") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal!!.payload.getClaim("userId").asString()
                val request = call.receive<WhitelistRequest>()

                val success = userRepository.addToWhiteList(userId, request.allowedUserId)

                if (success) {
                    call.respondSuccess("已加入白名单")
                } else {
                    call.respondFailure("操作失败")
                }
            }

            post("/remove") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal!!.payload.getClaim("userId").asString()
                val request = call.receive<WhitelistRequest>()

                val success = userRepository.removeFromWhiteList(userId, request.allowedUserId)

                if (success) {
                    call.respondSuccess("已移出白名单")
                } else {
                    call.respondFailure("记录不存在")
                }
            }
        }

        route("/lists") {
            post {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal!!.payload.getClaim("userId").asString()

                val blacklist = userRepository.getBlacklist(userId)
                val whitelist = userRepository.getWhitelist(userId)

                call.respondSuccess(UserListsResponse(blacklist, whitelist))
            }
        }
    }
}
