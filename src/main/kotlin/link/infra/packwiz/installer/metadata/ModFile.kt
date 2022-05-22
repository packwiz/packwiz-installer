package link.infra.packwiz.installer.metadata

import com.google.gson.annotations.JsonAdapter
import com.google.gson.annotations.SerializedName
import link.infra.packwiz.installer.metadata.curseforge.UpdateData
import link.infra.packwiz.installer.metadata.curseforge.UpdateDeserializer
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
		var mode: String? = null
	}

	@JsonAdapter(UpdateDeserializer::class)
	var update: Map<String, UpdateData>? = null
	var option: Option? = null

	@Transient
	val resolvedUpdateData = mutableMapOf<String, SpaceSafeURI>()

	class Option {
		var optional = false
		var description: String? = null
		@SerializedName("default")
		var defaultValue = false
	}

	@Throws(Exception::class)
	fun getSource(baseLoc: SpaceSafeURI?): Source {
		download?.let {
			if (it.mode == null || it.mode == "" || it.mode == "url") {
				if (it.url == null) {
					throw Exception("Metadata file doesn't have a download URI")
				}
				val newLoc = getNewLoc(baseLoc, it.url) ?: throw Exception("Metadata file URI is invalid")
				return getFileSource(newLoc)
			} else if (it.mode == "metadata:curseforge") {
				if (!resolvedUpdateData.contains("curseforge")) {
					throw Exception("Metadata file specifies CurseForge mode, but is missing metadata")
				}
				return getFileSource(resolvedUpdateData["curseforge"]!!)
			} else {
				throw Exception("Unsupported download mode " + it.mode)
			}
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