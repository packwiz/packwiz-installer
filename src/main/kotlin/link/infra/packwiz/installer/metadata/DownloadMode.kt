package link.infra.packwiz.installer.metadata

import cc.ekblad.toml.model.TomlValue
import cc.ekblad.toml.tomlMapper

enum class DownloadMode {
	URL,
	CURSEFORGE;

	companion object {
		fun mapper() = tomlMapper {
			decoder { it: TomlValue.String -> when (it.value) {
				"", "url" -> URL
				"metadata:curseforge" -> CURSEFORGE
				else -> throw Exception("Unsupported download mode ${it.value}")
			} }
		}
	}
}