package link.infra.packwiz.installer;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.net.URI;
import java.nio.file.Paths;

import com.google.gson.Gson;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;
import com.moandjiezana.toml.Toml;

import link.infra.packwiz.installer.metadata.ManifestFile;
import link.infra.packwiz.installer.metadata.PackFile;
import link.infra.packwiz.installer.metadata.hash.Hash;
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
		Gson gson = new Gson();
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
		Hash.HashInputStream packFileStream;
		try {
			InputStream stream = HandlerManager.getFileInputStream(opts.downloadURI);
			if (stream == null) {
				throw new Exception("Pack file URI is invalid, is it supported?");
			}
			packFileStream = new Hash.HashInputStream(stream, "sha256");
		} catch (Exception e) {
			// TODO: still launch the game if updating doesn't work?
			// TODO: ask user if they want to launch the game, exit(1) if they don't
			ui.handleExceptionAndExit(e);
			return;
		}
		PackFile pf;
		try {
			pf = new Toml().read(packFileStream).to(PackFile.class);
		} catch (IllegalStateException e) {
			ui.handleExceptionAndExit(e);
			return;
		}

		Hash packFileHash = packFileStream.get();
		if (packFileHash.equals(manifest.packFileHash)) {
			System.out.println("Hash already up to date!");
			// WOOO it's already up to date
			// todo: --force?
		}

		System.out.println(pf.name);


		// When successfully updated
		manifest.packFileHash = packFileHash;
		// update other hashes
		// TODO: don't do this on failure?
		try (Writer writer = new FileWriter(Paths.get(opts.packFolder, opts.manifestFile).toString())) {
			gson.toJson(manifest, writer);
		} catch (IOException e) {
			// TODO: add message?
			ui.handleException(e);
		}
		
	}

	protected void checkOptions() {
		// TODO: implement
	}
}
