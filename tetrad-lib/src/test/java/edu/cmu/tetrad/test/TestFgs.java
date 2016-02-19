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

package edu.cmu.tetrad.test;

import edu.cmu.tetrad.bayes.BayesIm;
import edu.cmu.tetrad.bayes.BayesPm;
import edu.cmu.tetrad.bayes.MlBayesIm;
import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.CovarianceMatrixOnTheFly;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphConverter;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.*;
import edu.cmu.tetrad.sem.LargeSemSimulator;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.MatrixUtils;
import edu.cmu.tetrad.util.RandomUtil;
import org.junit.Test;

import java.io.PrintStream;
import java.util.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author Joseph Ramsey
 */
public class TestFgs {


    private PrintStream out = System.out;
//    private OutputStream out =

    @Test
    public void explore1() {
        RandomUtil.getInstance().setSeed(1450184147770L);

        int numVars = 10;
        double edgesPerNode = 1.0;
        int numCases = 1000;
        double penaltyDiscount = 2.0;

        final int numEdges = (int) (numVars * edgesPerNode);

        List<Node> vars = new ArrayList<>();

        for (int i = 0; i < numVars; i++) {
            vars.add(new ContinuousVariable("X" + i));
        }

        Graph dag = GraphUtils.randomGraphRandomForwardEdges(vars, 0, numEdges, 30, 15, 15, false);
//        printDegreeDistribution(dag, System.out);

        int[] causalOrdering = new int[vars.size()];

        for (int i = 0; i < vars.size(); i++) {
            causalOrdering[i] = i;
        }

        LargeSemSimulator simulator = new LargeSemSimulator(dag, vars, causalOrdering);
        simulator.setOut(out);
        DataSet data = simulator.simulateDataAcyclic(numCases);

//        ICovarianceMatrix cov = new CovarianceMatrix(data);
        ICovarianceMatrix cov = new CovarianceMatrixOnTheFly(data);

        Fgs fgs = new Fgs(cov);
        fgs.setVerbose(false);
        fgs.setNumPatternsToStore(0);
        fgs.setPenaltyDiscount(penaltyDiscount);
        fgs.setOut(out);
        fgs.setFaithfulnessAssumed(true);
        fgs.setDepth(1);
        fgs.setCycleBound(5);

        Graph estPattern = fgs.search();

//        printDegreeDistribution(estPattern, out);

        final Graph truePattern = SearchGraphUtils.patternForDag(dag);

        int[][] counts = SearchGraphUtils.graphComparison(estPattern, truePattern, null);

        int[][] expectedCounts = {
                {2, 0, 0, 0, 0, 0},
                {0, 0, 0, 0, 0, 0},
                {0, 0, 0, 0, 0, 0},
                {0, 0, 0, 0, 0, 0},
                {0, 0, 0, 8, 0, 0},
                {0, 0, 0, 0, 0, 0},
                {0, 0, 0, 0, 0, 0},
                {0, 0, 0, 0, 0, 0},
        };

        for (int i = 0; i < counts.length; i++) {
            assertTrue(Arrays.equals(counts[i], expectedCounts[i]));
        }
    }

    @Test
    public void explore2() {
        RandomUtil.getInstance().setSeed(1450956446672L);

        int numVars = 10;
        double edgeFactor = 1.0;
        int numCases = 1000;
        double structurePrior = .01;
        double samplePrior = 10;

        List<Node> vars = new ArrayList<>();

        for (int i = 0; i < numVars; i++) {
            vars.add(new ContinuousVariable("X" + i));
        }

        Graph dag = GraphUtils.randomGraphRandomForwardEdges(vars, 0, (int) (numVars * edgeFactor),
                30, 15, 15, false);
//        printDegreeDistribution(dag, out);

        BayesPm pm = new BayesPm(dag, 2, 3);
        BayesIm im = new MlBayesIm(pm, MlBayesIm.RANDOM);
        DataSet data = im.simulateData(numCases, false);

//        out.println("Finishing simulation");

        Fgs ges = new Fgs(data);
        ges.setVerbose(false);
        ges.setNumPatternsToStore(0);
        ges.setFaithfulnessAssumed(false);
//        ges.setDepth(3);

        ges.setStructurePrior(structurePrior);
        ges.setSamplePrior(samplePrior);

        Graph estPattern = ges.search();

        final Graph truePattern = SearchGraphUtils.patternForDag(dag);

//        printDegreeDistribution(estPattern, out);

        int[][] counts = SearchGraphUtils.graphComparison(estPattern, truePattern, null);

//        System.out.println(MatrixUtils.toString(counts));

        int[][] expectedCounts = {
                {2, 0, 0, 0, 0, 0},
                {0, 0, 0, 0, 0, 0},
                {0, 0, 0, 0, 0, 0},
                {0, 0, 0, 0, 0, 0},
                {0, 0, 0, 8, 0, 0},
                {0, 0, 0, 0, 0, 0},
                {0, 0, 0, 0, 0, 0},
                {0, 0, 0, 1, 0, 0},
        };

//        System.out.println(RandomUtil.getInstance().getSeed());

        for (int i = 0; i < counts.length; i++) {
            assertTrue(Arrays.equals(counts[i], expectedCounts[i]));
        }

//        System.out.println(MatrixUtils.toString(counts));
//        System.out.println(MatrixUtils.toString(expectedCounts));
    }

