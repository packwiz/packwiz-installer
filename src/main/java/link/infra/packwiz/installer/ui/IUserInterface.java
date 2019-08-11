package link.infra.packwiz.installer.ui;

import java.util.List;
import java.util.concurrent.Future;

public interface IUserInterface {
	
	void show();

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

	Future<IExceptionDetails.ExceptionListResult> showExceptions(List<IExceptionDetails> opts, int numTotal);
	
}
