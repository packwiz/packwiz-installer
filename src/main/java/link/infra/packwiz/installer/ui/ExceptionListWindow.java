package link.infra.packwiz.installer.ui;

import link.infra.packwiz.installer.ui.IExceptionDetails.ExceptionListResult;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.concurrent.CompletableFuture;

class ExceptionListWindow extends JDialog {

	private static final long serialVersionUID = 1L;
	private final JTextArea lblExceptionStacktrace;

	/**
	 * Create the dialog.
	 */
	ExceptionListWindow(List<IExceptionDetails> eList, CompletableFuture<ExceptionListResult> future, int numTotal, boolean allowsIgnore, JFrame parentWindow) {
		super(parentWindow, "Failed file downloads", true);

		setBounds(100, 100, 540, 340);
		setLocationRelativeTo(parentWindow);
		getContentPane().setLayout(new BorderLayout());
		{
			JPanel errorPanel = new JPanel();
			getContentPane().add(errorPanel, BorderLayout.NORTH);
			{
				JLabel lblWarning = new JLabel("One or more errors were encountered while installing the modpack!");
				lblWarning.setIcon(UIManager.getIcon("OptionPane.warningIcon"));
				errorPanel.add(lblWarning);
			}
		}
		JPanel contentPanel = new JPanel();
		contentPanel.setBorder(new EmptyBorder(5, 5, 5, 5));
		getContentPane().add(contentPanel, BorderLayout.CENTER);
		contentPanel.setLayout(new BorderLayout(0, 0));
		{
			JSplitPane splitPane = new JSplitPane();
			splitPane.setResizeWeight(0.3);
			contentPanel.add(splitPane);
			{
				lblExceptionStacktrace = new JTextArea("Select a file");
				lblExceptionStacktrace.setBackground(UIManager.getColor("List.background"));
				lblExceptionStacktrace.setOpaque(true);
				lblExceptionStacktrace.setWrapStyleWord(true);
				lblExceptionStacktrace.setLineWrap(true);
				lblExceptionStacktrace.setEditable(false);
				lblExceptionStacktrace.setFocusable(true);
				lblExceptionStacktrace.setFont(UIManager.getFont("Label.font"));
				lblExceptionStacktrace.setBorder(new EmptyBorder(5, 5, 5, 5));
				JScrollPane scrollPane = new JScrollPane(lblExceptionStacktrace);
				scrollPane.setBorder(new EmptyBorder(0, 0, 0, 0));
				splitPane.setRightComponent(scrollPane);
			}
			{
				JList<String> list = new JList<>();
				list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
				list.setBorder(new EmptyBorder(5, 5, 5, 5));
				ExceptionListModel listModel = new ExceptionListModel(eList);
				list.setModel(listModel);
				list.addListSelectionListener(e -> {
					int i = list.getSelectedIndex();
					if (i > -1) {
						StringWriter sw = new StringWriter();
						listModel.getExceptionAt(i).printStackTrace(new PrintWriter(sw));
						lblExceptionStacktrace.setText(sw.toString());
						// Scroll to the top
						lblExceptionStacktrace.setCaretPosition(0);
					} else {
						lblExceptionStacktrace.setText("Select a file");
					}
				});
				JScrollPane scrollPane = new JScrollPane(list);
				scrollPane.setBorder(new EmptyBorder(0, 0, 0, 0));
				splitPane.setLeftComponent(scrollPane);
			}
		}
		{
			JPanel buttonPane = new JPanel();
			getContentPane().add(buttonPane, BorderLayout.SOUTH);
			buttonPane.setLayout(new BorderLayout(0, 0));
			{
				JPanel rightButtons = new JPanel();
				buttonPane.add(rightButtons, BorderLayout.EAST);
				{
					JButton btnContinue = new JButton("Continue");
					btnContinue.setToolTipText("Attempt to continue installing, excluding the failed downloads");
					btnContinue.addActionListener(e -> {
						future.complete(ExceptionListResult.CONTINUE);
						ExceptionListWindow.this.dispose();
					});
					rightButtons.add(btnContinue);
				}
				{
					JButton btnCancelLaunch = new JButton("Cancel launch");
					btnCancelLaunch.setToolTipText("Stop launching the game");
					btnCancelLaunch.addActionListener(e -> {
						future.complete(ExceptionListResult.CANCEL);
						ExceptionListWindow.this.dispose();
					});
					rightButtons.add(btnCancelLaunch);
				}
				{
					JButton btnIgnoreUpdate = new JButton("Ignore update");
					btnIgnoreUpdate.setEnabled(allowsIgnore);
					btnIgnoreUpdate.setToolTipText("Start the game without attempting to update");
					btnIgnoreUpdate.addActionListener(e -> {
						future.complete(ExceptionListResult.IGNORE);
						ExceptionListWindow.this.dispose();
					});
					rightButtons.add(btnIgnoreUpdate);
					{
						JLabel lblErrored = new JLabel(eList.size() + "/" + numTotal + " errored");
						lblErrored.setHorizontalAlignment(SwingConstants.CENTER);
						buttonPane.add(lblErrored, BorderLayout.CENTER);
					}
					{
						JPanel leftButtons = new JPanel();
						buttonPane.add(leftButtons, BorderLayout.WEST);
						{
							JButton btnReportIssue = new JButton("Report issue");
							boolean supported = Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE);
							btnReportIssue.setEnabled(supported);
							if (supported) {
								btnReportIssue.addActionListener(e -> {
									try {
										Desktop.getDesktop().browse(new URI("https://github.com/comp500/packwiz-installer/issues/new"));
									} catch (IOException | URISyntaxException e1) {
										// lol the button just won't work i guess
									}
								});
							}
							leftButtons.add(btnReportIssue);
						}
					}
				}
			}
		}
		addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				future.complete(ExceptionListResult.CANCEL);
			}

			@Override
			public void windowClosed(WindowEvent e) {
				// Just in case closing didn't get triggered - if something else called dispose() the
				// future will have already completed
				future.complete(ExceptionListResult.CANCEL);
			}
		});
	}

	private static class ExceptionListModel extends AbstractListModel<String> {
		private static final long serialVersionUID = 1L;
		private final List<IExceptionDetails> details;

		ExceptionListModel(List<IExceptionDetails> details) {
			this.details = details;
		}

		public int getSize() {
			return details.size();
		}

		public String getElementAt(int index) {
			return details.get(index).getName();
		}

		Exception getExceptionAt(int index) {
			return details.get(index).getException();
		}
	}

}
