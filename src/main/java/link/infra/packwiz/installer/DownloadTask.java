package link.infra.packwiz.installer;

import link.infra.packwiz.installer.metadata.IndexFile;
import link.infra.packwiz.installer.metadata.ManifestFile;
import link.infra.packwiz.installer.metadata.hash.Hash;
import link.infra.packwiz.installer.metadata.hash.HashUtils;
import link.infra.packwiz.installer.ui.IOptionDetails;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

class DownloadTask implements IOptionDetails {
	final IndexFile.File metadata;
	ManifestFile.File cachedFile = null;
	private Exception failure = null;
	private boolean alreadyUpToDate = false;
	private boolean metadataRequired = true;
	private boolean invalidated = false;
	// If file is new or isOptional changed to true, the option needs to be presented again
	private boolean newOptional = true;

	public DownloadTask(IndexFile.File metadata) {
		this.metadata = metadata;
	}

	public void setDefaultHashFormat(String format) {
		if (metadata.hashFormat == null || metadata.hashFormat.length() == 0) {
			metadata.hashFormat = format;
		}
	}

	public void invalidate() {
		invalidated = true;
		alreadyUpToDate = false;
	}

	public void updateFromCache(ManifestFile.File cachedFile) {
		if (failure != null) return;
		if (cachedFile == null) return;

		this.cachedFile = cachedFile;

		if (!invalidated) {
			Hash currHash = null;
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

	public void downloadMetadata(IndexFile parentIndexFile, URI indexUri) {
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

	public Exception getException() {
		return failure;
	}

	public boolean isOptional() {
		if (metadata.linkedFile != null) {
			return metadata.linkedFile.isOptional();
		}
		return false;
	}

	public boolean isNewOptional() {
		return isOptional() && this.newOptional;
	}

	public String getName() {
		return metadata.getName();
	}

	@Override
	public boolean getOptionValue() {
		return this.cachedFile.optionValue;
	}

	@Override
	public String getOptionDescription() {
		if (metadata.linkedFile != null) {
			return metadata.linkedFile.option.description;
		}
		return null;
	}

	public void setOptionValue(boolean value) {
		// TODO: if this is false, ensure the file is deleted in the actual download stage (regardless of alreadyUpToDate?)
		if (value && !this.cachedFile.optionValue) {
			// Ensure that an update is done if it changes from false to true
			alreadyUpToDate = false;
		}
		this.cachedFile.optionValue = value;
	}

	public static List<DownloadTask> createTasksFromIndex(IndexFile index) {
		ArrayList<DownloadTask> tasks = new ArrayList<>();
		for (IndexFile.File file : index.files) {
			tasks.add(new DownloadTask(file));
		}
		return tasks;
	}


}