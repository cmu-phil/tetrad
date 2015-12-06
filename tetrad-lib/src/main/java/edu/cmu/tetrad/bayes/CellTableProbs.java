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
import edu.cmu.tetrad.graph.Node;

import java.util.List;

/**
 * Estimates probabilities from data by constructing the entire cell count table
 * for the data.
 *
 * @author Joseph Ramsey
 */
public final class CellTableProbs implements DiscreteProbs {

    /**
     * The data set that this is a cell count table for.
     */
    private DataSet dataSet;

    /**
     * An array whose length is the number of dimensions of the cell and whose
     * contents, for each value dims[i], are the numbers of values for each
     * i'th dimension. Each of these dimensions must be an integer greater than
     * zero.
     */
    private final int[] dims;

    /**
     * A single-dimension array containing all of the cells of the table. Must
     * be at least long enough to contain data for each cell allowed for by the
     * given dimension array--in other words, the length must be greater than or
     * equal to dims[0] & dims[1] ... * dims[dims.length - 1].
     */
    private final int[] cells;

    /**
     * The total number of points in the cell count table.
     */
    private int numPoints = 0;

    /**
     * Indicates whether bounds on coordinate values are explicitly enforced.
     * This may slow down loops.
     */
    private boolean boundsEnforced = true;

    /**
     * True iff a missing value case was found.
     */
    private boolean missingValueCaseFound;

    //============================CONSTRUCTORS===========================//

    /**
     * Creates a cell count table for the given data set.
     */
    public CellTableProbs(DataSet dataSet) {
        if (dataSet == null) {
            throw new NullPointerException("Data set not provided.");
        }

        this.dataSet = dataSet;
        dims = new int[dataSet.getNumColumns()];

        for (int i = 0; i < dims.length; i++) {
            DiscreteVariable variable =
                    (DiscreteVariable) dataSet.getVariable(i);
            dims[i] = variable.getNumCategories();
        }

        int size = 1;

        for (int dim : dims) {
            size *= dim;
        }

        this.cells = new int[size];

        int numRows = dataSet.getNumRows();

        int[] point = new int[dims.length];
        this.missingValueCaseFound = false;

        point:
        for (int i = 0; i < numRows; i++) {
            for (int j = 0; j < dims.length; j++) {
                point[j] = dataSet.getInt(i, j);

                if (point[j] == DiscreteVariable.MISSING_VALUE) {
                    this.missingValueCaseFound = true;
                    continue point;
                }
            }

            int cellIndex = getCellIndex(point);
            cells[cellIndex]++;
            numPoints++;
        }
    }

    //===========================PUBLIC METHODS=========================//

    /**
     * @return the estimated probability for the given cell. The order of the
     * variable values is the order of the variables in getVariable().
     */
    public double getCellProb(int[] variableValues) {
        int cellIndex = getCellIndex(variableValues);
        int cellCount = cells[cellIndex];
        return cellCount / (double) numPoints;
    }

    /**
     * @return the estimated probability of the given proposition.
     */
    public double getProb(Proposition assertion) {

        // Initialize to 0's.
        int[] variableValues = new int[assertion.getNumVariables()];

        for (int i = 0; i < assertion.getNumVariables(); i++) {
            variableValues[i] = nextValue(assertion, i, -1);
        }

        variableValues[variableValues.length - 1] = -1;
        double p = 0.0;

        loop:
        while (true) {
            for (int i = assertion.getNumVariables() - 1; i >= 0; i--) {
                if (hasNextValue(assertion, i, variableValues[i])) {
                    variableValues[i] =
                            nextValue(assertion, i, variableValues[i]);

                    for (int j = i + 1; j < assertion.getNumVariables(); j++) {
                        if (hasNextValue(assertion, j, -1)) {
                            variableValues[j] = nextValue(assertion, j, -1);
                        } else {
                            break loop;
                        }
                    }

                    p += getCellProb(variableValues);

                    continue loop;
                }
            }

            break;
        }

        return p;
    }

