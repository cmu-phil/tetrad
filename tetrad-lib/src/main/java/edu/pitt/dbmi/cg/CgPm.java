/**
 * 
 */
package edu.pitt.dbmi.cg;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import edu.cmu.tetrad.bayes.BayesPm;
import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataUtils;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.graph.Dag;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeType;
import edu.cmu.tetrad.sem.ParamType;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.PM;
import edu.cmu.tetrad.util.RandomUtil;
import edu.cmu.tetrad.util.TetradSerializable;
import edu.cmu.tetrad.util.dist.Normal;
import edu.cmu.tetrad.util.dist.Split;
import edu.cmu.tetrad.util.dist.Uniform;

/**
 * Apr 10, 2019 4:48:07 PM
 *
 * @author Chirayu Kong Wongchokprasitti, PhD (chw20@pitt.edu)
 *
 */
public final class CgPm implements PM, TetradSerializable {

	private static final long serialVersionUID = 1L;

    private Graph graph;
    
    private BayesPm bayesPm;
    
    private SemPm semPm;
    
    private int discreteLowerBound = 3;
    private int discreteUpperBound = 3;

    // All discrete nodes
    private Map<Node, DiscreteVariable> discreteNodesToVariables = new HashMap<>();
    // All continuous nodes
    private Map<Node, ContinuousVariable> continuousNodesToVariables = new HashMap<>();
    
    // Mixed parents with discrete child
	private List<Node> cgDiscreteVariableNodes = new ArrayList<>();
    // Mixed parents with continuous child
	private List<Node> cgContinuousVariableNodes = new ArrayList<>();
    
    /**
     * The list of Parameters (unmodifiable).
     *
     * @serial Cannot be null.
     */
    private List<CgParameter> cgDiscreteNodeParameters;

    private List<CgParameter> cgContinuousNodeParameters;

    /**
     * The index of the most recent "cgT" parameter. (These are variance and
     * covariance terms.)
     *
     * @serial Range >= 0.
     */
    private int tIndex = 0;

    /**
     * The index of the most recent "cgM" parameter. (These are means.)
     *
     * @serial Range >= 0.
     */
    private int mIndex = 0;

    /**
     * The index of the most recent "cgB" parameter. (These are edge
     * coefficients.)
     *
     * @serial Range >= 0.
     */
    private int bIndex = 0;
    
	public CgPm(Graph graph) {
		this.graph = graph;
		initializeNodes(graph);
        initializeParams();
	}
	
	public CgPm(CgPm cgPm) {
		this.graph = cgPm.getGraph();
		initializeNodes(graph);
		initializeParams();
	}

	public CgPm(Graph graph, int discreteLowerBound, int discreteUpperBound) {
		this.graph = graph;
		this.discreteLowerBound = discreteLowerBound;
		this.discreteUpperBound = discreteUpperBound;
		initializeNodes(graph);
        initializeParams();
        initializeDiscreteValues(discreteLowerBound, discreteUpperBound);
	}
	
	public CgPm(CgPm cgPm, int discreteLowerBound, int discreteUpperBound) {
		this.graph = cgPm.getGraph();
		this.discreteLowerBound = discreteLowerBound;
		this.discreteUpperBound = discreteUpperBound;
		initializeNodes(graph);
		initializeParams();
        initializeDiscreteValues(discreteLowerBound, discreteUpperBound);
	}

	private void initializeNodes(Graph graph) {
		List<Node> nodes = graph.getNodes();
		
		// SEM
		List<Node> contNodes = new ArrayList<>();
        // Bayes
		List<Node> discNodes = new ArrayList<>();
        
        for(Node node : nodes) {
        	if(node instanceof ContinuousVariable) {
    			continuousNodesToVariables.put(node, (ContinuousVariable) node);
        		
        		boolean allParentsContinuous = true;
        		for (Node x : graph.getParents(node)) {
        			if(x instanceof DiscreteVariable) {
        				allParentsContinuous = false;
        				break;
        			}
        		}
        		if(allParentsContinuous) {
        			if(!contNodes.contains(node)) {
        				contNodes.add(node);       				
        			}
        			for (Node x : graph.getParents(node)) {
        				if(!contNodes.contains(x)) {
        					contNodes.add(x);
        				}
        			}
        		}else {
        			cgContinuousVariableNodes.add(node);
        		}
        	}else { // Discrete Variable
        		discreteNodesToVariables.put(node, (DiscreteVariable) node);
        		
        		boolean allParentsDiscrete = true;
        		for(Node x : graph.getParents(node)) {
        			if(x instanceof ContinuousVariable) {
        				allParentsDiscrete = false;
        				break;
        			}
        		}
        		if(allParentsDiscrete) {
        			if(!discNodes.contains(node)) {
        				discNodes.add(node);
        			}
        			for (Node x : graph.getParents(node)) {
        				if(!discNodes.contains(x)) {
        					discNodes.add(x);
        				}
        			}
        		}else {
        			cgDiscreteVariableNodes.add(node);
        		}
        	}
        }
        
        Graph contGraph = graph.subgraph(contNodes);
        Graph discGraph = graph.subgraph(discNodes);
        
        this.bayesPm = new BayesPm(discGraph, discreteLowerBound, discreteUpperBound);
        this.semPm = new SemPm(contGraph);
	}
	
