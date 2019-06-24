package link.infra.packwiz.installer.metadata;

import java.net.URI;
import java.util.Map;

import com.google.gson.annotations.JsonAdapter;
import com.google.gson.annotations.SerializedName;

public class PackFile {
	public String name;

	public IndexFileLoc index;
	public static class IndexFileLoc {
		@JsonAdapter(SpaceSafeURIParser.class)
		public URI file;
		@SerializedName("hash-format")
		public String hashFormat;
		public String hash;
	}

	public Map<String, String> versions;
	public Map<String, Object> client;
	public Map<String, Object> server;
}