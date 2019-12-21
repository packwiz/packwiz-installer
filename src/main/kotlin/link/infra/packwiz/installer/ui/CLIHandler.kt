package link.infra.packwiz.installer.ui

import link.infra.packwiz.installer.ui.IUserInterface.ExceptionListResult
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future

class CLIHandler : IUserInterface {
	override fun handleException(e: Exception) {
		e.printStackTrace()
	}

	override fun show(handler: InputStateHandler) {}
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

	override fun executeManager(task: () -> Unit) {
		task()
		println("Finished successfully!")
	}

	override fun showOptions(options: List<IOptionDetails>): Future<Boolean> {
		for (opt in options) {
			opt.optionValue = true
			// TODO: implement option choice in the CLI?
			println("Warning: accepting option " + opt.name + " as option choosing is not implemented in the CLI")
		}
		return CompletableFuture<Boolean>().apply {
			complete(false) // Can't be cancelled!
		}
	}

	override fun showExceptions(exceptions: List<ExceptionDetails>, numTotal: Int, allowsIgnore: Boolean): Future<ExceptionListResult> {
		val future = CompletableFuture<ExceptionListResult>()
		future.complete(ExceptionListResult.CANCEL)
		return future
	}
}