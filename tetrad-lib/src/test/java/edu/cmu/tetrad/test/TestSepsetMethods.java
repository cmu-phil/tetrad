/// ////////////////////////////////////////////////////////////////////////////
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
/// ////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.test;

import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.RecursiveBlocking;
import edu.cmu.tetrad.search.SepsetFinder;
import edu.cmu.tetrad.search.test.MsepTest;
import edu.cmu.tetrad.search.utils.DagToPag;
import edu.cmu.tetrad.search.utils.LogUtilsSearch;
import edu.cmu.tetrad.util.SublistGenerator;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static edu.cmu.tetrad.search.SepsetFinder.blockPathsLocalMarkov;
import static org.junit.Assert.assertTrue;

/**
 * The TestSepsetMethods class  is responsible for testing various methods for finding a sepset of two nodes in a DAG.
 */
public class TestSepsetMethods {

    private static final Logger log = LoggerFactory.getLogger(TestSepsetMethods.class);

    /**
     * This method is used to test various methods for finding a sepset of two nodes in a directed acyclic graph (DAG).
     * It performs several repetitions of the test and calculates the total time taken for each step.
     */
    @Test
    public void test1() {
        int numNodes = 20;
        int numEdges = 40;
        int numLatentsForPag = 5; // Ignored for the DAG or CPDAG cases.
        int numReps = 100;

        enum GraphType {
            DAG, CPDAG, PAG
        }

        GraphType graphType = GraphType.DAG;

        enum Method {
            BLOCK_PATHS_WITH_MARKOV_BLANKET,
            BLOCK_PATHS_LOCAL_MARKOV,
            BLOCK_PATHS_GREEDY,
            BLOCK_PATHS_MAX_P,
            BLOCK_PATHS_MIN_P,
            BLOCK_PATHS_RECURSIVELY,
        }

        List<Method> methods = List.of(
//                Method.BLOCK_PATHS_WITH_MARKOV_BLANKET,
//                Method.BLOCK_PATHS_LOCAL_MARKOV,
                Method.BLOCK_PATHS_RECURSIVELY
//                Method.BLOCK_PATHS_GREEDY,
//                Method.BLOCK_PATHS_MAX_P,
//                Method.BLOCK_PATHS_MIN_P
        );

        // Make a list of numNodes nodes.
        List<Node> nodes = new ArrayList<>();

        for (int i = 0; i < numNodes; i++) {
            nodes.add(new ContinuousVariable("X" + i));
        }

        Graph graph;

        switch (graphType) {
            case DAG -> graph = RandomGraph.randomDag(nodes, 0, numEdges, 100, 100, 100, false);
            case CPDAG -> {
                graph = RandomGraph.randomDag(nodes, 0, numEdges, 100, 100, 100, false);
                graph = GraphTransforms.dagToCpdag(graph);
            }
            case PAG -> {
                graph = RandomGraph.randomDag(nodes, numLatentsForPag, numEdges, 100, 100, 100, false);
                graph = GraphTransforms.dagToPag(graph);
            }
            default -> throw new IllegalArgumentException("Unknown graph type: " + graphType);
        }

        nodes = graph.getNodes();
        numNodes = nodes.size();
        numEdges = graph.getNumEdges();

        long[] timeSums = new long[methods.size()];
        int[] numPass = new int[methods.size()];

        for (int i = 0; i < numReps; i++) {
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

            MsepTest msepTest = new MsepTest(graph, graphType == GraphType.PAG);

            for (int k = 0; k < methods.size(); k++) {
                Method method = methods.get(k);
                Set<Node> blockingSet;
                long start = System.currentTimeMillis();

                switch (method) {
                    case BLOCK_PATHS_WITH_MARKOV_BLANKET -> {
                        blockingSet = SepsetFinder.blockPathsWithMarkovBlanket(x, graph);
                    }
                    case BLOCK_PATHS_RECURSIVELY -> {
                        try {
                            blockingSet = RecursiveBlocking.blockPathsRecursively(graph, x, y, new HashSet<Node>(), Set.of(), -1);

                            if (blockingSet == null) {

                                // There are known cases where this cannot succeed--Puzzle #2.
                                continue;
                            }
                        } catch (InterruptedException ex) {
                            throw new RuntimeException(ex);
                        }
                    }
                    case BLOCK_PATHS_LOCAL_MARKOV -> {
                        blockingSet = blockPathsLocalMarkov(graph, x);
                    }
                    case BLOCK_PATHS_GREEDY -> {
                        blockingSet = SepsetFinder.findSepsetSubsetOfAdjxOrAdjy(graph, x, y, new HashSet<>(), msepTest, -1);
                    }
                    case BLOCK_PATHS_MAX_P -> {
                        try {
                            blockingSet = SepsetFinder.getSepsetContainingMaxPHybrid(graph, x, y, new HashSet<>(), msepTest, -1);
                        } catch (InterruptedException ex) {
                            throw new RuntimeException(ex);
                        }
                    }
                    case BLOCK_PATHS_MIN_P -> {
                        try {
                            blockingSet = SepsetFinder.getSepsetContainingMinPHybrid(graph, x, y, msepTest, -1);
                        } catch (InterruptedException ex) {
                            throw new RuntimeException(ex);
                        }
                    }
                    default -> throw new IllegalArgumentException("Unknown method: " + graphType);
                }

                long stop = System.currentTimeMillis();
                timeSums[k] += stop - start;

                System.out.println("Sepset " + method + ": " + blockingSet);
                if (blockingSet != null) {
                    System.out.println("M-sep = " + msepTest.checkIndependence(x, y, blockingSet).isIndependent());
                    numPass[k] += msepTest.checkIndependence(x, y, blockingSet).isIndependent() ? 1 : 0;
                }
            }
        }

        System.out.println();
        System.out.println("Graph type = " + graphType);
        System.out.println();
        System.out.println(numReps + " repetitions of the test were performed.");
        System.out.println();

        System.out.println("Num nodes = " + numNodes + " Num edges = " + numEdges + " Num latents (for PAGS only) = " + numLatentsForPag);
        System.out.println();

        for (int i = 0; i < methods.size(); i++) {
            System.out.println("Number of times msep(x, y | set) with " + methods.get(i) + " = " + numPass[i]);
        }

        System.out.println();

        for (int i = 0; i < methods.size(); i++) {
            System.out.println("The total time required for " + methods.get(i) + " = " + timeSums[i]);
        }
    }

