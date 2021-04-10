package link.infra.packwiz.installer.metadata

import com.google.gson.annotations.SerializedName
import link.infra.packwiz.installer.metadata.hash.Hash
import link.infra.packwiz.installer.metadata.hash.HashUtils.getHash
import link.infra.packwiz.installer.request.HandlerManager.getFileSource
import link.infra.packwiz.installer.request.HandlerManager.getNewLoc
import link.infra.packwiz.installer.target.Side
import okio.Source

class ModFile {
	var name: String? = null
	var filename: String? = null
	var side: Side? = null
	var download: Download? = null

	class Download {
		var url: SpaceSafeURI? = null
		@SerializedName("hash-format")
		var hashFormat: String? = null
		var hash: String? = null
	}

	var update: Map<String, Any>? = null
	var option: Option? = null

	class Option {
		var optional = false
		var description: String? = null
		@SerializedName("default")
		var defaultValue = false
	}

	@Throws(Exception::class)
	fun getSource(baseLoc: SpaceSafeURI?): Source {
		download?.let {
			if (it.url == null) {
				throw Exception("Metadata file doesn't have a download URI")
			}
			val newLoc = getNewLoc(baseLoc, it.url) ?: throw Exception("Metadata file URI is invalid")
			return getFileSource(newLoc)
		} ?: throw Exception("Metadata file doesn't have download")
	}

	@get:Throws(Exception::class)
	val hash: Hash
		get() {
			download?.let {
				return getHash(
						it.hashFormat ?: throw Exception("Metadata file doesn't have a hash format"),
						it.hash ?: throw Exception("Metadata file doesn't have a hash")
				)
			} ?: throw Exception("Metadata file doesn't have download")
		}

	val isOptional: Boolean get() = option?.optional ?: false
}