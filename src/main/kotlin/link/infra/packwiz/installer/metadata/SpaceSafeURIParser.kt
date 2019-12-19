package link.infra.packwiz.installer.metadata

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import java.lang.reflect.Type
import java.net.URISyntaxException

/**
 * This class encodes spaces before parsing the URI, so the URI can actually be
 * parsed.
 */
internal class SpaceSafeURIParser : JsonDeserializer<SpaceSafeURI> {
	@Throws(JsonParseException::class)
	override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): SpaceSafeURI {
		return try {
			SpaceSafeURI(json.asString)
		} catch (e: URISyntaxException) {
			throw JsonParseException("Failed to parse URI", e)
		}
	}

	// TODO: replace this with a better solution?
}