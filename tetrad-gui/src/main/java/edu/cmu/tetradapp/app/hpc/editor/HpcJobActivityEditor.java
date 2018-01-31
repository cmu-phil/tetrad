package edu.cmu.tetradapp.app.hpc.editor;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.Vector;

import javax.swing.Box;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableModel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.cmu.tetradapp.app.TetradDesktop;
import edu.cmu.tetradapp.app.hpc.action.DeleteHpcJobInfoAction;
import edu.cmu.tetradapp.app.hpc.action.KillHpcJobAction;
import edu.cmu.tetradapp.app.hpc.manager.HpcAccountManager;
import edu.cmu.tetradapp.app.hpc.manager.HpcJobManager;
import edu.cmu.tetradapp.app.hpc.task.PendingHpcJobUpdaterTask;
import edu.cmu.tetradapp.app.hpc.task.SubmittedHpcJobUpdaterTask;
import edu.cmu.tetradapp.util.DesktopController;
import edu.cmu.tetradapp.util.FinalizingEditor;
import edu.pitt.dbmi.ccd.commons.file.FilePrint;
import edu.pitt.dbmi.tetrad.db.entity.AlgorithmParamRequest;
import edu.pitt.dbmi.tetrad.db.entity.HpcAccount;
import edu.pitt.dbmi.tetrad.db.entity.HpcJobInfo;
import edu.pitt.dbmi.tetrad.db.entity.HpcJobLog;

/**
 * 
 * Feb 11, 2017 4:59:21 PM
 * 
 * @author Chirayu (Kong) Wongchokprasitti, PhD
 * 
 */
public class HpcJobActivityEditor extends JPanel implements FinalizingEditor {

	private static final long serialVersionUID = -6178713484456753741L;
	
	private final Logger LOGGER = LoggerFactory.getLogger(HpcJobActivityEditor.class);

	private final List<HpcAccount> checkedHpcAccountList;

	private final Set<HpcJobInfo> pendingDisplayHpcJobInfoSet;

	private final Set<HpcJobInfo> submittedDisplayHpcJobInfoSet;

	private final Timer pendingTimer;

	private final Timer submittedTimer;

	private int PENDING_TIME_INTERVAL = 100;

	private int SUBMITTED_TIME_INTERVAL = 1000;

	private JTable jobsTable;

	private JTabbedPane tabbedPane;

	private PendingHpcJobUpdaterTask pendingJobUpdater;

	private SubmittedHpcJobUpdaterTask submittedJobUpdater;

	public final static int ID_COLUMN = 0;
	public final static int STATUS_COLUMN = 1;
	public final static int DATA_UPLOAD_COLUMN = 5;
	public final static int KNOWLEDGE_UPLOAD_COLUMN = 6;
	public final static int ACTIVE_SUBMITTED_COLUMN = 7;
	public final static int ACTIVE_HPC_JOB_ID_COLUMN = 8;
	public final static int ACTIVE_LAST_UPDATED_COLUMN = 9;
	public final static int KILL_BUTTON_COLUMN = 10;

	public final static int DELETE_BUTTON_COLUMN = 11;

	public HpcJobActivityEditor() throws Exception {
		checkedHpcAccountList = new ArrayList<>();
		pendingDisplayHpcJobInfoSet = new HashSet<>();
		submittedDisplayHpcJobInfoSet = new HashSet<>();
		this.pendingTimer = new Timer();
		this.submittedTimer = new Timer();
		TetradDesktop desktop = (TetradDesktop) DesktopController.getInstance();
		buildHpcJobActivityComponent(desktop);
	}

