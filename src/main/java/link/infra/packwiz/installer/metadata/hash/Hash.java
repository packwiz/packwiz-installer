package link.infra.packwiz.installer.metadata.hash;

import java.lang.reflect.Type;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

public abstract class Hash {
	protected abstract String getStringValue();

	protected abstract String getType();

	public static class TypeHandler implements JsonDeserializer<Hash>, JsonSerializer<Hash> {

		@Override
		public JsonElement serialize(Hash src, Type typeOfSrc, JsonSerializationContext context) {
			JsonObject out = new JsonObject();
			out.add("type", new JsonPrimitive(src.getType()));
			out.add("value", new JsonPrimitive(src.getStringValue()));
			return out;
		}

		@Override
		public Hash deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
				throws JsonParseException {
			JsonObject obj = json.getAsJsonObject();
			String type, value;
			try {
				type = obj.get("type").getAsString();
				value = obj.get("value").getAsString();
			} catch (NullPointerException e) {
				throw new JsonParseException("Invalid hash JSON data");
			}
			try {
				return HashUtils.getHash(type, value);
			} catch (Exception e) {
				throw new JsonParseException("Failed to create hash object", e);
			}
		}

	}
}