package link.infra.packwiz.installer.ui.gui

import link.infra.packwiz.installer.ui.IUserInterface
import link.infra.packwiz.installer.ui.IUserInterface.ExceptionListResult
import link.infra.packwiz.installer.ui.data.ExceptionDetails
import link.infra.packwiz.installer.ui.data.IOptionDetails
import link.infra.packwiz.installer.ui.data.InstallProgress
import link.infra.packwiz.installer.util.Log
import java.awt.EventQueue
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import javax.swing.JDialog
import javax.swing.JOptionPane
import javax.swing.UIManager
import kotlin.system.exitProcess

class GUIHandler : IUserInterface {
	private lateinit var frmPackwizlauncher: InstallWindow

	@Volatile
	override var optionsButtonPressed = false
		set(value) {
			optionalSelectedLatch.countDown()
			field = value
		}
	@Volatile
	override var cancelButtonPressed = false
		set(value) {
			optionalSelectedLatch.countDown()
			field = value
		}
	var okButtonPressed = false
		set(value) {
			optionalSelectedLatch.countDown()
			field = value
		}
	@Volatile
	override var firstInstall = false

	override var title = "packwiz-installer"
		set(value) {
			field = value
			EventQueue.invokeLater { frmPackwizlauncher.title = value }
		}

	init {
		EventQueue.invokeAndWait {
			try {
				UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName())
			} catch (e: Exception) {
				Log.warn("Failed to set look and feel", e)
			}
			frmPackwizlauncher = InstallWindow(this).apply {
				title = this@GUIHandler.title
			}
		}
	}

	private val visibleCountdownLatch = CountDownLatch(1)
	private val optionalSelectedLatch = CountDownLatch(1)

	override fun show() = EventQueue.invokeLater {
		frmPackwizlauncher.isVisible = true
		visibleCountdownLatch.countDown()
	}

	override fun dispose() = EventQueue.invokeAndWait {
		frmPackwizlauncher.dispose()
	}

	override fun showErrorAndExit(message: String, e: Exception?): Nothing {
		val buttons = arrayOf("Quit", if (firstInstall) "Continue without installing" else "Continue without updating")
		if (e != null) {
			Log.fatal(message, e)
			EventQueue.invokeAndWait {
				val result = JOptionPane.showOptionDialog(frmPackwizlauncher,
					"$message: $e",
					title,
					JOptionPane.YES_NO_OPTION, JOptionPane.ERROR_MESSAGE, null, buttons, buttons[0])
				if (result == 1) {
					Log.info("User selected to continue without installing/updating, exiting with code 0...")
					exitProcess(0)
				} else {
					Log.info("User selected to quit, exiting with code 1...")
					exitProcess(1)
				}
			}
		} else {
			Log.fatal(message)
			EventQueue.invokeAndWait {
				val result = JOptionPane.showOptionDialog(frmPackwizlauncher,
					message,
					title,
					JOptionPane.YES_NO_OPTION, JOptionPane.ERROR_MESSAGE, null, buttons, buttons[0])
				if (result == 1) {
					Log.info("User selected to continue without installing/updating, exiting with code 0...")
					exitProcess(0)
				} else {
					Log.info("User selected to quit, exiting with code 1...")
					exitProcess(1)
				}
			}
		}
		exitProcess(1)
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
		Log.info(sb.toString())
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

	override fun disableOptionsButton(hasOptions: Boolean) = EventQueue.invokeLater {
		frmPackwizlauncher.disableOptionsButton(hasOptions)
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

	override fun showUpdateConfirmationDialog(oldVersions: List<Pair<String, String?>>, newVersions: List<Pair<String, String?>>): IUserInterface.UpdateConfirmationResult {
		assert(newVersions.isNotEmpty())
		val future = CompletableFuture<IUserInterface.UpdateConfirmationResult>()
		EventQueue.invokeLater {
			val oldVersIndex = oldVersions.map { it.first to it.second }.toMap()
			val newVersIndex = newVersions.map { it.first to it.second }.toMap()
			val message = StringBuilder()
			message.append("<html>" +
					"This modpack uses newer versions of the following:<br>" +
					"<ul>")

			for (oldVer in oldVersions) {
				val correspondingNewVer = newVersIndex[oldVer.first]
				message.append("<li>")
				message.append(oldVer.first.replaceFirstChar { it.uppercase() })
				message.append(": <font color=${if (oldVer.second != correspondingNewVer) "#ff0000" else "#000000"}>")
				message.append(oldVer.second ?: "Not found")
				message.append("</font></li>")
			}
			message.append("</ul>")

			message.append("New versions:" +
					"<ul>")
			for (newVer in newVersions) {
				val correspondingOldVer = oldVersIndex[newVer.first]
				message.append("<li>")
				message.append(newVer.first.replaceFirstChar { it.uppercase() })
				message.append(": <font color=${if (newVer.second != correspondingOldVer) "#00ff00" else "#000000"}>")
				message.append(newVer.second ?: "Not found")
				message.append("</font></li>")
			}
			message.append("</ul><br>" +
					"Would you like to update the versions, launch without updating, or cancel the launch?")


			val options = arrayOf("Cancel", "Continue anyways", "Update")
			val result = JOptionPane.showOptionDialog(frmPackwizlauncher, message,
					"Updating MultiMC versions",
					JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[2])
			future.complete(
				when (result) {
					JOptionPane.CLOSED_OPTION, 0 -> IUserInterface.UpdateConfirmationResult.CANCELLED
					1 -> IUserInterface.UpdateConfirmationResult.CONTINUE
					2 -> IUserInterface.UpdateConfirmationResult.UPDATE
					else -> IUserInterface.UpdateConfirmationResult.CANCELLED
				}
			)
		}
		return future.get()
	}

	override fun awaitOptionalButton(showCancel: Boolean) {
		EventQueue.invokeAndWait {
			frmPackwizlauncher.showOk(!showCancel)
		}
		visibleCountdownLatch.await()
		optionalSelectedLatch.await()
		EventQueue.invokeLater {
			frmPackwizlauncher.hideOk()
		}
	}
}