	private void buildHpcJobActivityComponent(final TetradDesktop desktop) throws Exception {
		setLayout(new BorderLayout());

		final JPanel controllerPane = new JPanel(new BorderLayout());
		add(controllerPane, BorderLayout.NORTH);
		Dimension preferredSize = new Dimension(100, 100);
		controllerPane.setPreferredSize(preferredSize);
		buildController(controllerPane, desktop);

		final JPanel contentPanel = new JPanel(new BorderLayout());
		add(contentPanel, BorderLayout.CENTER);
		buildActivityContent(contentPanel, desktop);

		int minWidth = 800;
		int minHeight = 600;
		int screenWidth = Toolkit.getDefaultToolkit().getScreenSize().width;
		int screenHeight = Toolkit.getDefaultToolkit().getScreenSize().height;
		int frameWidth = screenWidth * 5 / 6;
		int frameHeight = screenHeight * 3 / 4;
		final int paneWidth = minWidth > frameWidth ? minWidth : frameWidth;
		final int paneHeight = minHeight > frameHeight ? minHeight : frameHeight;

		setPreferredSize(new Dimension(paneWidth, paneHeight));
	}

	private class HpcAccountSelectionAction implements ActionListener {

		private final List<HpcAccount> hpcAccounts;

		public HpcAccountSelectionAction(final List<HpcAccount> hpcAccounts) {
			this.hpcAccounts = hpcAccounts;
		}

		@Override
		public void actionPerformed(ActionEvent e) {
			final JCheckBox checkBox = (JCheckBox) e.getSource();
			for (HpcAccount hpcAccount : hpcAccounts) {
				if (checkBox.getText().equals(hpcAccount.getConnectionName())) {
					if (checkBox.isSelected() && !checkedHpcAccountList.contains(hpcAccount)) {
						checkedHpcAccountList.add(hpcAccount);
					} else if (!checkBox.isSelected() && checkedHpcAccountList.contains(hpcAccount)) {
						checkedHpcAccountList.remove(hpcAccount);
					}
				}
			}
			int index = tabbedPane.getSelectedIndex();
			tabbedPane.setSelectedIndex(-1);
			tabbedPane.setSelectedIndex(index);
		}

	}

	private void buildController(final JPanel controllerPane, final TetradDesktop desktop) {
		// Content
		Box contentBox = Box.createVerticalBox();

		JPanel hpcPanel = new JPanel(new BorderLayout());

		JLabel hpcAccountLabel = new JLabel("HPC Account: ", JLabel.TRAILING);
		hpcAccountLabel.setPreferredSize(new Dimension(100, 5));
		hpcPanel.add(hpcAccountLabel, BorderLayout.WEST);

		Box hpcAccountCheckBox = Box.createHorizontalBox();
		final HpcAccountManager hpcAccountManager = desktop.getHpcAccountManager();
		List<HpcAccount> hpcAccounts = hpcAccountManager.getHpcAccounts();

		HpcAccountSelectionAction hpcAccountSelectionAction = new HpcAccountSelectionAction(hpcAccounts);

		for (HpcAccount hpcAccount : hpcAccounts) {
			checkedHpcAccountList.add(hpcAccount);
			final JCheckBox hpcCheckBox = new JCheckBox(hpcAccount.getConnectionName(), true);
			hpcCheckBox.addActionListener(hpcAccountSelectionAction);
			hpcAccountCheckBox.add(hpcCheckBox);

		}
		hpcPanel.add(hpcAccountCheckBox, BorderLayout.CENTER);

		contentBox.add(hpcPanel);

		controllerPane.add(contentBox, BorderLayout.CENTER);
	}

