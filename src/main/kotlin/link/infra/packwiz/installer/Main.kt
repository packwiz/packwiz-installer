package link.infra.packwiz.installer

import link.infra.packwiz.installer.metadata.SpaceSafeURI
import link.infra.packwiz.installer.ui.CLIHandler
import link.infra.packwiz.installer.ui.InputStateHandler
import link.infra.packwiz.installer.ui.InstallWindow
import org.apache.commons.cli.DefaultParser
import org.apache.commons.cli.Options
import org.apache.commons.cli.ParseException
import java.awt.EventQueue
import java.awt.GraphicsEnvironment
import java.net.URISyntaxException
import javax.swing.JOptionPane
import javax.swing.UIManager
import kotlin.system.exitProcess

@Suppress("unused")
class Main(args: Array<String>) {
	private fun startup(args: Array<String>) {
		val options = Options()
		addNonBootstrapOptions(options)
		addBootstrapOptions(options)

		val parser = DefaultParser()
		val cmd = try {
			parser.parse(options, args)
		} catch (e: ParseException) {
			e.printStackTrace()
			try {
				UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName())
			} catch (e1: Exception) {
				// Ignore the exceptions, just continue using the ugly L&F
			}
			JOptionPane.showMessageDialog(null, e.message, "packwiz-installer", JOptionPane.ERROR_MESSAGE)
			exitProcess(1)
		}

		// if "headless", GUI creation will fail anyway!
		val ui = if (cmd.hasOption("no-gui") || GraphicsEnvironment.isHeadless()) {
			CLIHandler()
		} else InstallWindow()

		val unparsedArgs = cmd.args
		if (unparsedArgs.size > 1) {
			ui.handleExceptionAndExit(RuntimeException("Too many arguments specified!"))
		} else if (unparsedArgs.isEmpty()) {
			ui.handleExceptionAndExit(RuntimeException("URI to install from must be specified!"))
		}

		cmd.getOptionValue("title")?.also {
			ui.setTitle(it)
		}

		val inputStateHandler = InputStateHandler()
		ui.show(inputStateHandler)

		val uOptions = UpdateManager.Options().apply {
			side = cmd.getOptionValue("side")?.let { UpdateManager.Options.Side.from(it) } ?: side
			packFolder = cmd.getOptionValue("pack-folder") ?: packFolder
			manifestFile = cmd.getOptionValue("meta-file") ?: manifestFile
		}

		try {
			uOptions.downloadURI = SpaceSafeURI(unparsedArgs[0])
		} catch (e: URISyntaxException) {
			// TODO: better error message?
			ui.handleExceptionAndExit(e)
		}

		// Start update process!
		// TODO: start in SwingWorker?
		try {
			ui.executeManager {
				try {
					UpdateManager(uOptions, ui, inputStateHandler)
				} catch (e: Exception) { // TODO: better error message?
					ui.handleExceptionAndExit(e)
				}
			}
		} catch (e: Exception) { // TODO: better error message?
			ui.handleExceptionAndExit(e)
		}
	}

	companion object {
		// Called by packwiz-installer-bootstrap to set up the help command
		fun addNonBootstrapOptions(options: Options) {
			options.addOption("s", "side", true, "Side to install mods from (client/server, defaults to client)")
			options.addOption(null, "title", true, "Title of the installer window")
			options.addOption(null, "pack-folder", true, "Folder to install the pack to (defaults to the JAR directory)")
			options.addOption(null, "meta-file", true, "JSON file to store pack metadata, relative to the pack folder (defaults to packwiz.json)")
		}

		// TODO: link these somehow so they're only defined once?
		private fun addBootstrapOptions(options: Options) {
			options.addOption(null, "bootstrap-update-url", true, "Github API URL for checking for updates")
			options.addOption(null, "bootstrap-update-token", true, "Github API Access Token, for private repositories")
			options.addOption(null, "bootstrap-no-update", false, "Don't update packwiz-installer")
			options.addOption(null, "bootstrap-main-jar", true, "Location of the packwiz-installer JAR file")
			options.addOption("g", "no-gui", false, "Don't display a GUI to show update progress")
			options.addOption("h", "help", false, "Display this message") // Implemented in packwiz-installer-bootstrap!
		}
	}

	// Actual main() is in RequiresBootstrap!
	init {
		// Big overarching try/catch just in case everything breaks
		try {
			startup(args)
		} catch (e: Exception) {
			e.printStackTrace()
			EventQueue.invokeLater {
				JOptionPane.showMessageDialog(null,
						"A fatal error occurred: \n" + e.javaClass.canonicalName + ": " + e.message,
						"packwiz-installer", JOptionPane.ERROR_MESSAGE)
				exitProcess(1)
			}
			// In case the EventQueue is broken, exit after 1 minute
			Thread.sleep(60 * 1000.toLong())
			exitProcess(1)
		}
	}
}