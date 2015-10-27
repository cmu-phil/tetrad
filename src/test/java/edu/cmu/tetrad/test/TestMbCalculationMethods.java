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

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Dag;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.regression.Regression;
import edu.cmu.tetrad.regression.RegressionDataset;
import edu.cmu.tetrad.regression.RegressionResult;
import edu.cmu.tetrad.search.IndTestFisherZ;
import edu.cmu.tetrad.search.IndTestFisherZGeneralizedInverse;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.search.Mbfs;
import edu.cmu.tetrad.sem.LargeSemSimulator;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.NumberFormatUtil;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Compares MBF to Regression on the task of calculating a Markov blanket from continuous data.
 *
 * @author Joseph Ramsey
 */
public class TestMbCalculationMethods extends TestCase {
    public TestMbCalculationMethods(String name) {
        super(name);
    }

    public void testNothing() {

    }

    // Comparison of MBF with regression.

    public void rtest1() {
        NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();
        NumberFormat nf2 = new DecimalFormat("     0");
        double alphaRegr = 0.05;
        double alphaMbf = 0.05;
        int numIterations = 25;
        int numVars = 100;
        int numEdges = 100;
        int sampleSize = 250;
        boolean latentDataSaved = false;
        int depth = 2;

        System.out.println(
                "Comparison of MBF to Multiple Linear Regression on the " +
                        "\nTask of Calculating the Markov Blanket for a Data Set Simulated " +
                        "\nfrom a Randomly Generated Structural Equation Model.");
        System.out.println();
        System.out.println("Alpha for MBF = " + alphaMbf);
        System.out.println("Alpha for Regression = " + alphaRegr);
        System.out.println("Number of iterations = " + numIterations);
        System.out.println("Number of variables = " + numVars);
        System.out.println("Number of randomly selected edges = " + numEdges);
        System.out.println(
                "Sample size of randomly simulated data  = " + sampleSize);
        System.out.println("Depth for MBF = " + depth);
        System.out.println();
        System.out.println();

        System.out.println(" FP(MB)\t FN(MB)\t  FP(R)\t  FN(R)\t  Truth");
//        System.out.println(" FP(MB)\t FN(MB)\t Truth");

        int r1Sum = 0, r2Sum = 0, r5Sum = 0;
        int i = 0;

        do {
            Dag randomGraph = new Dag(GraphUtils.randomGraph(numVars, 0, numEdges, 40,
                    40, 40, false));

            Node t = randomGraph.getNodes().get(0);
            Graph trueMbDag = GraphUtils.markovBlanketDag(t, randomGraph);

            List<Node> nodes2 = trueMbDag.getNodes();
            List<String> truth = extractVarNames(nodes2, t);

            SemPm semPm1 = new SemPm(randomGraph);
            SemIm semIm1 = new SemIm(semPm1);

            DataSet dataSet = semIm1.simulateData(sampleSize, latentDataSaved);
            double[][] data = dataSet.getDoubleData().transpose().toArray();
            double[][] regressors = new double[data.length - 1][];
            System.arraycopy(data, 1, regressors, 0, regressors.length);

            List<String> allNames = dataSet.getVariableNames();
            String[] names = new String[allNames.size() - 1];
            List<Node> _regressors = new ArrayList<Node>();

            for (int i1 = 1; i1 < allNames.size(); i1++) {
                names[i1 - 1] = allNames.get(i1);
                _regressors.add(dataSet.getVariable(names[i1 - 1]));
            }

            Node _target = dataSet.getVariable("Target");

            Regression regression = new RegressionDataset(dataSet);
//            regression.setRegressors(regressors);
//            regression.setRegressorNames(names);
//            RegressionResult result = regression.regress(target, "Target");
            RegressionResult result = regression.regress(_target, _regressors);



            List<String> regressorNames = new ArrayList<String>();

            for (int i1 = 0; i1 < result.getNumRegressors(); i1++) {
                if (result.getP()[i1] < alphaRegr) {
                    regressorNames.add(result.getRegressorNames()[i1]);
                }
            }

            regressorNames.remove("const");
            Collections.sort(regressorNames);

            IndependenceTest test = new IndTestFisherZ(dataSet, alphaMbf);
            Mbfs mbSearch = new Mbfs(test, depth);
            Graph mbDag1 = mbSearch.search(t.getName());

//            MbFanSearch mbSearch2 = new MbFanSearch(test, depth);
//            Graph mbDag2 = mbSearch2.search("X001");

//            System.out.println("True: " + trueMbDag);
//            System.out.println("Estimated 1: " + mbDag1);
//            System.out.println("Estimated 2: " + mbDag2);

            List<Node> nodes = mbDag1.getNodes();
            List<String> mbf = extractVarNames(nodes, t);

            // Calculate intersection(mbf, truth).
            List<String> mbfAndTruth = new ArrayList<String>(mbf);
            mbfAndTruth.retainAll(truth);

            // Calculate intersection(regressorNames, truth).
            List<String> regrAndTruth = new ArrayList<String>(regressorNames);
            regrAndTruth.retainAll(truth);

            // Calculate MB false positives.
            List<String> mbfFp = new ArrayList<String>(mbf);
            mbfFp.removeAll(mbfAndTruth);
            int r1 = mbfFp.size();
            r1Sum += r1;

            // Calculate MB false negatives.
            List<String> mbfFn = new ArrayList<String>(truth);
            mbfFn.removeAll(mbfAndTruth);
            int r2 = mbfFn.size();
            r2Sum += r2;

            // Calculate regression false positives.
            List<String> regrFp = new ArrayList<String>(regressorNames);
            regrFp.removeAll(regrAndTruth);
            int r3 = regrFp.size();

            // Calculate regression false negatives.
            List<String> regrFn = new ArrayList<String>(truth);
            regrFn.removeAll(regrAndTruth);
            int r4 = regrFn.size();

            // Sum up truths.
            int r5 = truth.size();
            r5Sum += r5;

            System.out.println(nf2.format(r1) + "\t" + nf2.format(r2) + "\t" +
                    nf2.format(r3) + "\t" + nf2.format(r4) + "\t" +
                    nf2.format(r5));

            ++i;
        } while (i < numIterations);

        double s1 = r1Sum / (double) numIterations;
        double s2 = r2Sum / (double) numIterations;
//        double s3 = r3Sum / (double) numIterations;
//        double s4 = r4Sum / (double) numIterations;
        double s5 = r5Sum / (double) numIterations;

        System.out.println("\nAverages:");
        System.out.println(nf.format(s1) + "\t" + nf.format(s2) + "\t" +
                /*nf.format(s3) + "\t" + nf.format(s4) + "\t" +*/
                nf.format(s5));
    }

