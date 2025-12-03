# 私聊聊天室功能设计文档 (修订版)

TODO 新增同车聊天室功能，开发目标：

1. 用户在发车时可以勾选创建私聊群。
2. 用户点击标记上车后，同时将加入到该车车头创建的群中（如果有）
3. 群内可以互发消息（群外看不到）

具体逻辑：
- 用户A在发车时可勾选创建私聊群，随即自动加入自己创建的群组中。在app端表现为：右下角原发车FAB之上多出来一个FAB，点击后弹出modalBottomSheet就是聊天界面，可以查看消息，发送消息，房主可以管理群聊：移除成员、解散群聊
- 用户B在主页看到用户A所发的车牌后，可以点击卡片的标记上车按钮，在原有逻辑基础上，弹出对话框：该房主已创建群聊，是否加入。选择加入，则加入到群组中。在app端表现同上。同样可以查看和发送消息，但是非房主不能管理群聊，但可在聊天界面选择退出群聊。
- 私聊群组都是临时的，且一个群组最多支持8人；一个用户同时只能创建一个群组，若想创建新群组必须首先解散原群组； 一个用户同时只能加入一个群组，若想加入新群组必须首先退出原群组

本文档根据项目现有架构和代码风格进行修订，详细描述了私聊聊天室功能的数据库表结构、API接口和WebSocket事件。

---

## 1. 数据库设计 (Database Design)

为支持私聊聊天室功能，并与现有表结构 (`users.id` 为 `VARCHAR(128)`) 保持一致，新增以下三张表。

### 1.1. `chat_groups` (聊天群组表)

该表用于存储每个群聊的核心信息。群聊与用户（房主）是一对一的关系。

- **`id`**: `VARCHAR(36)` - 主键，UUID。
- **`owner_id`**: `VARCHAR(128)` - 外键，关联到 `users(id)`。**必须建立唯一约束 (UNIQUE)**，确保一个用户只能创建一个群聊。
- **`created_at`**: `TIMESTAMP` - 创建时间。

```sql
CREATE TABLE chat_groups (
    id VARCHAR(36) PRIMARY KEY,
    owner_id VARCHAR(128) NOT NULL UNIQUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (owner_id) REFERENCES users(id) ON DELETE CASCADE
);
```
*注：`ON DELETE CASCADE` 确保当用户被删除时，其拥有的群组也一并删除。*

### 1.2. `chat_group_members` (群组成员表)

该表维护群聊和成员之间的关系。

- **`group_id`**: `VARCHAR(36)` - 复合主键，外键，关联 `chat_groups(id)`。
- **`user_id`**: `VARCHAR(128)` - 复合主键，外键，关联 `users(id)`。**必须建立唯一约束 (UNIQUE)**，确保一个用户同一时间只能加入一个群。
- **`joined_at`**: `TIMESTAMP` - 加入时间。

```sql
CREATE TABLE chat_group_members (
    group_id VARCHAR(36) NOT NULL,
    user_id VARCHAR(128) NOT NULL,
    joined_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (group_id, user_id),
    UNIQUE (user_id),
    FOREIGN KEY (group_id) REFERENCES chat_groups(id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
```

### 1.3. `chat_messages` (聊天消息表)

该表用于持久化存储聊天消息。

- **`id`**: `BIGINT` - 主键，自增ID (`BIGSERIAL` for PostgreSQL)。
- **`group_id`**: `VARCHAR(36)` - 外键，关联 `chat_groups(id)`。
- **`user_id`**: `VARCHAR(128)` - 外键，关联 `users(id)`，消息发送者。
- **`content`**: `TEXT` - 消息内容。
- **`created_at`**: `TIMESTAMP` - 发送时间。

```sql
CREATE TABLE chat_messages (
    id BIGSERIAL PRIMARY KEY,
    group_id VARCHAR(36) NOT NULL,
    user_id VARCHAR(128) NOT NULL,
    content TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (group_id) REFERENCES chat_groups(id) ON DELETE CASCADE
);
```

---

## 2. API 接口设计 (RPC/Action-Style)

所有接口都在 `/bandori/api` 路径下，且需要JWT认证。响应格式遵循 `respondSuccess` 和 `respondFailure` 的封装。

### 2.1. 新增聊天相关接口

#### `POST /chat/create` - 创建一个新的聊天群组
- **Description**: 为当前用户创建一个新的私聊群组。一个用户只能拥有一个自己创建的群组。
- **Request Body**: 无。
- **业务逻辑**:
  1. 检查用户是否已创建过群组。如果存在，则返回错误。
  2. 创建新的 `chat_groups` 记录，并将用户设为 `owner_id`。
  3. 将创建者作为第一个成员添加到 `chat_group_members` 表中。
