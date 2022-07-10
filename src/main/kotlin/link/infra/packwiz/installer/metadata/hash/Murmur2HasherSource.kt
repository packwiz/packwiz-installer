package link.infra.packwiz.installer.metadata.hash

import okio.Buffer
import okio.ForwardingSource
import okio.Source
import java.io.IOException

class Murmur2HasherSource(type: HashFormat<UInt>, delegate: Source) : ForwardingSource(delegate), HasherSource<UInt> {
	private val internalBuffer = Buffer()
	private val tempBuffer = Buffer()

	override val hash: Hash<UInt> by lazy(LazyThreadSafetyMode.NONE) {
		// TODO: remove internal buffering?
		val data = internalBuffer.readByteArray()
		Hash(type, Murmur2Lib.hash32(data, data.size, 1).toUInt())
	}

	@Throws(IOException::class)
	override fun read(sink: Buffer, byteCount: Long): Long {
		val out = delegate.read(tempBuffer, byteCount)
		if (out > -1) {
			sink.write(tempBuffer.clone(), out)
			computeNormalizedBufferFaster(tempBuffer, internalBuffer)
		}
		return out
	}

	// Credit to https://github.com/modmuss50/CAV2/blob/master/murmur.go
	private fun computeNormalizedBufferFaster(input: Buffer, output: Buffer) {
		var index = 0
		val arr = input.readByteArray()
		for (b in arr) {
			when (b) {
				9.toByte(), 10.toByte(), 13.toByte(), 32.toByte() -> {}
				else -> {
					arr[index] = b
					index++
				}
			}
		}
		output.write(arr, 0, index)
	}
}