    /**
     * Test 1 without regression.
     */
    public void rtest2() {
        NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();
        NumberFormat nf2 = new DecimalFormat("     0");
        double alphaMbf = 0.01;
        int numIterations = 25;
        int numVars = 1000;
        int numEdges = 1000;
        int sampleSize = 1000;
        boolean latentDataSaved = false;
        int depth = 2;

        System.out.println("Alpha for MBF = " + alphaMbf);
        System.out.println("Number of iterations = " + numIterations);
        System.out.println("Number of variables = " + numVars);
        System.out.println("Number of randomly selected edges = " + numEdges);
        System.out.println(
                "Sample size of randomly simulated data  = " + sampleSize);
        System.out.println("Depth for MBF = " + depth);
        System.out.println();
        System.out.println();

        System.out.println(" FP(MB)\t FN(MB)\t Truth");

        int r1Sum = 0, r2Sum = 0, r5Sum = 0;
        int i = 0;

        do {
            Dag randomGraph = new Dag(GraphUtils.randomGraph(numVars, 0, numEdges, 40,
                    40, 40, false));

            Node t = randomGraph.getNodes().get(0);
            Graph trueMbDag = GraphUtils.markovBlanketDag(t, randomGraph);

            List<Node> nodes2 = trueMbDag.getNodes();
            List<String> truth = extractVarNames(nodes2, t);

            SemPm semPm1 = new SemPm(randomGraph);
            SemIm semIm1 = new SemIm(semPm1);

            DataSet dataSet = semIm1.simulateData(sampleSize, latentDataSaved);

            IndependenceTest test = new IndTestFisherZ(dataSet, alphaMbf);
            Mbfs mbSearch = new Mbfs(test, depth);
            Graph mbDag1 = mbSearch.search(t.getName());

            List<Node> nodes = mbDag1.getNodes();
            List<String> mbf = extractVarNames(nodes, t);

            // Calculate intersection(mbf, truth).
            List<String> mbfAndTruth = new ArrayList<String>(mbf);
            mbfAndTruth.retainAll(truth);

            // Calculate MB false positives.
            List<String> mbfFp = new ArrayList<String>(mbf);
            mbfFp.removeAll(mbfAndTruth);
            int r1 = mbfFp.size();
            r1Sum += r1;

            // Calculate MB false negatives.
            List<String> mbfFn = new ArrayList<String>(truth);
            mbfFn.removeAll(mbfAndTruth);
            int r2 = mbfFn.size();
            r2Sum += r2;

            // Sum up truths.
            int r5 = truth.size();
            r5Sum += r5;

            System.out.println(nf2.format(r1) + "\t" + nf2.format(r2) + "\t" +
                    nf2.format(r5));

            ++i;
        } while (i < numIterations);

        double s1 = r1Sum / (double) numIterations;
        double s2 = r2Sum / (double) numIterations;
        double s5 = r5Sum / (double) numIterations;

        System.out.println("\nAverages:");
        System.out.println(
                nf.format(s1) + "\t" + nf.format(s2) + "\t" + nf.format(s5));
    }

