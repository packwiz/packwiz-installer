package link.infra.packwiz.installer.metadata;

import link.infra.packwiz.installer.metadata.hash.Hash;

public class ManifestFile {
	
	public Hash packFileHash = null;
	public Hash indexFileHash = null;

	public static class File {
		public Hash hash = null;
	}
}