package link.infra.packwiz.installer;

import java.net.URI;

public class UpdateManager {

	public final Options opts;
	public final IUserInterface ui;

	public static class Options {
		public URI downloadURI;
		public String manifestFile = "packwiz.json";
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
		ui.submitProgress(new InstallProgress("Loading pack file..."));
		try {
			Thread.sleep(1000);
		} catch (InterruptedException e) {
			// Big oof
		}
		ui.submitProgress(new InstallProgress("Loading metadata", 1, 2));
		try {
			Thread.sleep(1000);
		} catch (InterruptedException e) {
			// Big oof
		}
		ui.submitProgress(new InstallProgress("Loading magic", 2, 2));
		try {
			Thread.sleep(1000);
		} catch (InterruptedException e) {
			// Big oof
		}
	}
}
