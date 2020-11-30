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
import link.infra.packwiz.installer.ui.InputStateHandler
import link.infra.packwiz.installer.ui.InstallProgress
import okio.buffer
import java.io.FileNotFoundException
import java.io.FileReader
import java.io.FileWriter
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*
import java.util.concurrent.CompletionService
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorCompletionService
import java.util.concurrent.Executors
import kotlin.system.exitProcess

class UpdateManager internal constructor(private val opts: Options, val ui: IUserInterface, private val stateHandler: InputStateHandler) {
	private var cancelled = false
	private var cancelledStartGame = false
	private var errorsOccurred = false

	init {
		start()
	}

	data class Options(
			var downloadURI: SpaceSafeURI? = null,
			var manifestFile: String = "packwiz.json", // TODO: make configurable
			var packFolder: String = ".",
			var side: Side = Side.CLIENT
	) {
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
			ManifestFile()
		} catch (e: JsonSyntaxException) {
			ui.handleExceptionAndExit(e)
			return
		} catch (e: JsonIOException) {
			ui.handleExceptionAndExit(e)
			return
		}

		if (stateHandler.cancelButton) {
			showCancellationDialog()
			handleCancellation()
		}

		ui.submitProgress(InstallProgress("Loading pack file..."))
		val packFileSource = try {
			val src = getFileSource(opts.downloadURI!!)
			getHasher("sha256").getHashingSource(src)
		} catch (e: Exception) {
			// TODO: run cancellation window?
			ui.handleExceptionAndExit(e)
			return
		}
		val pf = packFileSource.buffer().use {
			try {
				Toml().read(it.inputStream()).to(PackFile::class.java)
			} catch (e: IllegalStateException) {
				ui.handleExceptionAndExit(e)
				return
			}
		}

