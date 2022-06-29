package link.infra.packwiz.installer

import com.google.gson.Gson
import com.google.gson.JsonIOException
import com.google.gson.JsonParser
import com.google.gson.JsonSyntaxException
import link.infra.packwiz.installer.metadata.PackFile
import link.infra.packwiz.installer.ui.IUserInterface
import link.infra.packwiz.installer.util.Log
import java.io.File
import java.nio.file.Paths

class LauncherUtils internal constructor(private val opts: UpdateManager.Options, val ui: IUserInterface) {
	enum class LauncherStatus {
		SUCCESSFUL,
		NO_CHANGES,
		CANCELLED,
		NOT_FOUND, // When there is no mmc-pack.json file found (i.e. MultiMC is not being used)
	}

	fun handleMultiMC(pf: PackFile, gson: Gson): LauncherStatus {
		// MultiMC MC and loader version checker
		val manifestPath = Paths.get(opts.multimcFolder, "mmc-pack.json").toString()
		val manifestFile = File(manifestPath)

		if (!manifestFile.exists()) {
			return LauncherStatus.NOT_FOUND
		}

		val multimcManifest = manifestFile.reader().use {
			try {
				JsonParser.parseReader(it)
			} catch (e: JsonIOException) {
				throw Exception("Cannot read the MultiMC pack file", e)
			} catch (e: JsonSyntaxException) {
				throw Exception("Invalid MultiMC pack file", e)
			}.asJsonObject
		}

		Log.info("Loaded MultiMC config")

		// We only support format 1, if it gets updated in the future we'll have to handle that
		// There's only version 1 for now tho, so that's good
		if (multimcManifest["formatVersion"]?.asInt != 1) {
			throw Exception("Unsupported MultiMC format version ${multimcManifest["formatVersion"]}")
		}

		var manifestModified = false
		val modLoaders = hashMapOf(
			"net.minecraft" to "minecraft",
			"net.minecraftforge" to "forge",
			"net.fabricmc.fabric-loader" to "fabric",
			"org.quiltmc.quilt-loader" to "quilt",
			"com.mumfrey.liteloader" to "liteloader")
		val modLoadersClasses = modLoaders.entries.associate{(k,v)-> v to k}
		val loaderVersionsFound = HashMap<String, String?>()
		val outdatedLoaders = mutableSetOf<String>()
		val components = multimcManifest["components"]?.asJsonArray ?: throw Exception("Invalid mmc-pack.json: no components key")
		components.removeAll {
			val component = it.asJsonObject

			val version = component["version"]?.asString
			// If we find any of the modloaders we support, we save it and check the version
			if (modLoaders.containsKey(component["uid"]?.asString)) {
				val modLoader = modLoaders.getValue(component["uid"]!!.asString)
				loaderVersionsFound[modLoader] = version
				if (version != pf.versions?.get(modLoader)) {
					outdatedLoaders.add(modLoader)
					true // Delete component; cached metadata is invalid and will be re-added
				} else {
					false // Already up to date; cached metadata is valid
				}
			} else { false } // Not a known loader / MC
		}

		for ((_, loader) in modLoaders
			.filter {
				(!loaderVersionsFound.containsKey(it.value) || outdatedLoaders.contains(it.value))
					&& pf.versions?.containsKey(it.value) == true }
		) {
			manifestModified = true
			components.add(gson.toJsonTree(
				hashMapOf("uid" to modLoadersClasses[loader], "version" to pf.versions?.get(loader)))
			)
		}

		// If inconsistent Intermediary mappings version is found, delete it - MultiMC will add and re-dl the correct one
		components.find { it.isJsonObject && it.asJsonObject["uid"]?.asString == "net.fabricmc.intermediary" }?.let {
			if (it.asJsonObject["version"]?.asString != pf.versions?.get("minecraft")) {
				components.remove(it)
				manifestModified = true
			}
		}

		if (manifestModified) {
			// The manifest has been modified, so before saving it we'll ask the user
			// if they wanna update it, continue without updating it, or exit
			val oldVers = loaderVersionsFound.map { Pair(it.key, it.value) }
			val newVers = pf.versions!!.map { Pair(it.key, it.value) }

			when (ui.showUpdateConfirmationDialog(oldVers, newVers)) {
				IUserInterface.UpdateConfirmationResult.CANCELLED -> {
					return LauncherStatus.CANCELLED
				}
				IUserInterface.UpdateConfirmationResult.CONTINUE -> {
					return LauncherStatus.SUCCESSFUL
				}
				else -> {}
			}

			manifestFile.writeText(gson.toJson(multimcManifest))
			Log.info("Successfully updated mmc-pack.json based on version metadata")

			return LauncherStatus.SUCCESSFUL
		}

		return LauncherStatus.NO_CHANGES
	}
}