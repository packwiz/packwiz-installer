package link.infra.packwiz.installer.request.handlers

import link.infra.packwiz.installer.request.IRequestHandler
import okhttp3.HttpUrl
import okio.Source
import okio.source
import java.nio.file.Paths

open class RequestHandlerFile : IRequestHandler {
	override fun matchesHandler(loc: HttpUrl): Boolean {
		return "file" == loc.scheme
	}

	override fun getFileSource(loc: HttpUrl): Source? {
		val path = Paths.get(loc.toUri())
		return path.source()
	}
}
