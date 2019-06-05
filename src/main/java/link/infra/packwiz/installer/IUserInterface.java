package link.infra.packwiz.installer;

public interface IUserInterface {
	
	public void show();

	public void handleException(Exception e);
	
	public default void handleExceptionAndExit(Exception e) {
		handleException(e);
		System.exit(1);
	};
	
	public default void setTitle(String title) {};
	
}
