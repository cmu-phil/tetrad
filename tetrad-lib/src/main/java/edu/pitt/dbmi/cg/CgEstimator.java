/**
 * 
 */
package edu.pitt.dbmi.cg;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import edu.cmu.tetrad.bayes.BayesIm;
import edu.cmu.tetrad.bayes.BayesPm;
import edu.cmu.tetrad.bayes.DataSetProbs;
import edu.cmu.tetrad.bayes.DiscreteProbs;
import edu.cmu.tetrad.bayes.Evidence;
import edu.cmu.tetrad.bayes.MlBayesEstimator;
import edu.cmu.tetrad.bayes.MlBayesIm;
import edu.cmu.tetrad.bayes.Proposition;
import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.sem.SemEstimator;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.util.StatUtils;
import edu.cmu.tetrad.util.TetradMatrix;
import edu.cmu.tetrad.util.TetradSerializable;
import edu.cmu.tetrad.util.TetradVector;

/**
 * Apr 10, 2019 5:12:37 PM
 *
 * @author Chirayu Kong Wongchokprasitti, PhD (chw20@pitt.edu)
 *
 */
public final class CgEstimator implements TetradSerializable {

	private static final long serialVersionUID = 1L;

	private CgPm cgPm;
	
	private DataSet dataSet;
	
	private Graph graph;
	
	private Node[] nodes;
	
	public CgEstimator(CgIm cgIm, DataSet dataSet) {
		this(cgIm.getCgPm(), dataSet);
	}
	
	public CgEstimator(CgPm cgPm, DataSet dataSet) {
		
		if (cgPm == null) {
			throw new NullPointerException();
		}
		
		if (dataSet == null) {
			throw new NullPointerException();
		}
		
		this.cgPm = cgPm;
		this.dataSet = dataSet;
		this.graph = cgPm.getGraph();
		this.nodes = new Node[graph.getNumNodes()];
		int i = 0;
		for(Node node : graph.getNodes()) {
			this.nodes[i] = node;
			i++;
		}
		
	}

