package link.infra.packwiz.installer.target

import link.infra.packwiz.installer.metadata.hash.Hash

/**
 * A token used to determine if the source or destination file is changed, and ensure that the destination file is valid.
 */
interface ValidityToken {
	// TODO: functions to allow validating this from file metadata, from file, or during the download process

	/**
	 * Default implementation of ValidityToken based on a single hash.
	 */
	@JvmInline
	value class HashValidityToken(val hash: Hash): ValidityToken
}