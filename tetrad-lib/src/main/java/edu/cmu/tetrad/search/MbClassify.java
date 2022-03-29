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

package edu.cmu.tetrad.search;

import edu.cmu.tetrad.bayes.*;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataUtils;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.Edges;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.NumberFormatUtil;
import edu.cmu.tetrad.util.TetradLogger;
import edu.pitt.dbmi.data.reader.Delimiter;

import java.io.File;
import java.io.IOException;
import java.text.NumberFormat;
import java.util.Arrays;
import java.util.List;

/**
 * Performs a Bayesian classification of a test set based on a given training set. MBFS is used to select a Markov
 * blanket DAG of the target; this DAG is used to estimate a Bayes model using the training data. The Bayes model is
 * then updated for each case in the test data to produce classifications.
 *
 * @author Frank Wimberly
 * @author Joseph Ramsey
 */
public class MbClassify implements DiscreteClassifier {
    private DataSet train;
    private DataSet test;
    private String target;
    private double alpha;
    private int depth;
    private double prior;
    private int maxMissing;

    private DiscreteVariable targetVariable;
    private double percentCorrect;
    private int[][] crossTabulation;

    //============================CONSTRUCTOR===========================//

    /**
     * Constructs a new MbClassify, passing parameters in.
     *
     * @param train      The training data. Should be discrete.
     * @param test       The test data. Should be discrete and should contain all of the relevant variables from the
     *                   training data.
     * @param target     The name of the target variable. Should be in the trainin data.
     * @param alpha      The significance level for MBFS.
     * @param depth      The depth for MBFS.
     * @param prior      The symmetric alpha for the Dirichlet prior for Dirichlet estimation.
     * @param maxMissing The maximum number of missing values allowed. Cases with more than this number of missing
     *                   values among the variables in the DAG found by MBFS will be skipped.
     */
    public MbClassify(final DataSet train, final DataSet test,
                      final String target, final double alpha, final int depth, final double prior, final int maxMissing) {
        setup(train, test, target, alpha, depth, prior, maxMissing);
    }

