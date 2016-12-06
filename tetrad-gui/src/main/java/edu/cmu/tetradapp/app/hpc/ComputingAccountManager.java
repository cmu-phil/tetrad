package edu.cmu.tetradapp.app.hpc;

import java.util.List;

import edu.pitt.dbmi.tetrad.db.entity.ComputingAccount;
import edu.pitt.dbmi.tetrad.db.service.ComputingAccountService;

/**
 * 
 * Oct 31, 2016 1:50:12 PM
 * 
 * @author Chirayu (Kong) Wongchokprasitti, PhD
 * 
 */
public class ComputingAccountManager {

    private final ComputingAccountService computingAccountService;

    public ComputingAccountManager(
	    final ComputingAccountService computingAccountService) {
	this.computingAccountService = computingAccountService;
    }

    public List<ComputingAccount> getComputingAccounts() {
	List<ComputingAccount> computingAccounts = computingAccountService
		.get();
	return computingAccounts;
    }
    
    public void saveAccount(final ComputingAccount computingAccount) {
	computingAccountService.update(computingAccount);
    }

    public void removeAccount(final ComputingAccount computingAccount) {
	computingAccountService.remove(computingAccount);
    }
    
}
