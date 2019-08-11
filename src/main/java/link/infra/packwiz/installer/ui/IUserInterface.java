package link.infra.packwiz.installer.ui;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

public interface IUserInterface {
	
	void show(InputStateHandler handler);

	void handleException(Exception e);

	default void handleExceptionAndExit(Exception e) {
		handleException(e);
		System.exit(1);
	}

	default void setTitle(String title) {}

	void submitProgress(InstallProgress progress);

	void executeManager(Runnable task);

	// Return true if the installation was cancelled!
	Future<Boolean> showOptions(List<IOptionDetails> option);

	Future<IExceptionDetails.ExceptionListResult> showExceptions(List<IExceptionDetails> opts, int numTotal, boolean allowsIgnore);

	default void disableOptionsButton() {}

	// Should not return CONTINUE
	default Future<IExceptionDetails.ExceptionListResult> showCancellationDialog() {
		CompletableFuture<IExceptionDetails.ExceptionListResult> future = new CompletableFuture<>();
		future.complete(IExceptionDetails.ExceptionListResult.CANCEL);
		return future;
	}
	
}
