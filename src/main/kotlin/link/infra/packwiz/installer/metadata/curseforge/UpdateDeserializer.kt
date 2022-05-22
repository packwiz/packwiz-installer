package link.infra.packwiz.installer.metadata.curseforge

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import java.lang.reflect.Type

class UpdateDeserializer: JsonDeserializer<Map<String, UpdateData>> {
	override fun deserialize(
		json: JsonElement?,
		typeOfT: Type?,
		context: JsonDeserializationContext?
	): Map<String, UpdateData> {
		val out = mutableMapOf<String, UpdateData>()
		for ((k, v) in json!!.asJsonObject.entrySet()) {
			if (k == "curseforge") {
				out[k] = context!!.deserialize(v, CurseForgeUpdateData::class.java)
			}
		}
		return out
	}
}