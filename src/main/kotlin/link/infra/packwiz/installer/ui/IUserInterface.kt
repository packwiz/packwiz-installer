package link.infra.packwiz.installer.ui

import link.infra.packwiz.installer.ui.data.ExceptionDetails
import link.infra.packwiz.installer.ui.data.IOptionDetails
import link.infra.packwiz.installer.ui.data.InstallProgress
import kotlin.system.exitProcess

interface IUserInterface {
	fun show()
	fun dispose()
	fun handleException(e: Exception)
	fun handleExceptionAndExit(e: Exception) {
		handleException(e)
		exitProcess(1)
	}

	fun setTitle(title: String) {}
	fun submitProgress(progress: InstallProgress)
	// Return true if the installation was cancelled!
	fun showOptions(options: List<IOptionDetails>): Boolean

	fun showExceptions(exceptions: List<ExceptionDetails>, numTotal: Int, allowsIgnore: Boolean): ExceptionListResult
	fun disableOptionsButton() {}

	fun showCancellationDialog(): CancellationResult = CancellationResult.QUIT

	enum class ExceptionListResult {
		CONTINUE, CANCEL, IGNORE
	}

	enum class CancellationResult {
		QUIT, CONTINUE
	}

	var optionsButtonPressed: Boolean
	var cancelButtonPressed: Boolean
}