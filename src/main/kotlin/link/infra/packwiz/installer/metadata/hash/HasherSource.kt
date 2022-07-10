package link.infra.packwiz.installer.metadata.hash

import okio.Source

interface HasherSource<T>: Source {
	val hash: Hash<T>
}