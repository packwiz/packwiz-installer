package link.infra.packwiz.installer.request

import link.infra.packwiz.installer.metadata.SpaceSafeURI
import link.infra.packwiz.installer.request.handlers.RequestHandlerGithub
import link.infra.packwiz.installer.request.handlers.RequestHandlerHTTP
import okio.Source

object HandlerManager {

	private val handlers: List<IRequestHandler> = listOf(
			RequestHandlerGithub(),
			RequestHandlerHTTP()
	)

	@JvmStatic
	fun getNewLoc(base: SpaceSafeURI?, loc: SpaceSafeURI?): SpaceSafeURI? {
		if (loc == null) {
			return null
		}
		val dest = base?.run { resolve(loc) } ?: loc
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

	@JvmStatic
	@Throws(Exception::class)
	fun getFileSource(loc: SpaceSafeURI): Source {
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