package link.infra.packwiz.installer.metadata;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;

public class EfficientBooleanAdapter extends TypeAdapter<Boolean> {

	@Override
	public void write(JsonWriter out, Boolean value) throws IOException {
		if (value == null || !value) {
			out.nullValue();
			return;
		}
		out.value(true);
	}

	@Override
	public Boolean read(JsonReader in) throws IOException {
		if (in.peek() == JsonToken.NULL) {
			in.nextNull();
			return false;
		}
		return in.nextBoolean();
	}
}
