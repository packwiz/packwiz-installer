package link.infra.packwiz.launcher;

public class UpdateManager {
	Thread updateThread = new Thread(new UpdateThread());
	
	public void cleanup() {
		
	}
}
