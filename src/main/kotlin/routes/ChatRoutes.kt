package com.eynnzerr.routes

import com.eynnzerr.data.ChatGroupRepository
import com.eynnzerr.data.UserRepository
import com.eynnzerr.model.*
import com.eynnzerr.utils.respondFailure
import com.eynnzerr.utils.respondSuccess
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject
import java.util.UUID

import com.eynnzerr.utils.WebSocketManager // Import WebSocketManager

// Max members for a chat group as per requirement
private const val MAX_GROUP_MEMBERS = 8

fun Route.chatRoutes() {
    val chatGroupRepository by inject<ChatGroupRepository>()
    val webSocketManager by inject<WebSocketManager>()

    authenticate("auth-jwt") {
        route("/chat") {
            post("/create") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal!!.payload.getClaim("userId").asString()

                val existingGroup = chatGroupRepository.getChatGroupByOwnerId(userId)
                if (existingGroup != null) {
                    call.respondFailure("您已创建过一个群聊，请先解散原群聊")
                    return@post
                }

                val chatGroup = chatGroupRepository.createChatGroup(userId)
                if (chatGroup != null) {
                    call.respondSuccess(CreateChatResponse(groupId = chatGroup.id))
                } else {
                    call.respondFailure("创建群聊失败")
                }
            }

            post("/join") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal!!.payload.getClaim("userId").asString()

                val request = call.receive<JoinChatRequest>()
                val targetOwnerId = request.ownerId

                // Check if target owner has a group
                val targetGroup = chatGroupRepository.getChatGroupByOwnerId(targetOwnerId)
                if (targetGroup == null) {
                    call.respondFailure("目标用户未创建群聊")
                    return@post
                }

                // Check if user is already in any group
                if (chatGroupRepository.isUserInAnyGroup(userId)) {
                    call.respondFailure("您已在其他群聊中，请先退出")
                    return@post
                }

                // Try to join
                val success = chatGroupRepository.joinChatGroup(targetGroup.id, userId, MAX_GROUP_MEMBERS)
                if (success) {
                    // Broadcast to all group members that a new user joined
                    val joinNotification = WebSocketResponse(
                        status = "success",
                        action = WebSocketActions.USER_JOINED_CHAT,
                        response = UserJoinedChatPayload(groupId = targetGroup.id, user = UserInfo(id = userId))
                    )
                    webSocketManager.broadcastToChatGroup(targetGroup.id, joinNotification)

                    call.respondSuccess("成功加入群聊")
                } else {
                    // This could be due to group being full, or other issues not caught by previous checks
                    val memberCount = chatGroupRepository.getChatGroupMemberCount(targetGroup.id)
                    if (memberCount >= MAX_GROUP_MEMBERS) {
                        call.respondFailure("群聊已满")
                    } else {
                        call.respondFailure("加入群聊失败")
                    }
                }
            }

            get("/messages") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal!!.payload.getClaim("userId").asString()

                val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 50
                val beforeMessageId = call.request.queryParameters["before"]?.toLongOrNull()

                // Get the group the user is in
                val userGroup = chatGroupRepository.getChatGroupForUser(userId)
                if (userGroup == null) {
                    call.respondFailure("您当前不在任何群聊中")
                    return@get
                }

                val messages = chatGroupRepository.getChatMessages(userGroup.id, limit, beforeMessageId)
                val messageResponses = messages.map { chatMessage ->
                    ChatMessageResponse(
                        id = chatMessage.id,
                        sender = UserInfo(id = chatMessage.userId),
                        content = chatMessage.content,
                        username = chatMessage.username,
                        avatar = chatMessage.avatar,
                        createdAt = chatMessage.createdAt
                    )
                }
                call.respondSuccess(messageResponses)
            }

            post("/leave") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal!!.payload.getClaim("userId").asString()

                val userGroup = chatGroupRepository.getChatGroupForUser(userId)
                if (userGroup == null) {
                    call.respondFailure("您当前不在任何群聊中")
                    return@post
                }

                val success = chatGroupRepository.leaveChatGroup(userGroup.id, userId)
                if (success) {
                    // Broadcast to all group members that a user left
                    val leaveNotification = WebSocketResponse(
                        status = "success",
                        action = WebSocketActions.USER_LEFT_CHAT,
                        response = UserLeftChatPayload(groupId = userGroup.id, userId = userId)
                    )
                    webSocketManager.broadcastToChatGroup(userGroup.id, leaveNotification)

                    call.respondSuccess("已成功退出群聊")
                } else {
                    call.respondFailure("退出群聊失败")
                }
            }

            post("/disband") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal!!.payload.getClaim("userId").asString()

                // Check if user is owner of a group
                val ownedGroup = chatGroupRepository.getChatGroupByOwnerId(userId)
                if (ownedGroup == null) {
                    call.respondFailure("您没有创建群聊")
                    return@post
                }

                val success = chatGroupRepository.disbandChatGroup(ownedGroup.id)
                if (success) {
                    // Broadcast to all group members that the group was disbanded
                    val disbandNotification = WebSocketResponse(
                        status = "success",
                        action = WebSocketActions.CHAT_DISBANDED,
                        response = ChatDisbandedPayload(groupId = ownedGroup.id)
                    )
                    webSocketManager.broadcastToChatGroup(ownedGroup.id, disbandNotification)

                    call.respondSuccess("群聊已解散")
                } else {
                    call.respondFailure("解散群聊失败")
                }
            }

            post("/remove-member") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal!!.payload.getClaim("userId").asString()

                val request = call.receive<RemoveMemberRequest>()
                val memberToRemoveId = request.userId

                // Ensure current user is the owner
                val ownedGroup = chatGroupRepository.getChatGroupByOwnerId(userId)
                if (ownedGroup == null) {
                    call.respondFailure("您没有创建群聊，无权执行此操作")
                    return@post
                }
                if (memberToRemoveId == userId) {
                    call.respondFailure("您不能移除自己，请使用退出群聊功能")
                    return@post
                }

                val isMember = chatGroupRepository.getChatGroupMembers(ownedGroup.id).contains(memberToRemoveId)
                if (!isMember) {
                    call.respondFailure("该用户不在您的群聊中")
                    return@post
                }

                val success = chatGroupRepository.removeMemberFromChatGroup(ownedGroup.id, memberToRemoveId)
                if (success) {
                    // Notify removed user
                    val removedNotificationToUser = WebSocketResponse(
                        status = "failure", // Or a specific success type if needed for client handling
                        action = WebSocketActions.ERROR, // Using ERROR for simplicity as it's a negative event for the user
                        response = "您已被移出群聊 ${ownedGroup.id}"
                    )
                    webSocketManager.sendMessageToUser(memberToRemoveId, removedNotificationToUser)

                    // Broadcast to remaining group members
                    val removedNotificationToGroup = WebSocketResponse(
                        status = "success",
                        action = WebSocketActions.USER_REMOVED_FROM_CHAT,
                        response = UserLeftChatPayload(groupId = ownedGroup.id, userId = memberToRemoveId)
                    )
                    webSocketManager.broadcastToChatGroup(ownedGroup.id, removedNotificationToGroup)

                    call.respondSuccess("已移除该成员")
                } else {
                    call.respondFailure("移除成员失败")
                }
            }
        }
    }
}