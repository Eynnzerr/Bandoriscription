package com.eynnzerr.routes

import com.eynnzerr.data.ChatGroupRepository
import com.eynnzerr.model.*
import com.eynnzerr.utils.respondFailure
import com.eynnzerr.utils.respondSuccess
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject
import com.eynnzerr.utils.WebSocketManager

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

                val request = call.receive<CreateChatRequest>()
                val chatGroup = chatGroupRepository.createChatGroup(userId, request)
                if (chatGroup != null) {
                    // 广播新群聊信息
                    val syncMessage = WebSocketResponse(
                        status = "success",
                        action = WebSocketActions.CHAT_GROUP_CHANGE,
                        response = ChatGroupChange(
                            chatGroups = listOf(
                                ChatGroupDetails(
                                    id = chatGroup.id,
                                    name = chatGroup.name,
                                    owner = OwnerInfo(
                                        id = userId,
                                        name = request.ownerName,
                                        avatar = request.ownerAvatar,
                                    ),
                                    memberCount = 1,
                                    createdAt = chatGroup.createdAt,
                                    lastActivityAt = chatGroup.lastActivityAt,
                                )
                            ),
                            changeStatus = GroupChangeStatus.UPSERTED,
                        )
                    )
                    webSocketManager.sendMessageToAll(syncMessage)

                    call.respondSuccess(CreateChatResponse(groupId = chatGroup.id))
                } else {
                    call.respondFailure("创建群聊失败")
                }
            }

            post("/all_groups") {
                val groups = chatGroupRepository.getAllChatGroupsWithDetails()
                call.respondSuccess(AllChatGroups(groups))
            }

            post("/group_of_user") {
                val request = call.receive<UserInfo>()

                val group = chatGroupRepository.getChatGroupForUser(request.id)
                if (group == null) {
                    call.respondFailure("目标用户未加入群聊")
                } else {
                    call.respondSuccess(UserInfo(id = group.ownerId))
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
                val success = chatGroupRepository.joinChatGroup(targetGroup.id, userId, MAX_GROUP_MEMBERS, request)
                if (success) {
                    // respond along with history messages.
                    val members = chatGroupRepository.getChatGroupMembers(targetGroup.id)
                    val memberInfos = members.map { UserInfo(id = it) }
                    val recentMessages = chatGroupRepository.getChatMessages(targetGroup.id, limit = 20, beforeMessageId = null)
                    val messageInfos = recentMessages.map {
                        ChatMessageInfo(
                            id = it.id,
                            senderId = it.userId,
                            content = it.content,
                            username = it.username,
                            avatar = it.avatar,
                            createdAt = it.createdAt
                        )
                    }
                    val syncPayload = ChatStateSyncPayload(
                        groupId = targetGroup.id,
                        ownerId = targetGroup.ownerId,
                        members = memberInfos,
                        recentMessages = messageInfos,
                        name = targetGroup.name,
                    )

                    call.respondSuccess(syncPayload)

                    // Broadcast to all group members that a new user joined
                    val joinNotification = WebSocketResponse(
                        status = "success",
                        action = WebSocketActions.USER_JOINED_CHAT,
                        response = UserChatPayload(
                            groupId = targetGroup.id,
                            user = OwnerInfo(
                                id = userId,
                                name = request.username,
                                avatar = request.avatar,
                            )
                        )
                    )
                    webSocketManager.broadcastToChatGroup(targetGroup.id, joinNotification)

                    // TODO 广播群人数变化
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

            post("/messages") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal!!.payload.getClaim("userId").asString()

                val request = call.receive<MessageRequest>()
                val limit = request.limit
                val beforeMessageId = request.before

                // Get the group the user is in
                val userGroup = chatGroupRepository.getChatGroupForUser(userId)
                if (userGroup == null) {
                    call.respondFailure("您当前不在任何群聊中")
                    return@post
                }

                val messages = chatGroupRepository.getChatMessages(userGroup.id, limit, beforeMessageId)
                val messageResponses = messages.map { chatMessage ->
                    ChatMessageResponse(
                        id = chatMessage.id,
                        senderId = chatMessage.userId,
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
                    call.respondSuccess("您当前已不在任何群聊中。")
                    return@post
                }

                val userInfo = chatGroupRepository.getUserSimpleInfo(userId)
                val isOwner = userGroup.ownerId == userId
                if (isOwner) {
                    // Owner is leaving
                    val members = chatGroupRepository.getChatGroupMembersSortedByJoinDate(userGroup.id)
                    if (members.size <= 1) {
                        // Owner is the last person, disband the group
                        chatGroupRepository.disbandChatGroup(userGroup.id)
                        val disbandNotification = WebSocketResponse(
                            status = "success",
                            action = WebSocketActions.CHAT_DISBANDED,
                            response = ChatDisbandedPayload(groupId = userGroup.id)
                        )
                        webSocketManager.broadcastToChatGroup(userGroup.id, disbandNotification)
                        call.respondSuccess("您是最后一名成员，群聊已自动解散")

                        // 广播群聊删除
                        val syncMessage = WebSocketResponse(
                            status = "success",
                            action = WebSocketActions.CHAT_GROUP_CHANGE,
                            response = ChatGroupChange(
                                chatGroups = listOf(
                                    ChatGroupDetails(
                                        id = userGroup.id, // 唯一会被客户端消费的字段
                                        name = userGroup.name,
                                        owner = OwnerInfo(
                                            id = "",
                                            name = "",
                                            avatar = "",
                                        ),
                                        memberCount = 0,
                                        createdAt = userGroup.createdAt,
                                        lastActivityAt = userGroup.lastActivityAt,
                                    )
                                ),
                                changeStatus = GroupChangeStatus.REMOVED,
                            )
                        )
                        webSocketManager.sendMessageToAll(syncMessage)

                    } else {
                        // More members exist, transfer ownership
                        val newOwnerId = members.first { it != userId } // Find the first member who is not the current owner
                        chatGroupRepository.updateGroupOwner(userGroup.id, newOwnerId)
                        chatGroupRepository.leaveChatGroup(userGroup.id, userId)

                        // Notify the new owner
                        val newOwnerNotification = WebSocketResponse(
                            status = "success",
                            action = WebSocketActions.NEW_OWNER_ASSIGNED,
                            response = NewOwnerPayload(groupId = userGroup.id, newOwnerId = newOwnerId)
                        )
                        webSocketManager.sendMessageToUser(newOwnerId, newOwnerNotification)

                        // Notify all members (including new owner) about the change
                        val ownerChangedNotification = WebSocketResponse(
                            status = "success",
                            action = WebSocketActions.OWNER_CHANGED,
                            response = NewOwnerPayload(groupId = userGroup.id, newOwnerId = newOwnerId)
                        )
                        webSocketManager.broadcastToChatGroup(userGroup.id, ownerChangedNotification)

                        call.respondSuccess("您已退出群聊，房主已转让")

                        // TODO 广播群聊人数减少
                    }
                } else {
                    // Normal member is leaving
                    val success = chatGroupRepository.leaveChatGroup(userGroup.id, userId)
                    if (success) {
                        // Broadcast to all group members that a user left
                        val leaveNotification = WebSocketResponse(
                            status = "success",
                            action = WebSocketActions.USER_LEFT_CHAT,
                            response = UserChatPayload(
                                groupId = userGroup.id,
                                user = OwnerInfo(
                                    id = userId,
                                    name = userInfo?.name ?: "",
                                    avatar = userInfo?.avatar ?: "",
                                )
                            )
                        )
                        webSocketManager.broadcastToChatGroup(userGroup.id, leaveNotification)

                        call.respondSuccess("已成功退出群聊")

                        // TODO 广播群聊人数减少
                    } else {
                        call.respondFailure("退出群聊失败")
                    }
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

                    // 广播群聊删除
                    val syncMessage = WebSocketResponse(
                        status = "success",
                        action = WebSocketActions.CHAT_GROUP_CHANGE,
                        response = ChatGroupChange(
                            chatGroups = listOf(
                                ChatGroupDetails(
                                    id = ownedGroup.id, // 唯一会被客户端消费的字段
                                    name = ownedGroup.name,
                                    owner = OwnerInfo(
                                        id = "",
                                        name = "",
                                        avatar = "",
                                    ),
                                    memberCount = 0,
                                    createdAt = ownedGroup.createdAt,
                                    lastActivityAt = ownedGroup.lastActivityAt,
                                )
                            ),
                            changeStatus = GroupChangeStatus.REMOVED,
                        )
                    )
                    webSocketManager.sendMessageToAll(syncMessage)
                } else {
                    call.respondFailure("解散群聊失败")
                }
            }

            post("/remove_member") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal!!.payload.getClaim("userId").asString()

                val request = call.receive<RemoveMemberRequest>()
                val memberToRemoveId = request.userId
                val userInfo = chatGroupRepository.getUserSimpleInfo(memberToRemoveId)

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
                        status = "failure",
                        action = WebSocketActions.ERROR, // Using ERROR for simplicity as it's a negative event for the user
                        response = "您已被移出群聊 ${ownedGroup.name}"
                    )
                    webSocketManager.sendMessageToUser(memberToRemoveId, removedNotificationToUser)

                    // Broadcast to remaining group members
                    val removedNotificationToGroup = WebSocketResponse(
                        status = "success",
                        action = WebSocketActions.USER_REMOVED_FROM_CHAT,
                        response = UserChatPayload(
                            groupId = ownedGroup.id,
                            user = OwnerInfo(
                                id = userInfo?.id ?: "",
                                name = userInfo?.name ?: "",
                                avatar = userInfo?.avatar ?: "",
                            )
                        )
                    )
                    webSocketManager.broadcastToChatGroup(ownedGroup.id, removedNotificationToGroup)

                    // TODO 广播群聊人数减少

                    call.respondSuccess("已移除该成员")
                } else {
                    call.respondFailure("移除成员失败")
                }
            }
        }
    }
}