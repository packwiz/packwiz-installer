package link.infra.packwiz.installer.ui;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

public class CLIHandler implements IUserInterface {

	@Override
	public void handleException(Exception e) {
		e.printStackTrace();
	}

	@Override
	public void show(InputStateHandler h) {}

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
		System.out.println("Finished successfully!");
	}

	@Override
	public Future<Boolean> showOptions(List<IOptionDetails> options) {
		for (IOptionDetails opt : options) {
			opt.setOptionValue(true);
			System.out.println("Warning: accepting option " + opt.getName() + " as option choosing is not implemented in the CLI");
		}
		CompletableFuture<Boolean> future = new CompletableFuture<>();
		future.complete(false); // Can't be cancelled!
		return future;
	}

	@Override
	public Future<IExceptionDetails.ExceptionListResult> showExceptions(List<IExceptionDetails> opts, int numTotal, boolean allowsIgnore) {
		CompletableFuture<IExceptionDetails.ExceptionListResult> future = new CompletableFuture<>();
		future.complete(IExceptionDetails.ExceptionListResult.CANCEL);
		return future;
	}

}
