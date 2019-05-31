package link.infra.packwiz.installer.util;

import java.io.FilterInputStream;
import java.io.InputStream;

public class CachedInputStream extends FilterInputStream {
	
	public CachedInputStream(InputStream stream, boolean isMaster) {
		if (!isMaster) {
			
		}
		super(stream);
	}

	public CachedInputStream(InputStream stream) {
		super(stream, true);
	}
	
	public CachedInputStream getNewStream() {
		
	}
	
	

}
