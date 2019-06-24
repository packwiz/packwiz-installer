package link.infra.packwiz.installer.metadata;

import java.net.URI;
import java.util.Map;

import com.google.gson.annotations.JsonAdapter;
import com.google.gson.annotations.SerializedName;

import link.infra.packwiz.installer.UpdateManager.Options.Side;
import link.infra.packwiz.installer.metadata.hash.HashUtils;
import link.infra.packwiz.installer.request.HandlerManager;
import okio.Source;

public class ModFile {
	public String name;
	public String filename;
	public Side side;

	public Download download;
	public static class Download {
		@JsonAdapter(SpaceSafeURIParser.class)
		public URI url;
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

	public Source getSource(URI baseLoc) throws Exception {
		if (download == null) {
			throw new Exception("Metadata file doesn't have download");
		}
		if (download.url == null) {
			throw new Exception("Metadata file doesn't have a download URI");
		}
		URI newLoc = HandlerManager.getNewLoc(baseLoc, download.url);
		if (newLoc == null) {
			throw new Exception("Metadata file URI is invalid");
		}
		
		return HandlerManager.getFileSource(newLoc);
	}

	public Object getHash() throws Exception {
		if (download == null) {
			throw new Exception("Metadata file doesn't have download");
		}
		if (download.hash == null) {
			throw new Exception("Metadata file doesn't have a hash");
		}
		if (download.hashFormat == null) {
			throw new Exception("Metadata file doesn't have a hash format");
		}
		return HashUtils.getHash(download.hash, download.hashFormat);
	}

	public boolean isOptional() {
		if (option != null) {
			return option.optional;
		}
		return false;
	}

}