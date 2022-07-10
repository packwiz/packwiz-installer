package link.infra.packwiz.installer.metadata.hash

import cc.ekblad.toml.model.TomlValue
import cc.ekblad.toml.tomlMapper
import link.infra.packwiz.installer.metadata.hash.Hash.Encoding
import link.infra.packwiz.installer.metadata.hash.Hash.SourceProvider
import link.infra.packwiz.installer.metadata.hash.Hash.SourceProvider.Companion.fromOkio
import okio.ByteString
import okio.Source
import okio.HashingSource.Companion as OkHashes

sealed class HashFormat<T>(val formatName: String): Encoding<T>, SourceProvider<T> {
	object SHA1: HashFormat<ByteString>("sha1"),
		Encoding<ByteString> by Encoding.Hex, SourceProvider<ByteString> by fromOkio(OkHashes::sha1)
	object SHA256: HashFormat<ByteString>("sha256"),
		Encoding<ByteString> by Encoding.Hex, SourceProvider<ByteString> by fromOkio(OkHashes::sha256)
	object SHA512: HashFormat<ByteString>("sha512"),
		Encoding<ByteString> by Encoding.Hex, SourceProvider<ByteString> by fromOkio(OkHashes::sha512)
	object MD5: HashFormat<ByteString>("md5"),
		Encoding<ByteString> by Encoding.Hex, SourceProvider<ByteString> by fromOkio(OkHashes::md5)
	object MURMUR2: HashFormat<UInt>("murmur2"),
		Encoding<UInt> by Encoding.UInt, SourceProvider<UInt> by SourceProvider(::Murmur2HasherSource)

	fun source(delegate: Source): HasherSource<T> = source(this, delegate)
	fun fromString(str: String) = Hash(this, decodeFromString(str))
	override fun toString() = formatName

	companion object {
		// lazy used to prevent initialisation issues!
		private val values by lazy { listOf(SHA1, SHA256, SHA512, MD5, MURMUR2) }
		fun fromName(formatName: String) = values.find { formatName == it.formatName }

		fun mapper() = tomlMapper {
			// TODO: better exception?
			decoder { it: TomlValue.String -> fromName(it.value) ?: throw Exception("Hash format ${it.value} not supported") }
			encoder { it: HashFormat<*> -> TomlValue.String(it.formatName) }
		}
	}
}