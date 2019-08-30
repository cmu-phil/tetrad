/**
 * 
 */
package edu.pitt.dbmi.cg;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;

/**
 * Aug 28, 2019 5:10:07 PM
 *
 * @author Chirayu Kong Wongchokprasitti, PhD (chw20@pitt.edu)
 *
 */
public class CgProperties {

    private double dof;

    private double bic;
    
    private double likelihood;
	
	private DataSet dataSet;
	
	private int sampleSize;
	
	private Graph graph;
	
	public CgProperties(DataSet dataSet, Graph graph) {
		if (dataSet == null) {
            throw new NullPointerException();
        }

		if (graph == null) {
            throw new NullPointerException();
        }
		
        this.dataSet = dataSet;
        this.sampleSize = dataSet.getNumRows();
        this.graph = GraphUtils.replaceNodes(graph, dataSet.getVariables());
        
        calcuateStat();
	}

	private void calcuateStat() {
		List<Node> nodes = graph.getNodes();
		
		int dof = 0;
		
		for(Node node : nodes) {
			
			int k = 0;
			if(node instanceof ContinuousVariable) {
				k = 1;
			}
			
			int numColumn = 1;
			if (node instanceof DiscreteVariable) {
				numColumn = ((DiscreteVariable) node).getNumCategories();
			}			
			
			int dof_parents = 1;
			int numRows = 1;
			for(Node parentNode : graph.getParents(node)) {
				if(parentNode instanceof DiscreteVariable) {
					int numParentRows = ((DiscreteVariable) parentNode).getNumCategories();
					numRows *= numParentRows;
					dof_parents *= numParentRows;
				}

				if(parentNode instanceof ContinuousVariable) {
					k++;
				}
			}
			
			dof_parents -= 1;
			
			int dof_partition = k*(k+1)/2 + 1;
			int dof_node_and_parents = numColumn * numRows * dof_partition - 1;
			int dof_node = dof_node_and_parents - dof_parents;
			
			dof += dof_node;
		}
		
		this.dof = dof;
	}

	public double getDof() {
		return dof;
	}

	public double getBic() {
		return bic;
	}

	public double getLikelihood() {
		return likelihood;
	}
	
}
