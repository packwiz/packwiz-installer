package link.infra.packwiz.installer.metadata.hash

import com.google.gson.*
import link.infra.packwiz.installer.Msgs
import link.infra.packwiz.installer.metadata.hash.Hash.SourceProvider
import okio.ByteString
import okio.ByteString.Companion.decodeHex
import okio.ForwardingSource
import okio.HashingSource
import okio.Source
import java.lang.reflect.Type

data class Hash<T>(val type: HashFormat<T>, val value: T) {
	interface Encoding<T> {
		fun encodeToString(value: T): String
		fun decodeFromString(str: String): T

		object Hex: Encoding<ByteString> {
			override fun encodeToString(value: ByteString) = value.hex()
			override fun decodeFromString(str: String) = str.decodeHex()
		}

		object UInt: Encoding<kotlin.UInt> {
			override fun encodeToString(value: kotlin.UInt) = value.toString()
			override fun decodeFromString(str: String) =
				try {
					str.toUInt()
				} catch (e: NumberFormatException) {
					// Old packwiz.json values are signed; if they are negative they should be parsed as signed integers
					// and reinterpreted as unsigned integers
					str.toInt().toUInt()
				}
		}
	}

	fun interface SourceProvider<T> {
		fun source(type: HashFormat<T>, delegate: Source): HasherSource<T>

		companion object {
			fun fromOkio(provider: ((Source) -> HashingSource)): SourceProvider<ByteString> {
				return SourceProvider { type, delegate ->
					val delegateHashing = provider.invoke(delegate)
					object : ForwardingSource(delegateHashing), HasherSource<ByteString> {
						override val hash: Hash<ByteString> by lazy(LazyThreadSafetyMode.NONE) { Hash(type, delegateHashing.hash) }
					}
				}
			}
		}
	}

	class TypeHandler : JsonDeserializer<Hash<*>>, JsonSerializer<Hash<*>> {
		override fun serialize(src: Hash<*>, typeOfSrc: Type, context: JsonSerializationContext): JsonElement = JsonObject().apply {
			add("type", JsonPrimitive(src.type.formatName))
			// Local function for generics
			fun <T> addValue(src: Hash<T>) = add("value", JsonPrimitive(src.type.encodeToString(src.value)))
			addValue(src)
		}

		@Throws(JsonParseException::class)
		override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): Hash<*> {
			val obj = json.asJsonObject
			val type: String
			val value: String
			try {
				type = obj["type"].asString
				value = obj["value"].asString
			} catch (e: NullPointerException) {
				throw JsonParseException(Msgs.invalidHashJsonData())
			}
			return try {
				(HashFormat.fromName(type) ?: throw JsonParseException(Msgs.unknownHashType(type))).fromString(value)
			} catch (e: Exception) {
				throw JsonParseException(Msgs.failedCreateHashObj(), e)
			}
		}
	}
}