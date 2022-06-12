package link.infra.packwiz.installer.request

import okhttp3.HttpUrl
import okio.Source

/**
 * IRequestHandler handles requests for locations specified in modpack metadata.
 */
interface IRequestHandler {
	fun matchesHandler(loc: HttpUrl): Boolean

	fun getNewLoc(loc: HttpUrl): HttpUrl {
		return loc
	}

	/**
	 * Gets the Source for a location. Must be threadsafe.
	 * It is assumed that each location is read only once for the duration of an IRequestHandler.
	 * @param loc The location to be read
	 * @return The Source containing the data of the file
	 * @throws Exception Exception if it failed to download a file!!!
	 */
	fun getFileSource(loc: HttpUrl): Source?
}