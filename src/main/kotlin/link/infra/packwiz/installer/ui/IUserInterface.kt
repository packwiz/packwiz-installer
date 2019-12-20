package link.infra.packwiz.installer.ui

import link.infra.packwiz.installer.ui.IExceptionDetails.ExceptionListResult
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future
import kotlin.system.exitProcess

interface IUserInterface {
	fun show(handler: InputStateHandler)
	fun handleException(e: Exception)
	@JvmDefault
	fun handleExceptionAndExit(e: Exception) {
		handleException(e)
		exitProcess(1)
	}

	@JvmDefault
	fun setTitle(title: String) {}
	fun submitProgress(progress: InstallProgress)
	fun executeManager(task: () -> Unit)
	// Return true if the installation was cancelled!
	fun showOptions(options: List<IOptionDetails>): Future<Boolean>

	fun showExceptions(exceptions: List<IExceptionDetails>, numTotal: Int, allowsIgnore: Boolean): Future<ExceptionListResult>
	@JvmDefault
	fun disableOptionsButton() {}
	// Should not return CONTINUE
	@JvmDefault
	fun showCancellationDialog(): Future<ExceptionListResult> {
		return CompletableFuture<ExceptionListResult>().apply {
			complete(ExceptionListResult.CANCEL)
		}
	}
}