package link.infra.packwiz.installer.metadata.curseforge

import com.google.gson.annotations.SerializedName

class CurseForgeUpdateData: UpdateData {
	@SerializedName("file-id")
	var fileId = 0
	@SerializedName("project-id")
	var projectId = 0
}