	private void buildActivityContent(final JPanel activityPanel, final TetradDesktop desktop) throws Exception {

		jobsTable = new JTable();
		jobsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

		final JScrollPane scrollTablePane = new JScrollPane(jobsTable);

		tabbedPane = new JTabbedPane();

		final JPanel activeJobsPanel = new JPanel(new BorderLayout());
		activeJobsPanel.add(scrollTablePane, BorderLayout.CENTER);
		tabbedPane.add("Active Jobs", activeJobsPanel);

		final KillHpcJobAction killJobAction = new KillHpcJobAction(this);

		final JPanel finishedJobsPanel = new JPanel(new BorderLayout());

		tabbedPane.add("Finished Jobs", finishedJobsPanel);

		final DeleteHpcJobInfoAction deleteJobAction = new DeleteHpcJobInfoAction(this);

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
						final Vector<Vector<String>> activeRowData = getActiveRowData(desktop, checkedHpcAccountList);

						final DefaultTableModel activeJobTableModel = new HpcJobInfoTableModel(activeRowData,
								activeColumnNames, KILL_BUTTON_COLUMN);

						jobsTable.setModel(activeJobTableModel);

						if (activeRowData.size() > 0) {
							new ButtonColumn(jobsTable, killJobAction, KILL_BUTTON_COLUMN);
						}

						adjustActiveJobsWidthColumns(jobsTable);
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
						final Vector<Vector<String>> finishedRowData = getFinishedRowData(desktop,
								checkedHpcAccountList);

						final DefaultTableModel finishedJobTableModel = new HpcJobInfoTableModel(finishedRowData,
								finishedColumnNames, DELETE_BUTTON_COLUMN);

						jobsTable.setModel(finishedJobTableModel);

						if (finishedRowData.size() > 0) {
							new ButtonColumn(jobsTable, deleteJobAction, DELETE_BUTTON_COLUMN);
						}
						adjustFinishedJobsWidthColumns(jobsTable);
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

		final HpcJobManager hpcJobManager = desktop.getHpcJobManager();

		// Start active job updater
		pendingJobUpdater = new PendingHpcJobUpdaterTask(hpcJobManager, this);

		submittedJobUpdater = new SubmittedHpcJobUpdaterTask(hpcJobManager, this);

		tabbedPane.setSelectedIndex(-1);
		tabbedPane.setSelectedIndex(0);

		startUpdaters();
	}

	private void startUpdaters() {
		pendingTimer.schedule(pendingJobUpdater, 0, PENDING_TIME_INTERVAL);
		submittedTimer.schedule(submittedJobUpdater, 0, SUBMITTED_TIME_INTERVAL);
	}

	private void stopUpdaters() {
		pendingTimer.cancel();
		submittedTimer.cancel();
	}

	private void adjustActiveJobsWidthColumns(final JTable jobsTable) {
		jobsTable.getColumnModel().getColumn(0).setPreferredWidth(20);
		jobsTable.getColumnModel().getColumn(1).setPreferredWidth(30);
		jobsTable.getColumnModel().getColumn(3).setPreferredWidth(20);
		jobsTable.getColumnModel().getColumn(4).setPreferredWidth(40);
		jobsTable.getColumnModel().getColumn(8).setPreferredWidth(35);
	}

