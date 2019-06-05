package link.infra.packwiz.installer;

public class CLIHandler implements IUserInterface {

	@Override
	public void handleException(Exception e) {
		e.printStackTrace();
	}

	@Override
	public void show() {}
	
}
