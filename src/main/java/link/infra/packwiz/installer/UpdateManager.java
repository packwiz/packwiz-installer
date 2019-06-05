package link.infra.packwiz.installer;

import java.net.URI;

public class UpdateManager {
	
	public final Options opts;
	public final IUserInterface ui;
	
	public static class Options {
		public URI downloadURI;
		public String manifestFile = "packwiz.json";
	}
	
	public UpdateManager(Options opts, IUserInterface ui) {
		this.opts = opts;
		this.ui = ui;
	}
	
	public void cleanup() {
		
	}
}
