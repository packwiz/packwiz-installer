package link.infra.packwiz.installer.metadata.hash

import okio.Buffer
import okio.Source
import java.io.IOException

class Murmur2Hasher : IHasher {
	private inner class Murmur2GeneralHashingSource(delegate: Source) : GeneralHashingSource(delegate) {
		val internalBuffer = Buffer()
		val tempBuffer = Buffer()

		override val hash: Hash by lazy(LazyThreadSafetyMode.NONE) {
			val data = computeNormalizedArray(internalBuffer.readByteArray())
			Murmur2Hash(Murmur2Lib.hash32(data, data.size, 1))
		}

		@Throws(IOException::class)
		override fun read(sink: Buffer, byteCount: Long): Long {
			val out = delegate.read(tempBuffer, byteCount)
			if (out > -1) {
				sink.write(tempBuffer.clone(), out)
				internalBuffer.write(tempBuffer, out)
			}
			return out
		}

		// Credit to https://github.com/modmuss50/CAV2/blob/master/murmur.go
		private fun computeNormalizedArray(input: ByteArray): ByteArray {
			val output = ByteArray(input.size)
			var index = 0
			for (b in input) {
				when (b.toInt()) {
					9, 10, 13, 32 -> {}
					else -> {
						output[index] = b
						index++
					}
				}
			}
			val outputTrimmed = ByteArray(index)
			System.arraycopy(output, 0, outputTrimmed, 0, index)
			return outputTrimmed
		}

	}

	private class Murmur2Hash : Hash {
		val value: Int

		constructor(value: String) {
			// Parsing as long then casting to int converts values gt int max value but lt uint max value
			// into negatives. I presume this is how the murmur2 code handles this.
			this.value = value.toLong().toInt()
		}

		constructor(value: Int) {
			this.value = value
		}

		override val stringValue get() = value.toString()
		override val type get() = "murmur2"

		override fun equals(other: Any?): Boolean {
			if (other !is Murmur2Hash) {
				return false
			}
			return value == other.value
		}

		override fun toString(): String = "murmur2: $value"
		override fun hashCode(): Int = value
	}

	override fun getHashingSource(delegate: Source): GeneralHashingSource = Murmur2GeneralHashingSource(delegate)
	override fun getHash(value: String): Hash = Murmur2Hash(value)
}