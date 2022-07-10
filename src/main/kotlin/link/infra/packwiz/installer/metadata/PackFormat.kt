package link.infra.packwiz.installer.metadata

@JvmInline
value class PackFormat(val format: String) {
	companion object {
		val DEFAULT = PackFormat("packwiz:1.0.0")
	}

	// TODO: implement validation, errors for too new / invalid versions
}