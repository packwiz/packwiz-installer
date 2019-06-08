package link.infra.packwiz.installer.ui;

public class CLIHandler implements IUserInterface {

	@Override
	public void handleException(Exception e) {
		e.printStackTrace();
	}

	@Override
	public void show() {}

	@Override
	public void submitProgress(InstallProgress progress) {
		StringBuilder sb = new StringBuilder();
		if (progress.hasProgress) {
			sb.append('(');
			sb.append(progress.progress);
			sb.append('/');
			sb.append(progress.progressTotal);
			sb.append(") ");
		}
		sb.append(progress.message);
		System.out.println(sb.toString());
	}

	@Override
	public void executeManager(Runnable task) {
		task.run();
	}
	
}
