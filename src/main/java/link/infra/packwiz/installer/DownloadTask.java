package link.infra.packwiz.installer;

import link.infra.packwiz.installer.metadata.IndexFile;
import link.infra.packwiz.installer.metadata.ManifestFile;
import link.infra.packwiz.installer.metadata.SpaceSafeURI;
import link.infra.packwiz.installer.metadata.hash.GeneralHashingSource;
import link.infra.packwiz.installer.metadata.hash.Hash;
import link.infra.packwiz.installer.metadata.hash.HashUtils;
import link.infra.packwiz.installer.ui.IExceptionDetails;
import link.infra.packwiz.installer.ui.IOptionDetails;
import okio.Buffer;
import okio.Okio;
import okio.Source;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

class DownloadTask implements IOptionDetails, IExceptionDetails {
	final IndexFile.File metadata;
	ManifestFile.File cachedFile = null;
	private Exception failure = null;
	private boolean alreadyUpToDate = false;
	private boolean metadataRequired = true;
	private boolean invalidated = false;
	// If file is new or isOptional changed to true, the option needs to be presented again
	private boolean newOptional = true;
	private final UpdateManager.Options.Side downloadSide;

	private DownloadTask(IndexFile.File metadata, String defaultFormat, UpdateManager.Options.Side downloadSide) {
		this.metadata = metadata;
		if (metadata.hashFormat == null || metadata.hashFormat.length() == 0) {
			metadata.hashFormat = defaultFormat;
		}
		this.downloadSide = downloadSide;
	}

	void invalidate() {
		invalidated = true;
		alreadyUpToDate = false;
	}

	void updateFromCache(ManifestFile.File cachedFile) {
		if (failure != null) return;
		if (cachedFile == null) {
			this.cachedFile = new ManifestFile.File();
			return;
		}

		this.cachedFile = cachedFile;

		if (!invalidated) {
			Hash currHash;
			try {
				currHash = HashUtils.getHash(metadata.hashFormat, metadata.hash);
			} catch (Exception e) {
				failure = e;
				return;
			}
			if (currHash != null && currHash.equals(cachedFile.hash)) {
				// Already up to date
				alreadyUpToDate = true;
				metadataRequired = false;
			}
		}
		if (cachedFile.isOptional) {
			// Because option selection dialog might set this task to true/false, metadata is always needed to download
			// the file, and to show the description and name
			metadataRequired = true;
		}
	}

	void downloadMetadata(IndexFile parentIndexFile, SpaceSafeURI indexUri) {
		if (failure != null) return;
		if (metadataRequired) {
			try {
				metadata.downloadMeta(parentIndexFile, indexUri);
			} catch (Exception e) {
				failure = e;
				return;
			}
			if (metadata.linkedFile != null) {
				if (metadata.linkedFile.option != null) {
					if (metadata.linkedFile.option.optional) {
						if (cachedFile.isOptional) {
							// isOptional didn't change
							newOptional = false;
						} else {
							// isOptional false -> true, set option to it's default value
							// TODO: preserve previous option value, somehow??
							cachedFile.optionValue = this.metadata.linkedFile.option.defaultValue;
						}
					}
				}
				cachedFile.isOptional = isOptional();
			}
		}
	}

	void download(String packFolder, SpaceSafeURI indexUri) {
		if (failure != null) return;

		// Ensure it is removed
		if (!cachedFile.optionValue || !correctSide()) {
			if (cachedFile.cachedLocation == null) return;
			try {
				Files.deleteIfExists(Paths.get(packFolder, cachedFile.cachedLocation));
			} catch (IOException e) {
				// TODO: how much of a problem is this? use log4j/other log library to show warning?
				e.printStackTrace();
			}
			cachedFile.cachedLocation = null;
			return;
		}

		if (alreadyUpToDate) return;

		Path destPath = Paths.get(packFolder, metadata.getDestURI().toString());

		// Don't update files marked with preserve if they already exist on disk
		if (metadata.preserve) {
			if (Files.exists(destPath)) {
				return;
			}
		}

		try {
			Hash hash;
			String fileHashFormat;
			if (metadata.linkedFile != null) {
				hash = metadata.linkedFile.getHash();
				fileHashFormat = metadata.linkedFile.download.hashFormat;
			} else {
				hash = metadata.getHash();
				fileHashFormat = metadata.hashFormat;
			}

			Source src = metadata.getSource(indexUri);
			GeneralHashingSource fileSource = HashUtils.getHasher(fileHashFormat).getHashingSource(src);
			Buffer data = new Buffer();
			Okio.buffer(fileSource).readAll(data);

			if (fileSource.hashIsEqual(hash)) {
				Files.createDirectories(destPath.getParent());
				Files.copy(data.inputStream(), destPath, StandardCopyOption.REPLACE_EXISTING);
			} else {
				// TODO: no more SYSOUT!!!!!!!!!
				System.out.println("Invalid hash for " + metadata.getDestURI().toString());
				System.out.println("Calculated: " + fileSource.getHash());
				System.out.println("Expected:   " + hash);
				failure = new Exception("Hash invalid!");
			}

			if (cachedFile.cachedLocation != null && !destPath.equals(Paths.get(packFolder, cachedFile.cachedLocation))) {
				// Delete old file if location changes
				Files.delete(Paths.get(packFolder, cachedFile.cachedLocation));
			}
		} catch (Exception e) {
			failure = e;
		}
		if (failure == null) {
			if (cachedFile == null) {
				cachedFile = new ManifestFile.File();
			}
			// Update the manifest file
			try {
				cachedFile.hash = metadata.getHash();
			} catch (Exception e) {
				failure = e;
				return;
			}
			cachedFile.isOptional = isOptional();
			cachedFile.cachedLocation = metadata.getDestURI().toString();
			if (metadata.linkedFile != null) {
				try {
					cachedFile.linkedFileHash = metadata.linkedFile.getHash();
				} catch (Exception e) {
					failure = e;
				}
			}
		}
	}

	public Exception getException() {
		return failure;
	}

	boolean isOptional() {
		if (metadata.linkedFile != null) {
			return metadata.linkedFile.isOptional();
		}
		return false;
	}

	boolean isNewOptional() {
		return isOptional() && this.newOptional;
	}

	boolean correctSide() {
		if (metadata.linkedFile != null) {
			return metadata.linkedFile.side.hasSide(downloadSide);
		}
		return true;
	}

	public String getName() {
		return metadata.getName();
	}

	@Override
	public boolean getOptionValue() {
		return cachedFile.optionValue;
	}

	@Override
	public String getOptionDescription() {
		if (metadata.linkedFile != null) {
			return metadata.linkedFile.option.description;
		}
		return null;
	}

	public void setOptionValue(boolean value) {
		if (value && !cachedFile.optionValue) {
			// Ensure that an update is done if it changes from false to true, or from true to false
			alreadyUpToDate = false;
		}
		cachedFile.optionValue = value;
	}

	static List<DownloadTask> createTasksFromIndex(IndexFile index, String defaultFormat, UpdateManager.Options.Side downloadSide) {
		ArrayList<DownloadTask> tasks = new ArrayList<>();
		for (IndexFile.File file : index.files) {
			tasks.add(new DownloadTask(file, defaultFormat, downloadSide));
		}
		return tasks;
	}


}