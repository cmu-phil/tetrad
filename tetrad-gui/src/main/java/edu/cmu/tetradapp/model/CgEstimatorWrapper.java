/**
 * 
 */
package edu.cmu.tetradapp.model;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.List;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataModelList;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataUtils;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeType;
import edu.cmu.tetrad.session.SessionModel;
import edu.cmu.tetrad.util.TetradLogger;
import edu.pitt.dbmi.cg.CgEstimator;
import edu.pitt.dbmi.cg.CgIm;
import edu.pitt.dbmi.cg.CgPm;

/**
 * Jul 22, 2019 4:00:37 PM
 *
 * @author Chirayu Kong Wongchokprasitti, PhD (chw20@pitt.edu)
 *
 */
public class CgEstimatorWrapper implements SessionModel {

	private static final long serialVersionUID = 1L;

	/**
     * @serial Cannot be null.
     */
    private DataSet dataSet;
    private DataWrapper dataWrapper;
    
    private int numModels = 1;
    private int modelIndex = 0;

    /**
     * @serial Cannot be null.
     */
    private List<CgIm> cgIms;
    
    /**
     * @serial Cannot be null.
     */
    private String name;
    
    /**
     * @serial Cannot be null.
     */
    private CgIm cgIm;
    
    //=================================CONSTRUCTORS========================//
    public CgEstimatorWrapper(DataWrapper dataWrapper, CgPmWrapper cgPmWrapper) {
        if (dataWrapper == null) {
            throw new NullPointerException(
                    "CgDataWrapper must not be null.");
        }

        this.dataWrapper = dataWrapper;
        
        if (cgPmWrapper == null) {
        	throw new NullPointerException("CgPmWrapper must not be null");
        }
        
        DataModelList dataModel = dataWrapper.getDataModelList();
        
        if (dataModel != null) {
        	
        	cgIms = new ArrayList<>();
        	
        	for (int i = 0; i < dataWrapper.getDataModelList().size(); i++) {
        		DataModel model = dataWrapper.getDataModelList().get(i);
        		DataSet dataSet = (DataSet) model;
        		cgPmWrapper.setModelIndex(i);
        		CgPm cgPm = cgPmWrapper.getCgPm();
        		
        		estimate(dataSet, cgPm);
        		cgIms.add(this.cgIm);
        	}
        	
        	this.cgIm = cgIms.get(0);
        	log(cgIm);
        } else {
        	throw new IllegalArgumentException("Data must consist of mixed data sets.");
        }
    }
    
    public CgEstimatorWrapper(DataWrapper dataWrapper, CgImWrapper cgImWrapper) {
    	this(dataWrapper, new CgPmWrapper(cgImWrapper));
    }
    
    //==============================PUBLIC METHODS========================//
    public CgIm getEstimatedCgIm() {
    	return cgIm;
    }
    
    public void setCgIm(CgIm cgIm) {
    	cgIms.clear();
    	cgIms.add(cgIm);
    }
    
    public DataSet getDataSet() {
    	return dataSet;
    }
    
    public Graph getGraph() {
    	return cgIm.getCgPm().getGraph();
    }
    
    public int getNumModels() {
    	return numModels;
    }
    
	public void setNumModels(int numModels) {
		this.numModels = numModels;
	}

	public int getModelIndex() {
		return modelIndex;
	}

	public void setModelIndex(int modelIndex) {
		this.modelIndex = modelIndex;
		this.cgIm = cgIms.get(modelIndex);
		
		DataModel dataModel = dataWrapper.getDataModelList();
		
		this.dataSet = (DataSet)((DataModelList)dataModel).get(modelIndex);
	}
	
    @Override
	public void setName(String name) {
		this.name = name;
	}

	@Override
	public String getName() {
		return name;
	}

	//======================== Private Methods ======================//
    /**
     * Adds semantic checks to the default deserialization method. This method
     * must have the standard signature for a readObject method, and the body of
     * the method must begin with "s.defaultReadObject();". Other than that, any
     * semantic checks can be specified and do not need to stay the same from
     * version to version. A readObject method of this form may be added to any
     * class, even if Tetrad sessions were previously saved out using a version
     * of the class that didn't include it. (That's what the
     * "s.defaultReadObject();" is for. See J. Bloch, Effective Java, for help.
     *
     * @throws java.io.IOException
     * @throws ClassNotFoundException
     */
    private void readObject(ObjectInputStream s)
            throws IOException, ClassNotFoundException {
        s.defaultReadObject();

        if (cgIm == null) {
            throw new NullPointerException();
        }
    }

    private void log(CgIm im) {
        TetradLogger.getInstance().log("info", "ML estimated Conditional Gaussian IM.");
        TetradLogger.getInstance().log("im", im.toString());
    }
    
    private void estimate(DataSet dataSet, CgPm cgPm) {
    	Graph graph = cgPm.getGraph();
    	
    	for (Object o : graph.getNodes()) {
    		Node node = (Node) o;
    		if (node.getNodeType() == NodeType.LATENT) {
    			throw new IllegalArgumentException("Estimation of CG IM's "
                        + "with latents is not supported.");
    		}
    	}
    	
    	if (DataUtils.containsMissingValue(dataSet)) {
            throw new IllegalArgumentException("Please remove or impute missing values.");
        }
    	
        try {
        	CgEstimator estimator = new CgEstimator(cgPm, dataSet);
        	this.cgIm = estimator.getEstimatedCgIm();
        } catch (ArrayIndexOutOfBoundsException e) {
            e.printStackTrace();
            throw new RuntimeException("Value assignments between CG PM "
                    + "and mixed data set do not match.");
        }
    }
}
