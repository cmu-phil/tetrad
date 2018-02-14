package edu.cmu.tetradapp.app.hpc.task;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.cmu.tetradapp.app.TetradDesktop;
import edu.cmu.tetradapp.app.hpc.manager.HpcAccountManager;
import edu.cmu.tetradapp.app.hpc.manager.HpcAccountService;
import edu.cmu.tetradapp.app.hpc.manager.HpcJobManager;
import edu.cmu.tetradapp.app.hpc.util.HpcAccountUtils;
import edu.cmu.tetradapp.util.DesktopController;
import edu.pitt.dbmi.ccd.rest.client.dto.algo.AlgoParameter;
import edu.pitt.dbmi.ccd.rest.client.dto.algo.JobInfo;
import edu.pitt.dbmi.ccd.rest.client.dto.algo.JvmOptions;
import edu.pitt.dbmi.ccd.rest.client.dto.data.DataFile;
import edu.pitt.dbmi.ccd.rest.client.service.data.DataUploadService;
import edu.pitt.dbmi.ccd.rest.client.service.data.RemoteDataFileService;
import edu.pitt.dbmi.ccd.rest.client.service.jobqueue.JobQueueService;
import edu.pitt.dbmi.tetrad.db.entity.AlgorithmParamRequest;
import edu.pitt.dbmi.tetrad.db.entity.AlgorithmParameter;
import edu.pitt.dbmi.tetrad.db.entity.HpcAccount;
import edu.pitt.dbmi.tetrad.db.entity.HpcJobInfo;
import edu.pitt.dbmi.tetrad.db.entity.HpcJobLog;
import edu.pitt.dbmi.tetrad.db.entity.HpcParameter;

/**
 * 
 * Jan 10, 2017 4:12:10 PM
 * 
 * @author Chirayu Kong Wongchokprasitti, PhD (chw20@pitt.edu)
 * 
 */
public class HpcJobPreProcessTask implements Runnable {
	
	private final Logger LOGGER = LoggerFactory.getLogger(HpcJobPreProcessTask.class);

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
		final HpcAccountManager hpcAccountManager = desktop.getHpcAccountManager();
		final HpcJobManager hpcJobManager = desktop.getHpcJobManager();

		HpcAccount hpcAccount = hpcJobInfo.getHpcAccount();

		AlgorithmParamRequest algorParamReq = hpcJobInfo.getAlgorithmParamRequest();
		String datasetPath = algorParamReq.getDatasetPath();
		String priorKnowledgePath = algorParamReq.getPriorKnowledgePath();

		try {
			HpcAccountService hpcAccountService = hpcJobManager.getHpcAccountService(hpcAccount);

			HpcJobLog hpcJobLog = hpcJobManager.getHpcJobLog(hpcJobInfo);

			String log = "Initiated connection to " + hpcAccount.getConnectionName();
			LOGGER.debug(log);
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
				LOGGER.debug(log);
				prior = Paths.get(priorKnowledgePath);

				// Initiate prior knowledge uploading progress
				hpcJobManager.updateUploadFileProgress(priorKnowledgePath, 0);
			}

			// Check if this dataset already exists with this md5 hash
			RemoteDataFileService remoteDataService = hpcAccountService.getRemoteDataService();

			DataFile dataFile = HpcAccountUtils.getRemoteDataFile(hpcAccountManager, remoteDataService, hpcAccount,
					md5);
			DataUploadService dataUploadService = hpcAccountService.getDataUploadService();

			// If not, upload the file
			if (dataFile == null) {
				log = "Started uploading " + file.getFileName().toString();
				LOGGER.debug(log);
				dataUploadService.startUpload(file, HpcAccountUtils.getJsonWebToken(hpcAccountManager, hpcAccount));
				hpcJobManager.logHpcJobLogDetail(hpcJobLog, -1, log);

				int progress;
				while ((progress = dataUploadService.getUploadJobStatus(file.toAbsolutePath().toString())) < 100) {
					// System.out.println("Uploading "
					// + file.toAbsolutePath().toString() + " Progress: "
					// + progress + "%");
					hpcJobManager.updateUploadFileProgress(datasetPath, progress);
					Thread.sleep(10);
				}

				hpcJobManager.updateUploadFileProgress(datasetPath, progress);

				log = "Finished uploading " + file.getFileName().toString();
				LOGGER.debug(log);
				hpcJobManager.logHpcJobLogDetail(hpcJobLog, -1, log);

				// Get remote datafile
				dataFile = HpcAccountUtils.getRemoteDataFile(hpcAccountManager, remoteDataService, hpcAccount, md5);

				HpcAccountUtils.summarizeDataset(remoteDataService, algorParamReq, dataFile.getId(),
						HpcAccountUtils.getJsonWebToken(hpcAccountManager, hpcAccount));
				log = "Summarized " + file.getFileName().toString();
				LOGGER.debug(log);
				hpcJobManager.logHpcJobLogDetail(hpcJobLog, -1, log);
			} else {
				log = "Skipped uploading " + file.getFileName().toString();
				LOGGER.debug(log);

				hpcJobManager.updateUploadFileProgress(datasetPath, -1);

				hpcJobManager.logHpcJobLogDetail(hpcJobLog, -1, log);

				if (dataFile.getFileSummary().getVariableType() == null) {
					HpcAccountUtils.summarizeDataset(remoteDataService, algorParamReq, dataFile.getId(),
							HpcAccountUtils.getJsonWebToken(hpcAccountManager, hpcAccount));
					log = "Summarized " + file.getFileName().toString();
					LOGGER.debug(log);
					hpcJobManager.logHpcJobLogDetail(hpcJobLog, -1, "Summarized " + file.getFileName().toString());
				}

			}

