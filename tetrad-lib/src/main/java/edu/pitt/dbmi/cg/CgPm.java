/**
 * 
 */
package edu.pitt.dbmi.cg;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.cmu.tetrad.bayes.BayesPm;
import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.graph.Dag;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.sem.ParamType;
import edu.cmu.tetrad.sem.Parameter;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.PM;
import edu.cmu.tetrad.util.RandomUtil;
import edu.cmu.tetrad.util.TetradSerializable;
import edu.cmu.tetrad.util.dist.Normal;
import edu.cmu.tetrad.util.dist.SingleValue;
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

    private Map<Node, DiscreteVariable> discChildrenNodesToVariables;
    
    private Map<Node, ContinuousVariable> contChildrenNodesToVariables;
    
    /**
     * The list of Parameters (unmodifiable).
     *
     * @serial Cannot be null.
     */
    private List<Parameter> discChildrenNodesParameters;

    private List<Parameter> contChildrenNodesParameters;

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

	private void initializeNodes(Graph graph) {
		List<Node> nodes = graph.getNodes();
		
		// SEM
		List<Node> contNodes = new ArrayList<>();
        // Bayes
		List<Node> discNodes = new ArrayList<>();
        // Mixed parents with continuous child
		List<Node> contChildrenNodes = new ArrayList<>();
        // Mixed parents with discrete child
		List<Node> discChildrenNodes = new ArrayList<>();
        
        for(Node node : nodes) {
        	if(node instanceof ContinuousVariable) {
        		boolean allParentsContinuous = true;
        		for (Node x : graph.getParents(node)) {
        			if(x instanceof DiscreteVariable) {
        				allParentsContinuous = false;
        				break;
        			}
        		}
        		if(allParentsContinuous) {
            		contNodes.add(node);
        		}else {
        			contChildrenNodes.add(node);
        			contChildrenNodesToVariables.put(node, (ContinuousVariable) node);
        		}
        	}else { // Discrete Variable
        		boolean allParentsDiscrete = true;
        		for(Node x : graph.getParents(node)) {
        			if(x instanceof ContinuousVariable) {
        				allParentsDiscrete = false;
        				break;
        			}
        		}
        		if(allParentsDiscrete) {
            		discNodes.add(node);
        		}else {
        			discChildrenNodes.add(node);
        			discChildrenNodesToVariables.put(node, (DiscreteVariable) node);
        		}
        	}
        }
        
        Graph contGraph = graph.subgraph(contNodes);
        Graph discGraph = graph.subgraph(discNodes);
        
        this.bayesPm = new BayesPm(discGraph);
        this.semPm = new SemPm(contGraph);
	}
	
	private void initializeParams() {
		// Initialize Discrete Children with Mixed Parents
		initializeDiscreteChildrenParams();
		// Initialize Continuous Children with Mixed Parents
		initializeContinuousChildrenParams();
	}
	
	private void initializeDiscreteChildrenParams() {
		
		List<Parameter> parameters = new ArrayList<>();
		
		for(Node node : discChildrenNodesToVariables.keySet()) {
			
			DiscreteVariable discVar = discChildrenNodesToVariables.get(node);
			
			for (Node parentNode : graph.getParents(node)) {
    			if(parentNode instanceof DiscreteVariable) {
    				
    				
    				for(int i=0;i<discVar.getNumCategories();i++) {
        				Parameter mean = new Parameter(newMName(), ParamType.MEAN, parentNode, node);
        				mean.setDistribution(new Normal(0.0, 1.0));
        				parameters.add(mean);
        				
        				Parameter mean_parent = new Parameter(newMName(), ParamType.MEAN, parentNode, parentNode);
        				mean_parent.setDistribution(new Normal(0.0, 1.0));
        				parameters.add(mean_parent);
        				
        				Parameter param = new Parameter(newTName(), ParamType.VAR, node, node);
        	            param.setDistribution(new Uniform(1.0, 3.0));
        	            parameters.add(param);
        				
        				Parameter covar = new Parameter(newTName(), ParamType.COVAR, parentNode, node);
        				covar.setDistribution(new SingleValue(0.2));
                        parameters.add(covar);
                        
                        Parameter covar_parent = new Parameter(newTName(), ParamType.COVAR, parentNode, parentNode);
                        covar_parent.setDistribution(new SingleValue(0.2));
                        parameters.add(covar_parent);
    				}
    				
    			}
			}
			
		}
		
		this.discChildrenNodesParameters = Collections.unmodifiableList(parameters);
	}
	
	private void initializeContinuousChildrenParams() {
		
		List<Parameter> parameters = new ArrayList<>();
		
		for(Node node : contChildrenNodesToVariables.keySet()) {
			
			for(Node parentNode : graph.getParents(node)) {
				if(parentNode instanceof DiscreteVariable) {
					
					DiscreteVariable discVar = (DiscreteVariable) parentNode;
					
					for(int i=0;i<discVar.getNumCategories();i++) {
						Parameter coef = new Parameter(newBName(), ParamType.COEF, parentNode, node);
						coef.setDistribution(new Split(0.5, 1.5));
						parameters.add(coef);
						
						Parameter mean = new Parameter(newMName(), ParamType.MEAN, node, node);
			            mean.setDistribution(new Normal(0.0, 1.0));
			            parameters.add(mean);
						
						Parameter param = new Parameter(newTName(), ParamType.VAR, node, node);
			            param.setDistribution(new Uniform(1.0, 3.0));
			            parameters.add(param);
			            
					}
				}
			}
			
		}
		
		this.contChildrenNodesParameters = Collections.unmodifiableList(parameters);
	}
	
    /**
     * @return a unique (for this PM) parameter name beginning with the Greek
     * letter theta.
     */
    private String newTName() {
        return "cgT" + (++this.tIndex);
    }

    /**
     * @return a unique (for this PM) parameter name beginning with the Greek
     * letter mu.
     */
    private String newMName() {
        return "cgM" + (++this.mIndex);
    }

    /**
     * @return a unique (for this PM) parameter name beginning with the letter
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

	public Map<Node, DiscreteVariable> getDiscChildrenNodesToVariables() {
		return discChildrenNodesToVariables;
	}

	public Map<Node, ContinuousVariable> getContChildrenNodesToVariables() {
		return contChildrenNodesToVariables;
	}

	public List<Parameter> getDiscChildrenNodesParameters() {
		return discChildrenNodesParameters;
	}

	public List<Parameter> getContChildrenNodesParameters() {
		return contChildrenNodesParameters;
	}
	
}
