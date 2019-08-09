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
	private Exception failure = null;
	private boolean complete = false;
	private boolean invalidated = false;
	private boolean optionValue = true;
	// If file is new or isOptional changed to true, the option needs to be presented again
	private boolean newOptional = true;

	public DownloadTask(IndexFile.File metadata) {
		this.metadata = metadata;
		if (this.metadata.linkedFile != null) {
			if (this.metadata.linkedFile.option != null) {
				// Set option to it's default value
				optionValue = this.metadata.linkedFile.option.defaultValue;
			}
		}
	}

	public void setDefaultHashFormat(String format) {
		if (failure != null || complete) return;
		if (metadata.hashFormat == null || metadata.hashFormat.length() == 0) {
			metadata.hashFormat = format;
		}
	}

	public void invalidate() {
		invalidated = true;
		complete = false;
	}

	public void updateFromCache(ManifestFile.File cachedFile) {
		if (failure != null || complete) return;
		if (cachedFile == null) return;

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
				complete = true;
			}
		}
		if (cachedFile.isOptional) {
			// Set option to the cached value
			optionValue = cachedFile.optionValue;
			if (isOptional()) {
				// isOptional didn't change
				newOptional = false;
			}
		}
	}

	public void downloadMetadata(IndexFile parentIndexFile, URI indexUri) {
		if (failure != null || complete) return;
		try {
			metadata.downloadMeta(parentIndexFile, indexUri);
		} catch (Exception e) {
			failure = e;
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
		return this.optionValue;
	}

	@Override
	public String getOptionDescription() {
		if (metadata.linkedFile != null) {
			return metadata.linkedFile.option.description;
		}
		return null;
	}

	public void setOptionValue(boolean value) {
		// TODO: if this is false, ensure the file is deleted in the actual download stage
		this.optionValue = value;
	}

	public static List<DownloadTask> createTasksFromIndex(IndexFile index) {
		ArrayList<DownloadTask> tasks = new ArrayList<>();
		for (IndexFile.File file : index.files) {
			tasks.add(new DownloadTask(file));
		}
		return tasks;
	}


}