	private void initializeParams() {
		// Initialize Discrete Children with Mixed Parents
		initializeCgDiscreteNodeParams();
		// Initialize Continuous Children with Mixed Parents
		initializeCgContinuousNodeParams();
	}
	
	private void initializeCgDiscreteNodeParams() {
		
		List<CgParameter> parameters = new ArrayList<>();
		
		for(Node node : cgDiscreteVariableNodes) {
			
			List<Node> discreteParentNodes = new ArrayList<>();
			List<Node> continuousParentNodes = new ArrayList<>();

			int numConditionalCases = 1;
			
			for(Node parentNode : graph.getParents(node)) {
				if(parentNode instanceof DiscreteVariable) {
					if(!discreteParentNodes.contains(parentNode)) {
						discreteParentNodes.add(parentNode);
						
						DiscreteVariable discVar = (DiscreteVariable) parentNode;
						numConditionalCases *= discVar.getNumCategories();
					}
					
				}else {
					if(!continuousParentNodes.contains(parentNode)) {
						continuousParentNodes.add(parentNode);
					}	
				}
			}
			
			int numChildCategories = getDiscreteNumCategories(node);
			
			for(int i=0;i<numChildCategories;i++) {
				for(int j=0;j<numConditionalCases;j++) {
					for(Node parentNode : continuousParentNodes) {
						CgParameter mean = new CgParameter(newMName(), ParamType.MEAN, parentNode, node, i, j);
			            mean.setDistribution(new Normal(0.0, 1.0));
			            parameters.add(mean);
			            
			            CgParameter param = new CgParameter(newTName(), ParamType.COVAR, parentNode, node, i, j);
			            param.setDistribution(new Uniform(1.0, 3.0));
			            parameters.add(param);
					}
				}
			}
			
		}
		
		this.cgDiscreteNodeParameters = Collections.unmodifiableList(parameters);
	}
	
	private void initializeCgContinuousNodeParams() {
		
		List<CgParameter> parameters = new ArrayList<>();
		
		for(Node node : cgContinuousVariableNodes) {
			
			List<Node> discreteParentNodes = new ArrayList<>();
			List<Node> continuousParentNodes = new ArrayList<>();
			
			int numConditionalCases = 1;
			
			for(Node parentNode : graph.getParents(node)) {
				if(parentNode instanceof DiscreteVariable) {
					discreteParentNodes.add(parentNode);
					
					DiscreteVariable discVar = (DiscreteVariable) parentNode;
					numConditionalCases *= discVar.getNumCategories();
					
				}else {
					continuousParentNodes.add(parentNode);
				}
			}
			
			for(int i=0;i<numConditionalCases;i++) {
				CgParameter mean = new CgParameter(newMName(), ParamType.MEAN, node, node, i);
	            mean.setDistribution(new Normal(0.0, 1.0));
	            parameters.add(mean);
	            
	            CgParameter param = new CgParameter(newTName(), ParamType.VAR, node, node, i);
	            param.setDistribution(new Uniform(1.0, 3.0));
	            parameters.add(param);
	            
				for(Node parentNode : continuousParentNodes) {
					CgParameter coef = new CgParameter(newBName(), ParamType.COEF, parentNode, node, i);
					coef.setDistribution(new Split(0.5, 1.5));
					parameters.add(coef);
				}
			}			
			
		}
		
		this.cgContinuousNodeParameters = Collections.unmodifiableList(parameters);
	}
	
    public void initializeDiscreteValues(int discreteLowerBound, int discreteUpperBound) {
    	for(Node node : discreteNodesToVariables.keySet()) {
    		setDiscreteNewValues(node, discreteLowerBound, discreteUpperBound);
    	}
    }
    
