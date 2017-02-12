package edu.cmu.tetradapp.app.hpc.task;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import edu.cmu.tetradapp.app.TetradDesktop;
import edu.cmu.tetradapp.app.hpc.manager.HpcAccountManager;
import edu.cmu.tetradapp.app.hpc.manager.HpcAccountService;
import edu.cmu.tetradapp.app.hpc.manager.HpcJobManager;
import edu.cmu.tetradapp.app.hpc.util.HpcAccountUtils;
import edu.cmu.tetradapp.util.DesktopController;
import edu.pitt.dbmi.ccd.rest.client.dto.algo.JobInfo;
import edu.pitt.dbmi.ccd.rest.client.dto.data.DataFile;
import edu.pitt.dbmi.ccd.rest.client.service.data.DataUploadService;
import edu.pitt.dbmi.ccd.rest.client.service.data.RemoteDataFileService;
import edu.pitt.dbmi.ccd.rest.client.service.jobqueue.JobQueueService;
import edu.pitt.dbmi.tetrad.db.entity.AlgorithmParamRequest;
import edu.pitt.dbmi.tetrad.db.entity.AlgorithmParameter;
import edu.pitt.dbmi.tetrad.db.entity.HpcAccount;
import edu.pitt.dbmi.tetrad.db.entity.HpcJobInfo;
import edu.pitt.dbmi.tetrad.db.entity.HpcJobLog;
import edu.pitt.dbmi.tetrad.db.entity.JvmOption;

/**
 * 
 * Jan 10, 2017 4:12:10 PM
 * 
 * @author Chirayu (Kong) Wongchokprasitti, PhD
 * 
 */
public class HpcJobPreProcessTask implements Runnable {

    private final HpcJobInfo hpcJobInfo;

    public HpcJobPreProcessTask(final HpcJobInfo hpcJobInfo) {
	this.hpcJobInfo = hpcJobInfo;
    }

