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

package edu.cmu.tetrad.classify;

import edu.cmu.tetrad.bayes.*;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.data.SimpleDataLoader;
import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.Edges;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.search.Pc;
import edu.cmu.tetrad.search.PcMb;
import edu.cmu.tetrad.search.test.IndTestChiSquare;
import edu.cmu.tetrad.search.utils.MbUtils;
import edu.cmu.tetrad.util.NumberFormatUtil;
import edu.cmu.tetrad.util.TetradLogger;
import edu.pitt.dbmi.data.reader.Delimiter;

import java.io.File;
import java.io.IOException;
import java.text.NumberFormat;
import java.util.*;

/**
 * Performs a Bayesian classification of a test set based on a given training set. PC-MB is used to select a Markov
 * blanket DAG of the target; this DAG is used to estimate a Bayes model using the training data. The Bayes model is
 * then updated for each case in the test data to produce classifications.
 *
 * @author Frank Wimberly
 * @author josephramsey
 * @version $Id: $Id
 */
public class ClassifierMbDiscrete implements ClassifierDiscrete {

    /**
     * Train data.
     */
    private DataSet train;

    /**
     * Test data.
     */
    private DataSet test;

    /**
     * Target variable.
     */
    private Node target;
    private double alpha;

    /**
     * Depth for PC-MB search.
     */
    private int depth;

    /**
     * Prior for Dirichlet estimator.
     */
    private double prior;

    /**
     * Maximum number of missing values for a test case.
     */
    private int maxMissing;

    /**
     * Target variable.
     */
    private DiscreteVariable targetVariable;

    /**
     * Percent correct.
     */
    private double percentCorrect;

    /**
     * Cross-tabulation.
     */
    private int[][] crossTabulation;

    //============================CONSTRUCTOR===========================//

