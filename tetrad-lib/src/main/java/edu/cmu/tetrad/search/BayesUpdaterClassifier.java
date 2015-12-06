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
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetrad.util.TetradSerializable;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

/**
 * This class contains a method classify which uses an instantiated Bayes net (BayesIm) provided in the constructor. For
 * each case (record) in  the DataSet it uses the values of all variables but the target variable to update the
 * distributions of all the variables.  It then computes an estimated value for the target variable by selecting the
 * value with the greatest probability in the updated distribution.  The method returns a crosstabulation table in the
 * form of a two-dimensional integer array in which coefs of observed versus estimated values of the target variable
 * are stored. </p> Note that the variables must be the same in the dataset and the Bayes net.
 *
 * @author Frank Wimberly based on a specification by Clark Glymour
 */
public final class BayesUpdaterClassifier
        implements DiscreteClassifier, TetradSerializable {
    static final long serialVersionUID = 23L;

    /**
     * The BayesIm instance used to create an updater.  Supplied as an argument
     * to the constructor.
     *
     * @serial
     */
    private BayesIm bayesIm;

    /**
     * The dataset to be classified.
     *
     * @serial
     */
    private DataSet testData;

    /**
     * The percentage of correct estimates of the target variable.  This will be
     * set to a meaningful value upon completion of the crossTabulate method.
     *
     * @serial
     */
    private double percentCorrect;

    /**
     * The target variable (inferred from its name).
     *
     * @serial
     */
    private DiscreteVariable targetVariable;

    /**
     * True iff a missing value case was found on the last run through the
     * data.
     *
     * @serial
     */
    private boolean missingValueCaseFound;

    /**
     * The cutoff used for binary classifications.
     *
     * @serial
     */
    private double binaryCutoff = 0.5;

    /*
     * The variables in the dataset to be classified.  These should be
     * the same variables as in the training dataset according to the
     * "equals" method of DiscreteVariable.
     * @serial
     */
    private List<Node> bayesImVars;

    /**
     * @serial
     */
    private int[] classifications;

    /**
     * @serial
     */
    private double[][] marginals;

    /**
     * @serial
     */
    private int numCases = -1;

    /**
     * @serial
     */
    private int totalUsableCases;

    //===========================CONSTRUCTORS==========================//

    /*
     * The constructor sets the values of the private member variables.
     */

    public BayesUpdaterClassifier(BayesIm bayesIm,
                                  DataSet testData) {
        if (bayesIm == null) {
            throw new IllegalArgumentException("BayesIm must not be null.");
        }

        if (testData == null) {
            throw new IllegalArgumentException("DataSet must not be null.");
        }

        this.bayesIm = bayesIm;
        this.testData = testData;
        this.percentCorrect = Double.NaN;
        this.bayesImVars = new LinkedList<Node>(bayesIm.getVariables());
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     */
    public static BayesUpdaterClassifier serializableInstance() {
        return new BayesUpdaterClassifier(MlBayesIm.serializableInstance(),
                DataUtils.discreteSerializableInstance());
    }

    //==========================PUBLIC METHODS========================//

    public void setTarget(String target, int targetCategory) {

        //Find the target variable using its name.
        DiscreteVariable targetVariable = null;

        for (int j = 0; j < getBayesImVars().size(); j++) {
            DiscreteVariable dv = (DiscreteVariable) getBayesImVars().get(j);

            if (dv.getName().equals(target)) {
                targetVariable = dv;
                break;
            }
        }

        if (targetVariable == null) {
            throw new IllegalArgumentException(
                    "Not an available target: " + target);
        }

        this.targetVariable = targetVariable;
    }

    /**
     * Computes and returns the crosstabulation of observed versus estimated
     * values of the target variable as described above.
     */
    public int[] classify() {
        if (targetVariable == null) {
            throw new NullPointerException("Target not set.");
        }

        //Create an updater for the instantiated Bayes net.
        BayesUpdater bayesUpdater = new RowSummingExactUpdater(getBayesIm());

        //Get the raw data from the dataset to be classified, the number
        //of variables and the number of cases.
        int nvars = getBayesImVars().size();
        int ncases = testData.getNumRows();

        int[] varIndices = new int[nvars];
        List<Node> dataVars = testData.getVariables();

        for (int i = 0; i < nvars; i++) {
            DiscreteVariable variable =
                    (DiscreteVariable) getBayesImVars().get(i);


            if (variable == targetVariable) {
                continue;
            }

            varIndices[i] = dataVars.indexOf(variable);

            if (varIndices[i] == -1) {
                throw new IllegalArgumentException(
                        "Can't find the (non-target) variable " + variable +
                                " in the data. Either it's not there, or else its " +
                                "categories are in a different order.");
            }
        }

        DataSet selectedData = testData.subsetColumns(varIndices);

        this.numCases = ncases;

        int[] estimatedValues = new int[ncases];
        int numTargetCategories = targetVariable.getNumCategories();
        double[][] probOfClassifiedValues =
                new double[numTargetCategories][ncases];
        Arrays.fill(estimatedValues, -1);

        //For each case in the dataset to be classified compute the estimated
        //value of the target variable and increment the appropriate element
        //of the crosstabulation array. Compute the estimated value of the
        // target variable by using the observed values of the other variables
        // and Bayesian updating.
        for (int i = 0; i < ncases; i++) {

            //Create an Evidence instance for the instantiated Bayes net
            //which will allow that updating.
            Evidence evidence = Evidence.tautology(getBayesIm());

            //Let the target variable range over all its values.
            int itarget = evidence.getNodeIndex(targetVariable.getName());
            evidence.getProposition().setVariable(itarget, true);

            this.missingValueCaseFound = false;

            //Restrict all other variables to their observed values in
            //this case.
            for (int j = 0; j < getBayesImVars().size(); j++) {
                if (j == getBayesImVars().indexOf(targetVariable)) {
                    continue;
                }

                int observedValue = selectedData.getInt(i, j);

                if (observedValue == DiscreteVariable.MISSING_VALUE) {
                    this.missingValueCaseFound = true;
                    continue;
                }

                String jName = getBayesImVars().get(j).getName();
                int jIndex = evidence.getNodeIndex(jName);
                evidence.getProposition().setCategory(jIndex, observedValue);
            }

            //Update using those values.
            bayesUpdater.setEvidence(evidence);

            //for each possible value of target compute its probability in
            //the updated Bayes net.  Select the value with the highest
            //probability as the estimated value.
            Node targetNode = getBayesIm().getNode(targetVariable.getName());
            int indexTargetBN = getBayesIm().getNodeIndex(targetNode);

            //Straw man values--to be replaced.
            int estimatedValue = -1;

//            if (numTargetCategories == 2) {
//                for (int j = 0; j < numTargetCategories; j++) {
//                    double marginal =
//                            bayesUpdater.getMarginal(indexTargetBN, j);
//                    probOfClassifiedValues[j][i] = marginal;
//                    probOfClassifiedValues[1 - j][i] = 1.0 - marginal;
//
//                    if (targetCategory == j) {
//                        if (marginal > binaryCutoff) {
//                            estimatedValue = j;
//                        } else {
//                            estimatedValue = 1 - j;
//                        }
//
//                        break;
//                    }
//                }
//            } else
            {
                double highestProb = -0.1;

                for (int j = 0; j < numTargetCategories; j++) {
                    double marginal =
                            bayesUpdater.getMarginal(indexTargetBN, j);
                    probOfClassifiedValues[j][i] = marginal;

                    if (marginal >= highestProb) {
                        highestProb = marginal;
                        estimatedValue = j;
                    }
                }
            }

            //Sometimes the marginal cannot be computed because certain
            //combinations of values of the variables do not occur in the
            //training dataset.  If that happens skip the case.
            if (estimatedValue < 0) {
                TetradLogger.getInstance().log("details", "Case " + i + " does not return valid marginal.");

                for (int m = 0; m < nvars; m++) {
                    //System.out.print(getBayesImVars()
                    //        .get(m).getName());
                    TetradLogger.getInstance().log("details", "  " + selectedData.getDouble(i, m));
                }

                estimatedValues[i] = DiscreteVariable.MISSING_VALUE;
                continue;
            }

            estimatedValues[i] = estimatedValue;
        }

        this.classifications = estimatedValues;
        this.marginals = probOfClassifiedValues;

        return estimatedValues;
    }

    /**
     * Computes the "confusion matrix" of coefs of the number of cases
     * associated with each combination of estimated and observed values in the
     * test dataset.  Each row, column i,j corresponds to the ith and jth
     * categories of the target variable.
     *
     * @return an int[][] array containing the coefs, or null if the target
     * variable is not in the test data.
     */
    public int[][] crossTabulation() {
        int[] estimatedValues = classify();

        // Retrieve the column for the test variable from the test data; these
        // will be the observed values.
        Node variable = testData.getVariable(targetVariable.getName());
        int varIndex = testData.getVariables().indexOf(variable);

        if (variable == null) {
            return null;
        }

        int ncases = testData.getNumRows();

        // Create a cross-tabulation table to store the coefs of observed
        // versus estimated occurrences of each value of the target variable.
        int nvalues = targetVariable.getNumCategories();
        int[][] crosstabs = new int[nvalues][nvalues];
        for (int i = 0; i < nvalues; i++) {
            for (int j = 0; j < nvalues; j++) {
                crosstabs[i][j] = 0;
            }
        }

        //Will count the number of cases where the target variable
        //is correctly classified.
        int numberCorrect = 0;
        int ntot = 0;

        for (int i = 0; i < ncases; i++) {
            int estimatedValue = estimatedValues[i];
            int observedValue = testData.getInt(i, varIndex);

            if (estimatedValue < 0) {
                continue;
            }
            if (observedValue < 0) {
                continue;
            }
            ntot++;
            crosstabs[observedValue][estimatedValue]++;
            if (observedValue == estimatedValue) {
                numberCorrect++;
            }
        }

        this.percentCorrect =
                100.0 * ((double) numberCorrect) / ((double) ncases);
        this.totalUsableCases = ntot;

        return crosstabs;
    }

    /**
     * @return the percentage of cases in which the target variable is correctly
     * classified.
     */
    public double getPercentCorrect() {
        if (Double.isNaN(percentCorrect)) {
            crossTabulation();
        }
        return percentCorrect;
    }

    /**
     * @return the DiscreteVariable which is the target variable.
     */
    public DiscreteVariable getTargetVariable() {
        return targetVariable;
    }

    public BayesIm getBayesIm() {
        return bayesIm;
    }

    public DataSet getTestData() {
        return testData;
    }

    public int[] getClassifications() {
        return classifications;
    }

    public double[][] getMarginals() {
        return marginals;
    }

    public int getNumCases() {
        return numCases;
    }

    public int getTotalUsableCases() {
        return totalUsableCases;
    }

    public boolean isMissingValueCaseFound() {
        return missingValueCaseFound;
    }

    public double getBinaryCutoff() {
        return binaryCutoff;
    }

    public void setBinaryCutoff(double binaryCutoff) {
        if (binaryCutoff < 0.0 || binaryCutoff > 1.0) {
            throw new IllegalArgumentException();
        }

        this.binaryCutoff = binaryCutoff;
    }

    public List<Node> getBayesImVars() {
        return bayesImVars;
    }

    /**
     * Adds semantic checks to the default deserialization method. This method
     * must have the standard signature for a readObject method, and the body of
     * the method must begin with "s.defaultReadObject();". Other than that, any
     * semantic checks can be specified and do not need to stay the same from
     * version to version. A readObject method of this form may be added to any
     * class, even if Tetrad sessions were previously saved out using a version
     * of the class that didn't include it. (That's what the
     * "s.defaultReadObject();" is for. See J. Bloch, Effective Java, for help.
     *
     * @throws java.io.IOException
     * @throws ClassNotFoundException
     */
    private void readObject(ObjectInputStream s)
            throws IOException, ClassNotFoundException {
        s.defaultReadObject();

        if (bayesIm == null) {
            throw new NullPointerException();
        }

        if (testData == null) {
            throw new NullPointerException();
        }

        if (getBayesImVars() == null) {
            //Assumes all implementations of BayesIM create a non-null variables list.
            throw new NullPointerException();
        }

        if (binaryCutoff < 0.0 || binaryCutoff > 1.0) {
            throw new IllegalStateException();
        }
    }
}



