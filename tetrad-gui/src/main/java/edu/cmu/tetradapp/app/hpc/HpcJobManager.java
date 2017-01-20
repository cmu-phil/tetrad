package edu.cmu.tetradapp.app.hpc;

import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import edu.cmu.tetradapp.editor.GeneralAlgorithmEditor;
import edu.pitt.dbmi.tetrad.db.entity.HpcAccount;
import edu.pitt.dbmi.tetrad.db.entity.HpcJobInfo;
import edu.pitt.dbmi.tetrad.db.entity.HpcJobLog;
import edu.pitt.dbmi.tetrad.db.entity.HpcJobLogDetail;
import edu.pitt.dbmi.tetrad.db.service.HpcJobInfoService;
import edu.pitt.dbmi.tetrad.db.service.HpcJobLogService;
import edu.pitt.dbmi.tetrad.db.service.HpcJobLogDetailService;

/**
 * 
 * Dec 14, 2016 3:49:31 PM
 * 
 * @author Chirayu (Kong) Wongchokprasitti, PhD
 * 
 */
public class HpcJobManager {

    private final HpcJobLogService hpcJobLogService;

    private final HpcJobLogDetailService hpcJobLogDetailService;

    private final HpcJobInfoService hpcJobInfoService;

    private final ExecutorService executorService;

    private final Timer timer;

    private int TIME_INTERVAL = 30000;

    private final Map<String, Integer> uploadFileProgressMap;

    private final Map<HpcJobInfo, GeneralAlgorithmEditor> hpcResultMap;

    private final JsonWebTokenManager jsonWebTokenManager;

    private final Map<HpcAccount, Set<HpcJobInfo>> activeHpcJobInfoMap;

    public HpcJobManager(final HpcJobLogService hpcJobLogService,
	    final HpcJobLogDetailService hpcJobLogDetailService,
	    final HpcJobInfoService hpcJobInfoService,
	    final int simultaneousUpload) {
	this.hpcJobLogService = hpcJobLogService;
	this.hpcJobLogDetailService = hpcJobLogDetailService;
	this.hpcJobInfoService = hpcJobInfoService;
	this.jsonWebTokenManager = new JsonWebTokenManager();
	executorService = Executors.newFixedThreadPool(simultaneousUpload);
	uploadFileProgressMap = new HashMap<>();
	activeHpcJobInfoMap = new HashMap<>();
	hpcResultMap = new HashMap<>();
	resumePreProcessJobs();
	this.timer = new Timer();
	startHpcJobScheduler();
	resumeActiveHpcJobInfos();
    }

    private void resumePreProcessJobs() {
	// Lookup on DB for HpcJobInfo with status -1 (Pending)
	List<HpcJobInfo> pendingHpcJobInfo = hpcJobInfoService.findByStatus(-1);
	if (pendingHpcJobInfo != null) {
	    for (HpcJobInfo hpcJobInfo : pendingHpcJobInfo) {
		System.out.println("resumePreProcessJobs: "
			+ hpcJobInfo.getAlgorithmName()
			+ " : "
			+ hpcJobInfo.getHpcAccount().getConnectionName()
			+ " : "
			+ hpcJobInfo.getAlgorithmParamRequest()
				.getDatasetPath());

		HpcJobPreProcessTask preProcessTask = new HpcJobPreProcessTask(
			this, hpcJobInfo);
		executorService.submit(preProcessTask);
	    }
	} else {
	    System.out
		    .println("resumePreProcessJobs: no pending jobs to be resumed");
	}
    }

    public void startHpcJobScheduler() {
	System.out.println("startHpcJobScheduler");
	HpcJobsScheduledTask hpcScheduledTask = new HpcJobsScheduledTask(this);
	timer.schedule(hpcScheduledTask, 0, TIME_INTERVAL);
    }

    public void submitNewHpcJobToQueue(final HpcJobInfo hpcJobInfo,
	    final GeneralAlgorithmEditor generalAlgorithmEditor) {

	hpcJobInfoService.add(hpcJobInfo);
	System.out.println("hpcJobInfo: id: " + hpcJobInfo.getId());

	HpcJobLog hpcJobLog = new HpcJobLog();
	hpcJobLog.setAddedTime(new Date());
	hpcJobLog.setHpcJobInfo(hpcJobInfo);
	hpcJobLogService.update(hpcJobLog);
	System.out.println("HpcJobLog: id: " + hpcJobLog.getId());

	HpcJobLogDetail hpcJobLogDetail = new HpcJobLogDetail();
	hpcJobLogDetail.setAddedTime(new Date());
	hpcJobLogDetail.setHpcJobLog(hpcJobLog);
	hpcJobLogDetail.setJobState(-1);// Pending
	hpcJobLogDetail.setProgress("Pending");
	hpcJobLogDetailService.add(hpcJobLogDetail);
	System.out.println("HpcJobLogDetail: id: " + hpcJobLogDetail.getId());

	hpcResultMap.put(hpcJobInfo, generalAlgorithmEditor);

	// Put a new pre-process task into hpc job queue
	HpcJobPreProcessTask preProcessTask = new HpcJobPreProcessTask(this,
		hpcJobInfo);
	executorService.submit(preProcessTask);
    }

