package link.infra.packwiz.installer

import com.google.gson.GsonBuilder
import com.google.gson.JsonIOException
import com.google.gson.JsonSyntaxException
import com.google.gson.annotations.SerializedName
import com.moandjiezana.toml.Toml
import link.infra.packwiz.installer.DownloadTask.Companion.createTasksFromIndex
import link.infra.packwiz.installer.metadata.IndexFile
import link.infra.packwiz.installer.metadata.ManifestFile
import link.infra.packwiz.installer.metadata.PackFile
import link.infra.packwiz.installer.metadata.SpaceSafeURI
import link.infra.packwiz.installer.metadata.hash.Hash
import link.infra.packwiz.installer.metadata.hash.HashUtils.getHash
import link.infra.packwiz.installer.metadata.hash.HashUtils.getHasher
import link.infra.packwiz.installer.request.HandlerManager.getFileSource
import link.infra.packwiz.installer.request.HandlerManager.getNewLoc
import link.infra.packwiz.installer.ui.IUserInterface
import link.infra.packwiz.installer.ui.IUserInterface.CancellationResult
import link.infra.packwiz.installer.ui.IUserInterface.ExceptionListResult
import link.infra.packwiz.installer.ui.data.InstallProgress
import link.infra.packwiz.installer.util.Log
import link.infra.packwiz.installer.util.ifletOrErr
import okio.buffer
import java.io.FileNotFoundException
import java.io.FileReader
import java.io.FileWriter
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Paths
import java.util.concurrent.CompletionService
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorCompletionService
import java.util.concurrent.Executors
import kotlin.system.exitProcess

class UpdateManager internal constructor(private val opts: Options, val ui: IUserInterface) {
	private var cancelled = false
	private var cancelledStartGame = false
	private var errorsOccurred = false

	init {
		start()
	}

	data class Options(
		val downloadURI: SpaceSafeURI,
		val manifestFile: String,
		val packFolder: String,
		val side: Side
	) {
		// Horrible workaround for default params not working cleanly with nullable values
		companion object {
			fun construct(downloadURI: SpaceSafeURI, manifestFile: String?, packFolder: String?, side: Side?) =
				Options(downloadURI, manifestFile ?: "packwiz.json", packFolder ?: ".", side ?: Side.CLIENT)
		}

		enum class Side {
			@SerializedName("client")
			CLIENT("client"),
			@SerializedName("server")
			SERVER("server"),
			@SerializedName("both")
			@Suppress("unused")
			BOTH("both", arrayOf(CLIENT, SERVER));

			private val sideName: String
			private val depSides: Array<Side>?

			constructor(sideName: String) {
				this.sideName = sideName.toLowerCase()
				depSides = null
			}

			constructor(sideName: String, depSides: Array<Side>) {
				this.sideName = sideName.toLowerCase()
				this.depSides = depSides
			}

			override fun toString() = sideName

			fun hasSide(tSide: Side): Boolean {
				if (this == tSide) {
					return true
				}
				if (depSides != null) {
					for (depSide in depSides) {
						if (depSide == tSide) {
							return true
						}
					}
				}
				return false
			}

			companion object {
				fun from(name: String): Side? {
					val lowerName = name.toLowerCase()
					for (side in values()) {
						if (side.sideName == lowerName) {
							return side
						}
					}
					return null
				}
			}
		}
	}

