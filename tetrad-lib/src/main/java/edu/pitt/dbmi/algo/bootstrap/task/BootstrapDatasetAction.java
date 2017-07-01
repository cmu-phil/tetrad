package edu.pitt.dbmi.algo.bootstrap.task;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.RecursiveAction;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataUtils;
import edu.pitt.dbmi.algo.bootstrap.BootstrapSearch;

/**
 * 
 * Mar 19, 2017 11:38:36 PM
 * 
 * @author Chirayu (Kong) Wongchokprasitti, PhD
 * 
 */
public class BootstrapDatasetAction extends RecursiveAction {

    private static final long serialVersionUID = 2789430191040611462L;

    private int workLoad = -1;
    
    private DataSet dataSet;
    
    private BootstrapSearch bootstrapSearch;
    
    public BootstrapDatasetAction(int workLoad, DataSet dataSet, BootstrapSearch bootstrapSearch){
	this.workLoad = workLoad;
	this.dataSet = dataSet;
	this.bootstrapSearch = bootstrapSearch;
    }
    
    @Override
    protected void compute() {
	BootstrapDatasetAction task1 = null, task2 = null;
	if(workLoad < 2){
	    DataSet samplingDataSet = DataUtils.getBootstrapSample(dataSet,
		    dataSet.getNumRows());
	    bootstrapSearch.addBootstrapDataset(samplingDataSet);
	}else{
	    task1 = new BootstrapDatasetAction(workLoad / 2, dataSet, bootstrapSearch);
	    task2 = new BootstrapDatasetAction(workLoad - workLoad / 2, dataSet, bootstrapSearch);
	    
	    List<BootstrapDatasetAction> tasks = new ArrayList<>();
	    tasks.add(task1);
	    tasks.add(task2);
	    invokeAll(tasks);
	}
    }

}
