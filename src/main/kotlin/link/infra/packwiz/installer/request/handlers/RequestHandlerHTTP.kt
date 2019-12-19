package link.infra.packwiz.installer.request.handlers

import link.infra.packwiz.installer.metadata.SpaceSafeURI
import link.infra.packwiz.installer.request.IRequestHandler
import okio.Source
import okio.source

open class RequestHandlerHTTP : IRequestHandler {
	override fun matchesHandler(loc: SpaceSafeURI): Boolean {
		val scheme = loc.scheme
		return "http" == scheme || "https" == scheme
	}

	override fun getFileSource(loc: SpaceSafeURI): Source? {
		val conn = loc.toURL().openConnection()
		// TODO: when do we send specific headers??? should there be a way to signal this?
		// github *sometimes* requires it, sometimes not!
		//conn.addRequestProperty("Accept", "application/octet-stream");
		conn.apply {
			// 30 second read timeout
			readTimeout = 30 * 1000
		}
		return conn.getInputStream().source()
	}
}