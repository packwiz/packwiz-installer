package link.infra.packwiz.launcher;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.EventQueue;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;

public class Main {
	
	// TODO: move to seperate file, make usable without GUI

	private JFrame frmPackwizlauncher;
	private UpdateManager updateManager = new UpdateManager();

	/**
	 * Launch the application.
	 */
	public static void main(String[] args) {
		EventQueue.invokeLater(new Runnable() {
			public void run() {
				try {
					UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
					Main window = new Main();
					window.frmPackwizlauncher.setVisible(true);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
	}

	/**
	 * Create the application.
	 */
	public Main() {
		initialize();
	}

	/**
	 * Initialize the contents of the frame.
	 */
	private void initialize() {
		frmPackwizlauncher = new JFrame();
		frmPackwizlauncher.setTitle("Updating modpack...");
		frmPackwizlauncher.setBounds(100, 100, 493, 95);
		frmPackwizlauncher.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frmPackwizlauncher.setLocationRelativeTo(null);
		
		JPanel panel = new JPanel();
		panel.setBorder(new EmptyBorder(10, 10, 10, 10));
		frmPackwizlauncher.getContentPane().add(panel, BorderLayout.CENTER);
		panel.setLayout(new BorderLayout(0, 0));
		
		JProgressBar progressBar = new JProgressBar();
		progressBar.setValue(50);
		panel.add(progressBar, BorderLayout.CENTER);
		
		JLabel lblProgresslabel = new JLabel("Loading...");
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
				updateManager.cleanup();
				frmPackwizlauncher.dispose();
			}
		});
		btnCancel.setAlignmentX(Component.CENTER_ALIGNMENT);
		GridBagConstraints gbc_btnCancel = new GridBagConstraints();
		gbc_btnCancel.gridx = 0;
		gbc_btnCancel.gridy = 1;
		panel_1.add(btnCancel, gbc_btnCancel);
	}

}
