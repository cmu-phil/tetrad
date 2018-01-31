package edu.cmu.tetradapp.app.hpc.task;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimerTask;

import org.apache.http.client.ClientProtocolException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.cmu.tetradapp.app.TetradDesktop;
import edu.cmu.tetradapp.app.hpc.manager.HpcAccountManager;
import edu.cmu.tetradapp.app.hpc.manager.HpcJobManager;
import edu.cmu.tetradapp.editor.GeneralAlgorithmEditor;
import edu.cmu.tetradapp.util.DesktopController;
import edu.pitt.dbmi.ccd.rest.client.dto.algo.JobInfo;
import edu.pitt.dbmi.ccd.rest.client.dto.algo.ResultFile;
import edu.pitt.dbmi.tetrad.db.entity.HpcAccount;
import edu.pitt.dbmi.tetrad.db.entity.HpcJobInfo;
import edu.pitt.dbmi.tetrad.db.entity.HpcJobLog;

/**
 * 
 * Jan 10, 2017 11:37:53 AM
 * 
 * @author Chirayu (Kong) Wongchokprasitti, PhD
 * 
 */
public class HpcJobsScheduledTask extends TimerTask {
	
	private final Logger LOGGER = LoggerFactory.getLogger(HpcJobsScheduledTask.class);

	public HpcJobsScheduledTask() {
	}

