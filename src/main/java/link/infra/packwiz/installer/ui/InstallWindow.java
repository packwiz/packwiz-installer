package link.infra.packwiz.installer.ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

public class InstallWindow implements IUserInterface {

	private JFrame frmPackwizlauncher;
	private JLabel lblProgresslabel;
	private JProgressBar progressBar;
	private InputStateHandler inputStateHandler;

	private String title = "Updating modpack...";
	private SwingWorkerButWithPublicPublish<Void, InstallProgress> worker;
	private AtomicBoolean aboutToCrash = new AtomicBoolean();
	private JButton btnOptions;

	@Override
	public void show(InputStateHandler handler) {
		this.inputStateHandler = handler;
		EventQueue.invokeLater(() -> {
			try {
				UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
				initialize();
				frmPackwizlauncher.setVisible(true);
			} catch (Exception e) {
				e.printStackTrace();
			}
		});
	}

	/**
	 * Initialize the contents of the frame.
	 * @wbp.parser.entryPoint
	 */
	private void initialize() {
		frmPackwizlauncher = new JFrame();
		frmPackwizlauncher.setTitle(title);
		frmPackwizlauncher.setBounds(100, 100, 493, 95);
		frmPackwizlauncher.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frmPackwizlauncher.setLocationRelativeTo(null);

		JPanel panel = new JPanel();
		panel.setBorder(new EmptyBorder(10, 10, 10, 10));
		frmPackwizlauncher.getContentPane().add(panel, BorderLayout.CENTER);
		panel.setLayout(new BorderLayout(0, 0));

		progressBar = new JProgressBar();
		progressBar.setIndeterminate(true);
		panel.add(progressBar, BorderLayout.CENTER);

		lblProgresslabel = new JLabel("Loading...");
		panel.add(lblProgresslabel, BorderLayout.SOUTH);

		JPanel panel_1 = new JPanel();
		panel_1.setBorder(new EmptyBorder(0, 5, 0, 5));
		frmPackwizlauncher.getContentPane().add(panel_1, BorderLayout.EAST);
		GridBagLayout gbl_panel_1 = new GridBagLayout();
		panel_1.setLayout(gbl_panel_1);

		btnOptions = new JButton("Optional mods...");
		btnOptions.addActionListener(e -> {
			btnOptions.setText("Loading...");
			btnOptions.setEnabled(false);
			inputStateHandler.pressOptionsButton();
		});
		btnOptions.setAlignmentX(Component.CENTER_ALIGNMENT);
		GridBagConstraints gbc_btnOptions = new GridBagConstraints();
		gbc_btnOptions.gridx = 0;
		gbc_btnOptions.gridy = 0;
		panel_1.add(btnOptions, gbc_btnOptions);

		JButton btnCancel = new JButton("Cancel");
		btnCancel.addActionListener(e -> {
			btnCancel.setEnabled(false);
			inputStateHandler.pressCancelButton();
		});
		btnCancel.setAlignmentX(Component.CENTER_ALIGNMENT);
		GridBagConstraints gbc_btnCancel = new GridBagConstraints();
		gbc_btnCancel.gridx = 0;
		gbc_btnCancel.gridy = 1;
		panel_1.add(btnCancel, gbc_btnCancel);
	}

	@Override
	public void handleException(Exception e) {
		e.printStackTrace();
		EventQueue.invokeLater(() -> {
			JOptionPane.showMessageDialog(null, "An error occurred: \n" + e.getClass().getCanonicalName() + ": " + e.getMessage(), title, JOptionPane.ERROR_MESSAGE);
		});
	}

	@Override
	public void handleExceptionAndExit(Exception e) {
		e.printStackTrace();
		// Used to prevent the done() handler of SwingWorker executing if the invokeLater hasn't happened yet
		aboutToCrash.set(true);
		EventQueue.invokeLater(() -> {
			JOptionPane.showMessageDialog(null, "A fatal error occurred: \n" + e.getClass().getCanonicalName() + ": " + e.getMessage(), title, JOptionPane.ERROR_MESSAGE);
			System.exit(1);
		});
		// Pause forever, so it blocks while we wait for System.exit to take effect
		try {
			Thread.currentThread().join();
		} catch (InterruptedException ex) {
			// no u
		}
	}

	@Override
	public void setTitle(String title) {
		this.title = title;
		if (frmPackwizlauncher != null) {
			EventQueue.invokeLater(() -> frmPackwizlauncher.setTitle(title));
		}
	}

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
		// TODO: better logging library?
		System.out.println(sb.toString());
		if (worker != null) {
			worker.publishPublic(progress);
		}
	}

	@Override
	public void executeManager(Runnable task) {
		EventQueue.invokeLater(() -> {
			worker = new SwingWorkerButWithPublicPublish<Void, InstallProgress>() {

				@Override
				protected Void doInBackground() {
					task.run();
					return null;
				}

				@Override
				protected void process(List<InstallProgress> chunks) {
					// Only process last chunk
					if (chunks.size() > 0) {
						InstallProgress prog = chunks.get(chunks.size() - 1);
						if (prog.hasProgress) {
							progressBar.setIndeterminate(false);
							progressBar.setValue(prog.progress);
							progressBar.setMaximum(prog.progressTotal);
						} else {
							progressBar.setIndeterminate(true);
							progressBar.setValue(0);
						}
						lblProgresslabel.setText(prog.message);
					}
				}

				@Override
				protected void done() {
					if (aboutToCrash.get()) {
						return;
					}
					// TODO: a better way to do this?
					frmPackwizlauncher.dispose();
					System.out.println("Finished successfully!");
					System.exit(0);
				}

			};
			worker.execute();
		});
	}

	@Override
	public Future<Boolean> showOptions(List<IOptionDetails> opts) {
		CompletableFuture<Boolean> future = new CompletableFuture<>();
		EventQueue.invokeLater(() -> {
			OptionsSelectWindow dialog = new OptionsSelectWindow(opts, future, frmPackwizlauncher);
			dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
			dialog.setVisible(true);
		});
		return future;
	}

	@Override
	public Future<IExceptionDetails.ExceptionListResult> showExceptions(List<IExceptionDetails> opts, int numTotal, boolean allowsIgnore) {
		CompletableFuture<IExceptionDetails.ExceptionListResult> future = new CompletableFuture<>();
		EventQueue.invokeLater(() -> {
			ExceptionListWindow dialog = new ExceptionListWindow(opts, future, numTotal, allowsIgnore, frmPackwizlauncher);
			dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
			dialog.setVisible(true);
		});
		return future;
	}

	@Override
	public void disableOptionsButton() {
		if (btnOptions != null) {
			btnOptions.setText("Optional mods...");
			btnOptions.setEnabled(false);
		}
	}

	@Override
	public Future<IExceptionDetails.ExceptionListResult> showCancellationDialog() {
		CompletableFuture<IExceptionDetails.ExceptionListResult> future = new CompletableFuture<>();
		EventQueue.invokeLater(() -> {
			Object[] buttons = {"Quit", "Ignore"};
			int result = JOptionPane.showOptionDialog(frmPackwizlauncher,
					"The installation was cancelled. Would you like to quit the game, or ignore the update and start the game?",
					"Cancelled installation",
					JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE, null, buttons, buttons[0]);
			future.complete(result == 0 ? IExceptionDetails.ExceptionListResult.CANCEL : IExceptionDetails.ExceptionListResult.IGNORE);
		});
		return future;
	}
}