	public CgParameter getCgContinuousCoefficientParameter(Node nodeA, Node nodeB, int conditionalCase) {

		for(CgParameter parameter : this.cgContinuousNodeParameters) {
			Node _nodeA = parameter.getNodeA();
            Node _nodeB = parameter.getNodeB();
            int caseNo = parameter.getConditionalCaseNo();

            if (nodeA == _nodeA && nodeB == _nodeB && parameter.getType() == ParamType.COEF && 
            		conditionalCase == caseNo) {
            	return parameter;
            }
		}
		
		return null;
	}
	
	public CgParameter getCgContinuousVarianceParameter(Node node, int conditionalCase) {
		if(node.getNodeType() == NodeType.ERROR) {
			node = getGraph().getChildren(node).get(0);
		}
		
		for(CgParameter parameter : this.cgContinuousNodeParameters) {
			Node _nodeA = parameter.getNodeA();
			Node _nodeB = parameter.getNodeB();
			int caseNo = parameter.getConditionalCaseNo();
			
			if(node == _nodeA && node == _nodeB && parameter.getType() == ParamType.VAR && 
            		conditionalCase == caseNo) {
				return parameter;
			}
		}
		
		return null;
	}
	
	public List<CgParameter> getCgContinuousFreeParameters(){
		List<CgParameter> freeParameters = new ArrayList<>();
		
		for(CgParameter parameter : this.cgContinuousNodeParameters) {
			ParamType type = parameter.getType();
			
			if (type == ParamType.VAR || type == ParamType.COVAR || type == ParamType.COEF) {
                if (!parameter.isFixed()) {
                    freeParameters.add(parameter);
                }
            }
		}
		
		return freeParameters;
	}
	
	public CgParameter getCgDiscreteVarianceParameter(Node nodeA, Node nodeB, int childCategoryNo, int conditionalCaseNo){
		for(CgParameter parameter : this.cgDiscreteNodeParameters) {
			Node _nodeA = parameter.getNodeA();
            Node _nodeB = parameter.getNodeB();
            int cateNo = parameter.getChildCategoryNo();
            int caseNo = parameter.getConditionalCaseNo();

            if (nodeA == _nodeA && nodeB == _nodeB && parameter.getType() == ParamType.COVAR && 
            		cateNo == childCategoryNo && conditionalCaseNo == caseNo) {
            	return parameter;
            }
		}
		
		return null;
	}
	
	public List<CgParameter> getCgDiscreteFreeParameters(){
		List<CgParameter> freeParameters = new ArrayList<>();
		
		for(CgParameter parameter : this.cgDiscreteNodeParameters) {
			ParamType type = parameter.getType();
			
			if (type == ParamType.VAR || type == ParamType.COVAR || type == ParamType.COEF) {
                if (!parameter.isFixed()) {
                    freeParameters.add(parameter);
                }
            }
		}
		
		return freeParameters;
	}
	
    /**
     * @return a unique (for this PM) parameter name beginning with CG (Conditional Gaussian) and the Greek
     * letter theta.
     */
    private String newTName() {
        return "cgT" + (++this.tIndex);
    }

    /**
     * @return a unique (for this PM) parameter name beginning with CG (Conditional Gaussian) and the Greek
     * letter mu.
     */
    private String newMName() {
        return "cgM" + (++this.mIndex);
    }

