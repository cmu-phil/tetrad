///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015 by Peter Spirtes, Richard Scheines, Joseph   //
// Ramsey, and Clark Glymour.                                                //
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

package edu.cmu.tetrad.study;

import edu.cmu.tetrad.algcomparison.Comparison;
import edu.cmu.tetrad.algcomparison.algorithm.Algorithms;
import edu.cmu.tetrad.algcomparison.algorithm.oracle.pag.Fci;
import edu.cmu.tetrad.algcomparison.algorithm.oracle.pag.Gfci;
import edu.cmu.tetrad.algcomparison.algorithm.oracle.pattern.Fges;
import edu.cmu.tetrad.algcomparison.graph.RandomForward;
import edu.cmu.tetrad.algcomparison.graph.SingleGraph;
import edu.cmu.tetrad.algcomparison.independence.FisherZ;
import edu.cmu.tetrad.algcomparison.score.SemBicScore;
import edu.cmu.tetrad.algcomparison.simulation.LinearFisherModel;
import edu.cmu.tetrad.algcomparison.simulation.SemSimulation;
import edu.cmu.tetrad.algcomparison.simulation.Simulations;
import edu.cmu.tetrad.algcomparison.statistic.*;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;

/**
 * An example script to simulate data and run a comparison analysis on it.
 *
 * @author jdramsey
 */
public class BryanSensitivityStudy {
    public static void main(String... args) {
        Statistics statistics = new Statistics();

        statistics.add(new ParameterColumn(Params.SAMPLE_SIZE));
        statistics.add(new ParameterColumn(Params.PENALTY_DISCOUNT));
        statistics.add(new ParameterColumn(Params.ALPHA));
        statistics.add(new ParameterColumn("thresholdAlpha"));

//        statistics.add(new NumberOfEdgesEst());
        statistics.add(new AdjacencyPrecision());
        statistics.add(new AdjacencyRecall());
        statistics.add(new ArrowheadPrecision());
        statistics.add(new ArrowheadRecall());
        statistics.add(new F1All());
//        statistics.add(new ArrowheadPrecisionCommonEdges());
//        statistics.add(new ArrowheadRecallCommonEdges());
        statistics.add(new GraphExactlyRight());
        statistics.add(new ElapsedTime());

        statistics.setWeight("AP", 1);
        statistics.setWeight("AR", 1);
        statistics.setWeight("AHP", 1);
        statistics.setWeight("AHR", 1);

        Algorithms algorithms = new Algorithms();

        algorithms.add(new Gfci(new FisherZ(), new SemBicScore()));
//        algorithms.add(new Fci(new FisherZ()));

        Comparison comparison = new Comparison();

        comparison.setShowAlgorithmIndices(true);
        comparison.setShowSimulationIndices(true);
        comparison.setSortByUtility(false);
        comparison.setShowUtilities(false);
        comparison.setComparisonGraph(Comparison.ComparisonGraph.PAG_of_the_true_DAG);


        {
            Parameters parameters = getParameters();
            Graph graph = getGraph1();
            Simulations simulations = new Simulations();
            simulations.add(new SemSimulation(new SingleGraph(graph)));
            comparison.compareFromSimulations("bryan.simulation", simulations, "graph1.txt", algorithms, statistics, parameters);
        }

        {
            Parameters parameters = getParameters();
            Graph graph = getGraph2();
            Simulations simulations = new Simulations();
            simulations.add(new SemSimulation(new SingleGraph(graph)));
            comparison.compareFromSimulations("bryan.simulation", simulations, "graph2.txt", algorithms, statistics, parameters);
        }

        {
            Parameters parameters = getParameters();
            Graph graph = getGraph3();
            Simulations simulations = new Simulations();
            simulations.add(new SemSimulation(new SingleGraph(graph)));
            comparison.compareFromSimulations("bryan.simulation", simulations, "graph3.txt", algorithms, statistics, parameters);
        }

        {
            Parameters parameters = getParameters();
            Graph graph = getGraph4();
            Simulations simulations = new Simulations();
            simulations.add(new SemSimulation(new SingleGraph(graph)));
            comparison.compareFromSimulations("bryan.simulation", simulations, "graph4.txt", algorithms, statistics, parameters);
        }
    }