    /**
     * @return the estimated conditional probability for the given assertion
     * conditional on the given condition.
     */
    public double getConditionalProb(Proposition assertion,
                                     Proposition condition) {
        if (assertion.getVariableSource() != condition.getVariableSource()) {
            throw new IllegalArgumentException(
                    "Assertion and condition must be " +
                            "for the same Bayes IM.");
        }

        List<Node> assertionVars = assertion.getVariableSource().getVariables();
        List<Node> dataVars = dataSet.getVariables();

        if (!assertionVars.equals(dataVars)) {
            throw new IllegalArgumentException(
                    "Assertion variable and data variables" +
                            " are either different or in a different order: " +
                            "\n\tAssertion vars: " + assertionVars +
                            "\n\tData vars: " + dataVars);
        }

        int[] variableValues = new int[condition.getNumVariables()];

        for (int i = 0; i < condition.getNumVariables(); i++) {
            variableValues[i] = nextValue(condition, i, -1);
        }

        variableValues[variableValues.length - 1] = -1;
        double conditionTrue = 0.0;
        double assertionTrue = 0.0;

        loop:
        while (true) {
            for (int i = condition.getNumVariables() - 1; i >= 0; i--) {
                if (hasNextValue(condition, i, variableValues[i])) {
                    variableValues[i] =
                            nextValue(condition, i, variableValues[i]);

                    for (int j = i + 1; j < condition.getNumVariables(); j++) {
                        if (hasNextValue(condition, j, -1)) {
                            variableValues[j] = nextValue(condition, j, -1);
                        } else {
                            break loop;
                        }
                    }

                    // Variable values should be in the order of the data set.

                    double cellProb = getCellProb(variableValues);
                    boolean assertionHolds = true;

                    for (int j = 0; j < assertion.getNumVariables(); j++) {
                        if (!assertion.isAllowed(j, variableValues[j])) {
                            assertionHolds = false;
                            break;
                        }
                    }

                    if (assertionHolds) {
                        assertionTrue += cellProb;
                    }

                    conditionTrue += cellProb;
                    continue loop;
                }
            }

            break;
        }

        return assertionTrue / conditionTrue;
    }

    /**
     * @return the dataset that this is estimating probabilities for.
     */
    public DataSet getDataSet() {
        return dataSet;
    }

    /**
     * @return the list of variables for the dataset that this is estimating
     * probabilities for.
     */
    public List<Node> getVariables() {
        return null;
    }

    /**
     * True iff bounds checking is performed on variable values indices.
     */
    private boolean isBoundsEnforced() {
        return boundsEnforced;
    }

//    /**
//     * True iff bounds checking is performed on variable values indices.
//     */
//    public void setBoundsEnforced(boolean boundsEnforced) {
//        this.boundsEnforced = boundsEnforced;
//    }

    //===========================PRIVATE METHODS===========================//

    /**
     * @param coords The coordinates of the cell. Each value must be less
     *               than the number of possible value for the corresponding
     *               dimension in the table. (Enforced.)
     * @return the row in the table for the given node and combination of parent
     * values.
     */
    private int getCellIndex(int[] coords) {
        int cellIndex = 0;

        if (isBoundsEnforced()) {
            if (coords.length != dims.length) {
                throw new IllegalArgumentException(
                        "Coordinate array must have the proper number of dimensions.");
            }

            for (int i = 0; i < coords.length; i++) {
                if ((coords[i] < 0) || (coords[i] >= dims[i])) {
                    throw new IllegalArgumentException("Coordinate #" + i +
                            " for variable " + dataSet.getVariable(i) +
                            " is out of bounds [0, " + (dims[i] - 1) + "]: " +
                            coords[i]);
                }
            }
        }

        for (int i = 0; i < dims.length; i++) {
            cellIndex *= dims[i];
            cellIndex += coords[i];

            if (cellIndex == Integer.MAX_VALUE || cellIndex < 0) {
                throw new ArrayIndexOutOfBoundsException("Cannot construct a " +
                        "cell table with that many cells.");
            }
        }

        return cellIndex;
    }

    private static boolean hasNextValue(Proposition proposition, int variable,
                                        int curIndex) {
        return nextValue(proposition, variable, curIndex) != -1;
    }

    private static int nextValue(Proposition proposition, int variable,
                                 int curIndex) {
        for (int i = curIndex + 1;
             i < proposition.getNumCategories(variable); i++) {
            if (proposition.isAllowed(variable, i)) {
                return i;
            }
        }

        return -1;
    }

    public boolean isMissingValueCaseFound() {
        return missingValueCaseFound;
    }
}





