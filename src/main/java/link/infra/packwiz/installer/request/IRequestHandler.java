package link.infra.packwiz.installer.request;

import okio.Source;

import java.net.URI;

/**
 * IRequestHandler handles requests for locations specified in modpack metadata.
 */
public interface IRequestHandler {
	
	boolean matchesHandler(URI loc);
	
	default URI getNewLoc(URI loc) {
		return loc;
	}
	
	/**
	 * Gets the Source for a location. Must be threadsafe.
	 * It is assumed that each location is read only once for the duration of an IRequestHandler.
	 * @param loc The location to be read
	 * @return The Source containing the data of the file
	 * @throws Exception
	 */
	Source getFileSource(URI loc) throws Exception;

}