    /**
     * MBF over ranges of alpha values.
     */
    public void rtest3() {
//        double alphaMbf = 0.001;
        int numIterations = 25;
        int numVars = 300;
        int numEdges = 300;
        int sampleSize = 500;
        boolean latentDataSaved = false;
        int depth = 3;

//        System.out.println("Alpha for MBF = " + alphaMbf);
        System.out.println("Number of iterations = " + numIterations);
        System.out.println("Number of variables = " + numVars);
        System.out.println("Number of randomly selected edges = " + numEdges);
        System.out.println(
                "Sample size of randomly simulated data  = " + sampleSize);
        System.out.println("Depth for MBF = " + depth);
        System.out.println();
        System.out.println();

        System.out.println(" FP(MB)\t FN(MB)\t Truth");

        int i = 0;

        do {
            Dag randomGraph = new Dag(GraphUtils.randomGraph(numVars, 0, numEdges, 40,
                    40, 40, false));

            Node target = randomGraph.getNodes().get(0);
            Graph trueMbDag = GraphUtils.markovBlanketDag(target, randomGraph);

            List<Node> nodes2 = trueMbDag.getNodes();
            List<String> truth = extractVarNames(nodes2, target);

            SemPm semPm1 = new SemPm(randomGraph);
            SemIm semIm1 = new SemIm(semPm1);

            DataSet dataSet = semIm1.simulateData(sampleSize, latentDataSaved);

            System.out.println("\n\nTruth = " + truth);
            NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();

            for (int j = 1; j <= 20; j++) {
                double _alpha = 0.001 * j;

                IndependenceTest test = new IndTestFisherZ(dataSet, _alpha);
                Mbfs mbSearch = new Mbfs(test, depth);
                Graph mbDag1 = mbSearch.search(target.getName());

                List<Node> estimated = mbDag1.getNodes();
                estimated.remove(dataSet.getVariable(target.getName()));
                System.out.println(nf.format(_alpha) + ": " + estimated);

//                for (int k = 1; k < estimated.size(); k++) {
//                    Variable variable = (Variable) estimated.get(k);
//                    Graph mbDag2 = mbSearch.search(variable.getName());
//                    System.out.println("\t" + variable + ": " + mbDag2.getNodes());
//                }
            }

            ++i;
        } while (i < numIterations);
    }

