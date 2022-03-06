package link.infra.packwiz.installer.target

import link.infra.packwiz.installer.target.path.PackwizPath

// TODO: rename to avoid conflicting with @Target
interface Target {
	val src: PackwizPath
	val dest: PackwizPath
	val validityToken: ValidityToken

	/**
	 * Token interface for types used to compare Target identity. Implementations should use equals to indicate that
	 * these tokens represent the same file; used to preserve optional target choices between file renames.
	 */
	interface IdentityToken

	/**
	 * Default implementation of IdentityToken that assumes files are not renamed; so optional choices do not need to
	 * be preserved across renames.
	 */
	@JvmInline
	value class PathIdentityToken(val path: String): IdentityToken

	val ident: IdentityToken
		get() = PathIdentityToken(dest.path)

	/**
	 * A user-friendly name; defaults to the destination path of the file.
	 */
	val name: String
		get() = dest.path
	val side: Side
		get() = Side.BOTH
	val overwriteMode: OverwriteMode
		get() = OverwriteMode.IF_SRC_CHANGED

	interface Optional: Target {
		val optional: Boolean
		val optionalDefaultValue: Boolean
	}

	interface OptionalDescribed: Optional {
		val optionalDescription: String
	}
}