	private fun start() {
		checkOptions()

		ui.submitProgress(InstallProgress("Loading manifest file..."))
		val gson = GsonBuilder().registerTypeAdapter(Hash::class.java, Hash.TypeHandler()).create()
		val manifest = try {
			gson.fromJson(FileReader(Paths.get(opts.packFolder, opts.manifestFile).toString()),
					ManifestFile::class.java)
		} catch (e: FileNotFoundException) {
			ui.firstInstall = true
			ManifestFile()
		} catch (e: JsonSyntaxException) {
			ui.showErrorAndExit("Invalid local manifest file, try deleting ${opts.manifestFile}", e)
		} catch (e: JsonIOException) {
			ui.showErrorAndExit("Failed to read local manifest file, try deleting ${opts.manifestFile}", e)
		}

		if (ui.cancelButtonPressed) {
			showCancellationDialog()
			handleCancellation()
		}

		ui.submitProgress(InstallProgress("Loading pack file..."))
		val packFileSource = try {
			val src = getFileSource(opts.downloadURI)
			getHasher("sha256").getHashingSource(src)
		} catch (e: Exception) {
			ui.showErrorAndExit("Failed to download pack.toml", e)
		}
		val pf = packFileSource.buffer().use {
			try {
				Toml().read(it.inputStream()).to(PackFile::class.java)
			} catch (e: IllegalStateException) {
				ui.showErrorAndExit("Failed to parse pack.toml", e)
			}
		}

		if (ui.cancelButtonPressed) {
			showCancellationDialog()
			handleCancellation()
		}

		ui.submitProgress(InstallProgress("Checking local files..."))

		// Invalidation checking must be done here, as it must happen before pack/index hashes are checked
		val invalidatedUris: MutableList<SpaceSafeURI> = ArrayList()
		for ((fileUri, file) in manifest.cachedFiles) {
			// ignore onlyOtherSide files
			if (file.onlyOtherSide) {
				continue
			}

			var invalid = false
			// if isn't optional, or is optional but optionValue == true
			if (!file.isOptional || file.optionValue) {
				if (file.cachedLocation != null) {
					if (!Paths.get(opts.packFolder, file.cachedLocation).toFile().exists()) {
						invalid = true
					}
				} else {
					// if cachedLocation == null, should probably be installed!!
					invalid = true
				}
			}
			if (invalid) {
				Log.info("File $fileUri invalidated, marked for redownloading")
				invalidatedUris.add(fileUri)
			}
		}

		if (manifest.packFileHash?.let { packFileSource.hashIsEqual(it) } == true && invalidatedUris.isEmpty()) {
			Log.info("Modpack is already up to date!")
			// todo: --force?
			if (!ui.optionsButtonPressed) {
				return
			}
		}

		Log.info("Modpack name: ${pf.name}")

		if (ui.cancelButtonPressed) {
			showCancellationDialog()
			handleCancellation()
		}
		try {
			// TODO: switch to OkHttp for better redirect handling
			ui.ifletOrErr(pf.index, "No index file found, or the pack file is empty; note that Java doesn't automatically follow redirects from HTTP to HTTPS (and may cause this error)") { index ->
				ui.ifletOrErr(index.hashFormat, index.hash, "Pack has no hash or hashFormat for index") { hashFormat, hash ->
					ui.ifletOrErr(getNewLoc(opts.downloadURI, index.file), "Pack has invalid index file: " + index.file) { newLoc ->
						processIndex(
							newLoc,
							getHash(hashFormat, hash),
							hashFormat,
							manifest,
							invalidatedUris
						)
					}
				}
			}
		} catch (e1: Exception) {
			ui.showErrorAndExit("Failed to process index file", e1)
		}

		handleCancellation()

		// TODO: update MMC params, java args etc

		// If there were errors, don't write the manifest/index hashes, to ensure they are rechecked later
		if (errorsOccurred) {
			manifest.indexFileHash = null
			manifest.packFileHash = null
		} else {
			manifest.packFileHash = packFileSource.hash
		}

		manifest.cachedSide = opts.side
		try {
			FileWriter(Paths.get(opts.packFolder, opts.manifestFile).toString()).use { writer -> gson.toJson(manifest, writer) }
		} catch (e: IOException) {
			ui.showErrorAndExit("Failed to save local manifest file", e)
		}
	}

	private fun checkOptions() {
		// TODO: implement
	}

