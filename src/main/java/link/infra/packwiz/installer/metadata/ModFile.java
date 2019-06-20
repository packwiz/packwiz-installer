package link.infra.packwiz.installer.metadata;

import java.net.URI;
import java.util.Map;

import com.google.gson.annotations.SerializedName;

import link.infra.packwiz.installer.UpdateManager.Options.Side;

class ModFile {
	public String name;
	public String filename;
	public Side side;

	public Download download;
	public static class Download {
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

}