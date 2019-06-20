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
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TetradLogger;
import edu.pitt.dbmi.cg.CgIm;
import edu.pitt.dbmi.cg.CgPm;

/**
 * Jun 20, 2019 3:43:09 PM
 *
 * @author Chirayu Kong Wongchokprasitti, PhD (chw20@pitt.edu)
 *
 */
public class CgPmWrapper implements SessionModel {

	private static final long serialVersionUID = 1L;
    private int numModels = 1;
    private int modelIndex = 0;
    private String modelSourceName = null;

    /**
     * @serial Can be null.
     */
    private String name;

    private List<CgPm> cgPms;
    
    //==============================CONSTRUCTORS=========================//
    /**
     * Creates a new CgPm from the given DAG and uses it to construct a new
     * CgPm.
     */
    public CgPmWrapper(Graph graph, Parameters params) {
        if (graph == null) {
            throw new NullPointerException("Graph must not be null.");
        }

        int lowerBound, upperBound;

        if (params.getString("initializationMode", "manualRetain").equals("manual")) {
            lowerBound = upperBound = 2;
        } else if (params.getString("initializationMode", "manualRetain").equals("automatic")) {
            lowerBound = params.getInt("minCategories", 2);
            upperBound = params.getInt("maxCategories", 2);
        } else {
            throw new IllegalStateException("Unrecognized type.");
        }
    	setCgPm(graph, lowerBound, upperBound);
    }
    
    public CgPmWrapper(Graph graph, CgPm cgPm, Parameters params) {
        if (graph == null) {
            throw new NullPointerException("Graph must not be null.");
        }
        
        if(cgPm == null) {
        	throw new NullPointerException("CgPm must not be null");
        }

        int lowerBound, upperBound;

        if (params.getString("initializationMode", "manualRetain").equals("manual")) {
            lowerBound = upperBound = 2;
            setCgPm(new CgPm(graph, cgPm, lowerBound, upperBound));
        } else if (params.getString("initializationMode", "manualRetain").equals("automatic")) {
            lowerBound = params.getInt("minCategories", 2);
            upperBound = params.getInt("maxCategories", 2);
            setCgPm(graph, lowerBound, upperBound);
        } else {
            throw new IllegalStateException("Unrecognized type.");
        }
    	log(cgPm);
    }
    
    public CgPmWrapper(Simulation simulation) {
    	List<CgIm> cgIms = null;
    	
        if (simulation == null) {
            throw new NullPointerException("The Simulation box does not contain a simulation.");
        }

        edu.cmu.tetrad.algcomparison.simulation.Simulation _simulation = simulation.getSimulation();
        
        if (_simulation == null) {
            throw new NullPointerException("No data sets have been simulated.");
        }

        if(!(_simulation instanceof ConditionalGaussianSimulation)) {
        	throw new IllegalArgumentException("That was not a conditional Gaussian net simulation.");
        }
        
        cgIms = ((ConditionalGaussianSimulation)_simulation).getCgIms();
        
        if (cgIms == null) {
        	throw new NullPointerException("It looks like you have not done a simulation.");
        }
        
        List<CgPm> cgPms = new ArrayList<>();
        
        for(CgIm cgIm : cgIms) {
        	cgPms.add(cgIm.getCgPm());
        }
        
        this.cgPms = cgPms;
        
        this.numModels = simulation.getDataModelList().size();
        this.modelIndex = 0;
        this.modelSourceName = simulation.getName();
    }
    
    //=============================PUBLIC METHODS========================//
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

    public CgPm getCgPm() {
    	return cgPms.get(getModelIndex());
    }
    
    public Graph getGraph() {
        return getCgPm().getGraph();
    }

	@Override
	public void setName(String name) {
		this.name = name;
	}

	@Override
	public String getName() {
		return name;
	}
	
    //================================= Private Methods ==================================//
    private void setCgPm(Graph graph, int lowerBound, int upperBound) {
    	CgPm cgPm = new CgPm(graph, lowerBound, upperBound);
    	setCgPm(cgPm);
    }
    
    private void setCgPm(CgPm cgPm) {
    	this.cgPms = new ArrayList<>();
    	cgPms.add(cgPm);
    }
    
    private void log(CgPm cgPm) {
        TetradLogger.getInstance().log("info", "Conditional Gaussian Parametric Model (CG PM)");
        TetradLogger.getInstance().log("cgPm", cgPm.toString());

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
