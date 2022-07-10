package link.infra.packwiz.installer.metadata

import com.google.gson.annotations.JsonAdapter
import link.infra.packwiz.installer.metadata.hash.Hash
import link.infra.packwiz.installer.target.Side
import link.infra.packwiz.installer.target.path.PackwizFilePath

class ManifestFile {
	var packFileHash: Hash<*>? = null
	var indexFileHash: Hash<*>? = null
	var cachedFiles: MutableMap<PackwizFilePath, File> = HashMap()
	// If the side changes, EVERYTHING invalidates. FUN!!!
	var cachedSide = Side.CLIENT

	class File {
		@Transient
		var revert: File? = null
			private set

		var hash: Hash<*>? = null
		var linkedFileHash: Hash<*>? = null
		var cachedLocation: PackwizFilePath? = null

		@JsonAdapter(EfficientBooleanAdapter::class)
		var isOptional = false
		var optionValue = true

		@JsonAdapter(EfficientBooleanAdapter::class)
		var onlyOtherSide = false

		// When an error occurs, the state needs to be reverted. To do this, I have a crude revert system.
		fun backup() {
			revert = File().also {
				it.hash = hash
				it.linkedFileHash = linkedFileHash
				it.cachedLocation = cachedLocation
				it.isOptional = isOptional
				it.optionValue = optionValue
				it.onlyOtherSide = onlyOtherSide
			}
		}

	}
}