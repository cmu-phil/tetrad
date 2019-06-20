/**
 * 
 */
package edu.cmu.tetrad.test.cg;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeType;
import edu.pitt.dbmi.cg.CgPm;

/**
 * Jun 5, 2019 1:34:40 PM
 *
 * @author Chirayu Kong Wongchokprasitti, PhD (chw20@pitt.edu)
 *
 */
public final class TestCgPm {

	private Graph getMixedDataGraph() {
		// Bayes
		// X1(D) -> X3(D)
		// X2(D) -> X3(D)
		// SEM
		// X4(C) -> X6(C)
		// X5(C) -> X6(C)
		// CG Discrete Child
		// X1(D) -> X7(D)
		// X4(C) -> X7(D)
		// CG Continuous Child
		// X2(D) -> X8(C)
		// X5(C) -> X8(C)
		
		DiscreteVariable var1 = new DiscreteVariable("X1");
		DiscreteVariable var2 = new DiscreteVariable("X2");
		DiscreteVariable var3 = new DiscreteVariable("X3");
		DiscreteVariable var7 = new DiscreteVariable("X7");

		ContinuousVariable var4 = new ContinuousVariable("X4");
		ContinuousVariable var5 = new ContinuousVariable("X5");
		ContinuousVariable var6 = new ContinuousVariable("X6");
		ContinuousVariable var8 = new ContinuousVariable("X8");
		
		Graph graph = new EdgeListGraph();
		graph.addNode(var1);
		graph.addNode(var2);
		graph.addNode(var3);
		graph.addNode(var4);
		graph.addNode(var5);
		graph.addNode(var6);
		graph.addNode(var7);
		graph.addNode(var8);
		
		graph.addDirectedEdge(var1, var3);
		graph.addDirectedEdge(var2, var3);
		graph.addDirectedEdge(var4, var6);
		graph.addDirectedEdge(var5, var6);
		graph.addDirectedEdge(var1, var7);
		graph.addDirectedEdge(var4, var7);
		graph.addDirectedEdge(var2, var8);
		graph.addDirectedEdge(var5, var8);

		return graph;
	}
	
	@Test
	public void testInitializeFixed() {
		Graph graph = getMixedDataGraph();
		
		CgPm cgPm = new CgPm(graph, 3, 3);
		
		for(Node node : graph.getNodes()) {
			if(node instanceof DiscreteVariable) {
				assertEquals(3, cgPm.getDiscreteNumCategories(node));
			}
		}
	}
	
	@Test
	public void testInitializeRandom() {
		Graph graph = getMixedDataGraph();
		
		CgPm cgPm = new CgPm(graph, 2, 5);
		
		for(Node node : graph.getNodes()) {
			if(node instanceof DiscreteVariable) {
				int numValues = cgPm.getDiscreteNumCategories(node);
	            assertTrue("Number of values out of range: " + numValues,
	                    numValues >= 2 && numValues <= 5);
			}
		}
	}
	
	@Test
	public void testChangeNumValues() {
		Graph graph = getMixedDataGraph();
		
		Node x1 = graph.getNode("X1");
		Node x2 = graph.getNode("X2");

		CgPm cgPm = new CgPm(graph, 3, 3);
		cgPm.setDiscreteNumCategories(x1, 5);
		
		
		assertEquals(5, cgPm.getDiscreteNumCategories(x1));
        assertEquals(3, cgPm.getDiscreteNumCategories(x2));
	}
	
    @Test
    public void testEquals() {
		Graph graph = getMixedDataGraph();

		CgPm cgPm = new CgPm(graph, 3, 3);
		
		assertEquals(cgPm, cgPm);
    }
    
    @Test
    public void testMeasuredNodes() {
    	Graph graph = getMixedDataGraph();
    	
    	DiscreteVariable var9 = new DiscreteVariable("X9");
    	var9.setNodeType(NodeType.LATENT);
    	graph.addNode(var9);
    	
    	Node x1 = graph.getNode("X1");
    	graph.addDirectedEdge(var9, x1);
    	
        new CgPm(graph, 3, 3);
    }
}
