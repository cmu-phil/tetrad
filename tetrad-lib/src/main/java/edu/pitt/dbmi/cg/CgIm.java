/**
 * 
 */
package edu.pitt.dbmi.cg;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import edu.cmu.tetrad.bayes.BayesIm;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.sem.ConnectionFunction;
import edu.cmu.tetrad.sem.ISemIm;
import edu.cmu.tetrad.sem.ParamType;
import edu.cmu.tetrad.util.IM;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.RandomUtil;
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
	 * Indicates that new rows in this CgIm should be initialized as unknowns,
	 * forcing them to be specified manually. This is the default.
	 */
	public static final int MANUAL = 0;

	/**
	 * Indicates that new rows in this CgIm should be initialized randomly.
	 */
	public static final int RANDOM = 1;

	private final CgPm cgPm;
	
	private BayesIm bayesIm;
	
	private ISemIm semIm;

	// Mixed parents with continuous child
	private final List<Node> cgContinuousVariableNodes;
	
	private final List<Node> cgContinuousMeasuredNodes;
	
	private List<CgParameter> continuousFreeParameters;
	
	private List<CgParameter> continuousFixedParameters;
	
	private List<CgMapping> continuousFreeMappings;
	
	private List<CgMapping> continuousFixedMappings;
	
    // Mixed parents with discrete child
	private final List<Node> cgDiscreteVariableNodes;
	
	private List<CgParameter> discreteFreeParameters;
	
	private List<CgParameter> discreteFixedParameters;
	
	private List<CgMapping> discreteFreeMappings;
	
	private List<CgMapping> discreteFixedMappings;
	
	/**
	 * True iff this CgIm is estimated.
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
	private Map<Node, Map<Integer, Distribution>> distributions;

	/**
	 * Stores the connection functions of specified nodes.
	 */
	private Map<Node, Map<Integer, ConnectionFunction>> functions;

	/**
	 * True iff only positive data should be simulated.
	 */
	private boolean simulatedPositiveDataOnly = false;

	private Map<Node, Integer> variablesHash;
	private TetradMatrix sampleCovInv;
	private static Collection<? extends String> parameterNames;

    /**
     * True iff setting freeParameters to out-of-bound values throws exceptions.
     *
     * @serial Any value.
     */
    private boolean parameterBoundsEnforced = true;
	
	// Discrete child nodes with mixed parents
	private Node[] cgDiscreteNodes;
	private Node[] cgDiscreteNodeDiscreteParentNodes;
	private Node[] cgDiscreteNodeContinuousParentNodes;
	
	private Map<Node,Integer> cgDiscreteNodeIndex;
	private Map<Node,Integer> cgDiscreteNodeDiscreteParentNodeIndex;
	private Map<Node,Integer> cgDiscreteNodeContinuousParentNodeIndex;
	
	// Continuous child nodes with mixed parents
	private Node[] cgContinuousNodes;
	private Node[] cgContinuousNodeDiscreteParentNodes;
	private Node[] cgContinuousNodeContinuousParentNodes;
	
	private Map<Node,Integer> cgContinuousNodeIndex;
	private Map<Node,Integer> cgContinuousNodeDiscreteParentNodeIndex;
	private Map<Node,Integer> cgContinuousNodeContinuousParentNodeIndex;

	private int[][] cgDiscreteNodeDiscreteParentArray;
	private int[][] cgDiscreteNodeContinuousParentArray;

	private int[][] cgContinuousNodeDiscreteParentArray;
	private int[][] cgContinuousNodeContinuousParentArray;

	private int[][] cgDiscreteNodeDims;
	private int[][] cgContinuousNodeDims;

	// nodeIndex, discreteParentConditionIndex, nodeCategoryIndex, continuousParentNodeIndex (= 0)
	private double[][][][] cgDiscreteNodeProbs;
	// nodeIndex, discreteParentConditionIndex, nodeCategoryIndex, continuousParentNodeIndex
	private double[][][][] cgDiscreteNodeErrCovar;
	private double[][][][] cgDiscreteNodeMeans;
	private double[][][][] cgDiscreteNodeMeanStdDevs;
	
	// nodeIndex, discreteParentConditionIndex, nodeCategoryIndex (= 0), continuousParentNodeIndex
	private double[][][][] cgContinuousNodeEdgeCoef;
	private double[][][][] cgContinuousNodeErrCovar;
	private double[][][][] cgContinuousNodeMeans;
	private double[][][][] cgContinuousNodeMeanStdDevs;

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
		
		this.cgContinuousVariableNodes = Collections.unmodifiableList(cgPm.getCgContinuousVariableNodes());
		this.cgContinuousMeasuredNodes = Collections.unmodifiableList(cgPm.getCgContinuousMeasureNodes());
		this.cgDiscreteVariableNodes = Collections.unmodifiableList(cgPm.getCgDiscreteVariableNodes());
		
		this.bayesIm = oldCgIm.getBayesIm();
		this.semIm = oldCgIm.getSemIm();
		
		this.continuousFreeParameters = initContinuousFreeParameters();
		this.continuousFreeMappings = createContinuousMappings(continuousFreeParameters);
		this.continuousFixedParameters = initContinuousFixedParameters();
		this.continuousFixedMappings = createContinuousMappings(continuousFixedParameters);
		
		this.discreteFreeParameters = initDiscreteFreeParameters();
		this.discreteFreeMappings = createDiscreteMappings(discreteFreeParameters);
		this.discreteFixedParameters = initDiscreteFixedParameters();
		this.discreteFixedMappings = createDiscreteMappings(discreteFixedParameters);

		Graph graph = getCgPm().getGraph();
		
		// Initialize mixed-parents discrete children
		this.cgDiscreteNodes = cgPm.getCgDiscreteVariableNodes()
							.toArray(new Node[cgPm.getCgDiscreteVariableNodes().size()]);

		for(int i=0;i<cgDiscreteNodes.length;i++) {
			Node node = cgDiscreteNodes[i];
			cgDiscreteNodeIndex.put(node, i);
		}
		
		List<Node> discreteParentList = new ArrayList<>();
		List<Node> continuousParentList = new ArrayList<>();
		
		for(int i=0;i<cgDiscreteNodes.length;i++) {
			Node node = cgDiscreteNodes[i];
			
			List<Node> parentList = graph.getParents(node);
			
			for(Node pNode : parentList) {
				if(pNode instanceof DiscreteVariable) {
					if(!discreteParentList.contains(pNode)) {
						discreteParentList.add(pNode);
					}
				}else {
					if(!continuousParentList.contains(pNode)) {
						continuousParentList.add(pNode);
					}
				}
			}
		}
		
		Collections.sort(discreteParentList);
		Collections.sort(continuousParentList);
 		
 		this.cgDiscreteNodeDiscreteParentNodes = discreteParentList.toArray(new Node[discreteParentList.size()]);
 		this.cgDiscreteNodeContinuousParentNodes = continuousParentList.toArray(new Node[continuousParentList.size()]);
		
		for(int i=0;i<cgDiscreteNodeDiscreteParentNodes.length;i++) {
			Node node = cgDiscreteNodeDiscreteParentNodes[i];
			cgDiscreteNodeDiscreteParentNodeIndex.put(node, i);
		}
		
		for(int i=0;i<cgDiscreteNodeContinuousParentNodes.length;i++) {
			Node node = cgDiscreteNodeContinuousParentNodes[i];
			cgDiscreteNodeContinuousParentNodeIndex.put(node, i);
		}
		 		
		// Initialize mixed-parents continuous children
		this.cgContinuousNodes = cgPm.getCgContinuousVariableNodes()
							.toArray(new Node[cgPm.getCgContinuousVariableNodes().size()]);

		for(int i=0;i<cgContinuousNodes.length;i++) {
			Node node = cgContinuousNodes[i];
			cgContinuousNodeIndex.put(node, i);
		}
		
		discreteParentList.clear();
		continuousParentList.clear();
		
		for(int i=0;i<cgContinuousNodes.length;i++) {
			Node node = cgContinuousNodes[i];
			
			List<Node> parentList = graph.getParents(node);
			
			for(Node pNode : parentList) {
				if(pNode instanceof DiscreteVariable) {
					if(!discreteParentList.contains(pNode)) {
						discreteParentList.add(pNode);
					}
				}else {
					if(!continuousParentList.contains(pNode)) {
						continuousParentList.add(pNode);
					}
				}
			}
		}
		
		Collections.sort(discreteParentList);
		Collections.sort(continuousParentList);
 		
 		this.cgContinuousNodeDiscreteParentNodes = discreteParentList.toArray(new Node[discreteParentList.size()]);
 		this.cgContinuousNodeContinuousParentNodes = continuousParentList.toArray(new Node[continuousParentList.size()]);
		
		for(int i=0;i<cgContinuousNodeDiscreteParentNodes.length;i++) {
			Node node = cgContinuousNodeDiscreteParentNodes[i];
			cgContinuousNodeDiscreteParentNodeIndex.put(node, i);
		}
		
		for(int i=0;i<cgContinuousNodeContinuousParentNodes.length;i++) {
			Node node = cgContinuousNodeContinuousParentNodes[i];
			cgContinuousNodeContinuousParentNodeIndex.put(node, i);
		}
		initializeCgValues(oldCgIm, initializationMethod);
	}

	public CgIm(CgIm cgIm) throws IllegalArgumentException {
		if(cgIm == null) {
			throw new NullPointerException("CG IM must not be null.");
		}
		
		this.cgPm = cgIm.getCgPm();
		
		this.bayesIm = cgIm.getBayesIm();
		this.semIm = cgIm.getSemIm();

		this.cgContinuousVariableNodes = Collections.unmodifiableList(cgPm.getCgContinuousVariableNodes());
		this.cgContinuousMeasuredNodes = Collections.unmodifiableList(cgPm.getCgContinuousMeasureNodes());
		this.cgDiscreteVariableNodes = Collections.unmodifiableList(cgPm.getCgDiscreteVariableNodes());

		this.cgDiscreteNodes = new Node[cgIm.getCgNumDiscreteNodes()];
		
		for(int i=0;i<cgIm.getCgNumDiscreteNodes();i++) {
			Node node = cgIm.getCgDiscreteNode(i);
			this.cgDiscreteNodes[i] = node;
			cgDiscreteNodeIndex.put(node, i);
		}
		
		Graph graph = cgPm.getGraph();
		
		List<Node> discreteParentList = new ArrayList<>();
		List<Node> continuousParentList = new ArrayList<>();
		
		for(int i=0;i<cgDiscreteNodes.length;i++) {
			Node node = cgDiscreteNodes[i];
			
			List<Node> parentList = graph.getParents(node);
			
			for(Node pNode : parentList) {
				if(pNode instanceof DiscreteVariable) {
					if(!discreteParentList.contains(pNode)) {
						discreteParentList.add(pNode);
					}
				}else {
					if(!continuousParentList.contains(pNode)) {
						continuousParentList.add(pNode);
					}
				}
			}
		}
		
		Collections.sort(discreteParentList);
		Collections.sort(continuousParentList);
 		
 		this.cgDiscreteNodeDiscreteParentNodes = discreteParentList.toArray(new Node[discreteParentList.size()]);
 		this.cgDiscreteNodeContinuousParentNodes = continuousParentList.toArray(new Node[continuousParentList.size()]);
		
		this.cgContinuousNodes = new Node[cgIm.getCgNumContinuousNodes()];

		for(int i=0;i<cgIm.getCgNumContinuousNodes();i++) {
			Node node = cgIm.getCgContinuousNode(i);
			this.cgContinuousNodes[i] = node;
			cgContinuousNodeIndex.put(node, i);
		}
		
		discreteParentList.clear();
		continuousParentList.clear();
		
		for(int i=0;i<cgContinuousNodes.length;i++) {
			Node node = cgContinuousNodes[i];
			
			List<Node> parentList = graph.getParents(node);
			
			for(Node pNode : parentList) {
				if(pNode instanceof DiscreteVariable) {
					if(!discreteParentList.contains(pNode)) {
						discreteParentList.add(pNode);
					}
				}else {
					if(!continuousParentList.contains(pNode)) {
						continuousParentList.add(pNode);
					}
				}
			}
		}
		
		Collections.sort(discreteParentList);
		Collections.sort(continuousParentList);
 		
 		this.cgContinuousNodeDiscreteParentNodes = discreteParentList.toArray(new Node[discreteParentList.size()]);
 		this.cgContinuousNodeContinuousParentNodes = continuousParentList.toArray(new Node[continuousParentList.size()]);
	}

	public List<CgParameter> initDiscreteFreeParameters(){
		return Collections.unmodifiableList(cgPm.getDiscreteFreeParameters());
	}
	
	public List<CgParameter> initContinuousFreeParameters(){
		return Collections.unmodifiableList(cgPm.getContinuousFreeParameters());
	}
	
	public List<CgMapping> createDiscreteMappings(List<CgParameter> parameters){
		List<CgMapping> mappings = new ArrayList<>();
		Graph graph = getCgPm().getGraph();
		
		for(CgParameter parameter : parameters) {
			Node nodeA = graph.getNode(parameter.getNodeA().getName());
			Node nodeB = graph.getNode(parameter.getNodeB().getName());
			
			if (nodeA == null || nodeB == null) {
                throw new IllegalArgumentException("Missing variable--either " + nodeA + " or " + nodeB + " parameter = " + parameter + ".");
            }
			
			int i = getDiscreteChildVariableNodes().indexOf(nodeA);
            int j = getDiscreteChildVariableNodes().indexOf(nodeB);
            
            int k = parameter.getChildCategoryNo();

            int l = parameter.getConditionalCaseNo();
            
            if (parameter.getType() == ParamType.MEAN) {
            	CgMapping mapping = new CgMapping(this, parameter, cgDiscreteNodeMeans, i, j, k, l);
            	mappings.add(mapping);
            } else if (parameter.getType() == ParamType.VAR || parameter.getType() == ParamType.COVAR) {
            	CgMapping mapping = new CgMapping(this, parameter, cgDiscreteNodeErrCovar, i, j, k, l);
            	mappings.add(mapping);
            }
            
		}
		
		return Collections.unmodifiableList(mappings);
	}
	
	public List<CgMapping> createContinuousMappings(List<CgParameter> parameters){
		List<CgMapping> mappings = new ArrayList<>();
		Graph graph = getCgPm().getGraph();
		
		for(CgParameter parameter : parameters) {
			Node nodeA = graph.getNode(parameter.getNodeA().getName());
			Node nodeB = graph.getNode(parameter.getNodeB().getName());
			
			if (nodeA == null || nodeB == null) {
                throw new IllegalArgumentException("Missing variable--either " + nodeA + " or " + nodeB + " parameter = " + parameter + ".");
            }
			
			int i = getCgContinuousVariableNodes().indexOf(nodeA);
            int j = getCgContinuousVariableNodes().indexOf(nodeB);
            
            int k = parameter.getChildCategoryNo();

            int l = parameter.getConditionalCaseNo();
            
            if (parameter.getType() == ParamType.MEAN) {
            	CgMapping mapping = new CgMapping(this, parameter, cgContinuousNodeMeans, i, j, k, l);
            	mappings.add(mapping);
            } else if (parameter.getType() == ParamType.COEF) {
            	CgMapping mapping = new CgMapping(this, parameter, cgContinuousNodeEdgeCoef, i, j, k, l);
            	mappings.add(mapping);
            } else if (parameter.getType() == ParamType.VAR || parameter.getType() == ParamType.COVAR) {
            	CgMapping mapping = new CgMapping(this, parameter, cgContinuousNodeErrCovar, i, j, k, l);
            	mappings.add(mapping);
            }
		}
		
		return Collections.unmodifiableList(mappings);
	}
	
	public List<CgParameter> initDiscreteFixedParameters(){
		List<CgParameter> fixedParameters = new ArrayList<>();
		
		for(CgParameter parameter : getCgPm().getCgDiscreteNodesParameters()) {
			ParamType type = parameter.getType();
			
			if(type == ParamType.VAR || type == ParamType.COVAR || type == ParamType.COEF) {
				if(parameter.isFixed()) {
					fixedParameters.add(parameter);
				}
			}
		}
		
		return Collections.unmodifiableList(fixedParameters);
	}
	
	public List<CgParameter> initContinuousFixedParameters(){
		List<CgParameter> fixedParameters = new ArrayList<>();
		
		for (CgParameter parameter : getCgPm().getCgContinuousNodesParameters()) {
			ParamType type = parameter.getType();
			
			if(type == ParamType.VAR || type == ParamType.COVAR || type == ParamType.COEF) {
				if(parameter.isFixed()) {
					fixedParameters.add(parameter);
				}
			}
		}
		
		return Collections.unmodifiableList(fixedParameters);
	}
	
	/**
	 * Iterates through all freeParameters, picking values for them from the
	 * distributions that have been set for them.
	 */
	private void initializeCgValues(CgIm oldCgIm, int initializationMethod) {
		initializeCgDiscreteNodes(oldCgIm, initializationMethod);
		initializeCgContinuousNodes(oldCgIm, initializationMethod);
	}

	private void initializeCgContinuousNodes(CgIm oldCgIm, int initializationMethod) {
		cgContinuousNodeDiscreteParentArray = new int[this.cgContinuousNodes.length][];
		cgContinuousNodeDims = new int[this.cgContinuousNodes.length][];
		cgContinuousNodeEdgeCoef = new double[this.cgContinuousNodes.length][][][];
		cgContinuousNodeErrCovar = new double[this.cgContinuousNodes.length][][][];
		cgContinuousNodeMeans = new double[this.cgContinuousNodes.length][][][];
		cgContinuousNodeMeanStdDevs = new double[this.cgContinuousNodes.length][][][];

		for (int nodeIndex = 0; nodeIndex < this.cgContinuousNodes.length; nodeIndex++) {
			initializeCgContinuousNode(nodeIndex, oldCgIm, initializationMethod);
		}
	}
	
	private void initializeCgDiscreteNodes(CgIm oldCgIm, int initializationMethod) {
		cgDiscreteNodeDiscreteParentArray = new int[this.cgDiscreteNodes.length][];
		cgDiscreteNodeDims = new int[this.cgDiscreteNodes.length][];
		cgDiscreteNodeProbs = new double[this.cgDiscreteNodes.length][][][];
		cgDiscreteNodeErrCovar = new double[this.cgDiscreteNodes.length][][][];
		cgDiscreteNodeMeans = new double[this.cgDiscreteNodes.length][][][];
		cgDiscreteNodeMeanStdDevs = new double[this.cgDiscreteNodes.length][][][];

		for (int nodeIndex = 0; nodeIndex < this.cgDiscreteNodes.length; nodeIndex++) {
			initializeCgDiscreteNode(nodeIndex, oldCgIm, initializationMethod);
		}
	}
	
	/**
	 * This method initializes the node indicated.
	 */
	private void initializeCgContinuousNode(int nodeIndex, CgIm oldCgIm, int initializationMethod) {
		Node node = cgContinuousNodes[nodeIndex];
		
		Graph graph = getCgPm().getGraph();
		List<Node> parentList = graph.getParents(node);
		List<Node> discreteParentList = new ArrayList<>();
		List<Node> continuousParentList = new ArrayList<>();
		
		for(Node pNode : parentList) {
			if(pNode instanceof DiscreteVariable) {
				discreteParentList.add(pNode);
			}else {
				continuousParentList.add(pNode);
			}
		}
		
 		int[] discreteParentArray = new int[discreteParentList.size()];
 		int[] continuousParentArray = new int[continuousParentList.size()];
 		
 		for(int i = 0; i < discreteParentArray.length; i++) {
 			Node pNode = discreteParentList.get(i);
 			discreteParentArray[i] = cgContinuousNodeDiscreteParentNodeIndex.get(pNode);
 		}
 		
 		for(int i = 0; i < continuousParentArray.length; i++) {
 			Node pNode = continuousParentList.get(i);
 			continuousParentArray[i] = cgContinuousNodeContinuousParentNodeIndex.get(pNode);
 		}
 		
 		// Sort parent array.
        Arrays.sort(discreteParentArray);
        Arrays.sort(continuousParentArray);
        
        cgContinuousNodeDiscreteParentArray[nodeIndex] = discreteParentArray;
        cgContinuousNodeContinuousParentArray[nodeIndex] = continuousParentArray;
        
        // Setup dimensions array for discrete parents
        int[] discreteDims = new int[discreteParentArray.length];
        
        for(int i = 0; i < discreteDims.length; i++) {
        	Node pNode = cgContinuousNodeDiscreteParentNodes[discreteParentArray[i]];
        	discreteDims[i] = getCgPm().getNumCategories(pNode);
        }
        
        // Calculate dimensions of table.
        // Rows = a number of discrete parents times their values
        int numRows = 1;

        // Discrete Parents' dims
        for (int dim : discreteDims) {
            if (numRows > 1000000 /* Integer.MAX_VALUE / dim*/) {
                throw new IllegalArgumentException(
                        "The number of rows in the " +
                                "conditional probability table for " +
                                cgContinuousNodes[nodeIndex] +
                                " is greater than 1,000,000 and cannot be " +
                                "represented.");
            }

            numRows *= dim;
        }
        
        // Continuous variable has only one category of bandwith's of values
        int numCols = 1;
        
        int numContinuousParents = continuousParentArray.length;
        
        cgContinuousNodeDims[nodeIndex] = discreteDims;
        cgContinuousNodeEdgeCoef[nodeIndex] = new double[numRows][numCols][numContinuousParents];
        cgContinuousNodeErrCovar[nodeIndex] = new double[numRows][numCols][numContinuousParents];
        cgContinuousNodeMeans[nodeIndex] = new double[numRows][numCols][numContinuousParents];
        cgContinuousNodeMeanStdDevs[nodeIndex] = new double[numRows][numCols][numContinuousParents];
        
        // Initialize each row.
        if (initializationMethod == RANDOM) {
        	// Set variable means to 0.0 pending the program creating the CgIm
            // setting them. I.e. by default they are set to 0.0.
            for (int i = 0; i < numRows; i++) {
            	for(int j=0;j<numContinuousParents;j++) {
                	cgContinuousNodeEdgeCoef[nodeIndex][i][0][j] = 1;
                	cgContinuousNodeErrCovar[nodeIndex][i][0][j] = 0;
                	cgContinuousNodeMeans[nodeIndex][i][0][j] = 0;
                	cgContinuousNodeMeanStdDevs[nodeIndex][i][0][j] = Double.NaN;
            	}
            }
        } else {
        	if (oldCgIm == null) {
            	for (int rowIndex = 0; rowIndex < numRows; rowIndex++) {
        			overwriteContinuousRow(nodeIndex, rowIndex, initializationMethod);
            	}
        	}else if(this.getParams().getBoolean("retainPreviousValues", false)) {
        		retainPreviousContinuousValues(oldCgIm);
        	}
        	
        }
        
        this.distributions = new HashMap<>();
        
        this.functions = new HashMap<>();
	}
	
	private void retainPreviousContinuousValues(CgIm oldCgIm) {
		if(oldCgIm == null) {
			System.out.println("Old CG IM is null.");
			return;
		}
		
		Graph oldGraph = oldCgIm.getCgPm().getGraph();
		
		for(Node node : cgContinuousVariableNodes) {
			
			Node _node = oldGraph.getNode(node.getName());
			
			if(_node == null) {
				continue;
			}
			
			List<Node> discreteParentList = new ArrayList<>();
			List<Node> continuousParentList = new ArrayList<>();
			
			int numConditionalCases = 1;
			
			for(Node parentNode : getCgPm().getGraph().getParents(node)) {
				if(parentNode instanceof DiscreteVariable) {
					if(!discreteParentList.contains(parentNode)) {
						discreteParentList.add(parentNode);
						
						DiscreteVariable discVar = (DiscreteVariable) parentNode;
						numConditionalCases *= discVar.getNumCategories();
					}
					
				}else {
					if(!continuousParentList.contains(parentNode)) {
						continuousParentList.add(parentNode);
					}
				}
			}
			
			for(int i=0;i < numConditionalCases;i++) {
				double _value = oldCgIm.getContinuousParamValue(_node, _node, i);
				
				if(!Double.isNaN(_value)) {
					try {
						CgParameter _parameter = oldCgIm.getCgPm().getContinuousVarianceParameter(_node, i);
						CgParameter parameter = getCgPm().getContinuousVarianceParameter(node, i);
						
						if (parameter == null || _parameter == null) {
                            continue;
                        }

                        if (parameter.getType() != _parameter.getType()) {
                            continue;
                        }

                        if (parameter.isFixed()) {
                            continue;
                        }
                        
                        setContinuousParamValue(node, node, i, _value);
						
					}catch (IllegalArgumentException e) {
                        System.out.println("Couldn't set " + _node);
                    }
				}
				
				for(Node parentNode : continuousParentList) {
					Node _parentNode = oldGraph.getNode(parentNode.getName());
					
					if(_parentNode == null) {
						continue;
					}
					
					_value = oldCgIm.getContinuousParamValue(_parentNode, _node, i);
					
					if(!Double.isNaN(_value)) {
						try {
							CgParameter _parameter = oldCgIm.getCgPm().getContinuousCoefficientParameter(_parentNode, _node, i);
							CgParameter parameter = getCgPm().getContinuousCoefficientParameter(parentNode, node, i);
							
							if (parameter == null || _parameter == null) {
	                            continue;
	                        }

	                        if (parameter.getType() != _parameter.getType()) {
	                            continue;
	                        }

	                        if (parameter.isFixed()) {
	                            continue;
	                        }
	                        
	                        setContinuousParamValue(parentNode, node, i, _value);
						}catch (IllegalArgumentException e) {
	                        System.out.println("Couldn't set " + _node + " , " + _parentNode);
	                    }
					}
				}
			}
			
		}
	}
	
	public void setDiscreteParamValue(CgParameter parameter, double value) {
		// getFreeParameters
		if(getDiscreteFreeParameters().contains(parameter)) {
			int index = getDiscreteFreeParameters().indexOf(parameter);
			CgMapping mapping = this.discreteFreeMappings.get(index);
			mapping.setValue(value);
		// throws error
		}else {
			throw new IllegalArgumentException("That parameter cannot be set in "
                    + "this model: " + parameter);
		}
		
	}
	
	public void setDiscreteFixedParamValue(CgParameter parameter, double value) {
		// getFixedParameters
		if(getDiscreteFixedParameters().contains(parameter)) {
			int index = getDiscreteFixedParameters().indexOf(parameter);
			CgMapping mapping = this.discreteFixedMappings.get(index);
			mapping.setValue(value);
		// throws error
		}else {
			throw new IllegalArgumentException("That parameter cannot be set in "
                    + "this model: " + parameter);
		}
		
	}
	
	public void setDiscreteParamValue(Node nodeA, Node nodeB, int childCategoryNo, int conditionalCaseNo, double value) {
        if (Double.isNaN(value)) {
            throw new IllegalArgumentException("Please remove or impute missing data.");
        }

        if (nodeA == null || nodeB == null) {
            throw new NullPointerException("Nodes must not be null: nodeA = " + nodeA + ", nodeB = " + nodeB);
        }

        CgParameter parameter = null;
        
        if(nodeA == nodeB) {
        	throw new IllegalArgumentException("Two nodes cannot be the same!");
		}
        
        if(parameter == null) {
        	parameter = getCgPm().getDiscreteVarianceParameter(nodeA, nodeB, childCategoryNo, conditionalCaseNo);
        }
        
        if (parameter == null) {
            throw new IllegalArgumentException("There is no parameter in "
                    + "model for an edge from " + nodeA + " to " + nodeB + ".");
        }

        if (!this.getContinuousFreeParameters().contains(parameter)) {
            throw new IllegalArgumentException(
                    "Not a free parameter in " + "this model: " + parameter);
        }

        setDiscreteParamValue(parameter, value);
        
	}
	
	public double getDiscreteParamValue(CgParameter parameter) {
		if (parameter == null) {
            throw new NullPointerException();
        }
		
		if(getDiscreteFreeParameters().contains(parameter)) {
			int index = getDiscreteFreeParameters().indexOf(parameter);
			CgMapping mapping = this.discreteFreeMappings.get(index);
			return mapping.getValue();
		} else if(getDiscreteFixedParameters().contains(parameter)) {
			int index = getDiscreteFixedParameters().indexOf(parameter);
			CgMapping mapping = this.discreteFixedMappings.get(index);
			return mapping.getValue();
		}
		
		throw new IllegalArgumentException(
                "Not a parameter in this model: " + parameter);
	}
	
	public double getDiscreteParamValue(Node nodeA, Node nodeB, int childCategoryNo, int conditionalCaseNo) {
		CgParameter parameter = null;
		
		if(nodeA == nodeB) {
			throw new IllegalArgumentException("Two nodes cannot be the same!");
		}
		
		parameter = getCgPm().getDiscreteVarianceParameter(nodeA, nodeB, childCategoryNo, conditionalCaseNo);
		
		if (parameter == null) {
            return Double.NaN;
        }

        if (!getContinuousFreeParameters().contains(parameter)) {
            return Double.NaN;
        }
		
		return getDiscreteParamValue(parameter);
	}
	
	public void setContinuousParamValue(CgParameter parameter, double value) {
		// getFreeParameters
		if(getContinuousFreeParameters().contains(parameter)) {
			int index = getContinuousFreeParameters().indexOf(parameter);
			CgMapping mapping = this.continuousFreeMappings.get(index);
			mapping.setValue(value);
		// throws error
		}else {
			throw new IllegalArgumentException("That parameter cannot be set in "
                    + "this model: " + parameter);
		}
		
	}
	
	public void setContinuousFixedParamValue(CgParameter parameter, double value) {
		// getFixedParameters
		if(getContinuousFixedParameters().contains(parameter)) {
			int index = getContinuousFixedParameters().indexOf(parameter);
			CgMapping mapping = this.continuousFixedMappings.get(index);
			mapping.setValue(value);
		// throws error
		}else {
			throw new IllegalArgumentException("That parameter cannot be set in "
                    + "this model: " + parameter);
		}
		
	}
	
	public void setContinuousParamValue(Node nodeA, Node nodeB, int conditionalCase, double value) {
        if (Double.isNaN(value)) {
            throw new IllegalArgumentException("Please remove or impute missing data.");
        }

        if (nodeA == null || nodeB == null) {
            throw new NullPointerException("Nodes must not be null: nodeA = " + nodeA + ", nodeB = " + nodeB);
        }

        CgParameter parameter = null;
        
        if(nodeA == nodeB) {
			parameter = getCgPm().getContinuousVarianceParameter(nodeA, conditionalCase);
		}
        
        if(parameter == null) {
        	parameter = getCgPm().getContinuousCoefficientParameter(nodeA, nodeB, conditionalCase);
        }
        
        if (parameter == null) {
            throw new IllegalArgumentException("There is no parameter in "
                    + "model for an edge from " + nodeA + " to " + nodeB + ".");
        }

        if (!this.getContinuousFreeParameters().contains(parameter)) {
            throw new IllegalArgumentException(
                    "Not a free parameter in " + "this model: " + parameter);
        }

        setContinuousParamValue(parameter, value);
        
	}
	
	public double getContinuousParamValue(CgParameter parameter) {
		if (parameter == null) {
            throw new NullPointerException();
        }
		
		if(getContinuousFreeParameters().contains(parameter)) {
			int index = getContinuousFreeParameters().indexOf(parameter);
			CgMapping mapping = this.continuousFreeMappings.get(index);
			return mapping.getValue();
		} else if(getContinuousFixedParameters().contains(parameter)) {
			int index = getContinuousFixedParameters().indexOf(parameter);
			CgMapping mapping = this.continuousFixedMappings.get(index);
			return mapping.getValue();
		}
		
		throw new IllegalArgumentException(
                "Not a parameter in this model: " + parameter);
	}
	
	public double getContinuousParamValue(Node nodeA, Node nodeB, int conditionalCase) {
		CgParameter parameter = null;
		
		if(nodeA == nodeB) {
			parameter = getCgPm().getContinuousVarianceParameter(nodeA, conditionalCase);
		}
		
		if(parameter == null) {
			parameter = getCgPm().getContinuousCoefficientParameter(nodeA, nodeB, conditionalCase);
		}
		
		if (parameter == null) {
            return Double.NaN;
        }

        if (!getContinuousFreeParameters().contains(parameter)) {
            return Double.NaN;
        }
		
		return getContinuousParamValue(parameter);
	}
	
	public List<CgParameter> getDiscreteFreeParameters() {
		return discreteFreeParameters;
	}
	
	public List<CgParameter> getDiscreteFixedParameters() {
		return discreteFixedParameters;
	}
	
	public List<CgParameter> getContinuousFreeParameters() {
		return continuousFreeParameters;
	}
	
	public List<CgParameter> getContinuousFixedParameters() {
		return continuousFixedParameters;
	}
	
	/**
	 * This method initializes the node indicated.
	 */
	private void initializeCgDiscreteNode(int nodeIndex, CgIm oldCgIm, int initializationMethod) {
		Node node = cgDiscreteNodes[nodeIndex];

		Graph graph = getCgPm().getGraph();
		List<Node> parentList = graph.getParents(node);
		List<Node> discreteParentList = new ArrayList<>();
		List<Node> continuousParentList = new ArrayList<>();
		
		for(Node pNode : parentList) {
			if(pNode instanceof DiscreteVariable) {
				discreteParentList.add(pNode);
			}else {
				continuousParentList.add(pNode);
			}
		}
		
 		int[] discreteParentArray = new int[discreteParentList.size()];
 		int[] continuousParentArray = new int[continuousParentList.size()];
 		
 		for(int i = 0; i < discreteParentArray.length; i++) {
 			Node pNode = discreteParentList.get(i);
 			discreteParentArray[i] = cgDiscreteNodeDiscreteParentNodeIndex.get(pNode);
 		}
 		
 		for(int i = 0; i < continuousParentArray.length; i++) {
 			Node pNode = continuousParentList.get(i);
 			continuousParentArray[i] = cgDiscreteNodeContinuousParentNodeIndex.get(pNode);
 		}
 		
 		// Sort parent array.
        Arrays.sort(discreteParentArray);
        Arrays.sort(continuousParentArray);
     
        cgDiscreteNodeDiscreteParentArray[nodeIndex] = discreteParentArray;
        cgDiscreteNodeContinuousParentArray[nodeIndex] = continuousParentArray;
        
        // Setup dimensions array for discrete parents
        int[] discreteDims = new int[discreteParentArray.length];
        
        for(int i = 0; i < discreteDims.length; i++) {
        	Node pNode = cgDiscreteNodeDiscreteParentNodes[discreteParentArray[i]];
        	discreteDims[i] = getCgPm().getNumCategories(pNode);
        }
        
        // Calculate dimensions of table.
        int numRows = 1;

        // Parents' dims
        for (int dim : discreteDims) {
            if (numRows > 1000000 /* Integer.MAX_VALUE / dim*/) {
                throw new IllegalArgumentException(
                        "The number of rows in the " +
                                "conditional probability table for " +
                                cgDiscreteNodes[nodeIndex] +
                                " is greater than 1,000,000 and cannot be " +
                                "represented.");
            }

            numRows *= dim;
        }
        
        int numCols = getCgPm().getNumCategories(node);
        
        int numContinuousParents = continuousParentArray.length;
        
        cgDiscreteNodeDims[nodeIndex] = discreteDims;
        cgDiscreteNodeProbs[nodeIndex] = new double[numRows][numCols][1]; // There is only one probability per one condition
        cgDiscreteNodeMeans[nodeIndex] = new double[numRows][numCols][numContinuousParents];
        cgDiscreteNodeMeanStdDevs[nodeIndex] = new double[numRows][numCols][numContinuousParents];
        
        // Initialize each row.
        if (initializationMethod == RANDOM) {
        	randomizeCgDiscreteTable(nodeIndex);
        } else {
        	for (int rowIndex = 0; rowIndex < numRows; rowIndex++) {
    			if (oldCgIm == null) {
           			overwriteDiscreteRow(nodeIndex, rowIndex, initializationMethod);
       		} else {
        			retainOldDiscreteRowIfPossible(nodeIndex, rowIndex, oldCgIm,
        					initializationMethod);
        		}
        	}
        }
	}
	
    /**
     * This method initializes the node indicated.
     */
    private void retainOldDiscreteRowIfPossible(int nodeIndex, int rowIndex,
                                        CgIm oldCgIm, int initializationMethod) {

        int oldNodeIndex = getCorrespondingDiscreteNodeIndex(nodeIndex, oldCgIm);

        if (oldNodeIndex == -1) {
            overwriteDiscreteRow(nodeIndex, rowIndex, initializationMethod);
        } else if (getCgNumDiscreteColumns(nodeIndex) != oldCgIm.getCgNumDiscreteColumns(oldNodeIndex)) {
            overwriteDiscreteRow(nodeIndex, rowIndex, initializationMethod);
        } else {
            int oldRowIndex = getUniqueCompatibleOldRow(nodeIndex, rowIndex, oldCgIm);

            if (oldRowIndex >= 0) {
                copyValuesFromOldToNew(oldNodeIndex, oldRowIndex, nodeIndex,
                        rowIndex, oldCgIm);
            } else {
                overwriteDiscreteRow(nodeIndex, rowIndex, initializationMethod);
            }
        }
    }

    /**
     * @return the unique rowIndex in the old CgIm for the given node that is
     * compatible with the given rowIndex in the new CgIm for that node, if
     * one exists. Otherwise, returns -1. A compatible rowIndex is one in which
     * all the parents that the given node has in common between the old CgIm
     * and the new CgIm are assigned the values they have in the new
     * rowIndex. If a parent node is removed in the new CgIm, there may be
     * more than one such compatible rowIndex in the old CgIm, in which case
     * -1 is returned. Likewise, there may be no compatible rows, in which case
     * -1 is returned.
     */
    private int getUniqueCompatibleOldRow(int nodeIndex, int rowIndex,
                                          CgIm oldCgIm) {
        int oldNodeIndex = getCorrespondingDiscreteNodeIndex(nodeIndex, oldCgIm);
        int oldNumParents = oldCgIm.getCgDiscreteNodeNumParents(oldNodeIndex);

        int[] oldParentValues = new int[oldNumParents];
        Arrays.fill(oldParentValues, -1);

        int[] parentValues = getCgDiscreteNodeDiscreteParentValues(nodeIndex, rowIndex);

        // Go through each parent of the node in the new CgIm.
        for (int i = 0; i < getCgDiscreteNodeNumParents(nodeIndex); i++) {

            // Get the index of the parent in the new graph and in the old
            // graph. If it's no longer in the new graph, skip to the next
            // parent.
            int parentNodeIndex = getCgDiscreteNodeDiscreteParentNodeIndex(nodeIndex, i);
            int oldParentNodeIndex =
            		getCorrespondingDiscreteNodeIndex(parentNodeIndex, oldCgIm);
            int oldParentIndex = -1;

            for (int j = 0; j < oldCgIm.getCgDiscreteNodeNumParents(oldNodeIndex); j++) {
                if (oldParentNodeIndex == oldCgIm.getCgDiscreteNodeDiscreteParentNodeIndex(oldNodeIndex, j)) {
                    oldParentIndex = j;
                    break;
                }
            }

            if (oldParentIndex == -1 ||
                    oldParentIndex >= oldCgIm.getCgDiscreteNodeNumParents(oldNodeIndex)) {
                return -1;
            }

            // Look up that value index for the new CgIm for that parent.
            // If it was a valid value index in the old CgIm, record
            // that value in oldParentValues. Otherwise return -1.
            int newParentValue = parentValues[i];
            int oldParentDim =
                    oldCgIm.getCgDiscreteNodeDiscreteParentDim(oldNodeIndex, oldParentIndex);

            if (newParentValue < oldParentDim) {
                oldParentValues[oldParentIndex] = newParentValue;
            } else {
                return -1;
            }
        }

        // If there are any -1's in the combination at this point, return -1.
        for (int oldParentValue : oldParentValues) {
            if (oldParentValue == -1) {
                return -1;
            }
        }

        // Otherwise, return the combination, which will be a row in the
        // old CgIm.
        return oldCgIm.getCgDiscreteNodeRowIndex(oldNodeIndex, oldParentValues);
    }

    private void copyValuesFromOldToNew(int oldNodeIndex, int oldRowIndex,
            int nodeIndex, int rowIndex, CgIm oldCgIm) {
		if (getCgNumDiscreteColumns(nodeIndex) != oldCgIm.getCgNumDiscreteColumns(oldNodeIndex)) {
			throw new IllegalArgumentException("It's only possible to copy " +
					"one row of probability values to another in a Bayes IM " +
					"if the number of columns in the table are the same.");
		}
		
		for (int colIndex = 0; colIndex < getCgNumDiscreteColumns(nodeIndex); colIndex++) {
			double prob = oldCgIm.getCgDiscreteNodeProbability(oldNodeIndex, oldRowIndex,	colIndex);
			setCgDiscreteProbability(nodeIndex, rowIndex, colIndex, prob);
		}
	}
	
    /**
     * Sets the probability mass function for the given node at a given row and column in the
     * table for that node.  To get the node index, use getNodeIndex().  To get
     * the row index, use getRowIndex().  To get the column index, use
     * getCategoryIndex() from the underlying CgPm().  The value returned
     * will represent a conditional probability of the form PMF(N=v0 | P1=v1,
     * P2=v2, ... , Pn=vn), where N is the node referenced by nodeIndex, v0 is
     * the value referenced by colIndex, and the combination of parent values
     * indicated is the combination indicated by rowIndex.
     *
     * @param nodeIndex the index of the node in question.
     * @param rowIndex  the row in the table for this for node which represents
     *                  the combination of discrete parent values in question.
     * @param colIndex  the column in the table for this node which represents
     *                  the value of the node in question.
     * @param value     the desired probability to be set.
     * @see #getPMF
     */
    public void setCgDiscreteProbability(int nodeIndex, int rowIndex, int colIndex,
                               double value) {
        if (colIndex >= getCgNumDiscreteColumns(nodeIndex)) {
            throw new IllegalArgumentException("Column out of range: " +
                    colIndex + " >= " + getCgNumDiscreteColumns(nodeIndex));
        }

        if (!(0.0 <= value && value <= 1.0) && !Double.isNaN(value)) {
            throw new IllegalArgumentException("Probability value must be " +
                    "between 0.0 and 1.0 or Double.NaN.");
        }

        cgDiscreteNodeProbs[nodeIndex][rowIndex][colIndex][0] = value;
    }
    
    public void setCgDiscreteParentMean(int nodeIndex, int rowIndex, int colIndex,
    							int continuousParentIndex, double value) {
        if (colIndex >= getCgNumDiscreteColumns(nodeIndex)) {
            throw new IllegalArgumentException("Column out of range: " +
                    colIndex + " >= " + getCgNumDiscreteColumns(nodeIndex));
        }

        cgDiscreteNodeMeans[nodeIndex][rowIndex][colIndex][continuousParentIndex] = value;
    }

    public void setCgDiscreteParentMeanStdDev(int nodeIndex, int rowIndex, int colIndex,
    							int continuousParentIndex, double value) {
    	if (colIndex >= getCgNumDiscreteColumns(nodeIndex)) {
			throw new IllegalArgumentException("Column out of range: " +
					colIndex + " >= " + getCgNumDiscreteColumns(nodeIndex));
		}
		
		cgDiscreteNodeMeanStdDevs[nodeIndex][rowIndex][colIndex][continuousParentIndex] = value;
    	// 
    }
    // 
    /**
     * @return the index of the node with the given name in the specified
     * CgIm.
     */
    public int getCorrespondingDiscreteNodeIndex(int nodeIndex, CgIm otherCgIm) {
        String nodeName = getCgDiscreteNode(nodeIndex).getName();
        Node oldNode = otherCgIm.getNode(nodeName);
        return otherCgIm.getCgDiscreteNodeIndex(oldNode);
    }

    /**
     * @return the index of the node with the given name in the specified
     * CgIm.
     */
    public int getCorrespondingContinuousNodeIndex(int nodeIndex, CgIm otherCgIm) {
        String nodeName = getCgContinuousNode(nodeIndex).getName();
        Node oldNode = otherCgIm.getNode(nodeName);
        return otherCgIm.getCgContinuousNodeIndex(oldNode);
    }

    private void overwriteContinuousRow(int nodeIndex, int rowIndex, int initializationMethod) {
    	if (initializationMethod == RANDOM) {
    		randomizeContinuousRow(nodeIndex, rowIndex);
		} else if (initializationMethod == MANUAL) {
			initializeContinuousRowAsUnknowns(nodeIndex, rowIndex);
		} else {
			throw new IllegalArgumentException("Unrecognized state.");
		}
    }
    
    private void initializeContinuousRowAsUnknowns(int nodeIndex, int rowIndex) {
    	int[] continuousParentArray = cgContinuousNodeContinuousParentArray[nodeIndex];
    	final int size = continuousParentArray.length;
        double[] row = new double[size];
        Arrays.fill(row, Double.NaN);
        // columnIndex always equal to 0 for continuous rows
        cgContinuousNodeEdgeCoef[nodeIndex][rowIndex][0] = row;
    }
    
    private void randomizeContinuousRow(int nodeIndex, int rowIndex) {
    	int[] continuousParentArray = cgContinuousNodeContinuousParentArray[nodeIndex];
    	final int size = continuousParentArray.length;
    	cgContinuousNodeEdgeCoef[nodeIndex][rowIndex][0] = getRandomWeights(size);
    }
    
	private void overwriteDiscreteRow(int nodeIndex, int rowIndex, int initializationMethod) {
		if (initializationMethod == RANDOM) {
			randomizeCgDiscreteRow(nodeIndex, rowIndex);
		} else if (initializationMethod == MANUAL) {
			initializeDiscreteRowAsUnknowns(nodeIndex, rowIndex);
		} else {
			throw new IllegalArgumentException("Unrecognized state.");
		}
	}	
	
    private void initializeDiscreteRowAsUnknowns(int nodeIndex, int rowIndex) {
        int numCols = getCgNumDiscreteColumns(nodeIndex);
        final int size = 1;
        
        for(int colIndex=0;colIndex<numCols;colIndex++) {
            double[] row = new double[size];
            Arrays.fill(row, Double.NaN);
            cgDiscreteNodeProbs[nodeIndex][rowIndex][colIndex] = row;
        }
        
    }

	/**
     * @return this number.
     * @see #getNumRows
     */
    public int getCgNumDiscreteColumns(int nodeIndex) {
        return cgDiscreteNodeProbs[nodeIndex][0].length;
    }

    /**
     * @return this number.
     * @see #getRowIndex
     * @see #getNumColumns
     */
    public int getCgDiscreteNumRows(int nodeIndex) {
        return cgDiscreteNodeProbs[nodeIndex].length;
    }

    /**
     * @param nodeIndex the given node.
     * @return the number of parents for this node.
     */
    public int getCgDiscreteNodeNumParents(int nodeIndex) {
        return cgDiscreteNodeDiscreteParentArray[nodeIndex].length;
    }
	
    /**
     * @param nodeIndex the given node.
     * @return the number of parents for this node.
     */
    public int getCgContinuousNodeNumParents(int nodeIndex) {
        return cgContinuousNodeDiscreteParentArray[nodeIndex].length;
    }
	
    /**
     * @return the given parent of the given node.
     */
    public int getCgDiscreteNodeDiscreteParentNodeIndex(int nodeIndex, int parentIndex) {
        return cgDiscreteNodeDiscreteParentArray[nodeIndex][parentIndex];
    }

    /**
     * @return the given parent of the given node.
     */
    public int getCgContinuousNodeDiscreteParentNodeIndex(int nodeIndex, int parentIndex) {
        return cgContinuousNodeDiscreteParentArray[nodeIndex][parentIndex];
    }

    /**
     * @return the dimension of the given parent for the given node.
     */
    public int getCgDiscreteNodeDiscreteParentDim(int nodeIndex, int parentIndex) {
        return cgDiscreteNodeDims[nodeIndex][parentIndex];
    }

    /**
     * @return this array of parent dimensions.
     * @see #getParents
     */
    public int[] getCgDiscreteNodeDiscreteParentDims(int nodeIndex) {
        int[] dims = cgDiscreteNodeDims[nodeIndex];
        int[] copy = new int[dims.length];
        System.arraycopy(dims, 0, copy, 0, dims.length);
        return copy;
    }

    /**
     * @return (a defensive copy of) the array containing all of the parents of
     * a given node in the order in which they are stored internally.
     * @see #getParentDims
     */
    public int[] getCgDiscreteNodeDiscreteParentNodeArray(int nodeIndex) {
        int[] nodeParents = cgDiscreteNodeDiscreteParentArray[nodeIndex];
        int[] copy = new int[nodeParents.length];
        System.arraycopy(nodeParents, 0, copy, 0, nodeParents.length);
        return copy;
    }

    /**
     * @return (a defensive copy of) the array containing all of the parents of
     * a given node in the order in which they are stored internally.
     * @see #getParentDims
     */
    public int[] getCgDiscreteNodeContinuousParentNodeArray(int nodeIndex) {
        int[] nodeParents = cgDiscreteNodeContinuousParentArray[nodeIndex];
        int[] copy = new int[nodeParents.length];
        System.arraycopy(nodeParents, 0, copy, 0, nodeParents.length);
        return copy;
    }

    /**
     * @return (a defensive copy of) the array containing all of the parents of
     * a given node in the order in which they are stored internally.
     * @see #getParentDims
     */
    public int[] getCgContinuousNodeDiscreteParentNodeArray(int nodeIndex) {
        int[] nodeParents = cgContinuousNodeDiscreteParentArray[nodeIndex];
        int[] copy = new int[nodeParents.length];
        System.arraycopy(nodeParents, 0, copy, 0, nodeParents.length);
        return copy;
    }

    /**
     * @return (a defensive copy of) the array containing all of the parents of
     * a given node in the order in which they are stored internally.
     * @see #getParentDims
     */
    public int[] getCgContinuousNodeContinuousParentNodeArray(int nodeIndex) {
        int[] nodeParents = cgContinuousNodeContinuousParentArray[nodeIndex];
        int[] copy = new int[nodeParents.length];
        System.arraycopy(nodeParents, 0, copy, 0, nodeParents.length);
        return copy;
    }

    /**
     * @param nodeIndex the index of the node.
     * @param rowIndex  the index of the row in question.
     * @return the array representing the combination of parent values for this
     * row.
     * @see #getNodeIndex
     * @see #getRowIndex
     */
    public int[] getCgDiscreteNodeDiscreteParentValues(int nodeIndex, int rowIndex) {
        int[] dims = getCgDiscreteNodeDiscreteParentDims(nodeIndex);
        int[] values = new int[dims.length];

        for (int i = dims.length - 1; i >= 0; i--) {
            values[i] = rowIndex % dims[i];
            rowIndex /= dims[i];
        }

        return values;
    }

    /**
     * @return the value in the probability table for the given node, at the
     * given row and column.
     */
    public int getCgDiscreteParentValueOfDiscreteNode(int nodeIndex, int rowIndex, int colIndex) {
        return getCgDiscreteNodeDiscreteParentValues(nodeIndex, rowIndex)[colIndex];
    }

    /**
     * @param nodeIndex the index of the node in question.
     * @param rowIndex  the row in the table for this for node which represents
     *                  the combination of parent values in question.
     * @param colIndex  the column in the table for this node which represents
     *                  the value of the node in question.
     * @return the probability stored for this parameter.
     * @see #getNodeIndex
     * @see #getRowIndex
     */
    public double getCgDiscreteNodeProbability(int nodeIndex, int rowIndex, int colIndex) {
        return cgDiscreteNodeProbs[nodeIndex][rowIndex][colIndex][0];
    }

    /**
     * @return the row in the table for the given node and combination of discrete parent
     * values.
     * @see #getParentValues
     */
    public int getCgDiscreteNodeRowIndex(int nodeIndex, int[] values) {
        int[] dim = getCgDiscreteNodeDiscreteParentDims(nodeIndex);
        int rowIndex = 0;

        for (int i = 0; i < dim.length; i++) {
            rowIndex *= dim[i];
            rowIndex += values[i];
        }

        return rowIndex;
    }

	/**
     * Randomizes every row in the table for the given node index.
     *
     * @param nodeIndex the node for the table to be randomized.
     */
	public void randomizeCgDiscreteTable(int nodeIndex) {
		for (int rowIndex = 0; rowIndex < getCgDiscreteNumRows(nodeIndex); rowIndex++) {
            randomizeCgDiscreteRow(nodeIndex, rowIndex);
        }
	}
	
	/**
     * Assigns random probability values to the child values of this row that
     * add to 1.
     *
     * @param nodeIndex the node for the table that this row belongs to.
     * @param rowIndex  the index of the row.
     */
	private void randomizeCgDiscreteRow(int nodeIndex, int rowIndex) {
		int numCols = getCgNumDiscreteColumns(nodeIndex);
		int[] continuousParentArray = cgDiscreteNodeContinuousParentArray[nodeIndex];
		final int size = continuousParentArray.length;
		for(int colIndex=0;colIndex<numCols;colIndex++) {
			cgDiscreteNodeProbs[nodeIndex][rowIndex][colIndex] = getRandomWeights(size);
		}
	}

    /**
     * @param node the given node.
     * @return the index for that node, or -1 if the node is not in the
     * CgIm.
     */
    public int getCgContinuousNodeIndex(Node node) {
    	if(cgContinuousNodeIndex.containsKey(node)) {
        	return cgContinuousNodeIndex.get(node).intValue();
        }

        return -1;
    }
    
    /**
     * @param node the given node.
     * @return the index for that node, or -1 if the node is not in the
     * CgIm.
     */
    public int getCgDiscreteNodeIndex(Node node) {
        if(cgDiscreteNodeIndex.containsKey(node)) {
        	return cgDiscreteNodeIndex.get(node).intValue();
        }

        return -1;
    }

    private static double[] getRandomWeights(int size) {
        assert size >= 0;

        double[] row = new double[size];
        double sum = 0.0;

        for (int i = 0; i < size; i++) {
            double v = RandomUtil.getInstance().nextBeta(1, size - 1);
            row[i] = v;// > 0.5 ? 2 * v : 0.5 * v;
            sum += row[i];
        }

        for (int i = 0; i < size; i++) {
            row[i] /= sum;
        }

        return row;
    }
    
    // Not Done Yet!!!
	@Override
	public DataSet simulateData(int sampleSize, boolean latentDataSaved) {
		return null;
	}

	// Not Done Yet!!!
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

	public void setBayesIm(BayesIm bayesIm) {
		this.bayesIm = bayesIm;
	}
	
	public ISemIm getSemIm() {
		return semIm;
	}

	public void setSemIm(ISemIm semIm) {
		this.semIm = semIm;
	}
	
	public Graph getDag() {
		return cgPm.getGraph();
	}
	
	public int getCgNumDiscreteNodes() {
		return cgDiscreteNodes.length;
	}
	
	public int getCgNumContinuousNodes() {
		return cgContinuousNodes.length;
	}
	
	public Node getCgDiscreteNode(int nodeIndex) {
		return cgDiscreteNodes[nodeIndex];
	}

	public Node getCgContinuousNode(int nodeIndex) {
		return cgContinuousNodes[nodeIndex];
	}

	/**
     * @param name the name of the node.
     * @return the node.
     */
    public Node getNode(String name) {
        return getDag().getNode(name);
    }
    
    public List<Node> getCgDiscreteVariables() {
    	return Arrays.asList(cgDiscreteNodes);
    }
    
    public List<String> getCgDiscreteVariableNames(){
    	List<String> variableNames = new LinkedList<>();

        for (int i = 0; i < getCgNumDiscreteNodes(); i++) {
            Node node = getCgDiscreteNode(i);
            DiscreteVariable var = (DiscreteVariable) node;
            variableNames.add(var.getName());
        }

        return variableNames;
    }
    
    public Parameters getParams() {
        return params;
    }

    public void setParams(Parameters params) {
        this.params = params;
    }
    
    public List<Node> getDiscreteChildVariableNodes() {
    	return cgDiscreteVariableNodes;
    }

    /**
     * The list of measured and latent nodes for the CgPm. (Unmodifiable.)
     */
    public List<Node> getCgContinuousVariableNodes() {
        return cgContinuousVariableNodes;
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     */
	public static CgIm serializableInstance() {
		return new CgIm(CgPm.serializableInstance());
	}

    public boolean isParameterBoundsEnforced() {
        return parameterBoundsEnforced;
    }

    public void setParameterBoundsEnforced(
            boolean parameterBoundsEnforced) {
        this.parameterBoundsEnforced = parameterBoundsEnforced;
    }

	public List<Node> getCgDiscreteVariableNodes() {
		return cgDiscreteVariableNodes;
	}

	public Node getCgDiscreteNodeDiscreteParentNode(int discreteParentIndex) {
		return cgDiscreteNodeDiscreteParentNodes[discreteParentIndex];
	}

	public Node getCgDiscreteNodeContinuousParentNode(int continuousParentIndex) {
		return cgDiscreteNodeContinuousParentNodes[continuousParentIndex];
	}

	public Map<Node, Integer> getCgDiscreteNodeIndex() {
		return cgDiscreteNodeIndex;
	}

	public Map<Node, Integer> getCgDiscreteNodeDiscreteParentNodeIndex() {
		return cgDiscreteNodeDiscreteParentNodeIndex;
	}

	public Map<Node, Integer> getCgDiscreteNodeContinuousParentNodeIndex() {
		return cgDiscreteNodeContinuousParentNodeIndex;
	}

	public Node[] getCgContinuousNodeDiscreteParentNodes() {
		return cgContinuousNodeDiscreteParentNodes;
	}

	public Node[] getCgContinuousNodeContinuousParentNodes() {
		return cgContinuousNodeContinuousParentNodes;
	}

	public Map<Node, Integer> getCgContinuousNodeIndex() {
		return cgContinuousNodeIndex;
	}

	public Map<Node, Integer> getCgContinuousNodeDiscreteParentNodeIndex() {
		return cgContinuousNodeDiscreteParentNodeIndex;
	}

	public Map<Node, Integer> getCgContinuousNodeContinuousParentNodeIndex() {
		return cgContinuousNodeContinuousParentNodeIndex;
	}

}