- **响应**: `call.respondSuccess(CreateChatResponse(groupId = "new-group-id"))` 或 `call.respondFailure("您已创建过一个群聊，请先解散原群聊")`。

#### `POST /chat/join` - 加入群聊
- **Description**: 用户请求加入一个房主创建的群聊。
- **Request Body**:
  ```kotlin
  // in model/Requests.kt
  @Serializable
  data class JoinChatRequest(val ownerId: String)
  ```
- **业务逻辑**:
  1. 检查 `ownerId` 是否创建了群聊。
  2. 检查当前用户是否已在其他群聊中。
  3. 检查群聊成员是否已达上限 (8人)。
  4. 通过则将用户加入成员列表。
- **响应**: `call.respondSuccess("成功加入群聊")` 或 `call.respondFailure("群聊不存在/已满/您已在其他群聊")`。

#### `GET /chat/messages` - 获取群聊历史消息
- **Description**: 获取用户所在群聊的聊天记录，支持分页。
- **Query Parameters**:
  - `limit`: `Int` (每页数量, e.g., 50)
  - `before`: `Long` (用于加载更早消息的锚点，对应 `chat_messages.id`)
- **响应**: `call.respondSuccess(List<ChatMessageResponse>)`

#### `POST /chat/leave` - 退出群聊
- **Description**: 当前用户主动退出所在的群聊。无请求体。
- **响应**: `call.respondSuccess("已成功退出群聊")`。

#### `POST /chat/disband` - 解散群聊
- **Description**: 仅群主可以执行此操作。无请求体。
- **响应**: `call.respondSuccess("群聊已解散")`。

#### `POST /chat/remove-member` - 移除成员
- **Description**: 仅群主可以执行此操作。
- **Request Body**:
  ```kotlin
  // in model/Requests.kt
  @Serializable
  data class RemoveMemberRequest(val userId: String)
  ```
- **响应**: `call.respondSuccess("已移除该成员")`。

---

## 3. WebSocket 事件设计

遵循现有 `WebSocketRequest<T>` 和 `WebSocketResponse<T>` 的结构。

### 3.1. 新增 WebSocket Actions
在 `model/WebSocket.kt` 的 `WebSocketActions` 对象中新增以下常量：
```kotlin
// in model/WebSocket.kt
object WebSocketActions {
    // ... existing actions
    const val SEND_CHAT_MESSAGE = "send_chat_message" // C -> S
    const val NEW_CHAT_MESSAGE = "new_chat_message" // S -> C
    const val USER_JOINED_CHAT = "user_joined_chat" // S -> C
    const val USER_LEFT_CHAT = "user_left_chat" // S -> C
    const val USER_REMOVED_FROM_CHAT = "user_removed_from_chat" // S -> C
    const val CHAT_DISBANDED = "chat_disbanded" // S -> C
}
```

### 3.2. WebSocket 载荷 (Payloads)

#### C -> S: `SEND_CHAT_MESSAGE`
- **Action**: `WebSocketActions.SEND_CHAT_MESSAGE`
- **Data (T)**:
  ```kotlin
  // in model/WebSocket.kt
  @Serializable
  data class SendChatMessageRequest(val content: String) // Group ID is known server-side based on user's membership
  ```
- **业务逻辑**: 服务器收到消息，存入数据库，然后向群内所有在线成员广播 `NEW_CHAT_MESSAGE`。

#### S -> C: `NEW_CHAT_MESSAGE`
- **Action**: `WebSocketActions.NEW_CHAT_MESSAGE`
- **Response (T)**:
  ```kotlin
  // in model/WebSocket.kt
  @Serializable
  data class NewChatMessagePayload(
      val groupId: String,
      val message: ChatMessageInfo
  )

  @Serializable
  data class ChatMessageInfo(
      val id: Long,
      val sender: UserInfo, // A new data class with id, nickname etc.
      val content: String,
      val createdAt: String // ISO 8601 format
  )
  ```

#### S -> C: `USER_JOINED_CHAT`
- **Action**: `WebSocketActions.USER_JOINED_CHAT`
- **Response (T)**:
  ```kotlin
  @Serializable
  data class UserJoinedChatPayload(
      val groupId: String,
      val user: UserInfo
  )
  ```

#### S -> C: `USER_LEFT_CHAT` / `USER_REMOVED_FROM_CHAT`
- **Action**: `WebSocketActions.USER_LEFT_CHAT` or `USER_REMOVED_FROM_CHAT`
- **Response (T)**:
  ```kotlin
  @Serializable
  data class UserLeftChatPayload(
      val groupId: String,
      val userId: String
  )
  ```

#### S -> C: `CHAT_DISBANDED`
- **Action**: `WebSocketActions.CHAT_DISBANDED`
- **Response (T)**:
  ```kotlin
  @Serializable
  data class ChatDisbandedPayload(val groupId: String)
  ```