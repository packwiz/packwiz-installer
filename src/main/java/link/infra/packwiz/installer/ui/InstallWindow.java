package link.infra.packwiz.installer.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.EventQueue;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;

public class InstallWindow implements IUserInterface {

	private JFrame frmPackwizlauncher;
	private JLabel lblProgresslabel;
	private JProgressBar progressBar;

	private String title = "Updating modpack...";
	private SwingWorkerButWithPublicPublish<Void, InstallProgress> worker;
	private AtomicBoolean aboutToCrash = new AtomicBoolean();

	@Override
	public void show() {
		EventQueue.invokeLater(new Runnable() {
			public void run() {
				try {
					UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
					InstallWindow.this.initialize();
					InstallWindow.this.frmPackwizlauncher.setVisible(true);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
	}

	/**
	 * Initialize the contents of the frame.
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
		
		JButton btnOptions = new JButton("Options...");
		btnOptions.setAlignmentX(Component.CENTER_ALIGNMENT);
		GridBagConstraints gbc_btnOptions = new GridBagConstraints();
		gbc_btnOptions.gridx = 0;
		gbc_btnOptions.gridy = 0;
		panel_1.add(btnOptions, gbc_btnOptions);
		
		JButton btnCancel = new JButton("Cancel");
		btnCancel.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				if (worker != null) {
					worker.cancel(true);
				}
				frmPackwizlauncher.dispose();
				// TODO: show window to ask user what to do
				System.out.println("Update process cancelled by user!");
				System.exit(1);
			}
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
		EventQueue.invokeLater(new Runnable() {
			public void run() {
				JOptionPane.showMessageDialog(null, "An error occurred: \n" + e.getClass().getCanonicalName() + ": " + e.getMessage(), title, JOptionPane.ERROR_MESSAGE);
			}
		});
	}

	@Override
	public void handleExceptionAndExit(Exception e) {
		e.printStackTrace();
		// Used to prevent the done() handler of SwingWorker executing if the invokeLater hasn't happened yet
		aboutToCrash.set(true);
		EventQueue.invokeLater(new Runnable() {
			public void run() {
				JOptionPane.showMessageDialog(null, "A fatal error occurred: \n" + e.getClass().getCanonicalName() + ": " + e.getMessage(), title, JOptionPane.ERROR_MESSAGE);
				System.exit(1);
			}
		});
	}
	
	@Override
	public void setTitle(String title) {
		this.title = title;
		if (frmPackwizlauncher != null) {
			EventQueue.invokeLater(new Runnable() {
				public void run() {
					InstallWindow.this.frmPackwizlauncher.setTitle(title);
				}
			});
		}
	}

	@Override
	public void submitProgress(InstallProgress progress) {
		if (worker != null) {
			worker.publishPublic(progress);
		}
	}

	@Override
	public void executeManager(Runnable task) {
		EventQueue.invokeLater(new Runnable() {
			public void run() {
				worker = new SwingWorkerButWithPublicPublish<Void, InstallProgress>() {

					@Override
					protected Void doInBackground() throws Exception {
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
			}
		});
	}

}
