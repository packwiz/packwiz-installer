package link.infra.packwiz.installer

import link.infra.packwiz.installer.metadata.IndexFile
import link.infra.packwiz.installer.metadata.ManifestFile
import link.infra.packwiz.installer.metadata.SpaceSafeURI
import link.infra.packwiz.installer.metadata.hash.Hash
import link.infra.packwiz.installer.metadata.hash.HashUtils.getHash
import link.infra.packwiz.installer.metadata.hash.HashUtils.getHasher
import link.infra.packwiz.installer.target.Side
import link.infra.packwiz.installer.ui.data.ExceptionDetails
import link.infra.packwiz.installer.ui.data.IOptionDetails
import link.infra.packwiz.installer.util.Log
import okio.*
import okio.Path.Companion.toOkioPath
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.*
import kotlin.io.use

internal class DownloadTask private constructor(val metadata: IndexFile.File, defaultFormat: String, private val downloadSide: Side) : IOptionDetails {
	var cachedFile: ManifestFile.File? = null

	private var err: Exception? = null
	val exceptionDetails get() = err?.let { e -> ExceptionDetails(name, e) }

	fun failed() = err != null

	var alreadyUpToDate = false
	private var metadataRequired = true
	private var invalidated = false
	// If file is new or isOptional changed to true, the option needs to be presented again
	private var newOptional = true

	val isOptional get() = metadata.linkedFile?.isOptional ?: false

	fun isNewOptional() = isOptional && newOptional

	fun correctSide() = metadata.linkedFile?.side?.hasSide(downloadSide) ?: true

	override val name get() = metadata.name

	// Ensure that an update is done if it changes from false to true, or from true to false
	override var optionValue: Boolean
		get() = cachedFile?.optionValue ?: true
		set(value) {
			if (value && !optionValue) { // Ensure that an update is done if it changes from false to true, or from true to false
				alreadyUpToDate = false
			}
			cachedFile?.optionValue = value
		}

	override val optionDescription get() = metadata.linkedFile?.option?.description ?: ""

	init {
		if (metadata.hashFormat?.isEmpty() != false) {
			metadata.hashFormat = defaultFormat
		}
	}

	fun invalidate() {
		invalidated = true
		alreadyUpToDate = false
	}

	fun updateFromCache(cachedFile: ManifestFile.File?) {
		if (err != null) return

		if (cachedFile == null) {
			this.cachedFile = ManifestFile.File()
			return
		}
		this.cachedFile = cachedFile
		if (!invalidated) {
			val currHash = try {
				getHash(metadata.hashFormat!!, metadata.hash!!)
			} catch (e: Exception) {
				err = e
				return
			}
			if (currHash == cachedFile.hash) { // Already up to date
				alreadyUpToDate = true
				metadataRequired = false
			}
		}
		if (cachedFile.isOptional) {
			// Because option selection dialog might set this task to true/false, metadata is always needed to download
			// the file, and to show the description and name
			metadataRequired = true
		}
	}

	fun downloadMetadata(parentIndexFile: IndexFile, indexUri: SpaceSafeURI) {
		if (err != null) return

		if (metadataRequired) {
			try {
				// Retrieve the linked metadata file
				metadata.downloadMeta(parentIndexFile, indexUri)
			} catch (e: Exception) {
				err = e
				return
			}
			cachedFile?.let { cachedFile ->
				val linkedFile = metadata.linkedFile
				if (linkedFile != null) {
					linkedFile.option?.let { opt ->
						if (opt.optional) {
							if (cachedFile.isOptional) {
								// isOptional didn't change
								newOptional = false
							} else {
								// isOptional false -> true, set option to it's default value
								// TODO: preserve previous option value, somehow??
								cachedFile.optionValue = opt.defaultValue
							}
						}
					}
					cachedFile.isOptional = isOptional
					cachedFile.onlyOtherSide = !correctSide()
				}
			}
		}
	}

