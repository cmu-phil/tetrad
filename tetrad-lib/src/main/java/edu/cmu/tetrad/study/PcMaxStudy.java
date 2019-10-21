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
import edu.cmu.tetrad.algcomparison.algorithm.oracle.pattern.*;
import edu.cmu.tetrad.algcomparison.graph.RandomForward;
import edu.cmu.tetrad.algcomparison.independence.FisherZ;
import edu.cmu.tetrad.algcomparison.score.SemBicScore;
import edu.cmu.tetrad.algcomparison.simulation.SemSimulation;
import edu.cmu.tetrad.algcomparison.simulation.Simulations;
import edu.cmu.tetrad.algcomparison.statistic.*;
import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphNode;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.IndTestFisherZ;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.search.OrientColliders;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * An example script to simulate data and run a comparison analysis on it.
 *
 * @author jdramsey
 */
public class PcMaxStudy {
    public static void main(String... args) {
        Statistics statistics = new Statistics();

        statistics.add(new ParameterColumn(Params.COLLIDER_DISCOVERY_RULE));
        statistics.add(new ParameterColumn(Params.STABLE_FAS));
        statistics.add(new ParameterColumn(Params.SAMPLE_SIZE));
        statistics.add(new ParameterColumn(Params.PENALTY_DISCOUNT));
        statistics.add(new ParameterColumn(Params.ALPHA));
        statistics.add(new ParameterColumn(Params.MAX_DEGREE_FGES));
        statistics.add(new ParameterColumn(Params.NUM_MEASURES));
        statistics.add(new ParameterColumn(Params.AVG_DEGREE));

        statistics.add(new AdjacencyPrecision());
        statistics.add(new AdjacencyRecall());
        statistics.add(new ArrowheadPrecision());
        statistics.add(new ArrowheadRecall());
        statistics.add(new ArrowheadPrecisionCommonEdges());
        statistics.add(new ArrowheadRecallCommonEdges());
        statistics.add(new SparsityTrueGraph());
        statistics.add(new ElapsedTime());

        statistics.setWeight("AR", 1);
        statistics.setWeight("AHP", .5);

        Algorithms algorithms = new Algorithms();

//        algorithms.add(new FgesCpc(new SemBicTest()));
//        algorithms.add(new Fges(new SemBicScore()));
        algorithms.add(new PcAll(new FisherZ()));

        Comparison comparison = new Comparison();

        comparison.setShowAlgorithmIndices(true);
        comparison.setShowSimulationIndices(true);
        comparison.setSortByUtility(false);
//        comparison.setShowUtilities(true);
        comparison.setComparisonGraph(Comparison.ComparisonGraph.true_DAG);

        Simulations simulations = new Simulations();
        simulations.add(new SemSimulation(new RandomForward()));

        comparison.compareFromSimulations("pcmax", simulations, algorithms, statistics, getParameters());

    }

    private static Parameters getParameters() {
        Parameters parameters = new Parameters();

        parameters.set(Params.COEF_LOW, 0);
        parameters.set(Params.COEF_HIGH, .5);
        parameters.set(Params.VAR_LOW, .2);
        parameters.set(Params.VAR_HIGH, .9);

        parameters.set(Params.STABLE_FAS, true);
        parameters.set(Params.CONCURRENT_FAS, false);
        parameters.set(Params.CONFLICT_RULE, 3);
        parameters.set(Params.SYMMETRIC_FIRST_STEP, true);
        parameters.set(Params.FAITHFULNESS_ASSUMED, false);
        parameters.set(Params.COLLIDER_DISCOVERY_RULE, 2);
        parameters.set(Params.DIFFERENT_GRAPHS, true);

        parameters.set(Params.NUM_MEASURES, 10, 20, 30, 40, 50, 100, 200, 500);
        parameters.set(Params.AVG_DEGREE, 4, 8, 12, 16);
        parameters.set(Params.MAX_DEGREE_FGES, 10);

        parameters.set(Params.NUM_RUNS, 5);
        parameters.set(Params.DEPTH, 5);
        parameters.set(Params.ALPHA, 0.001);
        parameters.set(Params.ORIENTATION_ALPHA, -1);
        parameters.set(Params.PENALTY_DISCOUNT, 1);
        parameters.set(Params.SAMPLE_SIZE, 10000);
        parameters.set(Params.DO_MARKOV_LOOP, false);
        parameters.set(Params.USE_FDR_FOR_INDEPENDENCE, true);
        parameters.set(Params.MAX_DEGREE, 20);


//        parameters.set(Params.ERRORS_NORMAL, false);

        parameters.set(Params.VERBOSE, false);


        return parameters;
    }

    @Test
    public void testColliders() {
        Node y = new ContinuousVariable("y");

        List<Node> xx = new ArrayList<>();

        int numParents = 10;

        for (int i = 1; i <= numParents; i++) {
            xx.add(new ContinuousVariable("x" + i));
        }

        List<Node> nodes = new ArrayList<>(xx);
        nodes.add(y);

        Graph graph = new EdgeListGraph(nodes);

        for (int i = 0; i < numParents; i++) {
            graph.addDirectedEdge(xx.get(i), y);
        }

        SemPm pm = new SemPm(graph);
        SemIm im = new SemIm(pm);
        DataSet dataSet = im.simulateData(10000 , false);

        double alpha = 0.05;

        IndependenceTest test = new IndTestFisherZ(dataSet, alpha);

        int noncollider = 0;
        int collider = 0;

        for (int i = 0; i < numParents; i++) {
            for (int j = i + 1; j < numParents; j++) {
                boolean indep1 = test.isIndependent(xx.get(i), xx.get(j));// {//, Collections.singletonList(y));
                boolean indep2 = test.isIndependent(xx.get(i), xx.get(j), Collections.singletonList(y));
                if (indep1 && !indep2) collider++;
                else noncollider++;
            }
        }

        System.out.println("noncollider = " + noncollider);
        System.out.println("collider = " + collider);

        edu.cmu.tetrad.search.PcAll pc = new edu.cmu.tetrad.search.PcAll(test,  null);
        pc.setColliderDiscovery(OrientColliders.ColliderMethod.SEPSETS);
        pc.setFasType(edu.cmu.tetrad.search.PcAll.FasType.REGULAR);
        pc.setVerbose(true);
        Graph g = pc.search();

        System.out.println(g);
    }
}




