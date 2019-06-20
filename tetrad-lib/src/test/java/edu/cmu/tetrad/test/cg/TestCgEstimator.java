/**
 * 
 */
package edu.cmu.tetrad.test.cg;

import org.junit.Test;

import edu.cmu.tetrad.algcomparison.simulation.ConditionalGaussianSimulation;
import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.pitt.dbmi.cg.CgEstimator;
import edu.pitt.dbmi.cg.CgIm;
import edu.pitt.dbmi.cg.CgPm;

/**
 * Jun 7, 2019 12:57:59 PM
 *
 * @author Chirayu Kong Wongchokprasitti, PhD (chw20@pitt.edu)
 *
 */
public final class TestCgEstimator {
	
	private Graph getMixedDataGraph() {
		// Bayes
		// D1(D) -> D3(D)
		// D2(D) -> D3(D)
		// SEM
		// C1(C) -> C3(C)
		// C2(C) -> C3(C)
		// CG Discrete Child
		// D1(D) -> D4(D)
		// D2(D) -> D4(D)
		// C1(C) -> D4(D)
		// C2(C) -> D4(D)
		// CG Continuous Child
		// D1(D) -> C4(C)
		// D2(D) -> C4(C)
		// C1(C) -> C4(C)
		// C2(C) -> C4(C)
		
		DiscreteVariable d1 = new DiscreteVariable("D1");
		DiscreteVariable d2 = new DiscreteVariable("D2");
		DiscreteVariable d3 = new DiscreteVariable("D3");
		DiscreteVariable d4 = new DiscreteVariable("D4");

		ContinuousVariable c1 = new ContinuousVariable("C1");
		ContinuousVariable c2 = new ContinuousVariable("C2");
		ContinuousVariable c3 = new ContinuousVariable("C3");
		ContinuousVariable c4 = new ContinuousVariable("C4");
		
		Graph graph = new EdgeListGraph();
		graph.addNode(d1);
		graph.addNode(d2);
		graph.addNode(d3);
		graph.addNode(d4);
		graph.addNode(c1);
		graph.addNode(c2);
		graph.addNode(c3);
		graph.addNode(c4);
		
		graph.addDirectedEdge(d1, d3);
		graph.addDirectedEdge(d2, d3);
		graph.addDirectedEdge(c1, c3);
		graph.addDirectedEdge(c2, c3);
		graph.addDirectedEdge(d1, d4);
		graph.addDirectedEdge(d2, d4);
		graph.addDirectedEdge(c1, d4);
		graph.addDirectedEdge(c2, d4);
		graph.addDirectedEdge(d1, c4);
		graph.addDirectedEdge(d2, c4);
		graph.addDirectedEdge(c1, c4);
		graph.addDirectedEdge(c2, c4);

		return graph;
	}
	
	@Test
	public void test1() {
		Graph graph = getMixedDataGraph();
		
		CgPm cgPm = new CgPm(graph);
		CgIm cgIm = new CgIm(cgPm);
		DataSet dataSet = cgIm.simulateData(100, true);
		
		//System.out.println("dataSet: " + dataSet);
		
		CgEstimator estimator = new CgEstimator(cgIm, dataSet);
		CgIm estimatedCgIm = estimator.getEstimatedCgIm();
	}

}
