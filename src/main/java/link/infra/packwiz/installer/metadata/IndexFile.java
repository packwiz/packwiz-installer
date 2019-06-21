package link.infra.packwiz.installer.metadata;

import java.io.InputStream;
import java.net.URI;
import java.nio.file.Paths;
import java.util.List;

import com.google.gson.annotations.SerializedName;
import com.moandjiezana.toml.Toml;

import link.infra.packwiz.installer.metadata.hash.Hash;
import link.infra.packwiz.installer.request.HandlerManager;

public class IndexFile {
	@SerializedName("hash-format")
	public String hashFormat;
	public List<File> files;
	
	public static class File {
		public URI file;
		@SerializedName("hash-format")
		public String hashFormat;
		public String hash;
		// TODO: implement
		public String alias;
		public boolean metafile;
		// TODO: implement
		public boolean preserve;

		public transient ModFile linkedFile;
		public transient URI linkedFileURI;
		public transient boolean optionValue = true;

		public void downloadMeta(IndexFile parentIndexFile, URI indexUri) throws Exception {
			if (!metafile) {
				return;
			}
			if (hashFormat == null || hashFormat.length() == 0) {
				hashFormat = parentIndexFile.hashFormat;
			}
			Hash fileHash = new Hash(hash, hashFormat);
			linkedFileURI = HandlerManager.getNewLoc(indexUri, file);
			InputStream stream = HandlerManager.getFileInputStream(linkedFileURI);
			if (stream == null) {
				throw new Exception("Index file URI is invalid, is it supported?");
			}
			Hash.HashInputStream fileStream = new Hash.HashInputStream(stream, fileHash);

			linkedFile = new Toml().read(fileStream).to(ModFile.class);
			if (!fileStream.hashIsEqual()) {
				throw new Exception("Invalid mod file hash");
			}
		}

		public InputStream getInputStream(URI indexUri) throws Exception {
			if (metafile) {
				if (linkedFile == null) {
					throw new Exception("Linked file doesn't exist!");
				}
				return linkedFile.getInputStream(linkedFileURI);
			} else {
				URI newLoc = HandlerManager.getNewLoc(indexUri, file);
				if (newLoc == null) {
					throw new Exception("Index file URI is invalid");
				}
				return HandlerManager.getFileInputStream(newLoc);
			}
		}

		public Hash getHash() throws Exception {
			if (hash == null) {
				throw new Exception("Index file doesn't have a hash");
			}
			if (hashFormat == null) {
				throw new Exception("Index file doesn't have a hash format");
			}
			return new Hash(hash, hashFormat);
		}

		public String getName() {
			if (metafile) {
				if (linkedFile != null) {
					if (linkedFile.name != null) {
						return linkedFile.name;
					} else if (linkedFile.filename != null) {
						return linkedFile.filename;
					}
				}
			}
			if (file != null) {
				return Paths.get(file.getPath()).getFileName().toString();
			}
			return file.getPath();
		}

		public URI getDestURI() {
			if (metafile && linkedFile != null) {
				return file.resolve(linkedFile.filename);
			} else {
				return file;
			}
		}
	}
}