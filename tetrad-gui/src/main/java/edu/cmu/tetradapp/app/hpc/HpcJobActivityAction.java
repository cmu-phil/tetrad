package edu.cmu.tetradapp.app.hpc;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.HeadlessException;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.Vector;

import javax.swing.AbstractAction;
import javax.swing.Box;
import javax.swing.ButtonGroup;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.table.DefaultTableModel;

import edu.cmu.tetrad.util.JOptionUtils;
import edu.cmu.tetradapp.app.TetradDesktop;
import edu.cmu.tetradapp.util.DesktopController;
import edu.pitt.dbmi.ccd.commons.file.FilePrint;
import edu.pitt.dbmi.ccd.rest.client.dto.data.DataFile;
import edu.pitt.dbmi.ccd.rest.client.service.data.RemoteDataFileService;
import edu.pitt.dbmi.tetrad.db.entity.AlgorithmParamRequest;
import edu.pitt.dbmi.tetrad.db.entity.HpcAccount;
import edu.pitt.dbmi.tetrad.db.entity.HpcJobInfo;
import edu.pitt.dbmi.tetrad.db.entity.HpcJobLog;

/**
 * 
 * Feb 7, 2017 1:53:53 PM
 * 
 * @author Chirayu (Kong) Wongchokprasitti, PhD
 * 
 */
public class HpcJobActivityAction extends AbstractAction {

    private static final long serialVersionUID = -8500391011385619809L;

    private static final String TITLE = "High-Performance Computing Job Activity";

    private final Timer pendingTimer;

    private final Timer submittedTimer;

    private int PENDING_TIME_INTERVAL = 100;

    private int SUBMITTED_TIME_INTERVAL = 10000;

    public HpcJobActivityAction(String actionTitle) {
	super(actionTitle);
	this.pendingTimer = new Timer();
	this.submittedTimer = new Timer();
    }

    @Override
    public void actionPerformed(ActionEvent e) {
	TetradDesktop desktop = (TetradDesktop) DesktopController.getInstance();

	try {
	    JComponent comp = buildHpcJobActivityComponent(desktop);
	    JOptionPane.showMessageDialog(JOptionUtils.centeringComp(), comp,
		    TITLE, JOptionPane.PLAIN_MESSAGE);
	} catch (HeadlessException e1) {
	    // TODO Auto-generated catch block
	    e1.printStackTrace();
	} catch (Exception e1) {
	    // TODO Auto-generated catch block
	    e1.printStackTrace();
	}
    }

    private JComponent buildHpcJobActivityComponent(final TetradDesktop desktop)
	    throws Exception {
	JPanel mainPanel = new JPanel(new BorderLayout());

	final JPanel controllerPane = new JPanel(new BorderLayout());
	mainPanel.add(controllerPane, BorderLayout.NORTH);
	Dimension preferredSize = new Dimension(100, 100);
	controllerPane.setPreferredSize(preferredSize);
	buildController(controllerPane, desktop);

	final JPanel contentPanel = new JPanel(new BorderLayout());
	mainPanel.add(contentPanel, BorderLayout.CENTER);
	buildActivityContent(contentPanel, desktop);

	int minWidth = 800;
	int minHeight = 600;
	int screenWidth = Toolkit.getDefaultToolkit().getScreenSize().width;
	int screenHeight = Toolkit.getDefaultToolkit().getScreenSize().height;
	int frameWidth = screenWidth * 5 / 6;
	int frameHeight = screenHeight * 3 / 4;
	final int paneWidth = minWidth > frameWidth ? minWidth : frameWidth;
	final int paneHeight = minHeight > frameHeight ? minHeight
		: frameHeight;

	mainPanel.setPreferredSize(new Dimension(paneWidth, paneHeight));

	return mainPanel;
    }

