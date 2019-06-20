/**
 * 
 */
package edu.cmu.tetrad.test.cg;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import edu.cmu.tetrad.bayes.BayesIm;
import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.RandomUtil;
import edu.pitt.dbmi.cg.CgIm;
import edu.pitt.dbmi.cg.CgPm;

/**
 * Jun 5, 2019 6:06:55 PM
 *
 * @author Chirayu Kong Wongchokprasitti, PhD (chw20@pitt.edu)
 *
 */
public final class TestCgIm {
	
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
    public void testCopyConstructor() {
    	Graph graph = getMixedDataGraph();
    	
    	CgPm cgPm = new CgPm(graph);
    	CgIm cgIm = new CgIm(cgPm, CgIm.RANDOM);
    	CgIm cgIm2 = new CgIm(cgIm);
    	assertEquals(cgIm, cgIm2);
    }
    
    @Test
    public void testConstructManual() {
    	Graph graph = getMixedDataGraph();
    	
    	CgPm cgPm = new CgPm(graph);
    	CgIm cgIm = new CgIm(cgPm);
    	Graph graph1 = cgIm.getCgPm().getGraph();
    	Graph graph2 = GraphUtils.replaceNodes(graph1, graph.getNodes());
    	assertEquals(graph1, graph2);
    }
    
    @Test
    public void testAddRemoveParent() {
    	Graph graph = getMixedDataGraph();
    	
		CgPm cgPm = new CgPm(graph);
		CgIm cgIm = new CgIm(cgPm, CgIm.RANDOM);
		
		CgIm cgIm2 = new CgIm(cgPm, cgIm, CgIm.MANUAL);
		
		assertEquals(cgIm, cgIm2);
		
		// Bayes
		// D1(D) -> D3(D)
		// D2(D) -> D3(D)
    	Node d3 = graph.getNode("D3");
		
    	DiscreteVariable d5 = new DiscreteVariable("D5");
		graph.addNode(d5);
		
		graph.addDirectedEdge(d5, d3);
		
		CgPm cgPm3 = new CgPm(graph, cgPm);
		CgIm cgIm3 = new CgIm(cgPm3, cgIm2, CgIm.MANUAL);
		
		// Make sure the 'd5' node got ?'s.
		assertTrue(rowUnspecified(cgIm3.getBayesIm(), cgIm3.getBayesIm().getNodeIndex(d5), 0));
		
		graph.removeNode(d5);
		CgPm cgPm4 = new CgPm(graph, cgPm3, 2, 2);
		CgIm cgIm4 = new CgIm(cgPm4, cgIm3, CgIm.MANUAL);
		
        // Make sure the 'd3' node has 4 rows of '?'s'.
		assertTrue(cgIm4.getBayesIm().getNumRows(cgIm4.getBayesIm().getNodeIndex(d3)) == 4);
		assertTrue(rowUnspecified(cgIm4.getBayesIm(), cgIm4.getBayesIm().getNodeIndex(d3), 0));
		assertTrue(rowUnspecified(cgIm4.getBayesIm(), cgIm4.getBayesIm().getNodeIndex(d3), 1));
    }
    
