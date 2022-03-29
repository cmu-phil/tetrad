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

package edu.cmu.tetrad.bayes;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

/**
 * Estimates maximum likelihood probabilities directly from data on the fly.
 *
 * @author Joseph Ramsey
 */
public final class DataSetProbs implements DiscreteProbs {
    static final long serialVersionUID = 23L;

    /**
     * The data set that this is a cell count table for.
     *
     * @serial
     */
    private final DataSet dataSet;

    /**
     * An array whose length is the number of dimensions of the cell and whose
     * contents, for each value dims[i], are the numbers of values for each
     * i'th dimension. Each of these dimensions must be an integer greater than
     * zero.
     *
     * @serial
     */
    private final int[] dims;

    /**
     * The number of rows in the data.
     *
     * @serial
     */
    private final int numRows;

    /**
     * True iff a missing value case was found on the last run through the
     * data.
     *
     * @serial
     */
    private boolean missingValueCaseFound;

    //============================CONSTRUCTORS===========================//

    /**
     * Creates a cell count table for the given data set.
     */
    public DataSetProbs(final DataSet dataSet) {
        if (dataSet == null) {
            throw new NullPointerException();
        }

        this.dataSet = dataSet;
        this.dims = new int[dataSet.getNumColumns()];

        for (int i = 0; i < this.dims.length; i++) {
            final DiscreteVariable variable =
                    (DiscreteVariable) dataSet.getVariable(i);
            this.dims[i] = variable.getNumCategories();
        }

        this.numRows = dataSet.getNumRows();
    }

    //===========================PUBLIC METHODS=========================//

    /**
     * @return the estimated probability for the given cell. The order of the
     * variable values is the order of the variables in getVariable().
     */
    public double getCellProb(final int[] variableValues) {
        final int[] point = new int[this.dims.length];
        int count = 0;

        this.missingValueCaseFound = false;

        point:
        for (int i = 0; i < this.numRows; i++) {
            for (int j = 0; j < this.dims.length; j++) {
                point[j] = this.dataSet.getInt(i, j);

                if (point[j] == DiscreteVariable.MISSING_VALUE) {
                    this.missingValueCaseFound = true;
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
     * @return the estimated probability of the given proposition.
     */
    public double getProb(final Proposition assertion) {
        final int[] point = new int[this.dims.length];
        int count = 0;

        this.missingValueCaseFound = false;

        point:
        for (int i = 0; i < this.numRows; i++) {
            for (int j = 0; j < this.dims.length; j++) {
                point[j] = this.dataSet.getInt(i, j);

                if (point[j] == DiscreteVariable.MISSING_VALUE) {
                    this.missingValueCaseFound = true;
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
     * @return the estimated conditional probability for the given assertion
     * conditional on the given condition.
     */
    public double getConditionalProb(final Proposition assertion,
                                     final Proposition condition) {
        if (assertion.getVariableSource() != condition.getVariableSource()) {
            throw new IllegalArgumentException(
                    "Assertion and condition must be " +
                            "for the same Bayes IM.");
        }

        List<Node> assertionVars = assertion.getVariableSource().getVariables();
        final List<Node> dataVars = this.dataSet.getVariables();

        assertionVars = GraphUtils.replaceNodes(assertionVars, dataVars);

        if (!new HashSet<>(assertionVars).equals(
                new HashSet<>(dataVars))) {
            throw new IllegalArgumentException(
                    "Assertion variable and data variables" +
                            " are either different or in a different order: " +
                            "\n\tAssertion vars: " + assertionVars +
                            "\n\tData vars: " + dataVars);
        }

        final int[] point = new int[this.dims.length];
        int count1 = 0;
        int count2 = 0;
        this.missingValueCaseFound = false;

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

        return count2 / (double) count1;
    }

    /**
     * @return the dataset that this is estimating probabilities for.
     */
    public DataSet getDataSet() {
        return this.dataSet;
    }

    /**
     * @return the list of variables for the dataset that this is estimating
     * probabilities for.
     */
    public List<Node> getVariables() {
        return null;
    }

}





