package link.infra.packwiz.installer.target

import cc.ekblad.toml.model.TomlValue
import cc.ekblad.toml.tomlMapper
import com.google.gson.annotations.SerializedName

enum class Side {
	@SerializedName("client")
	CLIENT("client"),
	@SerializedName("server")
	SERVER("server"),
	@SerializedName("both")
	@Suppress("unused")
	BOTH("both", arrayOf(CLIENT, SERVER));

	private val sideName: String
	private val depSides: Array<Side>?

	constructor(sideName: String) {
		this.sideName = sideName.lowercase()
		depSides = null
	}

	constructor(sideName: String, depSides: Array<Side>) {
		this.sideName = sideName.lowercase()
		this.depSides = depSides
	}

	override fun toString() = sideName

	fun hasSide(tSide: Side): Boolean {
		if (this == tSide) {
			return true
		}
		if (depSides != null) {
			for (depSide in depSides) {
				if (depSide == tSide) {
					return true
				}
			}
		}
		return false
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
			decoder { it: TomlValue.String -> from(it.value) ?: throw Exception("Invalid side name ${it.value}") }
		}
	}
}