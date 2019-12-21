package link.infra.packwiz.installer.ui

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

	fun showExceptions(exceptions: List<ExceptionDetails>, numTotal: Int, allowsIgnore: Boolean): Future<ExceptionListResult>
	@JvmDefault
	fun disableOptionsButton() {}

	@JvmDefault
	fun showCancellationDialog(): Future<CancellationResult> {
		return CompletableFuture<CancellationResult>().apply {
			complete(CancellationResult.QUIT)
		}
	}

	enum class ExceptionListResult {
		CONTINUE, CANCEL, IGNORE
	}

	enum class CancellationResult {
		QUIT, CONTINUE
	}
}