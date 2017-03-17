package edu.cmu.tetradapp.app.hpc.manager;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import edu.pitt.dbmi.ccd.rest.client.RestHttpsClient;
import edu.pitt.dbmi.ccd.rest.client.dto.user.JsonWebToken;
import edu.pitt.dbmi.ccd.rest.client.service.user.UserService;
import edu.pitt.dbmi.tetrad.db.entity.HpcAccount;

/**
 * 
 * Jan 18, 2017 11:14:37 AM
 * 
 * @author Chirayu (Kong) Wongchokprasitti, PhD
 * 
 */
public class JsonWebTokenManager {

	private final Map<HpcAccount, JsonWebToken> jsonWebTokenMap;

	private final Map<HpcAccount, Date> jsonWebTokenRequestTimeMap;

	private final static long TOKEN_VALID_TIME = 60 * 60 * 1000;// 1-hour

	public JsonWebTokenManager() {
		jsonWebTokenMap = new HashMap<>();
		jsonWebTokenRequestTimeMap = new HashMap<>();
	}

	public synchronized JsonWebToken getJsonWebToken(final HpcAccount hpcAccount) throws Exception {
		long now = System.currentTimeMillis();
		JsonWebToken jsonWebToken = jsonWebTokenMap.get(hpcAccount);
		if (jsonWebToken == null || (now - jsonWebTokenRequestTimeMap.get(hpcAccount).getTime()) > TOKEN_VALID_TIME) {

			final String username = hpcAccount.getUsername();
			final String password = hpcAccount.getPassword();
			final String scheme = hpcAccount.getScheme();
			final String hostname = hpcAccount.getHostname();
			final int port = hpcAccount.getPort();

			RestHttpsClient restClient = new RestHttpsClient(username, password, scheme, hostname, port);

			// Authentication
			UserService userService = new UserService(restClient, scheme, hostname, port);
			// JWT token is valid for 1 hour
			jsonWebToken = userService.requestJWT();
			jsonWebTokenMap.put(hpcAccount, jsonWebToken);
			jsonWebTokenRequestTimeMap.put(hpcAccount, new Date(System.currentTimeMillis()));
		}
		return jsonWebToken;
	}

}
