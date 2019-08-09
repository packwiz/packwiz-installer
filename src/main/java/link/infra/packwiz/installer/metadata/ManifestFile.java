package link.infra.packwiz.installer.metadata;

import link.infra.packwiz.installer.metadata.hash.Hash;

import java.net.URI;
import java.util.Map;

public class ManifestFile {
	public Hash packFileHash = null;
	public Hash indexFileHash = null;
	public Map<URI, File> cachedFiles;

	public static class File {
		public Hash hash = null;
		public Hash linkedFileHash = null;
		public String cachedLocation = null;

		public boolean isOptional = false;
		public boolean optionValue = true;
	}
}