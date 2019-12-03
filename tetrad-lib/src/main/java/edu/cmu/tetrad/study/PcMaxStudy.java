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
import edu.cmu.tetrad.algcomparison.independence.SemBicTest;
import edu.cmu.tetrad.algcomparison.score.SemBicScore;
import edu.cmu.tetrad.algcomparison.simulation.SemSimulation;
import edu.cmu.tetrad.algcomparison.simulation.Simulations;
import edu.cmu.tetrad.algcomparison.statistic.*;
import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.IKnowledge;
import edu.cmu.tetrad.data.simulation.LoadDataAndGraphs;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.IndTestFisherZ;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.search.SearchGraphUtils;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.DataConvertUtils;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.cmu.tetrad.util.RandomUtil;
import edu.pitt.dbmi.data.reader.Data;
import edu.pitt.dbmi.data.reader.Delimiter;
import edu.pitt.dbmi.data.reader.tabular.VerticalDiscreteTabularDatasetFileReader;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * An example script to simulate data and run a comparison analysis on it.
 *
 * @author jdramsey
 */
public class PcMaxStudy {

    @Test
    public void test0() {
        Statistics statistics = new Statistics();

        statistics.add(new ParameterColumn(Params.SAMPLE_SIZE));
        statistics.add(new ParameterColumn(Params.NUM_MEASURES));
        statistics.add(new ParameterColumn(Params.AVG_DEGREE));
        statistics.add(new ParameterColumn(Params.ALPHA));
        statistics.add(new ParameterColumn(Params.PENALTY_DISCOUNT));

        statistics.add(new AdjacencyPrecision());
        statistics.add(new AdjacencyRecall());
        statistics.add(new AvgDegreeTrueGraph());
        statistics.add(new AvgDegreeEstGraph());
        statistics.add(new AvgDegreeRatio());
        statistics.add(new UtRandomnessStatististic());
        statistics.add(new SparsityTrueGraph());
        statistics.add(new AHPCBound());
        statistics.add(new ArrowheadPrecisionCommonEdges());
        statistics.add(new ArrowheadPrecision());
        statistics.add(new AHRCBound());
        statistics.add(new ArrowheadRecallCommonEdges());

        statistics.setWeight("AHPC", 1);

        Algorithms algorithms = new Algorithms();

        algorithms.add(new FgesCpc(new SemBicTest()));
//        algorithms.add(new Fges(new SemBicScore()));
//        algorithms.add(new Pc(new FisherZ()));
//        algorithms.add(new Cpc(new FisherZ()));
//        algorithms.add(new Mpc(new FisherZ()));
//        algorithms.add(new PcMax(new FisherZ()));

        Comparison comparison = new Comparison();

        comparison.setShowAlgorithmIndices(true);
        comparison.setShowSimulationIndices(true);
        comparison.setSortByUtility(false);
//        comparison.setShowUtilities(true);
        comparison.setComparisonGraph(Comparison.ComparisonGraph.Pattern_of_the_true_DAG);

        Simulations simulations = new Simulations();
        simulations.add(new SemSimulation(new RandomForward()));

        comparison.compareFromSimulations("pcmax", simulations, algorithms, statistics, getParameters());

    }

    private static Parameters getParameters() {
        Parameters parameters = new Parameters();

        parameters.set(Params.COEF_LOW, 0);
        parameters.set(Params.COEF_HIGH, .7);
        parameters.set(Params.VAR_LOW, .5);
        parameters.set(Params.VAR_HIGH, .9);

        parameters.set(Params.STABLE_FAS, true);
        parameters.set(Params.CONCURRENT_FAS, false);
        parameters.set(Params.CONFLICT_RULE, 3);
        parameters.set(Params.SYMMETRIC_FIRST_STEP, true);
        parameters.set(Params.FAITHFULNESS_ASSUMED, false);
        parameters.set(Params.DIFFERENT_GRAPHS, true);
        parameters.set(Params.MAX_DEGREE_FGES, 100);

        parameters.set(Params.NUM_MEASURES, 20, 30, 50, 100, 200);
        parameters.set(Params.AVG_DEGREE, 2, 4, 6, 8);
        parameters.set(Params.NUM_RUNS, 5);
        parameters.set(Params.DEPTH, 5);
        parameters.set(Params.ALPHA, 0.001);
        parameters.set(Params.ORIENTATION_ALPHA, -1);
        parameters.set(Params.PENALTY_DISCOUNT, 1);
        parameters.set(Params.ALPHA, 0.001, 0.01, 0.1);
        parameters.set(Params.SAMPLE_SIZE, 1000);

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
            if (RandomUtil.getInstance().nextDouble() < 0.5) {
                graph.addDirectedEdge(xx.get(i), y);
            } else {
                graph.addDirectedEdge(y, xx.get(i));
            }
        }

        SemPm pm = new SemPm(graph);
        SemIm im = new SemIm(pm);
        DataSet dataSet = im.simulateData(1000, false);

        double alpha = 0.01;

        IndependenceTest test = new IndTestFisherZ(dataSet, alpha);

        int noncollider = 0;
        int collider = 0;

        Graph colliderGraph = new EdgeListGraph(dataSet.getVariables());

        for (int i = 0; i < numParents; i++) {
            for (int j = i + 1; j < numParents; j++) {
                double p1 = test.getPValue();

                test.isIndependent(xx.get(j), y);
                double p2 = test.getPValue();

                if (p1 < alpha && p2 < alpha) {
                    boolean indep1 = test.isIndependent(xx.get(i), xx.get(j));
                    test.getPValue();

                    if (indep1) {
                        System.out.println("Collider = " + xx.get(i) + "->y<-" + xx.get(j));
                        collider++;
                        colliderGraph.addDirectedEdge(xx.get(i), y);
                        colliderGraph.addDirectedEdge(xx.get(j), y);
                    } else noncollider++;
                }
            }
        }

