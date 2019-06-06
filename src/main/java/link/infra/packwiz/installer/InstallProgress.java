package link.infra.packwiz.installer;

public class InstallProgress {
	public final String message;
	public final boolean hasProgress;
	public final int progress;
	public final int progressTotal;

	InstallProgress(String message) {
		this.message = message;
		hasProgress = false;
		progress = 0;
		progressTotal = 0;
	}

	InstallProgress(String message, int progress, int progressTotal) {
		this.message = message;
		hasProgress = true;
		this.progress = progress;
		this.progressTotal = progressTotal;
	}
}