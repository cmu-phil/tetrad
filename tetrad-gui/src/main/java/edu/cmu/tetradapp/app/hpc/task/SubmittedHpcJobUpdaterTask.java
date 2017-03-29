package edu.cmu.tetradapp.app.hpc.task;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TimerTask;

import javax.swing.table.DefaultTableModel;

import edu.cmu.tetradapp.app.hpc.editor.HpcJobActivityEditor;
import edu.cmu.tetradapp.app.hpc.manager.HpcJobManager;
import edu.pitt.dbmi.ccd.commons.file.FilePrint;
import edu.pitt.dbmi.tetrad.db.entity.HpcAccount;
import edu.pitt.dbmi.tetrad.db.entity.HpcJobInfo;
import edu.pitt.dbmi.tetrad.db.entity.HpcJobLog;

/**
 * 
 * Feb 10, 2017 5:36:02 PM
 * 
 * @author Chirayu (Kong) Wongchokprasitti, PhD
 * 
 */
public class SubmittedHpcJobUpdaterTask extends TimerTask {

	private final HpcJobManager hpcJobManager;

	private final HpcJobActivityEditor hpcJobActivityEditor;

	public SubmittedHpcJobUpdaterTask(final HpcJobManager hpcJobManager,
			final HpcJobActivityEditor hpcJobActivityEditor) {
		this.hpcJobManager = hpcJobManager;
		this.hpcJobActivityEditor = hpcJobActivityEditor;
	}

	@Override
	public void run() {
		if (hpcJobActivityEditor.selectedTabbedPaneIndex() != 0)
			return;

		final DefaultTableModel model = (DefaultTableModel) hpcJobActivityEditor.getJobsTableModel();

		Set<HpcJobInfo> submittedDisplayHpcJobInfoSet = hpcJobActivityEditor.getSubmittedDisplayHpcJobInfoSet();

		Set<HpcJobInfo> finishedJobSet = monitorSubmittedJobStatus(submittedDisplayHpcJobInfoSet, model);

		hpcJobActivityEditor.removeSubmittedDisplayHpcJobInfo(finishedJobSet);

		hpcJobActivityEditor.removeSubmittedDisplayJobFromActiveTableModel(finishedJobSet);

	}

	private synchronized Set<HpcJobInfo> monitorSubmittedJobStatus(final Set<HpcJobInfo> submittedDisplayHpcJobInfoSet,
			final DefaultTableModel model) {

		Map<Long, Integer> rowMap = new HashMap<>();
		for (int row = 0; row < model.getRowCount(); row++) {
			rowMap.put(Long.valueOf(model.getValueAt(row, HpcJobActivityEditor.ID_COLUMN).toString()), row);
		}

		Set<HpcJobInfo> finishedJobSet = new HashSet<>();

		for (HpcJobInfo hpcJobInfo : submittedDisplayHpcJobInfoSet) {

			Long id = hpcJobInfo.getId();

			if (!rowMap.containsKey(id)) {
				// System.out.println("hpcJobInfo not found in rowMap");
				continue;
			}

			int modelRow = rowMap.get(id);

			Map<HpcAccount, Set<HpcJobInfo>> submittedHpcJobInfoMap = hpcJobManager.getSubmittedHpcJobInfoMap();
			Set<HpcJobInfo> submittedJobSet = submittedHpcJobInfoMap.get(hpcJobInfo.getHpcAccount());
			if (submittedJobSet != null) {
				for (HpcJobInfo submittedJob : submittedJobSet) {
					if (submittedJob.getId() == hpcJobInfo.getId()) {
						hpcJobInfo = submittedJob;
						// System.out
						// .println("Found submittedJob in the
						// submittedHpcJobInfoMap id matched!");
						continue;
					}
				}
			}

			int status = hpcJobInfo.getStatus();

			// Status
			switch (hpcJobInfo.getStatus()) {
			case -1:
				model.setValueAt("Pending", modelRow, HpcJobActivityEditor.STATUS_COLUMN);
				break;
			case 0:
				model.setValueAt("Submitted", modelRow, HpcJobActivityEditor.STATUS_COLUMN);
				break;
			case 1:
				model.setValueAt("Running", modelRow, HpcJobActivityEditor.STATUS_COLUMN);
				break;
			case 2:
				model.setValueAt("Kill Request", modelRow, HpcJobActivityEditor.STATUS_COLUMN);
				break;
			}

			// last update
			HpcJobLog hpcJobLog = hpcJobManager.getHpcJobLog(hpcJobInfo);
			model.setValueAt(FilePrint.fileTimestamp(hpcJobLog.getLastUpdatedTime().getTime()), modelRow,
					HpcJobActivityEditor.ACTIVE_LAST_UPDATED_COLUMN);

			// In case the job was accidentally added to the map OR the job
			// was finished.
			if (status > 2) {
				finishedJobSet.add(hpcJobInfo);
			}

		}

		return finishedJobSet;
	}

}
