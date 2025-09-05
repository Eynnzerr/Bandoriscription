# Bandoriscription - BandoriStation 防炸车房间号加密服务平台

一个用于游戏*《Band Dream! Girls Band Party!》*的拼车app[BandoriStation Mobile](https://github.com/Eynnzerr/BandoriStationMobile)的房间号加密服务，通过邀请码和授权机制防止恶意用户炸房。

## 功能特性

- **用户认证**：基于JWT的身份验证系统
- **房间加密**：房间号加密存储，需要授权才能访问
- **双重验证机制**：
    - 邀请码验证：用户可以设置邀请码，其他用户输入正确邀请码即可获取房间号
    - 实时授权：通过WebSocket实时请求房主授权
- **黑白名单**：
    - 黑名单用户自动拒绝
    - 白名单用户自动通过
- **实时通信**：WebSocket支持实时的授权请求和响应

## 技术栈

- Kotlin + Ktor
- Exposed (ORM)
- H2 Database
- JWT Authentication
- WebSocket

## API 文档

### 认证相关

#### 注册/登录
```http
POST /register
Content-Type: application/json

{
  "userId": "80920",
  "originalToken": "BandoriStation login接口返回的token"
}

Response:
{
  "token": "JWT token",
  "expiresIn": 604800000
}
```

### 房间管理

#### 上传房间信息
```http
POST /room/upload
Authorization: Bearer {token}
Content-Type: application/json

{
  "roomNumber": "123456"
}

Response:
{

}
```

#### 验证邀请码
```http
POST /room/verify-invite-code
Authorization: Bearer {token}
Content-Type: application/json

{
  "targetUserId": "roomOwner123",
  "inviteCode": "ABC123"
}

Response:
{
  "success": true,
  "roomInfo": {
    "roomNumber": "123456"
  }
}
```

#### 更新邀请码
```http
POST /room/invite-code
Authorization: Bearer {token}
Content-Type: application/json

{
  "inviteCode": "NEW123"
}
```

#### 删除房间
```http
POST /room/remove
Authorization: Bearer {token}
```

### 黑白名单管理

#### 添加黑名单
```http
POST /blacklist/{blockedUserId}
Authorization: Bearer {token}
```

#### 移除黑名单
```http
DELETE /blacklist/{blockedUserId}
Authorization: Bearer {token}
```

#### 添加白名单
```http
POST /whitelist/{allowedUserId}
Authorization: Bearer {token}
```

### WebSocket 实时授权

#### 连接
```
ws://localhost:8080/ws
Headers: Authorization: Bearer {token}
```

#### 请求访问房间
```json
{
  "type": "REQUEST_ACCESS",
  "payload": "{\"requestId\":\"req123\",\"requesterId\":\"user123\",\"targetUserId\":\"roomOwner123\",\"timestamp\":1234567890}"
}
```

#### 房主收到请求
```json
{
  "type": "ACCESS_REQUEST_RECEIVED",
  "payload": "{\"requestId\":\"req123\",\"requesterId\":\"user123\",\"targetUserId\":\"roomOwner123\",\"timestamp\":1234567890}"
}
```

#### 房主批准
```json
{
  "type": "APPROVE_ACCESS",
  "payload": "{\"requestId\":\"req123\",\"approved\":true}"
}
```

#### 房主拒绝
```json
{
  "type": "DENY_ACCESS",
  "payload": "{\"requestId\":\"req123\",\"approved\":false}"
}
```

## 部署指南

### 开发环境

1. 克隆项目
```bash
git clone https://github.com/yourusername/bandoriscription.git
cd bandoriscription
```

2. 配置环境变量（可选）
```bash
export JWT_SECRET=your-secret-key
export PORT=8080
```

3. 运行项目
```bash
./gradlew run
```

### 生产环境

1. 构建项目
```bash
./gradlew buildFatJar
```

2. 运行JAR
```bash
java -jar build/libs/bandoriscription-1.0.0-all.jar
```

### Docker部署

创建 `Dockerfile`:
```dockerfile
FROM openjdk:17-jdk-slim
WORKDIR /app
COPY build/libs/bandoriscription-1.0.0-all.jar app.jar
EXPOSE 8080
CMD ["java", "-jar", "app.jar"]
```

构建和运行：
```bash
docker build -t bandoriscription .
docker run -p 8080:8080 -e JWT_SECRET=your-secret-key bandoriscription
```

## 客户端集成示例

### Kotlin (Compose Multiplatform)

```kotlin
// 注册获取Token
suspend fun register(userId: String, originalToken: String): String {
    val response = httpClient.post("http://server:8080/register") {
        contentType(ContentType.Application.Json)
        setBody(UserRegisterRequest(userId, originalToken))
    }
    return response.body<UserRegisterResponse>().token
}

// WebSocket连接
val webSocketSession = httpClient.webSocketSession(
    method = HttpMethod.Get,
    host = "server",
    port = 8080,
    path = "/ws"
) {
    header("Authorization", "Bearer $token")
}

// 请求访问房间
suspend fun requestRoomAccess(targetUserId: String) {
    val request = RoomAccessRequest(
        requestId = UUID.randomUUID().toString(),
        requesterId = currentUserId,
        targetUserId = targetUserId,
        timestamp = System.currentTimeMillis()
    )
    
    val message = WebSocketMessage(
        type = MessageType.REQUEST_ACCESS,
        payload = Json.encodeToString(request)
    )
    
    webSocketSession.send(Json.encodeToString(message))
}
```

## TODO

1. [] **生产环境修改JWT密钥**
2. [] **使用HTTPS保护API通信**
3. [] **使用WSS保护WebSocket通信**
4. [] **实施请求频率限制防止暴力破解**
5. [] **定期清理过期的房间信息**
6. [] **考虑添加验证码机制**
7. [] **邀请码有效期**：设置邀请码过期时间
8. [] **访问日志**：记录所有访问请求便于审计
9. [] **批量管理**：支持批量添加/删除黑白名单
10. [] **统计功能**：房间访问统计、拒绝率等
11. [] **推送通知**：离线时的访问请求推送
