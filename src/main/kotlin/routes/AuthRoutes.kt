package com.eynnzerr.routes

import com.eynnzerr.data.UserRepository
import com.eynnzerr.model.UserRegisterRequest
import com.eynnzerr.model.UserRegisterResponse
import com.eynnzerr.utils.JwtConfig
import com.eynnzerr.utils.respondSuccess
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.routing.*

fun Route.authRoutes() {
    val userRepository = UserRepository()

    post("/register") {
        val request = call.receive<UserRegisterRequest>()

        // TODO 这里应该验证 originalToken 的有效性
        // 暂时简化处理，实际应该调用原服务端验证

        val token = JwtConfig.generateToken(request.userId)

        val user = userRepository.getUserById(request.userId)
        if (user == null) {
            userRepository.createUser(request.userId, token)
        } else {
            userRepository.updateUser(request.userId, token)
        }

        call.respondSuccess(UserRegisterResponse(
            token = token,
            expiresIn = 30L * 24 * 60 * 60 * 1000 // 30天
        ))
    }
}
