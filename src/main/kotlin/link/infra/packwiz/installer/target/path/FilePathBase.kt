package link.infra.packwiz.installer.target.path

import link.infra.packwiz.installer.request.RequestException
import link.infra.packwiz.installer.target.ClientHolder
import okio.*

data class FilePathBase(private val path: Path): PackwizPath.Base, PackwizPath.SinkableBase {
	override fun source(path: String, clientHolder: ClientHolder): BufferedSource {
		val resolved = this.path.resolve(path, true)
		try {
			return clientHolder.fileSystem.source(resolved).buffer()
		} catch (e: FileNotFoundException) {
			throw RequestException.Response.File.FileNotFound(resolved.toString())
		} catch (e: IOException) {
			throw RequestException.Response.File.Other(e)
		}
	}

	override fun sink(path: String, clientHolder: ClientHolder): BufferedSink {
		val resolved = this.path.resolve(path, true)
		try {
			return clientHolder.fileSystem.sink(resolved).buffer()
		} catch (e: FileNotFoundException) {
			throw RequestException.Response.File.FileNotFound(resolved.toString())
		} catch (e: IOException) {
			throw RequestException.Response.File.Other(e)
		}
	}
}