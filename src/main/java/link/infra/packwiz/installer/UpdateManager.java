package link.infra.packwiz.installer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;
import com.google.gson.annotations.SerializedName;
import com.moandjiezana.toml.Toml;
import link.infra.packwiz.installer.metadata.IndexFile;
import link.infra.packwiz.installer.metadata.ManifestFile;
import link.infra.packwiz.installer.metadata.PackFile;
import link.infra.packwiz.installer.metadata.hash.GeneralHashingSource;
import link.infra.packwiz.installer.metadata.hash.Hash;
import link.infra.packwiz.installer.metadata.hash.HashUtils;
import link.infra.packwiz.installer.request.HandlerManager;
import link.infra.packwiz.installer.ui.IExceptionDetails;
import link.infra.packwiz.installer.ui.IUserInterface;
import link.infra.packwiz.installer.ui.InstallProgress;
import okio.Okio;
import okio.Source;

import java.io.*;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class UpdateManager {

	private final Options opts;
	public final IUserInterface ui;
	private boolean cancelled;
	private boolean cancelledStartGame = false;

	public static class Options {
		URI downloadURI = null;
		String manifestFile = "packwiz.json"; // TODO: make configurable
		String packFolder = ".";
		Side side = Side.CLIENT;

		public enum Side {
			@SerializedName("client")
			CLIENT("client"),
			@SerializedName("server")
			SERVER("server"),
			@SerializedName("both")
			BOTH("both", new Side[] { CLIENT, SERVER });

			private final String sideName;
			private final Side[] depSides;

			Side(String sideName) {
				this.sideName = sideName.toLowerCase();
				this.depSides = null;
			}

			Side(String sideName, Side[] depSides) {
				this.sideName = sideName.toLowerCase();
				this.depSides = depSides;
			}

			@Override
			public String toString() {
				return this.sideName;
			}

			public boolean hasSide(Side tSide) {
				if (this.equals(tSide)) {
					return true;
				}
				if (this.depSides != null) {
					for (Side depSide : this.depSides) {
						if (depSide.equals(tSide)) {
							return true;
						}
					}
				}
				return false;
			}

			public static Side from(String name) {
				String lowerName = name.toLowerCase();
				for (Side side : Side.values()) {
					if (side.sideName.equals(lowerName)) {
						return side;
					}
				}
				return null;
			}
		}
	}

	UpdateManager(Options opts, IUserInterface ui) {
		this.opts = opts;
		this.ui = ui;
		this.start();
	}

	private void start() {
		this.checkOptions();

		ui.submitProgress(new InstallProgress("Loading manifest file..."));
		Gson gson = new GsonBuilder().registerTypeAdapter(Hash.class, new Hash.TypeHandler()).create();
		ManifestFile manifest;
		try {
			manifest = gson.fromJson(new FileReader(Paths.get(opts.packFolder, opts.manifestFile).toString()),
					ManifestFile.class);
		} catch (FileNotFoundException e) {
			manifest = new ManifestFile();
		} catch (JsonSyntaxException | JsonIOException e) {
			ui.handleExceptionAndExit(e);
			return;
		}

		ui.submitProgress(new InstallProgress("Loading pack file..."));
		GeneralHashingSource packFileSource;
		try {
			Source src = HandlerManager.getFileSource(opts.downloadURI);
			packFileSource = HashUtils.getHasher("sha256").getHashingSource(src);
		} catch (Exception e) {
			// TODO: still launch the game if updating doesn't work?
			// TODO: ask user if they want to launch the game, exit(1) if they don't
			ui.handleExceptionAndExit(e);
			return;
		}
		PackFile pf;
		try {
			pf = new Toml().read(Okio.buffer(packFileSource).inputStream()).to(PackFile.class);
		} catch (IllegalStateException e) {
			ui.handleExceptionAndExit(e);
			return;
		}

		ui.submitProgress(new InstallProgress("Checking local files..."));

		// Invalidation checking must be done here, as it must happen before pack/index hashes are checked
		List<URI> invalidatedUris = new ArrayList<>();
		if (manifest.cachedFiles != null) {
			for (Map.Entry<URI, ManifestFile.File> entry : manifest.cachedFiles.entrySet()) {
				boolean invalid = false;
				// if isn't optional, or is optional but optionValue == true
				if (!entry.getValue().isOptional || entry.getValue().optionValue) {
					if (entry.getValue().cachedLocation != null) {
						if (!Files.exists(Paths.get(opts.packFolder, entry.getValue().cachedLocation))) {
							invalid = true;
						}
					} else {
						// if cachedLocation == null, should probably be installed!!
						invalid = true;
					}
				}
				if (invalid) {
					URI fileUri = entry.getKey();
					System.out.println("File " + fileUri.toString() + " invalidated, marked for redownloading");
					invalidatedUris.add(fileUri);
				}
			}
		}

		if (manifest.packFileHash != null && packFileSource.hashIsEqual(manifest.packFileHash) && invalidatedUris.isEmpty()) {
			System.out.println("Modpack is already up to date!");
			// todo: --force?
			return;
		}

		System.out.println("Modpack name: " + pf.name);

		try {
			// This is badly written, I'll probably heavily refactor it at some point
			processIndex(HandlerManager.getNewLoc(opts.downloadURI, pf.index.file),
					HashUtils.getHash(pf.index.hashFormat, pf.index.hash), pf.index.hashFormat, manifest, invalidatedUris);
		} catch (Exception e1) {
			ui.handleExceptionAndExit(e1);
		}

		if (cancelled) {
			System.out.println("Update cancelled by user!");
			System.exit(1);
			return;
		} else if (cancelledStartGame) {
			System.out.println("Update cancelled by user! Continuing to start game...");
			System.exit(0);
			return;
		}

		// TODO: update MMC params, java args etc

		manifest.packFileHash = packFileSource.getHash();
		manifest.cachedSide = opts.side;
		try (Writer writer = new FileWriter(Paths.get(opts.packFolder, opts.manifestFile).toString())) {
			gson.toJson(manifest, writer);
		} catch (IOException e) {
			// TODO: add message?
			ui.handleException(e);
		}

	}

	private void checkOptions() {
		// TODO: implement
	}

	private void processIndex(URI indexUri, Hash indexHash, String hashFormat, ManifestFile manifest, List<URI> invalidatedUris) {
		if (manifest.indexFileHash != null && manifest.indexFileHash.equals(indexHash) && invalidatedUris.isEmpty()) {
			System.out.println("Modpack files are already up to date!");
			return;
		}
		manifest.indexFileHash = indexHash;

		GeneralHashingSource indexFileSource;
		try {
			Source src = HandlerManager.getFileSource(indexUri);
			indexFileSource = HashUtils.getHasher(hashFormat).getHashingSource(src);
		} catch (Exception e) {
			// TODO: still launch the game if updating doesn't work?
			// TODO: ask user if they want to launch the game, exit(1) if they don't
			ui.handleExceptionAndExit(e);
			return;
		}
		IndexFile indexFile;
		try {
			indexFile = new Toml().read(Okio.buffer(indexFileSource).inputStream()).to(IndexFile.class);
		} catch (IllegalStateException e) {
			ui.handleExceptionAndExit(e);
			return;
		}

		if (!indexFileSource.hashIsEqual(indexHash)) {
			// TODO: throw exception
			System.out.println("I was meant to put an error message here but I'll do that later");
			return;
		}

		if (manifest.cachedFiles == null) {
			manifest.cachedFiles = new HashMap<>();
		}

		ui.submitProgress(new InstallProgress("Checking local files..."));
		Iterator<Map.Entry<URI, ManifestFile.File>> it = manifest.cachedFiles.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<URI, ManifestFile.File> entry = it.next();
			if (entry.getValue().cachedLocation != null) {
				boolean alreadyDeleted = false;
				// Delete if option value has been set to false
				if (entry.getValue().isOptional && !entry.getValue().optionValue) {
					try {
						Files.deleteIfExists(Paths.get(opts.packFolder, entry.getValue().cachedLocation));
					} catch (IOException e) {
						// TODO: should this be shown to the user in some way?
						e.printStackTrace();
					}
					// Set to null, as it doesn't exist anymore
					entry.getValue().cachedLocation = null;
					alreadyDeleted = true;
				}
				if (indexFile.files.stream().noneMatch(f -> f.file.equals(entry.getKey()))) {
					// File has been removed from the index
					if (!alreadyDeleted) {
						try {
							Files.deleteIfExists(Paths.get(opts.packFolder, entry.getValue().cachedLocation));
						} catch (IOException e) {
							// TODO: should this be shown to the user in some way?
							e.printStackTrace();
						}
					}
					it.remove();
				}
			}
		}
		ui.submitProgress(new InstallProgress("Comparing new files..."));

		// TODO: progress bar, parallelify
		List<DownloadTask> tasks = DownloadTask.createTasksFromIndex(indexFile, indexFile.hashFormat, opts.side);
		// If the side changes, invalidate EVERYTHING just in case
		// Might not be needed, but done just to be safe
		boolean invalidateAll = !opts.side.equals(manifest.cachedSide);
		tasks.forEach(f -> {
			// TODO: should linkedfile be checked as well? should this be done in the download section?
			if (invalidateAll) {
				f.invalidate();
			} else if (invalidatedUris.contains(f.metadata.file)) {
				f.invalidate();
			}
			ManifestFile.File file = manifest.cachedFiles.get(f.metadata.file);
			if (file != null) {
				// Ensure the file can be reverted later if necessary - the DownloadTask modifies the file so if it fails we need the old version back
				file.backup();
			}
			// If it is null, the DownloadTask will make a new empty cachedFile
			f.updateFromCache(file);
		});
		tasks.forEach(f -> f.downloadMetadata(indexFile, indexUri));

		List<IExceptionDetails> failedTasks = tasks.stream().filter(t -> t.getException() != null).collect(Collectors.toList());
		if (failedTasks.size() > 0) {
			IExceptionDetails.ExceptionListResult exceptionListResult;
			try {
				exceptionListResult = ui.showExceptions(failedTasks, tasks.size(), true).get();
			} catch (InterruptedException | ExecutionException e) {
				// Interrupted means cancelled???
				ui.handleExceptionAndExit(e);
				return;
			}
			switch (exceptionListResult) {
				case CONTINUE:
					break;
				case CANCEL:
					cancelled = true;
					return;
				case IGNORE:
					cancelledStartGame = true;
					return;
			}
		}

		List<DownloadTask> nonFailedFirstTasks = tasks.stream().filter(t -> t.getException() == null).collect(Collectors.toList());
		List<DownloadTask> optionTasks = nonFailedFirstTasks.stream().filter(DownloadTask::correctSide).filter(DownloadTask::isOptional).collect(Collectors.toList());
		// If options changed, present all options again
		if (optionTasks.stream().anyMatch(DownloadTask::isNewOptional)) {
			// new ArrayList is requires so it's an IOptionDetails rather than a DownloadTask list
			Future<Boolean> cancelledResult = ui.showOptions(new ArrayList<>(optionTasks));
			try {
				if (cancelledResult.get()) {
					cancelled = true;
					// TODO: Should the UI be closed somehow??
					return;
				}
			} catch (InterruptedException | ExecutionException e) {
				// Interrupted means cancelled???
				ui.handleExceptionAndExit(e);
			}
		}

		// TODO: different thread pool type?
		ExecutorService threadPool = Executors.newFixedThreadPool(10);
		CompletionService<DownloadTask> completionService = new ExecutorCompletionService<>(threadPool);

		tasks.forEach(t -> completionService.submit(() -> {
			t.download(opts.packFolder, indexUri);
			return t;
		}));

		for (int i = 0; i < tasks.size(); i++) {
			DownloadTask task;
			try {
				task = completionService.take().get();
			} catch (InterruptedException | ExecutionException e) {
				ui.handleException(e);
				task = null;
			}
			// Update manifest - If there were no errors cachedFile has already been modified in place (good old pass by reference)
			if (task != null) {
				if (task.getException() != null) {
					ManifestFile.File file = task.cachedFile.getRevert();
					if (file != null) {
						manifest.cachedFiles.put(task.metadata.file, file);
					}
				} else {
					// idiot, if it wasn't there in the first place it won't magically appear there
					manifest.cachedFiles.putIfAbsent(task.metadata.file, task.cachedFile);
				}
			}

			String progress;
			if (task != null) {
				if (task.getException() != null) {
					progress = "Failed to download " + task.metadata.getName() + ": " + task.getException().getMessage();
					task.getException().printStackTrace();
				} else {
					// TODO: should this be revised for tasks that didn't actually download it?
					progress = "Downloaded " + task.metadata.getName();
				}
			} else {
				progress = "Failed to download, unknown reason";
			}
			ui.submitProgress(new InstallProgress(progress, i + 1, tasks.size()));
		}

		List<IExceptionDetails> failedTasks2ElectricBoogaloo = nonFailedFirstTasks.stream().filter(t -> t.getException() != null).collect(Collectors.toList());
		if (failedTasks2ElectricBoogaloo.size() > 0) {
			IExceptionDetails.ExceptionListResult exceptionListResult;
			try {
				exceptionListResult = ui.showExceptions(failedTasks2ElectricBoogaloo, tasks.size(), false).get();
			} catch (InterruptedException | ExecutionException e) {
				// Interrupted means cancelled???
				ui.handleExceptionAndExit(e);
				return;
			}
			switch (exceptionListResult) {
				case CONTINUE:
					break;
				case CANCEL:
					cancelled = true;
					return;
				case IGNORE:
					cancelledStartGame = true;
			}
		}
	}
}
