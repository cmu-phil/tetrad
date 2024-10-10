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

import static edu.cmu.tetrad.search.SepsetFinder.blockPathsLocalMarkov;
import static edu.cmu.tetrad.search.SepsetFinder.blockPathsRecursively;

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
            BLOCK_PATHS_NONCOLLIDERS_ONLY
        }

        List<Method> methods = List.of(
//                Method.BLOCK_PATHS_WITH_MARKOV_BLANKET,
//                Method.BLOCK_PATHS_LOCAL_MARKOV,
                Method.BLOCK_PATHS_RECURSIVELY
//                Method.BLOCK_PATHS_NONCOLLIDERS_ONLY,
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
                        blockingSet = blockPathsRecursively(graph, x, y, new HashSet<>(), msepTest);
                    }
                    case BLOCK_PATHS_LOCAL_MARKOV -> {
                        blockingSet = blockPathsLocalMarkov(graph, x);
                    }
                    case BLOCK_PATHS_NONCOLLIDERS_ONLY -> {
                        blockingSet = SepsetFinder.blockPathsNoncollidersOnly(graph, x, y, -1, graphType == GraphType.PAG);
                    }
                    case BLOCK_PATHS_GREEDY -> {
                        blockingSet = SepsetFinder.getSepsetContainingGreedy(graph, x, y, new HashSet<>(), msepTest, -1);
                    }
                    case BLOCK_PATHS_MAX_P -> {
                        blockingSet = SepsetFinder.getSepsetContainingMaxPHybrid(graph, x, y, new HashSet<>(), msepTest, -1);
                    }
                    case BLOCK_PATHS_MIN_P -> {
                        blockingSet = SepsetFinder.getSepsetContainingMinPHybrid(graph, x, y, new HashSet<>(), msepTest, -1);
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
}