    public MbClassify(final String trainPath, final String testPath, final String targetString,
                      final String alphaString, final String depthString, final String priorString, final String maxMissingString) {
        try {
            final StringBuilder buf = new StringBuilder();
            buf.append("MbClassify ");
            buf.append(trainPath).append(" ");
            buf.append(testPath).append(" ");
            buf.append(targetString).append(" ");
            buf.append(alphaString).append(" ");
            buf.append(depthString).append(" ");
            buf.append(priorString).append(" ");
            buf.append(maxMissingString).append(" ");

            TetradLogger.getInstance().log("info", buf.toString());

            final DataSet train = DataUtils.loadContinuousData(new File(trainPath), "//", '\"' ,
                    "*", true, Delimiter.TAB);
            final DataSet test = DataUtils.loadContinuousData(new File(testPath), "//", '\"' ,
                    "*", true, Delimiter.TAB);

            final double alpha = Double.parseDouble(alphaString);
            final int depth = Integer.parseInt(depthString);
            final double prior = Double.parseDouble(priorString);
            final int maxMissing = Integer.parseInt(maxMissingString);

            setup(train, test, targetString, alpha, depth, prior, maxMissing);
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void setup(final DataSet train, final DataSet test, final String target, final double alpha,
                       final int depth, final double prior, final int maxMissing) {
        this.train = train;
        this.test = test;
        this.alpha = alpha;
        this.target = target;
        this.depth = depth;
        this.prior = prior;
        this.maxMissing = maxMissing;

        this.targetVariable = (DiscreteVariable) train.getVariable(target);

        if (this.targetVariable == null) {
            throw new IllegalArgumentException("Target variable not in data: " +
                    target);
        }
    }

    //============================PUBLIC METHODS=========================//

    /**
     * Classifies the test data by Bayesian updating. The procedure is as follows. First, MBFS is run on the training
     * data to estimate an MB CPDAG. Bidirected edges are removed; an MB DAG G is selected from the CPDAG that
     * remains. Second, a Bayes model B is estimated using this G and the training data. Third, for each case in the
     * test data, the marginal for the target variable in B is calculated conditioning on values of the other varialbes
     * in B in the test data; these are reported as classifications. Estimation of B is done using a Dirichlet
     * estimator, with a symmetric prior, with the given alpha value. Updating is done using a row-summing exact
     * updater.
     * <p>
     * One consequence of using the row-summing exact updater is that classification will be fast except for cases in
     * which there are lots of missing values. The reason for this is that for such cases the number of rows that need
     * to be summed over will be exponential in the number of missing values for that case. Hence the parameter for max
     * num missing values. A good default for this is like 5. Any test case with more than that number of missing values
     * will be skipped.
     *
     * @return The classifications.
     */
    public int[] classify() {
        final IndependenceTest indTest = new IndTestChiSquare(this.train, this.alpha);

        final Mbfs search = new Mbfs(indTest, this.depth);
        search.setDepth(this.depth);
//        Hiton search = new Hiton(indTest, depth);
//        Mmmb search = new Mmmb(indTest, depth);
        final List<Node> mbPlusTarget = search.findMb(this.target);
        mbPlusTarget.add(this.train.getVariable(this.target));

        final DataSet subset = this.train.subsetColumns(mbPlusTarget);

        System.out.println("subset vars = " + subset.getVariables());

        final Pc cpdagSearch = new Pc(new IndTestChiSquare(subset, 0.05));
//        cpdagSearch.setMaxIndegree(depth);
        final Graph mbCPDAG = cpdagSearch.search();

//        MbFanSearch search = new MbFanSearch(indTest, depth);
//        Graph mbCPDAG = search.search(target);

        TetradLogger.getInstance().log("details", "CPDAG = " + mbCPDAG);
        MbUtils.trimToMbNodes(mbCPDAG, this.train.getVariable(this.target), true);
        TetradLogger.getInstance().log("details", "Trimmed CPDAG = " + mbCPDAG);

        // Removing bidirected edges from the CPDAG before selecting a DAG.                                   4
        for (final Edge edge : mbCPDAG.getEdges()) {
            if (Edges.isBidirectedEdge(edge)) {
                mbCPDAG.removeEdge(edge);
            }
        }

        final Graph selectedDag = MbUtils.getOneMbDag(mbCPDAG);

        TetradLogger.getInstance().log("details", "Selected DAG = " + selectedDag);
        TetradLogger.getInstance().log("details", "Vars = " + selectedDag.getNodes());
        TetradLogger.getInstance().log("details", "\nClassification using selected MB DAG:");

        final NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();
        final List<Node> mbNodes = selectedDag.getNodes();

        //The Markov blanket nodes will correspond to a subset of the variables
        //in the training dataset.  Find the subset dataset.
        final DataSet trainDataSubset = this.train.subsetColumns(mbNodes);

        //To create a Bayes net for the Markov blanket we need the DAG.
        final BayesPm bayesPm = new BayesPm(selectedDag);

        //To parameterize the Bayes net we need the number of values
        //of each variable.
        final List varsTrain = trainDataSubset.getVariables();

        for (int i1 = 0; i1 < varsTrain.size(); i1++) {
            final DiscreteVariable trainingVar = (DiscreteVariable) varsTrain.get(i1);
            bayesPm.setCategories(mbNodes.get(i1), trainingVar.getCategories());
        }

        //Create an updater for the instantiated Bayes net.
        TetradLogger.getInstance().log("info", "Estimating Bayes net; please wait...");
        final DirichletBayesIm prior = DirichletBayesIm.symmetricDirichletIm(bayesPm,
                this.prior);
        final BayesIm bayesIm = DirichletEstimator.estimate(prior, trainDataSubset);

        final RowSummingExactUpdater updater = new RowSummingExactUpdater(bayesIm);

        //The subset dataset of the dataset to be classified containing
        //the variables in the Markov blanket.
        final DataSet testSubset = this.test.subsetColumns(mbNodes);

        //Get the raw data from the dataset to be classified, the number
        //of variables, and the number of cases.
        final int numCases = testSubset.getNumRows();
        final int[] estimatedCategories = new int[numCases];
        Arrays.fill(estimatedCategories, -1);

        //The variables in the dataset.
        final List<Node> varsClassify = testSubset.getVariables();

        //For each case in the dataset to be classified compute the estimated
        //value of the target variable and increment the appropriate element
        //of the crosstabulation array.
        for (int k = 0; k < numCases; k++) {

            //Create an Evidence instance for the instantiated Bayes net
            //which will allow that updating.
            final Proposition proposition = Proposition.tautology(bayesIm);

            //Restrict all other variables to their observed values in
            //this case.
            int numMissing = 0;

            for (int testIndex = 0; testIndex < varsClassify.size(); testIndex++) {
                final DiscreteVariable var = (DiscreteVariable) varsClassify.get(testIndex);

                // If it's the target, ignore it.
                if (var.equals(this.targetVariable)) {
                    continue;
                }

                final int trainIndex = proposition.getNodeIndex(var.getName());

                // If it's not in the train subset, ignore it.
                if (trainIndex == -99) {
                    continue;
                }

                final int testValue = testSubset.getInt(k, testIndex);

                if (testValue == -99) {
                    numMissing++;
                } else {
                    proposition.setCategory(trainIndex, testValue);
                }
            }

            if (numMissing > this.maxMissing) {
                TetradLogger.getInstance().log("details", "classification(" + k + ") = " +
                        "not done since number of missing values too high " +
                        "(" + numMissing + ").");
                continue;
            }

            final Evidence evidence = Evidence.tautology(bayesIm);
            evidence.getProposition().restrictToProposition(proposition);
            updater.setEvidence(evidence);

            // for each possible value of target compute its probability in
            // the updated Bayes net.  Select the value with the highest
            // probability as the estimated getValue.
            final int targetIndex = proposition.getNodeIndex(this.targetVariable.getName());

            //Straw man values--to be replaced.
            double highestProb = -0.1;
            int _category = -1;

            for (int category = 0;
                 category < this.targetVariable.getNumCategories(); category++) {
                final double marginal = updater.getMarginal(targetIndex, category);

                if (marginal > highestProb) {
                    highestProb = marginal;
                    _category = category;
                }
            }

            //Sometimes the marginal cannot be computed because certain
            //combinations of values of the variables do not occur in the
            //training dataset.  If that happens skip the case.
            if (_category < 0) {
                System.out.println("classification(" + k + ") is undefined " +
                        "(undefined marginals).");
                continue;
            }

            final String estimatedCategory = this.targetVariable.getCategories().get(_category);
            TetradLogger.getInstance().log("details", "classification(" + k + ") = " + estimatedCategory);

            estimatedCategories[k] = _category;
        }

        //Create a crosstabulation table to store the coefs of observed
        //versus estimated occurrences of each value of the target variable.
        final int targetIndex = varsClassify.indexOf(this.targetVariable);
        final int numCategories = this.targetVariable.getNumCategories();
        final int[][] crossTabs = new int[numCategories][numCategories];

        //Will count the number of cases where the target variable
        //is correctly classified.
        int numberCorrect = 0;
        int numberCounted = 0;

        for (int k = 0; k < numCases; k++) {
            final int estimatedCategory = estimatedCategories[k];
            final int observedValue = testSubset.getInt(k, targetIndex);

//            if (observedValue < 0) {
//                continue;
//            }

            if (estimatedCategory < 0) {
                continue;
            }

            crossTabs[observedValue][estimatedCategory]++;
            numberCounted++;

            if (observedValue == estimatedCategory) {
                numberCorrect++;
            }
        }

        final double percentCorrect1 =
                100.0 * ((double) numberCorrect) / ((double) numberCounted);

        // Print the cross classification.
        TetradLogger.getInstance().log("details", "");
        TetradLogger.getInstance().log("details", "\t\t\tEstimated\t");
        TetradLogger.getInstance().log("details", "Observed\t");

        final StringBuilder buf0 = new StringBuilder();
        buf0.append("\t");

        for (int m = 0; m < numCategories; m++) {
            buf0.append(this.targetVariable.getCategory(m)).append("\t");
        }

        TetradLogger.getInstance().log("details", buf0.toString());

        for (int k = 0; k < numCategories; k++) {
            final StringBuilder buf = new StringBuilder();

            buf.append(this.targetVariable.getCategory(k)).append("\t");

            for (int m = 0; m < numCategories; m++)
                buf.append(crossTabs[k][m]).append("\t");

            TetradLogger.getInstance().log("details", buf.toString());
        }

        TetradLogger.getInstance().log("details", "");
        TetradLogger.getInstance().log("details", "Number correct = " + numberCorrect);
        TetradLogger.getInstance().log("details", "Number counted = " + numberCounted);
        TetradLogger.getInstance().log("details", "Percent correct = " + nf.format(percentCorrect1) + "%");

        this.crossTabulation = crossTabs;
        this.percentCorrect = percentCorrect1;

        return estimatedCategories;
    }

    /**
     * @return the cross-tabulation from the classify method. The classify method must be run first.
     */
    public int[][] crossTabulation() {
        return this.crossTabulation;
    }

    /**
     * @return the percent correct from the classify method. The classify method must be run first.
     */
    public double getPercentCorrect() {
        return this.percentCorrect;
    }

    /**
     * Runs MbClassify using moves-line arguments. The syntax is:
     * <pre>
     * java MbClassify train.dat test.dat target alpha depth
     * </pre>
     *
     * @param args train.dat test.dat alpha depth dirichlet_prior max_missing
     */
    public static void main(final String[] args) {
        final String trainPath = args[0];
        final String testPath = args[1];
        final String targetString = args[2];
        final String alphaString = args[3];
        final String depthString = args[4];
        final String priorString = args[5];
        final String maxMissingString = args[6];

        new MbClassify(trainPath, testPath, targetString, alphaString, depthString,
                priorString, maxMissingString);
    }
}