	private fun processIndex(indexUri: SpaceSafeURI, indexHash: Hash, hashFormat: String, manifest: ManifestFile, invalidatedUris: List<SpaceSafeURI>) {
		if (manifest.indexFileHash == indexHash && invalidatedUris.isEmpty()) {
			Log.info("Modpack files are already up to date!")
			if (!ui.optionsButtonPressed) {
				return
			}
		}
		manifest.indexFileHash = indexHash

		val indexFileSource = try {
			val src = getFileSource(indexUri)
			getHasher(hashFormat).getHashingSource(src)
		} catch (e: Exception) {
			ui.showErrorAndExit("Failed to download index file", e)
		}

		val indexFile = try {
			Toml().read(indexFileSource.buffer().inputStream()).to(IndexFile::class.java)
		} catch (e: IllegalStateException) {
			ui.showErrorAndExit("Failed to parse index file", e)
		}
		if (!indexFileSource.hashIsEqual(indexHash)) {
			ui.showErrorAndExit("Your index file hash is invalid! The pack developer should packwiz refresh on the pack again")
		}

		if (ui.cancelButtonPressed) {
			showCancellationDialog()
			return
		}

		ui.submitProgress(InstallProgress("Checking local files..."))
		// TODO: use kotlin filtering/FP rather than an iterator?
		val it: MutableIterator<Map.Entry<SpaceSafeURI, ManifestFile.File>> = manifest.cachedFiles.entries.iterator()
		while (it.hasNext()) {
			val (uri, file) = it.next()
			if (file.cachedLocation != null) {
				var alreadyDeleted = false
				// Delete if option value has been set to false
				if (file.isOptional && !file.optionValue) {
					try {
						Files.deleteIfExists(Paths.get(opts.packFolder, file.cachedLocation))
					} catch (e: IOException) {
						Log.warn("Failed to delete optional disabled file", e)
					}
					// Set to null, as it doesn't exist anymore
					file.cachedLocation = null
					alreadyDeleted = true
				}
				if (indexFile.files.none { it.file == uri }) { // File has been removed from the index
					if (!alreadyDeleted) {
						try {
							Files.deleteIfExists(Paths.get(opts.packFolder, file.cachedLocation))
						} catch (e: IOException) {
							Log.warn("Failed to delete file removed from index", e)
						}
					}
					it.remove()
				}
			}
		}

		if (ui.cancelButtonPressed) {
			showCancellationDialog()
			return
		}
		ui.submitProgress(InstallProgress("Comparing new files..."))

		// TODO: progress bar?
		if (indexFile.files.isEmpty()) {
			Log.warn("Index is empty!")
		}
		val tasks = createTasksFromIndex(indexFile, indexFile.hashFormat, opts.side)
		// If the side changes, invalidate EVERYTHING just in case
		// Might not be needed, but done just to be safe
		val invalidateAll = opts.side != manifest.cachedSide
		if (invalidateAll) {
			Log.info("Side changed, invalidating all mods")
		}
		tasks.forEach{ f ->
			// TODO: should linkedfile be checked as well? should this be done in the download section?
			if (invalidateAll) {
				f.invalidate()
			} else if (invalidatedUris.contains(f.metadata.file)) {
				f.invalidate()
			}
			val file = manifest.cachedFiles[f.metadata.file]
			// Ensure the file can be reverted later if necessary - the DownloadTask modifies the file so if it fails we need the old version back
			file?.backup()
			// If it is null, the DownloadTask will make a new empty cachedFile
			f.updateFromCache(file)
		}

		if (ui.cancelButtonPressed) {
			showCancellationDialog()
			return
		}

		// Let's hope downloadMetadata is a pure function!!!
		tasks.parallelStream().forEach { f -> f.downloadMetadata(indexFile, indexUri) }

		val failedTaskDetails = tasks.asSequence().map(DownloadTask::exceptionDetails).filterNotNull().toList()
		if (failedTaskDetails.isNotEmpty()) {
			errorsOccurred = true
			when (ui.showExceptions(failedTaskDetails, tasks.size, true)) {
				ExceptionListResult.CONTINUE -> {}
				ExceptionListResult.CANCEL -> {
					cancelled = true
					return
				}
				ExceptionListResult.IGNORE -> {
					cancelledStartGame = true
					return
				}
			}
		}

		if (ui.cancelButtonPressed) {
			showCancellationDialog()
			return
		}

		// TODO: task failed function?
		val nonFailedFirstTasks = tasks.filter { t -> !t.failed() }.toList()
		val optionTasks = nonFailedFirstTasks.filter(DownloadTask::correctSide).filter(DownloadTask::isOptional).toList()
		// If options changed, present all options again
		if (ui.optionsButtonPressed || optionTasks.any(DownloadTask::isNewOptional)) {
			// new ArrayList is required so it's an IOptionDetails rather than a DownloadTask list
			if (ui.showOptions(ArrayList(optionTasks))) {
				cancelled = true
				handleCancellation()
			}
		}
		// TODO: keep this enabled? then apply changes after download process?
		ui.disableOptionsButton(optionTasks.isNotEmpty())

		// TODO: different thread pool type?
		val threadPool = Executors.newFixedThreadPool(10)
		val completionService: CompletionService<DownloadTask> = ExecutorCompletionService(threadPool)
		tasks.forEach { t ->
			completionService.submit {
				t.download(opts.packFolder, indexUri)
				t
			}
		}
		for (i in tasks.indices) {
			val task: DownloadTask = try {
				completionService.take().get()
			} catch (e: InterruptedException) {
				ui.showErrorAndExit("Interrupted when consuming download tasks", e)
			} catch (e: ExecutionException) {
				ui.showErrorAndExit("Failed to execute download task", e)
			}
			// Update manifest - If there were no errors cachedFile has already been modified in place (good old pass by reference)
			task.cachedFile?.let { file ->
				if (task.failed()) {
					val oldFile = file.revert
					if (oldFile != null) {
						task.metadata.file?.let { uri -> manifest.cachedFiles.putIfAbsent(uri, oldFile) }
					} else { null }
				} else {
					task.metadata.file?.let { uri -> manifest.cachedFiles.putIfAbsent(uri, file) }
				}
			}

			val exDetails = task.exceptionDetails
			val progress = if (exDetails != null) {
				"Failed to download ${exDetails.name}: ${exDetails.exception.message}"
			} else {
				"Downloaded ${task.name}"
			}
			ui.submitProgress(InstallProgress(progress, i + 1, tasks.size))

			if (ui.cancelButtonPressed) { // Stop all tasks, don't launch the game (it's in an invalid state!)
				threadPool.shutdown()
				cancelled = true
				return
			}
		}

		// Shut down the thread pool when the update is done
		threadPool.shutdown()

		val failedTasks2ElectricBoogaloo = nonFailedFirstTasks.asSequence().map(DownloadTask::exceptionDetails).filterNotNull().toList()
		if (failedTasks2ElectricBoogaloo.isNotEmpty()) {
			errorsOccurred = true
			when (ui.showExceptions(failedTasks2ElectricBoogaloo, tasks.size, false)) {
				ExceptionListResult.CONTINUE -> {}
				ExceptionListResult.CANCEL -> cancelled = true
				ExceptionListResult.IGNORE -> cancelledStartGame = true
			}
		}
	}

	private fun showCancellationDialog() {
		when (ui.showCancellationDialog()) {
			CancellationResult.QUIT -> cancelled = true
			CancellationResult.CONTINUE -> cancelledStartGame = true
		}
	}

	// TODO: move to UI?
	private fun handleCancellation() {
		if (cancelled) {
			println("Update cancelled by user!")
			exitProcess(1)
		} else if (cancelledStartGame) {
			println("Update cancelled by user! Continuing to start game...")
			exitProcess(0)
		}
	}
}