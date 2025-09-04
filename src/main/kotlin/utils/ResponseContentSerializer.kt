package com.eynnzerr.utils

import com.eynnzerr.model.ApiResponseContent
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.buildSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonPrimitive

object ResponseContentSerializer : KSerializer<ApiResponseContent> {
    @OptIn(InternalSerializationApi::class)
    override val descriptor: SerialDescriptor = buildSerialDescriptor("ApiResponseContent", SerialKind.CONTEXTUAL)

    override fun deserialize(decoder: Decoder): ApiResponseContent {
        val jsonDecoder = decoder as? JsonDecoder ?: throw SerializationException("Expected JsonDecoder")
        val element = jsonDecoder.decodeJsonElement()

        return when {
            element is JsonPrimitive && element.isString -> ApiResponseContent.StringContent(element.content)
            else -> ApiResponseContent.ObjectContent(element)
//            element is JsonObject -> ApiResponseContent.ObjectContent(element)
//            element is JsonArray -> ApiResponseContent.StringContent("123")
//            else -> throw SerializationException()
        }
    }

    override fun serialize(encoder: Encoder, value: ApiResponseContent) {
        val jsonEncoder = encoder as? JsonEncoder ?: throw SerializationException("Expected JsonEncoder")

        when (value) {
            is ApiResponseContent.StringContent -> jsonEncoder.encodeString(value.text)
            is ApiResponseContent.ObjectContent -> jsonEncoder.encodeJsonElement(value.data)
        }
    }
}
