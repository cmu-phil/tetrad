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
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.SepsetFinder;
import edu.cmu.tetrad.search.test.MsepTest;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;

/**
 * The TestSepsetMethods class  is responsible for testing various methods for finding a sepset of two nodes in a DAG.
 */
public class TestSepsetMethods {

    /**
     * This method is used to test various methods for finding a sepset of two nodes in a directed acyclic graph (DAG).
     * It performs several repetitions of the test and calculates the total time taken for each step.
     */
    @Test
    public void test1() {
//        RandomUtil.getInstance().setSeed(384828384L);

        int numNodes = 20;
        int numEdges = 40;
        int numReps = 100;

        // Make a list of numNodes nodes.
        List<Node> nodes = new ArrayList<>();

        for (int i = 0; i < numNodes; i++) {
            nodes.add(new ContinuousVariable("X" + i));
        }

        Graph graph = RandomGraph.randomDag(nodes, 5, numEdges, 100, 100, 100, false);

        graph = GraphTransforms.dagToPag(graph);

        nodes = graph.getNodes();
        numNodes = nodes.size();

        // Commenting out the greedy, max p, and min p methods because they are so God-awfully slow when the graph is
        // large. For a fun time, uncomment that code and see. Also their performance is off from the recursive
        // and path blocking methods. I'm not sure why yet. The recursive and path blocking method in fact tie in
        // performance, which is interesting. Again, I'm not sure exactly why yet. The path blocking method is the
        // fastest, but the recursive method is not far behind. The greedy, max p, and min p methods are so slow that
        // they are not practical for use in the real world, which makes sense, given that they are exponential in the
        // degree of the graph.

        int numNullRecursive = 0;
//        int numNullGreedy = 0;
//        int numNullMaxP = 0;
//        int numNullMinP = 0;
        int numNullPathBlocking = 0;


        int numRecursivePass = 0;
//        int numGreedyPass = 0;
//        int numMaxPPass = 0;
//        int numMinPPass = 0;
        int numPathBlockingPass = 0;

        long[] timeSums = new long[6];

        for (int i = 0; i < numReps; i++) {

            // Pick two distinct nodes x and y randomly from the list of nodes.
            Node x, y;

            do {
                x = nodes.get((int) (Math.random() * numNodes));
                y = nodes.get((int) (Math.random() * numNodes));
            } while (x.equals(y));

            if (graph.isAdjacentTo(x, y)) {
                i--;
                continue;
            }

            Edge e = graph.getEdge(x, y);
            System.out.println("\n\n###Rep " + (i + 1) + " Checking nodes " + x + " and " + y + ". The edge is " + ((e != null) ? e : "absent"));

            MsepTest msepTest = new MsepTest(graph, true);

            long[] times = new long[6];

            long start1 = System.currentTimeMillis();
            Set<Node> sepset1 = SepsetFinder.getSepsetContainingRecursive(graph, x, y, new HashSet<>(), new MsepTest(graph));
            long stop1 = System.currentTimeMillis();
            System.out.println("Time taken by recursive: " + (stop1 - start1) + " ms");
            times[0] = stop1 - start1;

//            long start2 = System.currentTimeMillis();
//            Set<Node> sepset2 = SepsetFinder.getSepsetContainingGreedy(graph, x, y, new HashSet<>(), msepTest, -1);
//            long stop2 = System.currentTimeMillis();
//            times[1] = stop2 - start2;
//            System.out.println("Time taken by greedy: " + (stop2 - start2) + " ms");
//
//            long start3 = System.currentTimeMillis();
////            Set<Node> sepset3 = SepsetFinder.getSepsetContainingMaxP(graph, x, y, new HashSet<>(), msepTest, -1);
//            Set<Node> sepset3 = SepsetFinder.getSepsetContainingMaxPHybrid(graph, x, y, new HashSet<>(), msepTest, -1);
//            long stop3 = System.currentTimeMillis();
//            times[2] = stop3 - start3;
//            System.out.println("Time taken by max p: " + (stop3 - start3) + " ms");
//
//            long start4 = System.currentTimeMillis();
//            Set<Node> sepset4 = SepsetFinder.getSepsetContainingMinPHybrid(graph, x, y, new HashSet<>(), msepTest, -1);
//            long stop4 = System.currentTimeMillis();
//            times[3] = stop4 - start4;
//            System.out.println("Time taken by min p: " + (stop4 - start4) + " ms");

            long start5 = System.currentTimeMillis();
            Set<Node> sepset5 = SepsetFinder.getSepsetPathBlocking(graph, x, y, msepTest, -1, -1,
                    true);
            long stop5 = System.currentTimeMillis();
            times[4] = stop5 - start5;
            System.out.println("Time taken by getSepsetPathBlockingX: " + (stop5 - start5) + " ms");

            System.out.println("Sepset 1: " + sepset1);
//            System.out.println("Sepset 2: " + sepset2);
//            System.out.println("Sepset 3: " + sepset3);
//            System.out.println("Sepset 4: " + sepset4);
            System.out.println("Sepset 5: " + sepset5);

            // Note that methods 3 and 4 cannot find null sepsets from Oracle. These need to be tested separately from data.

            if (sepset1 != null) {
                System.out.println("sepset1 ind ? " + msepTest.checkIndependence(x, y, sepset1).isIndependent());
            } else {
                System.out.println("sepset1 is null");
            }

//            if (sepset2 != null) {
//                System.out.println("sepset2 ind ? " + msepTest.checkIndependence(x, y, sepset2).isIndependent());
//            } else {
//                System.out.println("sepset2 is null");
//            }
//
//            if (sepset3 != null) {
//                System.out.println("sepset3 ind ? " + msepTest.checkIndependence(x, y, sepset3).isIndependent());
//            } else {
//                System.out.println("sepset3 is null");
//            }
//
//            if (sepset4 != null) {
//                System.out.println("sepset4 ind ? " + msepTest.checkIndependence(x, y, sepset4).isIndependent());
//            } else {
//                System.out.println("sepset4 is null");
//            }
//
//            if (sepset5 != null) {
//                System.out.println("sepset5 ind ? " + msepTest.checkIndependence(x, y, sepset5).isIndependent());
//            } else {
//                System.out.println("sepset5 is null");
//            }

            // Are the sepsets null?
            numNullRecursive += sepset1 == null ? 1 : 0;
//            numNullGreedy += sepset2 == null ? 1 : 0;
//            numNullMaxP += sepset3 == null ? 1 : 0;
//            numNullMinP += sepset4 == null ? 1 : 0;
            numNullPathBlocking += sepset5 == null ? 1 : 0;

            // Is the various sepsets are not null, then they should be independent.
            numRecursivePass += sepset1 != null && msepTest.checkIndependence(x, y, sepset1).isIndependent() ? 1 : 0;
//            numGreedyPass += sepset2 != null && msepTest.checkIndependence(x, y, sepset2).isIndependent() ? 1 : 0;
//            numMaxPPass += sepset3 != null && msepTest.checkIndependence(x, y, sepset3).isIndependent() ? 1 : 0;
//            numMinPPass += sepset4 != null && msepTest.checkIndependence(x, y, sepset4).isIndependent() ? 1 : 0;
            numPathBlockingPass += sepset5 != null && msepTest.checkIndependence(x, y, sepset5).isIndependent() ? 1 : 0;

            for (int j = 0; j < times.length; j++) {
                timeSums[j] += times[j];
            }
        }

        // Print (formtted) results.
        System.out.println();
        System.out.println(numReps + " repetitions of the test were performed.");
        System.out.println();

        System.out.println("Number of times the sepset was null for recursive: " + numNullRecursive);
//        System.out.println("Number of times the sepset was null for greedy: " + numNullGreedy);
//        System.out.println("Number of times the sepset was null for max p: " + numNullMaxP);
//        System.out.println("Number of times the sepset was null for min p: " + numNullMinP);
        System.out.println("Number of times the sepset was null for path blocking: " + numNullPathBlocking);
        System.out.println();

        System.out.println("Number of times the sepset was not null and passed for recursive: " + numRecursivePass);
//        System.out.println("Number of times the sepset was not null and passed for greedy: " + numGreedyPass);
//        System.out.println("Number of times the sepset was not null and passed for max p: " + numMaxPPass);
//        System.out.println("Number of times the sepset was not null and passed for min p: " + numMinPPass);
        System.out.println("Number of times the sepset was not null and passed for path blocking: " + numPathBlockingPass);
        System.out.println();

        System.out.println("The total time required for recursive was " + timeSums[0] + " ms");
//        System.out.println("The total time required for greedy was " + timeSums[1] + " ms");
//        System.out.println("The total time required for max p was " + timeSums[2] + " ms");
//        System.out.println("The total time required for min p was " + timeSums[3] + " ms");
        System.out.println("The total time required for path blocking was " + timeSums[4] + " ms");
    }
}