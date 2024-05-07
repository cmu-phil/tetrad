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
 * Estimates probabilities directly from data on the fly using maximum likelihood method, with the exception that if
 * rows do not exist in the data satisfying a required condition because certain values are unattested, an attempt is
 * made to remove each relevant column in turn, record the estimated probability with column removed from the condition
 * (if it is defined), and then return the average over the estimated probabilities calculated this way.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public final class IntAveDataSetProbs implements DiscreteProbs {

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

    //============================CONSTRUCTORS===========================//

    /**
     * Creates a cell count table for the given data set.
     *
     * @param dataSet a {@link edu.cmu.tetrad.data.DataSet} object
     */
    public IntAveDataSetProbs(DataSet dataSet) {
        if (dataSet == null) {
            throw new NullPointerException();
        }

        this.dataSet = dataSet;
        this.dims = new int[dataSet.getNumColumns()];

        for (int i = 0; i < this.dims.length; i++) {
            DiscreteVariable variable =
                    (DiscreteVariable) dataSet.getVariable(i);
            this.dims[i] = variable.getNumCategories();
        }

//        untrimmedData = dataSet.getIntMatrixTransposed();
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

        point:
        for (int i = 0; i < this.numRows; i++) {
            for (int j = 0; j < this.dims[i]; j++) {
                point[j] = this.dataSet.getInt(i, j);

                if (point[j] == DiscreteVariable.MISSING_VALUE) {
                    continue point;
                }
            }

            if (Arrays.equals(point, variableValues)) {
                count++;
            }
        }

        return count / (double) this.numRows;
    }

    /**
     * Calculates the probability of a given assertion.
     *
     * @param assertion an object of type Proposition representing the assertion
     * @return the probability of the given assertion
     */
    public double getProb(Proposition assertion) {
        int[] point = new int[this.dims.length];
        int count = 0;

        point:
        for (int i = 0; i < this.numRows; i++) {
            for (int j = 0; j < this.dims.length; j++) {
                point[j] = this.dataSet.getInt(i, j);

                if (point[j] == DiscreteVariable.MISSING_VALUE) {
                    continue point;
                }
            }

            if (assertion.isPermissibleCombination(point)) {
                count++;
            }
        }

        return count / (double) this.numRows;
    }

    /**
     * Calculates the conditional probability of an assertion given a condition.
     *
     * @param assertion The proposition representing the assertion.
     * @param condition The proposition representing the condition.
     * @return The conditional probability of the assertion given the condition.
     * @throws IllegalArgumentException If the assertion and condition are not for the same Bayes IM or if the variables
     *                                  in the assertion and data set are different or in a different order.
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
        int count1 = 0;
        int count2 = 0;

        point:
        for (int i = 0; i < this.numRows; i++) {
            for (int j = 0; j < this.dims.length; j++) {
                point[j] = this.dataSet.getInt(i, j);

                if (point[j] == DiscreteVariable.MISSING_VALUE) {
                    continue point;
                }
            }

            if (condition.isPermissibleCombination(point)) {
                count1++;

                if (assertion.isPermissibleCombination(point)) {
                    count2++;
                }
            }
        }

        double p = count2 / (double) count1;

        if (!Double.isNaN(p)) {
            return p;
        }

        // Otherwise try removing each variable in turn from the conditioning
        // set; take the average over the defined probabilities of the assertion
        // given these revised conditions.
        int numDefinedProbs = 0;
        double sumProbs = 0.0;

        for (int i = 0; i < this.dims.length; i++) {
            if (condition.isConditioned(i)) {
                Proposition condition2 = new Proposition(condition);
                condition.setVariable(i, true);
                count1 = 0;
                count2 = 0;

                point:
                for (int i1 = 0; i1 < this.numRows; i1++) {
                    for (int j = 0; j < this.dims.length; j++) {
                        point[j] = this.dataSet.getInt(i, j);

                        if (point[j] == DiscreteVariable.MISSING_VALUE) {
                            continue point;
                        }
                    }

                    if (condition2.isPermissibleCombination(point)) {
                        count1++;

                        if (assertion.isPermissibleCombination(point)) {
                            count2++;
                        }
                    }
                }

                p = count2 / (double) count1;

                if (!Double.isNaN(p)) {
                    numDefinedProbs++;
                    sumProbs += p;
                }
            }
        }

        return sumProbs / numDefinedProbs;
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

}





