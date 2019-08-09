package link.infra.packwiz.installer.ui;

import java.util.List;

public interface IUserInterface {
	
	void show();

	void handleException(Exception e);
	
	/**
	 * This might not exit straight away, return after calling this!
	 */
	default void handleExceptionAndExit(Exception e) {
		handleException(e);
		System.exit(1);
	}

	default void setTitle(String title) {}

	void submitProgress(InstallProgress progress);

	void executeManager(Runnable task);

	void showOptions(List<IOptionDetails> option);
	
}
