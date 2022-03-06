package link.infra.packwiz.installer.target

enum class OverwriteMode {
	/**
	 * Overwrite the destination with the source file, if the source file has changed.
	 */
	IF_SRC_CHANGED,

	/**
	 * Never overwrite the destination; if it exists, it should not be written to.
	 */
	NEVER
}