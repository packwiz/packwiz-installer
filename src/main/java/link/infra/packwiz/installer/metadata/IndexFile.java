package link.infra.packwiz.installer.metadata;

import java.util.List;

import com.google.gson.annotations.SerializedName;

public class IndexFile {
	@SerializedName("hash-format")
	public String hashFormat;
	public List<File> files;
	
	public static class File {
		public String file;
		@SerializedName("hash-format")
		public String hashFormat;
		public String hash;
		public String alias;
		public boolean metafile;
		public boolean preserve;
	}
}