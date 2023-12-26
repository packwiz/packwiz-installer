package link.infra.packwiz.installer.metadata

import cc.ekblad.toml.decode
import cc.ekblad.toml.tomlMapper
import link.infra.packwiz.installer.Msgs
import link.infra.packwiz.installer.metadata.hash.Hash
import link.infra.packwiz.installer.metadata.hash.HashFormat
import link.infra.packwiz.installer.target.ClientHolder
import link.infra.packwiz.installer.target.path.PackwizPath
import link.infra.packwiz.installer.util.delegateTransitive
import okio.Source
import okio.buffer

data class IndexFile(
	val hashFormat: HashFormat<*>,
	val files: List<File> = listOf()
) {
	data class File(
		val file: PackwizPath<*>,
		private val hashFormat: HashFormat<*>? = null,
		val hash: String,
		val alias: PackwizPath<*>?,
		val metafile: Boolean = false,
		val preserve: Boolean = false,
	) {
		var linkedFile: ModFile? = null

		fun hashFormat(index: IndexFile) = hashFormat ?: index.hashFormat
		@Throws(Exception::class)
		fun getHashObj(index: IndexFile): Hash<*> {
			// TODO: more specific exceptions?
			return hashFormat(index).fromString(hash)
		}

		@Throws(Exception::class)
		fun downloadMeta(index: IndexFile, clientHolder: ClientHolder) {
			if (!metafile) {
				return
			}
			val fileHash = getHashObj(index)
			val src = file.source(clientHolder)
			val fileStream = hashFormat(index).source(src)
			linkedFile = ModFile.mapper(file).decode<ModFile>(fileStream.buffer().inputStream())
			if (fileHash != fileStream.hash) {
				// TODO: propagate details about hash, and show better error!
				throw Exception(Msgs.invalidModFileHash())
			}
		}

		@Throws(Exception::class)
		fun getSource(clientHolder: ClientHolder): Source {
			return if (metafile) {
				if (linkedFile == null) {
					throw Exception(Msgs.linkedFileNotExist())
				}
				linkedFile!!.getSource(clientHolder)
			} else {
				file.source(clientHolder)
			}
		}

		val name: String
			get() {
				if (metafile) {
					return linkedFile?.name ?: file.filename
				}
				return file.filename
			}

		val destURI: PackwizPath<*>
			get() {
				if (alias != null) {
					return alias
				}
				return if (metafile) {
					linkedFile!!.filename
				} else {
					file
				}
			}

		companion object {
			fun mapper(base: PackwizPath<*>) = tomlMapper {
				mapping<File>("hash-format" to "hashFormat")
				delegateTransitive<HashFormat<*>>(HashFormat.mapper())
				delegateTransitive<PackwizPath<*>>(PackwizPath.mapperRelativeTo(base))
			}
		}
	}

	companion object {
		fun mapper(base: PackwizPath<*>) = tomlMapper {
			mapping<IndexFile>("hash-format" to "hashFormat")
			delegateTransitive<HashFormat<*>>(HashFormat.mapper())
			delegateTransitive<File>(File.mapper(base))
		}
	}
}