    @Override
    public void run() {
	TetradDesktop desktop = (TetradDesktop) DesktopController.getInstance();
	while (desktop == null) {
	    try {
		Thread.sleep(1000);
	    } catch (InterruptedException e) {
		e.printStackTrace();
	    }
	}
	final HpcAccountManager hpcAccountManager = desktop
		.getHpcAccountManager();
	final HpcJobManager hpcJobManager = desktop.getHpcJobManager();

	HpcAccount hpcAccount = hpcJobInfo.getHpcAccount();

	AlgorithmParamRequest algorParamReq = hpcJobInfo
		.getAlgorithmParamRequest();
	String datasetPath = algorParamReq.getDatasetPath();
	String priorKnowledgePath = algorParamReq.getPriorKnowledgePath();

	try {
	    HpcAccountService hpcAccountService = hpcJobManager
		    .getHpcAccountService(hpcAccount);

	    HpcJobLog hpcJobLog = hpcJobManager.getHpcJobLog(hpcJobInfo);

	    String log = "Initiated connection to "
		    + hpcAccount.getConnectionName();
	    System.out.println(log);
	    hpcJobManager.logHpcJobLogDetail(hpcJobLog, -1, log);

	    log = "datasetPath: " + datasetPath;
	    System.out.println(log);
	    Path file = Paths.get(datasetPath);
	    // Get file's MD5 hash and use it as its identifier
	    String md5 = algorParamReq.getDatasetMd5();

	    // Initiate data uploading progress
	    hpcJobManager.updateUploadFileProgress(datasetPath, 0);

	    Path prior = null;
	    if (priorKnowledgePath != null) {
		log = "priorKnowledgePath: " + priorKnowledgePath;
		System.out.println(log);
		prior = Paths.get(priorKnowledgePath);

		// Initiate prior knowledge uploading progress
		hpcJobManager.updateUploadFileProgress(priorKnowledgePath, 0);
	    }

	    // Check if this dataset already exists with this md5 hash
	    RemoteDataFileService remoteDataService = hpcAccountService
		    .getRemoteDataService();

	    DataFile dataFile = HpcAccountUtils.getRemoteDataFile(
		    hpcAccountManager, remoteDataService, hpcAccount, md5);
	    DataUploadService dataUploadService = hpcAccountService
		    .getDataUploadService();

	    // If not, upload the file
	    if (dataFile == null) {
		log = "Started uploading " + file.getFileName().toString();
		System.out.println(log);
		dataUploadService.startUpload(file, HpcAccountUtils
			.getJsonWebToken(hpcAccountManager, hpcAccount));
		hpcJobManager.logHpcJobLogDetail(hpcJobLog, -1, log);

		int progress;
		while ((progress = dataUploadService.getUploadJobStatus(file
			.toAbsolutePath().toString())) < 100) {
		    // System.out.println("Uploading "
		    // + file.toAbsolutePath().toString() + " Progress: "
		    // + progress + "%");
		    hpcJobManager.updateUploadFileProgress(datasetPath,
			    progress);
		    Thread.sleep(10);
		}

		hpcJobManager.updateUploadFileProgress(datasetPath, progress);

		log = "Finished uploading " + file.getFileName().toString();
		System.out.println(log);
		hpcJobManager.logHpcJobLogDetail(hpcJobLog, -1, log);

		// Get remote datafile
		dataFile = HpcAccountUtils.getRemoteDataFile(hpcAccountManager,
			remoteDataService, hpcAccount, md5);

		HpcAccountUtils
			.summarizeDataset(remoteDataService, algorParamReq,
				dataFile.getId(), HpcAccountUtils
					.getJsonWebToken(hpcAccountManager,
						hpcAccount));
		log = "Summarized " + file.getFileName().toString();
		System.out.println(log);
		hpcJobManager.logHpcJobLogDetail(hpcJobLog, -1, log);
	    } else {
		log = "Skipped uploading " + file.getFileName().toString();
		System.out.println(log);
		hpcJobManager.logHpcJobLogDetail(hpcJobLog, -1, log);

		if (dataFile.getFileSummary().getVariableType() == null) {
		    HpcAccountUtils.summarizeDataset(remoteDataService,
			    algorParamReq, dataFile.getId(), HpcAccountUtils
				    .getJsonWebToken(hpcAccountManager,
					    hpcAccount));
		    log = "Summarized " + file.getFileName().toString();
		    System.out.println(log);
		    hpcJobManager.logHpcJobLogDetail(hpcJobLog, -1,
			    "Summarized " + file.getFileName().toString());
		}

	    }

	    DataFile priorKnowledgeFile = null;

	    // Prior Knowledge File
	    if (prior != null) {
		// Get prior knowledge file Id
		md5 = algorParamReq.getPriorKnowledgeMd5();

		priorKnowledgeFile = HpcAccountUtils
			.getRemotePriorKnowledgeFile(hpcAccountManager,
				remoteDataService, hpcAccount, md5);

		if (priorKnowledgeFile == null) {
		    // Upload prior knowledge file
		    dataUploadService.startUpload(prior, HpcAccountUtils
			    .getJsonWebToken(hpcAccountManager, hpcAccount));

		    log = "Started uploading Prior Knowledge File";
		    System.out.println(log);
		    hpcJobManager.logHpcJobLogDetail(hpcJobLog, -1, log);

		    int progress;
		    while ((progress = dataUploadService
			    .getUploadJobStatus(prior.toAbsolutePath()
				    .toString())) < 100) {
			hpcJobManager.updateUploadFileProgress(
				priorKnowledgePath, progress);
			Thread.sleep(10);
		    }

		    hpcJobManager.updateUploadFileProgress(priorKnowledgePath,
			    progress);

		    priorKnowledgeFile = HpcAccountUtils
			    .getRemotePriorKnowledgeFile(hpcAccountManager,
				    remoteDataService, hpcAccount, md5);

		    log = "Finished uploading Prior Knowledge File";
		    System.out.println(log);
		    hpcJobManager.logHpcJobLogDetail(hpcJobLog, -1, log);

		}

	    }

	    // Algorithm Job Preparation
	    edu.pitt.dbmi.ccd.rest.client.dto.algo.AlgorithmParamRequest paramRequest = new edu.pitt.dbmi.ccd.rest.client.dto.algo.AlgorithmParamRequest();
	    paramRequest.setDatasetFileId(dataFile.getId());

	    Map<String, Object> dataValidation = new HashMap<>();
	    dataValidation.put("skipUniqueVarName", false);
	    System.out.println("dataValidation: skipUniqueVarName: false");
	    if (algorParamReq.getVariableType().equalsIgnoreCase("discrete")) {
		dataValidation.put("skipNonzeroVariance", false);
		System.out
			.println("dataValidation: skipNonzeroVariance: false");
	    } else {
		dataValidation.put("skipCategoryLimit", false);
		System.out.println("dataValidation: skipCategoryLimit: false");
	    }
	    paramRequest.setDataValidation(dataValidation);

	    Map<String, Object> algorithmParameters = new HashMap<>();
	    for (AlgorithmParameter param : algorParamReq
		    .getAlgorithmParameters()) {
		algorithmParameters.put(param.getParameter(), param.getValue());
		System.out.println("AlgorithmParameter: "
			+ param.getParameter() + " : " + param.getValue());
	    }

	    if (priorKnowledgeFile != null) {
		algorithmParameters.put("priorKnowledgeFileId",
			priorKnowledgeFile.getId());
		System.out.println("priorKnowledgeFileId: "
			+ priorKnowledgeFile.getId());
	    }
	    paramRequest.setAlgorithmParameters(algorithmParameters);

	    Map<String, Object> jvmOptions = new HashMap<>();
	    for (JvmOption jvmOption : algorParamReq.getJvmOptions()) {
		jvmOptions.put(jvmOption.getParameter(), jvmOption.getValue());
		System.out.println("JvmOption: " + jvmOption.getParameter()
			+ " : " + jvmOption.getValue());
	    }
	    paramRequest.setJvmOptions(jvmOptions);

	    // Submit a job
	    String algorithmName = hpcJobInfo.getAlgorithmName();
	    JobQueueService jobQueueService = hpcAccountService
		    .getJobQueueService();
	    JobInfo jobInfo = jobQueueService.addToRemoteQueue(algorithmName,
		    paramRequest, HpcAccountUtils.getJsonWebToken(
			    hpcAccountManager, hpcAccount));

	    // Log the job submission
	    hpcJobInfo.setSubmittedTime(new Date(System.currentTimeMillis()));
	    hpcJobInfo.setStatus(0); // Submitted
	    hpcJobInfo.setPid(jobInfo.getId());
	    hpcJobInfo.setResultFileName(jobInfo.getResultFileName());
	    hpcJobInfo.setResultJsonFileName(jobInfo.getResultJsonFileName());
	    hpcJobInfo.setErrorResultFileName(jobInfo.getErrorResultFileName());

	    hpcJobManager.updateHpcJobInfo(hpcJobInfo);

	    log = "Submitted job to " + hpcAccount.getConnectionName();
	    System.out.println(log);
	    hpcJobManager.logHpcJobLogDetail(hpcJobLog, 0, log);

	    System.out.println("HpcJobPreProcessTask: HpcJobInfo: id : "
		    + hpcJobInfo.getId() + " : pid : " + hpcJobInfo.getPid()
		    + " : " + hpcJobInfo.getAlgorithmName() + " : "
		    + hpcJobInfo.getResultFileName());

	    hpcJobManager.addNewSubmittedHpcJob(hpcJobInfo);

	} catch (Exception e) {
	    // TODO Auto-generated catch block
	    e.printStackTrace();
	}

    }

    public HpcJobInfo getHpcJobInfo() {
	return hpcJobInfo;
    }

}
