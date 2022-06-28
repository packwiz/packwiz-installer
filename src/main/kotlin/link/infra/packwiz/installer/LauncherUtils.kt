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
        Succesful,
        NoChanges,
        Cancelled,
        NotFound, // We'll use the NotFound as the neutral return type for now
    }

    fun handleMultiMC(pf: PackFile, gson: Gson): LauncherStatus {
        // MultiMC MC and loader version checker
        val manifestPath = Paths.get(opts.multimcFolder, "mmc-pack.json").toString()
        val manifestFile = File(manifestPath)

        if (!manifestFile.exists()) {
            return LauncherStatus.NotFound
        }

        val multimcManifest = try {
            JsonParser.parseReader(manifestFile.reader())
        } catch (e: JsonIOException) {
            throw Exception("Cannot read the MultiMC pack file", e)
        } catch (e: JsonSyntaxException) {
            throw Exception("Invalid MultiMC pack file", e)
        }.asJsonObject

        Log.info("Loaded MultiMC config")

        // We only support format 1, if it gets updated in the future we'll have to handle that
        // There's only version 1 for now tho, so that's good
        if (multimcManifest["formatVersion"].asInt != 1) {
            throw Exception("Invalid MultiMC format version")
        }

        var manifestModified = false
        val modLoaders = hashMapOf("net.minecraft" to "minecraft", "net.minecraftforge" to "forge", "net.fabricmc.fabric-loader" to "fabric", "org.quiltmc.quilt-loader" to "quilt", "com.mumfrey.liteloader" to "liteloader")
        val modLoadersClasses = modLoaders.entries.associate{(k,v)-> v to k}
        var modLoaderFound = false
        val modLoadersFound = HashMap<String, String>() // Key: modLoader, Value: Version
        val components = multimcManifest["components"].asJsonArray
        for (componentObj in components) {
            val component = componentObj.asJsonObject

            val version = component["version"].asString
            // If we find any of the modloaders we support, we save it and check the version
            if (modLoaders.containsKey(component["uid"].asString)) {
                val modLoader = modLoaders.getValue(component["uid"].asString)
                if (modLoader != "minecraft")
                    modLoaderFound = true // Only set to true if modLoader isn't Minecraft
                modLoadersFound[modLoader] = version
                if (version != pf.versions?.get(modLoader)) {
                    manifestModified = true
                    component.addProperty("version", pf.versions?.get(modLoader))
                }
            }
        }

        // If we can't find the mod loader in the MultiMC file, we add it
        if (!modLoaderFound) {
            // Using this filter and loop to handle multiple handlers
            for ((_, loader) in modLoaders
                    .filter { it.value != "minecraft" && !modLoadersFound.containsKey(it.value) && pf.versions?.containsKey(it.value) == true }
            ) {
                components.add(gson.toJsonTree(hashMapOf("uid" to modLoadersClasses.get(loader), "version" to pf.versions?.get(loader))))
            }
        }

        // If mc version change detected, and fabric mappings are found, delete them, MultiMC will add and re-dl the correct one
        if (modLoadersFound["minecraft"] != pf.versions?.getValue("minecraft"))
            components.find { it.asJsonObject["uid"].asString == "net.fabricmc.intermediary" }?.asJsonObject?.let { components.remove(it) }

        if (manifestModified) {
            // The manifest has been modified, so before saving it we'll ask the user
            // if they wanna update it, continue without updating it, or exit
            val oldVers = modLoadersFound.map { Pair(it.key, it.value) }
            val newVers = pf.versions!!.map { Pair(it.key, it.value) }


            when (ui.showUpdateConfirmationDialog(oldVers, newVers)) {
                IUserInterface.UpdateConfirmationResult.CANCELLED -> {
                    return LauncherStatus.Cancelled
                }
                IUserInterface.UpdateConfirmationResult.CONTINUE -> {
                    return LauncherStatus.Succesful // Returning succesful as... Well, the user is telling us to continue
                }
                else -> {} // Compiler is giving warning about "non-exhaustive when", so i'll just add an empty one
            }

            manifestFile.writeText(gson.toJson(multimcManifest))
            Log.info("Updated modpack Minecrafts and/or the modloaders version")

            return LauncherStatus.Succesful
        }

        return LauncherStatus.NoChanges
    }
}