    /**
     * This method is used to test the blockPathsRecursively method for finding a set of nodes that blocks all blockable
     * paths between two nodes in a graph.
     */
    @Test
    public void test2() {

        Graph graph = GraphUtils.convert("X-->Y,X-->Z,X-->W,Y-->Z,W-->Z");

        System.out.println(graph);

        Set<Node> blocking = null;
        try {
            Node x = graph.getNode("X");
            Node y = graph.getNode("Z");
            blocking = RecursiveBlocking.blockPathsRecursively(graph, x, y, new HashSet<Node>(), Set.of(), -1);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        System.out.println(blocking);

        assertTrue(blocking.containsAll(Set.of(graph.getNode("Y"), graph.getNode("W"))));

        Graph graph2 = GraphUtils.convert("X-->Y,X-->W,Y-->Z,W-->Z");

        assertTrue(new MsepTest(graph2, false).checkIndependence(graph2.getNode("X"), graph2.getNode("Z"), blocking).isIndependent());

    }

    /**
     * This method is used to test the blockPathsRecursively method for finding a set of nodes that blocks all blockable
     * paths between two nodes in a graph, for local Markov.
     * <p>
     * The blocking set returned by blockPathsRecursively should always be a sepset of x and y given parents(x) for
     * non-descendants x.
     */
    @Test
    public void test3() {

        System.out.println("Checking to make sure blockPathsRecursively works for local Markov for a DAG.");

        Graph graph = RandomGraph.randomDag(20, 0, 40, 100,
                100, 100, false);

        for (Node x : graph.getNodes()) {
            for (Node y : graph.getNodes()) {
                if (x.equals(y)) {
                    continue;
                }

                Set<Node> parents = new HashSet<>(graph.getParents(x));

                if (parents.contains(y)) {
                    continue;
                }

                if (graph.paths().isDescendentOf(y, x)) {
                    continue;
                }

                Set<Node> blocking = null;
                try {
                    blocking = RecursiveBlocking.blockPathsRecursively(graph, x, y, parents, Set.of(), -1);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }

                boolean msep = new MsepTest(graph, false).checkIndependence(x, y, blocking).isIndependent();

                if (!msep) {
                    System.out.println(LogUtilsSearch.independenceFact(x, y, blocking));
                }

                assertTrue(msep);
            }
        }
    }

    // This doesn't work if we return z in instead possibly null from the recursive method.
//    @Test
    public void test4() {
        System.out.println("Checking to make sure blockPathsRecursively works for dsep(x, y | mb(x)) for a PAG for y not in mb(x).");

        Graph dag = RandomGraph.randomDag(20, 10, 40, 100,
                100, 100, false);

        Graph pag = new DagToPag(dag).convert();

        for (Node x : pag.getNodes()) {
            for (Node y : pag.getNodes()) {
                if (x.equals(y)) {
                    continue;
                }

                if (pag.paths().markovBlanket(x).contains(y)) {
                    continue;
                }

                try {
                    Set<Node> blocking = RecursiveBlocking.blockPathsRecursively(dag, x, y, Set.of(),
                            Set.of(), -1);
                    boolean msep = new MsepTest(pag, false).checkIndependence(x, y, blocking).isIndependent();

                    if (!msep) {
                        System.out.println(LogUtilsSearch.independenceFact(x, y, blocking));
                    }

                    assertTrue(msep);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    @Test
    public void test5() {
        System.out.println("Checking to make sure blockPathsRecursively distinguishes adj vs non-adj for dsep(x, y | \n" +
                           "path_blocking(x)) for a PAG for y not in mb(x).");

        boolean allOK = true;

        for (int i = 0; i < 1; i++) {
            Graph dag = RandomGraph.randomDag(15, 5, 40, 100,
                    100, 100, false);

            Graph pag = new DagToPag(dag).convert();


            for (Node x : pag.getNodes()) {
                for (Node y : pag.getNodes()) {
                    if (x.equals(y)) {
                        continue;
                    }

                    try {
                        Set<Node> blocking = RecursiveBlocking.blockPathsRecursively(pag, x, y, Set.of(),
                                Set.of(), -1);

                        if (new MsepTest(pag, false).checkIndependence(x, y, blocking).isIndependent()) {

                            // If independent, then ~adj(x, y).
                            if (pag.isAdjacentTo(x, y)) {
                                allOK = false;
                            }
                        } else {
                            System.out.print(pag.isAdjacentTo(x, y) ? " Adjacent" : " Not adjacent");
                            System.out.print(pag.paths().markovBlanket(x).contains(y) ? ", In MB" : ", Not in MB");

                            // If dependent, then y in MB(x).
                            if (!pag.paths().markovBlanket(x).contains(y)) {
                                allOK = false;
                            }

                            if (removeIfInMb(pag, x, y)) {
                                if (pag.isAdjacentTo(x, y)) {
                                    allOK = false;
                                }
                            } else {
                                System.out.print( ", OK to remove... ");
                            }

                            System.out.println();
                        }
                    } catch (InterruptedException e) {
                        System.out.println("Exception");
                        throw new RuntimeException(e);
                    }
                }
            }
        }

        assertTrue(allOK);
    }

    private boolean removeIfInMb(Graph pag, Node x, Node y) {
        List<Node> common = pag.getAdjacentNodes(x);
        common.retainAll(pag.getAdjacentNodes(y));

        SublistGenerator gen2 = new SublistGenerator(common.size(), common.size());
        int[] choice2;

        while ((choice2 = gen2.next()) != null) {
            Set<Node> c = GraphUtils.asSet(choice2, common);

            try {
                Set<Node> b = RecursiveBlocking.blockPathsRecursively(pag, x, y, Set.of(), Set.of(), -1);

                b.removeAll(c);

                if (new MsepTest(pag, false).checkIndependence(x, y, b).isIndependent()) {
                    return true;
                }
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        return false;
    }
}