    private void buildController(final JPanel controllerPane,
	    final TetradDesktop desktop) {
	// Content
	Box contentBox = Box.createVerticalBox();

	JPanel hpcPanel = new JPanel(new BorderLayout());

	JLabel hpcAccountLabel = new JLabel("HPC Account: ", JLabel.TRAILING);
	hpcAccountLabel.setPreferredSize(new Dimension(100, 5));
	hpcPanel.add(hpcAccountLabel, BorderLayout.WEST);

	Box hpcAccountCheckBox = Box.createHorizontalBox();
	final HpcAccountManager hpcAccountManager = desktop
		.getHpcAccountManager();
	List<HpcAccount> hpcAccounts = hpcAccountManager.getHpcAccounts();
	for (HpcAccount hpcAccount : hpcAccounts) {
	    final JCheckBox hpcCheckBox = new JCheckBox(
		    hpcAccount.getConnectionName(), true);
	    
	    hpcAccountCheckBox.add(hpcCheckBox);

	}
	hpcPanel.add(hpcAccountCheckBox, BorderLayout.CENTER);

	contentBox.add(hpcPanel);

	controllerPane.add(contentBox, BorderLayout.CENTER);
    }

    private void buildActivityContent(final JPanel activityPanel,
	    final TetradDesktop desktop) throws Exception {

	final JTable jobsTable = new JTable();
	jobsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

	final JScrollPane scrollTablePane = new JScrollPane(jobsTable);

	JTabbedPane tabbedPane = new JTabbedPane();

	JPanel activeJobsPanel = new JPanel(new BorderLayout());
	activeJobsPanel.add(scrollTablePane, BorderLayout.CENTER);
	tabbedPane.add("Active Jobs", activeJobsPanel);

	final KillHpcJobAction killJobAction = new KillHpcJobAction();

	JPanel finishedJobsPanel = new JPanel(new BorderLayout());
	// finishedJobsPanel.add(scrollTablePane, BorderLayout.CENTER);
	tabbedPane.add("Finished Jobs", finishedJobsPanel);

	final DeleteHpcJobInfoAction deleteJobAction = new DeleteHpcJobInfoAction();

	class HpcJobInfoTableModel extends DefaultTableModel {

	    private static final long serialVersionUID = 1L;

	    private final int buttonColumn;
	    
	    public HpcJobInfoTableModel(
		    final Vector<Vector<String>> activeRowData,
		    final Vector<String> activeColumnNames,
		    final int buttonColumn) {
		super(activeRowData, activeColumnNames);
		this.buttonColumn = buttonColumn;
	    }

	    public boolean isCellEditable(int row, int column) {
		if (column == buttonColumn)
		    return true;
		return false;
	    }

	};
	
	ChangeListener changeListener = new ChangeListener() {

	    @Override
	    public void stateChanged(ChangeEvent e) {
		JTabbedPane sourceTabbedPane = (JTabbedPane) e.getSource();
		int index = sourceTabbedPane.getSelectedIndex();
		if (index == 0) {
		    finishedJobsPanel.remove(scrollTablePane);
		    activeJobsPanel.add(scrollTablePane, BorderLayout.CENTER);
		    try {
			final Vector<String> activeColumnNames = genActiveJobColumnNames();
			final Vector<Vector<String>> activeRowData = getActiveRowData(desktop);

			final int KILL_BUTTON_COLUMN = 10;
			final DefaultTableModel activeJobTableModel = new HpcJobInfoTableModel(
				activeRowData, activeColumnNames, KILL_BUTTON_COLUMN);

			jobsTable.setModel(activeJobTableModel);

			if (activeRowData.size() > 0) {
			    new ButtonColumn(jobsTable, killJobAction, KILL_BUTTON_COLUMN);
			}

			jobsTable.getColumnModel().getColumn(0)
				.setPreferredWidth(20);
			jobsTable.getColumnModel().getColumn(1)
				.setPreferredWidth(30);
			jobsTable.getColumnModel().getColumn(3)
				.setPreferredWidth(20);
			jobsTable.getColumnModel().getColumn(4)
				.setPreferredWidth(40);

			jobsTable.updateUI();
		    } catch (Exception e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		    }
		} else if (index == 1) {
		    activeJobsPanel.remove(scrollTablePane);
		    finishedJobsPanel.add(scrollTablePane, BorderLayout.CENTER);
		    try {
			final Vector<String> finishedColumnNames = genFinishedJobColumnNames();
			final Vector<Vector<String>> finishedRowData = getFinishedRowData(desktop);

			final int DELETE_BUTTON_COLUMN = 10;

			final DefaultTableModel finishedJobTableModel = new HpcJobInfoTableModel(
				finishedRowData, finishedColumnNames, DELETE_BUTTON_COLUMN);

			jobsTable.setModel(finishedJobTableModel);

			if (finishedRowData.size() > 0) {
			    new ButtonColumn(jobsTable, deleteJobAction, DELETE_BUTTON_COLUMN);
			}

			jobsTable.getColumnModel().getColumn(0)
				.setPreferredWidth(20);
			jobsTable.getColumnModel().getColumn(1)
				.setPreferredWidth(30);
			jobsTable.getColumnModel().getColumn(3)
				.setPreferredWidth(20);
			jobsTable.getColumnModel().getColumn(4)
				.setPreferredWidth(40);
			jobsTable.getColumnModel().getColumn(6)
				.setPreferredWidth(35);

			jobsTable.updateUI();
		    } catch (Exception e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		    }

		}
	    }

	};
	tabbedPane.addChangeListener(changeListener);

	activityPanel.add(tabbedPane, BorderLayout.CENTER);

	tabbedPane.setSelectedIndex(-1);
	tabbedPane.setSelectedIndex(0);

	// Start active job updater

    }

