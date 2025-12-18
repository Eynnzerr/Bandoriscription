package com.eynnzerr.model

import kotlinx.serialization.Serializable

/**
 * WebSocket请求消息
 * 实际上按bandoriscription的原接口设计，请求/响应使用相同类即可，但为客户端能复用websocket客户端，此处复用车站服务端接口定义
 * @param action 操作名称
 * @param data 请求数据，可以是任意类型或null
 */
@Serializable
data class WebSocketRequest<T>(
    val action: String,
    val data: T? = null
)

/**
 * WebSocket响应消息
 * @param status 响应状态："success"或"failure"
 * @param action 操作名称
 * @param response 响应数据，可以是任意类型
 */
@Serializable
data class WebSocketResponse<T>(
    val status: String,
    val action: String,
    val response: T
)

/**
 * 车牌查看请求数据体，用于请求者的上传参数和发给房主的响应
 * @param requestId 请求的唯一标识符，由app端通过UUID生成
 * @param targetUserId 待查看车牌的房主车站ID
 * @param requesterId 发起查看的用户车站ID
 * @param requesterName 发起查看的用户名称
 * @param requesterAvatar 发起查看的用户头像
 */
@Serializable
data class RoomAccessRequest(
    val requestId: String,
    val targetUserId: String,
    val requesterId: String,
    val requesterName: String,
    val requesterAvatar: String,
)

/**
 * 车牌查看回复数据体，用于房主的上传参数和发给请求者的响应
 * @param requestId 请求的唯一标识符，上传时填写原请求的id
 * @param approved 是否同意查看车牌
 * @param roomNumber 真实车牌号，上传时不携带或填写空
 * @param message 其它消息（如拒绝提示），上传时不携带或填写空
 */
@Serializable
data class RoomAccessResponse(
    val requestId: String,
    val approved: Boolean,
    val roomNumber: String? = null,
    val message: String? = null,
)

object WebSocketActions {
    // 车牌加密相关
    const val REQUEST_ACCESS = "request_access" // app -> server 用户发起车牌查看请求时调用
    const val RESPOND_ACCESS = "respond_access" // app -> server 房主收到查看请求且批准/拒绝后调用
    const val ACCESS_REQUEST_RECEIVED = "access_request_received" // server -> app 向房主发送某用户的查看请求
    const val ACCESS_RESULT = "access_result" // server -> app 向用户发送房主批复结果
    const val ERROR = "error" // server -> app 调用错误

    // 车内聊天相关
    const val SEND_CHAT_MESSAGE = "send_chat_message" // app -> server 用户发送消息到聊天群
    const val NEW_CHAT_MESSAGE = "new_chat_message" // server -> app 用户接收聊天群新消息
    const val USER_JOINED_CHAT = "user_joined_chat" // server -> app 用户接收新用户加入通知
    const val USER_LEFT_CHAT = "user_left_chat" // server -> app 用户接收其他用户离开通知
    const val USER_REMOVED_FROM_CHAT = "user_removed_from_chat" // server -> app 用户接收其他用户被移出通知
    const val CHAT_DISBANDED = "chat_disbanded" // server -> app 用户接收聊天室解散通知
    const val NEW_OWNER_ASSIGNED = "new_owner_assigned" // server -> app 用户被指派为新房主
    const val OWNER_CHANGED = "owner_changed" // server -> app 房主变更通知
    const val CHAT_STATE_SYNC = "chat_state_sync" // server -> app 用户重连后同步当前聊天室状态
    const val CHAT_GROUP_CHANGE = "new_chat_group" // server -> app 有聊天室状态发生改变时推送
}

@Serializable
data class ChatStateSyncPayload(
    val groupId: String,
    val ownerId: String,
    val name: String,
    val members: List<UserInfo>,
    val recentMessages: List<ChatMessageInfo>
)

@Serializable
data class NewOwnerPayload(
    val groupId: String,
    val newOwnerId: String
)

@Serializable
data class SendChatMessageRequest(
    val content: String,
    val username: String,
    val avatar: String,
)

@Serializable
data class NewChatMessagePayload(
    val groupId: String,
    val message: ChatMessageInfo
)

@Serializable
data class ChatMessageInfo(
    val id: Long,
    val senderId: String,
    val content: String,
    val username: String,
    val avatar: String,
    val createdAt: String // ISO 8601 format
)

@Serializable
data class UserChatPayload(
    val groupId: String,
    val user: OwnerInfo,
)

@Serializable
data class ChatDisbandedPayload(val groupId: String)