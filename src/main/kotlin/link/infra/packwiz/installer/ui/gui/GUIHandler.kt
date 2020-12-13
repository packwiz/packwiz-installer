package link.infra.packwiz.installer.ui.gui

import link.infra.packwiz.installer.ui.IUserInterface
import link.infra.packwiz.installer.ui.IUserInterface.ExceptionListResult
import link.infra.packwiz.installer.ui.data.ExceptionDetails
import link.infra.packwiz.installer.ui.data.IOptionDetails
import link.infra.packwiz.installer.ui.data.InstallProgress
import java.awt.EventQueue
import java.util.concurrent.CompletableFuture
import javax.swing.JDialog
import javax.swing.JOptionPane
import javax.swing.UIManager
import kotlin.system.exitProcess

class GUIHandler : IUserInterface {
	private lateinit var frmPackwizlauncher: InstallWindow

	@Volatile
	override var optionsButtonPressed = false
	@Volatile
	override var cancelButtonPressed = false

	private var title = "Updating modpack..."

	init {
		EventQueue.invokeAndWait {
			try {
				UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName())
			} catch (e: Exception) {
				println("Failed to set look and feel:")
				e.printStackTrace()
			}
			frmPackwizlauncher = InstallWindow(this).apply {
				title = this@GUIHandler.title
			}
		}
	}

	override fun show() = EventQueue.invokeLater {
		frmPackwizlauncher.isVisible = true
	}

	override fun dispose() = EventQueue.invokeAndWait {
		frmPackwizlauncher.dispose()
	}

	override fun handleException(e: Exception) {
		e.printStackTrace()
		EventQueue.invokeAndWait {
			JOptionPane.showMessageDialog(null,
					"An error occurred: \n" + e.javaClass.canonicalName + ": " + e.message,
					title, JOptionPane.ERROR_MESSAGE)
		}
	}

	override fun handleExceptionAndExit(e: Exception) {
		e.printStackTrace()
		EventQueue.invokeAndWait {
			JOptionPane.showMessageDialog(null,
					"A fatal error occurred: \n" + e.javaClass.canonicalName + ": " + e.message,
					title, JOptionPane.ERROR_MESSAGE)
			exitProcess(1)
		}
	}

	override fun setTitle(title: String) {
		this.title = title
		EventQueue.invokeLater { frmPackwizlauncher.title = title }
	}

	override fun submitProgress(progress: InstallProgress) {
		val sb = StringBuilder()
		if (progress.hasProgress) {
			sb.append('(')
			sb.append(progress.progress)
			sb.append('/')
			sb.append(progress.progressTotal)
			sb.append(") ")
		}
		sb.append(progress.message)
		// TODO: better logging library?
		println(sb.toString())
		EventQueue.invokeLater {
			frmPackwizlauncher.displayProgress(progress)
		}
	}

	override fun showOptions(options: List<IOptionDetails>): Boolean {
		val future = CompletableFuture<Boolean>()
		EventQueue.invokeAndWait {
			if (options.isEmpty()) {
				JOptionPane.showMessageDialog(null,
					"This modpack has no optional mods!",
					"Optional mods", JOptionPane.INFORMATION_MESSAGE)
				future.complete(false)
			} else {
				OptionsSelectWindow(options, future, frmPackwizlauncher).apply {
					defaultCloseOperation = JDialog.DISPOSE_ON_CLOSE
					isVisible = true
				}
			}
		}
		return future.get()
	}

	override fun showExceptions(exceptions: List<ExceptionDetails>, numTotal: Int, allowsIgnore: Boolean): ExceptionListResult {
		val future = CompletableFuture<ExceptionListResult>()
		EventQueue.invokeLater {
			ExceptionListWindow(exceptions, future, numTotal, allowsIgnore, frmPackwizlauncher).apply {
				defaultCloseOperation = JDialog.DISPOSE_ON_CLOSE
				isVisible = true
			}
		}
		return future.get()
	}

	override fun disableOptionsButton() = EventQueue.invokeLater {
		frmPackwizlauncher.disableOptionsButton()
	}

	override fun showCancellationDialog(): IUserInterface.CancellationResult {
		val future = CompletableFuture<IUserInterface.CancellationResult>()
		EventQueue.invokeLater {
			val buttons = arrayOf("Quit", "Ignore")
			val result = JOptionPane.showOptionDialog(frmPackwizlauncher,
					"The installation was cancelled. Would you like to quit the game, or ignore the update and start the game?",
					"Cancelled installation",
					JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE, null, buttons, buttons[0])
			future.complete(if (result == 0) IUserInterface.CancellationResult.QUIT else IUserInterface.CancellationResult.CONTINUE)
		}
		return future.get()
	}
}