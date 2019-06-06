package link.infra.packwiz.installer;

public interface IUserInterface {
	
	public void show();

	public void handleException(Exception e);
	
	/**
	 * This might not exit straight away, return after calling this!
	 */
	public default void handleExceptionAndExit(Exception e) {
		handleException(e);
		System.exit(1);
	};
	
	public default void setTitle(String title) {};

	public void submitProgress(InstallProgress progress);

	public void executeManager(Runnable task);
	
}
