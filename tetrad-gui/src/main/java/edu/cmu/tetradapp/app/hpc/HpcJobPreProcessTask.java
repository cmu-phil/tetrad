package edu.cmu.tetradapp.app.hpc;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.apache.http.client.ClientProtocolException;

import edu.pitt.dbmi.ccd.commons.file.MessageDigestHash;
import edu.pitt.dbmi.ccd.rest.client.dto.algo.JobInfo;
import edu.pitt.dbmi.ccd.rest.client.dto.data.DataFile;
import edu.pitt.dbmi.ccd.rest.client.dto.user.JsonWebToken;
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

    private final HpcJobManager hpcJobManager;

    private final HpcJobInfo hpcJobInfo;

    public HpcJobPreProcessTask(final HpcJobManager hpcJobManager,
	    final HpcJobInfo hpcJobInfo) {
	this.hpcJobManager = hpcJobManager;
	this.hpcJobInfo = hpcJobInfo;
    }

    @Override
    public void run() {
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
	    String md5 = MessageDigestHash.computeMD5Hash(file);

	    Path prior = null;
	    if (priorKnowledgePath != null) {
		log = "priorKnowledgePath: " + priorKnowledgePath;
		System.out.println(log);
		prior = Paths.get(priorKnowledgePath);
	    }

	    // Check if this dataset already exists with this md5 hash
	    RemoteDataFileService remoteDataService = hpcAccountService.getRemoteDataService();

	    DataFile dataFile = getRemoteDataFile(remoteDataService,
		    hpcAccount, md5);
	    DataUploadService dataUploadService = hpcAccountService.getDataUploadService();

	    // If not, upload the file
	    if (dataFile == null) {
		log = "Started uploading " + file.getFileName().toString();
		System.out.println(log);
		dataUploadService
			.startUpload(file, getJsonWebToken(hpcAccount));
		hpcJobManager.logHpcJobLogDetail(hpcJobLog, -1, log);

		int progress;
		while ((progress = dataUploadService.getUploadJobStatus(file
			.toAbsolutePath().toString())) < 100) {
		    System.out.println("Uploading "
			    + file.toAbsolutePath().toString() + " Progress: "
			    + progress + "%");
		    hpcJobManager.updateUploadFileProgress(datasetPath,
			    progress);
		    Thread.sleep(100);
		}

		log = "Finished uploading " + file.getFileName().toString();
		System.out.println(log);
		hpcJobManager.logHpcJobLogDetail(hpcJobLog, -1, log);

		// Get remote datafile
		dataFile = getRemoteDataFile(remoteDataService, hpcAccount, md5);

		summarizeDataset(remoteDataService, algorParamReq,
			dataFile.getId(), getJsonWebToken(hpcAccount));
		log = "Summarized " + file.getFileName().toString();
		System.out.println(log);
		hpcJobManager.logHpcJobLogDetail(hpcJobLog, -1, log);
	    } else {
		log = "Skipped uploading " + file.getFileName().toString();
		System.out.println(log);
		hpcJobManager.logHpcJobLogDetail(hpcJobLog, -1, log);

		if (dataFile.getFileSummary().getVariableType() == null) {
		    summarizeDataset(remoteDataService, algorParamReq,
			    dataFile.getId(), getJsonWebToken(hpcAccount));
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
		md5 = MessageDigestHash.computeMD5Hash(prior);

		priorKnowledgeFile = getRemotePriorKnowledgeFile(
			remoteDataService, hpcAccount, md5);

		if (priorKnowledgeFile == null) {
		    // Upload prior knowledge file
		    dataUploadService.startUpload(prior,
			    getJsonWebToken(hpcAccount));

		    log = "Started uploading Prior Knowledge File";
		    System.out.println(log);
		    hpcJobManager.logHpcJobLogDetail(hpcJobLog, -1, log);

		    int progress;
		    while ((progress = dataUploadService
			    .getUploadJobStatus(prior.toAbsolutePath()
				    .toString())) < 100) {
			System.out.println("Uploading "
				+ prior.toAbsolutePath().toString()
				+ " Progress: " + progress + "%");
			hpcJobManager.updateUploadFileProgress(
				priorKnowledgePath, progress);
			Thread.sleep(100);
		    }

		    priorKnowledgeFile = getRemotePriorKnowledgeFile(
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
	    JobQueueService jobQueueService = hpcAccountService.getJobQueueService();
	    JobInfo jobInfo = jobQueueService.addToRemoteQueue(algorithmName,
		    paramRequest, getJsonWebToken(hpcAccount));

	    // Log the job submission
	    hpcJobInfo.setSubmittedTime(new Date(System.currentTimeMillis()));
	    hpcJobInfo.setStatus(0); // Submitted
	    hpcJobInfo.setPid(jobInfo.getId());
	    hpcJobInfo.setResultFileName(jobInfo.getResultFileName());
	    hpcJobInfo.setResultJsonFileName(jobInfo.getResultJsonFileName());
	    hpcJobInfo.setErrorResultFileName(jobInfo.getErrorResultFileName());

	    hpcJobManager.updateHpcJobInfo(hpcJobInfo);

	    log = "Submitted job to " + hpcAccount.getConnectionName();

	    hpcJobManager.logHpcJobLogDetail(hpcJobLog, 0, log);

	    System.out.println("HpcJobPreProcessTask: HpcJobInfo: id : "
		    + hpcJobInfo.getId() + " : pid : " + hpcJobInfo.getPid()
		    + " : " + hpcJobInfo.getAlgorithmName() + " : "
		    + hpcJobInfo.getResultFileName());

	    hpcJobManager.addNewMonitoredHpcJob(hpcJobInfo);

	} catch (Exception e) {
	    // TODO Auto-generated catch block
	    e.printStackTrace();
	}

    }

    private void summarizeDataset(
	    final RemoteDataFileService remoteDataService,
	    final AlgorithmParamRequest algorParamReq,
	    final long datasetFileId, final JsonWebToken jsonWebToken)
	    throws ClientProtocolException, URISyntaxException, IOException {
	String variableType = "continuous";
	if (algorParamReq.getVariableType().equalsIgnoreCase("discrete")) {
	    variableType = "discrete";
	}

	String fileDelimiter = "tab";
	if (!algorParamReq.getFileDelimiter().equalsIgnoreCase("tab")) {
	    fileDelimiter = "comma";
	}

	remoteDataService.summarizeDataFile(datasetFileId, variableType,
		fileDelimiter, jsonWebToken);
    }

    private JsonWebToken getJsonWebToken(final HpcAccount hpcAccount)
	    throws Exception {
	return hpcJobManager.getJsonWebTokenManager().getJsonWebToken(
		hpcAccount);
    }

    private DataFile getRemoteDataFile(
	    final RemoteDataFileService remoteDataService,
	    final HpcAccount hpcAccount, String md5)
	    throws ClientProtocolException, URISyntaxException, IOException,
	    Exception {
	Set<DataFile> dataFiles = remoteDataService
		.retrieveDataFileInfo(getJsonWebToken(hpcAccount));
	for (DataFile dataFile : dataFiles) {
	    String remoteMd5 = dataFile.getMd5checkSum();
	    if (md5.equalsIgnoreCase(remoteMd5)) {
		return dataFile;
	    }
	}
	return null;
    }

    private DataFile getRemotePriorKnowledgeFile(
	    final RemoteDataFileService remoteDataService,
	    final HpcAccount hpcAccount, String md5)
	    throws ClientProtocolException, URISyntaxException, IOException,
	    Exception {
	Set<DataFile> priorFiles = remoteDataService
		.retrievePriorKnowledgeFileInfo(getJsonWebToken(hpcAccount));
	for (DataFile priorFile : priorFiles) {
	    String remoteMd5 = priorFile.getMd5checkSum();
	    if (md5.equalsIgnoreCase(remoteMd5)) {
		return priorFile;
	    }
	}
	return null;
    }

}
