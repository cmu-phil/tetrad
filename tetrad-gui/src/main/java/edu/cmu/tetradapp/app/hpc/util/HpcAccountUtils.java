package edu.cmu.tetradapp.app.hpc.util;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Set;

import org.apache.http.client.ClientProtocolException;

import edu.cmu.tetradapp.app.hpc.manager.HpcAccountManager;
import edu.pitt.dbmi.ccd.rest.client.dto.data.DataFile;
import edu.pitt.dbmi.ccd.rest.client.dto.user.JsonWebToken;
import edu.pitt.dbmi.ccd.rest.client.service.data.RemoteDataFileService;
import edu.pitt.dbmi.tetrad.db.entity.AlgorithmParamRequest;
import edu.pitt.dbmi.tetrad.db.entity.HpcAccount;

/**
 * 
 * Nov 2, 2016 1:37:49 PM
 * 
 * @author Chirayu (Kong) Wongchokprasitti, PhD
 * 
 */
public class HpcAccountUtils {

	public static boolean testConnection(final HpcAccountManager hpcAccountManager, final HpcAccount hpcAccount) {
		try {
			getJsonWebToken(hpcAccountManager, hpcAccount);

			return true;

		} catch (ClientProtocolException e) {
		} catch (URISyntaxException e) {
		} catch (IOException e) {
		} catch (Exception e) {
		}

		return false;
	}

	public static DataFile getRemoteDataFile(final HpcAccountManager hpcAccountManager,
			final RemoteDataFileService remoteDataService, final HpcAccount hpcAccount, String md5)
			throws ClientProtocolException, URISyntaxException, IOException, Exception {
		Set<DataFile> dataFiles = remoteDataService
				.retrieveDataFileInfo(getJsonWebToken(hpcAccountManager, hpcAccount));
		for (DataFile dataFile : dataFiles) {
			String remoteMd5 = dataFile.getMd5checkSum();
			if (md5.equalsIgnoreCase(remoteMd5)) {
				return dataFile;
			}
		}
		return null;
	}

	public static DataFile getRemotePriorKnowledgeFile(final HpcAccountManager hpcAccountManager,
			final RemoteDataFileService remoteDataService, final HpcAccount hpcAccount, String md5)
			throws ClientProtocolException, URISyntaxException, IOException, Exception {
		Set<DataFile> priorFiles = remoteDataService
				.retrievePriorKnowledgeFileInfo(getJsonWebToken(hpcAccountManager, hpcAccount));
		for (DataFile priorFile : priorFiles) {
			String remoteMd5 = priorFile.getMd5checkSum();
			if (md5.equalsIgnoreCase(remoteMd5)) {
				return priorFile;
			}
		}
		return null;
	}

	public static JsonWebToken getJsonWebToken(final HpcAccountManager hpcAccountManager, final HpcAccount hpcAccount)
			throws Exception {
		return hpcAccountManager.getJsonWebTokenManager().getJsonWebToken(hpcAccount);
	}

	public static void summarizeDataset(final RemoteDataFileService remoteDataService,
			final AlgorithmParamRequest algorParamReq, final long datasetFileId, final JsonWebToken jsonWebToken)
			throws ClientProtocolException, URISyntaxException, IOException {
		String variableType = "continuous";
		if (algorParamReq.getVariableType().equalsIgnoreCase("discrete")) {
			variableType = "discrete";
		}

		String fileDelimiter = "tab";
		if (!algorParamReq.getFileDelimiter().equalsIgnoreCase("tab")) {
			fileDelimiter = "comma";
		}

		remoteDataService.summarizeDataFile(datasetFileId, variableType, fileDelimiter, jsonWebToken);
	}

}
