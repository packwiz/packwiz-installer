package link.infra.packwiz.installer.ui

import link.infra.packwiz.installer.ui.data.ExceptionDetails
import link.infra.packwiz.installer.ui.data.IOptionDetails
import link.infra.packwiz.installer.ui.data.InstallProgress

interface IUserInterface {
	fun show()
	fun dispose()

	fun showErrorAndExit(message: String): Nothing {
		showErrorAndExit(message, null)
	}
	fun showErrorAndExit(message: String, e: Exception?): Nothing

	var title: String
	fun submitProgress(progress: InstallProgress)
	// Return true if the installation was cancelled!
	fun showOptions(options: List<IOptionDetails>): Boolean

	fun showExceptions(exceptions: List<ExceptionDetails>, numTotal: Int, allowsIgnore: Boolean): ExceptionListResult
	fun disableOptionsButton(hasOptions: Boolean) {}

	fun showCancellationDialog(): CancellationResult = CancellationResult.QUIT

	fun showCustomDialog(message: String, title: String, options: Array<String>): String? = null

	fun awaitOptionalButton(showCancel: Boolean)

	enum class ExceptionListResult {
		CONTINUE, CANCEL, IGNORE
	}

	enum class CancellationResult {
		QUIT, CONTINUE
	}

	var optionsButtonPressed: Boolean
	var cancelButtonPressed: Boolean

	var firstInstall: Boolean
}