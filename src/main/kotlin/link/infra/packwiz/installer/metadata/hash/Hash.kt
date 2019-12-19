package link.infra.packwiz.installer.metadata.hash

import com.google.gson.*
import java.lang.reflect.Type

abstract class Hash {
	protected abstract val stringValue: String
	protected abstract val type: String

	class TypeHandler : JsonDeserializer<Hash>, JsonSerializer<Hash> {
		override fun serialize(src: Hash, typeOfSrc: Type, context: JsonSerializationContext): JsonElement = JsonObject().apply {
			add("type", JsonPrimitive(src.type))
			add("value", JsonPrimitive(src.stringValue))
		}

		@Throws(JsonParseException::class)
		override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): Hash {
			val obj = json.asJsonObject
			val type: String
			val value: String
			try {
				type = obj["type"].asString
				value = obj["value"].asString
			} catch (e: NullPointerException) {
				throw JsonParseException("Invalid hash JSON data")
			}
			return try {
				HashUtils.getHash(type, value)
			} catch (e: Exception) {
				throw JsonParseException("Failed to create hash object", e)
			}
		}
	}
}