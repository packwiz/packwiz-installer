package link.infra.packwiz.installer.ui.cli

import link.infra.packwiz.installer.ui.IUserInterface
import link.infra.packwiz.installer.ui.IUserInterface.ExceptionListResult
import link.infra.packwiz.installer.ui.data.ExceptionDetails
import link.infra.packwiz.installer.ui.data.IOptionDetails
import link.infra.packwiz.installer.ui.data.InstallProgress
import kotlin.system.exitProcess

class CLIHandler : IUserInterface {
	@Volatile
	override var optionsButtonPressed = false
	@Volatile
	override var cancelButtonPressed = false

	override fun handleException(e: Exception) {
		e.printStackTrace()
	}

	override fun show() {}
	override fun dispose() {}
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
		println(sb.toString())
	}

	override fun showOptions(options: List<IOptionDetails>): Boolean {
		for (opt in options) {
			opt.optionValue = true
			// TODO: implement option choice in the CLI?
			println("Warning: accepting option " + opt.name + " as option choosing is not implemented in the CLI")
		}
		return false // Can't be cancelled!
	}

	override fun showExceptions(exceptions: List<ExceptionDetails>, numTotal: Int, allowsIgnore: Boolean): ExceptionListResult {
		println("Failed to download modpack, the following errors were encountered:")
		for (ex in exceptions) {
			println(ex.name + ": ")
			ex.exception.printStackTrace()
		}
		exitProcess(1)
	}
}