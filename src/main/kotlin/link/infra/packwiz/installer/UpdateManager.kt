package link.infra.packwiz.installer

import cc.ekblad.toml.decode
import com.google.gson.GsonBuilder
import com.google.gson.JsonIOException
import com.google.gson.JsonSyntaxException
import link.infra.packwiz.installer.DownloadTask.Companion.createTasksFromIndex
import link.infra.packwiz.installer.metadata.DownloadMode
import link.infra.packwiz.installer.metadata.IndexFile
import link.infra.packwiz.installer.metadata.ManifestFile
import link.infra.packwiz.installer.metadata.PackFile
import link.infra.packwiz.installer.metadata.curseforge.resolveCfMetadata
import link.infra.packwiz.installer.metadata.hash.Hash
import link.infra.packwiz.installer.metadata.hash.HashFormat
import link.infra.packwiz.installer.request.RequestException
import link.infra.packwiz.installer.target.ClientHolder
import link.infra.packwiz.installer.target.Side
import link.infra.packwiz.installer.target.path.PackwizFilePath
import link.infra.packwiz.installer.target.path.PackwizPath
import link.infra.packwiz.installer.ui.IUserInterface
import link.infra.packwiz.installer.ui.IUserInterface.CancellationResult
import link.infra.packwiz.installer.ui.IUserInterface.ExceptionListResult
import link.infra.packwiz.installer.ui.data.InstallProgress
import link.infra.packwiz.installer.util.Log
import okio.buffer
import java.io.IOException
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.nio.file.Files
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
		val packFile: PackwizPath<*>,
		val manifestFile: PackwizFilePath,
		val packFolder: PackwizFilePath,
		val multimcFolder: PackwizFilePath,
		val side: Side,
		val timeout: Long,
	)

	// TODO: make this return a value based on results?
	private fun start() {
		val clientHolder = ClientHolder()
		ui.cancelCallback = {
			clientHolder.close()
		}

		ui.submitProgress(InstallProgress("Loading manifest file..."))
		val gson = GsonBuilder()
			.registerTypeAdapter(Hash::class.java, Hash.TypeHandler())
			.registerTypeAdapter(PackwizFilePath::class.java, PackwizPath.adapterRelativeTo(opts.packFolder))
			.enableComplexMapKeySerialization()
			.create()
		val manifest = try {
			// TODO: kotlinx.serialisation?
			InputStreamReader(opts.manifestFile.source(clientHolder).inputStream(), StandardCharsets.UTF_8).use { reader ->
				gson.fromJson(reader, ManifestFile::class.java)
			}
		} catch (e: RequestException.Response.File.FileNotFound) {
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
			val src = opts.packFile.source(clientHolder)
			HashFormat.SHA256.source(src)
		} catch (e: Exception) {
			// TODO: ensure suppressed/caused exceptions are shown?
			ui.showErrorAndExit("Failed to download pack.toml", e)
		}
		val pf = packFileSource.buffer().use {
			try {
				PackFile.mapper(opts.packFile).decode<PackFile>(it.inputStream())
			} catch (e: IllegalStateException) {
				ui.showErrorAndExit("Failed to parse pack.toml", e)
			}
		}

		if (ui.cancelButtonPressed) {
			showCancellationDialog()
			handleCancellation()
		}

		// Launcher checks
		val lu = LauncherUtils(opts, ui)

		// MultiMC MC and loader version checker
		ui.submitProgress(InstallProgress("Loading MultiMC pack file..."))
		try {
			when (lu.handleMultiMC(pf, gson)) {
				LauncherUtils.LauncherStatus.CANCELLED -> cancelled = true
				LauncherUtils.LauncherStatus.NOT_FOUND -> Log.info("MultiMC not detected")
				else -> {}
			}
			handleCancellation()
		} catch (e: Exception) {
			ui.showErrorAndExit(e.message!!, e)
		}

		if (ui.cancelButtonPressed) {
			showCancellationDialog()
			handleCancellation()
		}

		ui.submitProgress(InstallProgress("Checking local files..."))

		// Invalidation checking must be done here, as it must happen before pack/index hashes are checked
		val invalidatedUris: MutableList<PackwizFilePath> = ArrayList()
		for ((fileUri, file) in manifest.cachedFiles) {
			// ignore onlyOtherSide files
			if (file.onlyOtherSide) {
				continue
			}

			var invalid = false
			// if isn't optional, or is optional but optionValue == true
			if (!file.isOptional || file.optionValue) {
				if (file.cachedLocation != null) {
					if (!file.cachedLocation!!.nioPath.toFile().exists()) {
						invalid = true
					}
				} else {
					// if cachedLocation == null, should probably be installed!!
					invalid = true
				}
			}
			if (invalid) {
				Log.info("File ${fileUri.filename} invalidated, marked for redownloading")
				invalidatedUris.add(fileUri)
			}
		}

		if (manifest.packFileHash?.let { it == packFileSource.hash } == true && invalidatedUris.isEmpty()) {
			// todo: --force?
			ui.submitProgress(InstallProgress("Modpack is already up to date!", 1, 1))
			if (manifest.cachedFiles.any { it.value.isOptional }) {
				ui.awaitOptionalButton(false, opts.timeout)
			}
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
			processIndex(
				pf.index.file,
				pf.index.hashFormat.fromString(pf.index.hash),
				pf.index.hashFormat,
				manifest,
				invalidatedUris,
				clientHolder
			)
		} catch (e1: Exception) {
			ui.showErrorAndExit("Failed to process index file", e1)
		}

		handleCancellation()


		// If there were errors, don't write the manifest/index hashes, to ensure they are rechecked later
		if (errorsOccurred) {
			manifest.indexFileHash = null
			manifest.packFileHash = null
		} else {
			manifest.packFileHash = packFileSource.hash
		}

		manifest.cachedSide = opts.side
		try {
			Files.newBufferedWriter(opts.manifestFile.nioPath, StandardCharsets.UTF_8).use { writer -> gson.toJson(manifest, writer) }
		} catch (e: IOException) {
			ui.showErrorAndExit("Failed to save local manifest file", e)
		}
	}

	private fun processIndex(indexUri: PackwizPath<*>, indexHash: Hash<*>, hashFormat: HashFormat<*>, manifest: ManifestFile, invalidatedFiles: List<PackwizFilePath>, clientHolder: ClientHolder) {
		if (manifest.indexFileHash == indexHash && invalidatedFiles.isEmpty()) {
			ui.submitProgress(InstallProgress("Modpack files are already up to date!", 1, 1))
			if (manifest.cachedFiles.any { it.value.isOptional }) {
				ui.awaitOptionalButton(false, opts.timeout)
			}
			if (!ui.optionsButtonPressed) {
				return
			}
			if (ui.cancelButtonPressed) {
				showCancellationDialog()
				return
			}
		}
		manifest.indexFileHash = indexHash

		val indexFileSource = try {
			val src = indexUri.source(clientHolder)
			hashFormat.source(src)
		} catch (e: Exception) {
			ui.showErrorAndExit("Failed to download index file", e)
		}

		val indexFile = try {
			IndexFile.mapper(indexUri).decode<IndexFile>(indexFileSource.buffer().inputStream())
		} catch (e: IllegalStateException) {
			ui.showErrorAndExit("Failed to parse index file", e)
		}
		if (indexHash != indexFileSource.hash) {
			ui.showErrorAndExit("Your index file hash is invalid! The pack developer should packwiz refresh on the pack again")
		}

		if (ui.cancelButtonPressed) {
			showCancellationDialog()
			return
		}

		ui.submitProgress(InstallProgress("Checking local files..."))
		// TODO: use kotlin filtering/FP rather than an iterator?
		val it: MutableIterator<Map.Entry<PackwizFilePath, ManifestFile.File>> = manifest.cachedFiles.entries.iterator()
		while (it.hasNext()) {
			val (uri, file) = it.next()
			if (file.cachedLocation != null) {
				var alreadyDeleted = false
				// Delete if option value has been set to false
				if (file.isOptional && !file.optionValue) {
					try {
						Files.deleteIfExists(file.cachedLocation!!.nioPath)
					} catch (e: IOException) {
						Log.warn("Failed to delete optional disabled file", e)
					}
					// Set to null, as it doesn't exist anymore
					file.cachedLocation = null
					alreadyDeleted = true
				}
				if (indexFile.files.none { it.file.rebase(opts.packFolder) == uri }) { // File has been removed from the index
					if (!alreadyDeleted) {
						try {
							Files.deleteIfExists(file.cachedLocation!!.nioPath)
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
		val tasks = createTasksFromIndex(indexFile, opts.side)
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
			} else if (invalidatedFiles.contains(f.metadata.file.rebase(opts.packFolder))) {
				f.invalidate()
			}
			val file = manifest.cachedFiles[f.metadata.file.rebase(opts.packFolder)]
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
		tasks.parallelStream().forEach { f -> f.downloadMetadata(clientHolder) }

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
		tasks.removeAll { it.failed() }
		val optionTasks = tasks.filter(DownloadTask::correctSide).filter(DownloadTask::isOptional).toList()
		val optionsChanged = optionTasks.any(DownloadTask::isNewOptional)
		if (optionTasks.isNotEmpty() && !optionsChanged) {
			if (!ui.optionsButtonPressed) {
				// TODO: this is so ugly
				ui.submitProgress(InstallProgress("Reconfigure optional mods?", 0,1))
				ui.awaitOptionalButton(true, opts.timeout)
				if (ui.cancelButtonPressed) {
					showCancellationDialog()
					return
				}
			}
		}
		// If options changed, present all options again
		if (ui.optionsButtonPressed || optionsChanged) {
			// new ArrayList is required so it's an IOptionDetails rather than a DownloadTask list
			if (ui.showOptions(ArrayList(optionTasks))) {
				cancelled = true
				handleCancellation()
			}
		}
		// TODO: keep this enabled? then apply changes after download process?
		ui.disableOptionsButton(optionTasks.isNotEmpty())

		while (true) {
			when (validateAndResolve(tasks, clientHolder)) {
				ResolveResult.RETRY -> {}
				ResolveResult.QUIT -> return
				ResolveResult.SUCCESS -> break
			}
		}

		// TODO: different thread pool type?
		val threadPool = Executors.newFixedThreadPool(10)
		val completionService: CompletionService<DownloadTask> = ExecutorCompletionService(threadPool)
		tasks.forEach { t ->
			completionService.submit {
				t.download(opts.packFolder, clientHolder)
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
						manifest.cachedFiles.putIfAbsent(task.metadata.file.rebase(opts.packFolder), oldFile)
					} else { null }
				} else {
					manifest.cachedFiles.putIfAbsent(task.metadata.file.rebase(opts.packFolder), file)
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
				// TODO: close client holder in more places?
				clientHolder.close()
				threadPool.shutdown()
				cancelled = true
				return
			}
		}

		// Shut down the thread pool when the update is done
		threadPool.shutdown()

		val failedTasks2ElectricBoogaloo = tasks.asSequence().map(DownloadTask::exceptionDetails).filterNotNull().toList()
		if (failedTasks2ElectricBoogaloo.isNotEmpty()) {
			errorsOccurred = true
			when (ui.showExceptions(failedTasks2ElectricBoogaloo, tasks.size, false)) {
				ExceptionListResult.CONTINUE -> {}
				ExceptionListResult.CANCEL -> cancelled = true
				ExceptionListResult.IGNORE -> cancelledStartGame = true
			}
		}
	}

	enum class ResolveResult {
		RETRY,
		QUIT,
		SUCCESS;
	}

	private fun validateAndResolve(nonFailedFirstTasks: List<DownloadTask>, clientHolder: ClientHolder): ResolveResult {
		ui.submitProgress(InstallProgress("Validating existing files..."))

		// Validate existing files
		for (downloadTask in nonFailedFirstTasks.filter(DownloadTask::correctSide)) {
			downloadTask.validateExistingFile(opts.packFolder, clientHolder)
		}

		// Resolve CurseForge metadata
		val cfFiles = nonFailedFirstTasks.asSequence().filter { !it.alreadyUpToDate }
			.filter(DownloadTask::correctSide)
			.map { it.metadata }
			.filter { it.linkedFile != null }
			.filter { it.linkedFile!!.download.mode == DownloadMode.CURSEFORGE }.toList()
		if (cfFiles.isNotEmpty()) {
			ui.submitProgress(InstallProgress("Resolving CurseForge metadata..."))
			val resolveFailures = resolveCfMetadata(cfFiles, opts.packFolder, clientHolder)
			if (resolveFailures.isNotEmpty()) {
				errorsOccurred = true
				return when (ui.showExceptions(resolveFailures, cfFiles.size, true)) {
					ExceptionListResult.CONTINUE -> {
						ResolveResult.RETRY
					}
					ExceptionListResult.CANCEL -> {
						cancelled = true
						ResolveResult.QUIT
					}
					ExceptionListResult.IGNORE -> {
						cancelledStartGame = true
						ResolveResult.QUIT
					}
				}
			}
		}
		return ResolveResult.SUCCESS
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