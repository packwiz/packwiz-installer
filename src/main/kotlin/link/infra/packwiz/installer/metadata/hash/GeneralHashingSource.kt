package link.infra.packwiz.installer.metadata.hash

import okio.ForwardingSource
import okio.Source

abstract class GeneralHashingSource(delegate: Source) : ForwardingSource(delegate) {
	abstract val hash: Hash

	fun hashIsEqual(compareTo: Any) = compareTo == hash
}