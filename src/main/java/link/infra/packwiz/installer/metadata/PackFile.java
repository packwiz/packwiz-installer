package link.infra.packwiz.installer.metadata;

import com.google.gson.annotations.SerializedName;

import java.util.Map;

public class PackFile {
	public String name;

	public IndexFileLoc index;
	public static class IndexFileLoc {
		public SpaceSafeURI file;
		@SerializedName("hash-format")
		public String hashFormat;
		public String hash;
	}

	public Map<String, String> versions;
	public Map<String, Object> client;
	public Map<String, Object> server;
}