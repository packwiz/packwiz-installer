package link.infra.packwiz.installer.metadata

import com.google.gson.annotations.SerializedName
import com.moandjiezana.toml.Toml
import link.infra.packwiz.installer.metadata.hash.Hash
import link.infra.packwiz.installer.metadata.hash.HashUtils.getHash
import link.infra.packwiz.installer.metadata.hash.HashUtils.getHasher
import link.infra.packwiz.installer.request.HandlerManager.getFileSource
import link.infra.packwiz.installer.request.HandlerManager.getNewLoc
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okio.Source
import okio.buffer
import java.io.InputStreamReader
import java.nio.file.Paths

class IndexFile {
	@SerializedName("hash-format")
	var hashFormat: String = "sha-256"
	var files: MutableList<File> = ArrayList()

	class File {
		var file: String? = null
		@SerializedName("hash-format")
		var hashFormat: String? = null
		var hash: String? = null
		var alias: String? = null
		var metafile = false
		var preserve = false

		@Transient
		var linkedFile: ModFile? = null
		@Transient
		var linkedFileURI: HttpUrl? = null

		@Throws(Exception::class)
		fun downloadMeta(parentIndexFile: IndexFile, indexUri: HttpUrl?) {
			if (!metafile) {
				return
			}
			if (hashFormat?.length ?: 0 == 0) {
				hashFormat = parentIndexFile.hashFormat
			}
			// TODO: throw a proper exception instead of allowing NPE?
			val fileHash = getHash(hashFormat!!, hash!!)
			linkedFileURI = getNewLoc(indexUri, file)
			val src = getFileSource(linkedFileURI!!)
			val fileStream = getHasher(hashFormat!!).getHashingSource(src)
			linkedFile = Toml().read(InputStreamReader(fileStream.buffer().inputStream(), "UTF-8")).to(ModFile::class.java)
			if (!fileStream.hashIsEqual(fileHash)) {
				throw Exception("Invalid mod file hash")
			}
		}

		@Throws(Exception::class)
		fun getSource(indexUri: HttpUrl?): Source {
			return if (metafile) {
				if (linkedFile == null) {
					throw Exception("Linked file doesn't exist!")
				}
				linkedFile!!.getSource(linkedFileURI)
			} else {
				val newLoc = getNewLoc(indexUri, file) ?: throw Exception("Index file URI is invalid")
				getFileSource(newLoc)
			}
		}

		@Throws(Exception::class)
		fun getHashObj(): Hash {
			if (hash == null) { // TODO: should these be more specific exceptions (e.g. IndexFileException?!)
				throw Exception("Index file doesn't have a hash")
			}
			if (hashFormat == null) {
				throw Exception("Index file doesn't have a hash format")
			}
			return getHash(hashFormat!!, hash!!)
		}

		// TODO: throw some kind of exception?
		val name: String
			get() {
				if (metafile) {
					return linkedFile?.name ?: linkedFile?.filename ?:
					file?.let { Paths.get(it).fileName.toString() } ?: "Invalid file"
				}
				return file?.let { Paths.get(it).fileName.toString() } ?: "Invalid file"
			}

		// TODO: URIs are bad
		val destURI: String?
			get() {
				if (alias != null) {
					return alias
				}
				return if (metafile && linkedFile != null) {
					linkedFile?.filename?.let { Paths.get(file).parent.resolve(it).toString() }
				} else {
					file
				}
			}
	}
}