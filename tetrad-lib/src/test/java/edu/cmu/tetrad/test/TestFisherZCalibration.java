package edu.cmu.tetrad.test;

import edu.cmu.tetrad.algcomparison.independence.FisherZ;
import edu.cmu.tetrad.algcomparison.independence.SemBicTest;
import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.*;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.sem.StandardizedSemIm;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.cmu.tetrad.util.RandomUtil;
import edu.cmu.tetrad.util.TextTable;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static java.lang.StrictMath.abs;
import static org.junit.Assert.assertTrue;

public class TestFisherZCalibration {

//    public static void main(String... args) {
//        test1();
//    }

    @Test
    public void test1() {
        RandomUtil.getInstance().setSeed(105034020L);
        toTest(0.05);
    }

    private void toTest(double alpha) {
        Parameters parameters = new Parameters();
        parameters.set(Params.ALPHA, alpha);
        parameters.set(Params.DEPTH, 2);
        parameters.set(Params.PENALTY_DISCOUNT, 1);
        parameters.set(Params.STRUCTURE_PRIOR, 0);
        parameters.set(Params.COEF_LOW, .2);
        parameters.set(Params.COEF_HIGH, .7);
        int numDraws = 2000;
        int sampleSize = 2000;

        Graph graph = GraphUtils.randomDag(20, 0, 40, 100,
                100, 100, false);
        SemPm pm = new SemPm(graph);
        SemIm im = new SemIm(pm);
        DataSet data = im.simulateData(sampleSize, false);


        IndependenceTest test1 = new FisherZ().getTest(data, parameters);
        IndependenceTest test2 = new SemBicTest().getTest(data, parameters);

        List<Node> variables = data.getVariables();
        graph = GraphUtils.replaceNodes(graph, variables);

        IndependenceTest dsep = new IndTestDSep(graph);

        for (int depth : new int[]{0, 1}) {
            testOneDepth(parameters, numDraws, test1, test2, variables, dsep, depth);
        }
    }

    private void testOneDepth(Parameters parameters, int numDraws, IndependenceTest test1, IndependenceTest test2, List<Node> variables, IndependenceTest dsep, int depth) {
        int countSame = 0;
        int fn1 = 0;
        int fn2 = 0;
        int fp1 = 0;
        int fp2 = 0;
        int ds = 0;

        for (int i = 0; i < numDraws; i++) {
            Collections.shuffle(variables);
            Collections.shuffle(variables);
            Collections.shuffle(variables);

            Node x = variables.get(0);
            Node y = variables.get(1);

            List<Node> z = new ArrayList<>();
            for (int j = 0; j < depth; j++) {
                z.add(variables.get(j + 2));
            }

            boolean fzInd = test1.isIndependent(x, y, z);
            boolean sembInd = test2.isIndependent(x, y, z);
            boolean _dsep = dsep.isIndependent(x, y, z);

            if (fzInd == sembInd) countSame++;

            if (fzInd && !_dsep) fn1++;
            if (!fzInd && _dsep) fp1++;
            if (sembInd && !_dsep) fn2++;
            if (!sembInd && _dsep) fp2++;
            if (_dsep) ds++;
        }

        TextTable table = new TextTable(3, 3);
        table.setToken(0, 1, "FP");
        table.setToken(0, 2, "FN");
        table.setToken(1, 0, "Fisher Z");
        table.setToken(2, 0, "Local Consistency Criterion");

        table.setToken(1, 1, "" + fp1);
        table.setToken(1, 2, "" + fn1);
        table.setToken(2, 1, "" + fp2);
        table.setToken(2, 2, "" + fn2);

        System.out.println();
        System.out.println("Depth = " + depth);
        System.out.println();
        System.out.println("Same = " + countSame + " out of " + numDraws);
        System.out.println();
        System.out.println(table);

        System.out.println();

        double alpha = parameters.getDouble(Params.ALPHA);
        System.out.println("alpha = " + alpha);
        double alphaHat = fp1 / (double) ds;
        System.out.println("alpha^ = " + alphaHat);

        Assert.assertTrue(abs(alpha - alphaHat) < 2 * alpha);
    }

