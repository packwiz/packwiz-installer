package link.infra.packwiz.installer.metadata.curseforge

import cc.ekblad.toml.model.TomlValue
import cc.ekblad.toml.tomlMapper

interface UpdateData {
	companion object {
		fun mapper() = tomlMapper {
			val cfMapper = CurseForgeUpdateData.mapper()
			decoder { it: TomlValue.Map ->
				if (it.properties.contains("curseforge")) {
					mapOf("curseforge" to cfMapper.decode<CurseForgeUpdateData>(it.properties["curseforge"]!!))
				} else { mapOf() }
			}
		}
	}
}