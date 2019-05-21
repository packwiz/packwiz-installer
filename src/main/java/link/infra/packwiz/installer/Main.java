package link.infra.packwiz.installer;

import javax.swing.JOptionPane;
import javax.swing.UIManager;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

public class Main {
	
	// Actual main() is in RequiresBootstrap!
	
	public Main(String[] args) {
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
		
		System.out.println("Hello World!");
	}

	// Called by packwiz-installer-bootstrap to set up the help command
	public static void addNonBootstrapOptions(Options options) {
		//options.addOption("w", "welp", false, "Testing options");
	}
	
	// TODO: link these somehow so they're only defined once?
	private static void addBootstrapOptions(Options options) {
		options.addOption(null, "bootstrap-update-url", true, "Github API URL for checking for updates");
		options.addOption(null, "bootstrap-update-token", true, "Github API Access Token, for private repositories");
		options.addOption(null, "bootstrap-no-update", false, "Don't update packwiz-installer");
		options.addOption(null, "bootstrap-main-jar", true, "Location of the packwiz-installer JAR file");
		options.addOption("g", "no-gui", false, "Don't display a GUI to show update progress");
		options.addOption("h", "help", false, "Display this message");
	}
	
}
