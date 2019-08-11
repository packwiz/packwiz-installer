package link.infra.packwiz.installer.metadata;

import link.infra.packwiz.installer.UpdateManager;
import link.infra.packwiz.installer.metadata.hash.Hash;

import java.util.Map;

public class ManifestFile {
	public Hash packFileHash = null;
	public Hash indexFileHash = null;
	public Map<SpaceSafeURI, File> cachedFiles;
	// If the side changes, EVERYTHING invalidates. FUN!!!
	public UpdateManager.Options.Side cachedSide = UpdateManager.Options.Side.CLIENT;

	public static class File {
		private transient File revert;

		public Hash hash = null;
		public Hash linkedFileHash = null;
		public String cachedLocation = null;

		public boolean isOptional = false;
		public boolean optionValue = true;

		// When an error occurs, the state needs to be reverted. To do this, I have a crude revert system.
		public void backup() {
			revert = new File();
			revert.hash = hash;
			revert.linkedFileHash = linkedFileHash;
			revert.cachedLocation = cachedLocation;
			revert.isOptional = isOptional;
			revert.optionValue = optionValue;
		}

		public File getRevert() {
			return revert;
		}
	}
}