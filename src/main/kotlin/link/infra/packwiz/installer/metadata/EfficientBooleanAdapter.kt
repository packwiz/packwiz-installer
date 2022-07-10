package link.infra.packwiz.installer.metadata

import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import java.io.IOException

class EfficientBooleanAdapter : TypeAdapter<Boolean?>() {
	@Throws(IOException::class)
	override fun write(out: JsonWriter, value: Boolean?) {
		if (value == null || !value) {
			out.nullValue()
			return
		}
		out.value(true)
	}

	@Throws(IOException::class)
	override fun read(reader: JsonReader): Boolean {
		if (reader.peek() == JsonToken.NULL) {
			reader.nextNull()
			return false
		}
		return reader.nextBoolean()
	}
}