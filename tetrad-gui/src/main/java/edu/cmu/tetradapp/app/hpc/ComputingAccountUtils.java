package edu.cmu.tetradapp.app.hpc;

import java.io.IOException;
import java.net.URISyntaxException;

import org.apache.http.client.ClientProtocolException;

import edu.pitt.dbmi.ccd.rest.client.RestHttpsClient;
import edu.pitt.dbmi.ccd.rest.client.dto.user.JsonWebToken;
import edu.pitt.dbmi.ccd.rest.client.service.user.UserService;
import edu.pitt.dbmi.tetrad.db.entity.ComputingAccount;

/**
 * 
 * Nov 2, 2016 1:37:49 PM
 * 
 * @author Chirayu (Kong) Wongchokprasitti, PhD
 * 
 */
public class ComputingAccountUtils {

    public static boolean testConnection(final ComputingAccount computingAccount) {
	final String username = computingAccount.getUsername();
	final String password = computingAccount.getPassword();
	final String scheme = computingAccount.getScheme();
	final String hostname = computingAccount.getHostname();
	final int port = computingAccount.getPort();

	try {
	    RestHttpsClient restClient = new RestHttpsClient(username,
		    password, scheme, hostname, port);

	    UserService userService = new UserService(restClient, scheme,
		    hostname, port);
	    userService.requestJWT();

	    return true;

	} catch (ClientProtocolException e) {
	} catch (URISyntaxException e) {
	} catch (IOException e) {
	} catch (Exception e) {
	}

	return false;
    }

}
