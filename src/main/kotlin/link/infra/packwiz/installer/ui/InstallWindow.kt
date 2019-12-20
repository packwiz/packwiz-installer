package link.infra.packwiz.installer.ui

import link.infra.packwiz.installer.ui.IExceptionDetails.ExceptionListResult
import java.awt.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.*
import javax.swing.border.EmptyBorder
import kotlin.system.exitProcess

class InstallWindow : IUserInterface {
	private val frmPackwizlauncher: JFrame
	private val lblProgresslabel: JLabel
	private val progressBar: JProgressBar
	private val btnOptions: JButton

	private var inputStateHandler: InputStateHandler? = null
	private var title = "Updating modpack..."
	private var worker: SwingWorkerButWithPublicPublish<Unit, InstallProgress>? = null
	private val aboutToCrash = AtomicBoolean()

	// TODO: separate JFrame junk from IUserInterface junk?

	init {
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
						inputStateHandler?.pressOptionsButton()
					}
				}
				add(btnOptions, GridBagConstraints().apply {
					gridx = 0
					gridy = 0
				})

				add(JButton("Cancel").apply {
					addActionListener {
						isEnabled = false
						inputStateHandler?.pressCancelButton()
					}
				}, GridBagConstraints().apply {
					gridx = 0
					gridy = 1
				})
			}, BorderLayout.EAST)
		}
	}

	override fun show(handler: InputStateHandler) {
		inputStateHandler = handler
		EventQueue.invokeLater {
			try {
				UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName())
				frmPackwizlauncher.isVisible = true
			} catch (e: Exception) {
				e.printStackTrace()
			}
		}
	}

	override fun handleException(e: Exception) {
		e.printStackTrace()
		EventQueue.invokeLater {
			JOptionPane.showMessageDialog(null,
					"An error occurred: \n" + e.javaClass.canonicalName + ": " + e.message,
					title, JOptionPane.ERROR_MESSAGE)
		}
	}

	override fun handleExceptionAndExit(e: Exception) {
		e.printStackTrace()
		// TODO: Fix this mess
		// Used to prevent the done() handler of SwingWorker executing if the invokeLater hasn't happened yet
		aboutToCrash.set(true)
		EventQueue.invokeLater {
			JOptionPane.showMessageDialog(null,
					"A fatal error occurred: \n" + e.javaClass.canonicalName + ": " + e.message,
					title, JOptionPane.ERROR_MESSAGE)
			exitProcess(1)
		}
		// Pause forever, so it blocks while we wait for System.exit to take effect
		try {
			Thread.currentThread().join()
		} catch (ex: InterruptedException) { // no u
		}
	}

	override fun setTitle(title: String) {
		this.title = title
		frmPackwizlauncher.let { frame ->
			EventQueue.invokeLater { frame.title = title }
		}
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
		worker?.publishPublic(progress)
	}

	override fun executeManager(task: Function0<Unit>) {
		EventQueue.invokeLater {
			// TODO: rewrite this stupidity to use channels??!!!
			worker = object : SwingWorkerButWithPublicPublish<Unit, InstallProgress>() {
				override fun doInBackground() {
					task.invoke()
				}

				override fun process(chunks: List<InstallProgress>) {
					// Only process last chunk
					if (chunks.isNotEmpty()) {
						val (message, hasProgress, progress, progressTotal) = chunks[chunks.size - 1]
						if (hasProgress) {
							progressBar.isIndeterminate = false
							progressBar.value = progress
							progressBar.maximum = progressTotal
						} else {
							progressBar.isIndeterminate = true
							progressBar.value = 0
						}
						lblProgresslabel.text = message
					}
				}

				override fun done() {
					if (aboutToCrash.get()) {
						return
					}
					// TODO: a better way to do this?
					frmPackwizlauncher.dispose()
					println("Finished successfully!")
					exitProcess(0)
				}
			}.also {
				it.execute()
			}
		}
	}

	override fun showOptions(options: List<IOptionDetails>): Future<Boolean> {
		val future = CompletableFuture<Boolean>()
		EventQueue.invokeLater {
			OptionsSelectWindow(options, future, frmPackwizlauncher).apply {
				defaultCloseOperation = JDialog.DISPOSE_ON_CLOSE
				isVisible = true
			}
		}
		return future
	}

	override fun showExceptions(exceptions: List<IExceptionDetails>, numTotal: Int, allowsIgnore: Boolean): Future<ExceptionListResult> {
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
		btnOptions.apply {
			text = "Optional mods..."
			isEnabled = false
		}
	}

	override fun showCancellationDialog(): Future<ExceptionListResult> {
		val future = CompletableFuture<ExceptionListResult>()
		EventQueue.invokeLater {
			val buttons = arrayOf("Quit", "Ignore")
			val result = JOptionPane.showOptionDialog(frmPackwizlauncher,
					"The installation was cancelled. Would you like to quit the game, or ignore the update and start the game?",
					"Cancelled installation",
					JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE, null, buttons, buttons[0])
			future.complete(if (result == 0) ExceptionListResult.CANCEL else ExceptionListResult.IGNORE)
		}
		return future
	}
}