	// Pooling job status from HPC nodes
	@Override
	public void run() {
		TetradDesktop desktop = (TetradDesktop) DesktopController.getInstance();
		if (desktop == null)
			return;

		final HpcAccountManager hpcAccountManager = desktop.getHpcAccountManager();
		
		// No Hpc Account in the first place, no need to proceed!
		List<HpcAccount> hpcAccounts = hpcAccountManager.getHpcAccounts();
		if(hpcAccounts == null || hpcAccounts.isEmpty())return;
		
		
		final HpcJobManager hpcJobManager = desktop.getHpcJobManager();

		//LOGGER.debug("HpcJobsScheduledTask: " + new Date(System.currentTimeMillis()));

		// Load active jobs: Status (0 = Submitted; 1 = Running; 2 = Kill
		// Request)
		Map<HpcAccount, Set<HpcJobInfo>> submittedHpcJobInfos = hpcJobManager.getSubmittedHpcJobInfoMap();
		/*if (submittedHpcJobInfos.size() == 0) {
			LOGGER.debug("Submitted job pool is empty!");
		} else {
			LOGGER.debug("Submitted job pool has " + submittedHpcJobInfos.keySet().size() + " hpcAccount"
					+ (submittedHpcJobInfos.keySet().size() > 1 ? "s" : ""));
		}*/

		for (HpcAccount hpcAccount : submittedHpcJobInfos.keySet()) {

			LOGGER.debug("HpcJobsScheduledTask: " + hpcAccount.getConnectionName());

			Set<HpcJobInfo> hpcJobInfos = submittedHpcJobInfos.get(hpcAccount);
			// Pid-HpcJobInfo map
			Map<Long, HpcJobInfo> hpcJobInfoMap = new HashMap<>();
			for (HpcJobInfo hpcJobInfo : hpcJobInfos) {
				if (hpcJobInfo.getPid() != null) {
					long pid = hpcJobInfo.getPid().longValue();
					hpcJobInfoMap.put(pid, hpcJobInfo);

					LOGGER.debug("id: " + hpcJobInfo.getId() + " : " + hpcJobInfo.getAlgoId() + ": pid: "
							+ pid + " : " + hpcJobInfo.getResultFileName());

				} else {

					LOGGER.debug("id: " + hpcJobInfo.getId() + " : " + hpcJobInfo.getAlgoId()
							+ ": no pid! : " + hpcJobInfo.getResultFileName());

					hpcJobInfos.remove(hpcJobInfo);
				}
			}

			// Finished job map
			HashMap<Long, HpcJobInfo> finishedJobMap = new HashMap<>();
			for (HpcJobInfo job : hpcJobInfos) {
				finishedJobMap.put(job.getPid(), job);
			}

			try {
				List<JobInfo> jobInfos = hpcJobManager.getRemoteActiveJobs(hpcAccountManager, hpcAccount);

				for (JobInfo jobInfo : jobInfos) {
					LOGGER.debug("Remote pid: " + jobInfo.getId() + " : " + jobInfo.getAlgoId() + " : "
							+ jobInfo.getResultFileName());

					long pid = jobInfo.getId();

					if (finishedJobMap.containsKey(pid)) {
						finishedJobMap.remove(pid);
					}

					int remoteStatus = jobInfo.getStatus();
					String recentStatusText = (remoteStatus == 0 ? "Submitted"
							: (remoteStatus == 1 ? "Running" : "Kill Request"));
					HpcJobInfo hpcJobInfo = hpcJobInfoMap.get(pid);// Local job
					// map
					HpcJobLog hpcJobLog = hpcJobManager.getHpcJobLog(hpcJobInfo);
					if (hpcJobInfo != null) {
						int status = hpcJobInfo.getStatus();
						if (status != remoteStatus) {
							// Update status
							hpcJobInfo.setStatus(remoteStatus);

							hpcJobManager.updateHpcJobInfo(hpcJobInfo);
							hpcJobLog.setLastUpdatedTime(new Date(System.currentTimeMillis()));

							String log = "Job status changed to " + recentStatusText;
							LOGGER.debug(hpcJobInfo.getAlgoId() + " : id : " + hpcJobInfo.getId()
									+ " : pid : " + pid);
							LOGGER.debug(log);

							hpcJobManager.logHpcJobLogDetail(hpcJobLog, remoteStatus, log);
						}
					}
				}

				// Download finished jobs' results
				if (finishedJobMap.size() > 0) {
					Set<ResultFile> resultFiles = hpcJobManager.listRemoteAlgorithmResultFiles(hpcAccountManager,
							hpcAccount);

					Set<String> resultFileNames = new HashSet<>();
					for (ResultFile resultFile : resultFiles) {
						resultFileNames.add(resultFile.getName());
						// LOGGER.debug(hpcAccount.getConnectionName()
						// + " Result : " + resultFile.getName());
					}

					for (HpcJobInfo hpcJobInfo : finishedJobMap.values()) {// Job
						// is
						// done
						// or
						// killed or
						// time-out
						HpcJobLog hpcJobLog = hpcJobManager.getHpcJobLog(hpcJobInfo);
						String recentStatusText = "Job finished";
						int recentStatus = 3; // Finished
						if (hpcJobInfo.getStatus() == 2) {
							recentStatusText = "Job killed";
							recentStatus = 4; // Killed
						}
						hpcJobInfo.setStatus(recentStatus);
						hpcJobManager.updateHpcJobInfo(hpcJobInfo);

						// LOGGER.debug("hpcJobInfo: id: "
						// + hpcJobInfo.getId() + " : "
						// + hpcJobInfo.getStatus());

						hpcJobManager.logHpcJobLogDetail(hpcJobLog, recentStatus, recentStatusText);

						LOGGER.debug(hpcJobInfo.getAlgoId() + " : id : " + hpcJobInfo.getId() + " : "
								+ recentStatusText);

						GeneralAlgorithmEditor editor = hpcJobManager.getGeneralAlgorithmEditor(hpcJobInfo);
						if (editor != null) {
							LOGGER.debug("GeneralAlgorithmEditor is not null");

							String resultJsonFileName = hpcJobInfo.getResultJsonFileName();
							String errorResultFileName = hpcJobInfo.getErrorResultFileName();

							if (resultFileNames.contains(resultJsonFileName)) {
								recentStatus = 5; // Result Downloaded

								String json = downloadAlgorithmResultFile(hpcAccountManager, hpcJobManager, hpcAccount,
										resultJsonFileName, editor);

								if (!json.toLowerCase().contains("not found")) {
									editor.setAlgorithmResult(json);
								}

								String log = "Result downloaded";
								hpcJobManager.logHpcJobLogDetail(hpcJobLog, recentStatus, log);

								LOGGER.debug(
										hpcJobInfo.getAlgoId() + " : id : " + hpcJobInfo.getId() + " : " + log);

							} else if (resultFileNames.contains(errorResultFileName)) {
								recentStatus = 6; // Error Result Downloaded

								String error = downloadAlgorithmResultFile(hpcAccountManager, hpcJobManager, hpcAccount,
										errorResultFileName, editor);

								if (!error.toLowerCase().contains("not found")) {
									editor.setAlgorithmErrorResult(error);
								}

								String log = "Error Result downloaded";
								hpcJobManager.logHpcJobLogDetail(hpcJobLog, recentStatus, log);

								LOGGER.debug(
										hpcJobInfo.getAlgoId() + " : id : " + hpcJobInfo.getId() + " : " + log);

							} else {

								// Try again
								Thread.sleep(5000);

								String json = downloadAlgorithmResultFile(hpcAccountManager, hpcJobManager, hpcAccount,
										resultJsonFileName, editor);

								if (!json.toLowerCase().contains("not found")) {
									editor.setAlgorithmResult(json);

									recentStatus = 5; // Result Downloaded

									String log = "Result downloaded";
									hpcJobManager.logHpcJobLogDetail(hpcJobLog, recentStatus, log);

									LOGGER.debug(hpcJobInfo.getAlgoId() + " : id : " + hpcJobInfo.getId()
											+ " : " + log);
								} else {
									String error = downloadAlgorithmResultFile(hpcAccountManager, hpcJobManager,
											hpcAccount, errorResultFileName, editor);

									if (!error.toLowerCase().contains("not found")) {
										editor.setAlgorithmErrorResult(error);

										recentStatus = 6; // Error Result
										// Downloaded

										String log = "Error Result downloaded";
										hpcJobManager.logHpcJobLogDetail(hpcJobLog, recentStatus, log);

										LOGGER.debug(hpcJobInfo.getAlgoId() + " : id : "
												+ hpcJobInfo.getId() + " : " + log);
									} else {
										recentStatus = 7; // Result Not Found

										String log = resultJsonFileName + " not found";
										hpcJobManager.logHpcJobLogDetail(hpcJobLog, recentStatus, log);

										LOGGER.debug(hpcJobInfo.getAlgoId() + " : id : "
												+ hpcJobInfo.getId() + " : " + log);
									}

								}

							}

						}
						hpcJobManager.removeFinishedHpcJob(hpcJobInfo);
					}
				} else {
					LOGGER.debug("No finished job yet.");
				}

			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

		}

	}

	private String downloadAlgorithmResultFile(final HpcAccountManager hpcAccountManager,
			final HpcJobManager hpcJobManager, final HpcAccount hpcAccount, final String resultFileName,
			final GeneralAlgorithmEditor editor)
			throws ClientProtocolException, URISyntaxException, IOException, Exception {
		int trial = 10;
		String txt = hpcJobManager.downloadAlgorithmResultFile(hpcAccountManager, hpcAccount, resultFileName);
		while (trial != 0 && txt.toLowerCase().contains("not found")) {
			Thread.sleep(5000);
			txt = hpcJobManager.downloadAlgorithmResultFile(hpcAccountManager, hpcAccount, resultFileName);
			trial--;
		}

		return txt;
	}

}
