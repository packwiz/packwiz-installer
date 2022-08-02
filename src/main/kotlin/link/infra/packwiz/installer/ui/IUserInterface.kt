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

	fun showUpdateConfirmationDialog(oldVersions: List<Pair<String, String?>>, newVersions: List<Pair<String, String?>>): UpdateConfirmationResult = UpdateConfirmationResult.CANCELLED

	fun awaitOptionalButton(showCancel: Boolean, timeout: Long)

	enum class ExceptionListResult {
		CONTINUE, CANCEL, IGNORE
	}

	enum class CancellationResult {
		QUIT, CONTINUE
	}

	enum class UpdateConfirmationResult {
		CANCELLED, CONTINUE, UPDATE
	}

	var optionsButtonPressed: Boolean
	var cancelButtonPressed: Boolean
	var cancelCallback: (() -> Unit)?

	var firstInstall: Boolean

}

inline fun <T> IUserInterface.wrap(message: String, inner: () -> T): T {
	return try {
		inner.invoke()
	} catch (e: Exception) {
		showErrorAndExit(message, e)
	}
}