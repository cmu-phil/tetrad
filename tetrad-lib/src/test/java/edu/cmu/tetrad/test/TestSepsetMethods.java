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
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.RandomGraph;
import edu.cmu.tetrad.search.LvLite;
import edu.cmu.tetrad.search.test.MsepTest;
import org.junit.Test;

import java.util.*;

import static junit.framework.TestCase.fail;

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
        int numEdges = 150;
        int numReps = 10;

        // Make a list of numNodes nodes.
        List<Node> nodes = new ArrayList<>();

        for (int i = 0; i < numNodes; i++) {
            nodes.add(new ContinuousVariable("X" + i));
        }

        // Make a random DAG with numEdges edges.
        Graph dag = RandomGraph.randomDag(nodes, 0, numEdges, 100, 100, 100, false);

        System.out.println(dag);

        Map<Node, Set<Node>> ancestorMap = dag.paths().getAncestorMap();

        long[] timeSums = new long[4];

        for (int i = 0; i < numReps; i++) {

            // Pick two distinct nodes x and y randomly from the list of nodes.
            Node x, y;

            do {
                x = nodes.get((int) (Math.random() * numNodes));
                y = nodes.get((int) (Math.random() * numNodes));
            } while (x.equals(y));

            System.out.println("\n\n###Rep " + (i + 1) + " Checking nodes " + x + " and " + y + ".");

            // Check this pair.
            long[] times = checkNodePair(dag, x, y, ancestorMap);

            for (int j = 0; j < 4; j++) {
                timeSums[j] += times[j];
            }
        }

        System.out.println("Total times = " + Arrays.toString(timeSums));
    }

    /**
     * We will test various methods here for finding a sepset of two nodes in a DAG.
     */
    public long[] checkNodePair(Graph dag, Node x, Node y, Map<Node, Set<Node>> ancestorMap) {

        // We have several methods for finding a sepset for x and y in a DAG. Let me find them briefly.
        long[] times = new long[4];

        // Method 1: Using the getSepset method of the DagSepsets class.
        long start1 = System.currentTimeMillis();

//        Set<Node> sepset1 = dag.getSepset(x, y);

        long stop1 = System.currentTimeMillis();

        times[0] = stop1 - start1;

        long start2 = System.currentTimeMillis();

        // Method 2: Using the getSepset method of the Graph class.
        Set<Node> sepset2 = dag.paths().getSepsetContaining(x, y, new HashSet<>(), false);

        long stop2 = System.currentTimeMillis();

        times[1] = stop2 - start2;

        long start3 = System.currentTimeMillis();

        // Method 3: Use the getSepsetContaining2 method of the Graph class.
//        Set<Node> sepset3 = dag.paths().getSepsetContaining2(x, y, new HashSet<>(), false);

        long stop3 = System.currentTimeMillis();

        times[2] = stop3 - start3;

        long start4 = System.currentTimeMillis();

        // Method 3: Using the getSepset method from the LvLite class.
//        Set<Node> sepset4 = LvLite.getSepset(x, y, dag, new MsepTest(dag), ancestorMap, -1, -1, -1);

        long stop4 = System.currentTimeMillis();

        times[3] = stop4 - start4;

//        System.out.println("Sepset 1: " + sepset1);
        System.out.println("Sepset 2: " + sepset2);
//        System.out.println("Sepset 3: " + sepset3);
//        System.out.println("Sepset 4: " + sepset4);

        // Check if the sepsets found by the three methods all separate x from y.
        MsepTest msepTest = new MsepTest(dag);

        // If sepset1 is null, then x and y are not d-separated, so print this.
//        if (sepset1 == null) {
//            System.out.println("Sepset 1 is null.");
//        } else {
//            if (msepTest.checkIndependence(x, y, sepset1).isDependent()) {
//                System.out.println("Sepset 1 does not separate x from y.");
//            }
//        }

        if (sepset2 != null) {
            if (msepTest.checkIndependence(x, y, sepset2).isDependent()) {
                System.out.println("Sepset 2 does not separate x from y.");
            }
        }

//        if (sepset3 != null) {
//            if (msepTest.checkIndependence(x, y, sepset3).isDependent()) {
//                System.out.println("Sepset 3 does not separate x from y.");
//            }
//        }

//        // For the LV-Lite method, if sepset1 is not null and sepset4 is null, fail, since if Method1 found a sepset,
//        // Method 4 should also.
//        if (sepset4 == null) {
//            System.out.println("Sepset 4 is null, but sepset 1 is not.");
//        } else {
//            if (msepTest.checkIndependence(x, y, sepset4).isDependent()) {
//                System.out.println("Sepset 4 does not separate x from y.");
//            }
//        }

        return times;
    }
}



