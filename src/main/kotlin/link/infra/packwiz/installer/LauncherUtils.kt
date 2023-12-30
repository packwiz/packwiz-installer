package link.infra.packwiz.installer

import com.google.gson.Gson
import com.google.gson.JsonIOException
import com.google.gson.JsonParser
import com.google.gson.JsonSyntaxException
import link.infra.packwiz.installer.metadata.PackFile
import link.infra.packwiz.installer.ui.IUserInterface
import link.infra.packwiz.installer.util.Log
import kotlin.io.path.reader
import kotlin.io.path.writeText

class LauncherUtils internal constructor(private val opts: UpdateManager.Options, val ui: IUserInterface) {
	enum class LauncherStatus {
		SUCCESSFUL,
		NO_CHANGES,
		CANCELLED,
		NOT_FOUND, // When there is no mmc-pack.json file found (i.e. MultiMC is not being used)
	}

	fun handleMultiMC(pf: PackFile, gson: Gson): LauncherStatus {
		// MultiMC MC and loader version checker
		val manifestPath = opts.multimcFolder / "mmc-pack.json"

		if (!manifestPath.nioPath.toFile().exists()) {
			return LauncherStatus.NOT_FOUND
		}

		val multimcManifest = manifestPath.nioPath.reader().use {
			try {
				JsonParser.parseReader(it)
			} catch (e: JsonIOException) {
				throw Exception(Msgs.umInvalidMultiMCIO(), e)
			} catch (e: JsonSyntaxException) {
				throw Exception(Msgs.umInvalidMultiMCSyntax(), e)
			}.asJsonObject
		}

		Log.info(Msgs.umLoadedMultiMC())

		// We only support format 1, if it gets updated in the future we'll have to handle that
		// There's only version 1 for now tho, so that's good
		if (multimcManifest["formatVersion"]?.asInt != 1) {
			throw Exception(Msgs.umUnsupportedMultiMCVersion(multimcManifest["formatVersion"]))
		}

		var manifestModified = false
		val modLoaders = hashMapOf(
			"net.minecraft" to "minecraft",
			"net.minecraftforge" to "forge",
			"net.fabricmc.fabric-loader" to "fabric",
			"org.quiltmc.quilt-loader" to "quilt",
			"com.mumfrey.liteloader" to "liteloader"
		)
		// MultiMC requires components to be sorted; this is defined in the MultiMC meta repo, but they seem to
		// be the same for every version so they are just used directly here
		val componentOrders = mapOf(
			"net.minecraft" to -2,
			"org.lwjgl" to -1,
			"org.lwjgl3" to -1,
			"net.minecraftforge" to 5,
			"net.fabricmc.fabric-loader" to 10,
			"org.quiltmc.quilt-loader" to 10,
			"com.mumfrey.liteloader" to 10,
			"net.fabricmc.intermediary" to 11
		)
		val modLoadersClasses = modLoaders.entries.associate{(k,v)-> v to k}
		val loaderVersionsFound = HashMap<String, String?>()
		val outdatedLoaders = mutableSetOf<String>()
		val components = multimcManifest["components"]?.asJsonArray ?: throw Exception(Msgs.umInvalidMultiMCNoComponent())
		components.removeAll {
			val component = it.asJsonObject

			val version = component["version"]?.asString
			// If we find any of the modloaders we support, we save it and check the version
			if (modLoaders.containsKey(component["uid"]?.asString)) {
				val modLoader = modLoaders.getValue(component["uid"]!!.asString)
				loaderVersionsFound[modLoader] = version
				if (version != pf.versions[modLoader]) {
					outdatedLoaders.add(modLoader)
					true // Delete component; cached metadata is invalid and will be re-added
				} else {
					false // Already up to date; cached metadata is valid
				}
			} else { false } // Not a known loader / MC
		}

		for ((_, loader) in modLoaders
			.filter {
				(!loaderVersionsFound.containsKey(it.value) || outdatedLoaders.contains(it.value)) && pf.versions.containsKey(it.value)
			}
		) {
			manifestModified = true
			components.add(gson.toJsonTree(
				hashMapOf("uid" to modLoadersClasses[loader], "version" to pf.versions[loader]))
			)
		}

		// If inconsistent Intermediary mappings version is found, delete it - MultiMC will add and re-dl the correct one
		components.find { it.isJsonObject && it.asJsonObject["uid"]?.asString == "net.fabricmc.intermediary" }?.let {
			if (it.asJsonObject["version"]?.asString != pf.versions["minecraft"]) {
				components.remove(it)
				manifestModified = true
			}
		}

		if (manifestModified) {
			// Sort manifest by component order
			val sortedComponents = components.sortedWith(nullsLast(compareBy {
				if (it.isJsonObject) {
					componentOrders[it.asJsonObject["uid"]?.asString]
				} else { null }
			}))
			components.removeAll { true }
			sortedComponents.forEach { components.add(it) }

			// The manifest has been modified, so before saving it we'll ask the user
			// if they wanna update it, continue without updating it, or exit
			val oldVers = loaderVersionsFound.map { Pair(it.key, it.value) }
			val newVers = pf.versions.map { Pair(it.key, it.value) }

			when (ui.showUpdateConfirmationDialog(oldVers, newVers)) {
				IUserInterface.UpdateConfirmationResult.CANCELLED -> {
					return LauncherStatus.CANCELLED
				}
				IUserInterface.UpdateConfirmationResult.CONTINUE -> {
					return LauncherStatus.SUCCESSFUL
				}
				else -> {}
			}

			manifestPath.nioPath.writeText(gson.toJson(multimcManifest))
			Log.info(Msgs.umUpdatedMultiMC())

			return LauncherStatus.SUCCESSFUL
		}

		return LauncherStatus.NO_CHANGES
	}
}