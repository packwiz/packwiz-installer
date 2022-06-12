package link.infra.packwiz.installer.request

import link.infra.packwiz.installer.request.handlers.RequestHandlerFile
import link.infra.packwiz.installer.request.handlers.RequestHandlerGithub
import link.infra.packwiz.installer.request.handlers.RequestHandlerHTTP
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okio.Source

object HandlerManager {

	private val handlers: List<IRequestHandler> = listOf(
			RequestHandlerGithub(),
			RequestHandlerHTTP(),
			RequestHandlerFile()
	)

	 // TODO: get rid of nullable stuff here
	@JvmStatic
	fun getNewLoc(base: HttpUrl?, loc: String?): HttpUrl? {
		if (loc == null) {
			return null
		}
		val dest = base?.run { resolve(loc) } ?: loc.toHttpUrl()
		for (handler in handlers) with (handler) {
			if (matchesHandler(dest)) {
				return getNewLoc(dest)
			}
		}
		return dest
	}

	// TODO: What if files are read multiple times??
	// Zip handler discards once read, requesting multiple times on other handlers would cause multiple downloads
	// Caching system? Copy from already downloaded files?

	// TODO: change to use something more idiomatic than exceptions?

	@JvmStatic
	@Throws(Exception::class)
	fun getFileSource(loc: HttpUrl): Source {
		for (handler in handlers) {
			if (handler.matchesHandler(loc)) {
				return handler.getFileSource(loc) ?: throw Exception("Couldn't find URI: $loc")
			}
		}
		throw Exception("No handler available for URI: $loc")
	}

	// TODO: github toml resolution?
	// e.g. https://github.com/comp500/Demagnetize -> demagnetize.toml
	// https://github.com/comp500/Demagnetize/blob/master/demagnetize.toml
}