	private void adjustFinishedJobsWidthColumns(final JTable jobsTable) {
		jobsTable.getColumnModel().getColumn(0).setPreferredWidth(20);
		jobsTable.getColumnModel().getColumn(1).setPreferredWidth(30);
		jobsTable.getColumnModel().getColumn(3).setPreferredWidth(20);
		jobsTable.getColumnModel().getColumn(4).setPreferredWidth(40);
		jobsTable.getColumnModel().getColumn(6).setPreferredWidth(35);
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

	private Vector<Vector<String>> getActiveRowData(final TetradDesktop desktop,
			final List<HpcAccount> exclusiveHpcAccounts) throws Exception {
		final Vector<Vector<String>> activeRowData = new Vector<>();
		final HpcJobManager hpcJobManager = desktop.getHpcJobManager();
		Map<Long, HpcJobInfo> activeHpcJobInfoMap = null;

		// Pending
		Map<HpcAccount, Set<HpcJobInfo>> pendingHpcJobInfoMap = hpcJobManager.getPendingHpcJobInfoMap();

		pendingDisplayHpcJobInfoSet.clear();

		for (HpcAccount hpcAccount : pendingHpcJobInfoMap.keySet()) {

			if (exclusiveHpcAccounts != null && !exclusiveHpcAccounts.contains(hpcAccount)) {
				continue;
			}

			Set<HpcJobInfo> pendingHpcJobSet = pendingHpcJobInfoMap.get(hpcAccount);
			for (HpcJobInfo hpcJobInfo : pendingHpcJobSet) {
				// For monitoring purpose
				pendingDisplayHpcJobInfoSet.add(hpcJobInfo);

				if (activeHpcJobInfoMap == null) {
					activeHpcJobInfoMap = new HashMap<>();
				}
				activeHpcJobInfoMap.put(hpcJobInfo.getId(), hpcJobInfo);
			}
		}

		// Submitted
		Map<HpcAccount, Set<HpcJobInfo>> submittedHpcJobInfoMap = hpcJobManager.getSubmittedHpcJobInfoMap();

		submittedDisplayHpcJobInfoSet.clear();

		for (HpcAccount hpcAccount : submittedHpcJobInfoMap.keySet()) {

			if (exclusiveHpcAccounts != null && !exclusiveHpcAccounts.contains(hpcAccount)) {
				continue;
			}

			Set<HpcJobInfo> submittedHpcJobSet = submittedHpcJobInfoMap.get(hpcAccount);
			for (HpcJobInfo hpcJobInfo : submittedHpcJobSet) {
				// For monitoring purpose
				submittedDisplayHpcJobInfoSet.add(hpcJobInfo);

				if (activeHpcJobInfoMap == null) {
					activeHpcJobInfoMap = new HashMap<>();
				}
				activeHpcJobInfoMap.put(hpcJobInfo.getId(), hpcJobInfo);
			}
		}

		if (activeHpcJobInfoMap != null) {

			List<Long> activeJobIds = new ArrayList<>(activeHpcJobInfoMap.keySet());

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
				rowData.add(FilePrint.fileTimestamp(hpcJobLog.getAddedTime().getTime()));

				// HPC node name
				HpcAccount hpcAccount = hpcJobInfo.getHpcAccount();
				rowData.add(hpcAccount.getConnectionName());

				// Algorithm
				rowData.add(hpcJobInfo.getAlgoId());

				// Dataset uploading progress
				AlgorithmParamRequest algorParamReq = hpcJobInfo.getAlgorithmParamRequest();
				String datasetPath = algorParamReq.getDatasetPath();

				int progress = hpcJobManager.getUploadFileProgress(datasetPath);
				if (progress > -1 && progress < 100) {
					rowData.add("" + progress + "%");
				} else {
					rowData.add("Done");
				}

				// Prior Knowledge uploading progress
				String priorKnowledgePath = algorParamReq.getPriorKnowledgePath();
				if (priorKnowledgePath != null) {
					progress = hpcJobManager.getUploadFileProgress(priorKnowledgePath);
					if (progress > -1 && progress < 100) {
						rowData.add("" + progress + "%");
					} else {
						rowData.add("Done");
					}
				} else {
					rowData.add("Skipped");
				}

				if (status > -1) {
					// Submitted time
					rowData.add(FilePrint.fileTimestamp(hpcJobInfo.getSubmittedTime().getTime()));

					// HPC job id
					rowData.add(hpcJobInfo.getPid() != null ? "" + hpcJobInfo.getPid() : "");

				} else {
					rowData.add("");
					rowData.add("");
				}

				// Last update time
				rowData.add(FilePrint.fileTimestamp(hpcJobLog.getLastUpdatedTime().getTime()));

				// Cancel job
				rowData.add("Cancel");

				activeRowData.add(rowData);
			}
		}

		return activeRowData;
	}