    @Test
    public void test16() {

        Graph gStar = new EdgeListGraph();
        Node x1 = new GraphNode("X1");
        Node x2 = new GraphNode("X2");
        Node x3 = new GraphNode("X3");
        Node x4 = new GraphNode("X4");

        gStar.addNode(x1);
        gStar.addNode(x2);
        gStar.addNode(x3);
        gStar.addNode(x4);

        gStar.addDirectedEdge(x1, x2);
        gStar.addDirectedEdge(x2, x3);
        gStar.addDirectedEdge(x3, x4);
        gStar.addDirectedEdge(x1, x4);

        SemPm semPm = new SemPm(gStar);
        SemIm semIm = new SemIm(semPm);

        Parameters parameters = new Parameters();
        parameters.set(Params.SAMPLE_SIZE, 50000);

        StandardizedSemIm sem = new StandardizedSemIm(semIm, parameters);

        for (double d2 = -.6; d2 <= .6; d2 += 0.05) {
            for (double d3 = -.6; d3 <= .6; d3 += 0.05) {
                for (double d4 = -.6; d4 <= .6; d4 += 0.05) {
                    for (double d1 = -.6; d1 <= .6; d1 += 0.05) {
                        if ((d1 > -0.801 && d1 < -0.199) || (d1 > 0.199 && d1 < 0.801)) {
                            if ((d2 > -0.801 && d2 < -0.199) || (d2 > 0.199 && d2 < 0.801)) {
                                if ((d3 > -0.801 && d3 < -0.199) || (d3 > 0.199 && d3 < 0.801)) {
                                    if ((d4 > -0.801 && d4 < -0.199) || (d4 > 0.199 && d4 < 0.801)) {
                                        d1 = Math.round(d1 * 10000.00) / 10000.00;
                                        d2 = Math.round(d2 * 10000.00) / 10000.00;
                                        d3 = Math.round(d3 * 10000.00) / 10000.00;
                                        d4 = Math.round(d4 * 10000.00) / 10000.00;

//                                        System.out.println("d1 = " + d1 + " d2 = " + d2 + " d3 = " + d3 + " d4 = " + d4);

                                        try {
                                            assertTrue(sem.setEdgeCoefficient(x1, x2, d1));
                                            assertTrue(sem.setEdgeCoefficient(x2, x3, d2));
                                            assertTrue(sem.setEdgeCoefficient(x3, x4, d3));
                                            assertTrue(sem.setEdgeCoefficient(x1, x4, d4));

//                                            System.out.println(sem.getImplCovar());
//
                                            DataSet dataSet = sem.simulateDataReducedForm(parameters.getInt(Params.SAMPLE_SIZE),
                                                    false);

                                            CovarianceMatrix covarianceMatrix = new CovarianceMatrix(dataSet);
                                            IndependenceTest test = new IndTestFisherZ(covarianceMatrix, 0.001);

                                            Node _x1 = dataSet.getVariable("X1");
                                            Node _x2 = dataSet.getVariable("X2");
                                            Node _x3 = dataSet.getVariable("X3");
                                            Node _x4 = dataSet.getVariable("X4");

                                            boolean independent = test.isIndependent(_x1, _x2, Collections.singletonList(_x4));
                                            if (!independent) continue;

                                            Fges fges = new Fges(new SemBicScore(dataSet));

                                            Graph out = fges.search();

                                            if (out.getNumEdges() >= 4) {
                                                System.out.println("\nd1 = " + d1 + " d2 = " + d2 + " d3 = " + d3 + " d4 = " + d4);
                                                System.out.println(out);

                                                CovarianceMatrix cov = new CovarianceMatrix(dataSet.getVariables(), sem.getImplCovar(), sem.getSampleSize());
                                                System.out.println(cov);
                                            }
                                        } catch (AssertionError e) {
                                            System.out.println("Couldn't set parameters");
                                            continue;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }


    }

    @Test
    public void test17() {
        double score;

        double d1 = 0.5;
        double d2 = 0.5;
        double d3 = 0.5;
        double d4 = .5;

        Graph gStar = new EdgeListGraph();
        Node x1 = new GraphNode("X1");
        Node x2 = new GraphNode("X2");
        Node x3 = new GraphNode("X3");
        Node x4 = new GraphNode("X4");

        gStar.addNode(x1);
        gStar.addNode(x2);
        gStar.addNode(x3);
        gStar.addNode(x4);

        gStar.addDirectedEdge(x1, x2);
        gStar.addDirectedEdge(x2, x3);
        gStar.addDirectedEdge(x3, x4);
        gStar.addDirectedEdge(x1, x4);

        Parameters parameters = new Parameters();
        parameters.set(Params.SAMPLE_SIZE, 500000);

        int sampleSize = parameters.getInt(Params.SAMPLE_SIZE);

        StandardizedSemIm sem;
//        DataSet[] dataSet;

//        do {
            SemPm semPm = new SemPm(gStar);
            SemIm semIm = new SemIm(semPm);

            sem = new StandardizedSemIm(semIm, parameters);
            System.out.println("d1 = " + d1 + " d2 = " + d2 + " d3 = " + d3 + " d4 = " + d4);
//            dataSet = new DataSet[1];
//
//            score = tryThis(sem, sampleSize, x1, x2, x3, x4, d1, d2, d3, d4, dataSet, gStar, parameters);
//        } while (Double.isNaN(score));

        double delta = 0.1;
        score = 50;

        while (delta > 0.01) {

            double d12 = d1 + getRandom(delta);
            double d22 = d2 + getRandom(delta);
            double d32 = d2 + getRandom(delta);
            double d42 = d2 + getRandom(delta);

            try {
                assertTrue(sem.setEdgeCoefficient(x1, x2, d12));
                assertTrue(sem.setEdgeCoefficient(x2, x3, d22));
                assertTrue(sem.setEdgeCoefficient(x3, x4, d32));
                assertTrue(sem.setEdgeCoefficient(x1, x4, d42));
            } catch (AssertionError e) {
                continue;
            }

            DataSet dataSet = sem.simulateDataReducedForm(parameters.getInt(Params.SAMPLE_SIZE),
                    false);

            CovarianceMatrix covarianceMatrix = new CovarianceMatrix(dataSet);
            IndependenceTest test = new IndTestFisherZ(covarianceMatrix, 0.0001);

            Node _x1 = dataSet.getVariable("X1");
            Node _x2 = dataSet.getVariable("X2");
            Node _x3 = dataSet.getVariable("X3");
            Node _x4 = dataSet.getVariable("X4");

            boolean independent = test.isIndependent(_x1, _x2, Collections.singletonList(_x4));
//            if (!independent) continue;

            double _score = test.getScore();

            if (_score > score) continue;

            score = _score;

            Fges fges = new Fges(new SemBicScore(dataSet));

            Graph out = fges.search();

            if (out.getNumEdges() >= 4) {
                System.out.println("\nd1 = " + d1 + " d2 = " + d2 + " d3 = " + d3 + " d4 = " + d4
                        + " score = " + score);
                System.out.println(out);
            }


//            double p = tryThis(sem, sampleSize, x1, x2, x3, x4, d12, d22, d32, d42, dataSet, gStar, parameters);
//
//            if (Double.isNaN(p)) continue;
//            if (p > score) continue;
//
//            score = p;
//            DataSet dataset = dataSet[0];
//            d1 = d12;
//            d2 = d22;
//            d3 = d32;
//            d4 = d42;
//
            delta *= 0.95;

//            Fges fges = new Fges(new SemBicScore(dataset));
//
//            Graph out = fges.search();
//
//            if (out.getNumEdges() >= 4) {
//            System.out.println("d1 = " + d1 + " d2 = " + d2 + " d3 = " + d3 + " d4 = " + d4 + " score = " + score);
//            System.out.println(out);
//            }
        }
    }

    private double getRandom(double delta) {
        return 3 * (RandomUtil.getInstance().nextDouble() - 0.5) * delta;
    }

    private double tryThis(StandardizedSemIm sem3, int sampleSize, Node x1, Node x2, Node x3, Node x4,
                           double d1, double d2, double d3, double d4, DataSet[] _dataSet, Graph gStar,
                           Parameters parameters) {
        try {
            SemPm semPm = new SemPm(gStar);
            SemIm semIm = new SemIm(semPm);

            semIm.setEdgeCoef(x1, x2, d1);
            semIm.setEdgeCoef(x2, x3, d2);
            semIm.setEdgeCoef(x3, x4, d3);
            semIm.setEdgeCoef(x1, x4, d4);

            StandardizedSemIm sem = new StandardizedSemIm(semIm, parameters);



//            assertTrue(sem.setEdgeCoefficient(x1, x2, d1));
//            assertTrue(sem.setEdgeCoefficient(x2, x3, d2));
//            assertTrue(sem.setEdgeCoefficient(x3, x4, d3));
//            assertTrue(sem.setEdgeCoefficient(x1, x4, d4));
            DataSet dataSet = sem.simulateDataReducedForm(sampleSize,
                    false);

            _dataSet[0] = dataSet;

            CovarianceMatrix covarianceMatrix = new CovarianceMatrix(dataSet);
            IndependenceTest test = new IndTestFisherZ(covarianceMatrix, 0.0001);

            Node _x1 = dataSet.getVariable("X1");
            Node _x2 = dataSet.getVariable("X2");
            Node _x3 = dataSet.getVariable("X3");
            Node _x4 = dataSet.getVariable("X4");

            test.isIndependent(_x1, _x2, Collections.singletonList(_x4));
            return test.getScore();
        } catch (AssertionError e) {
            return Double.NaN;
        }
    }
}
