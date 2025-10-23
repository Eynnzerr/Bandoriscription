package com.eynnzerr.routes

import com.eynnzerr.data.UserRepository
import com.eynnzerr.model.UserRegisterRequest
import com.eynnzerr.model.UserRegisterResponse
import com.eynnzerr.utils.JwtConfig
import com.eynnzerr.utils.respondFailure
import com.eynnzerr.utils.respondSuccess
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.response.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.koin.ktor.ext.inject

import kotlinx.serialization.json.JsonElement

@Serializable
data class ExternalApiResponse(val status: String, val response: JsonElement? = null)

fun Route.authRoutes() {
    val userRepository = UserRepository()
    val httpClient by inject<HttpClient>()

    post("/register") {
        val request = call.receive<UserRegisterRequest>()

        // 使用用户携带的车站token向车站发起账号信息请求，观察结果，从而验证身份有效性
        val externalApiUrl = "https://server.bandoristation.com"
        val externalApiRequestBody = mapOf(
            "function_group" to "MainAction",
            "function" to "initializeAccountSetting"
        )

        try {
            val externalApiResponse: HttpResponse = httpClient.post(externalApiUrl) {
                contentType(ContentType.Application.Json)
                setBody(externalApiRequestBody)
                header("Auth-Token", request.originalToken)
            }

            if (externalApiResponse.status == HttpStatusCode.OK) {
                val responseBody = externalApiResponse.bodyAsText()
                val apiResponse = Json.decodeFromString<ExternalApiResponse>(responseBody)

                if (apiResponse.status == "success") {
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
                } else {
                    call.respondFailure("Invalid token from BandoriStation.", HttpStatusCode.Unauthorized)
                }
            } else {
                call.respondFailure("Failed to communicate with BandoriStation: ${externalApiResponse.status.description}",
                    HttpStatusCode.BadGateway)
            }
        } catch (e: Exception) {
            call.respondFailure("Error validating token: ${e.localizedMessage}", HttpStatusCode.InternalServerError)
        }
    }
}