    private Vector<String> genActiveJobColumnNames() {
	final Vector<String> columnNames = new Vector<>();

	columnNames.addElement("Job ID");
	columnNames.addElement("Status");
	columnNames.addElement("Added");
	columnNames.addElement("HPC");
	columnNames.addElement("Algorithm");
	columnNames.addElement("Data Upload");
	columnNames.addElement("Knowledge Upload");
	columnNames.addElement("Submitted");
	columnNames.addElement("HPC Job ID");
	columnNames.addElement("lastUpdated");
	columnNames.addElement("");

	return columnNames;
    }

    private Vector<Vector<String>> getActiveRowData(final TetradDesktop desktop)
	    throws Exception {
	return getActiveRowData(desktop, null);
    }

    private Vector<Vector<String>> getActiveRowData(
	    final TetradDesktop desktop,
	    final List<HpcAccount> exclusiveHpcAccounts) throws Exception {
	final Vector<Vector<String>> activeRowData = new Vector<>();
	final HpcAccountManager hpcAccountManager = desktop
		.getHpcAccountManager();
	final HpcJobManager hpcJobManager = desktop.getHpcJobManager();
	Map<Long, HpcJobInfo> activeHpcJobInfoMap = null;

	// Pending
	Map<HpcAccount, Set<HpcJobInfo>> pendingHpcJobInfoMap = hpcJobManager
		.getPendingHpcJobInfoMap();
	for (HpcAccount hpcAccount : pendingHpcJobInfoMap.keySet()) {

	    if (exclusiveHpcAccounts != null
		    && exclusiveHpcAccounts.contains(hpcAccount)) {
		continue;
	    }

	    Set<HpcJobInfo> pendingHpcJobSet = pendingHpcJobInfoMap
		    .get(hpcAccount);
	    for (HpcJobInfo hpcJobInfo : pendingHpcJobSet) {
		if (activeHpcJobInfoMap == null) {
		    activeHpcJobInfoMap = new HashMap<>();
		}
		activeHpcJobInfoMap.put(hpcJobInfo.getId(), hpcJobInfo);
	    }
	}

	// Submitted
	Map<HpcAccount, Set<HpcJobInfo>> submittedHpcJobInfoMap = hpcJobManager
		.getSubmittedHpcJobInfoMap();
	for (HpcAccount hpcAccount : submittedHpcJobInfoMap.keySet()) {

	    if (exclusiveHpcAccounts != null
		    && exclusiveHpcAccounts.contains(hpcAccount)) {
		continue;
	    }

	    Set<HpcJobInfo> submittedHpcJobSet = submittedHpcJobInfoMap
		    .get(hpcAccount);
	    for (HpcJobInfo hpcJobInfo : submittedHpcJobSet) {
		if (activeHpcJobInfoMap == null) {
		    activeHpcJobInfoMap = new HashMap<>();
		}
		activeHpcJobInfoMap.put(hpcJobInfo.getId(), hpcJobInfo);
	    }
	}

	if (activeHpcJobInfoMap != null) {

	    List<Long> activeJobIds = new ArrayList<>(
		    activeHpcJobInfoMap.keySet());

	    Collections.sort(activeJobIds);
	    Collections.reverse(activeJobIds);

	    for (Long jobId : activeJobIds) {

		final HpcJobInfo hpcJobInfo = activeHpcJobInfoMap.get(jobId);

		Vector<String> rowData = new Vector<>();

		HpcJobLog hpcJobLog = hpcJobManager.getHpcJobLog(hpcJobInfo);

		// Local job id
		rowData.add(hpcJobInfo.getId().toString());

		int status = hpcJobInfo.getStatus();

		switch (status) {
		case -1:
		    rowData.add("Pending");
		    break;
		case 0:
		    rowData.add("Submitted");
		    break;
		case 1:
		    rowData.add("Running");
		    break;
		case 2:
		    rowData.add("Kill Request");
		    break;
		}

		// Locally added time
		rowData.add(FilePrint.fileTimestamp(hpcJobLog.getAddedTime()
			.getTime()));

		// HPC node name
		HpcAccount hpcAccount = hpcJobInfo.getHpcAccount();
		rowData.add(hpcAccount.getConnectionName());

		// Algorithm
		rowData.add(hpcJobInfo.getAlgorithmName());

		// Dataset uploading progress
		HpcAccountService hpcAccountService = hpcJobManager
			.getHpcAccountService(hpcAccount);

		AlgorithmParamRequest algorParamReq = hpcJobInfo
			.getAlgorithmParamRequest();
		String datasetPath = algorParamReq.getDatasetPath();

		String md5 = algorParamReq.getDatasetMd5();
		// Check if this dataset already exists with this md5 hash
		RemoteDataFileService remoteDataService = hpcAccountService
			.getRemoteDataService();
		DataFile dataFile = HpcAccountUtils.getRemoteDataFile(
			hpcAccountManager, remoteDataService, hpcAccount, md5);

		if (dataFile == null) {
		    int progress = hpcJobManager
			    .getUploadFileProgress(datasetPath);
		    rowData.add("" + progress + "%");
		} else {
		    rowData.add("Done");
		}

		// Prior Knowledge uploading progress
		String priorKnowledgePath = algorParamReq
			.getPriorKnowledgePath();
		if (priorKnowledgePath != null) {
		    md5 = algorParamReq.getPriorKnowledgeMd5();
		    dataFile = HpcAccountUtils.getRemoteDataFile(
			    hpcAccountManager, remoteDataService, hpcAccount,
			    md5);
		    if (dataFile == null) {
			int progress = hpcJobManager
				.getUploadFileProgress(priorKnowledgePath);
			rowData.add("" + progress + "%");
		    } else {
			rowData.add("Done");
		    }
		} else {
		    rowData.add("Skipped");
		}

		if (status > -1) {
		    // Submitted time
		    rowData.add(FilePrint.fileTimestamp(hpcJobInfo
			    .getSubmittedTime().getTime()));

		    // HPC job id
		    rowData.add("" + hpcJobInfo.getPid());

		    // Result Name
		    // rowData.add(hpcJobInfo.getResultFileName());

		} else {
		    rowData.add("");
		    rowData.add("");
		    // rowData.add("");
		}

		// Last update time
		rowData.add(FilePrint.fileTimestamp(hpcJobLog
			.getLastUpdatedTime().getTime()));

		// Cancel job
		rowData.add("Cancel");

		activeRowData.add(rowData);
	    }
	}

	return activeRowData;
    }

