package link.infra.packwiz.installer.ui.gui

import link.infra.packwiz.installer.Msgs
import link.infra.packwiz.installer.ui.data.InstallProgress
import java.awt.BorderLayout
import java.awt.Component
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.*
import javax.swing.border.EmptyBorder

class InstallWindow(private val handler: GUIHandler) : JFrame() {
	private var lblProgresslabel: JLabel
	private var progressBar: JProgressBar
	private var btnOptions: JButton
	private val btnCancel: JButton
	private val btnOk: JButton
	private val buttonsPanel: JPanel

	init {
		setBounds(100, 100, 493, 95)
		// Works better with tiling window managers - there isn't any reason to change window size currently anyway
		isResizable = false
		defaultCloseOperation = EXIT_ON_CLOSE
		setLocationRelativeTo(null)

		// Progress bar and loading text
		add(JPanel().apply {
			border = EmptyBorder(10, 10, 10, 10)
			layout = BorderLayout(0, 0)

			progressBar = JProgressBar().apply {
				isIndeterminate = true
			}
			add(progressBar, BorderLayout.CENTER)

			lblProgresslabel = JLabel(Msgs.hintMore(Msgs.loading()))
			add(lblProgresslabel, BorderLayout.SOUTH)
		}, BorderLayout.CENTER)

		// Buttons
		buttonsPanel = JPanel().apply {
			border = EmptyBorder(0, 5, 0, 5)
			layout = GridBagLayout()

			btnOptions = JButton(Msgs.hintMore(Msgs.optionalMods())).apply {
				alignmentX = Component.CENTER_ALIGNMENT

				addActionListener {
					text = Msgs.hintMore(Msgs.loading())
					isEnabled = false
					handler.optionsButtonPressed = true
				}
			}
			add(btnOptions, GridBagConstraints().apply {
				gridx = 1
				gridy = 0
			})

			btnCancel = JButton(Msgs.cancel()).apply {
				addActionListener {
					isEnabled = false
					handler.cancelButtonPressed = true
				}
			}
			add(btnCancel, GridBagConstraints().apply {
				gridx = 1
				gridy = 1
			})
		}

		btnOk = JButton(Msgs.continueText()).apply {
			addActionListener {
				handler.okButtonPressed = true
			}
		}
		add(buttonsPanel, BorderLayout.EAST)
	}

	fun displayProgress(progress: InstallProgress) {
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

	fun disableOptionsButton(hasOptions: Boolean) {
		btnOptions.apply {
			text = if (hasOptions) { Msgs.hintMore(Msgs.optionalMods()) } else { Msgs.noOptionalMods() }
 			isEnabled = false
		}
	}

	fun showOk(hideCancel: Boolean) {
		if (hideCancel) {
			buttonsPanel.add(btnOk, GridBagConstraints().apply {
				gridx = 1
				gridy = 1
			})
			buttonsPanel.remove(btnCancel)
		} else {
			buttonsPanel.add(btnOk, GridBagConstraints().apply {
				gridx = 0
				gridy = 1
			})
		}
		buttonsPanel.revalidate()
	}

	fun hideOk() {
		buttonsPanel.remove(btnOk)
		if (!buttonsPanel.components.contains(btnCancel)) {
			buttonsPanel.add(btnCancel, GridBagConstraints().apply {
				gridx = 1
				gridy = 1
			})
		}
		buttonsPanel.revalidate()
	}

	fun timeoutOk(remaining: Long) {
		btnOk.text = Msgs.countdown(Msgs.continueText(), remaining)
	}
}