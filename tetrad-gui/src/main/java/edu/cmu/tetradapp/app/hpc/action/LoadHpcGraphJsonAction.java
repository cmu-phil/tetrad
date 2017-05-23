package edu.cmu.tetradapp.app.hpc.action;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.Vector;

import javax.swing.AbstractAction;
import javax.swing.DefaultListModel;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumnModel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.util.JOptionUtils;
import edu.cmu.tetrad.util.JsonUtils;
import edu.cmu.tetradapp.app.TetradDesktop;
import edu.cmu.tetradapp.app.hpc.editor.LoadHpcGraphJsonTableModel;
import edu.cmu.tetradapp.app.hpc.manager.HpcAccountManager;
import edu.cmu.tetradapp.app.hpc.manager.HpcAccountService;
import edu.cmu.tetradapp.app.hpc.manager.HpcJobManager;
import edu.cmu.tetradapp.app.hpc.util.HpcAccountUtils;
import edu.cmu.tetradapp.editor.GraphEditable;
import edu.cmu.tetradapp.util.DesktopController;
import edu.pitt.dbmi.ccd.commons.file.FilePrint;
import edu.pitt.dbmi.ccd.rest.client.dto.algo.ResultFile;
import edu.pitt.dbmi.ccd.rest.client.service.result.ResultService;
import edu.pitt.dbmi.tetrad.db.entity.HpcAccount;

/**
 * 
 * Dec 5, 2016 12:16:22 PM
 * 
 * @author Chirayu (Kong) Wongchokprasitti, PhD
 * 
 */
public class LoadHpcGraphJsonAction extends AbstractAction {

	private static final long serialVersionUID = 3640705055173728331L;
	
	private final Logger LOGGER = LoggerFactory.getLogger(LoadHpcGraphJsonAction.class);

	/**
	 * The component whose image is to be saved.
	 */
	private GraphEditable graphEditable;

	private String jsonFileName = null;

	private HpcAccount hpcAccount = null;

	public LoadHpcGraphJsonAction(GraphEditable graphEditable, String title) {
		super(title);

		if (graphEditable == null) {
			throw new NullPointerException("Component must not be null.");
		}

		this.graphEditable = graphEditable;
	}

