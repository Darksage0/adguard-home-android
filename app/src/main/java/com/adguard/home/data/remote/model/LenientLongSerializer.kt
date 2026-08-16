package com.adguard.home.data.remote.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Decodes a JSON number into a Long whether the server sent it as an integer or a float literal.
 *
 * AdGuard Home's OpenAPI spec types most numeric fields as integers, but at least one real
 * server has been observed emitting a field typed int64 in the spec as a JSON float instead
 * (`"start_time":1786713564601.578` -- see ServerStatusDto, where this first surfaced and took
 * down the entire /control/status decode over one field). A published build talks to whatever
 * AdGuard Home version and platform each user happens to be running, not just the one that
 * exposed this bug, so every server-supplied Long field here uses this serializer rather than
 * assuming only the fields already known to be affected are at risk. Falls back to 0 only if the
 * value is neither a valid integer nor a valid float, which should not happen for a well-formed
 * response.
 */
object LenientLongSerializer : KSerializer<Long> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("LenientLong", PrimitiveKind.LONG)

    override fun deserialize(decoder: Decoder): Long {
        val jsonDecoder = decoder as? JsonDecoder ?: return decoder.decodeLong()
        val primitive = jsonDecoder.decodeJsonElement().jsonPrimitive
        return primitive.longOrNull ?: primitive.doubleOrNull?.toLong() ?: 0L
    }

    override fun serialize(encoder: Encoder, value: Long) {
        encoder.encodeLong(value)
    }
}

/** [LenientLongSerializer] applied element-wise to a `List<Long>` field. */
object LenientLongListSerializer : KSerializer<List<Long>> by ListSerializer(LenientLongSerializer)
