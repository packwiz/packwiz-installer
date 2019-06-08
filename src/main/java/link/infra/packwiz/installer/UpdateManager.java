package link.infra.packwiz.installer;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Paths;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;

import link.infra.packwiz.installer.metadata.HashInputStream;
import link.infra.packwiz.installer.metadata.HashTypeAdapter;
import link.infra.packwiz.installer.metadata.ManifestFile;
import link.infra.packwiz.installer.request.HandlerManager;
import link.infra.packwiz.installer.ui.IUserInterface;
import link.infra.packwiz.installer.ui.InstallProgress;

public class UpdateManager {

	public final Options opts;
	public final IUserInterface ui;

	public static class Options {
		public URI downloadURI = null;
		public String manifestFile = "packwiz.json"; // TODO: make configurable
		public String packFolder = ".";
		public Side side = Side.CLIENT;

		public static enum Side {
			CLIENT("client"), SERVER("server"), BOTH("both", new Side[] { CLIENT, SERVER });

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
					for (int i = 0; i < this.depSides.length; i++) {
						if (this.depSides[i].equals(tSide)) {
							return true;
						}
					}
				}
				return false;
			}

			public static Side from(String name) {
				String lowerName = name.toLowerCase();
				for (Side side : Side.values()) {
					if (side.sideName == lowerName) {
						return side;
					}
				}
				return null;
			}
		}
	}

	public UpdateManager(Options opts, IUserInterface ui) {
		this.opts = opts;
		this.ui = ui;
		this.start();
	}

	protected void start() {
		this.checkOptions();

		ui.submitProgress(new InstallProgress("Loading manifest file..."));
		Gson gson = new GsonBuilder().registerTypeHierarchyAdapter(byte[].class, new HashTypeAdapter()).create();
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
		HashInputStream packFileStream;
		try {
			packFileStream = new HashInputStream(HandlerManager.getFileInputStream(opts.downloadURI));
		} catch (Exception e) {
			ui.handleExceptionAndExit(e);
			return;
		}
		// TODO: read file
		try {
			packFileStream.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		byte[] packFileHash = packFileStream.getHashValue();
		if (packFileHash.equals(manifest.packFileHash)) {
			// WOOO it's already up to date
			// todo: --force?
		}

		// TODO: save manifest file
	}

	protected void checkOptions() {
		// TODO: implement
	}
}
