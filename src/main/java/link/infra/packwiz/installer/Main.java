package link.infra.packwiz.installer;

import link.infra.packwiz.installer.ui.CLIHandler;
import link.infra.packwiz.installer.ui.IUserInterface;
import link.infra.packwiz.installer.ui.InstallWindow;
import org.apache.commons.cli.*;

import javax.swing.*;
import java.awt.*;
import java.net.URI;
import java.net.URISyntaxException;

public class Main {

	// Actual main() is in RequiresBootstrap!

	public Main(String[] args) {
		// Big overarching try/catch just in case everything breaks
		try {
			this.startup(args);
		} catch (Exception e) {
			e.printStackTrace();
			EventQueue.invokeLater(() -> {
				JOptionPane.showMessageDialog(null,
						"A fatal error occurred: \n" + e.getClass().getCanonicalName() + ": " + e.getMessage(),
						"packwiz-installer", JOptionPane.ERROR_MESSAGE);
				System.exit(1);
			});
			// In case the eventqueue is broken, exit after 1 minute
			try {
				Thread.sleep(60 * 1000);
			} catch (InterruptedException e1) {
				// Good, it was already called?
				return;
			}
			System.exit(1);
		}
	}

	protected void startup(String[] args) {
		Options options = new Options();
		addNonBootstrapOptions(options);
		addBootstrapOptions(options);

		CommandLineParser parser = new DefaultParser();
		CommandLine cmd = null;
		try {
			cmd = parser.parse(options, args);
		} catch (ParseException e) {
			e.printStackTrace();
			try {
				UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
			} catch (Exception e1) {
				// Ignore the exceptions, just continue using the ugly L&F
			}
			JOptionPane.showMessageDialog(null, e.getMessage(), "packwiz-installer", JOptionPane.ERROR_MESSAGE);
			System.exit(1);
		}

		IUserInterface ui;
		// if "headless", GUI creation will fail anyway!
		if (cmd.hasOption("no-gui") || GraphicsEnvironment.isHeadless()) {
			ui = new CLIHandler();
		} else {
			ui = new InstallWindow();
		}

		String[] unparsedArgs = cmd.getArgs();
		if (unparsedArgs.length > 1) {
			ui.handleExceptionAndExit(new RuntimeException("Too many arguments specified!"));
			return;
		} else if (unparsedArgs.length < 1) {
			ui.handleExceptionAndExit(new RuntimeException("URI to install from must be specified!"));
			return;
		}

		String title = cmd.getOptionValue("title");
		if (title != null) {
			ui.setTitle(title);
		}

		ui.show();

		UpdateManager.Options uOptions = new UpdateManager.Options();

		String side = cmd.getOptionValue("side");
		if (side != null) {
			uOptions.side = UpdateManager.Options.Side.from(side);
		}

		String packFolder = cmd.getOptionValue("pack-folder");
		if (packFolder != null) {
			uOptions.packFolder = packFolder;
		}

		String metaFile = cmd.getOptionValue("meta-file");
		if (metaFile != null) {
			uOptions.manifestFile = metaFile;
		}

		try {
			uOptions.downloadURI = new URI(unparsedArgs[0]);
		} catch (URISyntaxException e) {
			// TODO: better error message?
			ui.handleExceptionAndExit(e);
			return;
		}

		// Start update process!
		// TODO: start in SwingWorker?
		try {
			ui.executeManager(new Runnable(){
				@Override
				public void run() {
					try {
						new UpdateManager(uOptions, ui);
					} catch (Exception e) {
						// TODO: better error message?
						ui.handleExceptionAndExit(e);
					}
				}
			});
		} catch (Exception e) {
			// TODO: better error message?
			ui.handleExceptionAndExit(e);
		}
	}

	// Called by packwiz-installer-bootstrap to set up the help command
	public static void addNonBootstrapOptions(Options options) {
		options.addOption("s", "side", true, "Side to install mods from (client/server, defaults to client)");
		options.addOption(null, "title", true, "Title of the installer window");
		options.addOption(null, "pack-folder", true, "Folder to install the pack to (defaults to the JAR directory)");
		options.addOption(null, "meta-file", true, "JSON file to store pack metadata, relative to the pack folder (defaults to packwiz.json)");
	}
	
	// TODO: link these somehow so they're only defined once?
	private static void addBootstrapOptions(Options options) {
		options.addOption(null, "bootstrap-update-url", true, "Github API URL for checking for updates");
		options.addOption(null, "bootstrap-update-token", true, "Github API Access Token, for private repositories");
		options.addOption(null, "bootstrap-no-update", false, "Don't update packwiz-installer");
		options.addOption(null, "bootstrap-main-jar", true, "Location of the packwiz-installer JAR file");
		options.addOption("g", "no-gui", false, "Don't display a GUI to show update progress");
		options.addOption("h", "help", false, "Display this message"); // Implemented in packwiz-installer-bootstrap!
	}
	
}
