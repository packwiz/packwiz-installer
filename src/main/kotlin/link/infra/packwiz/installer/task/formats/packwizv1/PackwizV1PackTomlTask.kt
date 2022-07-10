package link.infra.packwiz.installer.task.formats.packwizv1

import com.google.gson.annotations.SerializedName
import link.infra.packwiz.installer.metadata.hash.Hash
import link.infra.packwiz.installer.metadata.hash.HashFormat
import link.infra.packwiz.installer.target.path.PackwizPath
import link.infra.packwiz.installer.task.CacheKey
import link.infra.packwiz.installer.task.Task
import link.infra.packwiz.installer.task.TaskCombinedResult
import link.infra.packwiz.installer.task.TaskContext

class PackwizV1PackTomlTask(ctx: TaskContext, val path: PackwizPath<*>): Task<PackwizV1PackFile>(ctx) {
	// TODO: make hierarchically defined by caller? - then changing the pack format type doesn't leave junk in the cache
	private var cache by ctx.cache[CacheKey<Hash<*>>("packwiz.v1.packtoml.hash", 1)]

	private class PackFile {
		var name: String? = null
		var index: IndexFileLoc? = null

		class IndexFileLoc {
			var file: String? = null
			@SerializedName("hash-format")
			var hashFormat: HashFormat<*>? = null
			var hash: String? = null
		}

		var versions: Map<String, String>? = null
	}

	private val internalResult by lazy {
		// TODO: query, parse JSON
		val packFile = PackFile()
			//Toml().read(InputStreamReader(path.source(ctx.clients).inputStream(), "UTF-8")).to(PackFile::class.java)

		val hashFormat = (packFile.index?.hashFormat ?: throw RuntimeException("Hash format required"))
		val resolved = PackwizV1PackFile(
			packFile.name ?: throw RuntimeException("Name required"), // TODO: better exception handling
			path.resolve(packFile.index?.file ?: throw RuntimeException("File required")),
			hashFormat.fromString(packFile.index?.hash ?: throw RuntimeException("Hash required"))
		)
		val hash = hashFormat.fromString("whatever was just read")

		TaskCombinedResult(resolved, wasUpdated(::cache, hash))
	}

	override val value by internalResult::result
	override val upToDate by internalResult::upToDate
}