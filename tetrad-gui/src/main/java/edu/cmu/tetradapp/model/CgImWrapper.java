/**
 * 
 */
package edu.cmu.tetradapp.model;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.List;

import edu.cmu.tetrad.algcomparison.simulation.ConditionalGaussianSimulation;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.session.SessionModel;
import edu.cmu.tetrad.util.Memorable;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TetradSerializableUtils;
import edu.pitt.dbmi.cg.CgIm;
import edu.pitt.dbmi.cg.CgPm;

/**
 * Jul 2, 2019 2:53:35 PM
 *
 * @author Chirayu Kong Wongchokprasitti, PhD (chw20@pitt.edu)
 *
 */
public class CgImWrapper implements SessionModel, Memorable {

	private static final long serialVersionUID = 1L;

	private int numModels = 1;
    private int modelIndex = 0;
    private String modelSourceName = null;

    /**
     * @serial Can be null.
     */
    private String name;

    /**
     * @serial Cannot be null.
     */
    private List<CgIm> cgIms;
    
    //===========================CONSTRUCTORS===========================//
    public CgImWrapper(CgPmWrapper cgPmWrapper, CgImWrapper oldCgImWrapper, Parameters params) {
    	if(cgPmWrapper == null) {
    		throw new NullPointerException("CgPmWrapper must not be null.");
    	}
    	
    	if(params == null) {
    		throw new NullPointerException("Parameters must not be null.");
    	}
    	
    	CgPm cgPm = new CgPm(cgPmWrapper.getCgPm());
    	CgIm oldCgIm = oldCgImWrapper.getCgIm();
    	
    	String initModeParam = params.getString("initializationMode", "manualRetain");
    	if (initModeParam.equalsIgnoreCase("manualRetain")) {
    		setCgIm(cgPm, oldCgIm, CgIm.MANUAL);
    	} else if (initModeParam.equalsIgnoreCase("randomRetain")) {
    		setCgIm(cgPm, oldCgIm, CgIm.RANDOM);
    	} else if (initModeParam.equalsIgnoreCase("randomOverwrite")) {
    		setCgIm(new CgIm(cgPm, CgIm.RANDOM));
    	}
    }
    
    private void setCgIm(CgPm cgPm, CgIm oldCgIm, int manual) {
    	//System.out.println("setCgIm(CgPm cgPm, CgIm oldCgIm, int manual)");
    	
    	cgIms = new ArrayList<>();
    	cgIms.add(new CgIm(cgPm, oldCgIm, manual));
    }
    
    public CgImWrapper(Simulation simulation) {
    	//System.out.println("CgImWrapper(Simulation simulation)");
    	
    	List<CgIm> cgIms = null;
    	
    	if (simulation == null) {
            throw new NullPointerException("The Simulation box does not contain a simulation.");
        }

        edu.cmu.tetrad.algcomparison.simulation.Simulation _simulation = simulation.getSimulation();

        if (_simulation == null) {
            throw new NullPointerException("No data sets have been simulated.");
        }

        if (!(_simulation instanceof ConditionalGaussianSimulation)) {
            throw new IllegalArgumentException("That was not a conditional Gaussian net simulation.");
        }
        
        cgIms = ((ConditionalGaussianSimulation) _simulation).getCgIms();
        
        if(cgIms == null) {
        	throw new NullPointerException("It looks like you have not done a simulation.");
        }
        
        this.cgIms = cgIms;
        
        this.numModels = simulation.getDataModelList().size();
        this.modelIndex = 0;
        this.modelSourceName = simulation.getName();
    }
    
    //public CgImWrapper(CgEstimatorWrapper wrapper, Parameters parameters) {
    	
    //}
    
    public CgImWrapper(CgPmWrapper cgPmWrapper, Parameters params) {
    	//System.out.println("CgImWrapper(CgPmWrapper cgPmWrapper, Parameters params)");
    	
    	if(cgPmWrapper == null) {
    		throw new NullPointerException("CgPmWrapper must not be null.");
    	}
    	
    	if(params == null) {
    		throw new NullPointerException("Parameters must not be null.");
    	}
    	
    	CgPm cgPm = new CgPm(cgPmWrapper.getCgPm());
    	
    	String initModeParam = params.getString("initializationMode", "manualRetain");
    	//System.out.println("params.getString(\"initializationMode\", \"manualRetain\"): " + initModeParam);
    	if (initModeParam.equalsIgnoreCase("manualRetain")) {
    		setCgIm(new CgIm(cgPm, CgIm.MANUAL));
    	} else if (initModeParam.equalsIgnoreCase("randomRetain")) {
    		setCgIm(new CgIm(cgPm, CgIm.RANDOM));
    	} else if (initModeParam.equalsIgnoreCase("randomOverwrite")) {
    		setCgIm(new CgIm(cgPm, CgIm.RANDOM));
    	}
    }
    
    public CgImWrapper(CgImWrapper cgImWrapper) {
    	//System.out.println("CgImWrapper(CgImWrapper cgImWrapper)");
    	
    	if(cgImWrapper == null) {
    		throw new NullPointerException();
    	}
    	
    	setCgIm(new CgIm(cgImWrapper.getCgIm()));
    }
    
    public CgImWrapper(CgIm cgIm) {
    	//System.out.println("CgImWrapper(CgIm cgIm)");
    	
    	if(cgIm == null) {
    		throw new NullPointerException("CG IM must not be null.");
    	}
    	
    	setCgIm(new CgIm(cgIm));
    }
    
    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @see TetradSerializableUtils
     */
    public static CgImWrapper serializableInstance() {
    	return new CgImWrapper(CgPmWrapper.serializableInstance(), new Parameters());
    }
    
    //=============================PUBLIC METHODS=========================//
    public CgIm getCgIm() {
    	return cgIms.get(getModelIndex());
    }
    
    public Graph getGraph() {
    	return getCgIm().getCgPm().getGraph();
    }
	@Override
	public void setName(String name) {
		this.name = name;
	}

	@Override
	public String getName() {
		return name;
	}
	
    public Graph getSourceGraph() {
        return getGraph();
    }

    public Graph getResultGraph() {
        return getGraph();
    }

    public List<String> getVariableNames() {
        return getGraph().getNodeNames();
    }

    public List<Node> getVariables() {
        return getGraph().getNodes();
    }

    public void setCgIm(CgIm cgIm) {
    	cgIms = new ArrayList<>();
    	cgIms.add(cgIm);
    }
    
    public int getNumModels() {
        return numModels;
    }

    public int getModelIndex() {
        return modelIndex;
    }

    public String getModelSourceName() {
        return modelSourceName;
    }

    public void setModelIndex(int modelIndex) {
        this.modelIndex = modelIndex;
    }

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
    }

}
