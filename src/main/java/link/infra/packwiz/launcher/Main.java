package link.infra.packwiz.launcher;

import java.awt.EventQueue;

import javax.swing.JFrame;
import javax.swing.UIManager;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import javax.swing.JProgressBar;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.border.EmptyBorder;

public class Main {

	private JFrame frmPackwizlauncher;

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
		frmPackwizlauncher.setTitle("packwiz-launcher");
		frmPackwizlauncher.setBounds(100, 100, 450, 87);
		frmPackwizlauncher.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		
		JPanel panel = new JPanel();
		panel.setBorder(new EmptyBorder(10, 10, 10, 10));
		frmPackwizlauncher.getContentPane().add(panel, BorderLayout.CENTER);
		panel.setLayout(new BorderLayout(0, 0));
		
		JProgressBar progressBar = new JProgressBar();
		progressBar.setValue(50);
		panel.add(progressBar, BorderLayout.NORTH);
		
		JLabel lblProgresslabel = new JLabel("Loading...");
		panel.add(lblProgresslabel, BorderLayout.SOUTH);
		
		JPanel panel_1 = new JPanel();
		frmPackwizlauncher.getContentPane().add(panel_1, BorderLayout.EAST);
		
		JButton btnOptions = new JButton("Options...");
		panel_1.add(btnOptions);
	}

}
