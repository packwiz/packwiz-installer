package link.infra.packwiz.installer.metadata.hash

import okio.Source

interface IHasher {
	fun getHashingSource(delegate: Source): GeneralHashingSource
	fun getHash(value: String): Hash
}