package edu.cmu.tetradapp.app.hpc.task;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TimerTask;

import javax.swing.table.DefaultTableModel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.cmu.tetradapp.app.hpc.editor.HpcJobActivityEditor;
import edu.cmu.tetradapp.app.hpc.manager.HpcJobManager;
import edu.pitt.dbmi.ccd.commons.file.FilePrint;
import edu.pitt.dbmi.tetrad.db.entity.AlgorithmParamRequest;
import edu.pitt.dbmi.tetrad.db.entity.HpcAccount;
import edu.pitt.dbmi.tetrad.db.entity.HpcJobInfo;
import edu.pitt.dbmi.tetrad.db.entity.HpcJobLog;

/**
 * 
 * Feb 10, 2017 2:02:20 PM
 * 
 * @author Chirayu (Kong) Wongchokprasitti, PhD
 * 
 */
public class PendingHpcJobUpdaterTask extends TimerTask {

	private final Logger LOGGER = LoggerFactory.getLogger(PendingHpcJobUpdaterTask.class);

	private final HpcJobManager hpcJobManager;

	private final HpcJobActivityEditor hpcJobActivityEditor;

	public PendingHpcJobUpdaterTask(final HpcJobManager hpcJobManager,
			final HpcJobActivityEditor hpcJobActivityEditor) {
		this.hpcJobManager = hpcJobManager;
		this.hpcJobActivityEditor = hpcJobActivityEditor;
	}

	@Override
	public void run() {

		if (hpcJobActivityEditor.selectedTabbedPaneIndex() != 0)
			return;

		final DefaultTableModel model = (DefaultTableModel) hpcJobActivityEditor.getJobsTableModel();

		Set<HpcJobInfo> pendingDisplayHpcJobInfoSet = hpcJobActivityEditor.getPendingDisplayHpcJobInfoSet();

		Set<HpcJobInfo> notPendingJobAnymoreSet = monitorDataUploadProgress(pendingDisplayHpcJobInfoSet, model);

		hpcJobActivityEditor.addSubmittedDisplayHpcJobInfo(notPendingJobAnymoreSet);

		hpcJobActivityEditor.removePendingDisplayHpcJobInfo(notPendingJobAnymoreSet);
	}

	private synchronized Set<HpcJobInfo> monitorDataUploadProgress(final Set<HpcJobInfo> pendingDisplayHpcJobInfoSet,
			final DefaultTableModel model) {

		Map<Long, Integer> rowMap = new HashMap<>();
		for (int row = 0; row < model.getRowCount(); row++) {
			rowMap.put(Long.valueOf(model.getValueAt(row, HpcJobActivityEditor.ID_COLUMN).toString()), row);
		}

		Set<HpcJobInfo> notPendingJobAnymoreSet = new HashSet<>();

		for (HpcJobInfo hpcJobInfo : pendingDisplayHpcJobInfoSet) {

			int status = hpcJobInfo.getStatus();

			if (!rowMap.containsKey(hpcJobInfo.getId())) {
				continue;
			}

			int modelRow = rowMap.get(hpcJobInfo.getId());

			// In case the job was accidentally added to the map OR the kill
			// request was issued
			if (status != -1) {
				notPendingJobAnymoreSet.add(hpcJobInfo);
			} else {

				// Dataset uploading progress
				AlgorithmParamRequest algorParamReq = hpcJobInfo.getAlgorithmParamRequest();
				String datasetPath = algorParamReq.getDatasetPath();

				int dataUploadProgress = hpcJobManager.getUploadFileProgress(datasetPath);

				if (dataUploadProgress > -1 && dataUploadProgress < 100) {
					model.setValueAt("" + dataUploadProgress + "%", modelRow, HpcJobActivityEditor.DATA_UPLOAD_COLUMN);
				} else if (dataUploadProgress == -1) {
					model.setValueAt("Skipped", modelRow, HpcJobActivityEditor.DATA_UPLOAD_COLUMN);
				} else {
					model.setValueAt("Done", modelRow, HpcJobActivityEditor.DATA_UPLOAD_COLUMN);
				}

				// Prior Knowledge uploading progress
				String priorKnowledgePath = algorParamReq.getPriorKnowledgePath();

				int priorKnowledgeUploadProgress = -1;
				if (priorKnowledgePath != null) {

					LOGGER.debug("priorKnowledgePath: " + priorKnowledgePath);

					priorKnowledgeUploadProgress = hpcJobManager.getUploadFileProgress(priorKnowledgePath);

					if (priorKnowledgeUploadProgress > -1 && priorKnowledgeUploadProgress < 100) {
						model.setValueAt("" + priorKnowledgeUploadProgress + "%", modelRow,
								HpcJobActivityEditor.KNOWLEDGE_UPLOAD_COLUMN);
					} else {
						model.setValueAt("Done", modelRow, HpcJobActivityEditor.KNOWLEDGE_UPLOAD_COLUMN);
					}
				} else {
					model.setValueAt("Skipped", modelRow, HpcJobActivityEditor.KNOWLEDGE_UPLOAD_COLUMN);
				}

				if (dataUploadProgress == 100
						&& (priorKnowledgeUploadProgress == -1 || priorKnowledgeUploadProgress == 100)) {

					LOGGER.debug("HpcJobInfo Id: " + hpcJobInfo.getId() + " done with both uploading");

					Map<HpcAccount, Set<HpcJobInfo>> pendingHpcJobInfoMap = hpcJobManager.getPendingHpcJobInfoMap();

					Map<HpcAccount, Set<HpcJobInfo>> submittedHpcJobInfoMap = hpcJobManager.getSubmittedHpcJobInfoMap();

					if (pendingHpcJobInfoMap != null) {
						Set<HpcJobInfo> pendingJobSet = pendingHpcJobInfoMap.get(hpcJobInfo.getHpcAccount());

						// Is the job still stuck in the pre-processed schedule
						// task?
						long id = -1;
						for (HpcJobInfo pendingJob : pendingJobSet) {
							if (pendingJob.getId() == hpcJobInfo.getId()) {
								id = pendingJob.getId();
								continue;
							}
						}

						// The job is not in the pre-processed schedule task
						if (id == -1 && submittedHpcJobInfoMap != null) {
							Set<HpcJobInfo> submittedJobSet = submittedHpcJobInfoMap.get(hpcJobInfo.getHpcAccount());

							// Is the job in the submitted schedule task?
							for (HpcJobInfo submittedJob : submittedJobSet) {
								if (submittedJob.getId() == hpcJobInfo.getId()) {

									// Status
									switch (submittedJob.getStatus()) {
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

									// Submitted time
									if (submittedJob.getSubmittedTime() != null) {
										model.setValueAt(
												FilePrint.fileTimestamp(hpcJobInfo.getSubmittedTime().getTime()),
												modelRow, HpcJobActivityEditor.ACTIVE_SUBMITTED_COLUMN);
									}

									// Hpc Pid
									model.setValueAt(submittedJob.getPid(), modelRow,
											HpcJobActivityEditor.ACTIVE_HPC_JOB_ID_COLUMN);

									// last update
									HpcJobLog hpcJobLog = hpcJobManager.getHpcJobLog(submittedJob);
									model.setValueAt(FilePrint.fileTimestamp(hpcJobLog.getLastUpdatedTime().getTime()),
											modelRow, HpcJobActivityEditor.ACTIVE_LAST_UPDATED_COLUMN);

									// Remove from the pending queue
									notPendingJobAnymoreSet.add(submittedJob);

									continue;
								}
							}
						}
					}
				}
			}
		}

		return notPendingJobAnymoreSet;
	}

}