			DataFile priorKnowledgeFile = null;

			// Prior Knowledge File
			if (prior != null) {
				// Get prior knowledge file Id
				md5 = algorParamReq.getPriorKnowledgeMd5();

				priorKnowledgeFile = HpcAccountUtils.getRemotePriorKnowledgeFile(hpcAccountManager, remoteDataService,
						hpcAccount, md5);

				if (priorKnowledgeFile == null) {
					// Upload prior knowledge file
					dataUploadService.startUpload(prior,
							HpcAccountUtils.getJsonWebToken(hpcAccountManager, hpcAccount));

					log = "Started uploading Prior Knowledge File";
					LOGGER.debug(log);
					hpcJobManager.logHpcJobLogDetail(hpcJobLog, -1, log);

					int progress;
					while ((progress = dataUploadService.getUploadJobStatus(prior.toAbsolutePath().toString())) < 100) {
						hpcJobManager.updateUploadFileProgress(priorKnowledgePath, progress);
						Thread.sleep(10);
					}

					hpcJobManager.updateUploadFileProgress(priorKnowledgePath, progress);

					priorKnowledgeFile = HpcAccountUtils.getRemotePriorKnowledgeFile(hpcAccountManager,
							remoteDataService, hpcAccount, md5);

					log = "Finished uploading Prior Knowledge File";
					LOGGER.debug(log);
					hpcJobManager.logHpcJobLogDetail(hpcJobLog, -1, log);

				}

			}

			// Algorithm Job Preparation
			edu.pitt.dbmi.ccd.rest.client.dto.algo.AlgorithmParamRequest paramRequest = new edu.pitt.dbmi.ccd.rest.client.dto.algo.AlgorithmParamRequest();
			String algoId = hpcJobInfo.getAlgoId();
			paramRequest.setAlgoId(algoId);
			paramRequest.setDatasetFileId(dataFile.getId());
			//Test
			if(algorParamReq.getTestId() != null){
				paramRequest.setTestId(algorParamReq.getTestId());
			}
			//Score
			if(algorParamReq.getScoreId() != null){
				paramRequest.setScoreId(algorParamReq.getScoreId());
			}
			

			Set<AlgoParameter> algorithmParameters = new HashSet<>();
			for (AlgorithmParameter param : algorParamReq.getAlgorithmParameters()) {
				algorithmParameters.add(new AlgoParameter(param.getParameter(), param.getValue()));
				LOGGER.debug("AlgorithmParameter: " + param.getParameter() + " : " + param.getValue());
			}

			if (priorKnowledgeFile != null) {
				paramRequest.setPriorKnowledgeFileId(priorKnowledgeFile.getId());
				LOGGER.debug("priorKnowledgeFileId: " + priorKnowledgeFile.getId());
			}
			paramRequest.setAlgoParameters(algorithmParameters);

			if(algorParamReq.getJvmOptions() != null){
				JvmOptions jvmOptions = new JvmOptions();
				jvmOptions.setMaxHeapSize(algorParamReq.getJvmOptions().getMaxHeapSize());
				paramRequest.setJvmOptions(jvmOptions);
			}
			
			Set<HpcParameter> hpcParameters = algorParamReq.getHpcParameters();
			if(hpcParameters != null){
				Set<edu.pitt.dbmi.ccd.rest.client.dto.algo.HpcParameter> hpcParams = new HashSet<>();
				for(HpcParameter param : hpcParameters){
					edu.pitt.dbmi.ccd.rest.client.dto.algo.HpcParameter hpcParam = new edu.pitt.dbmi.ccd.rest.client.dto.algo.HpcParameter();
					hpcParam.setKey(param.getKey());
					hpcParam.setValue(param.getValue());
					hpcParams.add(hpcParam);
					LOGGER.debug("HpcParameter: " + hpcParam.getKey() + " : " + hpcParam.getValue());
				}
				paramRequest.setHpcParameters(hpcParams);
			}
			
			// Submit a job
			JobQueueService jobQueueService = hpcAccountService.getJobQueueService();
			JobInfo jobInfo = jobQueueService.addToRemoteQueue(paramRequest,
					HpcAccountUtils.getJsonWebToken(hpcAccountManager, hpcAccount));

			// Log the job submission
			hpcJobInfo.setSubmittedTime(new Date(System.currentTimeMillis()));
			hpcJobInfo.setStatus(0); // Submitted
			hpcJobInfo.setPid(jobInfo.getId());
			hpcJobInfo.setResultFileName(jobInfo.getResultFileName());
			hpcJobInfo.setResultJsonFileName(jobInfo.getResultJsonFileName());
			hpcJobInfo.setErrorResultFileName(jobInfo.getErrorResultFileName());

			hpcJobManager.updateHpcJobInfo(hpcJobInfo);

			log = "Submitted job to " + hpcAccount.getConnectionName();
			LOGGER.debug(log);
			hpcJobManager.logHpcJobLogDetail(hpcJobLog, 0, log);

			LOGGER.debug(
					"HpcJobPreProcessTask: HpcJobInfo: id : " + hpcJobInfo.getId() + " : pid : " + hpcJobInfo.getPid()
							+ " : " + hpcJobInfo.getAlgoId()
							+ hpcJobInfo.getAlgorithmParamRequest().getTestId() == null?"":" : " + hpcJobInfo.getAlgorithmParamRequest().getTestId()
							+ hpcJobInfo.getAlgorithmParamRequest().getScoreId() == null?"":" : " + hpcJobInfo.getAlgorithmParamRequest().getScoreId()
							+ " : " + hpcJobInfo.getResultFileName());

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
