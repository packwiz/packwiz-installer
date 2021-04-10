package link.infra.packwiz.installer.target

import link.infra.packwiz.installer.metadata.SpaceSafeURI
import link.infra.packwiz.installer.metadata.hash.Hash
import java.nio.file.Path

data class Target(
	/**
	 * The name that uniquely identifies this target.
	 * Often equal to the name of the metadata file for this target, and can be displayed to the user in progress UI.
	 */
	val name: String,
	/**
	 * An optional user-friendly name.
	 */
	val userFriendlyName: String?,
	val src: SpaceSafeURI,
	val dest: Path,
	val hash: Hash,
	val side: Side,
	val optional: Boolean,
	val optionalDefaultValue: Boolean,
	val optionalDescription: String,
	/**
	 * If this is true, don't update a target when the file already exists.
	 */
	val noOverwrite: Boolean
) {
	fun Iterable<Target>.filterForSide(side: Side) = this.filter {
		it.side.hasSide(side)
	}
}
