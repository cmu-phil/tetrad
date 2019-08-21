/**
 * 
 */
package edu.pitt.dbmi.cg;

import static java.lang.Math.abs;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import edu.cmu.tetrad.bayes.BayesIm;
import edu.cmu.tetrad.bayes.BayesPm;
import edu.cmu.tetrad.bayes.MlBayesIm;
import edu.cmu.tetrad.data.BoxDataSet;
import edu.cmu.tetrad.data.ContinuousVariable;
//import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataUtils;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.data.Discretizer;
import edu.cmu.tetrad.data.MixedDataBox;
import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeType;
import edu.cmu.tetrad.sem.ConnectionFunction;
import edu.cmu.tetrad.sem.ParamType;
import edu.cmu.tetrad.sem.Parameter;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.IM;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.RandomUtil;
import edu.cmu.tetrad.util.TetradMatrix;
import edu.cmu.tetrad.util.TetradSerializable;
import edu.cmu.tetrad.util.dist.Distribution;
import edu.cmu.tetrad.util.dist.Split;

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

	private static final double ALLOWABLE_DIFFERENCE = 1.0e-3;
	
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
	
	private SemIm semIm;

    // All discrete nodes
    private List<Node> discreteNodes;
    // All continuous nodes
    private List<Node> continuousNodes;
	
	// Mixed parents with continuous child
	private final List<Node> cgContinuousVariableNodes;
	
	//private final List<Node> cgContinuousMeasuredNodes;
	
	//private List<CgParameter> cgContinuousFreeParameters;
	
	//private List<CgParameter> cgContinuousFixedParameters;
	
	//private List<CgMapping> cgContinuousFreeMappings;
	
	//private List<CgMapping> cgContinuousFixedMappings;
	
    // Mixed parents with discrete child
	private final List<Node> cgDiscreteVariableNodes;
	
	//private List<CgParameter> cgDiscreteFreeParameters;
	
	//private List<CgParameter> cgDiscreteFixedParameters;
	
	//private List<CgMapping> cgDiscreteFreeMappings;
	
	//private List<CgMapping> cgDiscreteFixedMappings;
	
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
     * True just in case the graph for the SEM is cyclic.
     */
    private boolean cyclic;

    /**
     * True just in case cyclicity has already been checked.
     */
    private boolean cyclicChecked = false;

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
	
	// nodeIndex, discreteParentConditionIndex, nodeCategoryIndex (= 0), continuousParentNodeIndex ( +1 self)
	private double[][][][] cgContinuousNodeEdgeCoef; // cgContinuousNodeEdgeCoef[][][][0] = 1 # correlation of itself
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
	 * Constructs a new Condtional Gaussian IM from a Conditional Gaussian PM.
	 */
	public CgIm(CgPm cgPm) {
		this(cgPm, null, new Parameters(), MANUAL);
	}

	public CgIm(CgPm cgPm, int initializationMethod) {
		this(cgPm, null, new Parameters(), initializationMethod);
	}

	public CgIm(CgPm cgPm, CgIm oldCgIm, int initializationMethod) {
		this(cgPm, oldCgIm, new Parameters(), initializationMethod);
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

		this.cgPm = new CgPm(cgPm); // cgPm;
		
		this.discreteNodes = Collections.unmodifiableList(getCgPm().getDiscreteNodes());
		this.continuousNodes = Collections.unmodifiableList(getCgPm().getContinuousNodes());
		
		this.cgContinuousVariableNodes = Collections.unmodifiableList(getCgPm().getCgContinuousVariableNodes());
		//this.cgContinuousMeasuredNodes = Collections.unmodifiableList(getCgPm().getCgContinuousMeasureNodes());
		this.cgDiscreteVariableNodes = Collections.unmodifiableList(getCgPm().getCgDiscreteVariableNodes());
		
		if(oldCgIm != null) {
			this.bayesIm = new MlBayesIm(cgPm.getBayesPm(),oldCgIm.getBayesIm(), initializationMethod);
			this.semIm = new SemIm(cgPm.getSemPm(), oldCgIm.getSemIm(), parameters);
		}else {
			this.bayesIm = new MlBayesIm(cgPm.getBayesPm(), initializationMethod);
			this.semIm = new SemIm(cgPm.getSemPm(), parameters);
		}

		Graph graph = getCgPm().getGraph();
		
		// Initialize mixed-parents discrete children
		this.cgDiscreteNodes = getCgPm().getCgDiscreteVariableNodes()
							.toArray(new Node[getCgPm().getCgDiscreteVariableNodes().size()]);
		
		cgDiscreteNodeIndex = new HashMap<>();
		for(int i=0;i<cgDiscreteNodes.length;i++) {
			Node node = cgDiscreteNodes[i];
			cgDiscreteNodeIndex.put(node, i);
			//System.out.println("CgIm(CgPm cgPm, CgIm oldCgIm, Parameters parameters, int initializationMethod).cgDiscreteNodeIndex.put(node: " + node + " i: " + i + ")");
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
		
 		cgDiscreteNodeDiscreteParentNodeIndex = new HashMap<>();
		for(int i=0;i<cgDiscreteNodeDiscreteParentNodes.length;i++) {
			Node node = cgDiscreteNodeDiscreteParentNodes[i];
			cgDiscreteNodeDiscreteParentNodeIndex.put(node, i);
		}
		cgDiscreteNodeContinuousParentNodeIndex = new HashMap<>();
		System.out.println("***************************************");
		System.out.println("cgDiscreteNodeContinuousParentNodes");
		for(int i=0;i<cgDiscreteNodeContinuousParentNodes.length;i++) {
			Node node = cgDiscreteNodeContinuousParentNodes[i];
			cgDiscreteNodeContinuousParentNodeIndex.put(node, i);
			System.out.println(node.getName() + " : " + i);
		}
		 		
		// Initialize mixed-parents continuous children
		this.cgContinuousNodes = getCgPm().getCgContinuousVariableNodes()
							.toArray(new Node[getCgPm().getCgContinuousVariableNodes().size()]);

		cgContinuousNodeIndex = new HashMap<>();
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
		
 		cgContinuousNodeDiscreteParentNodeIndex = new HashMap<>();
		for(int i=0;i<cgContinuousNodeDiscreteParentNodes.length;i++) {
			Node node = cgContinuousNodeDiscreteParentNodes[i];
			cgContinuousNodeDiscreteParentNodeIndex.put(node, i);
		}
		
		cgContinuousNodeContinuousParentNodeIndex = new HashMap<>();
		for(int i=0;i<cgContinuousNodeContinuousParentNodes.length;i++) {
			Node node = cgContinuousNodeContinuousParentNodes[i];
			cgContinuousNodeContinuousParentNodeIndex.put(node, i);
		}
		
		initializeCgValues(oldCgIm, initializationMethod);

		//this.cgContinuousFreeParameters = initCgContinuousFreeParameters();
		//this.cgContinuousFreeMappings = createCgContinuousMappings(cgContinuousFreeParameters);
		//this.cgContinuousFixedParameters = initCgContinuousFixedParameters();
		//this.cgContinuousFixedMappings = createCgContinuousMappings(cgContinuousFixedParameters);
		
		//this.cgDiscreteFreeParameters = initCgDiscreteFreeParameters();
		//this.cgDiscreteFreeMappings = createDiscreteMappings(cgDiscreteFreeParameters);
		//this.cgDiscreteFixedParameters = initCgDiscreteFixedParameters();
		//this.cgDiscreteFixedMappings = createDiscreteMappings(cgDiscreteFixedParameters);

		this.distributions = new HashMap<>();
		
		this.functions = new HashMap<>();
	}

	public CgIm(CgIm cgIm) throws IllegalArgumentException {
		if(cgIm == null) {
			throw new NullPointerException("CG IM must not be null.");
		}
		
		this.cgPm = new CgPm(cgIm.getCgPm());//cgIm.getCgPm();
		
		this.bayesIm = new MlBayesIm(cgIm.getBayesIm());//cgIm.getBayesIm();
		this.semIm = new SemIm(cgIm.getSemIm());//cgIm.getSemIm();

		this.discreteNodes = Collections.unmodifiableList(cgIm.getCgPm().getDiscreteNodes());
		this.continuousNodes = Collections.unmodifiableList(cgIm.getCgPm().getContinuousNodes());

		this.cgContinuousVariableNodes = Collections.unmodifiableList(cgIm.getCgPm().getCgContinuousVariableNodes());
		//this.cgContinuousMeasuredNodes = Collections.unmodifiableList(cgIm.getCgPm().getCgContinuousMeasureNodes());
		this.cgDiscreteVariableNodes = Collections.unmodifiableList(cgIm.getCgPm().getCgDiscreteVariableNodes());

		this.cgDiscreteNodes = new Node[cgIm.getCgDiscreteNumNodes()];
		
		cgDiscreteNodeIndex = new HashMap<>();
		for(int i=0;i<cgIm.getCgDiscreteNumNodes();i++) {
			Node node = cgIm.getCgDiscreteNode(i);
			this.cgDiscreteNodes[i] = node;
			cgDiscreteNodeIndex.put(node, i);
			//System.out.println("CgIm(CgIm cgIm).cgDiscreteNodeIndex.put(node: " + node + " i: " + i + ")");
		}
		
		Graph graph = cgIm.getCgPm().getGraph();
		
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
		
 		cgDiscreteNodeDiscreteParentNodeIndex = new HashMap<>();
		for(int i=0;i<cgDiscreteNodeDiscreteParentNodes.length;i++) {
			Node node = cgDiscreteNodeDiscreteParentNodes[i];
			cgDiscreteNodeDiscreteParentNodeIndex.put(node, i);
		}
		
		cgDiscreteNodeContinuousParentNodeIndex = new HashMap<>();
		System.out.println("***************************************");
		System.out.println("cgDiscreteNodeContinuousParentNodes");
		for(int i=0;i<cgDiscreteNodeContinuousParentNodes.length;i++) {
			Node node = cgDiscreteNodeContinuousParentNodes[i];
			cgDiscreteNodeContinuousParentNodeIndex.put(node, i);
			System.out.println(node.getName() + " : " + i);
		}
 		
		this.cgContinuousNodes = new Node[cgIm.getCgContinuousNumNodes()];

		cgContinuousNodeIndex = new HashMap<>();
		for(int i=0;i<cgIm.getCgContinuousNumNodes();i++) {
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

 		cgContinuousNodeDiscreteParentNodeIndex = new HashMap<>();
		for(int i=0;i<cgContinuousNodeDiscreteParentNodes.length;i++) {
			Node node = cgContinuousNodeDiscreteParentNodes[i];
			cgContinuousNodeDiscreteParentNodeIndex.put(node, i);
		}
		
		cgContinuousNodeContinuousParentNodeIndex = new HashMap<>();
		for(int i=0;i<cgContinuousNodeContinuousParentNodes.length;i++) {
			Node node = cgContinuousNodeContinuousParentNodes[i];
			cgContinuousNodeContinuousParentNodeIndex.put(node, i);
		}
		
		initializeCgValues(cgIm, CgIm.MANUAL);
		
		/*this.cgContinuousFreeParameters = initCgContinuousFreeParameters();
		this.cgContinuousFreeMappings = createCgContinuousMappings(cgContinuousFreeParameters);
		this.cgContinuousFixedParameters = initCgContinuousFixedParameters();
		this.cgContinuousFixedMappings = createCgContinuousMappings(cgContinuousFixedParameters);
		
		this.cgDiscreteFreeParameters = initCgDiscreteFreeParameters();
		this.cgDiscreteFreeMappings = createDiscreteMappings(cgDiscreteFreeParameters);
		this.cgDiscreteFixedParameters = initCgDiscreteFixedParameters();
		this.cgDiscreteFixedMappings = createDiscreteMappings(cgDiscreteFixedParameters);*/

		this.distributions = new HashMap<>();
		
		this.functions = new HashMap<>();
	}

	/*public List<CgParameter> initCgDiscreteFreeParameters(){
		return Collections.unmodifiableList(cgPm.getCgDiscreteFreeParameters());
	}
	
	public List<CgParameter> initCgContinuousFreeParameters(){
		//System.out.println("cgPm.getCgContinuousFreeParameters(): " + cgPm.getCgContinuousFreeParameters());
		return Collections.unmodifiableList(cgPm.getCgContinuousFreeParameters());
	}*/
	
	public List<CgMapping> createDiscreteMappings(List<CgParameter> parameters){
		List<CgMapping> mappings = new ArrayList<>();
		Graph graph = getCgPm().getGraph();
		
		for(CgParameter parameter : parameters) {
			Node nodeA = graph.getNode(parameter.getNodeA().getName());
			Node nodeB = graph.getNode(parameter.getNodeB().getName());
			
			if (nodeA == null || nodeB == null) {
                throw new IllegalArgumentException("Missing variable--either " + nodeA + " or " + nodeB + " parameter = " + parameter + ".");
            }
			
			int i = getDiscreteNodes().indexOf(nodeA);
            int j = getDiscreteNodes().indexOf(nodeB);
            
            int k = parameter.getChildCategoryNo();

            int l = parameter.getConditionalCaseNo();
            
            if (parameter.getType() == ParamType.MEAN) {
            	CgMapping mapping = new CgMapping(this, parameter, cgDiscreteNodeMeans, i, j, k, l);
            	mappings.add(mapping);
            }else if(parameter.getType() == ParamType.VAR) {
            	CgMapping mapping = new CgMapping(this, parameter, cgDiscreteNodeMeanStdDevs, i, j, k, l);
            	mappings.add(mapping);
            } else if (parameter.getType() == ParamType.COVAR) {
            	CgMapping mapping = new CgMapping(this, parameter, cgContinuousNodeErrCovar, i, j, k, l);
            	mappings.add(mapping);
            }
            
		}
		
		return Collections.unmodifiableList(mappings);
	}
	
	public List<CgMapping> createCgContinuousMappings(List<CgParameter> parameters){
		List<CgMapping> mappings = new ArrayList<>();
		Graph graph = getCgPm().getGraph();
		
		for(CgParameter parameter : parameters) {
			Node nodeA = graph.getNode(parameter.getNodeA().getName());
			Node nodeB = graph.getNode(parameter.getNodeB().getName());
			
			if (nodeA == null || nodeB == null) {
                throw new IllegalArgumentException("Missing variable--either " + nodeA + " or " + nodeB + " parameter = " + parameter + ".");
            }

            //System.out.println("nodeA: " + nodeA + " nodeB: " + nodeB);
            //System.out.println("parameter: " + parameter.getName());

			int i = getContinuousNodes().indexOf(nodeA);
            int j = getContinuousNodes().indexOf(nodeB);
            
            int k = parameter.getChildCategoryNo();

            int l = parameter.getConditionalCaseNo();
            
            //System.out.println("i: " + i + " j: " + j + " k: " + k + " l: " + l);
            
            if (parameter.getType() == ParamType.MEAN) {
            	CgMapping mapping = new CgMapping(this, parameter, cgContinuousNodeMeans, i, j, k, l);
            	mappings.add(mapping);
            } else if (parameter.getType() == ParamType.COEF) {
            	CgMapping mapping = new CgMapping(this, parameter, cgContinuousNodeEdgeCoef, i, j, k, l);
            	mappings.add(mapping);
            } else if (parameter.getType() == ParamType.VAR) {
            	CgMapping mapping = new CgMapping(this, parameter, cgContinuousNodeMeanStdDevs, i, j, k, l);
            	mappings.add(mapping);
            } else if (parameter.getType() == ParamType.COVAR) {
            	CgMapping mapping = new CgMapping(this, parameter, cgContinuousNodeErrCovar, i, j, k, l);
            	mappings.add(mapping);
            }
            
		}
		
		return Collections.unmodifiableList(mappings);
	}
	
	/*public List<CgParameter> initCgDiscreteFixedParameters(){
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
	
	public List<CgParameter> initCgContinuousFixedParameters(){
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
	}*/
	
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
		cgContinuousNodeContinuousParentArray = new int[this.cgContinuousNodes.length][];
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
		cgDiscreteNodeContinuousParentArray = new int[this.cgDiscreteNodes.length][];
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
        	int numCategories = (getCgPm().getDiscreteNumCategories(pNode) < 2)?2:getCgPm().getDiscreteNumCategories(pNode);
        	discreteDims[i] = numCategories;
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
        
        int numContinuousSelfAndParents = continuousParentArray.length + 1;
        
        cgContinuousNodeDims[nodeIndex] = discreteDims;
        cgContinuousNodeEdgeCoef[nodeIndex] = new double[numRows][numCols][numContinuousSelfAndParents];
        cgContinuousNodeErrCovar[nodeIndex] = new double[numRows][numCols][numContinuousSelfAndParents];
        cgContinuousNodeMeans[nodeIndex] = new double[numRows][numCols][numContinuousSelfAndParents];
        cgContinuousNodeMeanStdDevs[nodeIndex] = new double[numRows][numCols][numContinuousSelfAndParents];
        
        // Initialize each row.
        if (initializationMethod == RANDOM) {
        	
        	final double coefLow = getParams().getDouble("coefLow", .5);
            final double coefHigh = getParams().getDouble("coefHigh", 1.5);
            final double covLow = getParams().getDouble("covLow", 1);
            final double covHigh = getParams().getDouble("covHigh", 3);
                        
            for (int i = 0; i < numRows; i++) {
            	for(int j=0;j<numContinuousSelfAndParents;j++) {
                    double coefValue = new Split(coefLow, coefHigh).nextRandom();
                    if(!getParams().getBoolean("coefSymmetric", true)) {
                    	coefValue = Math.abs(coefValue);
                    }
                    double varValue = new Split(covLow, covHigh).nextRandom();
                    if(!getParams().getBoolean("covSymmetric", true)) {
                    	varValue = Math.abs(varValue);
                    }
                    double meanValue = new Split(coefLow, coefHigh).nextRandom();
                    double sdValue = new Split(covLow, covHigh).nextRandom();
                    sdValue = Math.abs(sdValue);
                    
                    cgContinuousNodeEdgeCoef[nodeIndex][i][0][j] = coefValue;
                	cgContinuousNodeErrCovar[nodeIndex][i][0][j] = varValue;
                	cgContinuousNodeMeans[nodeIndex][i][0][j] = meanValue;
                	cgContinuousNodeMeanStdDevs[nodeIndex][i][0][j] = sdValue;
            	}
            }
        } else {
        	if (oldCgIm == null) {
            	for (int rowIndex = 0; rowIndex < numRows; rowIndex++) {
        			overwriteContinuousRow(nodeIndex, rowIndex, initializationMethod);
            	}
        	}else {//if(this.getParams().getBoolean("retainPreviousValues", true)) {
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
			
			int nodeIndex = getCgContinuousNodeIndex(node);
			int _nodeIndex = oldCgIm.getCgContinuousNodeIndex(_node);
			
			List<Node> discreteParentList = new ArrayList<>();
			List<Node> continuousParentList = new ArrayList<>();
			
			int numConditionalCases = 1;
			
			for(Node parentNode : getDag().getParents(node)) {
				Node  _parentNode = oldGraph.getNode(parentNode.getName());
				if(_parentNode == null) {
					continue;
				}
				
				if(parentNode instanceof DiscreteVariable) {
					if(!discreteParentList.contains(parentNode)) {
						discreteParentList.add(parentNode);
						
						int numCategories = getCgPm().getDiscreteNumCategories(parentNode);
						int _numCategories = oldCgIm.getCgPm().getDiscreteNumCategories(parentNode);
						
						System.out.println("parentNode: " + parentNode + " numCategories: " + numCategories);
						
						if(numCategories != _numCategories) {
							continue;
						}
						
						numConditionalCases *= numCategories;
					}
					
				}else {
					if(!continuousParentList.contains(parentNode)) {
						continuousParentList.add(parentNode);
					}
				}
			}
			
			//rowIndex
			for(int i=0;i < numConditionalCases;i++) {
				int numContinuousSelfAndParents = continuousParentList.size() + 1;
				for(int continuousParentIndex=0;continuousParentIndex < numContinuousSelfAndParents;continuousParentIndex++) {
					System.out.println("node: " + node + " nodeIndex: " + nodeIndex + " _nodeIndex:" + _nodeIndex + " i: " + i + " continuousParentIndex: " + continuousParentIndex);
					double value = oldCgIm.getCgContinuousNodeContinuousParentEdgeCoef(_nodeIndex, i, continuousParentIndex);
					setCgContinuousNodeContinuousParentEdgeCoef(nodeIndex, i, continuousParentIndex, value);
					
					value = oldCgIm.getCgContinuousNodeContinuousParentErrCovar(_nodeIndex, i, continuousParentIndex);
					setCgContinuousNodeContinuousParentErrCovar(nodeIndex, i, continuousParentIndex, value);
					
					value = oldCgIm.getCgContinuousNodeContinuousParentMean(_nodeIndex, i, continuousParentIndex);
					setCgContinuousNodeContinuousParentMean(nodeIndex, i, continuousParentIndex, value);
					
					value = oldCgIm.getCgContinuousNodeContinuousParentMeanStdDev(_nodeIndex, i, continuousParentIndex);
					setCgContinuousNodeContinuousParentMeanStdDev(nodeIndex, i, continuousParentIndex, value);
				}
				
				
				/*double _value = oldCgIm.getContinuousParamValue(_node, _node, i);
				
				if(!Double.isNaN(_value)) {
					try {
						CgParameter _parameter = oldCgIm.getCgPm().getCgContinuousVarianceParameter(_node, i);
						CgParameter parameter = getCgPm().getCgContinuousVarianceParameter(node, i);
						
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
							CgParameter _parameter = oldCgIm.getCgPm().getCgContinuousCoefficientParameter(_parentNode, _node, i);
							CgParameter parameter = getCgPm().getCgContinuousCoefficientParameter(parentNode, node, i);
							
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
				}*/
			}
			
		}
	}
	
	/*public void setDiscreteParamValue(CgParameter parameter, double value) {
		// getFreeParameters
		if(getCgDiscreteFreeParameters().contains(parameter)) {
			int index = getCgDiscreteFreeParameters().indexOf(parameter);
			CgMapping mapping = this.cgDiscreteFreeMappings.get(index);
			mapping.setValue(value);
		// throws error
		}else {
			throw new IllegalArgumentException("That parameter cannot be set in "
                    + "this model: " + parameter);
		}
		
	}
	
	public void setDiscreteFixedParamValue(CgParameter parameter, double value) {
		// getFixedParameters
		if(getCgDiscreteFixedParameters().contains(parameter)) {
			int index = getCgDiscreteFixedParameters().indexOf(parameter);
			CgMapping mapping = this.cgDiscreteFixedMappings.get(index);
			mapping.setValue(value);
		// throws error
		}else {
			throw new IllegalArgumentException("That parameter cannot be set in "
                    + "this model: " + parameter);
		}
		
	}*/
	
	/*public void setDiscreteParamValue(Node nodeA, Node nodeB, int childCategoryNo, int conditionalCaseNo, double value) {
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
        	parameter = getCgPm().getCgDiscreteVarianceParameter(nodeA, nodeB, childCategoryNo, conditionalCaseNo);
        }
        
        if (parameter == null) {
            throw new IllegalArgumentException("There is no parameter in "
                    + "model for an edge from " + nodeA + " to " + nodeB + ".");
        }

        if (!this.getCgContinuousFreeParameters().contains(parameter)) {
            throw new IllegalArgumentException(
                    "Not a free parameter in " + "this model: " + parameter);
        }

        setDiscreteParamValue(parameter, value);
        
	}
	
	public double getDiscreteParamValue(CgParameter parameter) {
		if (parameter == null) {
            throw new NullPointerException();
        }
		
		if(getCgDiscreteFreeParameters().contains(parameter)) {
			int index = getCgDiscreteFreeParameters().indexOf(parameter);
			CgMapping mapping = this.cgDiscreteFreeMappings.get(index);
			return mapping.getValue();
		} else if(getCgDiscreteFixedParameters().contains(parameter)) {
			int index = getCgDiscreteFixedParameters().indexOf(parameter);
			CgMapping mapping = this.cgDiscreteFixedMappings.get(index);
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
		
		parameter = getCgPm().getCgDiscreteVarianceParameter(nodeA, nodeB, childCategoryNo, conditionalCaseNo);
		
		if (parameter == null) {
            return Double.NaN;
        }

        if (!getCgContinuousFreeParameters().contains(parameter)) {
            return Double.NaN;
        }
		
		return getDiscreteParamValue(parameter);
	}
	
	public void setContinuousParamValue(CgParameter parameter, double value) {
		// getFreeParameters
		if(getCgContinuousFreeParameters().contains(parameter)) {
			int index = getCgContinuousFreeParameters().indexOf(parameter);
			CgMapping mapping = this.cgContinuousFreeMappings.get(index);
			mapping.setValue(value);
		// throws error
		}else {
			throw new IllegalArgumentException("That parameter cannot be set in "
                    + "this model: " + parameter);
		}
		
	}
	
	public void setContinuousFixedParamValue(CgParameter parameter, double value) {
		// getFixedParameters
		if(getCgContinuousFixedParameters().contains(parameter)) {
			int index = getCgContinuousFixedParameters().indexOf(parameter);
			CgMapping mapping = this.cgContinuousFixedMappings.get(index);
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
			parameter = getCgPm().getCgContinuousVarianceParameter(nodeA, conditionalCase);
		}
        
        if(parameter == null) {
        	parameter = getCgPm().getCgContinuousCoefficientParameter(nodeA, nodeB, conditionalCase);
        }
        
        if (parameter == null) {
            throw new IllegalArgumentException("There is no parameter in "
                    + "model for an edge from " + nodeA + " to " + nodeB + ".");
        }

        if (!this.getCgContinuousFreeParameters().contains(parameter)) {
            throw new IllegalArgumentException(
                    "Not a free parameter in " + "this model: " + parameter);
        }

        setContinuousParamValue(parameter, value);
        
	}
	
	public double getContinuousParamValue(CgParameter parameter) {
		if (parameter == null) {
            throw new NullPointerException();
        }
		
		if(getCgContinuousFreeParameters().contains(parameter)) {
			int index = getCgContinuousFreeParameters().indexOf(parameter);
			CgMapping mapping = this.cgContinuousFreeMappings.get(index);
			return mapping.getValue();
		} else if(getCgContinuousFixedParameters().contains(parameter)) {
			int index = getCgContinuousFixedParameters().indexOf(parameter);
			CgMapping mapping = this.cgContinuousFixedMappings.get(index);
			return mapping.getValue();
		}
		
		throw new IllegalArgumentException(
                "Not a parameter in this model: " + parameter);
	}
	
	public double getContinuousParamValue(Node nodeA, Node nodeB, int conditionalCase) {
		CgParameter parameter = null;
		
		if(nodeA == nodeB) {
			parameter = getCgPm().getCgContinuousVarianceParameter(nodeA, conditionalCase);
		}
		
		if(parameter == null) {
			parameter = getCgPm().getCgContinuousCoefficientParameter(nodeA, nodeB, conditionalCase);
		}
		
		if (parameter == null) {
            return Double.NaN;
        }

        if (!getCgContinuousFreeParameters().contains(parameter)) {
            return Double.NaN;
        }
		
		return getContinuousParamValue(parameter);
	}
	
	public List<CgParameter> getCgDiscreteFreeParameters() {
		return cgDiscreteFreeParameters;
	}
	
	public List<CgParameter> getCgDiscreteFixedParameters() {
		return cgDiscreteFixedParameters;
	}
	
	public List<CgParameter> getCgContinuousFreeParameters() {
		return cgContinuousFreeParameters;
	}
	
	public List<CgParameter> getCgContinuousFixedParameters() {
		return cgContinuousFixedParameters;
	}*/
	
	/**
	 * This method initializes the node indicated.
	 */
	private void initializeCgDiscreteNode(int nodeIndex, CgIm oldCgIm, int initializationMethod) {
		Node node = cgDiscreteNodes[nodeIndex];
		//System.out.println("initializeCgDiscreteNode.node: "  + node);

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
        //System.out.println("discreteParentArray.length: " + discreteParentArray.length);
        
        for(int i = 0; i < discreteDims.length; i++) {
        	Node pNode = cgDiscreteNodeDiscreteParentNodes[discreteParentArray[i]];
        	int numCategories = (getCgPm().getDiscreteNumCategories(pNode) < 2)?2:getCgPm().getDiscreteNumCategories(pNode);
        	discreteDims[i] = numCategories;
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
        
        int numCols = (getCgPm().getDiscreteNumCategories(node) < 2)?2:getCgPm().getDiscreteNumCategories(node);
        
        //System.out.println("initializing node: " + node.getName() + " nodeIndex: " + nodeIndex + " rows: " + numRows + " cols: " + numCols);
        
        int numContinuousParents = continuousParentArray.length;
        
        //System.out.println("numContinuousParents: " + numContinuousParents);
        
        cgDiscreteNodeDims[nodeIndex] = discreteDims;
        cgDiscreteNodeProbs[nodeIndex] = new double[numRows][numCols][1]; // There is only one probability per one condition
        cgDiscreteNodeErrCovar[nodeIndex] = new double[numRows][numCols][numContinuousParents];
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

        int oldNodeIndex = getCorrespondingCgDiscreteNodeIndex(nodeIndex, oldCgIm);
        //System.out.println("retainOldDiscreteRowIfPossible.nodeIndex: " + nodeIndex + " rowIndex: " + rowIndex + " oldNodeIndex: " + oldNodeIndex);
        
        if (oldNodeIndex == -1) {
        	//System.out.println("retainOldDiscreteRowIfPossible.oldNodeIndex == -1");
            overwriteDiscreteRow(nodeIndex, rowIndex, initializationMethod);
        } else if (getCgDiscreteNumColumns(nodeIndex) != oldCgIm.getCgDiscreteNumColumns(oldNodeIndex)) {
        	//System.out.println("retainOldDiscreteRowIfPossible.getCgDiscreteNumColumns(nodeIndex) != oldCgIm.getCgDiscreteNumColumns(oldNodeIndex)");
            overwriteDiscreteRow(nodeIndex, rowIndex, initializationMethod);
        } else {
        	//System.out.println("retainOldDiscreteRowIfPossible.else");
            int oldRowIndex = getUniqueCompatibleOldRow(nodeIndex, rowIndex, oldCgIm);
            //System.out.println("retainOldDiscreteRowIfPossible.getUniqueCompatibleOldRow(nodeIndex: " + nodeIndex + " rowIndex: " + rowIndex + " oldNodeIndex: " + oldNodeIndex + ")");

            if (oldRowIndex >= 0) {
            	//System.out.println("retainOldDiscreteRowIfPossible.oldRowIndex >= 0");
                copyCgDiscreteValuesFromOldToNew(oldNodeIndex, oldRowIndex, nodeIndex,
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
        int oldNodeIndex = getCorrespondingCgDiscreteNodeIndex(nodeIndex, oldCgIm);
        int oldNumParents = oldCgIm.getCgDiscreteNodeNumDiscreteParents(oldNodeIndex);
        //System.out.println("getUniqueCompatibleOldRow.oldNodeIndex: " + oldNodeIndex);
        //System.out.println("getUniqueCompatibleOldRow.oldNumParents: " + oldNumParents);
        
        int[] oldParentValues = new int[oldNumParents];
        Arrays.fill(oldParentValues, -1);

        int[] parentValues = getCgDiscreteNodeDiscreteParentValues(nodeIndex, rowIndex);

        // Go through each parent of the node in the new CgIm.
        for (int i = 0; i < getCgDiscreteNodeNumDiscreteParents(nodeIndex); i++) {

            // Get the index of the parent in the new graph and in the old
            // graph. If it's no longer in the new graph, skip to the next
            // parent.
            int parentNodeIndex = getCgDiscreteNodeDiscreteParentNodeIndex(nodeIndex, i);
            //System.out.println("getUniqueCompatibleOldRow.parentNodeIndex: " + parentNodeIndex);
            int oldParentNodeIndex = getCorrespondingCgDiscreteNodeDiscreteParentIndex(parentNodeIndex, oldCgIm);
            int oldParentIndex = -1;

            for (int j = 0; j < oldCgIm.getCgDiscreteNodeNumDiscreteParents(oldNodeIndex); j++) {
                if (oldParentNodeIndex == oldCgIm.getCgDiscreteNodeDiscreteParentNodeIndex(oldNodeIndex, j)) {
                    oldParentIndex = j;
                    break;
                }
            }

            if (oldParentIndex == -1 ||
                    oldParentIndex >= oldCgIm.getCgDiscreteNodeNumDiscreteParents(oldNodeIndex)) {
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

    private void copyCgDiscreteValuesFromOldToNew(int oldNodeIndex, int oldRowIndex,
            int nodeIndex, int rowIndex, CgIm oldCgIm) {
		if (getCgDiscreteNumColumns(nodeIndex) != oldCgIm.getCgDiscreteNumColumns(oldNodeIndex)) {
			throw new IllegalArgumentException("It's only possible to copy " +
					"one row of probability values to another in a Conditional Gaussian IM " +
					"if the number of columns in the table are the same.");
		}
		
		for (int colIndex = 0; colIndex < getCgDiscreteNumColumns(nodeIndex); colIndex++) {
			double prob = oldCgIm.getCgDiscreteNodeProbability(oldNodeIndex, oldRowIndex,	colIndex);
			setCgDiscreteNodeProbability(nodeIndex, rowIndex, colIndex, prob);
			
			if(getCgDiscreteNodeNumContinuousParents(nodeIndex) == oldCgIm.getCgDiscreteNodeNumContinuousParents(oldNodeIndex)) {
				//System.out.println("nodeIndex: " + nodeIndex + " oldNodeIndex: " + oldNodeIndex);
				//System.out.println("getCgDiscreteNodeNumContinuousParents(nodeIndex): " + getCgDiscreteNodeNumContinuousParents(nodeIndex));
				//System.out.println("oldCgIm.getCgDiscreteNodeNumContinuousParents(oldNodeIndex): " + oldCgIm.getCgDiscreteNodeNumContinuousParents(oldNodeIndex));
				
				// nodeIndex, discreteParentConditionIndex, nodeCategoryIndex, continuousParentNodeIndex
				for(int parentIndex=0;parentIndex<getCgDiscreteNodeNumContinuousParents(nodeIndex);parentIndex++) {
					//ErrCovar
					double errCovar = oldCgIm.getCgDiscreteNodeContinuousParentErrCovar(oldNodeIndex, oldRowIndex, colIndex, parentIndex);
					setCgDiscreteNodeContinuousParentErrCovar(nodeIndex, rowIndex, colIndex, parentIndex, errCovar);
					
					//Mean
					double mean = oldCgIm.getCgDiscreteNodeContinuousParentMean(oldNodeIndex, oldRowIndex, colIndex, parentIndex);
					setCgDiscreteNodeContinuousParentMean(nodeIndex, rowIndex, colIndex, parentIndex, mean);

					//MeanStdDev
					double sd = oldCgIm.getCgDiscreteNodeContinuousParentMeanStdDev(oldNodeIndex, oldRowIndex, colIndex, parentIndex);
					setCgDiscreteNodeContinuousParentMeanStdDev(nodeIndex, rowIndex, colIndex, parentIndex, sd);
				}
			}
			
		}
	}
	
    /**
     * Sets the distribution for the given node.
     */
    public void setCgDistribution(Node node, int conditionalCaseNo, Distribution distribution) {
        if (node == null) {
            throw new NullPointerException();
        }

        if (!cgContinuousVariableNodes.contains(node)) {
            throw new IllegalArgumentException("Not a node in this Conditional Gaussian Continuous nodes.");
        }

        if (distribution == null) {
            throw new NullPointerException("Distribution must be specified.");
        }

        Map<Integer, Distribution> conditionalCaseDistributions = distributions.get(node);
        if(conditionalCaseDistributions == null) {
        	conditionalCaseDistributions = new HashMap<>();
        }
        conditionalCaseDistributions.put(conditionalCaseNo, distribution);
        distributions.put(node, conditionalCaseDistributions);
    }
	
    public void setCgFunction(Node node, int conditionalCaseNo, ConnectionFunction function) {
        List<Node> parents = cgPm.getGraph().getParents(node);

        for (Iterator<Node> j = parents.iterator(); j.hasNext();) {
            Node _node = j.next();

            if (_node.getNodeType() == NodeType.ERROR) {
                j.remove();
            }
        }

        HashSet<Node> parentSet = new HashSet<>(parents);
        List<Node> inputList = Arrays.asList(function.getInputNodes());
        HashSet<Node> inputSet = new HashSet<>(inputList);

        if (!parentSet.equals(inputSet)) {
            throw new IllegalArgumentException("The given function for " + node
                    + " may only use the parents of " + node + ": " + parents);
        }

        Map<Integer, ConnectionFunction> conditionalCaseFunctions = functions.get(node);
        if(conditionalCaseFunctions == null) {
        	conditionalCaseFunctions = new HashMap<>();
        }
        
        conditionalCaseFunctions.put(conditionalCaseNo, function);
        functions.put(node, conditionalCaseFunctions);
    }

    public ConnectionFunction getCgConnectionFunction(Node node, int conditionalCaseNo) {
    	Map<Integer, ConnectionFunction> conditionalCaseFunctions = functions.get(node);
    	if(conditionalCaseFunctions != null) {
    		return conditionalCaseFunctions.get(conditionalCaseNo);
    	}
        return null;
    }

    /**
     * Sets the value of a single free parameter to the given value.
     *
     * @throws IllegalArgumentException if the given parameter is not a free
     * parameter in this model.
     */
    /*public void setCgDiscretesParamValue(CgParameter parameter, double value) {
    	if(getCgDiscreteFreeParameters().contains(parameter)) {
    		int index = getCgDiscreteFreeParameters().indexOf(parameter);
    		CgMapping mapping = this.cgDiscreteFreeMappings.get(index);
    		mapping.setValue(value);
    	} else {
            throw new IllegalArgumentException("That parameter cannot be set in "
                    + "this model: " + parameter);
        }
    }

    public double getCgContinuousParamValue(CgParameter parameter) {
    	if (parameter == null) {
            throw new NullPointerException();
        }
    	
    	if(getCgContinuousFreeParameters().contains(parameter)) {
    		int index = getCgContinuousFreeParameters().indexOf(parameter);
    		CgMapping mapping = this.cgContinuousFreeMappings.get(index);
    		return mapping.getValue();
    	} else {
            throw new IllegalArgumentException("That parameter cannot be set in "
                    + "this model: " + parameter);
        }
    }*/
    
    /**
     * Sets the value of a single free parameter to the given value.
     *
     * @throws IllegalArgumentException if the given parameter is not a free
     * parameter in this model.
     */
    /*public void setCgContinuousParamValue(CgParameter parameter, double value) {
    	if(getCgContinuousFreeParameters().contains(parameter)) {
    		int index = getCgContinuousFreeParameters().indexOf(parameter);
    		CgMapping mapping = this.cgContinuousFreeMappings.get(index);
    		mapping.setValue(value);
    	} else {
            throw new IllegalArgumentException("That parameter cannot be set in "
                    + "this model: " + parameter);
        }
    }*/
    
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
    public void setCgDiscreteNodeProbability(int nodeIndex, int rowIndex, int colIndex,
                               double value) {
        if (colIndex >= getCgDiscreteNumColumns(nodeIndex)) {
            throw new IllegalArgumentException("Column out of range: " +
                    colIndex + " >= " + getCgDiscreteNumColumns(nodeIndex));
        }

        if (!(0.0 <= value && value <= 1.0) && !Double.isNaN(value)) {
            throw new IllegalArgumentException("Probability value must be " +
                    "between 0.0 and 1.0 or Double.NaN.");
        }

        // nodeIndex, discreteParentConditionIndex, nodeCategoryIndex, 0 = self
        Node node = cgDiscreteNodes[nodeIndex];
    	Node continuousParentNode = cgDiscreteNodeContinuousParentNodes[cgDiscreteNodeContinuousParentArray[nodeIndex][0]];
    	System.out.println("cgDiscreteNode: " + node.getName() + " continuousParentNode: " + continuousParentNode.getName());
    	System.out.println("Before: setCgDiscreteNodeProbability: cgDiscreteNodeProbs[" + nodeIndex + "][" + + rowIndex + "][" + colIndex + "][" + + 0 + "] = " + cgDiscreteNodeProbs[nodeIndex][rowIndex][colIndex][0]);
        cgDiscreteNodeProbs[nodeIndex][rowIndex][colIndex][0] = value;
    	System.out.println("After: setCgDiscreteNodeProbability: cgDiscreteNodeProbs[" + nodeIndex + "][" + + rowIndex + "][" + colIndex + "][" + + 0 + "] = " + cgDiscreteNodeProbs[nodeIndex][rowIndex][colIndex][0]);
    }
    
    public double getCgDiscreteNodeContinuousParentErrCovar(int nodeIndex, int rowIndex, int colIndex,
				int continuousParentIndex) {
		if (colIndex >= getCgDiscreteNumColumns(nodeIndex)) {
			throw new IllegalArgumentException("Column out of range: " +
					colIndex + " >= " + getCgDiscreteNumColumns(nodeIndex));
		}
		
		//System.out.println("getCgDiscreteNodeContinuousParentErrCovar.nodeIndex: " + nodeIndex + " rowIndex:" + rowIndex + " colIndex: " + colIndex + " continuousParentIndex: " + continuousParentIndex);
		//System.out.println("getCgDiscreteNodeContinuousParentErrCovar[nodeIndex].length: " + cgDiscreteNodeErrCovar[nodeIndex].length);
		//System.out.println("getCgDiscreteNodeContinuousParentErrCovar[nodeIndex][rowIndex].length: " + cgDiscreteNodeErrCovar[nodeIndex][rowIndex].length);
		//System.out.println("getCgDiscreteNodeContinuousParentErrCovar[nodeIndex][rowIndex][colIndex].length: " + cgDiscreteNodeErrCovar[nodeIndex][rowIndex][colIndex].length);
		
		return cgDiscreteNodeErrCovar[nodeIndex][rowIndex][colIndex][continuousParentIndex];
	}

    public void setCgDiscreteNodeContinuousParentErrCovar(int nodeIndex, int rowIndex, int colIndex,
				int continuousParentIndex, double value) {
		if (colIndex >= getCgDiscreteNumColumns(nodeIndex)) {
			throw new IllegalArgumentException("Column out of range: " +
					colIndex + " >= " + getCgDiscreteNumColumns(nodeIndex));
		}
		
		cgDiscreteNodeErrCovar[nodeIndex][rowIndex][colIndex][continuousParentIndex] = value;
	}
	
    public double getCgDiscreteNodeContinuousParentMean(int nodeIndex, int rowIndex, int colIndex,
    							int continuousParentIndex) {
    	if (colIndex >= getCgDiscreteNumColumns(nodeIndex)) {
            throw new IllegalArgumentException("Column out of range: " +
                    colIndex + " >= " + getCgDiscreteNumColumns(nodeIndex));
        }
    	
    	// nodeIndex, discreteParentConditionIndex, nodeCategoryIndex, continuousParentNodeIndex
    	Node node = cgDiscreteNodes[nodeIndex];
    	Node continuousParentNode = cgDiscreteNodeContinuousParentNodes[cgDiscreteNodeContinuousParentArray[nodeIndex][continuousParentIndex]];
    	System.out.println("cgDiscreteNode: " + node.getName() + " continuousParentNode: " + continuousParentNode.getName());
    	System.out.println("getCgDiscreteNodeContinuousParentMean: cgDiscreteNodeMeans[" + nodeIndex + "][" + + rowIndex + "][" + colIndex + "][" + + continuousParentIndex + "] = " + 
    			cgDiscreteNodeMeans[nodeIndex][rowIndex][colIndex][continuousParentIndex]);
    	
        return cgDiscreteNodeMeans[nodeIndex][rowIndex][colIndex][continuousParentIndex];
    }
    
    public void setCgDiscreteNodeContinuousParentMean(int nodeIndex, int rowIndex, int colIndex,
    							int continuousParentIndex, double value) {
        if (colIndex >= getCgDiscreteNumColumns(nodeIndex)) {
            throw new IllegalArgumentException("Column out of range: " +
                    colIndex + " >= " + getCgDiscreteNumColumns(nodeIndex));
        }

        System.out.println("cgDiscreteNodeMeans.dim: " + cgDiscreteNodeMeans.length + ":" + + cgDiscreteNodeMeans[0].length + ":" + cgDiscreteNodeMeans[0][0].length + ":" + + cgDiscreteNodeMeans[0][0][0].length);
        // nodeIndex, discreteParentConditionIndex, nodeCategoryIndex, continuousParentNodeIndex
        System.out.println("Before: cgDiscreteNodeMeans[" + nodeIndex + "][" + + rowIndex + "][" + colIndex + "][" + + continuousParentIndex + "] = " + 
        		cgDiscreteNodeMeans[nodeIndex][rowIndex][colIndex][continuousParentIndex]);
        
        cgDiscreteNodeMeans[nodeIndex][rowIndex][colIndex][continuousParentIndex] = value;
        
        System.out.println("After: cgDiscreteNodeMeans[" + nodeIndex + "][" + + rowIndex + "][" + colIndex + "][" + + continuousParentIndex + "] = " + 
        		cgDiscreteNodeMeans[nodeIndex][rowIndex][colIndex][continuousParentIndex]);
    }

    public double getCgDiscreteNodeContinuousParentMeanStdDev(int nodeIndex, int rowIndex, int colIndex,
						int continuousParentIndex) {
			if (colIndex >= getCgDiscreteNumColumns(nodeIndex)) {
			throw new IllegalArgumentException("Column out of range: " +
			colIndex + " >= " + getCgDiscreteNumColumns(nodeIndex));
			}
	
		// nodeIndex, discreteParentConditionIndex, nodeCategoryIndex, continuousParentNodeIndex
		Node node = cgDiscreteNodes[nodeIndex];
		Node continuousParentNode = cgDiscreteNodeContinuousParentNodes[cgDiscreteNodeContinuousParentArray[nodeIndex][continuousParentIndex]];
		System.out.println("cgDiscreteNode: " + node.getName() + " continuousParentNode: " + continuousParentNode.getName());
		System.out.println("getCgDiscreteNodeContinuousParentMeanStdDev: cgDiscreteNodeMeanStdDevs[" + nodeIndex + "][" + + rowIndex + "][" + colIndex + "][" + + continuousParentIndex + "] = " + 
				cgDiscreteNodeMeanStdDevs[nodeIndex][rowIndex][colIndex][continuousParentIndex]);
	
		return cgDiscreteNodeMeanStdDevs[nodeIndex][rowIndex][colIndex][continuousParentIndex];
	}

    public void setCgDiscreteNodeContinuousParentMeanStdDev(int nodeIndex, int rowIndex, int colIndex,
    							int continuousParentIndex, double value) {
    	if (colIndex >= getCgDiscreteNumColumns(nodeIndex)) {
			throw new IllegalArgumentException("Column out of range: " +
					colIndex + " >= " + getCgDiscreteNumColumns(nodeIndex));
		}
		
		cgDiscreteNodeMeanStdDevs[nodeIndex][rowIndex][colIndex][continuousParentIndex] = value;
    }
    
    /*public double getCgContinuousNodeContinuousParentEdgeCoef(Node continuousNode, Node continuousParentNode, int conditionalCaseNo) {
    	CgParameter param = cgPm.getCgContinuousCoefficientParameter(continuousNode, continuousParentNode, conditionalCaseNo);
    	return getCgContinuousParamValue(param);
    }*/

    public double getCgContinuousNodeContinuousParentEdgeCoef(int nodeIndex, int rowIndex, int continuousParentIndex) {
    	return cgContinuousNodeEdgeCoef[nodeIndex][rowIndex][0][continuousParentIndex];
    }
    
    /*public void setCgContinuousNodeContinuousParentEdgeCoef(Node continuousNode, Node continuousParentNode, int conditionalCaseNo, double value) {
    	CgParameter param = cgPm.getCgContinuousCoefficientParameter(continuousNode, continuousParentNode, conditionalCaseNo);
    	setCgContinuousParamValue(param, value);
    	
    	/*int nodeIndex = getCgContinuousNodeIndex(continuousNode);
    	int rowIndex = conditionalCaseNo;
    	int continuousParentIndex = -1;
    	int[] parentNodeArray = getCgContinuousNodeContinuousParentNodeArray(nodeIndex);
    	for(int i=0;i<parentNodeArray.length;i++) {
    		int parentIndex = parentNodeArray[i];
    		Node parentNode = getCgContinuousNodeContinuousParentNode(parentIndex);
    		if(parentNode.equals(continuousParentNode)) {
    			continuousParentIndex = i;
    			break;
    		}
    	}
    	if(continuousParentIndex == -1) {
    		throw new IllegalArgumentException(
                    "Not found continuous parent node: " + continuousParentNode);
    	}
    	setCgContinuousNodeContinuousParentEdgeCoef(nodeIndex, rowIndex, continuousParentIndex, value);*/
    //}
    
    public void setCgContinuousNodeContinuousParentEdgeCoef(int nodeIndex, int rowIndex, int continuousParentIndex, double value) {
    	//System.out.println("cgContinuousNodeEdgeCoef.numNodes: " + cgContinuousNodeEdgeCoef.length);
    	//System.out.println("cgContinuousNodeEdgeCoef.numNodes.rows: " + cgContinuousNodeEdgeCoef[nodeIndex].length);
    	//System.out.println("cgContinuousNodeEdgeCoef.numNodes.rows.cols: " + cgContinuousNodeEdgeCoef[nodeIndex][rowIndex].length);
    	//System.out.println("cgContinuousNodeEdgeCoef.numNodes.rows.cols.continuousParents: " + cgContinuousNodeEdgeCoef[nodeIndex][rowIndex][0].length);
    	cgContinuousNodeEdgeCoef[nodeIndex][rowIndex][0][continuousParentIndex] = value;
    }
    
    public double getCgContinuousNodeContinuousParentErrCovar(int nodeIndex, int rowIndex, int continuousParentIndex) {
    	return cgContinuousNodeErrCovar[nodeIndex][rowIndex][0][continuousParentIndex];
    }
    
    public void setCgContinuousNodeContinuousParentErrCovar(int nodeIndex, int rowIndex, int continuousParentIndex, double value) {
    	cgContinuousNodeErrCovar[nodeIndex][rowIndex][0][continuousParentIndex] = value;
    }
    
    public double getCgContinuousNodeContinuousParentMean(int nodeIndex, int rowIndex, int continuousParentIndex) {
    	return cgContinuousNodeMeans[nodeIndex][rowIndex][0][continuousParentIndex];
    }
    
    public void setCgContinuousNodeContinuousParentMean(int nodeIndex, int rowIndex, int continuousParentIndex, double value) {
		cgContinuousNodeMeans[nodeIndex][rowIndex][0][continuousParentIndex] = value;
    }
    
    public double getCgContinuousNodeContinuousParentMeanStdDev(int nodeIndex, int rowIndex, int continuousParentIndex) {
    	return cgContinuousNodeMeanStdDevs[nodeIndex][rowIndex][0][continuousParentIndex];
    }
    
    public void setCgContinuousNodeContinuousParentMeanStdDev(int nodeIndex, int rowIndex, int continuousParentIndex, double value) {
		cgContinuousNodeMeanStdDevs[nodeIndex][rowIndex][0][continuousParentIndex] = value;
    }
    
    /**
     * @return the index of the node with the given name in the specified
     * CgIm.
     */
    public int getCorrespondingCgDiscreteNodeDiscreteParentIndex(int discreteParentIndex, CgIm otherCgIm) {
    	Node node = getCgDiscreteNodeDiscreteParentNode(discreteParentIndex);
        String nodeName = node.getName();
        //System.out.println("getCorrespondingCgDiscreteNodeIndex.nodeName: " + nodeName);
        Node oldNode = otherCgIm.getNode(nodeName);
        //System.out.println("getCorrespondingCgDiscreteNodeIndex.oldNode: " + oldNode);
        return otherCgIm.getCgDiscreteNodeDiscreteParentNodeIndex(oldNode);
    }

    /**
     * @return the index of the node with the given name in the specified
     * CgIm.
     */
    public int getCorrespondingCgDiscreteNodeIndex(int nodeIndex, CgIm otherCgIm) {
    	Node node = getCgDiscreteNode(nodeIndex);
        String nodeName = node.getName();
        //System.out.println("getCorrespondingCgDiscreteNodeIndex.nodeName: " + nodeName);
        Node oldNode = otherCgIm.getNode(nodeName);
        //System.out.println("getCorrespondingCgDiscreteNodeIndex.oldNode: " + oldNode);
        return otherCgIm.getCgDiscreteNodeIndex(oldNode);
    }

    /**
     * @return the index of the node with the given name in the specified
     * CgIm.
     */
    public int getCorrespondingCgContinuousNodeIndex(int nodeIndex, CgIm otherCgIm) {
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
    	final int size = continuousParentArray.length + 1; // +1 self

        // columnIndex always equal to 0 for continuous rows
        cgContinuousNodeEdgeCoef[nodeIndex][rowIndex][0] = genereateNaNArray(size);
        cgContinuousNodeErrCovar[nodeIndex][rowIndex][0] = genereateNaNArray(size);
        cgContinuousNodeMeans[nodeIndex][rowIndex][0] = genereateNaNArray(size);
        cgContinuousNodeMeanStdDevs[nodeIndex][rowIndex][0] = genereateNaNArray(size);
    }
    
    private void randomizeContinuousRow(int nodeIndex, int rowIndex) {
    	int[] continuousParentArray = cgContinuousNodeContinuousParentArray[nodeIndex];
    	final int size = continuousParentArray.length + 1;
    	final double coefLow = getParams().getDouble("coefLow", .5);
        final double coefHigh = getParams().getDouble("coefHigh", 1.5);
        final double covLow = getParams().getDouble("covLow", 1);
        final double covHigh = getParams().getDouble("covHigh", 3);
    	
    	cgContinuousNodeEdgeCoef[nodeIndex][rowIndex][0] = getRandomWeights(size,coefLow,coefHigh);
    	cgContinuousNodeErrCovar[nodeIndex][rowIndex][0] = getRandomWeights(size,covLow,covHigh);
    	cgContinuousNodeMeans[nodeIndex][rowIndex][0] = getRandomWeights(size,coefLow,coefHigh);
    	cgContinuousNodeMeanStdDevs[nodeIndex][rowIndex][0] = getRandomPositiveWeights(size,covLow,covHigh);
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
        int numCols = getCgDiscreteNumColumns(nodeIndex);
        final int size = getCgDiscreteNodeNumContinuousParents(nodeIndex);
        
        for(int colIndex=0;colIndex<numCols;colIndex++) {
            cgDiscreteNodeProbs[nodeIndex][rowIndex][colIndex] = genereateNaNArray(size);
            cgDiscreteNodeErrCovar[nodeIndex][rowIndex][colIndex] = genereateNaNArray(size);
            cgDiscreteNodeMeans[nodeIndex][rowIndex][colIndex] = genereateNaNArray(size);
            cgDiscreteNodeMeanStdDevs[nodeIndex][rowIndex][colIndex] = genereateNaNArray(size);
        }
        
    }
    
    private double[] genereateNaNArray(int size) {
    	double[] row = new double[size];
        Arrays.fill(row, Double.NaN);
        return row;
    }

	/**
     * @return this number.
     * @see #getNumRows
     */
    public int getCgDiscreteNumColumns(int nodeIndex) {
    	//System.out.println("cgDiscreteNodeMeans.numNodes: " + cgDiscreteNodeMeans.length);
    	//System.out.println("cgDiscreteNodeMeans.numNodes.rows: " + cgDiscreteNodeMeans[nodeIndex].length);
    	//System.out.println("cgDiscreteNodeMeans.numNodes.rows.cols: " + cgDiscreteNodeMeans[nodeIndex][0].length);
        return cgDiscreteNodeMeans[nodeIndex][0].length;
    }

	/**
     * @return this number.
     * @see #getNumRows
     */
    public int getCgContinuousNumColumns(int nodeIndex) {
        return cgContinuousNodeEdgeCoef[nodeIndex][0].length;
    }

    /**
     * @return this number.
     * @see #getRowIndex
     * @see #getNumColumns
     */
    public int getCgDiscreteNumRows(int nodeIndex) {;
        return cgDiscreteNodeProbs[nodeIndex].length;
    }

    /**
     * @return this number.
     * @see #getRowIndex
     * @see #getNumColumns
     */
    public int getCgContinuousNumRows(int nodeIndex) {
        return cgContinuousNodeEdgeCoef[nodeIndex].length;
    }

    
    /**
     * @param nodeIndex the given node.
     * @return the number of parents for this node.
     */
    public int getCgDiscreteNodeNumDiscreteParents(int nodeIndex) {
        return cgDiscreteNodeDiscreteParentArray[nodeIndex].length;
    }
	
    /**
     * @param nodeIndex the given node.
     * @return the number of parents for this node.
     */
    public int getCgDiscreteNodeNumContinuousParents(int nodeIndex) {
        return cgDiscreteNodeContinuousParentArray[nodeIndex].length;
    }
	
    /**
     * @param nodeIndex the given node.
     * @return the number of parents for this node.
     */
    public int getCgContinuousNodeNumDiscreteParents(int nodeIndex) {
        return cgContinuousNodeDiscreteParentArray[nodeIndex].length;
    }
	
    /**
     * @param nodeIndex the given node.
     * @return the number of parents for this node.
     */
    public int getCgContinuousNodeNumContinuousParents(int nodeIndex) {
        return cgContinuousNodeContinuousParentArray[nodeIndex].length;
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
     * @return this array of parent dimensions.
     * @see #getParents
     */
    public int[] getCgContinuousNodeDiscreteParentDims(int nodeIndex) {
        int[] dims = cgContinuousNodeDims[nodeIndex];
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
    	System.out.println("*********************************************************");
    	System.out.println("getCgDiscreteNodeContinuousParentNodeArray.nodeIndex: " + nodeIndex);
    	
        int[] nodeParents = cgDiscreteNodeContinuousParentArray[nodeIndex];
        if(nodeParents != null) {
        	for(int i=0;i<nodeParents.length;i++) {
        		int continuousParentNodeIndex = nodeParents[i];
        		Node node = cgDiscreteNodeContinuousParentNodes[continuousParentNodeIndex];
        		
        		System.out.println("continuousParentNode: " + node + " continuousParentNodeIndex: " + continuousParentNodeIndex);
        	}
        }
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
    public int getCgDiscreteNodeDiscreteParentValue(int nodeIndex, int rowIndex, int colIndex) {
        return getCgDiscreteNodeDiscreteParentValues(nodeIndex, rowIndex)[colIndex];
    }

    /**
     * @param nodeIndex the index of the node.
     * @param rowIndex  the index of the row in question.
     * @return the array representing the combination of parent values for this
     * row.
     * @see #getNodeIndex
     * @see #getRowIndex
     */
    public int[] getCgContinuousNodeDiscreteParentValues(int nodeIndex, int rowIndex) {
        int[] dims = getCgContinuousNodeDiscreteParentDims(nodeIndex);
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
    public int getCgContinuousNodeDiscreteParentValue(int nodeIndex, int rowIndex, int colIndex) {
        return getCgContinuousNodeDiscreteParentValues(nodeIndex, rowIndex)[colIndex];
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
    	Node node = cgDiscreteNodes[nodeIndex];
    	Node continuousParentNode = cgDiscreteNodeContinuousParentNodes[cgDiscreteNodeContinuousParentArray[nodeIndex][0]];
    	System.out.println("cgDiscreteNode: " + node.getName() + " continuousParentNode: " + continuousParentNode.getName());
    	System.out.println("getCgDiscreteNodeProbability: cgDiscreteNodeProbs[" + nodeIndex + "][" + + rowIndex + "][" + colIndex + "][" + + 0 + "] = " + cgDiscreteNodeProbs[nodeIndex][rowIndex][colIndex][0]);
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
		int numCols = getCgDiscreteNumColumns(nodeIndex);
		int[] continuousParentArray = cgDiscreteNodeContinuousParentArray[nodeIndex];
		final int size = continuousParentArray.length;
    	final double coefLow = getParams().getDouble("coefLow", .5);
        final double coefHigh = getParams().getDouble("coefHigh", 1.5);
        final double covLow = getParams().getDouble("covLow", 1);
        final double covHigh = getParams().getDouble("covHigh", 3);
    	
        cgDiscreteNodeProbs[nodeIndex][rowIndex] = getRandomProbability(numCols);
        
		for(int colIndex=0;colIndex<numCols;colIndex++) {
			cgDiscreteNodeErrCovar[nodeIndex][rowIndex][colIndex] =  getRandomWeights(size,covLow,covHigh);
			cgDiscreteNodeMeans[nodeIndex][rowIndex][colIndex] = getRandomWeights(size,coefLow,coefHigh);
            cgDiscreteNodeMeanStdDevs[nodeIndex][rowIndex][colIndex] = getRandomPositiveWeights(size,covLow,covHigh);
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
    	Graph graph = getCgPm().getGraph();
    	Node _node = graph.getNode(node.getName());
        if(_node != null && cgDiscreteNodeIndex.containsKey(_node)) {
        	//System.out.println("getCgDiscreteNodeIndex.cgDiscreteNodeIndex.containsKey(node): true");
        	int nodeIndex = cgDiscreteNodeIndex.get(_node).intValue();
        	//System.out.println("getCgDiscreteNodeIndex.nodeIndex: " + nodeIndex);
        	return nodeIndex;
        } else {
        	System.out.println("++++++++++++++++++++++");
        	System.out.println("cgDiscreteNodeIndex.containsKey(_node) == null: node: " + node.getName());
        	for(Node n : cgDiscreteNodeIndex.keySet()) {
        		System.out.println("node: " + n.getName() + " cgDiscreteNodeIndex: " + cgDiscreteNodeIndex.get(n));
        	}
        }

        //System.out.println("getCgDiscreteNodeIndex.cgDiscreteNodeIndex.containsKey(node): false");
        return -1;
    }
    
    private static double[][] getRandomProbability(int numColumns) {
    	assert numColumns >= 0;
    	
    	double[][] probs = new double[numColumns][1];
    	double sum = 0.0;
    	
    	for (int i = 0; i < numColumns; i++) {
    		double v = RandomUtil.getInstance().nextBeta(1, numColumns - 1);
    		probs[i][0] = v;// > 0.5 ? 2 * v : 0.5 * v;
            sum += probs[i][0];
        }
    	
    	for (int i = 0; i < numColumns; i++) {
    		probs[i][0] /= sum;
    	}
    	
    	return probs;
    }

    private static double[] getRandomPositiveWeights(int size, double lowerBound, double upperBound) {
    	double[] row = getRandomWeights(size, lowerBound, upperBound);
    	for (int i = 0; i < size; i++) {
    		row[i] = Math.abs(row[i]);
    	}
    	return row;
    }
    
    private static double[] getRandomWeights(int size, double lowerBound, double upperBound) {
        assert size >= 0;

        double[] row = new double[size];
        for (int i = 0; i < size; i++) {
            double v = new Split(lowerBound, upperBound).nextRandom();
            row[i] = v;
        }
    	return row;
    }
    
	@Override
	public DataSet simulateData(int sampleSize, boolean latentDataSaved) {
		// Get a tier ordering and convert it to an int array.
        Graph _graph = getCgPm().getGraph();
        
        if (_graph.existsDirectedCycle()) {
            throw new IllegalArgumentException("Graph must be acyclic to simulate from Conditional Gaussian net.");
        }
        
        List<Node> nodes = new ArrayList<>();
        List<Node> discreteNodes = new ArrayList<>();
        List<Node> continuousNodes = new ArrayList<>();
        
        for(Node _node : _graph.getNodes()) {
        	Node node;
        	if(_node instanceof DiscreteVariable) {
        		int numCategories = ((DiscreteVariable)_node).getNumCategories();
        		numCategories = numCategories < 3?2:numCategories;
        		
        		node = new DiscreteVariable(_node.getName(), numCategories);
        		discreteNodes.add(node);
        	}else {
        		node = new ContinuousVariable(_node.getName());
        		continuousNodes.add(node);
        	}
        	nodes.add(node);
        }
        
        Graph graph = new EdgeListGraph(nodes);
        
        for(Edge _edge : _graph.getEdges()) {
        	Node n1 = _edge.getNode1();
        	Node n2 = _edge.getNode2();
        	Edge edge = new Edge(graph.getNode(n1.getName()),graph.getNode(n2.getName()),_edge.getEndpoint1(),_edge.getEndpoint2());
        	graph.addEdge(edge);
        }
        
        for(Node node : nodes) {
        	if(node instanceof DiscreteVariable) {
        		discreteNodes.add(node);
        	}else {
        		continuousNodes.add(node);
        	}
        }
        //System.out.println("discreteNodes: " + discreteNodes.size() + " continuousNodes: " + continuousNodes.size());
        
        Graph discreteGraph = graph.subgraph(discreteNodes);
        Graph continuousGraph = graph.subgraph(continuousNodes);
        
        // Continuous parent - discrete child map
        Map<ContinuousVariable, DiscreteVariable> erstatzNodes = new HashMap<>();
        Map<String, ContinuousVariable> erstatzNodesReverse = new HashMap<>();
        
        for(Node node : discreteNodes) {
        	for(Node parentNode : graph.getParents(node)) {
        		if(parentNode instanceof ContinuousVariable) {
        			DiscreteVariable ersatz = erstatzNodes.get(parentNode);

                    if (ersatz == null) {
                        ersatz = new DiscreteVariable("Ersatz_" + parentNode.getName(), RandomUtil.getInstance().nextInt(3) + 2);
                        erstatzNodes.put((ContinuousVariable) parentNode, ersatz);
                        erstatzNodesReverse.put(ersatz.getName(), (ContinuousVariable) parentNode);
                        discreteGraph.addNode(ersatz);
                    }

                    discreteGraph.addDirectedEdge(ersatz, node);
        		}
        	}
        }
        
        BayesPm bayesPm = new BayesPm(discreteGraph);
        BayesIm bayesIm = new MlBayesIm(bayesPm, MlBayesIm.RANDOM);
        
        SemPm semPm = new SemPm(continuousGraph);
        
        DataSet mixedData = new BoxDataSet(new MixedDataBox(nodes, sampleSize), nodes);
        //System.out.println("mixedData: " + mixedData);
        
        List<Node> tierOrdering = graph.getCausalOrdering();

        int[] tiers = new int[tierOrdering.size()];

        for (int t = 0; t < tierOrdering.size(); t++) {
            tiers[t] = nodes.indexOf(tierOrdering.get(t));
            //System.out.println("tiers[" + t + "]: " + tiers[t]);
        }
        
        Map<CgCombination, Double> paramValues = new HashMap<>();
        
        Map<Integer, double[]> breakpointsMap = new HashMap<>();
        
        for (int mixedIndex : tiers) {
        	Node node = nodes.get(mixedIndex);
        	boolean isDiscreteVar = true;

        	Node _node = discreteGraph.getNode(node.getName());
        	if(_node == null) {
        		isDiscreteVar = false;
        	}
        	
        	
            for (int sampleNo = 0; sampleNo < sampleSize; sampleNo++) {
            	
            	//System.out.println("node: " + node + " mixedIndex: " + mixedIndex  + " sampleNo: " + sampleNo);
            	
            	if(isDiscreteVar) {
        			int bayesIndex = bayesIm.getNodeIndex(node);
        			int numParents = bayesIm.getNumParents(bayesIndex);
        			int rowIndex = 0;
        			
        			if(numParents > 0) {
            			int[] bayesParents = bayesIm.getParents(bayesIndex);
                        int[] parentValues = new int[bayesParents.length];

                        for (int k = 0; k < parentValues.length; k++) {
                            int bayesParentColumn = bayesParents[k];

                            Node bayesParent = bayesIm.getVariables().get(bayesParentColumn);
                            DiscreteVariable _parent = (DiscreteVariable) bayesParent;
                            int value;

                            //System.out.println("bayesParent: " + bayesParent);
                            
                            ContinuousVariable orig = erstatzNodesReverse.get(_parent.getName());

                            // Continuous Parent - Discrete Child
                            if (orig != null) {
                                int mixedParentColumn = mixedData.getColumn(orig);
                                double d = mixedData.getDouble(sampleNo, mixedParentColumn);
                                double[] breakpoints = breakpointsMap.get(mixedParentColumn);

                                if (breakpoints == null) {
                                    breakpoints = getBreakpoints(mixedData, _parent, mixedParentColumn);
                                    breakpointsMap.put(mixedParentColumn, breakpoints);
                                }

                                value = breakpoints.length;

                                for (int j = 0; j < breakpoints.length; j++) {
                                    if (d < breakpoints[j]) {
                                        value = j;
                                        break;
                                    }
                                }
                            } else {// Bayes
                                int mixedColumn = mixedData.getColumn(bayesParent);
                                value = mixedData.getInt(sampleNo, mixedColumn);
                            }

                            parentValues[k] = value;
                        }
                        
                        rowIndex = bayesIm.getRowIndex(bayesIndex, parentValues);
        			}
        			
                    double sum = 0.0;

                    double r = RandomUtil.getInstance().nextDouble();
                    mixedData.setInt(sampleNo, mixedIndex, 0);
                    
                    for (int k = 0; k < bayesIm.getNumColumns(bayesIndex); k++) {
                        double probability = bayesIm.getProbability(bayesIndex, rowIndex, k);
                        sum += probability;

                        if (sum >= r) {
                            mixedData.setInt(sampleNo, mixedIndex, k);
                            break;
                        }
                    }
            		
            	} else {
            		Set<DiscreteVariable> discreteParents = new HashSet<>();
                    Set<ContinuousVariable> continuousParents = new HashSet<>();

                    for (Node parentNode : graph.getParents(node)) {
                        if (parentNode instanceof DiscreteVariable) {
                            discreteParents.add((DiscreteVariable) parentNode);
                        } else {
                            continuousParents.add((ContinuousVariable) parentNode);
                        }
                    }
                    
                    // These parameters should be retrieved from 
                    //global semPm or cgPm
                    Parameter varParam = semPm.getParameter(node, node);
                    Parameter muParam = semPm.getMeanParameter(node);

                    Node __node = getDag().getNode(node.getName());
                    
                	Parameter _varParam = getSemIm().getSemPm().getParameter(__node, __node);
                	Parameter _muParam = getSemIm().getSemPm().getMeanParameter(__node);
                	
                	if(_varParam != null) {
                		Distribution distribution = _varParam.getDistribution();
                		double startingValue = _varParam.getStartingValue();
                		varParam.setDistribution(distribution);
                		varParam.setStartingValue(startingValue);
                	}
                	
                	if(_muParam != null) {
                		Distribution distribution = _muParam.getDistribution();
                		double startingValue = _muParam.getStartingValue();
                		varParam.setDistribution(distribution);
                		varParam.setStartingValue(startingValue);
                	}
                    
                    CgCombination varComb = new CgCombination(varParam);
                    CgCombination muComb = new CgCombination(muParam);

                    for (DiscreteVariable discreteVar : discreteParents) {
                        varComb.addParamValue(discreteVar, mixedData.getInt(sampleNo, mixedData.getColumn(discreteVar)));
                        muComb.addParamValue(discreteVar, mixedData.getInt(sampleNo, mixedData.getColumn(discreteVar)));
                    }
                    
                    double value = RandomUtil.getInstance().nextNormal(0, getParamValue(varComb, paramValues));
                    
                    for(Node parentNode : continuousParents) {
                    	Parameter coefParam = semPm.getParameter(parentNode, node);
                    	CgCombination coefComb = new CgCombination(coefParam);
                    	
                    	for (DiscreteVariable discreteVar : discreteParents) {
                    		coefComb.addParamValue(discreteVar, mixedData.getInt(sampleNo, mixedData.getColumn(discreteVar)));
                    	}
                    	
                    	int parentIndex = nodes.indexOf(parentNode);
                    	double parentValue = mixedData.getDouble(sampleNo, parentIndex);
                    	double parentCoef = getParamValue(coefComb, paramValues);
                        value += parentValue * parentCoef;
                    }
                    
                    value += getParamValue(muComb, paramValues);
                    mixedData.setDouble(sampleNo, mixedIndex, value);
            	}
            	
            }
        }
        
        return latentDataSaved ? mixedData : DataUtils.restrictToMeasured(mixedData);
	}
	
    private double[] getBreakpoints(DataSet mixedData, DiscreteVariable _parent, int mixedParentColumn) {
        double[] data = new double[mixedData.getNumRows()];

        for (int r = 0; r < mixedData.getNumRows(); r++) {
            data[r] = mixedData.getDouble(r, mixedParentColumn);
        }

        return Discretizer.getEqualFrequencyBreakPoints(data, _parent.getNumCategories());
    }

    private Double getParamValue(CgCombination values, Map<CgCombination, Double> map) {
        Double d = map.get(values);

        if (d == null) {
            Parameter parameter = values.getParameter();
            
        	final double coefLow = getParams().getDouble("coefLow", .5);
            final double coefHigh = getParams().getDouble("coefHigh", 1.5);
            final double varLow = getParams().getDouble("varLow", 1);
            final double varHigh = getParams().getDouble("varHigh", 3);
            final double meanLow = getParams().getDouble("meanLow", -1);
            final double meanHigh = getParams().getDouble("meanHigh", 1);
            final boolean coefSymmetric = getParams().getBoolean("coefSymmetric", true);

            if (parameter.getType() == ParamType.VAR) {
                d = RandomUtil.getInstance().nextUniform(varLow, varHigh);
                map.put(values, d);
            } else if (parameter.getType() == ParamType.COEF) {
                double min = coefLow;
                double max = coefHigh;
                double value = RandomUtil.getInstance().nextUniform(min, max);
                d = RandomUtil.getInstance().nextUniform(0, 1) < 0.5 && coefSymmetric ? -value : value;
                map.put(values, d);
            } else if (parameter.getType() == ParamType.MEAN) {
                d = RandomUtil.getInstance().nextUniform(meanLow, meanHigh);
                map.put(values, d);
            }
        }

        return d;
    }


	@Override
	public DataSet simulateData(int sampleSize, long sampleSeed, boolean latentDataSaved) {
		RandomUtil random = RandomUtil.getInstance();
		long _seed = random.getSeed();
		DataSet dataSet = simulateData(sampleSize, latentDataSaved);
        random.revertSeed(_seed);
        return dataSet;
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
	
	public SemIm getSemIm() {
		return semIm;
	}

	public void setSemIm(SemIm semIm) {
		this.semIm = semIm;
	}
	
	public Graph getDag() {
		return cgPm.getGraph();
	}
	
	public int getCgDiscreteNumNodes() {
		return cgDiscreteNodes.length;
	}
	
	public int getCgContinuousNumNodes() {
		return cgContinuousNodes.length;
	}
	
	public Node getCgDiscreteNode(int nodeIndex) {
		//System.out.println("getCgDiscreteNode.nodeIndex: " + nodeIndex);
		//System.out.println("getCgDiscreteNode.cgDiscreteNodes.length: " + cgDiscreteNodes.length);
		//System.out.println("getCgDiscreteNode.cgDiscreteNodes[nodeIndex]: " + cgDiscreteNodes[nodeIndex]);
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

        for (int i = 0; i < getCgDiscreteNumNodes(); i++) {
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

    public List<Node> getDiscreteNodes() {
    	return discreteNodes;
    }
    
    public List<Node> getContinuousNodes() {
    	return continuousNodes;
    }
    
    public List<Node> getCgDiscreteChildVariableNodes() {
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

	public int getCgDiscreteNodeDiscreteParentNodeIndex(Node discreteParentNode) {
		if(cgDiscreteNodeDiscreteParentNodeIndex.containsKey(discreteParentNode)) {
			return cgDiscreteNodeDiscreteParentNodeIndex.get(discreteParentNode).intValue();
		}
		return -1;
	}
	
	public Node getCgDiscreteNodeContinuousParentNode(int continuousParentIndex) {
		//System.out.println("cgDiscreteNodeContinuousParentNodes.length:" + cgDiscreteNodeContinuousParentNodes.length);
		return cgDiscreteNodeContinuousParentNodes[continuousParentIndex];
	}

	public Node getCgContinuousNodeDiscreteParentNode(int discreteParentIndex) {
		return cgContinuousNodeDiscreteParentNodes[discreteParentIndex];
	}

	public Node getCgContinuousNodeContinuousParentNode(int continuousParentIndex) {
		return cgContinuousNodeContinuousParentNodes[continuousParentIndex];
	}

    /**
     * @return the number of nodes in the model.
     */
    public int getNumNodes() {
        return getCgPm().getGraph().getNumNodes();
    }

    public boolean isCyclic() {
    	if (!cyclicChecked) {
            this.cyclic = cgPm.getGraph().existsDirectedCycle();
            cyclicChecked = true;
        }

        return cyclic;
    }
    
    public double getCgContinuousIntercept(Node node, int conditionalCaseNo) {
        if (isCyclic()) {
            return Double.NaN;
        }
        int nodeIndex = getCgContinuousNodeIndex(node);
        if(nodeIndex == -1) {
        	return Double.NaN;
        }
        
        double weightedSumOfSelfandParentMeans = 0.0;
    	
        int rowIndex = conditionalCaseNo;
    	int numParents = getCgContinuousNodeNumContinuousParents(nodeIndex);
    	
    	for(int i=0;i<numParents;i++) {
    		double coef = getCgContinuousNodeContinuousParentEdgeCoef(nodeIndex, rowIndex, i+1);
    		double mean = getCgContinuousNodeContinuousParentMean(nodeIndex, rowIndex, i+1);
    		
    		weightedSumOfSelfandParentMeans += coef * mean;
    	}

    	double mean = getCgContinuousNodeContinuousParentMean(nodeIndex, rowIndex, 0); // self
    	double intercept = mean - weightedSumOfSelfandParentMeans;
        
    	return round(intercept, 10);
    }
    
    private double round(double value, int decimalPlace) {
        double power_of_ten = 1;
        while (decimalPlace-- > 0) {
            power_of_ten *= 10.0;
        }
        return Math.round(value * power_of_ten)
                / power_of_ten;
    }

  public void setCgContinuousIntercept(Node node, int conditionalCaseNo, double intercept) {
    	if (isCyclic()) {
            throw new UnsupportedOperationException("Setting and getting of "
                    + "intercepts is supported for acyclic CGs only. The internal "
                    + "parameterizations uses variable means; the relationship "
                    + "between variable means and intercepts has not been fully "
                    + "worked out for the cyclic case.");
        }
        int nodeIndex = getCgContinuousNodeIndex(node);
        if(nodeIndex == -1) {
        	throw new UnsupportedOperationException("" + node + " is not found!");
        }
    	
        double weightedSumOfSelfandParentMeans = 0.0;
    	
        int rowIndex = conditionalCaseNo;
    	int numParents = getCgContinuousNodeNumContinuousParents(nodeIndex);
    	
    	for(int i=0;i<numParents;i++) {
    		double coef = getCgContinuousNodeContinuousParentEdgeCoef(nodeIndex, rowIndex, i+1);
    		double mean = getCgContinuousNodeContinuousParentMean(nodeIndex, rowIndex, i+1);
    		
    		weightedSumOfSelfandParentMeans += coef * mean;
    	}
        
    	double mean = weightedSumOfSelfandParentMeans + intercept;
	   	setCgContinuousNodeContinuousParentMean(nodeIndex, rowIndex, 0, mean);	
    }
    
    public boolean equals(Object o) {
        if (o == this) {
        	//System.out.println("equals: o == this");
            return true;
        }

        if (!(o instanceof CgIm)) {
        	//System.out.println("equals: !(o instanceof CgIm)");
            return false;
        }

        CgIm otherIm = (CgIm) o;

        if (getNumNodes() != otherIm.getNumNodes()) {
        	//System.out.println("equals: getNumNodes() != otherIm.getNumNodes()");
            return false;
        }
        
        // BayesIm
        BayesIm otherBayesIm = otherIm.getBayesIm();

        for (int i = 0; i < getBayesIm().getNumNodes(); i++) {
            int otherIndex = otherBayesIm.getCorrespondingNodeIndex(i, otherBayesIm);

            if (otherIndex == -1) {
            	//System.out.println("equals: BayesIm.otherIndex == -1");
                return false;
            }

            if (getBayesIm().getNumColumns(i) != otherBayesIm.getNumColumns(otherIndex)) {
            	//System.out.println("equals: getBayesIm().getNumColumns(i) != otherBayesIm.getNumColumns(otherIndex)");
                return false;
            }

            if (getBayesIm().getNumRows(i) != otherBayesIm.getNumRows(otherIndex)) {
            	//System.out.println("equals: getBayesIm().getNumRows(i) != otherBayesIm.getNumRows(otherIndex)");
                return false;
            }

            for (int j = 0; j < getBayesIm().getNumRows(i); j++) {
                for (int k = 0; k < getBayesIm().getNumColumns(i); k++) {
                    double prob = getBayesIm().getProbability(i, j, k);
                    double otherProb = otherBayesIm.getProbability(i, j, k);

                    if (Double.isNaN(prob) && Double.isNaN(otherProb)) {
                        continue;
                    }

                    if (abs(prob - otherProb) > ALLOWABLE_DIFFERENCE) {
                      	//System.out.println("equals: abs(prob - otherProb) > ALLOWABLE_DIFFERENCE");
                        return false;
                    }
                }
            }
        }
        
        // cgDiscreteNodes
        for(int i=0;i<getCgDiscreteNumNodes();i++) {
        	int otherIndex = otherIm.getCorrespondingCgDiscreteNodeIndex(i, otherIm);
        	
        	if (otherIndex == -1) {
              	//System.out.println("equals: cgDiscreteNodes.otherIndex == -1");
                return false;
            }

        	if(getCgDiscreteNumColumns(i) != otherIm.getCgDiscreteNumColumns(i)) {
              	//System.out.println("equals: getCgDiscreteNumColumns(i) != otherIm.getCgDiscreteNumColumns(i)");
        		return false;
        	}
        	
        	if(getCgDiscreteNumRows(i) != otherIm.getCgDiscreteNumRows(i)) {
              	//System.out.println("equals: getCgDiscreteNumRows(i) != otherIm.getCgDiscreteNumRows(i)");
        		return false;
        	}
        	
        	for(int j=0;j<getCgDiscreteNumRows(i);j++) {
        		for(int k=0;k<getCgDiscreteNumColumns(i);k++) {
    				double prob  = getCgDiscreteNodeProbability(i, j, k);
    				double otherProb = otherIm.getCgDiscreteNodeProbability(i, j, k);
    				
    				if (Double.isNaN(prob) && Double.isNaN(otherProb)) {
                        //continue;
                    } else if (abs(prob - otherProb) > ALLOWABLE_DIFFERENCE) {
                      	//System.out.println("equals: cgDiscreteNodes.abs(prob - otherProb) > ALLOWABLE_DIFFERENCE");
                        return false;
                    }
                    
        			for(int l=0;l<getCgDiscreteNodeContinuousParentNodeArray(i).length;l++) {
        				double errCov = getCgDiscreteNodeContinuousParentErrCovar(i, j, k, l);
        				double otherErrCov = otherIm.getCgDiscreteNodeContinuousParentErrCovar(i, j, k, l);
        				
        				if (Double.isNaN(errCov) && Double.isNaN(otherErrCov)) {
                            //continue;
                        } else if (abs(errCov - otherErrCov) > ALLOWABLE_DIFFERENCE) {
                          	//System.out.println("equals: cgDiscreteNodes.abs(errCov - otherErrCov) > ALLOWABLE_DIFFERENCE");
                            return false;
                        }
        				
        				double mean = getCgDiscreteNodeContinuousParentMean(i, j, k, l);
        				double otherMean = otherIm.getCgDiscreteNodeContinuousParentMean(i, j, k, l);
        				
        				if (Double.isNaN(mean) && Double.isNaN(otherMean)) {
                            //continue;
                        } else if (abs(mean - otherMean) > ALLOWABLE_DIFFERENCE) {
                          	//System.out.println("equals: cgDiscreteNodes.abs(mean - otherMean) > ALLOWABLE_DIFFERENCE");
                            return false;
                        }
        				
        				double stdDev = getCgDiscreteNodeContinuousParentMeanStdDev(i, j, k, l);
        				double otherStdDev = otherIm.getCgDiscreteNodeContinuousParentMeanStdDev(i, j, k, l);
        				
        				if (Double.isNaN(stdDev) && Double.isNaN(otherStdDev)) {
                            //continue;
                        } else if (abs(stdDev - otherStdDev) > ALLOWABLE_DIFFERENCE) {
                          	//System.out.println("equals: cgDiscreteNodes.(abs(stdDev - otherStdDev) > ALLOWABLE_DIFFERENCE");
                            return false;
                        }
        			}
        		}
        	}
        }
        
        // cgContinuousNodes
        for(int i=0;i<getCgContinuousNumNodes();i++) {
        	
        	int otherIndex = otherIm.getCorrespondingCgContinuousNodeIndex(i, otherIm);
        	//System.out.println("cgContinuousNode: " +i + " otherIndex: " + otherIndex);
        	
        	if (otherIndex == -1) {
                return false;
            }

        	if(getCgContinuousNumRows(i) != otherIm.getCgContinuousNumRows(i)) {
        		//System.out.println("getCgContinuousNumRows(i) != otherIm.getCgContinuousNumRows(i)");
        		return false;
        	}
        	
        	for(int j=0;j<getCgContinuousNumRows(i);j++) {
        		//System.out.println("getCgContinuousNumRows(" +i + "): " + getCgContinuousNumRows(i));
    			for(int k=0;k<getCgContinuousNodeNumContinuousParents(i)+1;k++) {
    				//System.out.println("getCgContinuousNodeNumContinuousParents(" +i + "): " + getCgContinuousNodeNumContinuousParents(i));
       				double coef  = getCgContinuousNodeContinuousParentEdgeCoef(i, j, k);
    				double otherCoef = otherIm.getCgContinuousNodeContinuousParentEdgeCoef(otherIndex, j, k);
    				//System.out.println("coef: " + coef + " other: " + otherCoef);
    				
    				if (Double.isNaN(coef) && Double.isNaN(otherCoef)) {
                        //continue;
                    } else if (abs(coef - otherCoef) > ALLOWABLE_DIFFERENCE) {
                        return false;
                    }
                    
        			double errCov = getCgContinuousNodeContinuousParentErrCovar(i, j, k);
    				double otherErrCov = otherIm.getCgContinuousNodeContinuousParentErrCovar(otherIndex, j, k);
    				//System.out.println("errCov: " + errCov + " other: " + otherErrCov);
    				
    				if (Double.isNaN(errCov) && Double.isNaN(otherErrCov)) {
                        //continue;
                    } else if (abs(errCov - otherErrCov) > ALLOWABLE_DIFFERENCE) {
                        return false;
                    }
    				
        			double mean = getCgContinuousNodeContinuousParentMean(i, j, k);
    				double otherMean = otherIm.getCgContinuousNodeContinuousParentMean(otherIndex, j, k);
    				//System.out.println("mean: " + mean + " other: " + otherMean);
    				
    				if (Double.isNaN(mean) && Double.isNaN(otherMean)) {
                        //continue;
                    } else if (abs(mean - otherMean) > ALLOWABLE_DIFFERENCE) {
                        return false;
                    }
    				
    				double stdDev = getCgContinuousNodeContinuousParentMeanStdDev(i, j, k);
    				double otherStdDev = otherIm.getCgContinuousNodeContinuousParentMeanStdDev(otherIndex, j, k);
    				//System.out.println("stdDev: " + stdDev + " other: " + otherStdDev);
    				
    				if (Double.isNaN(stdDev) && Double.isNaN(otherStdDev)) {
                        //continue;
                    } else if (abs(stdDev - otherStdDev) > ALLOWABLE_DIFFERENCE) {
                        return false;
                    }
    			}
        	}
        }

        return true;
    }

}
