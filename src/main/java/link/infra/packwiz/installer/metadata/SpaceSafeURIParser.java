package link.infra.packwiz.installer.metadata;

import java.lang.reflect.Type;
import java.net.URI;
import java.net.URISyntaxException;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;

/**
 * This class encodes spaces before parsing the URI, so the URI can actually be
 * parsed.
 */
class SpaceSafeURIParser implements JsonDeserializer<URI> {

	@Override
	public URI deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
			throws JsonParseException {
		String uriString = json.getAsString().replace(" ", "%20");
		try {
			return new URI(uriString);
		} catch (URISyntaxException e) {
			throw new JsonParseException("Failed to parse URI", e);
		}
	}

	// TODO: replace this with a better solution?

}