    public void stopHpcJobScheduler() {
	timer.cancel();
    }

    public void restartHpcJobScheduler() {
	stopHpcJobScheduler();
	startHpcJobScheduler();
    }

    public HpcJobLog getHpcJobLog(final HpcJobInfo hpcJobInfo) {
	return hpcJobLogService.findByHpcJobInfo(hpcJobInfo);
    }

    public void appendHpcJobLogDetail(final HpcJobLogDetail hpcJobLogDetail) {
	hpcJobLogDetailService.add(hpcJobLogDetail);
    }

    public void updateHpcJobInfo(final HpcJobInfo hpcJobInfo) {
	hpcJobInfoService.update(hpcJobInfo);
    }

    public void updateHpcJobLog(final HpcJobLog hpcJobLog) {
	hpcJobLogService.update(hpcJobLog);
    }

    public void logHpcJobLogDetail(final HpcJobLog hpcJobLog, int jobState,
	    String jobProgress) {
	Date now = new Date(System.currentTimeMillis());
	hpcJobLog.setLastUpdatedTime(now);
	if (jobState == 3) {// Finished
	    hpcJobLog.setEndedTime(now);
	}
	if (jobState == 4) {// Killed
	    hpcJobLog.setCanceledTime(now);
	}
	updateHpcJobLog(hpcJobLog);

	HpcJobLogDetail hpcJobLogDetail = new HpcJobLogDetail();
	hpcJobLogDetail.setAddedTime(new Date(System.currentTimeMillis()));
	hpcJobLogDetail.setHpcJobLog(hpcJobLog);
	hpcJobLogDetail.setJobState(jobState);
	hpcJobLogDetail.setProgress(jobProgress);
	appendHpcJobLogDetail(hpcJobLogDetail);
    }

    public void updateUploadFileProgress(final String datasetPath,
	    int percentageProgress) {
	uploadFileProgressMap.put(datasetPath, percentageProgress);
    }

    public int getUploadFileProgress(final String dataPath) {
	int progress = -1;
	if (uploadFileProgressMap.containsKey(dataPath)) {
	    progress = uploadFileProgressMap.get(dataPath).intValue();
	}
	return progress;
    }

    public void resumeActiveHpcJobInfos() {
	// Lookup on DB for HpcJobInfo with status 0 (Submitted); 1 (Running); 2
	// (Kill Request)
	for (int status = 0; status <= 2; status++) {
	    System.out.println("resumeActiveHpcJobInfos: looping status: "
		    + status);
	    List<HpcJobInfo> submittedHpcJobInfo = hpcJobInfoService
		    .findByStatus(status);
	    if (submittedHpcJobInfo != null) {
		for (HpcJobInfo hpcJobInfo : submittedHpcJobInfo) {
		    addNewMonitoredHpcJob(hpcJobInfo);
		}
	    }
	}
    }

    public JsonWebTokenManager getJsonWebTokenManager() {
	return jsonWebTokenManager;
    }

    public GeneralAlgorithmEditor getGeneralAlgorithmEditor(
	    final HpcJobInfo hpcJobInfo) {
	return hpcResultMap.get(hpcJobInfo);
    }

    public void addNewMonitoredHpcJob(final HpcJobInfo hpcJobInfo) {
	HpcAccount hpcAccount = hpcJobInfo.getHpcAccount();
	System.out.println("addNewMonitoredHpcJob: connection: "
		+ hpcAccount.getConnectionName());
	System.out.println("addNewMonitoredHpcJob: algorithm: "
		+ hpcJobInfo.getAlgorithmName());
	System.out.println("addNewMonitoredHpcJob: status: "
		+ hpcJobInfo.getStatus());
	System.out
		.println("addNewMonitoredHpcJob: pid: " + hpcJobInfo.getPid());
	Set<HpcJobInfo> hpcJobInfos = activeHpcJobInfoMap.get(hpcAccount);
	if (hpcJobInfos == null) {
	    hpcJobInfos = new HashSet<>();
	}
	hpcJobInfos.add(hpcJobInfo);
	activeHpcJobInfoMap.put(hpcAccount, hpcJobInfos);
    }

    public void removedFinishedHpcJob(final HpcJobInfo hpcJobInfo) {
	HpcAccount hpcAccount = hpcJobInfo.getHpcAccount();
	System.out.println("removedFinishedHpcJob: connection: "
		+ hpcAccount.getConnectionName());
	System.out.println("removedFinishedHpcJob: algorithm: "
		+ hpcJobInfo.getAlgorithmName());
	System.out.println("removedFinishedHpcJob: status: "
		+ hpcJobInfo.getStatus());
	System.out
		.println("removedFinishedHpcJob: pid: " + hpcJobInfo.getPid());
	Set<HpcJobInfo> hpcJobInfos = activeHpcJobInfoMap.get(hpcAccount);
	if (hpcJobInfos != null) {
	    hpcJobInfos.remove(hpcJobInfo);
	    activeHpcJobInfoMap.put(hpcAccount, hpcJobInfos);
	}
	hpcResultMap.remove(hpcJobInfo);
    }

    public Map<HpcAccount, Set<HpcJobInfo>> getActiveHpcJobInfoMap() {
	return activeHpcJobInfoMap;
    }

}
