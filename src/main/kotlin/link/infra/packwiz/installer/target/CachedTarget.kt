package link.infra.packwiz.installer.target

import link.infra.packwiz.installer.metadata.hash.Hash
import java.nio.file.Path

data class CachedTarget(
	/**
	 * @see Target.name
	 */
	val name: String,
	/**
	 * The location where the target was last downloaded to.
	 * This is used for removing old files when the destination path changes.
	 * This shouldn't be set to the .disabled path (that is manually appended and checked)
	 */
	val cachedLocation: Path,
	val enabled: Boolean,
	val hash: Hash,
	/**
	 * For detecting when a target transitions non-optional -> optional and showing the option selection screen
	 */
	val isOptional: Boolean
)
