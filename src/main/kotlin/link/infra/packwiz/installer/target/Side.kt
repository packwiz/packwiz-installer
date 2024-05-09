package link.infra.packwiz.installer.target

import cc.ekblad.toml.model.TomlValue
import cc.ekblad.toml.tomlMapper
import com.google.gson.annotations.SerializedName
import link.infra.packwiz.installer.Msgs

enum class Side(sideName: String) {
	@SerializedName("client")
	CLIENT("client"),
	@SerializedName("server")
	SERVER("server"),
	@SerializedName("both")
	@Suppress("unused")
	BOTH("both") {
		override fun hasSide(tSide: Side): Boolean {
			return true
		}
	};

	private val sideName: String

	init {
		this.sideName = sideName.lowercase()
	}

	override fun toString() = sideName

	open fun hasSide(tSide: Side): Boolean {
		return this == tSide || tSide == BOTH
	}

	companion object {
		fun from(name: String): Side? {
			val lowerName = name.lowercase()
			for (side in values()) {
				if (side.sideName == lowerName) {
					return side
				}
			}
			return null
		}

		fun mapper() = tomlMapper {
			encoder { it: Side -> TomlValue.String(it.sideName) }
			decoder { it: TomlValue.String -> from(it.value) ?: throw Exception(Msgs.unknownSide(it.value)) }
		}
	}
}