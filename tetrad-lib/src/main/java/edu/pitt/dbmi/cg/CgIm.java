/**
 * 
 */
package edu.pitt.dbmi.cg;

import java.io.IOException;
import java.rmi.MarshalledObject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import edu.cmu.tetrad.bayes.BayesIm;
import edu.cmu.tetrad.bayes.MlBayesIm;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.sem.ConnectionFunction;
import edu.cmu.tetrad.sem.ISemIm;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.util.IM;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TetradMatrix;
import edu.cmu.tetrad.util.TetradSerializable;
import edu.cmu.tetrad.util.dist.Distribution;

/**
 * Stores an instantiated conditional Gaussian model (CG).
 * 
 * 
 * Apr 10, 2019 4:03:52 PM
 *
 * @author Chirayu Kong Wongchokprasitti, PhD (chw20@pitt.edu)
 *
 */
public final class CgIm implements IM, ICgIm, TetradSerializable {

	private static final long serialVersionUID = 1L;

	/**
	 * Indicates that new rows in this BayesIm should be initialized as unknowns,
	 * forcing them to be specified manually. This is the default.
	 */
	public static final int MANUAL = 0;

	/**
	 * Indicates that new rows in this BayesIm should be initialized randomly.
	 */
	public static final int RANDOM = 1;

	private final CgPm cgPm;
	
	private final BayesIm bayesIm;
	
	private final ISemIm semIm;

	/**
	 * True iff this SemIm is estimated.
	 *
	 * @serial Any value.
	 */
	private boolean estimated = false;

	/**
	 * Parameters to help guide how values are chosen for freeParameters.
	 */
	private Parameters params = new Parameters();

	/**
	 * Stores a distribution for each variable. Initialized to N(0, 1) for each.
	 */
	private Map<Node, Distribution> distributions;

	/**
	 * Stores the connection functions of specified nodes.
	 */
	private Map<Node, ConnectionFunction> functions;

	/**
	 * True iff only positive data should be simulated.
	 */
	private boolean simulatedPositiveDataOnly = false;

	private Map<Node, Integer> variablesHash;
	private TetradMatrix sampleCovInv;
	private static Collection<? extends String> parameterNames;

	// Mixed parents to discrete child nodes
	private Node[] mixedParentsDiscreteNodes;

	private int[][] discreteNodesMixedParents;

	private int[][] discreteNodesMixedParentDims;

	private double[][][] mixedParentsdiscreteNodePMFs;

	public static List<String> getParameterNames() {
		List<String> parameters = new ArrayList<>();
		parameters.add("coefLow");
		parameters.add("coefHigh");
		parameters.add("covLow");
		parameters.add("covHigh");
		parameters.add("varLow");
		parameters.add("varHigh");
		parameters.add("coefSymmetric");
		parameters.add("covSymmetric");
		return parameters;
	}

	// =============================CONSTRUCTORS============================//
	/**
	 * Constructs a new CG IM from a CG PM.
	 */
	public CgIm(CgPm cgPm) {
		this(cgPm, null, new Parameters(), MANUAL);
	}

	public CgIm(CgPm cgPm, Parameters parameters, int initializationMethod) {
		this(cgPm, null, parameters, initializationMethod);
	}

	public CgIm(CgPm cgPm, CgIm oldCgIm, Parameters parameters, int initializationMethod) {
		if (cgPm == null) {
			throw new NullPointerException("CG PM must not be null.");
		}

		if (params == null) {
			throw new NullPointerException();
		}

		this.params = parameters;

		this.cgPm = new CgPm(cgPm);
		
		this.bayesIm = oldCgIm.getBayesIm();
		this.semIm = oldCgIm.getSemIm();
		
		// Initialize mixed-parents discrete children
		this.mixedParentsDiscreteNodes = cgPm.getDiscChildrenNodesToVariables()
							.keySet()
							.toArray(new Node[cgPm.getDiscChildrenNodesToVariables().size()]);
		
		// Initialize mixed-parents continuous children
		
		initializeValues(oldCgIm, initializationMethod);
	}

