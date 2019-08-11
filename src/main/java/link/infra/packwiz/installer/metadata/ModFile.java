package link.infra.packwiz.installer.metadata;

import com.google.gson.annotations.SerializedName;
import link.infra.packwiz.installer.UpdateManager.Options.Side;
import link.infra.packwiz.installer.metadata.hash.Hash;
import link.infra.packwiz.installer.metadata.hash.HashUtils;
import link.infra.packwiz.installer.request.HandlerManager;
import okio.Source;

import java.util.Map;

public class ModFile {
	public String name;
	public String filename;
	public Side side;

	public Download download;
	public static class Download {
		public SpaceSafeURI url;
		@SerializedName("hash-format")
		public String hashFormat;
		public String hash;
	}

	public Map<String, Object> update;

	public Option option;
	public static class Option {
		public boolean optional;
		public String description;
		@SerializedName("default")
		public boolean defaultValue;
	}

	public Source getSource(SpaceSafeURI baseLoc) throws Exception {
		if (download == null) {
			throw new Exception("Metadata file doesn't have download");
		}
		if (download.url == null) {
			throw new Exception("Metadata file doesn't have a download URI");
		}
		SpaceSafeURI newLoc = HandlerManager.getNewLoc(baseLoc, download.url);
		if (newLoc == null) {
			throw new Exception("Metadata file URI is invalid");
		}
		
		return HandlerManager.getFileSource(newLoc);
	}

	public Hash getHash() throws Exception {
		if (download == null) {
			throw new Exception("Metadata file doesn't have download");
		}
		if (download.hash == null) {
			throw new Exception("Metadata file doesn't have a hash");
		}
		if (download.hashFormat == null) {
			throw new Exception("Metadata file doesn't have a hash format");
		}
		return HashUtils.getHash(download.hashFormat, download.hash);
	}

	public boolean isOptional() {
		if (option != null) {
			return option.optional;
		}
		return false;
	}

}