	@Override
	public void actionPerformed(ActionEvent e) {
		TetradDesktop desktop = (TetradDesktop) DesktopController.getInstance();
		final HpcAccountManager hpcAccountManager = desktop.getHpcAccountManager();
		final HpcJobManager hpcJobManager = desktop.getHpcJobManager();

		JComponent comp = buildHpcJsonChooserComponent(desktop);
		int option = JOptionPane.showConfirmDialog(JOptionUtils.centeringComp(), comp,
				"High-Performance Computing Account Json Results Chooser", JOptionPane.OK_CANCEL_OPTION,
				JOptionPane.PLAIN_MESSAGE);

		if (option == JOptionPane.OK_OPTION && jsonFileName != null && hpcAccount != null) {

			try {
				HpcAccountService hpcAccountService = hpcJobManager.getHpcAccountService(hpcAccount);

				ResultService resultService = hpcAccountService.getResultService();

				String json = resultService.downloadAlgorithmResultFile(jsonFileName,
						HpcAccountUtils.getJsonWebToken(hpcAccountManager, hpcAccount));

				Graph graph = JsonUtils.parseJSONObjectToTetradGraph(json);
				GraphUtils.circleLayout(graph, 300, 300, 150);
				graphEditable.setGraph(graph);
				graphEditable.setName(jsonFileName);
			} catch (Exception e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
		} else {
			LOGGER.debug("Option: OK " + (option == JOptionPane.OK_OPTION));
			LOGGER.debug("Option: jsonFileName " + (jsonFileName != null));
			LOGGER.debug("Option: computingAccount " + (hpcAccount != null));
		}
	}

	private JComponent buildHpcJsonChooserComponent(final TetradDesktop desktop) {
		final HpcAccountManager hpcAccountManager = desktop.getHpcAccountManager();
		final HpcJobManager hpcJobManager = desktop.getHpcJobManager();
		// Get ComputingAccount from DB
		final DefaultListModel<HpcAccount> listModel = new DefaultListModel<HpcAccount>();

		for (HpcAccount account : hpcAccountManager.getHpcAccounts()) {
			listModel.addElement(account);
		}

		// JSplitPane
		final JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);

		// Left pane -> JList (parent pane)
		JPanel leftPanel = new JPanel(new BorderLayout());

		// Right pane -> ComputingAccountResultList
		final JPanel jsonResultListPanel = new JPanel(new BorderLayout());

		int minWidth = 800;
		int minHeight = 600;
		int screenWidth = Toolkit.getDefaultToolkit().getScreenSize().width;
		int screenHeight = Toolkit.getDefaultToolkit().getScreenSize().height;
		int frameWidth = screenWidth * 3 / 4;
		int frameHeight = screenHeight * 3 / 4;
		final int paneWidth = minWidth > frameWidth ? minWidth : frameWidth;
		final int paneHeight = minHeight > frameHeight ? minHeight : frameHeight;

		// JTable
		final Vector<String> columnNames = new Vector<>();
		columnNames.addElement("Name");
		columnNames.addElement("Created");
		columnNames.addElement("Last Modified");
		columnNames.addElement("Size");

		Vector<Vector<String>> rowData = new Vector<>();

		final DefaultTableModel tableModel = new LoadHpcGraphJsonTableModel(rowData, columnNames);
		final JTable jsonResultTable = new JTable(tableModel);
		jsonResultTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

		// Resize table's column width
		jsonResultTable.getColumnModel().getColumn(0).setPreferredWidth(paneWidth * 2 / 5);
		jsonResultTable.getColumnModel().getColumn(1).setPreferredWidth(paneWidth * 2 / 15);
		jsonResultTable.getColumnModel().getColumn(2).setPreferredWidth(paneWidth * 2 / 15);
		jsonResultTable.getColumnModel().getColumn(3).setPreferredWidth(paneWidth * 2 / 15);

		ListSelectionModel selectionModel = jsonResultTable.getSelectionModel();
		selectionModel.addListSelectionListener(new ListSelectionListener() {

			@Override
			public void valueChanged(ListSelectionEvent e) {
				int row = jsonResultTable.getSelectedRow();
				if (row >= 0) {
					DefaultTableModel model = (DefaultTableModel) jsonResultTable.getModel();
					jsonFileName = (String) model.getValueAt(row, 0);
				}
			}
		});

		final JScrollPane scrollTablePane = new JScrollPane(jsonResultTable);

		jsonResultListPanel.add(scrollTablePane, BorderLayout.CENTER);

		splitPane.setLeftComponent(leftPanel);
		splitPane.setRightComponent(jsonResultListPanel);

		// Center Panel
		final JList<HpcAccount> accountList = new JList<>(listModel);
		accountList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		accountList.setLayoutOrientation(JList.VERTICAL);
		accountList.setSelectedIndex(-1);
		accountList.addListSelectionListener(new ListSelectionListener() {

			@Override
			public void valueChanged(ListSelectionEvent e) {
				if (e.getValueIsAdjusting())
					return;
				int selectedIndex = ((JList<?>) e.getSource()).getSelectedIndex();
				// Show or remove the json list
				if (selectedIndex > -1) {
					jsonFileName = null;
					hpcAccount = listModel.get(selectedIndex);

					TableColumnModel columnModel = jsonResultTable.getColumnModel();
					List<Integer> columnWidthList = new ArrayList<>();
					for (int i = 0; i < columnModel.getColumnCount(); i++) {
						int width = columnModel.getColumn(i).getPreferredWidth();
						columnWidthList.add(width);
					}

					jsonResultTable.clearSelection();

					try {
						HpcAccountService hpcAccountService = hpcJobManager.getHpcAccountService(hpcAccount);

						ResultService resultService = hpcAccountService.getResultService();

						Set<ResultFile> results = resultService.listAlgorithmResultFiles(
								HpcAccountUtils.getJsonWebToken(hpcAccountManager, hpcAccount));

						Vector<Vector<String>> jsonFiles = new Vector<>();

						for (ResultFile resultFile : results) {
							if (resultFile.getName().endsWith(".json")) {
								Vector<String> rowData = new Vector<>();
								rowData.addElement(resultFile.getName());
								rowData.addElement(FilePrint.fileTimestamp(resultFile.getCreationTime().getTime()));
								rowData.addElement(FilePrint.fileTimestamp(resultFile.getLastModifiedTime().getTime()));
								rowData.addElement(FilePrint.humanReadableSize(resultFile.getFileSize(), false));

								jsonFiles.add(rowData);
							}
						}

						tableModel.setDataVector(jsonFiles, columnNames);

					} catch (Exception e1) {
						// TODO Auto-generated catch block
						e1.printStackTrace();
					}

					// Resize table's column width
					for (int i = 0; i < columnModel.getColumnCount(); i++) {
						jsonResultTable.getColumnModel().getColumn(i)
								.setPreferredWidth(columnWidthList.get(i).intValue());
					}

				}
			}
		});

		// Left Panel
		JScrollPane accountListScroller = new JScrollPane(accountList);
		leftPanel.add(accountListScroller, BorderLayout.CENTER);

		splitPane.setDividerLocation(paneWidth / 5);
		accountListScroller.setPreferredSize(new Dimension(paneWidth / 5, paneHeight));
		jsonResultListPanel.setPreferredSize(new Dimension(paneWidth * 4 / 5, paneHeight));

		return splitPane;
	}

}
