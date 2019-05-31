package link.infra.packwiz.installer.request;

import java.io.InputStream;
import java.net.URI;

public interface IRequestHandler {
	
	public boolean matchesHandler(URI loc);
	
	public default URI getNewLoc(URI loc) {
		return loc;
	}
	
	public InputStream getFileInputStream(URI loc);

}
