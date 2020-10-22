package link.infra.packwiz.installer.request.handlers

import link.infra.packwiz.installer.metadata.SpaceSafeURI
import link.infra.packwiz.installer.request.IRequestHandler
import okio.Source
import okio.source
import java.net.HttpURLConnection

open class RequestHandlerHTTP : IRequestHandler {
	override fun matchesHandler(loc: SpaceSafeURI): Boolean {
		val scheme = loc.scheme
		return "http" == scheme || "https" == scheme
	}

	override fun getFileSource(loc: SpaceSafeURI): Source? {
		val conn = loc.toURL().openConnection() as HttpURLConnection
		// TODO: when do we send specific headers??? should there be a way to signal this?
		conn.addRequestProperty("Accept", "application/octet-stream")
		// TODO: include version?
		conn.addRequestProperty("User-Agent", "packwiz-installer")

		conn.apply {
			// 30 second read timeout
			readTimeout = 30 * 1000
			requestMethod = "GET"
		}
		return conn.inputStream.source()
	}
}