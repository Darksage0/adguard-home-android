package com.adguard.home.data.remote.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

@Serializable(with = TopEntrySerializer::class)
data class TopEntryDto(
    val key: String,
    val value: Double
)

/**
 * Custom serializer for AdGuard Home's TopArrayEntry objects which arrive as dynamic key maps:
 * e.g. [{"192.168.1.1": 1500}, {"192.168.1.2": 320}] or [{"dns.google": 0.023}]
 */
object TopEntrySerializer : KSerializer<TopEntryDto> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("TopEntryDto", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): TopEntryDto {
        val jsonDecoder = decoder as? JsonDecoder
            ?: throw IllegalStateException("This serializer can only be used with JsonDecoder")
        val jsonObject = jsonDecoder.decodeJsonElement() as? JsonObject
            ?: return TopEntryDto("", 0.0)

        val firstEntry = jsonObject.entries.firstOrNull() ?: return TopEntryDto("", 0.0)
        val key = firstEntry.key
        val value = firstEntry.value.jsonPrimitive.doubleOrNull
            ?: firstEntry.value.jsonPrimitive.longOrNull?.toDouble()
            ?: 0.0

        return TopEntryDto(key = key, value = value)
    }

    override fun serialize(encoder: Encoder, value: TopEntryDto) {
        throw UnsupportedOperationException("Serialization of TopEntryDto is not needed for client requests")
    }
}
