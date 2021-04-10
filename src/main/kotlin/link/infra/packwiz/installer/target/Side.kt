package link.infra.packwiz.installer.target

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
		this.sideName = sideName.toLowerCase()
		depSides = null
	}

	constructor(sideName: String, depSides: Array<Side>) {
		this.sideName = sideName.toLowerCase()
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
			val lowerName = name.toLowerCase()
			for (side in values()) {
				if (side.sideName == lowerName) {
					return side
				}
			}
			return null
		}
	}
}