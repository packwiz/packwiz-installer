package link.infra.packwiz.installer.metadata.hash

import okio.HashingSource
import okio.Source

class HashingSourceHasher internal constructor(private val type: String) : IHasher {
	// i love naming things
	private inner class HashingSourceGeneralHashingSource(val delegateHashing: HashingSource) : GeneralHashingSource(delegateHashing) {
		override val hash: Hash by lazy(LazyThreadSafetyMode.NONE) {
			HashingSourceHash(delegateHashing.hash.hex())
		}
	}

	// this some funky inner class stuff
	// each of these classes is specific to the instance of the HasherHashingSource
	// therefore HashingSourceHashes from different parent instances will be not instanceof each other
	private inner class HashingSourceHash(val value: String) : Hash() {
		override val stringValue get() = value

		override fun equals(other: Any?): Boolean {
			if (other !is HashingSourceHash) {
				return false
			}
			return stringValue.equals(other.stringValue, ignoreCase = true)
		}

		override fun toString(): String = "$type: $stringValue"
		override fun hashCode(): Int = value.hashCode()

		override val type: String get() = this@HashingSourceHasher.type
	}

	override fun getHashingSource(delegate: Source): GeneralHashingSource {
		when (type) {
			"md5" -> return HashingSourceGeneralHashingSource(HashingSource.md5(delegate))
			"sha256" -> return HashingSourceGeneralHashingSource(HashingSource.sha256(delegate))
			"sha512" -> return HashingSourceGeneralHashingSource(HashingSource.sha512(delegate))
			"sha1" -> return HashingSourceGeneralHashingSource(HashingSource.sha1(delegate))
		}
		throw RuntimeException("Invalid hash type provided")
	}

	override fun getHash(value: String): Hash {
		return HashingSourceHash(value)
	}
}