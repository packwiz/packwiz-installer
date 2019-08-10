package link.infra.packwiz.installer.ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.TableModelListener;
import javax.swing.table.TableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class OptionsSelectWindow extends JDialog implements ActionListener {

	private static final long serialVersionUID = 1L;
	private final JTextArea lblOptionDescription;
	private final OptionTableModel tableModel;
	private final CompletableFuture<Boolean> future;

	/**
	 * Create the dialog.
	 */
	OptionsSelectWindow(List<IOptionDetails> optList, CompletableFuture<Boolean> future) {
		tableModel = new OptionTableModel(optList);
		this.future = future;

		setModal(true);
		setTitle("Select optional mods...");
		setBounds(100, 100, 450, 300);
		getContentPane().setLayout(new BorderLayout());
		JPanel contentPanel = new JPanel();
		contentPanel.setBorder(new EmptyBorder(5, 5, 5, 5));
		getContentPane().add(contentPanel, BorderLayout.CENTER);
		contentPanel.setLayout(new BorderLayout(0, 0));
		{
			JSplitPane splitPane = new JSplitPane();
			splitPane.setResizeWeight(0.5);
			contentPanel.add(splitPane);
			{
				JTable table = new JTable();
				table.setShowVerticalLines(false);
				table.setShowHorizontalLines(false);
				table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
				table.setShowGrid(false);
				table.setModel(tableModel);
				table.getColumnModel().getColumn(0).setResizable(false);
				table.getColumnModel().getColumn(0).setPreferredWidth(15);
				table.getColumnModel().getColumn(0).setMaxWidth(15);
				table.getColumnModel().getColumn(1).setResizable(false);
				table.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
					@Override
					public void valueChanged(ListSelectionEvent e) {
						int i = table.getSelectedRow();
						if (i > -1) {
							lblOptionDescription.setText(tableModel.getDescription(i));
						} else {
							lblOptionDescription.setText("Select an option...");
						}
					}
				});
				table.setTableHeader(null);
				JScrollPane scrollPane = new JScrollPane(table);
				scrollPane.getViewport().setBackground(UIManager.getColor("List.background"));
				scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
				scrollPane.setBorder(new EmptyBorder(0, 0, 0, 0));
				splitPane.setLeftComponent(scrollPane);
			}
			{
				lblOptionDescription = new JTextArea("Select an option...");
				lblOptionDescription.setBackground(UIManager.getColor("List.background"));
				lblOptionDescription.setOpaque(true);
				lblOptionDescription.setWrapStyleWord(true);
				lblOptionDescription.setLineWrap(true);
				lblOptionDescription.setOpaque(true);
				lblOptionDescription.setEditable(false);
				lblOptionDescription.setFocusable(false);
				lblOptionDescription.setFont(UIManager.getFont("Label.font"));
				lblOptionDescription.setBorder(new EmptyBorder(10, 10, 10, 10));
				JScrollPane scrollPane = new JScrollPane(lblOptionDescription);
				scrollPane.setBorder(new EmptyBorder(0, 0, 0, 0));
				splitPane.setRightComponent(scrollPane);
			}
		}
		{
			JPanel buttonPane = new JPanel();
			buttonPane.setLayout(new FlowLayout(FlowLayout.RIGHT));
			getContentPane().add(buttonPane, BorderLayout.SOUTH);
			{
				JButton okButton = new JButton("OK");
				okButton.setActionCommand("OK");
				okButton.addActionListener(this);
				buttonPane.add(okButton);
				getRootPane().setDefaultButton(okButton);
			}
			{
				JButton cancelButton = new JButton("Cancel");
				cancelButton.setActionCommand("Cancel");
				cancelButton.addActionListener(this);
				buttonPane.add(cancelButton);
			}
		}
		addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				future.complete(true);
			}

			@Override
			public void windowClosed(WindowEvent e) {
				// Just in case closing didn't get triggered - if something else called dispose() the
				// future will have already completed
				future.complete(true);
			}
		});
	}

	private static class OptionTableModel implements TableModel {
		private List<OptionTempHandler> opts = new ArrayList<>();

		OptionTableModel(List<IOptionDetails> givenOpts) {
			for (IOptionDetails opt : givenOpts) {
				opts.add(new OptionTempHandler(opt));
			}
		}

		@Override
		public int getRowCount() {
			return opts.size();
		}

		@Override
		public int getColumnCount() {
			return 2;
		}

		private final String[] columnNames = {"Enabled", "Mod name"};
		private final Class<?>[] columnTypes = {Boolean.class, String.class};
		private final boolean[] columnEditables = {true, false};

		@Override
		public String getColumnName(int columnIndex) {
			return columnNames[columnIndex];
		}

		@Override
		public Class<?> getColumnClass(int columnIndex) {
			return columnTypes[columnIndex];
		}

		@Override
		public boolean isCellEditable(int rowIndex, int columnIndex) {
			return columnEditables[columnIndex];
		}

		@Override
		public Object getValueAt(int rowIndex, int columnIndex) {
			OptionTempHandler opt = opts.get(rowIndex);
			return columnIndex == 0 ? opt.getOptionValue() : opt.getName();
		}

		@Override
		public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
			if (columnIndex == 0) {
				OptionTempHandler opt = opts.get(rowIndex);
				opt.setOptionValue((boolean) aValue);
			}
		}

		// Noop, the table model doesn't change!
		@Override
		public void addTableModelListener(TableModelListener l) {}

		@Override
		public void removeTableModelListener(TableModelListener l) {}

		String getDescription(int rowIndex) {
			return opts.get(rowIndex).getOptionDescription();
		}

		void finalise() {
			for (OptionTempHandler opt : opts) {
				opt.finalise();
			}
		}

	}

	@Override
	public void actionPerformed(ActionEvent e) {
		if (e.getActionCommand().equals("OK")) {
			tableModel.finalise();
			future.complete(false);
			dispose();
		} else if (e.getActionCommand().equals("Cancel")) {
			future.complete(true);
			dispose();
		}
	}

}
