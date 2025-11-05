# Bandoriscription - BandoriStation 车牌加密服务

本项目是为[BandoriStation Mobile](https://github.com/Eynnzerr/BandoriStationMobile)（ *《BanG Dream! Girls Band Party!》* 游戏的协力组队平台）设计的扩展后端服务。通过提供房间号（车牌）的加密存储和多重授权机制，旨在解决冲榜中因房间号公开而导致的“恶意炸车”问题。

## 核心功能

- **身份验证**: 用户通过 `BandoriStation` 的有效Token进行注册，向 `BandoriStation` 服务器验证Token的有效性，并签发一个用于本服务的JWT。
- **房间号加密与存储**: 用户上传房间号，服务器会对其进行加密存储。
- **多重访问控制**:
    - **邀请码**: 房主可以设置一个邀请码。知道邀请码的用户可以直接验证并获取房间号。
    - **实时授权**: 其他用户可以向房主发起实时请求，房主可以在客户端上选择同意或拒绝，请求者会立即收到结果。
- **黑白名单**: 房主可以管理自己的黑白名单。
    - **黑名单**: 自动拒绝来自黑名单用户的任何请求（包括邀请码和实时授权）。
    - **白名单**: 自动通过来自白名单用户的实时授权请求。
- **实时通信**: 基于WebSocket实现实时的授权请求、响应和结果通知。
- **定时清理**: 自动清理数据库中长期未更新的过期房间信息。
- **API限流**: 对API和WebSocket请求实施了频率限制，以防止暴力破解和滥用。

## 技术栈

- **后端框架**: [Ktor](https://ktor.io/)
- **语言**: [Kotlin](https://kotlinlang.org/)
- **数据库**: [PostgreSQL](https://www.postgresql.org/)
- **数据库访问**: [Exposed](https://github.com/JetBrains/Exposed) (ORM)
- **数据库迁移**: [Flyway](https://flywaydb.org/)
- **依赖注入**: [Koin](https://insert-koin.io/)
- **认证**: [JWT](https://jwt.io/)
- **异步通信**: [WebSockets](https://developer.mozilla.org/en-US/docs/Web/API/WebSockets_API)
- **部署**: [Docker](https://www.docker.com/) & [Docker Compose](https://docs.docker.com/compose/)

## 部署指南

### 准备工作

1.  **安装 Docker 和 Docker Compose**
    请确保您已安装最新版本的 [Docker Engine](https://docs.docker.com/engine/install/) 和 [Docker Compose](https://docs.docker.com/compose/install/)。

2.  **克隆项目**
    ```bash
    git clone https://github.com/Eynnzerr/Bandoriscription.git
    cd Bandoriscription
    ```

3.  **创建环境变量文件**
    复制 `.env.example` 文件来创建你自己的 `.env` 文件。
    ```bash
    cp .env.example .env
    ```
    然后，编辑 `.env` 文件并填入必要的环境变量：
    ```dotenv
    # 用于签发和验证JWT的密钥
    JWT_SECRET="your_strong_jwt_secret_here"

    # PostgreSQL 数据库的用户名和密码
    DB_USER="your_db_user"
    DB_PASSWORD="your_db_password"
    ```

### 启动服务

在项目根目录下，执行以下命令来构应用镜像：

```bash
docker build -t bandoriscription-app:latest .
```

执行以下命令来启动所有服务（应用和数据库）：

```bash
docker-compose up -d --build
```

服务启动后，应用将监听在 `18080` 端口。

### 服务管理

- **查看日志**:
  ```bash
  docker-compose logs -f app
  docker-compose logs -f db
  ```

- **停止服务**:
  ```bash
  docker-compose down
  ```

- **停止并删除数据卷** (这将删除所有数据库数据):
  ```bash
  docker-compose down -v
  ```

## API & WebSocket 概览

所有API和WebSocket路由都以 `/bandori` 为前缀。

### 健康检查

- `GET /health`: 返回 `200 OK`，用于服务健康状态检查。

### REST API (`/bandori/api`)

- **`POST /register`**: 使用 `BandoriStation` 的Token进行注册/登录，获取本服务的JWT。
- **`POST /room/upload`**: (需认证) 上传或更新房间号。
- **`POST /room/verify-invite-code`**: (需认证) 验证邀请码以获取房间号。
- **`POST /room/invite-code`**: (需认证) 设置或更新自己的邀请码。
- **`POST /room/remove`**: (需认证) 删除自己的房间信息。
- **`POST /blacklist/add` & `/blacklist/remove`**: (需认证) 添加或移除黑名单用户。
- **`POST /whitelist/add` & `/whitelist/remove`**: (需认证) 添加或移除白名单用户。
- **`POST /lists`**: (需认证) 获取自己的黑白名单列表。

### WebSocket API (`/bandori/ws`)

- **`/connect`**: (需认证) 建立WebSocket连接。
- **`REQUEST_ACCESS`** (Client -> Server): 客户端发起向其他用户获取房间号的实时请求。
- **`RESPOND_ACCESS`** (Client -> Server): 房主客户端响应收到的授权请求（同意/拒绝）。
- **`ACCESS_REQUEST_RECEIVED`** (Server -> Client): 服务器通知房主收到了一个授权请求。
- **`ACCESS_RESULT`** (Server -> Client): 服务器将授权结果（成功/失败/超时）通知给请求者。
- **`ERROR`** (Server -> Client): 服务器在发生错误（如请求频繁、操作无效）时发送通知。

---
*文档最后更新于 2025-11-04*