package link.infra.packwiz.installer

import com.google.gson.GsonBuilder
import com.google.gson.JsonIOException
import com.google.gson.JsonParser
import com.google.gson.JsonSyntaxException
import com.moandjiezana.toml.Toml
import link.infra.packwiz.installer.DownloadTask.Companion.createTasksFromIndex
import link.infra.packwiz.installer.metadata.IndexFile
import link.infra.packwiz.installer.metadata.ManifestFile
import link.infra.packwiz.installer.metadata.PackFile
import link.infra.packwiz.installer.metadata.SpaceSafeURI
import link.infra.packwiz.installer.metadata.curseforge.resolveCfMetadata
import link.infra.packwiz.installer.metadata.hash.Hash
import link.infra.packwiz.installer.metadata.hash.HashUtils.getHash
import link.infra.packwiz.installer.metadata.hash.HashUtils.getHasher
import link.infra.packwiz.installer.request.HandlerManager.getFileSource
import link.infra.packwiz.installer.request.HandlerManager.getNewLoc
import link.infra.packwiz.installer.target.Side
import link.infra.packwiz.installer.ui.IUserInterface
import link.infra.packwiz.installer.ui.IUserInterface.CancellationResult
import link.infra.packwiz.installer.ui.IUserInterface.ExceptionListResult
import link.infra.packwiz.installer.ui.data.InstallProgress
import link.infra.packwiz.installer.util.Log
import link.infra.packwiz.installer.util.ifletOrErr
import okio.buffer
import java.io.*
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
		val multimcFolder: String,
		val side: Side
	) {
		// Horrible workaround for default params not working cleanly with nullable values
		companion object {
			fun construct(downloadURI: SpaceSafeURI, manifestFile: String?, packFolder: String?, multimcFolder: String?, side: Side?) =
				Options(downloadURI, manifestFile ?: "packwiz.json", packFolder ?: ".", multimcFolder ?: "..", side ?: Side.CLIENT)
		}

	}

	private fun start() {
		checkOptions()

		ui.submitProgress(InstallProgress("Loading manifest file..."))
		val gson = GsonBuilder().registerTypeAdapter(Hash::class.java, Hash.TypeHandler()).setPrettyPrinting().create()
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
				Toml().read(InputStreamReader(it.inputStream(), "UTF-8")).to(PackFile::class.java)
			} catch (e: IllegalStateException) {
				ui.showErrorAndExit("Failed to parse pack.toml", e)
			}
		}

		if (ui.cancelButtonPressed) {
			showCancellationDialog()
			handleCancellation()
		}

		// MultiMC MC and loader version checker
		val multimcManifestPath = Paths.get(opts.multimcFolder, "mmc-pack.json").toString();
		val multimcManifestFile = File(multimcManifestPath)

		if (multimcManifestFile.exists()) {
			ui.submitProgress(InstallProgress("Loading MultiMC pack file..."))

			val multimcManifest = try {
				JsonParser.parseReader(multimcManifestFile.reader())
			} catch (e: JsonIOException) {
				ui.showErrorAndExit("Cannot read the MultiMC pack file", e)
			} catch (e: JsonSyntaxException) {
				ui.showErrorAndExit("Invalid MultiMC pack file", e)
			}.asJsonObject

			Log.info("Loaded MultiMC config")

			// We only support format 1, if it gets updated in the future we'll have to handle that
			// There's only version 1 for now tho, so that's good
			if (multimcManifest["formatVersion"].asInt != 1) {
				ui.showErrorAndExit("Invalid MultiMC format version")
			}

			var manifestModified = false
			val modLoaders = hashMapOf("net.minecraftforge" to "forge", "net.fabricmc.fabric-loader" to "fabric")
			val modLoadersClasses = modLoaders.entries.associate{(k,v)-> v to k}
			var modLoaderFound = false
			var modLoader: String? = null
			var modLoaderOldVer: String? = null
			var mcOldVer = "Unknown"
			val components = multimcManifest["components"].asJsonArray
			for (componentObj in components) {
				val component = componentObj.asJsonObject

				val version = component["version"].asString
				when (component["uid"].asString) {
					"net.minecraft" -> {
						mcOldVer = version
						if (version != pf.versions?.get("minecraft")) {
							manifestModified = true
							component.addProperty("version", pf.versions?.get("minecraft"))
						}
					}
				}
				// If we find any of the modloaders we support, we save it and check the version
				if (modLoaders.containsKey(component["uid"].asString)) {
					modLoaderFound = true
					modLoader = modLoaders.getValue(component["uid"].asString)
					modLoaderOldVer = version
					if (version != pf.versions?.get(modLoader)) {
						manifestModified = true
						component.addProperty("version", pf.versions?.get(modLoader))
					}
				}
			}

			// If we can't find the mod loader in the MultiMC file, we add it
			if (!modLoaderFound) {
				for ((loaderClass, loader) in modLoaders) {
					if (pf.versions?.containsKey(loader) ?: false) {
						modLoader = loader
						break
					}
				}
				// If we can't find it in the modpack pack file, something is wrong, we'll stop here
				if (modLoader == null) ui.showErrorAndExit("Mod Loader not found in modpack pack file")

				components.add(gson.toJsonTree(hashMapOf("uid" to modLoadersClasses.get(modLoader), "version" to pf.versions?.get(modLoader))))
				manifestModified = true
			}

			if (manifestModified) {
				// The manifest has been modified, so before saving it we'll ask the user
				// if they wanna update it, continue without updating it, or exit
				val oldVers = listOf(Pair("minecraft", mcOldVer), Pair(modLoader!!, modLoaderOldVer))
				val newVers = listOf(Pair("minecraft", pf.versions?.getValue("minecraft")), Pair(modLoader!!, pf.versions?.getValue(modLoader!!)))

				when (ui.showUpdateConfirmationDialog(oldVers, newVers)) {
					IUserInterface.UpdateConfirmationResult.CANCELLED -> {
						cancelled = true
					}
					IUserInterface.UpdateConfirmationResult.CONTINUE -> {
						cancelledStartGame = true
					}
				}
				handleCancellation()

				multimcManifestFile.writeText(gson.toJson(multimcManifest))
				Log.info("Updated modpack Minecraft and/or ${modLoader?.replaceFirstChar { it.uppercase() }} version")
			}

			if (ui.cancelButtonPressed) {
				showCancellationDialog()
				handleCancellation()
			}
		} else {
			Log.warn("MultiMC installation not detected... Tried looking for it in $multimcManifestPath")
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
			// todo: --force?
			ui.submitProgress(InstallProgress("Modpack is already up to date!", 1, 1))
			if (manifest.cachedFiles.any { it.value.isOptional }) {
				ui.awaitOptionalButton(false)
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
			ui.submitProgress(InstallProgress("Modpack files are already up to date!", 1, 1))
			if (manifest.cachedFiles.any { it.value.isOptional }) {
				ui.awaitOptionalButton(false)
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
			val src = getFileSource(indexUri)
			getHasher(hashFormat).getHashingSource(src)
		} catch (e: Exception) {
			ui.showErrorAndExit("Failed to download index file", e)
		}

		val indexFile = try {
			Toml().read(InputStreamReader(indexFileSource.buffer().inputStream(), "UTF-8")).to(IndexFile::class.java)
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
		tasks.removeAll { it.failed() }
		val optionTasks = tasks.filter(DownloadTask::correctSide).filter(DownloadTask::isOptional).toList()
		val optionsChanged = optionTasks.any(DownloadTask::isNewOptional)
		if (optionTasks.isNotEmpty() && !optionsChanged) {
			if (!ui.optionsButtonPressed) {
				// TODO: this is so ugly
				ui.submitProgress(InstallProgress("Reconfigure optional mods?", 0,1))
				ui.awaitOptionalButton(true)
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
			when (validateAndResolve(tasks)) {
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

	private fun validateAndResolve(nonFailedFirstTasks: List<DownloadTask>): ResolveResult {
		ui.submitProgress(InstallProgress("Validating existing files..."))

		// Validate existing files
		for (downloadTask in nonFailedFirstTasks.filter(DownloadTask::correctSide)) {
			downloadTask.validateExistingFile(opts.packFolder)
		}

		// Resolve CurseForge metadata
		val cfFiles = nonFailedFirstTasks.asSequence().filter { !it.alreadyUpToDate }
			.filter(DownloadTask::correctSide)
			.map { it.metadata }
			.filter { it.linkedFile != null }
			.filter { it.linkedFile?.download?.mode == "metadata:curseforge" }.toList()
		if (cfFiles.isNotEmpty()) {
			ui.submitProgress(InstallProgress("Resolving CurseForge metadata..."))
			val resolveFailures = resolveCfMetadata(cfFiles)
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