package link.infra.packwiz.installer.metadata;

import java.net.URI;
import java.util.Map;

import link.infra.packwiz.installer.metadata.hash.Hash;

public class ManifestFile {
	
	public Hash packFileHash = null;
	public Hash indexFileHash = null;
	public Map<URI, File> cachedFiles;

	public static class File {
		public Hash hash = null;
		public boolean isOptional = false;
		public boolean optionValue = true;
		public Hash linkedFileHash = null;
	}
}