	public CgIm(CgIm cgIm) throws IllegalArgumentException {
		if(cgIm == null) {
			throw new NullPointerException("CG IM must not be null.");
		}
		
		this.cgPm = cgIm.getCgPm();
		
		this.bayesIm = cgIm.getBayesIm();
		this.semIm = cgIm.getSemIm();
		
		this.mixedParentsDiscreteNodes = new Node[cgIm.getNumMixedParentsDiscreteNodes()];
		
		for(int i=0;i<cgIm.getNumMixedParentsDiscreteNodes();i++) {
			this.mixedParentsDiscreteNodes[i] = cgIm.getMixedParentsDiscreteNode(i);
		}
	}

	/**
	 * Iterates through all freeParameters, picking values for them from the
	 * distributions that have been set for them.
	 */
	private void initializeValues(CgIm oldCgIm, int initializationMethod) {
		discreteNodesMixedParents = new int[this.mixedParentsDiscreteNodes.length][];
		discreteNodesMixedParentDims = new int[this.mixedParentsDiscreteNodes.length][];
		mixedParentsdiscreteNodePMFs = new double[this.mixedParentsDiscreteNodes.length][][];

		for (int nodeIndex = 0; nodeIndex < this.mixedParentsDiscreteNodes.length; nodeIndex++) {
			initializeNode(nodeIndex, oldCgIm, initializationMethod);
		}
	}

	/**
	 * This method initializes the node indicated.
	 */
	private void initializeNode(int nodeIndex, CgIm oldCgIm, int initializationMethod) {
		Node node = mixedParentsDiscreteNodes[nodeIndex];

		Graph graph = getCgPm().getGraph();
		List<Node> parentList = graph.getParents(node);
		int[] parentArray = new int[parentList.size()];
	}

    /**
     * @param node the given node.
     * @return the index for that node, or -1 if the node is not in the
     * BayesIm.
     */
    public int getNodeIndex(Node node) {
        for (int i = 0; i < mixedParentsDiscreteNodes.length; i++) {
            if (node == mixedParentsDiscreteNodes[i]) {
                return i;
            }
        }

        return -1;
    }
	
	@Override
	public DataSet simulateData(int sampleSize, boolean latentDataSaved) {
		return null;
	}

	@Override
	public DataSet simulateData(int sampleSize, long sampleSeed, boolean latentDataSaved) {
		return null;
	}

	public CgPm getCgPm() {
		return cgPm;
	}

	public BayesIm getBayesIm() {
		return bayesIm;
	}

	public ISemIm getSemIm() {
		return semIm;
	}

	public Graph getDag() {
		return cgPm.getGraph();
	}
	
	public int getNumMixedParentsDiscreteNodes() {
		return mixedParentsDiscreteNodes.length;
	}
	
	public Node getMixedParentsDiscreteNode(int nodeIndex) {
		return mixedParentsDiscreteNodes[nodeIndex];
	}

	/**
     * @param name the name of the node.
     * @return the node.
     */
    public Node getNode(String name) {
        return getDag().getNode(name);
    }
    
    public int getMixedParentsDiscreteNodeIndex(Node node) {
    	for (int i = 0; i < mixedParentsDiscreteNodes.length; i++) {
            if (node == mixedParentsDiscreteNodes[i]) {
                return i;
            }
        }

        return -1;
    }
    
    public List<Node> getMixedParentsDiscreteVariables() {
    	return Arrays.asList(mixedParentsDiscreteNodes);
    }
    
    public List<String> getMixedParentsDiscreteVariableNames(){
    	List<String> variableNames = new LinkedList<>();

        for (int i = 0; i < getNumMixedParentsDiscreteNodes(); i++) {
            Node node = getMixedParentsDiscreteNode(i);
            variableNames.add(cgPm.getDiscChildrenNodesToVariables().get(node).getName());
        }

        return variableNames;
    }
}