	private Vector<Vector<String>> getFinishedRowData(final TetradDesktop desktop,
			final List<HpcAccount> exclusiveHpcAccounts) throws Exception {
		final Vector<Vector<String>> finishedRowData = new Vector<>();
		HpcJobManager hpcJobManager = desktop.getHpcJobManager();
		Map<Long, HpcJobInfo> finishedHpcJobIdMap = null;

		// Finished jobs
		Map<HpcAccount, Set<HpcJobInfo>> finishedHpcJobInfoMap = hpcJobManager.getFinishedHpcJobInfoMap();
		for (HpcAccount hpcAccount : finishedHpcJobInfoMap.keySet()) {

			if (exclusiveHpcAccounts != null && !exclusiveHpcAccounts.contains(hpcAccount)) {
				continue;
			}

			Set<HpcJobInfo> finishedHpcJobSet = finishedHpcJobInfoMap.get(hpcAccount);
			for (HpcJobInfo hpcJobInfo : finishedHpcJobSet) {
				if (finishedHpcJobIdMap == null) {
					finishedHpcJobIdMap = new HashMap<>();
				}
				finishedHpcJobIdMap.put(hpcJobInfo.getId(), hpcJobInfo);
			}
		}

		if (finishedHpcJobIdMap != null) {

			List<Long> finishedJobIds = new ArrayList<>(finishedHpcJobIdMap.keySet());

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
				rowData.add(FilePrint.fileTimestamp(hpcJobLog.getAddedTime().getTime()));

				// HPC node name
				HpcAccount hpcAccount = hpcJobInfo.getHpcAccount();
				rowData.add(hpcAccount.getConnectionName());

				// Algorithm
				rowData.add(hpcJobInfo.getAlgoId());

				// Submitted time
				rowData.add(hpcJobInfo.getSubmittedTime() != null
						? FilePrint.fileTimestamp(hpcJobInfo.getSubmittedTime().getTime()) : "");

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
					rowData.add(FilePrint.fileTimestamp(hpcJobLog.getEndedTime().getTime()));
				} else {
					rowData.add("");
				}

				// Canceled time
				if (status == 4) {
					rowData.add(hpcJobLog.getCanceledTime() != null
							? FilePrint.fileTimestamp(hpcJobLog.getCanceledTime().getTime()) : "");
				} else {
					rowData.add("");
				}

				// Last update time
				rowData.add(FilePrint.fileTimestamp(hpcJobLog.getLastUpdatedTime().getTime()));

				// Delete job from db
				rowData.add("Delete");

				finishedRowData.add(rowData);
			}

		}

		return finishedRowData;
	}

	public synchronized Set<HpcJobInfo> getPendingDisplayHpcJobInfoSet() {

		return pendingDisplayHpcJobInfoSet;
	}

	public synchronized void removePendingDisplayHpcJobInfo(final Set<HpcJobInfo> removingJobSet) {
		for (final HpcJobInfo hpcJobInfo : removingJobSet) {
			for (HpcJobInfo pendingJob : pendingDisplayHpcJobInfoSet) {
				if (hpcJobInfo.getId() == pendingJob.getId()) {
					pendingDisplayHpcJobInfoSet.remove(pendingJob);
					continue;
				}
			}
		}
	}

	public Set<HpcJobInfo> getSubmittedDisplayHpcJobInfoSet() {
		return submittedDisplayHpcJobInfoSet;
	}

	public synchronized void addSubmittedDisplayHpcJobInfo(final Set<HpcJobInfo> submittedJobSet) {
		for (HpcJobInfo job : submittedJobSet) {
			LOGGER.debug("addSubmittedDisplayHpcJobInfo: job: " + job.getId());
		}
		submittedDisplayHpcJobInfoSet.addAll(submittedJobSet);
	}

	public synchronized void removeSubmittedDisplayHpcJobInfo(final Set<HpcJobInfo> removingJobSet) {
		for (final HpcJobInfo hpcJobInfo : removingJobSet) {
			for (Iterator<HpcJobInfo> it = submittedDisplayHpcJobInfoSet.iterator(); it.hasNext();) {
				final HpcJobInfo submittedJob = it.next();
				if (hpcJobInfo.getId() == submittedJob.getId()) {
					submittedDisplayHpcJobInfoSet.remove(hpcJobInfo);
					continue;
				}
			}
		}

	}

	public synchronized void removeSubmittedDisplayJobFromActiveTableModel(final Set<HpcJobInfo> finishedJobSet) {
		DefaultTableModel model = (DefaultTableModel) jobsTable.getModel();
		Map<Long, Integer> rowMap = new HashMap<>();
		for (int row = 0; row < model.getRowCount(); row++) {
			rowMap.put(Long.valueOf(model.getValueAt(row, ID_COLUMN).toString()), row);
		}

		for (final HpcJobInfo hpcJobInfo : finishedJobSet) {
			if (rowMap.containsKey(hpcJobInfo.getId())) {
				model.removeRow(rowMap.get(hpcJobInfo.getId()));
			}
		}

	}

	public TableModel getJobsTableModel() {
		return jobsTable.getModel();
	}

	public int selectedTabbedPaneIndex() {
		return tabbedPane.getSelectedIndex();
	}

	@Override
	public boolean finalizeEditor() {
		stopUpdaters();
		return true;
	}

}