    /**
     * MBF for all variables in a data set, with markups on output to indicate FN, etc.
     */
    public void rtest4() {
        NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();
        NumberFormat nf2 = new DecimalFormat("     0");
        double alphaMbf = 0.01;
        int numVars = 2000;
        int numEdges = 2000;
        int sampleSize = 500;
        int depth = 3;

        System.out.println("Alpha for MBF = " + alphaMbf);
        System.out.println("Number of variables = " + numVars);
        System.out.println("Number of randomly selected edges = " + numEdges);
        System.out.println(
                "Sample size of randomly simulated data  = " + sampleSize);
        System.out.println("Depth for MBF = " + depth);
        System.out.println();
        System.out.println();

        int r1Sum = 0, r2Sum = 0, r5Sum = 0;

        System.out.println("... creating random DAG");
        Dag randomGraph = new Dag(GraphUtils.randomGraph(numVars, 0, numEdges, 40,
                40, 40, false));

//        System.out.println("... creating SEM PM");
//        SemPm semPm1 = new SemPm(randomGraph);

//        System.out.println("... creating SEM IM");
//        SemIm semIm1 = new SemIm(semPm1);

        System.out.println("... creating simulator");
        LargeSemSimulator simulator = new LargeSemSimulator(randomGraph);

        System.out.println("... simulating data");
//        RectangularDataSet dataSet = semIm1.simulateData(sampleSize);
        DataSet dataSet = simulator.simulateDataAcyclic(sampleSize);

        IndependenceTest test = new IndTestFisherZGeneralizedInverse(dataSet, alphaMbf);
        Mbfs mbSearch = new Mbfs(test, depth);

        System.out.println("\t FP(MB)\t FN(MB)\t Truth\tFound half?");

        List<Node> graphNodes = randomGraph.getNodes();

        for (int i = 0; i < graphNodes.size(); i++) {
            Node target = graphNodes.get(i);
            Graph trueMbDag = GraphUtils.markovBlanketDag(target, randomGraph);

            List<Node> nodes2 = trueMbDag.getNodes();
            List<String> truth = extractVarNames(nodes2, target);

//            if (truth.size() < 6) {
//                continue;
//            }

            Graph estimatedMbd = mbSearch.search(target.getName());
            List<Node> estimatedMb = estimatedMbd.getNodes();
//            Variable targetVariable = null;
//
//            for (Iterator j = estimatedMb.iterator(); j.hasNext(); ) {
//                Variable node = (Variable) j.next();
//                if (node.getName().equals(target.getName())) {
//                    targetVariable = node;
//                    j.remove();
//                }
//            }

            List<String> mbf = extractVarNames(estimatedMb, target);

            // Calculate intersection(mbf, truth).
            List<String> mbfAndTruth = new ArrayList<String>(mbf);
            mbfAndTruth.retainAll(truth);

            // Calculate MB false positives.
            List<String> mbfFp = new ArrayList<String>(mbf);
            mbfFp.removeAll(mbfAndTruth);
            int r1 = mbfFp.size();
            r1Sum += r1;

            // Calculate MB false negatives.
            List<String> mbfFn = new ArrayList<String>(truth);
            mbfFn.removeAll(mbfAndTruth);
            int r2 = mbfFn.size();
            r2Sum += r2;

//            List vnVars = new LinkedList();
//
//            for (int j = 0; j < mbfFn.size(); j++) {
//                String name = (String) mbfFn.get(j);
//                vnVars.add(test.getVariable(name));
//            }
//
//            MbFanSearch4 specialSearch = new MbFanSearch4(test, depth);
//            specialSearch.search(target.getName(), vnVars);

            // Sum up truths.
            int r5 = truth.size();
            r5Sum += r5;

//            List bidirectedParents = getBidirectedParents(estimatedMbd, targetVariable);

            double proportionCorrect = (r5 - r2) / (double) r5;
            boolean property =
                    (r2 == 0.0 && r5 == 0.0) || proportionCorrect >= 0.5;

            System.out.println((i + 1) + ".\t" + nf2.format(r1) + "\t" +
                    nf2.format(r2) + "\t" + nf2.format(r5) + "\t" +
                    (property ? "*" : " ") + "\t(" +
                    nf.format(mbSearch.getElapsedTime()) + " seconds)");
//            System.out.println("\t\t" + target.toString());
//            System.out.println("\t\t" +
//                    markup(estimatedMb, mbfFp, mbfFn, bidirectedParents));
//            System.out.println("\t\t(" +
//                    markup(truth, mbfFp, mbfFn, bidirectedParents) + ")");
        }

        double s1 = r1Sum / (double) graphNodes.size();
        double s2 = r2Sum / (double) graphNodes.size();
        double s5 = r5Sum / (double) graphNodes.size();

        System.out.println("\nAverages:");
        System.out.println(
                nf.format(s1) + "\t" + nf.format(s2) + "\t" + nf.format(s5));
    }

    private List<String> extractVarNames(List<Node> nodes, Node target) {
        List<String> varNames = new ArrayList<String>();

        for (Node node : nodes) {
            varNames.add(node.getName());
        }

        varNames.remove(target.getName());
        Collections.sort(varNames);
        return varNames;
    }

    /**
     * This method uses reflection to collect up all of the test methods from this class and return them to the test
     * runner.
     */
    public static Test suite() {

        // Edit the name of the class in the parens to match the name
        // of this class.
        return new TestSuite(TestMbCalculationMethods.class);
    }
}