    private Vector<String> genFinishedJobColumnNames() {
	final Vector<String> columnNames = new Vector<>();

	columnNames.addElement("Job ID");
	columnNames.addElement("Status");
	columnNames.addElement("Added");
	columnNames.addElement("HPC");
	columnNames.addElement("Algorithm");
	columnNames.addElement("Submitted");
	columnNames.addElement("HPC Job ID");
	columnNames.addElement("Result Name");
	columnNames.addElement("Finished");
	columnNames.addElement("Canceled");
	columnNames.addElement("lastUpdated");
	columnNames.addElement("");

	return columnNames;
    }

    private Vector<Vector<String>> getFinishedRowData(
	    final TetradDesktop desktop) throws Exception {
	return getFinishedRowData(desktop, null);
    }

    private Vector<Vector<String>> getFinishedRowData(
	    final TetradDesktop desktop,
	    final List<HpcAccount> exclusiveHpcAccounts) throws Exception {
	final Vector<Vector<String>> finishedRowData = new Vector<>();
	HpcJobManager hpcJobManager = desktop.getHpcJobManager();
	Map<Long, HpcJobInfo> finishedHpcJobIdMap = null;

	// Finished jobs
	Map<HpcAccount, Set<HpcJobInfo>> finishedHpcJobInfoMap = hpcJobManager
		.getFinishedHpcJobInfoMap();
	for (HpcAccount hpcAccount : finishedHpcJobInfoMap.keySet()) {

	    if (exclusiveHpcAccounts != null
		    && exclusiveHpcAccounts.contains(hpcAccount)) {
		continue;
	    }

	    Set<HpcJobInfo> finishedHpcJobSet = finishedHpcJobInfoMap
		    .get(hpcAccount);
	    for (HpcJobInfo hpcJobInfo : finishedHpcJobSet) {
		if (finishedHpcJobIdMap == null) {
		    finishedHpcJobIdMap = new HashMap<>();
		}
		finishedHpcJobIdMap.put(hpcJobInfo.getId(), hpcJobInfo);
	    }
	}

	if (finishedHpcJobIdMap != null) {

	    List<Long> finishedJobIds = new ArrayList<>(
		    finishedHpcJobIdMap.keySet());

	    Collections.sort(finishedJobIds);
	    Collections.reverse(finishedJobIds);

	    for (Long jobId : finishedJobIds) {
		final HpcJobInfo hpcJobInfo = finishedHpcJobIdMap.get(jobId);

		Vector<String> rowData = new Vector<>();

		HpcJobLog hpcJobLog = hpcJobManager.getHpcJobLog(hpcJobInfo);

		// Local job id
		rowData.add(hpcJobInfo.getId().toString());

		int status = hpcJobInfo.getStatus();

		switch (status) {
		case 3:
		    rowData.add("Finished");
		    break;
		case 4:
		    rowData.add("Canceled");
		    break;
		case 5:
		    rowData.add("Finished");
		    break;
		case 6:
		    rowData.add("Error");
		    break;
		}

		// Locally added time
		rowData.add(FilePrint.fileTimestamp(hpcJobLog.getAddedTime()
			.getTime()));

		// HPC node name
		HpcAccount hpcAccount = hpcJobInfo.getHpcAccount();
		rowData.add(hpcAccount.getConnectionName());

		// Algorithm
		rowData.add(hpcJobInfo.getAlgorithmName());

		// Submitted time
		rowData.add(FilePrint.fileTimestamp(hpcJobInfo
			.getSubmittedTime().getTime()));

		// HPC job id
		rowData.add("" + hpcJobInfo.getPid());

		// Result Name
		switch (status) {
		case 3:
		    rowData.add(hpcJobInfo.getResultFileName());
		    break;
		case 4:
		    rowData.add("");
		    break;
		case 5:
		    rowData.add(hpcJobInfo.getResultFileName());
		    break;
		case 6:
		    rowData.add(hpcJobInfo.getErrorResultFileName());
		    break;
		}

		// Finished time
		if (status != 4) {
		    rowData.add(FilePrint.fileTimestamp(hpcJobLog
			    .getEndedTime().getTime()));
		} else {
		    rowData.add("");
		}

		// Canceled time
		if (status == 4) {
		    rowData.add(FilePrint.fileTimestamp(hpcJobLog
			    .getCanceledTime().getTime()));
		} else {
		    rowData.add("");
		}

		// Last update time
		rowData.add(FilePrint.fileTimestamp(hpcJobLog
			.getLastUpdatedTime().getTime()));

		// Delete job from db
		rowData.add("Delete");

		finishedRowData.add(rowData);
	    }

	}

	return finishedRowData;
    }

}
