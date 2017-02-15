package edu.cmu.tetradapp.app.hpc.manager;

import edu.pitt.dbmi.ccd.rest.client.RestHttpsClient;
import edu.pitt.dbmi.ccd.rest.client.service.data.DataUploadService;
import edu.pitt.dbmi.ccd.rest.client.service.data.RemoteDataFileService;
import edu.pitt.dbmi.ccd.rest.client.service.jobqueue.JobQueueService;
import edu.pitt.dbmi.ccd.rest.client.service.result.ResultService;
import edu.pitt.dbmi.tetrad.db.entity.HpcAccount;

/**
 * 
 * Jan 24, 2017 12:49:26 PM
 * 
 * @author Chirayu (Kong) Wongchokprasitti, PhD
 * 
 */
public class HpcAccountService {

	private final RemoteDataFileService remoteDataService;

	private final DataUploadService dataUploadService;

	private final JobQueueService jobQueueService;

	private final ResultService resultService;

	public HpcAccountService(final HpcAccount hpcAccount, final int simultaneousUpload) throws Exception {

		final String username = hpcAccount.getUsername();
		final String password = hpcAccount.getPassword();
		final String scheme = hpcAccount.getScheme();
		final String hostname = hpcAccount.getHostname();
		final int port = hpcAccount.getPort();

		RestHttpsClient restHttpsClient = new RestHttpsClient(username, password, scheme, hostname, port);

		this.remoteDataService = new RemoteDataFileService(restHttpsClient, scheme, hostname, port);

		this.dataUploadService = new DataUploadService(restHttpsClient, simultaneousUpload, scheme, hostname, port);

		this.jobQueueService = new JobQueueService(restHttpsClient, scheme, hostname, port);

		this.resultService = new ResultService(restHttpsClient, scheme, hostname, port);

	}

	public RemoteDataFileService getRemoteDataService() {
		return remoteDataService;
	}

	public DataUploadService getDataUploadService() {
		return dataUploadService;
	}

	public JobQueueService getJobQueueService() {
		return jobQueueService;
	}

	public ResultService getResultService() {
		return resultService;
	}

}
