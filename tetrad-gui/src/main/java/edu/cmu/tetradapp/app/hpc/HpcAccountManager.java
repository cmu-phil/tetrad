package edu.cmu.tetradapp.app.hpc;

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

    public HpcAccountManager(final org.hibernate.Session session) {
	this.hpcAccountService = new HpcAccountService(session);
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

}
