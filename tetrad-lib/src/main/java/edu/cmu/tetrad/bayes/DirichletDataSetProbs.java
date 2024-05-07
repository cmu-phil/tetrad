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

package edu.cmu.tetrad.bayes;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.graph.Node;

import java.util.Arrays;
import java.util.List;

/**
 * Estimates probabilities directly from data on the fly using maximum likelihood method.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public final class DirichletDataSetProbs implements DiscreteProbs {

    /**
     * The data set that this is a cell count table for.
     */
    private final DataSet dataSet;

    /**
     * An array whose length is the number of dimensions of the cell and whose contents, for each value dims[i], are the
     * numbers of values for each i'th dimension. Each of these dimensions must be an integer greater than zero.
     */
    private final int[] dims;
    /**
     * The number of rows in the data.
     */
    private final int numRows;
    /**
     * The value to add to each cell count to avoid zero counts.
     */
    private final double symmValue;
    /**
     * Indicates whether bounds on coordinate values are explicitly enforced. This may slow down loops.
     */
    private boolean boundsEnforced = true;

    //============================CONSTRUCTORS===========================//

    /**
     * Creates a cell count table for the given data set.
     *
     * @param dataSet   a {@link edu.cmu.tetrad.data.DataSet} object
     * @param symmValue a double
     */
    public DirichletDataSetProbs(DataSet dataSet, double symmValue) {
        if (dataSet == null) {
            throw new NullPointerException();
        }

        if (symmValue < 0.0) {
            throw new IllegalArgumentException();
        }

        this.dataSet = dataSet;
        this.symmValue = symmValue;
        this.dims = new int[dataSet.getNumColumns()];

        for (int i = 0; i < this.dims.length; i++) {
            DiscreteVariable variable =
                    (DiscreteVariable) dataSet.getVariable(i);
            this.dims[i] = variable.getNumCategories();
        }

        this.numRows = dataSet.getNumRows();
    }

    //===========================PUBLIC METHODS=========================//

    /**
     * <p>getCellProb.</p>
     *
     * @param variableValues an array of {@link int} objects
     * @return the estimated probability for the given cell. The order of the variable values is the order of the
     * variables in getVariable().
     */
    public double getCellProb(int[] variableValues) {
        int[] point = new int[this.dims.length];
        int count = 0;

        for (int i = 0; i < this.numRows; i++) {
            for (int j = 0; j < this.dims[i]; j++) {
                point[j] = this.dataSet.getInt(i, j);
            }

            if (Arrays.equals(point, variableValues)) {
                count++;
            }
        }

        return count / (double) this.numRows;
    }

    /**
     * Retrieves the probability of the given assertion in the DirichletDataSetProbs.
     *
     * @param assertion The proposition to be checked for probability.
     * @return The probability of the assertion as a double value.
     */
    public double getProb(Proposition assertion) {
        int[] point = new int[this.dims.length];
        int count = 0;

        for (int i = 0; i < this.numRows; i++) {
            for (int j = 0; j < this.dims[i]; j++) {
                point[j] = this.dataSet.getInt(i, j);
            }

            if (assertion.isPermissibleCombination(point)) {
                count++;
            }
        }

        return count / (double) this.numRows;
    }

    /**
     * Calculates the conditional probability of an assertion given a condition in the context of a Bayes IM.
     *
     * @param assertion the proposition representing the assertion
     * @param condition the proposition representing the condition
     * @return the conditional probability as a double value
     * @throws IllegalArgumentException if the assertion and condition are not for the same Bayes IM or if the assertion
     *                                  variable and data variables are different or in a different order
     */
    public double getConditionalProb(Proposition assertion,
                                     Proposition condition) {
        if (assertion.getVariableSource() != condition.getVariableSource()) {
            throw new IllegalArgumentException(
                    "Assertion and condition must be " +
                    "for the same Bayes IM.");
        }

        List<Node> assertionVars = assertion.getVariableSource().getVariables();
        List<Node> dataVars = this.dataSet.getVariables();

        if (!assertionVars.equals(dataVars)) {
            throw new IllegalArgumentException(
                    "Assertion variable and data variables" +
                    " are either different or in a different order: " +
                    "\n\tAssertion vars: " + assertionVars +
                    "\n\tData vars: " + dataVars);
        }

        int[] point = new int[this.dims.length];

        double count1 = 1.0;
        double count2 = 1.0;

        for (int i = 0; i < this.dims.length; i++) {
            if (condition.isConditioned(i) || assertion.isConditioned(i)) {
                count2 *= condition.getNumAllowedCategories(i) * this.symmValue;

                if (assertion.isConditioned(i)) {
                    count1 *= assertion.getNumAllowedCategories(i) * this.symmValue;
                }
            }
        }

        for (int i = 0; i < this.numRows; i++) {
            for (int j = 0; j < this.dims.length; j++) {
                point[j] = this.dataSet.getInt(i, j);
            }

            if (condition.isPermissibleCombination(point)) {
                count1 += 1.0;

                if (assertion.isPermissibleCombination(point)) {
                    count2 += 1.0;
                }
            }
        }

        return count2 / count1;
    }

    /**
     * <p>Getter for the field <code>dataSet</code>.</p>
     *
     * @return the dataset that this is estimating probabilities for.
     */
    public DataSet getDataSet() {
        return this.dataSet;
    }

    /**
     * <p>getVariables.</p>
     *
     * @return the list of variables for the dataset that this is estimating probabilities for.
     */
    public List<Node> getVariables() {
        return null;
    }

    /**
     * True iff bounds checking is performed on variable values indices.
     *
     * @return a boolean
     */
    public boolean isBoundsEnforced() {
        return this.boundsEnforced;
    }

    /**
     * True iff bounds checking is performed on variable values indices.
     *
     * @param boundsEnforced a boolean
     */
    public void setBoundsEnforced(boolean boundsEnforced) {
        this.boundsEnforced = boundsEnforced;
    }
}





