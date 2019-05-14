package link.infra.packwiz.installer;

public class UpdateManager {
	Thread updateThread = new Thread(new UpdateThread());
	
	public void cleanup() {
		
	}
}
