package link.infra.packwiz.installer.metadata;

import java.net.URI;
import java.util.Map;

public class ManifestFile {
	
	public Object packFileHash = null;
	public Object indexFileHash = null;
	public Map<URI, File> cachedFiles;

	public static class File {
		public Object hash = null;
		public boolean isOptional = false;
		public boolean optionValue = true;
		public Object linkedFileHash = null;
	}
}