    private static Parameters getParameters() {
        Parameters parameters = new Parameters();

        parameters.set("thresholdAlpha", .5);

        parameters.set(Params.NUM_RUNS, 20);
        parameters.set(Params.SAMPLE_SIZE, 500, 1000, 5000, 10000, 50000);

        parameters.set(Params.COEF_LOW, 0.2);
        parameters.set(Params.COEF_HIGH, 0.7);
        parameters.set(Params.VAR_LOW, 1);
        parameters.set(Params.VAR_HIGH, 3);
        parameters.set(Params.VERBOSE, false);
        parameters.set(Params.COEF_SYMMETRIC, true);
        parameters.set(Params.RANDOMIZE_COLUMNS, true);

        parameters.set(Params.DEPTH, -1);

        parameters.set(Params.SYMMETRIC_FIRST_STEP, false);
        parameters.set(Params.FAITHFULNESS_ASSUMED, false);
        parameters.set("verbose", false);

        parameters.set(Params.SYMMETRIC_FIRST_STEP, false);
        parameters.set(Params.FAITHFULNESS_ASSUMED, true);
        parameters.set(Params.MAX_DEGREE, 100);
        parameters.set(Params.MAX_INDEGREE, 100);
        parameters.set(Params.MAX_OUTDEGREE, 100);

        parameters.set(Params.PENALTY_DISCOUNT, 1.);
        parameters.set(Params.STRUCTURE_PRIOR, 3);
        parameters.set(Params.ALPHA, 0.001, 0.01);

        parameters.set(Params.STABLE_FAS, true);
        parameters.set(Params.CONCURRENT_FAS, true);
        parameters.set(Params.COLLIDER_DISCOVERY_RULE, 2, 3);
        return parameters;
    }

    private static Graph getGraph1() {
        Node A = new GraphNode("A");
        Node B = new GraphNode("B");
        Node C = new GraphNode("C");
        Node D = new GraphNode("D");

        Node H1 = new GraphNode("H1");
        H1.setNodeType(NodeType.LATENT);

        Graph graph = new EdgeListGraph();
        graph.addNode(A);
        graph.addNode(B);
        graph.addNode(C);
        graph.addNode(D);
        graph.addNode(H1);

        graph.addDirectedEdge(A, C);
        graph.addDirectedEdge(B, D);

        graph.addDirectedEdge(H1, C);
        graph.addDirectedEdge(H1, D);

        return graph;
    }

    private static Graph getGraph2() {
        Node A = new GraphNode("A");
        Node B = new GraphNode("B");
        Node C = new GraphNode("C");
        Node D = new GraphNode("D");

        Node H1 = new GraphNode("H1");
        H1.setNodeType(NodeType.LATENT);

        Node H2 = new GraphNode("H2");
        H2.setNodeType(NodeType.LATENT);

        Graph graph = new EdgeListGraph();
        graph.addNode(A);
        graph.addNode(B);
        graph.addNode(C);
        graph.addNode(D);
        graph.addNode(H1);
        graph.addNode(H2);

        graph.addDirectedEdge(A, B);
        graph.addDirectedEdge(C, D);

        graph.addDirectedEdge(H1, A);
        graph.addDirectedEdge(H1, D);

        graph.addDirectedEdge(H2, B);
        graph.addDirectedEdge(H2, C);

        return graph;
    }

    private static Graph getGraph3() {
        Node A = new GraphNode("A");
        Node B = new GraphNode("B");
        Node C = new GraphNode("C");
        Node D = new GraphNode("D");

        Node H1 = new GraphNode("H1");
        H1.setNodeType(NodeType.LATENT);

        Node H2 = new GraphNode("H2");
        H2.setNodeType(NodeType.LATENT);

        Node H3 = new GraphNode("H3");
        H3.setNodeType(NodeType.LATENT);

        Node H4 = new GraphNode("H4");
        H4.setNodeType(NodeType.LATENT);

        Graph graph = new EdgeListGraph();
        graph.addNode(A);
        graph.addNode(B);
        graph.addNode(C);
        graph.addNode(D);
        graph.addNode(H1);
        graph.addNode(H2);
        graph.addNode(H3);
        graph.addNode(H4);

        graph.addDirectedEdge(H1, A);
        graph.addDirectedEdge(H1, C);

        graph.addDirectedEdge(H2, A);
        graph.addDirectedEdge(H2, B);

        graph.addDirectedEdge(H3, B);
        graph.addDirectedEdge(H3, D);

        graph.addDirectedEdge(H4, C);
        graph.addDirectedEdge(H4, D);

        return graph;
    }

    private static Graph getGraph4() {
        Node A = new GraphNode("A");
        Node B = new GraphNode("B");
        Node C = new GraphNode("C");
        Node D = new GraphNode("D");

        Node H1 = new GraphNode("H1");
        H1.setNodeType(NodeType.LATENT);

        Node H2 = new GraphNode("H2");
        H2.setNodeType(NodeType.LATENT);


        Graph graph = new EdgeListGraph();
        graph.addNode(A);
        graph.addNode(B);
        graph.addNode(C);
        graph.addNode(D);
        graph.addNode(H1);
        graph.addNode(H2);

        graph.addDirectedEdge(A, B);
        graph.addDirectedEdge(B, D);

        graph.addDirectedEdge(H1, B);
        graph.addDirectedEdge(H1, C);

        graph.addDirectedEdge(H2, C);
        graph.addDirectedEdge(H2, D);

        return graph;
    }
}