	/**
	 * Check if the file in the destination location is already valid
	 * Must be done after metadata retrieval
	 */
	fun validateExistingFile(packFolder: String) {
		if (!alreadyUpToDate) {
			try {
				// TODO: only do this for files that didn't exist before or have been modified since last full update?
				val destPath = Paths.get(packFolder, metadata.destURI.toString())
				FileSystem.SYSTEM.source(destPath.toOkioPath()).buffer().use { src ->
					val hash: Hash
					val fileHashFormat: String
					val linkedFile = metadata.linkedFile

					if (linkedFile != null) {
						hash = linkedFile.hash
						fileHashFormat = linkedFile.download!!.hashFormat!!
					} else {
						hash = metadata.getHashObj()
						fileHashFormat = metadata.hashFormat!!
					}

					val fileSource = getHasher(fileHashFormat).getHashingSource(src)
					fileSource.buffer().readAll(blackholeSink())
					if (fileSource.hashIsEqual(hash)) {
						alreadyUpToDate = true

						// Update the manifest file
						cachedFile = (cachedFile ?: ManifestFile.File()).also {
							try {
								it.hash = metadata.getHashObj()
							} catch (e: Exception) {
								err = e
								return
							}
							it.isOptional = isOptional
							it.cachedLocation = metadata.destURI.toString()
							metadata.linkedFile?.let { linked ->
								try {
									it.linkedFileHash = linked.hash
								} catch (e: Exception) {
									err = e
								}
							}
						}
					}
				}
			} catch (e: IOException) {
				// Ignore exceptions; if the file doesn't exist we'll be downloading it
			}
		}
	}

	fun download(packFolder: String, indexUri: SpaceSafeURI) {
		if (err != null) return

		// Ensure wrong-side or optional false files are removed
		cachedFile?.let {
			if ((it.isOptional && !it.optionValue) || !correctSide()) {
				if (it.cachedLocation != null) {
					try {
						Files.deleteIfExists(Paths.get(packFolder, it.cachedLocation))
					} catch (e: IOException) {
						Log.warn("Failed to delete file before downloading", e)
					}
				}
				it.cachedLocation = null
			}
		}
		if (alreadyUpToDate) return

		val destPath = Paths.get(packFolder, metadata.destURI.toString())

		// Don't update files marked with preserve if they already exist on disk
		if (metadata.preserve) {
			if (destPath.toFile().exists()) {
				return
			}
		}

		// TODO: if already exists and has correct hash, ignore?
		// TODO: add .disabled support?

		try {
			val hash: Hash
			val fileHashFormat: String
			val linkedFile = metadata.linkedFile

			if (linkedFile != null) {
				hash = linkedFile.hash
				fileHashFormat = linkedFile.download!!.hashFormat!!
			} else {
				hash = metadata.getHashObj()
				fileHashFormat = metadata.hashFormat!!
			}

			val src = metadata.getSource(indexUri)
			val fileSource = getHasher(fileHashFormat).getHashingSource(src)
			val data = Buffer()

			// Read all the data into a buffer (very inefficient for large files! but allows rollback if hash check fails)
			// TODO: should we instead rename the existing file, then stream straight to the file and rollback from the renamed file?
			fileSource.buffer().use {
				it.readAll(data)
			}

			if (fileSource.hashIsEqual(hash)) {
				// isDirectory follows symlinks, but createDirectories doesn't
				try {
					Files.createDirectories(destPath.parent)
				} catch (e: java.nio.file.FileAlreadyExistsException) {
					if (!Files.isDirectory(destPath.parent)) {
						throw e
					}
				}
				Files.copy(data.inputStream(), destPath, StandardCopyOption.REPLACE_EXISTING)
				data.clear()
			} else {
				// TODO: move println to something visible in the error window
				println("Invalid hash for " + metadata.destURI.toString())
				println("Calculated: " + fileSource.hash)
				println("Expected:   $hash")
				// Attempt to get the SHA256 hash
				val sha256 = HashingSink.sha256(blackholeSink())
				data.readAll(sha256)
				println("SHA256 hash value: " + sha256.hash)
				err = Exception("Hash invalid!")
				data.clear()
				return
			}
			cachedFile?.cachedLocation?.let {
				if (destPath != Paths.get(packFolder, it)) {
					// Delete old file if location changes
					try {
						Files.delete(Paths.get(packFolder, cachedFile!!.cachedLocation))
					} catch (e: IOException) {
						// Continue, as it was probably already deleted?
						// TODO: log it
					}
				}
			}
		} catch (e: Exception) {
			err = e
			return
		}

		// Update the manifest file
		cachedFile = (cachedFile ?: ManifestFile.File()).also {
			try {
				it.hash = metadata.getHashObj()
			} catch (e: Exception) {
				err = e
				return
			}
			it.isOptional = isOptional
			it.cachedLocation = metadata.destURI.toString()
			metadata.linkedFile?.let { linked ->
				try {
					it.linkedFileHash = linked.hash
				} catch (e: Exception) {
					err = e
				}
			}
		}
	}

	companion object {
		@JvmStatic
		fun createTasksFromIndex(index: IndexFile, defaultFormat: String, downloadSide: Side): List<DownloadTask> {
			val tasks = ArrayList<DownloadTask>()
			for (file in Objects.requireNonNull(index.files)) {
				tasks.add(DownloadTask(file, defaultFormat, downloadSide))
			}
			return tasks
		}
	}
}