package edu.cmu.tetradapp.app.hpc.manager;

import java.util.List;

import edu.pitt.dbmi.tetrad.db.entity.HpcAccount;
import edu.pitt.dbmi.tetrad.db.service.HpcAccountService;

/**
 * 
 * Oct 31, 2016 1:50:12 PM
 * 
 * @author Chirayu (Kong) Wongchokprasitti, PhD
 * 
 */
public class HpcAccountManager {

	private final HpcAccountService hpcAccountService;

	private final JsonWebTokenManager jsonWebTokenManager;

	public HpcAccountManager(final org.hibernate.Session session) {
		this.hpcAccountService = new HpcAccountService(session);
		this.jsonWebTokenManager = new JsonWebTokenManager();
	}

	public List<HpcAccount> getHpcAccounts() {
		List<HpcAccount> hpcAccounts = hpcAccountService.get();
		return hpcAccounts;
	}

	public void saveAccount(final HpcAccount hpcAccount) {
		hpcAccountService.update(hpcAccount);
	}

	public void removeAccount(final HpcAccount hpcAccount) {
		hpcAccountService.remove(hpcAccount);
	}

	public JsonWebTokenManager getJsonWebTokenManager() {
		return jsonWebTokenManager;
	}

}