    @Test
    public void testAddRemoveValues() {
    	Graph graph = getMixedDataGraph();
    	
    	CgPm cgPm = new CgPm(graph, 3, 3);
    	CgIm cgIm = new CgIm(cgPm, CgIm.RANDOM);
    	
    	Node d1 = graph.getNode("D1");
    	Node d2 = graph.getNode("D2");
    	Node d3 = graph.getNode("D3");
    	Node d4 = graph.getNode("D4");
    	
    	cgPm.setDiscreteNumCategories(d1, 4);
    	cgPm.setDiscreteNumCategories(d2, 4);
    	CgIm cgIm2 = new CgIm(cgPm, cgIm, CgIm.MANUAL);
    	
    	cgPm.setDiscreteNumCategories(d1, 2);
    	CgIm cgIm3 = new CgIm(cgPm, cgIm2, CgIm.MANUAL);
    	
    	cgPm.setDiscreteNumCategories(d3, 2);
    	cgPm.setDiscreteNumCategories(d4, 2);
    	CgIm cgIm4 = new CgIm(cgPm, CgIm.RANDOM);
    	
    	// At this point, D1 has 2 categories, D2 has 4 categories, and D3/D4 has 2 categories.
    	
    	for(int node = 0;node < cgIm4.getCgDiscreteNumNodes();node++) {
    		for(int row = 0;row < cgIm4.getCgDiscreteNumRows(node);row++) {
    			for(int col = 0;col < cgIm4.getCgDiscreteNumColumns(node);col++) {
    				cgIm4.setCgDiscreteProbability(node, row, col, Double.NaN);
    			}
    		}
    	}

    	double[][] aTable = {
                {.2, .8}
        };

        double[][] bTable = {
                {.1, .9},
                {.7, .3},
                {.3, .7},
                {.5, .5},
                {.09, .91},
                {.6, .4},
                {.2, .8},
                {.8, .2}
        };

        double[][] cTable = {
                {.1, .2, .3, .4},
        };
        
        int _d1 = cgIm.getBayesIm().getNodeIndex(d1);
        if(_d1 > -1) {
        	//System.out.println("_d1: " + _d1);
        	//System.out.println("cgIm4.getBayesIm().getNumRows(_d1): " + cgIm4.getBayesIm().getNumRows(_d1));
            for (int row = 0; row < cgIm4.getBayesIm().getNumRows(_d1); row++) {
            	//System.out.println("cgIm4.getBayesIm().getNumColumns(_d1): " + cgIm4.getBayesIm().getNumColumns(_d1));
                for (int col = 0; col < cgIm4.getBayesIm().getNumColumns(_d1); col++) {
                	cgIm4.getBayesIm().setProbability(_d1, row, col, aTable[row][col]);
                }
            }
        }else {
        	System.out.println("D1 not found!!!");
        }
    	
        int _d2 = cgIm.getBayesIm().getNodeIndex(d2);
        if(_d2 > -1) {
        	//System.out.println("_d2: " + _d2);
        	//System.out.println("cgIm4.getBayesIm().getNumRows(_d2): " + cgIm4.getBayesIm().getNumRows(_d2));
            for (int row = 0; row < cgIm4.getBayesIm().getNumRows(_d2); row++) {
            	//System.out.println("cgIm4.getBayesIm().getNumColumns(_d2): " + cgIm4.getBayesIm().getNumColumns(_d2));
                for (int col = 0; col < cgIm4.getBayesIm().getNumColumns(_d2); col++) {
                	cgIm4.getBayesIm().setProbability(_d2, row, col, cTable[row][col]);
                }
            }
        }else {
        	System.out.println("D2 not found!!!");
        }
        
        int _d3 = cgIm.getBayesIm().getNodeIndex(d3);
        if(_d3 > -1) {
        	//System.out.println("_d3: " + _d3);
        	//System.out.println("cgIm4.getBayesIm().getNumRows(_d3): " + cgIm4.getBayesIm().getNumRows(_d3));
            for (int row = 0; row < cgIm4.getBayesIm().getNumRows(_d3); row++) {
            	//System.out.println("cgIm4.getBayesIm().getNumColumns(_d3): " + cgIm4.getBayesIm().getNumColumns(_d3));
                for (int col = 0; col < cgIm4.getBayesIm().getNumColumns(_d3); col++) {
                	cgIm4.getBayesIm().setProbability(_d3, row, col, bTable[row][col]);
                }
            }
        }else {
        	System.out.println("D3 not found!!!");
        }
        
        int _d4 = cgIm.getCgDiscreteNodeIndex(d4);
        if(_d4 > -1) {
        	//System.out.println("_d4: " + _d4);
        	//System.out.println("cgIm4.getCgDiscreteNumRows(_d4): " + cgIm4.getCgDiscreteNumRows(_d4));
            for (int row = 0; row < cgIm4.getCgDiscreteNumRows(_d4); row++) {
            	//System.out.println("cgIm4.getCgDiscreteNumColumns(_d4): " + cgIm4.getCgDiscreteNumColumns(_d4));
                for (int col = 0; col < cgIm4.getCgDiscreteNumColumns(_d4); col++) {
                	cgIm4.setCgDiscreteProbability(_d4, row, col, bTable[row][col]);
                }
            }
        }else {
        	System.out.println("D4 not found!!!");
        }
    }
    
    @Test
    public void testSetVariableParameters() {
    	RandomUtil.getInstance().setSeed(49489384L);
    	
    	Graph graph = getMixedDataGraph();
    	
    	CgPm cgPm = new CgPm(graph);
    	CgIm cgIm = new CgIm(cgPm);
    	
    	// SEM
    	// C1(C) -> C3(C)
    	Node c1 = graph.getNode("C1");
    	Node c3 = graph.getNode("C3");
    	Node c4 = graph.getNode("C4");
    	
    	cgIm.getSemIm().setEdgeCoef(c1, c3, 100.0);
    	assertEquals(100.0, cgIm.getSemIm().getEdgeCoef(c1, c3), 0.1);
    	
    	cgIm.getSemIm().setErrCovar(c1, c1, 25.0);
    	assertEquals(2.96, cgIm.getSemIm().getErrVar(c1), 0.1);
    	
		// CG Continuous Child
		// D1(D) -> C4(C)
		// D2(D) -> C4(C)
		// C1(C) -> C4(C)
		// C2(C) -> C4(C)
    	Node d1 = graph.getNode("D1");
    	Node d2 = graph.getNode("D2");
    	cgPm.setDiscreteNumCategories(d1, 3);
    	cgPm.setDiscreteNumCategories(d2, 4);
    	CgIm cgIm2 = new CgIm(cgPm, cgIm, CgIm.MANUAL);
    	
    	int nodeIndex = cgIm.getCgContinuousNodeIndex(c4);
    	int rowIndex = 11;
    	int continuousParentIndex = 0;
    	cgIm2.setCgContinuousNodeContinuousParentEdgeCoef(nodeIndex, rowIndex, continuousParentIndex, 50.0);
    	assertEquals(50.0, cgIm2.getCgContinuousNodeContinuousParentEdgeCoef(nodeIndex, rowIndex, continuousParentIndex), 0.1);
    	
    	//cgIm2.setCgContinuousNodeContinuousParentEdgeCoef(c4, c1, rowIndex, 50.0);
    	//assertEquals(50.0, cgIm2.getCgContinuousNodeContinuousParentEdgeCoef(c4, c1, rowIndex), 0.1);
    	
    	cgIm2.setCgContinuousNodeContinuousParentErrCovar(nodeIndex, rowIndex, continuousParentIndex, 25.0);
    	assertEquals(25, cgIm2.getCgContinuousNodeContinuousParentErrCovar(nodeIndex, rowIndex, continuousParentIndex), 0.1);

    }
    
    private static boolean rowUnspecified(BayesIm bayesIm, int node, int row) {
        for (int col = 0; col < bayesIm.getNumColumns(node); col++) {
            double prob = bayesIm.getProbability(node, row, col);
            if (!Double.isNaN(prob)) {
                return false;
            }
        }

        return true;
    }
    
}
