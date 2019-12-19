package link.infra.packwiz.installer.metadata

import com.google.gson.annotations.SerializedName

class PackFile {
	var name: String? = null
	var index: IndexFileLoc? = null

	class IndexFileLoc {
		var file: SpaceSafeURI? = null
		@SerializedName("hash-format")
		var hashFormat: String? = null
		var hash: String? = null
	}

	var versions: Map<String, String>? = null
	var client: Map<String, Any>? = null
	var server: Map<String, Any>? = null
}