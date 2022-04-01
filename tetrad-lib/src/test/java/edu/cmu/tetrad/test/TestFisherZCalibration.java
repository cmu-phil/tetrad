package edu.cmu.tetrad.test;

import edu.cmu.tetrad.algcomparison.independence.FisherZ;
import edu.cmu.tetrad.algcomparison.independence.SemBicTest;
import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.IndTestDSep;
import edu.cmu.tetrad.search.IndTestFisherZ;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.sem.StandardizedSemIm;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.cmu.tetrad.util.RandomUtil;
import edu.cmu.tetrad.util.TextTable;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TestFisherZCalibration {

    @Test
    public void test1() {
        RandomUtil.getInstance().setSeed(105034020L);
        toTest();
    }

    private void toTest() {
        Parameters parameters = new Parameters();
        parameters.set(Params.ALPHA, 0.05);
        parameters.set(Params.DEPTH, 2);
        parameters.set(Params.PENALTY_DISCOUNT, 1);
        parameters.set(Params.STRUCTURE_PRIOR, 0);
        parameters.set(Params.COEF_LOW, .2);
        parameters.set(Params.COEF_HIGH, .7);
        final int numDraws = 2000;
        final int sampleSize = 2000;

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
            testOneDepth(parameters, test1, test2, variables, dsep, depth);
        }
    }

    private void testOneDepth(Parameters parameters, IndependenceTest test1, IndependenceTest test2, List<Node> variables, IndependenceTest dsep, int depth) {
        int countSame = 0;
        int fn1 = 0;
        int fn2 = 0;
        int fp1 = 0;
        int fp2 = 0;
        int ds = 0;

        for (int i = 0; i < 2000; i++) {
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
        System.out.println("Same = " + countSame + " out of " + 2000);
        System.out.println();
        System.out.println(table);

        System.out.println();

        double alpha = parameters.getDouble(Params.ALPHA);
        System.out.println("alpha = " + alpha);
        double alphaHat = fp1 / (double) ds;
        System.out.println("alpha^ = " + alphaHat);

//        Assert.assertTrue(abs(alpha - alphaHat) < alpha);
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