    /**
     * Constructs a new ClassifierMbDiscrete object using the given training and test data, target variable, alpha
     * value,
     *
     * @param trainPath        the path to the training data file
     * @param testPath         the path to the test data file
     * @param targetString     the name of the target variable
     * @param alphaString      the alpha value for the Dirichlet estimator
     * @param depthString      the depth for the PC-MB search
     * @param priorString      the prior for the Dirichlet estimator
     * @param maxMissingString the maximum number of missing values for a test case
     */
    public ClassifierMbDiscrete(String trainPath, String testPath, String targetString,
                                String alphaString, String depthString, String priorString, String maxMissingString) {
        try {
            String s = "MbClassify " +
                       trainPath + " " +
                       testPath + " " +
                       targetString + " " +
                       alphaString + " " +
                       depthString + " " +
                       priorString + " " +
                       maxMissingString + " ";

            TetradLogger.getInstance().forceLogMessage(s);

            DataSet train = SimpleDataLoader.loadContinuousData(new File(trainPath), "//", '\"',
                    "*", true, Delimiter.TAB, false);
            DataSet test = SimpleDataLoader.loadContinuousData(new File(testPath), "//", '\"',
                    "*", true, Delimiter.TAB, false);

            double alpha = Double.parseDouble(alphaString);
            int depth = Integer.parseInt(depthString);
            double prior = Double.parseDouble(priorString);
            int maxMissing = Integer.parseInt(maxMissingString);

            setup(train, test, target, alpha, depth, prior, maxMissing);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Runs MbClassify using moves-line arguments. The syntax is:
     * <pre>
     * java MbClassify train.dat test.dat target alpha depth
     * </pre>
     *
     * @param args train.dat test.dat alpha depth dirichlet_prior max_missing
     */
    public static void main(String[] args) {
        String trainPath = args[0];
        String testPath = args[1];
        String targetString = args[2];
        String alphaString = args[3];
        String depthString = args[4];
        String priorString = args[5];
        String maxMissingString = args[6];

        new ClassifierMbDiscrete(trainPath, testPath, targetString, alphaString, depthString,
                priorString, maxMissingString);
    }

    //============================PUBLIC METHODS=========================//

    private void setup(DataSet train, DataSet test, Node target, double alpha,
                       int depth, double prior, int maxMissing) {
        this.train = train;
        this.test = test;
        this.alpha = alpha;
        this.target = target;
        this.depth = depth;
        this.prior = prior;
        this.maxMissing = maxMissing;

        this.targetVariable = (DiscreteVariable) target;

        if (this.targetVariable == null) {
            throw new IllegalArgumentException("Target variable not in data: " +
                                               target);
        }
    }

    /**
     * Classifies the test data by Bayesian updating. The procedure is as follows. First, PC-MB is run on the training
     * data to estimate an MB CPDAG. Bidirected edges are removed; an MB DAG G is selected from the CPDAG that remains.
     * Second, a Bayes model B is estimated using this G and the training data. Third, for each case in the test data,
     * the marginal for the target variable in B is calculated conditioning on values of the other varialbes in B in the
     * test data; these are reported as classifications. Estimation of B is done using a Dirichlet estimator, with a
     * symmetric prior, with the given alpha value. Updating is done using a row-summing exact updater.
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
        IndependenceTest indTest = new IndTestChiSquare(this.train, this.alpha);

        PcMb search = new PcMb(indTest, this.depth);
        search.setDepth(this.depth);
        Set<Node> mbPlusTarget = search.findMb(this.target);
        mbPlusTarget.add(this.target);

        ArrayList<Node> vars = new ArrayList<>(mbPlusTarget);
        Collections.sort(vars);
        DataSet subset = this.train.subsetColumns(vars);

        System.out.println("subset vars = " + subset.getVariables());

        Pc cpdagSearch = new Pc(new IndTestChiSquare(subset, 0.05));
        Graph mbCPDAG = cpdagSearch.search();

        TetradLogger.getInstance().forceLogMessage("CPDAG = " + mbCPDAG);
        MbUtils.trimToMbNodes(mbCPDAG, this.target, true);
        TetradLogger.getInstance().forceLogMessage("Trimmed CPDAG = " + mbCPDAG);

        // Removing bidirected edges from the CPDAG before selecting a DAG.                                   4
        for (Edge edge : mbCPDAG.getEdges()) {
            if (Edges.isBidirectedEdge(edge)) {
                mbCPDAG.removeEdge(edge);
            }
        }

        Graph selectedDag = MbUtils.getOneMbDag(mbCPDAG);

        TetradLogger.getInstance().forceLogMessage("Selected DAG = " + selectedDag);
        String message1 = "Vars = " + selectedDag.getNodes();
        TetradLogger.getInstance().forceLogMessage(message1);
        TetradLogger.getInstance().forceLogMessage("\nClassification using selected MB DAG:");

        NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();
        List<Node> mbNodes = selectedDag.getNodes();

        //The Markov blanket nodes will correspond to a subset of the variables
        //in the training dataset.  Find the subset dataset.
        DataSet trainDataSubset = this.train.subsetColumns(mbNodes);

        //To create a Bayes net for the Markov blanket we need the DAG.
        BayesPm bayesPm = new BayesPm(selectedDag);

        //To parameterize the Bayes net we need the number of values
        //of each variable.
        List<Node> varsTrain = trainDataSubset.getVariables();

        for (int i1 = 0; i1 < varsTrain.size(); i1++) {
            DiscreteVariable trainingVar = (DiscreteVariable) varsTrain.get(i1);
            bayesPm.setCategories(mbNodes.get(i1), trainingVar.getCategories());
        }

        //Create an updater for the instantiated Bayes net.
        TetradLogger.getInstance().forceLogMessage("Estimating Bayes net; please wait...");
        DirichletBayesIm prior = DirichletBayesIm.symmetricDirichletIm(bayesPm,
                this.prior);
        BayesIm bayesIm = DirichletEstimator.estimate(prior, trainDataSubset);

        RowSummingExactUpdater updater = new RowSummingExactUpdater(bayesIm);

        //The subset dataset of the dataset to be classified containing
        //the variables in the Markov blanket.
        DataSet testSubset = this.test.subsetColumns(mbNodes);

        //Get the raw data from the dataset to be classified, the number
        //of variables, and the number of cases.
        int numCases = testSubset.getNumRows();
        int[] estimatedCategories = new int[numCases];
        Arrays.fill(estimatedCategories, -1);

        //The variables in the dataset.
        List<Node> varsClassify = testSubset.getVariables();

        //For each case in the dataset to be classified compute the estimated
        //value of the target variable and increment the appropriate element
        //of the crosstabulation array.
        for (int k = 0; k < numCases; k++) {

            //Create an Evidence instance for the instantiated Bayes net
            //which will allow that updating.
            Proposition proposition = Proposition.tautology(bayesIm);

            //Restrict all other variables to their observed values in
            //this case.
            int numMissing = 0;

            for (int testIndex = 0; testIndex < varsClassify.size(); testIndex++) {
                DiscreteVariable var = (DiscreteVariable) varsClassify.get(testIndex);

                // If it's the target, ignore it.
                if (var.equals(this.targetVariable)) {
                    continue;
                }

                int trainIndex = proposition.getNodeIndex(var.getName());

                // If it's not in the train subset, ignore it.
                if (trainIndex == -99) {
                    continue;
                }

                int testValue = testSubset.getInt(k, testIndex);

                if (testValue == -99) {
                    numMissing++;
                } else {
                    proposition.setCategory(trainIndex, testValue);
                }
            }

            if (numMissing > this.maxMissing) {
                TetradLogger.getInstance().forceLogMessage("classification(" + k + ") = " +
                                                           "not done since number of missing values too high " +
                                                           "(" + numMissing + ").");
                continue;
            }

            Evidence evidence = Evidence.tautology(bayesIm);
            evidence.getProposition().restrictToProposition(proposition);
            updater.setEvidence(evidence);

            // for each possible value of target compute its probability in
            // the updated Bayes net.  Select the value with the highest
            // probability as the estimated getValue.
            int targetIndex = proposition.getNodeIndex(this.targetVariable.getName());

            //Straw man values--to be replaced.
            double highestProb = -0.1;
            int _category = -1;

            for (int category = 0;
                 category < this.targetVariable.getNumCategories(); category++) {
                double marginal = updater.getMarginal(targetIndex, category);

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

            String estimatedCategory = this.targetVariable.getCategories().get(_category);
            TetradLogger.getInstance().forceLogMessage("classification(" + k + ") = " + estimatedCategory);

            estimatedCategories[k] = _category;
        }

        //Create a crosstabulation table to store the coefs of observed
        //versus estimated occurrences of each value of the target variable.
        int targetIndex = varsClassify.indexOf(this.targetVariable);
        int numCategories = this.targetVariable.getNumCategories();
        int[][] crossTabs = new int[numCategories][numCategories];

        //Will count the number of cases where the target variable
        //is correctly classified.
        int numberCorrect = 0;
        int numberCounted = 0;

        for (int k = 0; k < numCases; k++) {
            int estimatedCategory = estimatedCategories[k];
            int observedValue = testSubset.getInt(k, targetIndex);

            if (estimatedCategory < 0) {
                continue;
            }

            crossTabs[observedValue][estimatedCategory]++;
            numberCounted++;

            if (observedValue == estimatedCategory) {
                numberCorrect++;
            }
        }

        double percentCorrect1 =
                100.0 * ((double) numberCorrect) / ((double) numberCounted);

        // Print the cross classification.
        TetradLogger.getInstance().forceLogMessage("");
        TetradLogger.getInstance().forceLogMessage("\t\t\tEstimated\t");
        TetradLogger.getInstance().forceLogMessage("Observed\t");

        StringBuilder buf0 = new StringBuilder();
        buf0.append("\t");

        for (int m = 0; m < numCategories; m++) {
            buf0.append(this.targetVariable.getCategory(m)).append("\t");
        }

        TetradLogger.getInstance().forceLogMessage(buf0.toString());

        for (int k = 0; k < numCategories; k++) {
            StringBuilder buf = new StringBuilder();

            buf.append(this.targetVariable.getCategory(k)).append("\t");

            for (int m = 0; m < numCategories; m++)
                buf.append(crossTabs[k][m]).append("\t");

            TetradLogger.getInstance().forceLogMessage(buf.toString());
        }

        TetradLogger.getInstance().forceLogMessage("");
        TetradLogger.getInstance().forceLogMessage("Number correct = " + numberCorrect);
        TetradLogger.getInstance().forceLogMessage("Number counted = " + numberCounted);
        String message = "Percent correct = " + nf.format(percentCorrect1) + "%";
        TetradLogger.getInstance().forceLogMessage(message);

        this.crossTabulation = crossTabs;
        this.percentCorrect = percentCorrect1;

        return estimatedCategories;
    }

    /**
     * <p>crossTabulation.</p>
     *
     * @return the cross-tabulation from the classify method. The classify method must be run first.
     */
    public int[][] crossTabulation() {
        return this.crossTabulation;
    }

    /**
     * <p>Getter for the field <code>percentCorrect</code>.</p>
     *
     * @return the percent correct from the classify method. The classify method must be run first.
     */
    public double getPercentCorrect() {
        return this.percentCorrect;
    }
}