    @Test
    public void testExplore3() {
        RandomUtil.getInstance().setSeed(1450452162212L);
        Graph graph = GraphConverter.convert("A-->B,A-->C,B-->D,C-->D");
        SemPm pm = new SemPm(graph);
        SemIm im = new SemIm(pm);
        DataSet data = im.simulateData(1000, false);
        Fgs fgs = new Fgs(data);
        fgs.setPenaltyDiscount(2);
        Graph pattern = fgs.search();
//        System.out.println(RandomUtil.getInstance().getSeed());
        assertEquals(SearchGraphUtils.patternForDag(graph), pattern);
    }

    @Test
    public void testExplore4() {
        RandomUtil.getInstance().setSeed(1450536154213L);
        Graph graph = GraphConverter.convert("A-->B,A-->C,A-->D,B-->E,C-->E,D-->E");
        SemPm pm = new SemPm(graph);
        SemIm im = new SemIm(pm);
        DataSet data = im.simulateData(1000, false);
        Fgs fgs = new Fgs(data);
//        fgs.setFaithfulnessAssumed(false);
        fgs.setPenaltyDiscount(2);
        Graph pattern = fgs.search();
        assertEquals(SearchGraphUtils.patternForDag(graph), pattern);
    }

    @Test
    public void testExplore5() {
        RandomUtil.getInstance().setSeed(1450536192774L);
        Graph graph = GraphConverter.convert("A-->B,A-->C,A-->D,A->E,B-->F,C-->F,D-->F,E-->F");
        SemPm pm = new SemPm(graph);
        SemIm im = new SemIm(pm);
        DataSet data = im.simulateData(1000, false);
        Fgs fgs = new Fgs(data);
        fgs.setFaithfulnessAssumed(false);
        fgs.setPenaltyDiscount(2);
        Graph pattern = fgs.search();
        assertEquals(SearchGraphUtils.patternForDag(graph), pattern);
//        System.out.println(RandomUtil.getInstance().getSeed());
    }

    @Test
    public void testExplore6() {
//        RandomUtil.getInstance().setSeed(1450536192774L);
        Graph graph = GraphConverter.convert("A-->B,A-->C,A-->D,A->E,B-->F,C-->F,D-->F,E-->F");
//        Graph graph = GraphConverter.convert("A-->B,A-->C,B-->D,C-->D");

        int count = 0;

        for (int i = 0; i < 100; i++) {
            SemPm pm = new SemPm(graph);
            SemIm im = new SemIm(pm);
            DataSet data = im.simulateData(1000, false);
            Fgs fgs = new Fgs(data);
            fgs.setFaithfulnessAssumed(false);
            fgs.setPenaltyDiscount(1);
            Graph pattern = fgs.search();

//            int shd = SearchGraphUtils.structuralHammingDistance(SearchGraphUtils.patternForDag(graph), pattern);
//
//            System.out.println(shd);
//            if (shd < 6) {
//                count++;
//            }
//
            if (SearchGraphUtils.patternForDag(graph).equals(pattern)) {
                count++;
            }
        }

//        System.out.println(count);
    }

    @Test
    public void testFromGraph() {
        for (int i = 0; i < 1; i++) {
            Graph dag = GraphUtils.randomDag(6, 0, 6, 10, 10, 10, false);
            Fgs fgs = new Fgs(new GraphScore(dag));
            Graph pattern1 = fgs.search();
            Pc pc = new Pc(new IndTestDSep(dag));
            Graph pattern2 = pc.search();
            assertEquals(pattern2, pattern1);
        }
    }

    private void printDegreeDistribution(Graph dag, PrintStream out) {
        int max = 0;

        for (Node node : dag.getNodes()) {
            int degree = dag.getAdjacentNodes(node).size();
            if (degree > max) max = degree;
        }

        int[] counts = new int[max + 1];
        Map<Integer, List<Node>> names = new HashMap<>();

        for (int i = 0; i <= max; i++) {
            names.put(i, new ArrayList<Node>());
        }

        for (Node node : dag.getNodes()) {
            int degree = dag.getAdjacentNodes(node).size();
            counts[degree]++;
            names.get(degree).add(node);
        }

        for (int k = 0; k < counts.length; k++) {
            if (counts[k] == 0) continue;

            out.print(k + " " + counts[k]);

            for (Node node : names.get(k)) {
                out.print(" " + node.getName());
            }

            out.println();
        }
    }
}





