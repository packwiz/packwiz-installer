package link.infra.packwiz.installer.target.path

import link.infra.packwiz.installer.request.RequestException
import link.infra.packwiz.installer.target.ClientHolder
import okio.*

class PackwizFilePath(private val base: Path, path: String? = null): PackwizPath<PackwizFilePath>(path) {
	@Throws(RequestException::class)
	override fun source(clientHolder: ClientHolder): BufferedSource {
		val resolved = if (path == null) { base } else { this.base.resolve(path, true) }
		try {
			return clientHolder.fileSystem.source(resolved).buffer()
		} catch (e: FileNotFoundException) {
			throw RequestException.Response.File.FileNotFound(resolved.toString())
		} catch (e: IOException) {
			throw RequestException.Response.File.Other(e)
		}
	}

	val nioPath: java.nio.file.Path get() {
		val resolved = if (path == null) { base } else { this.base.resolve(path, true) }
		return resolved.toNioPath()
	}

	override fun construct(path: String): PackwizFilePath = PackwizFilePath(base, path)

	override val folder: Boolean
		get() = pathFolder ?: (base.segments.last() == "")
	override val filename: String
		get() = pathFilename ?: base.segments.last()

	override fun equals(other: Any?): Boolean {
		if (this === other) return true
		if (javaClass != other?.javaClass) return false
		if (!super.equals(other)) return false

		other as PackwizFilePath

		if (base != other.base) return false

		return true
	}

	override fun hashCode(): Int {
		var result = super.hashCode()
		result = 31 * result + base.hashCode()
		return result
	}

	override fun toString() = nioPath.toString()
}