    /**
     * @return a unique (for this PM) parameter name beginning with CG (Conditional Gaussian) and the letter
     * "B".
     */
    private String newBName() {
        return "cgB" + (++this.bIndex);
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     */
    public static CgPm serializableInstance() {
        return new CgPm(Dag.serializableInstance());
    }

	public Graph getGraph() {
		return graph;
	}

	public BayesPm getBayesPm() {
		return bayesPm;
	}

	public SemPm getSemPm() {
		return semPm;
	}

	public List<Node> getCgDiscreteVariableNodes() {
		return cgDiscreteVariableNodes;
	}

	public List<Node> getCgContinuousVariableNodes() {
		return cgContinuousVariableNodes;
	}

	public List<Node> getCgContinuousMeasureNodes() {
		List<Node> measuredNodes = new ArrayList<>();
		
		for (Node variable : getCgContinuousVariableNodes()) {
            if (variable.getNodeType() == NodeType.MEASURED) {
                measuredNodes.add(variable);
            }
        }

        return measuredNodes;
	}
	
    /**
     * @return the list of latent variableNodes.
     */
    public List<Node> getLatentNodes() {
        List<Node> latentNodes = new ArrayList<>();

        for (Node node : getCgContinuousVariableNodes()) {
            if (node.getNodeType() == NodeType.LATENT) {
            	if(!latentNodes.contains(node)) {
                    latentNodes.add(node);
            	}
            }
        }

        for (Node node : getCgDiscreteVariableNodes()) {
            if (node.getNodeType() == NodeType.LATENT) {
            	if(!latentNodes.contains(node)) {
                    latentNodes.add(node);
            	}
            }
        }

        return latentNodes;
    }

	public List<CgParameter> getCgDiscreteNodesParameters() {
		return cgDiscreteNodeParameters;
	}

	public List<CgParameter> getCgContinuousNodesParameters() {
		return cgContinuousNodeParameters;
	}
	
    /**
     * @return the number of values for the given node.
     */
    public int getDiscreteNumCategories(Node node) {
        DiscreteVariable variable = discreteNodesToVariables.get(node);

        if (variable == null) {
            return 0;
        }

        return variable.getNumCategories();
    }
	
    /**
     * @return the index'th value for the given node.
     */
    public String getDiscreteCategory(Node node, int index) {
        DiscreteVariable variable = discreteNodesToVariables.get(node);

        if (variable != null) {
            return variable.getCategory(index);
        }

        for (DiscreteVariable _node : discreteNodesToVariables.values()) {
            if (_node == null) {
                continue;
            }

            if (_node.getName().equals(node.getName())) {
                return _node.getCategory(index);
            }
        }

        throw new IllegalStateException();
    }

    /**
     * @return the index of the given category for the given node.
     */
    public int getDiscreteCategoryIndex(Node node, String category) {
        DiscreteVariable variable = discreteNodesToVariables.get(node);
        return variable.getIndex(category);
    }

    /**
     * Sets the number of values for the given node to the given number.
     */
    public void setDiscreteNumCategories(Node node, int numCategories) {
        if (!discreteNodesToVariables.containsKey(node)) {
            throw new IllegalArgumentException("Node not in CgPm: " + node);
        }

        if (numCategories < 1) {
            throw new IllegalArgumentException(
                    "Number of categories must be >= 1: " + numCategories);
        }

        DiscreteVariable variable = discreteNodesToVariables.get(node);

        List<String> oldCategories = variable.getCategories();
        List<String> newCategories = new LinkedList<>();
        int min = Math.min(numCategories, oldCategories.size());

        for (int i = 0; i < min; i++) {
            newCategories.add(oldCategories.get(i));
        }

        for (int i = min; i < numCategories; i++) {
            String proposedName = DataUtils.defaultCategory(i);

            if (newCategories.contains(proposedName)) {
                throw new IllegalArgumentException("Default name already in " +
                        "list of categories: " + proposedName);
            }

            newCategories.add(proposedName);
        }

        mapDiscreteNodeToVariable(node, newCategories);
    }

    public void setDiscreteCategories(Node node, List<String> categories) {
    	mapDiscreteNodeToVariable(node, categories);
    }
    
    private void setDiscreteNewValues(Node node, int lowerBound, int upperBound) {
        if (node == null) {
            throw new NullPointerException("Node must not be null.");
        }

        List<String> valueList = new ArrayList<>();

        for (int i = 0; i < pickDiscreteNumVals(lowerBound, upperBound); i++) {
            valueList.add(DataUtils.defaultCategory(i));
        }

        mapDiscreteNodeToVariable(node, valueList);
    }
    
    private void mapDiscreteNodeToVariable(Node node, List<String> categories) {
        if (categories.size() != new HashSet<>(categories).size()) {
            throw new IllegalArgumentException("Duplicate variable names.");
        }

        DiscreteVariable variable =
                new DiscreteVariable(node.getName(), categories);

        variable.setNodeType(node.getNodeType());
        
        this.discreteNodesToVariables.put(node, variable);
        
        if(this.bayesPm.getDag().getNodes().contains(node)) {
        	this.bayesPm.setCategories(node, categories);
        }
    }

    private static int pickDiscreteNumVals(int lowerBound, int upperBound) {
        if (lowerBound < 2) {
            throw new IllegalArgumentException(
                    "Lower bound must be >= 2: " + lowerBound);
        }

        if (upperBound < lowerBound) {
            throw new IllegalArgumentException(
                    "Upper bound for number of categories must be >= lower " + "bound.");
        }

        int difference = upperBound - lowerBound;
        RandomUtil randomUtil = RandomUtil.getInstance();
        return randomUtil.nextInt(difference + 1) + lowerBound;
    }

}
