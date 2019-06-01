package link.infra.packwiz.installer.request;

import java.io.InputStream;
import java.net.URI;

/**
 * IRequestHandler handles requests for locations specified in modpack metadata.
 */
public interface IRequestHandler {
	
	public boolean matchesHandler(URI loc);
	
	public default URI getNewLoc(URI loc) {
		return loc;
	}
	
	/**
	 * Gets the InputStream for a location. Must be threadsafe.
	 * @param loc The location to be read
	 * @return The InputStream containing the data of the file
	 * @throws Exception
	 */
	public InputStream getFileInputStream(URI loc) throws Exception;

}
