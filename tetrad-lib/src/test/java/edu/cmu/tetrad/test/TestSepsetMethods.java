///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015, 2022 by Peter Spirtes, Richard        //
// Scheines, Joseph Ramsey, and Clark Glymour.                               //
//                                                                           //
// This program is free software; you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation; either version 2 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program; if not, write to the Free Software               //
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.test;

import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.RandomGraph;
import edu.cmu.tetrad.search.SepsetFinder;
import edu.cmu.tetrad.search.test.MsepTest;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

/**
 * Tests the BooleanFunction class.
 *
 * @author josephramsey
 */
public class TestSepsetMethods {

    /**
     * We will call the checkNodePair method here with a random DAG 10 choices of x and y.
     */
    @Test
    public void test1() {

        int numNodes = 50;
        int numEdges = 100;
        int numReps = 100;

        // Make a list of numNodes nodes.
        List<Node> nodes = new ArrayList<>();

        for (int i = 0; i < numNodes; i++) {
            nodes.add(new ContinuousVariable("X" + i));
        }

        // Make a random DAG with numEdges edges.
        Graph dag = RandomGraph.randomDag(nodes, 0, numEdges, 100, 100, 100, false);

        System.out.println(dag);

        Map<Node, Set<Node>> ancestorMap = dag.paths().getAncestorMap();

        long[] timeSums = new long[5];

        for (int i = 0; i < numReps; i++) {

            // Pick two distinct nodes x and y randomly from the list of nodes.
            Node x, y;

            do {
                x = nodes.get((int) (Math.random() * numNodes));
                y = nodes.get((int) (Math.random() * numNodes));
            } while (x.equals(y));

            Edge e = dag.getEdge(x, y);
            System.out.println("\n\n###Rep " + (i + 1) + " Checking nodes " + x + " and " + y + ". The edge is " + ((e != null) ? e : "absent"));

            // Check this pair.
            long[] times = checkNodePair(dag, x, y, ancestorMap);

            for (int j = 0; j < times.length; j++) {
                timeSums[j] += times[j];
            }
        }

        System.out.println("Total times = " + Arrays.toString(timeSums));
    }

    /**
     * We will test various methods here for finding a sepset of two nodes in a DAG.
     */
    public long[] checkNodePair(Graph dag, Node x, Node y, Map<Node, Set<Node>> ancestorMap) {

        Edge e = dag.getEdge(x, y);

        // Method 1: Using the getSepset method of the DagSepsets class.
        // Method 2: Using the getSepset method of the Graph class.
        // Method 3: Using the getSepset method from the LvLite class.

        // We have several methods for finding a sepset for x and y in a DAG. Let me find them briefly.
        long[] times = new long[5];

        long start1 = System.currentTimeMillis();
        Set<Node> sepset1 = SepsetFinder.getSepsetContaining1(dag, x, y, new HashSet<>(), new MsepTest(dag));
        long stop1 = System.currentTimeMillis();
        System.out.println("Time taken by getSepsetContaining1: " + (stop1 - start1) + " ms");
        times[0] = stop1 - start1;

        long start2 = System.currentTimeMillis();
        Set<Node> sepset2 = SepsetFinder.getSepsetContaining2(dag, x, y, new HashSet<>(), false, new MsepTest(dag));
        long stop2 = System.currentTimeMillis();
        times[1] = stop2 - start2;
        System.out.println("Time taken by getSepsetContaining2: " + (stop2 - start2) + " ms");

        long start3 = System.currentTimeMillis();
        Set<Node> sepset3 = SepsetFinder.getSepsetContainingMaxP(dag, x, y, new HashSet<>(), false, new MsepTest(dag));
        long stop3 = System.currentTimeMillis();
        times[2] = stop3 - start3;
        System.out.println("Time taken by getSepsetContaining2: " + (stop3 - start3) + " ms");

        long start4 = System.currentTimeMillis();
        Set<Node> sepset4 = SepsetFinder.getSepsetContainingMinP(dag, x, y, new HashSet<>(), false, new MsepTest(dag));
        long stop4 = System.currentTimeMillis();
        times[3] = stop4 - start4;
        System.out.println("Time taken by getSepsetContaining2: " + (stop4 - start4) + " ms");

        long start5 = System.currentTimeMillis();
        Set<Node> sepset5 = SepsetFinder.getSepset5(x, y, dag, new MsepTest(dag), ancestorMap, 10, -1,
                false);
        long stop5 = System.currentTimeMillis();
        times[4] = stop5 - start5;
        System.out.println("Time taken by getSepset5: " + (stop5 - start5) + " ms");

        System.out.println("Sepset 1: " + sepset1);
        System.out.println("Sepset 2: " + sepset2);
        System.out.println("Sepset 3: " + sepset3);
        System.out.println("Sepset 4: " + sepset4);
        System.out.println("Sepset 5: " + sepset5);

        // Check if the sepsets found by the five methods all separate x from y.
        MsepTest msepTest = new MsepTest(dag);

        // Note that methods 3 and 4 cannot find null sepsets from Oracle. These need to be tested separately from data.

        if (e == null) {
            assertNotNull(sepset1);
            assertNotNull(sepset2);
            assertNotNull(sepset3);
            assertNotNull(sepset4);
            assertNotNull(sepset5);

            assertTrue(msepTest.checkIndependence(x, y, sepset1).isIndependent());
            assertTrue(msepTest.checkIndependence(x, y, sepset2).isIndependent());
//            assertTrue(msepTest.checkIndependence(x, y, sepset3).isIndependent());
//            assertTrue(msepTest.checkIndependence(x, y, sepset4).isIndependent());
            assertTrue(msepTest.checkIndependence(x, y, sepset5).isIndependent());
        } else {
            assertNull(sepset1);
            assertNull(sepset2);
//            assertNull(sepset3);
//            assertNull(sepset4);
            assertNull(sepset5);
        }

        return times;
    }
}



