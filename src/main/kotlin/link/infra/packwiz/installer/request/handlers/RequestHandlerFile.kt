package link.infra.packwiz.installer.request.handlers

import link.infra.packwiz.installer.metadata.SpaceSafeURI
import link.infra.packwiz.installer.request.IRequestHandler
import okio.Source
import okio.source
import java.nio.file.Paths

open class RequestHandlerFile : IRequestHandler {
	override fun matchesHandler(loc: SpaceSafeURI): Boolean {
		return "file" == loc.scheme
	}

	override fun getFileSource(loc: SpaceSafeURI): Source? {
		val path = Paths.get(loc.toURL().toURI())
		return path.source()
	}
}
