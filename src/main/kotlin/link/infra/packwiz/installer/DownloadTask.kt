package link.infra.packwiz.installer

import link.infra.packwiz.installer.metadata.IndexFile
import link.infra.packwiz.installer.metadata.ManifestFile
import link.infra.packwiz.installer.metadata.SpaceSafeURI
import link.infra.packwiz.installer.metadata.hash.Hash
import link.infra.packwiz.installer.metadata.hash.HashUtils.getHash
import link.infra.packwiz.installer.metadata.hash.HashUtils.getHasher
import link.infra.packwiz.installer.ui.ExceptionDetails
import link.infra.packwiz.installer.ui.IOptionDetails
import okio.Buffer
import okio.HashingSink
import okio.buffer
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.*

internal class DownloadTask private constructor(val metadata: IndexFile.File, defaultFormat: String, private val downloadSide: UpdateManager.Options.Side) : IOptionDetails {
	var cachedFile: ManifestFile.File? = null

	private var err: Exception? = null
	val exceptionDetails get() = err?.let { e -> ExceptionDetails(name, e) }

	fun failed() = err != null

	private var alreadyUpToDate = false
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

	fun download(packFolder: String, indexUri: SpaceSafeURI) {
		if (err != null) return

		// Ensure it is removed
		cachedFile?.let {
			if (!it.optionValue || !correctSide()) {
				if (it.cachedLocation == null) return

				try {
					Files.deleteIfExists(Paths.get(packFolder, it.cachedLocation))
				} catch (e: IOException) {
					// TODO: how much of a problem is this? use log4j/other log library to show warning?
					e.printStackTrace()
				}
				it.cachedLocation = null
			}
		}
		if (alreadyUpToDate) return

		// TODO: should I be validating JSON properly, or this fine!!!!!!!??
		assert(metadata.destURI != null)
		val destPath = Paths.get(packFolder, metadata.destURI.toString())

		// Don't update files marked with preserve if they already exist on disk
		if (metadata.preserve) {
			if (destPath.toFile().exists()) {
				return
			}
		}

		try {
			val hash: Hash
			val fileHashFormat: String
			val linkedFile = metadata.linkedFile

			if (linkedFile != null) {
				hash = linkedFile.hash
				fileHashFormat = Objects.requireNonNull(Objects.requireNonNull(linkedFile.download)!!.hashFormat)!!
			} else {
				hash = metadata.getHashObj()
				fileHashFormat = Objects.requireNonNull(metadata.hashFormat)!!
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
				if (!Files.isDirectory(destPath.parent)) {
					Files.createDirectories(destPath.parent)
				}
				Files.copy(data.inputStream(), destPath, StandardCopyOption.REPLACE_EXISTING)
				data.clear()
			} else {
				// TODO: no more PRINTLN!!!!!!!!!
				println("Invalid hash for " + metadata.destURI.toString())
				println("Calculated: " + fileSource.hash)
				println("Expected:   $hash")
				// Attempt to get the SHA256 hash
				val sha256 = HashingSink.sha256(okio.blackholeSink())
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
		fun createTasksFromIndex(index: IndexFile, defaultFormat: String, downloadSide: UpdateManager.Options.Side): List<DownloadTask> {
			val tasks = ArrayList<DownloadTask>()
			for (file in Objects.requireNonNull(index.files)) {
				tasks.add(DownloadTask(file, defaultFormat, downloadSide))
			}
			return tasks
		}
	}
}