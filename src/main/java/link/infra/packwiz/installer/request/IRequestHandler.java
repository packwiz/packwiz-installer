package link.infra.packwiz.installer.request;

import java.net.URI;

import okio.Source;

/**
 * IRequestHandler handles requests for locations specified in modpack metadata.
 */
public interface IRequestHandler {
	
	public boolean matchesHandler(URI loc);
	
	public default URI getNewLoc(URI loc) {
		return loc;
	}
	
	/**
	 * Gets the Source for a location. Must be threadsafe.
	 * It is assumed that each location is read only once for the duration of an IRequestHandler.
	 * @param loc The location to be read
	 * @return The Source containing the data of the file
	 * @throws Exception
	 */
	public Source getFileSource(URI loc) throws Exception;

}
