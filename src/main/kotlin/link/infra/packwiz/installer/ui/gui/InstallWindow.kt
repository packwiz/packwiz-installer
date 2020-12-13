package link.infra.packwiz.installer.ui.gui

import link.infra.packwiz.installer.ui.IUserInterface
import link.infra.packwiz.installer.ui.IUserInterface.ExceptionListResult
import link.infra.packwiz.installer.ui.data.ExceptionDetails
import link.infra.packwiz.installer.ui.data.IOptionDetails
import link.infra.packwiz.installer.ui.data.InstallProgress
import java.awt.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future
import javax.swing.*
import javax.swing.border.EmptyBorder
import kotlin.system.exitProcess

class InstallWindow : IUserInterface {
	private lateinit var frmPackwizlauncher: JFrame
	private lateinit var lblProgresslabel: JLabel
	private lateinit var progressBar: JProgressBar
	private lateinit var btnOptions: JButton

	@Volatile
	override var optionsButtonPressed = false
	@Volatile
	override var cancelButtonPressed = false

	private var title = "Updating modpack..."

	// TODO: separate JFrame junk from IUserInterface junk?

	init {
		EventQueue.invokeAndWait {
			frmPackwizlauncher = JFrame().apply {
				title = this@InstallWindow.title
				setBounds(100, 100, 493, 95)
				defaultCloseOperation = JFrame.EXIT_ON_CLOSE
				setLocationRelativeTo(null)

				// Progress bar and loading text
				add(JPanel().apply {
					border = EmptyBorder(10, 10, 10, 10)
					layout = BorderLayout(0, 0)

					progressBar = JProgressBar().apply {
						isIndeterminate = true
					}
					add(progressBar, BorderLayout.CENTER)

					lblProgresslabel = JLabel("Loading...")
					add(lblProgresslabel, BorderLayout.SOUTH)
				}, BorderLayout.CENTER)

				// Buttons
				add(JPanel().apply {
					border = EmptyBorder(0, 5, 0, 5)
					layout = GridBagLayout()

					btnOptions = JButton("Optional mods...").apply {
						alignmentX = Component.CENTER_ALIGNMENT

						addActionListener {
							text = "Loading..."
							isEnabled = false
							optionsButtonPressed = true
						}
					}
					add(btnOptions, GridBagConstraints().apply {
						gridx = 0
						gridy = 0
					})

					add(JButton("Cancel").apply {
						addActionListener {
							isEnabled = false
							cancelButtonPressed = true
						}
					}, GridBagConstraints().apply {
						gridx = 0
						gridy = 1
					})
				}, BorderLayout.EAST)
			}
		}
	}

	override fun show() {
		EventQueue.invokeLater {
			try {
				// TODO: shouldn't we do this before everything else?
				UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName())
				frmPackwizlauncher.isVisible = true
			} catch (e: Exception) {
				e.printStackTrace()
			}
		}
	}

	override fun dispose() {
		EventQueue.invokeAndWait {
			frmPackwizlauncher.dispose()
		}
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
			if (progress.hasProgress) {
				progressBar.isIndeterminate = false
				progressBar.value = progress.progress
				progressBar.maximum = progress.progressTotal
			} else {
				progressBar.isIndeterminate = true
				progressBar.value = 0
			}
			lblProgresslabel.text = progress.message
		}
	}

	override fun showOptions(options: List<IOptionDetails>): Future<Boolean> {
		val future = CompletableFuture<Boolean>()
		EventQueue.invokeLater {
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
		return future
	}

	override fun showExceptions(exceptions: List<ExceptionDetails>, numTotal: Int, allowsIgnore: Boolean): Future<ExceptionListResult> {
		val future = CompletableFuture<ExceptionListResult>()
		EventQueue.invokeLater {
			ExceptionListWindow(exceptions, future, numTotal, allowsIgnore, frmPackwizlauncher).apply {
				defaultCloseOperation = JDialog.DISPOSE_ON_CLOSE
				isVisible = true
			}
		}
		return future
	}

	override fun disableOptionsButton() {
		EventQueue.invokeLater {
			btnOptions.apply {
				text = "No optional mods"
				isEnabled = false
			}
		}
	}

	override fun showCancellationDialog(): Future<IUserInterface.CancellationResult> {
		val future = CompletableFuture<IUserInterface.CancellationResult>()
		EventQueue.invokeLater {
			val buttons = arrayOf("Quit", "Ignore")
			val result = JOptionPane.showOptionDialog(frmPackwizlauncher,
					"The installation was cancelled. Would you like to quit the game, or ignore the update and start the game?",
					"Cancelled installation",
					JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE, null, buttons, buttons[0])
			future.complete(if (result == 0) IUserInterface.CancellationResult.QUIT else IUserInterface.CancellationResult.CONTINUE)
		}
		return future
	}
}