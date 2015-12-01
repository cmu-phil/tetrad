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

import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.NumberFormatUtil;
import edu.cmu.tetrad.util.RandomUtil;
import edu.cmu.tetrad.util.TetradSerializable;

import java.text.NumberFormat;
import java.util.*;

/**
 * <p>Creates a table of stored cell probabilities for the given list of
 * variables. Since for a moderate number of variables and for a moderate number
 * of values per variables this could get to be a very large table, it might not
 * be a good idea to use this class except for unit testing.</p>
 *
 * @author Joseph Ramsey
 */
public final class StoredCellProbs implements TetradSerializable, DiscreteProbs {
    static final long serialVersionUID = 23L;

    private List<Node> variables;
    private int[] parentDims;
    private double[] probs;

    //============================CONSTRUCTORS============================//

    private StoredCellProbs(List<Node> variables) {
        if (variables == null) {
            throw new NullPointerException();
        }

        for (Object variable : variables) {
            if (variable == null) {
                throw new NullPointerException();
            }

            if (!(variable instanceof DiscreteVariable)) {
                throw new IllegalArgumentException(
                        "Not a discrete variable: " + variable.getClass());
            }
        }

        this.variables = Collections.unmodifiableList(variables);
        Set<Object> variableSet = new HashSet<Object>(this.variables);
        if (variableSet.size() < this.variables.size()) {
            throw new IllegalArgumentException("Duplicate variable.");
        }

        this.parentDims = new int[getVariables().size()];

        for (int i = 0; i < getVariables().size(); i++) {
            DiscreteVariable var = (DiscreteVariable) getVariables().get(i);
            parentDims[i] = var.getNumCategories();
        }

        int numCells = 1;

        for (int parentDim : this.parentDims) {
            numCells *= parentDim;
        }

        this.probs = new double[numCells];
    }

    public static StoredCellProbs createRandomCellTable(List<Node> variables) {
        StoredCellProbs cellProbs = new StoredCellProbs(variables);

        double sum = 0.0;

        for (int i = 0; i < cellProbs.probs.length; i++) {
            double value = RandomUtil.getInstance().nextDouble();
            cellProbs.probs[i] = value;
            sum += value;
        }

        for (int i = 0; i < cellProbs.probs.length; i++) {
            cellProbs.probs[i] /= sum;
        }

        return cellProbs;
    }

    public static StoredCellProbs createCellTable(BayesIm bayesIm) {
        if (bayesIm == null) {
            throw new NullPointerException();
        }

        BayesImProbs cellProbsOnTheFly = new BayesImProbs(bayesIm);
        StoredCellProbs cellProbs =
                new StoredCellProbs(cellProbsOnTheFly.getVariables());

        for (int i = 0; i < cellProbs.probs.length; i++) {
            int[] variableValues = cellProbs.getVariableValues(i);
            double p = cellProbsOnTheFly.getCellProb(variableValues);
            cellProbs.setCellProbability(variableValues, p);
        }

        return cellProbs;
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     */
    public static StoredCellProbs serializableInstance() {
        return new StoredCellProbs(new ArrayList<Node>());
    }

    //=============================PUBLIC METHODS=========================//

    /**
     * @return the probability for the given cell, specified as a particular
     * combination of variable values, for the list of variables (in order)
     * returned by get
     */
    public double getCellProb(int[] variableValues) {
        return probs[getOffset(variableValues)];
    }

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

    public double getConditionalProb(Proposition assertion,
                                     Proposition condition) {
        if (assertion.getVariableSource() != condition.getVariableSource()) {
            throw new IllegalArgumentException(
                    "Assertion and condition must be " +
                            "for the same Bayes IM.");
        }

        // Initialize to 0's.
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

    public boolean isMissingValueCaseFound() {
        return false;
    }

    public List<Node> getVariables() {
        return this.variables;
    }

    public String toString() {
        StringBuilder buf = new StringBuilder();
        NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();

        buf.append("\nCell Probabilities:");

        buf.append("\n");

        for (Node variable : variables) {
            buf.append(variable).append("\t");
        }

        double sum = 0.0;
        int maxLines = 500;

        for (int i = 0; i < probs.length; i++) {
            if (i >= maxLines) {
                buf.append("\nCowardly refusing to print more than ")
                        .append(maxLines).append(" lines.");
                break;
            }

            buf.append("\n");

            int[] variableValues = getVariableValues(i);

            for (int variableValue : variableValues) {
                buf.append(variableValue).append("\t");
            }

            buf.append(nf.format(probs[i]));
            sum += probs[i];
        }

        buf.append("\n\nSum = ").append(nf.format(sum));

        return buf.toString();
    }

    //============================PRIVATE METHODS==========================//

    /**
     * @return the row in the table for the given node and combination of parent
     * values.
     */
    private int getOffset(int[] values) {
        int[] dim = getParentDims();
        int offset = 0;

        for (int i = 0; i < dim.length; i++) {
            if (values[i] < 0 || values[i] >= dim[i]) {
                throw new IllegalArgumentException();
            }

            offset *= dim[i];
            offset += values[i];
        }

        return offset;
    }

    private int[] getVariableValues(int rowIndex) {
        int[] dims = getParentDims();
        int[] values = new int[dims.length];

        for (int i = dims.length - 1; i >= 0; i--) {
            values[i] = rowIndex % dims[i];
            rowIndex /= dims[i];
        }

        return values;
    }

    /**
     * @return an array containing the number of values, in order, of each
     * variable.
     */
    private int[] getParentDims() {
        return this.parentDims;
    }

    /**
     * Sets the cell probability. Should not be made public for now, since
     * there's no way to guarantee the probabilities will add to 1.0 if they're
     * set one at a time.
     */
    private void setCellProbability(int[] variableValues, double probability) {
        if (probability < 0.0 || probability > 1.0) {
            throw new IllegalArgumentException(
                    "Probability not in [0.0, 1.0]: " + probability);
        }

        probs[getOffset(variableValues)] = probability;
    }
}





