package com.eynnzerr.utils

import com.eynnzerr.model.ApiResponse
import com.eynnzerr.model.ApiResponseContent
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import kotlin.reflect.typeOf

@OptIn(InternalSerializationApi::class)
suspend inline fun <reified T : Any> ApplicationCall.respondSuccess(data: T) {
    val content = when (data) {
        is String -> ApiResponseContent.StringContent(data)
        else -> {
            // val serializer = T::class.serializer()
            val serializer = serializer(typeOf<T>())
            ApiResponseContent.ObjectContent(Json.encodeToJsonElement(serializer, data))
        }
    }
    val response = ApiResponse(status = "success", response = content)
    respond(HttpStatusCode.OK, response)
}

suspend fun ApplicationCall.respondFailure(message: String, statusCode: HttpStatusCode = HttpStatusCode.OK) {
    val content = ApiResponseContent.StringContent(message)
    val response = ApiResponse(status = "failure", response = content)
    respond(statusCode, response)
}
