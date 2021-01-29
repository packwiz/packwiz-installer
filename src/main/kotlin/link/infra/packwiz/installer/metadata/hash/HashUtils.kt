package link.infra.packwiz.installer.metadata.hash

object HashUtils {
	private val hashTypeConversion: Map<String, IHasher> = mapOf(
			"sha256" to HashingSourceHasher("sha256"),
			"sha512" to HashingSourceHasher("sha512"),
			"murmur2" to Murmur2Hasher(),
			"sha1" to HashingSourceHasher("sha1")
	)

	@JvmStatic
	@Throws(Exception::class)
	fun getHasher(type: String): IHasher {
		return hashTypeConversion[type] ?: throw Exception("Hash type not supported: $type")
	}

	@JvmStatic
	@Throws(Exception::class)
	fun getHash(type: String, value: String): Hash {
		return hashTypeConversion[type]?.getHash(value) ?: throw Exception("Hash type not supported: $type")
	}
}