        List<Node> parents = graph.getParents(y);

        for (int i = 0; i < parents.size(); i++) {
            for (int j = i + 1; j < parents.size(); j++) {
                boolean indep1 = test.isIndependent(parents.get(i), parents.get(j));
                double p1 = test.getPValue();
                boolean indep2 = test.isIndependent(parents.get(i), parents.get(j), Collections.singletonList(y));
                double p2 = test.getPValue();

                if (indep2) {
                    System.out.println("CUC violation " + parents.get(i) + "--" + y + "--" + parents.get(j)
                            + " p1 - p2 = " + (p1 - p2));
                    graph.addUndirectedEdge(parents.get(i), parents.get(j));
                }
            }
        }

        System.out.println("noncollider = " + noncollider);
        System.out.println("collider = " + collider);

        System.out.println(graph);

//        edu.cmu.tetrad.search.PcAll pc = new edu.cmu.tetrad.search.PcAll(test, null);
//        pc.setColliderDiscovery(OrientColliders.ColliderMethod.CPC);
//        pc.setFasType(edu.cmu.tetrad.search.PcAll.FasType.REGULAR);
//        pc.setDepth(2);
////        pc.setVerbose(true);
//        Graph g = pc.search();
//
        System.out.println("Parents = " + graph.getParents(y));
//
//        System.out.println(g);
    }

    @Test
    public void test2() {


        Graph graph = GraphUtils.randomGraph(10, 0, 10, 100,
                100, 100, false);


        SemPm pm = new SemPm(graph);
        SemIm im = new SemIm(pm);
        DataSet dataSet = im.simulateData(1000, false);

        double alpha = 0.01;

        IndependenceTest test = new IndTestFisherZ(dataSet, alpha);

        Graph knowledgeGraph = new EdgeListGraph(dataSet.getVariables());

        for (Node y : dataSet.getVariables()) {
            List<Node> maybeParents = new ArrayList<>();

            for (Node x : dataSet.getVariables()) {
                if (y == x) continue;

                test.isIndependent(y, x);
                double p = test.getPValue();

                if (p < 0.1) maybeParents.add(x);
            }

            Set<Node> parents = new HashSet<>();


            for (int i = 0; i < maybeParents.size(); i++) {
                for (int j = i + 1; j < maybeParents.size(); j++) {
                    boolean indep1 = test.isIndependent(maybeParents.get(i), maybeParents.get(j));
                    double p3 = test.getPValue();

                    test.isIndependent(maybeParents.get(i), maybeParents.get(j), Collections.singletonList(y));
                    double p4 = test.getPValue();

                    if (indep1) {
                        System.out.println("Collider = " + maybeParents.get(i) + "->" + y + "<-" + maybeParents.get(j));
                        parents.add(maybeParents.get(i));
                        parents.add(maybeParents.get(j));
                    }
                }
            }

            List<Node> _parents = new ArrayList<>(parents);

            for (int i = 0; i < _parents.size(); i++) {
                for (int j = i + 1; j < _parents.size(); j++) {
                    if (knowledgeGraph.isAdjacentTo(_parents.get(i), y)) {
                        knowledgeGraph.removeEdge(_parents.get(i), y);
                        knowledgeGraph.addUndirectedEdge(_parents.get(i), y);
                    }

                    if (knowledgeGraph.isAdjacentTo(_parents.get(j), y)) {
                        knowledgeGraph.removeEdge(_parents.get(j), y);
                        knowledgeGraph.addUndirectedEdge(_parents.get(j), y);
                    }

                    if (!knowledgeGraph.isAdjacentTo(_parents.get(i), y)) {
                        knowledgeGraph.addDirectedEdge(_parents.get(i), y);
                    }

                    if (!knowledgeGraph.isAdjacentTo(_parents.get(j), y)) {
                        knowledgeGraph.addDirectedEdge(_parents.get(j), y);
                    }
                }
            }
        }

        System.out.println(SearchGraphUtils.patternForDag(graph));
        System.out.println(knowledgeGraph);
    }

    @Test
    public void test3() {
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

        algorithms.add(new Fges(new SemBicScore()));
        algorithms.add(new FgesCpc(new SemBicTest()));
        algorithms.add(new PcAll(new FisherZ()));

        Comparison comparison = new Comparison();

        comparison.setShowAlgorithmIndices(true);
        comparison.setShowSimulationIndices(true);
        comparison.setSortByUtility(false);
//        comparison.setShowUtilities(true);
        comparison.setComparisonGraph(Comparison.ComparisonGraph.true_DAG);

        Simulations simulations = new Simulations();

        simulations.add(new LoadDataAndGraphs("/Users/user/Downloads/save"));

//        simulations.add(new SemSimulation(new RandomForward()));

        comparison.compareFromSimulations("pcmax", simulations, algorithms, statistics, getParameters());

    }


    @Test
    public void test5() {
        try {
            VerticalDiscreteTabularDatasetFileReader reader = new VerticalDiscreteTabularDatasetFileReader(
                    new File("/Users/user/Downloads/Inputdata.csv").toPath(), Delimiter.COMMA);

            Data data = reader.readInData();

            DataModel dataSet = DataConvertUtils.toDataModel(data);

            System.out.println("Data loaded");

            IKnowledge knowledge = new edu.cmu.tetrad.data.DataReader().parseKnowledge(
                    new File("/Users/user/Downloads/Knowledge"));

            System.out.println("Knowledge loaded");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}




