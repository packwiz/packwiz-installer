package link.infra.packwiz.installer.metadata;

import com.google.gson.annotations.SerializedName;
import com.moandjiezana.toml.Toml;
import link.infra.packwiz.installer.metadata.hash.GeneralHashingSource;
import link.infra.packwiz.installer.metadata.hash.Hash;
import link.infra.packwiz.installer.metadata.hash.HashUtils;
import link.infra.packwiz.installer.request.HandlerManager;
import okio.Okio;
import okio.Source;

import java.nio.file.Paths;
import java.util.List;

public class IndexFile {
	@SerializedName("hash-format")
	public String hashFormat;
	public List<File> files;
	
	public static class File {
		public SpaceSafeURI file;
		@SerializedName("hash-format")
		public String hashFormat;
		public String hash;
		public SpaceSafeURI alias;
		public boolean metafile;
		public boolean preserve;

		public transient ModFile linkedFile;
		public transient SpaceSafeURI linkedFileURI;

		public void downloadMeta(IndexFile parentIndexFile, SpaceSafeURI indexUri) throws Exception {
			if (!metafile) {
				return;
			}
			if (hashFormat == null || hashFormat.length() == 0) {
				hashFormat = parentIndexFile.hashFormat;
			}
			Hash fileHash = HashUtils.getHash(hashFormat, hash);
			linkedFileURI = HandlerManager.getNewLoc(indexUri, file);
			Source src = HandlerManager.getFileSource(linkedFileURI);
			GeneralHashingSource fileStream = HashUtils.getHasher(hashFormat).getHashingSource(src);

			linkedFile = new Toml().read(Okio.buffer(fileStream).inputStream()).to(ModFile.class);
			if (!fileStream.hashIsEqual(fileHash)) {
				throw new Exception("Invalid mod file hash");
			}
		}

		public Source getSource(SpaceSafeURI indexUri) throws Exception {
			if (metafile) {
				if (linkedFile == null) {
					throw new Exception("Linked file doesn't exist!");
				}
				return linkedFile.getSource(linkedFileURI);
			} else {
				SpaceSafeURI newLoc = HandlerManager.getNewLoc(indexUri, file);
				if (newLoc == null) {
					throw new Exception("Index file URI is invalid");
				}
				return HandlerManager.getFileSource(newLoc);
			}
		}

		public Hash getHash() throws Exception {
			if (hash == null) {
				// TODO: should these be more specific exceptions (e.g. IndexFileException?!)
				throw new Exception("Index file doesn't have a hash");
			}
			if (hashFormat == null) {
				throw new Exception("Index file doesn't have a hash format");
			}
			return HashUtils.getHash(hashFormat, hash);
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
			// TODO: throw some kind of exception?
			return "Invalid file";
		}

		public SpaceSafeURI getDestURI() {
			if (alias != null) {
				return alias;
			}
			if (metafile && linkedFile != null) {
				// TODO: URIs are bad
				return file.resolve(linkedFile.filename);
			} else {
				return file;
			}
		}
	}
}