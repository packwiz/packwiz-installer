package link.infra.packwiz.installer.metadata.curseforge

import cc.ekblad.toml.tomlMapper

data class CurseForgeUpdateData(
	val fileId: Int,
	val projectId: Int,
): UpdateData {
	companion object {
		fun mapper() = tomlMapper {
			mapping<CurseForgeUpdateData>("file-id" to "fileId", "project-id" to "projectId")
		}
	}
}