		if (stateHandler.cancelButton) {
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
				println("File $fileUri invalidated, marked for redownloading")
				invalidatedUris.add(fileUri)
			}
		}

		if (manifest.packFileHash?.let { packFileSource.hashIsEqual(it) } == true && invalidatedUris.isEmpty()) {
			println("Modpack is already up to date!")
			// todo: --force?
			if (!stateHandler.optionsButton) {
				return
			}
		}

		println("Modpack name: " + pf.name)

		if (stateHandler.cancelButton) {
			showCancellationDialog()
			handleCancellation()
		}
		try {
			val index = pf.index!!
			getNewLoc(opts.downloadURI, index.file)?.let { newLoc ->
				index.hashFormat?.let { hashFormat ->
					processIndex(
						newLoc,
						getHash(index.hashFormat!!, index.hash!!),
						hashFormat,
						manifest,
						invalidatedUris
					)
				}
			}
		} catch (e1: Exception) {
			ui.handleExceptionAndExit(e1)
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
			// TODO: add message?
			ui.handleException(e)
		}
	}

	private fun checkOptions() {
		// TODO: implement
	}

	private fun processIndex(indexUri: SpaceSafeURI, indexHash: Hash, hashFormat: String, manifest: ManifestFile, invalidatedUris: List<SpaceSafeURI>) {
		if (manifest.indexFileHash == indexHash && invalidatedUris.isEmpty()) {
			println("Modpack files are already up to date!")
			if (!stateHandler.optionsButton) {
				return
			}
		}
		manifest.indexFileHash = indexHash

		val indexFileSource = try {
			val src = getFileSource(indexUri)
			getHasher(hashFormat).getHashingSource(src)
		} catch (e: Exception) {
			// TODO: run cancellation window?
			ui.handleExceptionAndExit(e)
			return
		}
		val indexFile = try {
			Toml().read(indexFileSource.buffer().inputStream()).to(IndexFile::class.java)
		} catch (e: IllegalStateException) {
			ui.handleExceptionAndExit(e)
			return
		}
		if (!indexFileSource.hashIsEqual(indexHash)) {
			ui.handleExceptionAndExit(RuntimeException("Your index hash is invalid! Please run packwiz refresh on the pack again"))
			return
		}
		if (stateHandler.cancelButton) {
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
						// TODO: should this be shown to the user in some way?
						e.printStackTrace()
					}
					// Set to null, as it doesn't exist anymore
					file.cachedLocation = null
					alreadyDeleted = true
				}
				if (indexFile.files.none { it.file == uri }) { // File has been removed from the index
					if (!alreadyDeleted) {
						try {
							Files.deleteIfExists(Paths.get(opts.packFolder, file.cachedLocation))
						} catch (e: IOException) { // TODO: should this be shown to the user in some way?
							e.printStackTrace()
						}
					}
					it.remove()
				}
			}
		}

		if (stateHandler.cancelButton) {
			showCancellationDialog()
			return
		}
		ui.submitProgress(InstallProgress("Comparing new files..."))

		// TODO: progress bar?
		if (indexFile.files.isEmpty()) {
			println("Warning: Index is empty!")
		}
		val tasks = createTasksFromIndex(indexFile, indexFile.hashFormat, opts.side)
		// If the side changes, invalidate EVERYTHING just in case
		// Might not be needed, but done just to be safe
		val invalidateAll = opts.side != manifest.cachedSide
		if (invalidateAll) {
			println("Side changed, invalidating all mods")
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

		if (stateHandler.cancelButton) {
			showCancellationDialog()
			return
		}

		// Let's hope downloadMetadata is a pure function!!!
		tasks.parallelStream().forEach { f -> f.downloadMetadata(indexFile, indexUri) }

		val failedTaskDetails = tasks.asSequence().map(DownloadTask::exceptionDetails).filterNotNull().toList()
		if (failedTaskDetails.isNotEmpty()) {
			errorsOccurred = true
			val exceptionListResult: ExceptionListResult
			exceptionListResult = try {
				ui.showExceptions(failedTaskDetails, tasks.size, true).get()
			} catch (e: InterruptedException) { // Interrupted means cancelled???
				ui.handleExceptionAndExit(e)
				return
			} catch (e: ExecutionException) {
				ui.handleExceptionAndExit(e)
				return
			}
			when (exceptionListResult) {
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

		if (stateHandler.cancelButton) {
			showCancellationDialog()
			return
		}

		// TODO: task failed function?
		val nonFailedFirstTasks = tasks.filter { t -> !t.failed() }.toList()
		val optionTasks = nonFailedFirstTasks.filter(DownloadTask::correctSide).filter(DownloadTask::isOptional).toList()
		// If options changed, present all options again
		if (stateHandler.optionsButton || optionTasks.any(DownloadTask::isNewOptional)) {
			// new ArrayList is required so it's an IOptionDetails rather than a DownloadTask list
			val cancelledResult = ui.showOptions(ArrayList(optionTasks))
			try {
				if (cancelledResult.get()) {
					cancelled = true
					// TODO: Should the UI be closed somehow??
					return
				}
			} catch (e: InterruptedException) {
				// Interrupted means cancelled???
				ui.handleExceptionAndExit(e)
			} catch (e: ExecutionException) {
				ui.handleExceptionAndExit(e)
			}
		}
		ui.disableOptionsButton()

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
			var task: DownloadTask?
			task = try {
				completionService.take().get()
			} catch (e: InterruptedException) {
				ui.handleException(e)
				null
			} catch (e: ExecutionException) {
				ui.handleException(e)
				null
			}
			// Update manifest - If there were no errors cachedFile has already been modified in place (good old pass by reference)
			task?.cachedFile?.let { file ->
				if (task.failed()) {
					val oldFile = file.revert
					if (oldFile != null) {
						task.metadata.file?.let { uri -> manifest.cachedFiles.putIfAbsent(uri, oldFile) }
					} else { null }
				} else {
					task.metadata.file?.let { uri -> manifest.cachedFiles.putIfAbsent(uri, file) }
				}
			}

			var progress: String
			if (task != null) {
				val exDetails = task.exceptionDetails
				if (exDetails != null) {
					progress = "Failed to download ${exDetails.name}: ${exDetails.exception.message}"
					exDetails.exception.printStackTrace()
				} else {
					progress = "Downloaded ${task.name}"
				}
			} else {
				progress = "Failed to download, unknown reason"
			}
			ui.submitProgress(InstallProgress(progress, i + 1, tasks.size))

			if (stateHandler.cancelButton) { // Stop all tasks, don't launch the game (it's in an invalid state!)
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
			val exceptionListResult: ExceptionListResult
			exceptionListResult = try {
				ui.showExceptions(failedTasks2ElectricBoogaloo, tasks.size, false).get()
			} catch (e: InterruptedException) {
				// Interrupted means cancelled???
				ui.handleExceptionAndExit(e)
				return
			} catch (e: ExecutionException) {
				ui.handleExceptionAndExit(e)
				return
			}
			when (exceptionListResult) {
				ExceptionListResult.CONTINUE -> {}
				ExceptionListResult.CANCEL -> cancelled = true
				ExceptionListResult.IGNORE -> cancelledStartGame = true
			}
		}
	}

	private fun showCancellationDialog() {
		val cancellationResult: CancellationResult
		cancellationResult = try {
			ui.showCancellationDialog().get()
		} catch (e: InterruptedException) {
			// Interrupted means cancelled???
			ui.handleExceptionAndExit(e)
			return
		} catch (e: ExecutionException) {
			ui.handleExceptionAndExit(e)
			return
		}
		when (cancellationResult) {
			CancellationResult.QUIT -> cancelled = true
			CancellationResult.CONTINUE -> cancelledStartGame = true
		}
	}

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