	public CgIm getEstimatedCgIm() {
		CgIm estimatedCgIm = new CgIm(cgPm);
		
		// SEM
		List<Node> contNodes = new ArrayList<>();
        // Bayes
		List<Node> discNodes = new ArrayList<>();
		
		List<Node> continuousNodes = estimatedCgIm.getContinuousNodes();
		for(Node _node : continuousNodes) {
			Node node = dataSet.getVariable(_node.getName());
			contNodes.add(node);
		}
		
		List<Node> cgDiscreteNodesAndTheirDiscreteParents = new ArrayList<>();
        
		List<Node> discreteNodes = estimatedCgIm.getDiscreteNodes();
		for(Node _node : discreteNodes) {
			Node node = dataSet.getVariable(_node.getName());
			discNodes.add(node);
			
			boolean allParentsDiscrete = true;
			
    		for(Node _parentNode : graph.getParents(_node)) {
    			if(_parentNode instanceof ContinuousVariable) {
    				allParentsDiscrete = false;
    				break;
    			}
    		}
    		if(!allParentsDiscrete) {
    			if(!cgDiscreteNodesAndTheirDiscreteParents.contains(node)) {
    				cgDiscreteNodesAndTheirDiscreteParents.add(node);
    			}
    			for (Node _parentNode : graph.getParents(_node)) {
    				Node parentNode = dataSet.getVariable(_parentNode.getName());
    				
        			if(parentNode instanceof DiscreteVariable && 
        					!cgDiscreteNodesAndTheirDiscreteParents.contains(parentNode)) {
        				cgDiscreteNodesAndTheirDiscreteParents.add(parentNode);
        			}
        		}
    		}
		}
		
		DataSet semDataSet = dataSet.subsetColumns(contNodes);
		DataSet bayesDataSet = dataSet.subsetColumns(discNodes);
        
		MlBayesEstimator bayesEstimator = new MlBayesEstimator();
		BayesIm estimatedBayesIm = bayesEstimator.estimate(cgPm.getBayesPm(), bayesDataSet);
		estimatedCgIm.setBayesIm(estimatedBayesIm);
		
		SemEstimator semEstimator = new SemEstimator(semDataSet, cgPm.getSemPm());
		SemIm  estimatedSemIm = semEstimator.estimate();
		estimatedCgIm.setSemIm(estimatedSemIm);
		
		// Conditional Gaussian Estimator
		// 1) Mixed Parent Discrete Child Node
		Graph cgBayesGraph = graph.subgraph(cgDiscreteNodesAndTheirDiscreteParents);
		BayesPm cgBayesPm = new BayesPm(cgBayesGraph);
		BayesIm estimatedCgBayesIm = new MlBayesIm(cgBayesPm);
		
		DataSet cgBayesDataSet = dataSet.subsetColumns(cgDiscreteNodesAndTheirDiscreteParents);
		DiscreteProbs cgBayesProbs = new DataSetProbs(cgBayesDataSet);
		
		Proposition assertion = Proposition.tautology(estimatedCgBayesIm);
		Proposition condition = Proposition.tautology(estimatedCgBayesIm);
		Evidence evidence = Evidence.tautology(estimatedCgBayesIm);
		
		int numCgBayesNodes = estimatedCgBayesIm.getNumNodes();
		
		for(int nodeIndex=0;nodeIndex<numCgBayesNodes;nodeIndex++) {
			
			Node cgDiscreteNode = estimatedCgBayesIm.getNode(nodeIndex);
			
			// Mixed parents with discrete child
			if(discreteNodes.contains(cgDiscreteNode)) {
				
				int cgDiscreteNodeIndex = estimatedCgIm.getCgDiscreteNodeIndex(cgDiscreteNode);
				
				int numRows = estimatedCgBayesIm.getNumRows(nodeIndex); // a total number of combination of discrete parent's values
				int numCols = estimatedCgBayesIm.getNumColumns(nodeIndex); // a number of node's categories
				int[] cgBayesParents = estimatedCgBayesIm.getParents(nodeIndex);
				
            	int[] cgContinuousParentArray = estimatedCgIm
            			.getCgDiscreteNodeContinuousParentNodeArray(
            					cgDiscreteNodeIndex);

				for(int row=0;row<numRows;row++) {
					
					int[] cgBayesParentValues = estimatedCgBayesIm.getParentValues(nodeIndex, row);
					
					for(int col=0;col<numCols;col++) {
						
						// Remove values from the proposition in various ways; if
	                    // a combination exists in the end, calculate a conditional
	                    // probability.
	                    assertion.setToTautology();
	                    condition.setToTautology();

	                    for (int i = 0; i < numCgBayesNodes; i++) {
	                        for (int j = 0; j < evidence.getNumCategories(i); j++) {
	                            if (!evidence.getProposition().isAllowed(i, j)) {
	                                condition.removeCategory(i, j);
	                            }
	                        }
	                    }

	                    assertion.disallowComplement(nodeIndex, col);

	                    for (int k = 0; k < cgBayesParents.length; k++) {
	                        condition.disallowComplement(cgBayesParents[k], cgBayesParentValues[k]);
	                    }
	                    
	                    if (condition.existsCombination()) {
	                    	double prob = cgBayesProbs.getConditionalProb(assertion, condition);
	                    	
	                    	estimatedCgIm.setCgDiscreteProbability(cgDiscreteNodeIndex, row, col, prob);
	                    	
	                    	if(cgContinuousParentArray != null) {
	                    		
		                    	List<Integer> rowListConditioned = new ArrayList<>();
		                    	
		                    	for(int dataRow=0;dataRow<dataSet.getNumRows();dataRow++) {
		                    		
		                    		boolean qualified = true;
		                    		
		                    		for(int arrayIndex=0;arrayIndex<cgBayesParents.length;arrayIndex++) {
			                    	
		                    			int parentIndex = cgBayesParents[arrayIndex];
		                    			int categoryIndex = cgBayesParentValues[arrayIndex];
			                    		
		                    			Node cgDiscreteParentNode = estimatedCgBayesIm.getNode(parentIndex);
		                    			int cgDiscreteParentNodeIndex = dataSet.getColumn(cgDiscreteParentNode);
			                    		
		                    			if(categoryIndex != dataSet.getInt(dataRow, cgDiscreteParentNodeIndex)) {
		                    				qualified = false;
		                    				break;
		                    			}
			                    	}
		                    		
		                    		if(qualified) {
		                    			rowListConditioned.add(dataRow);
		                    		}
		                    		
		                    	}
		                    	
		                    	int[] rowsConditioned = new int[rowListConditioned.size()];
		                    	
		                    	Iterator<Integer> it = rowListConditioned.iterator();
		                    	int rowIndex = 0;
		                    	
		                    	while(it.hasNext()) {
		                    		rowsConditioned[rowIndex] = it.next().intValue();
		                    		rowIndex++;
		                    	}
		                    	
		                    	DataSet conditionedData = dataSet.subsetRows(rowsConditioned);
		                    	
		                    	List<Node> cgContinuousParentNodes = new ArrayList<>();
		                    	
		                    	for(int arrayIndex=0;arrayIndex<cgContinuousParentArray.length;arrayIndex++) {
		                    		
		                    		int cgDiscreteNodeContinuousParentNodeIndex = cgContinuousParentArray[arrayIndex];
		                    		
		                    		Node continuousParentNode = estimatedCgIm
		                    				.getCgDiscreteNodeContinuousParentNode(
		                    						cgDiscreteNodeContinuousParentNodeIndex);
		                    		cgContinuousParentNodes.add(continuousParentNode);
		                    	}
		                    	
		                    	conditionedData = conditionedData.subsetColumns(cgContinuousParentNodes);
		                    	
		                    	TetradMatrix matrix = conditionedData.getDoubleData();
		                    	
		                    	for(int arrayIndex=0;arrayIndex<cgContinuousParentArray.length;arrayIndex++) {
		                    		int continuousParentIndex = cgContinuousParentArray[arrayIndex];
		                    		
		                    		TetradVector vector = matrix.getColumn(arrayIndex);
		                    		double[] data = vector.toArray();
		                    		
	    	                    	// Set a mean of each of parents' continuous nodes
		                    		double mean = StatUtils.mean(data);
		                    		
	    	                    	// Set a stand deviation of each of parents' continuous nodes
		                    		double sd = StatUtils.sd(data);
		                    		
		                    		estimatedCgIm.setCgDiscreteNodeContinuousParentMean(cgDiscreteNodeIndex, row, col, continuousParentIndex, mean);
		                    		estimatedCgIm.setCgDiscreteNodeContinuousParentMeanStdDev(cgDiscreteNodeIndex, row, col, continuousParentIndex, sd);
		                    	}
	                    	}
	                    	
	                    	
	                    } else {
	                    	estimatedCgIm.setCgDiscreteProbability(cgDiscreteNodeIndex, row, col, Double.NaN);
	                    	
	                    	if(cgContinuousParentArray != null) {
	                    		for(int arrayIndex=0;arrayIndex<cgContinuousParentArray.length;arrayIndex++) {
		                    		int continuousParentIndex = cgContinuousParentArray[arrayIndex];

		                    		estimatedCgIm.setCgDiscreteNodeContinuousParentMean(cgDiscreteNodeIndex, row, col, continuousParentIndex, Double.NaN);
		                    		estimatedCgIm.setCgDiscreteNodeContinuousParentMeanStdDev(cgDiscreteNodeIndex, row, col, continuousParentIndex, Double.NaN);
		                    	}
	                    	}
	                    }
					}
				}
			}
		}
		
		
		// 2) Mixed Parent Continuous Child Node
		List<Node> cgContinuousNodes = estimatedCgIm.getCgContinuousVariableNodes();
		for(Node cgContinuousNode : cgContinuousNodes) {
			
			int cgContinuousNodeIndex = estimatedCgIm.getCgContinuousNodeIndex(cgContinuousNode);
			
			int numRows = estimatedCgIm.getCgContinuousNumRows(cgContinuousNodeIndex); // a total number of combination of discrete parent's values
			int[] cgDiscreteParents = estimatedCgIm.getCgContinuousNodeDiscreteParentNodeArray(cgContinuousNodeIndex);
			
        	int[] cgContinuousParentArray = estimatedCgIm
        			.getCgContinuousNodeContinuousParentNodeArray(cgContinuousNodeIndex);
        	
        	for(int row=0;row<numRows;row++) {
				
				int[] cgDiscreteParentValues = estimatedCgIm.getCgContinuousNodeDiscreteParentValues(cgContinuousNodeIndex, row);
				
				if(cgContinuousParentArray != null) {
            		
                	List<Integer> rowListConditioned = new ArrayList<>();
                	
                	for(int dataRow=0;dataRow<dataSet.getNumRows();dataRow++) {
                		
                		boolean qualified = true;
                		
                		for(int arrayIndex=0;arrayIndex<cgDiscreteParents.length;arrayIndex++) {
                    	
                			int discreteParentIndex = cgDiscreteParents[arrayIndex];
                			int categoryIndex = cgDiscreteParentValues[arrayIndex];
                			
                			Node cgDiscreteParentNode = estimatedCgIm.getCgContinuousNodeDiscreteParentNode(discreteParentIndex);
                			int cgDiscreteParentNodeIndex = dataSet.getColumn(dataSet.getVariable(cgDiscreteParentNode.getName()));
                    		
                			if(categoryIndex != dataSet.getInt(dataRow, cgDiscreteParentNodeIndex)) {
                				qualified = false;
                				break;
                			}
                    	}
                		
                		if(qualified) {
                			rowListConditioned.add(dataRow);
                		}
                		
                	}
                	
                	int[] rowsConditioned = new int[rowListConditioned.size()];
                	
                	Iterator<Integer> it = rowListConditioned.iterator();
                	int rowConditionedIndex = 0;
                	
                	while(it.hasNext()) {
                		rowsConditioned[rowConditionedIndex] = it.next().intValue();
                		rowConditionedIndex++;
                	}
                	
                	DataSet conditionedData = dataSet.subsetRows(rowsConditioned);
                	
                	List<Node> cgContinuousNodeContinuousParentNodes = new ArrayList<>();
                	cgContinuousNodeContinuousParentNodes.add(cgContinuousNode);
                	
                	for(int arrayIndex=0;arrayIndex<cgContinuousParentArray.length;arrayIndex++) {
                		
                		int cgContinuousNodeContinuousParentNodeIndex = cgContinuousParentArray[arrayIndex];
                		
                		Node continuousParentNode = estimatedCgIm
                				.getCgDiscreteNodeContinuousParentNode(
                						cgContinuousNodeContinuousParentNodeIndex);
                		cgContinuousNodeContinuousParentNodes.add(continuousParentNode);
                	}
                	
                	conditionedData = conditionedData.subsetColumns(cgContinuousNodeContinuousParentNodes);
                	
                	TetradMatrix matrix = conditionedData.getDoubleData();
                	TetradVector childVector = matrix.getColumn(0);
                	
                	estimatedCgIm.setCgContinuousNodeContinuousParentEdgeCoef(cgContinuousNodeIndex, row, 0, 1);
                	estimatedCgIm.setCgContinuousNodeContinuousParentMean(cgContinuousNodeIndex, row, 0, StatUtils.mean(childVector, childVector.size()));
            		estimatedCgIm.setCgContinuousNodeContinuousParentMeanStdDev(cgContinuousNodeIndex, row, 0, StatUtils.sd(childVector, childVector.size()));
            		
                	// We start from index 1 instead of 0 because we reserve index 0 for the continuous child node
                	for(int arrayIndex=1;arrayIndex<cgContinuousParentArray.length+1;arrayIndex++) {
                		
                		TetradVector parentVector = matrix.getColumn(arrayIndex);
                		
                		// Set a correlation coefficient of continuous child and continuous parent given this condition
                		double coef = StatUtils.correlation(childVector, parentVector);
                		
                		estimatedCgIm.setCgContinuousNodeContinuousParentEdgeCoef(cgContinuousNodeIndex, row, arrayIndex, coef);
                		
                    	// Set a mean of each of parents' continuous nodes
                		double mean = StatUtils.mean(parentVector, parentVector.size());
                		
                    	// Set a stand deviation of each of parents' continuous nodes
                		double sd = StatUtils.sd(parentVector, parentVector.size());
                		
                		estimatedCgIm.setCgContinuousNodeContinuousParentMean(cgContinuousNodeIndex, row, arrayIndex, mean);
                		estimatedCgIm.setCgContinuousNodeContinuousParentMeanStdDev(cgContinuousNodeIndex, row, arrayIndex, sd);
                	}
				}
				
        	}
        	
		}
		
		
		return estimatedCgIm;
	}
	
	
	
}
