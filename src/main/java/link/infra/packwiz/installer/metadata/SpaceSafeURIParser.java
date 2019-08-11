package link.infra.packwiz.installer.metadata;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;

import java.lang.reflect.Type;
import java.net.URISyntaxException;

/**
 * This class encodes spaces before parsing the URI, so the URI can actually be
 * parsed.
 */
class SpaceSafeURIParser implements JsonDeserializer<SpaceSafeURI> {

	@Override
	public SpaceSafeURI deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
			throws JsonParseException {
		try {
			return new SpaceSafeURI(json.getAsString());
		} catch (URISyntaxException e) {
			throw new JsonParseException("Failed to parse URI", e);
		}
	}

	// TODO: replace this with a better solution?

}