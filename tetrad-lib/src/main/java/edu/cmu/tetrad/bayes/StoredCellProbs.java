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

import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.NumberFormatUtil;
import edu.cmu.tetrad.util.RandomUtil;
import edu.cmu.tetrad.util.TetradSerializable;

import java.io.Serial;
import java.text.NumberFormat;
import java.util.*;

/**
 * <p>Creates a table of stored cell probabilities for the given list of
 * variables. Since for a moderate number of variables and for a moderate number of values per variables this could get
 * to be a very large table, it might not be a good idea to use this class except for unit testing.&gt; 0
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public final class StoredCellProbs implements TetradSerializable, DiscreteProbs {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The variables for which this table stores cell probabilities.
     */
    private final List<Node> variables;

    /**
     * The parent dimensions of the table.
     */
    private final int[] parentDims;

    /**
     * The cell probabilities.
     */
    private final double[] probs;

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
        Set<Object> variableSet = new HashSet<>(this.variables);
        if (variableSet.size() < this.variables.size()) {
            throw new IllegalArgumentException("Duplicate variable.");
        }

        this.parentDims = new int[getVariables().size()];

        for (int i = 0; i < getVariables().size(); i++) {
            DiscreteVariable var = (DiscreteVariable) getVariables().get(i);
            this.parentDims[i] = var.getNumCategories();
        }

        int numCells = 1;

        for (int parentDim : this.parentDims) {
            numCells *= parentDim;
        }

        this.probs = new double[numCells];
    }

    /**
     * <p>createRandomCellTable.</p>
     *
     * @param variables a {@link java.util.List} object
     * @return a {@link edu.cmu.tetrad.bayes.StoredCellProbs} object
     */
    public static StoredCellProbs createRandomCellTable(List<Node> variables) {
        StoredCellProbs cellProbs = new StoredCellProbs(variables);
//        RandomUtil.getInstance().setSeed(39492993L);

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

    /**
     * <p>createCellTable.</p>
     *
     * @param bayesIm a {@link edu.cmu.tetrad.bayes.BayesIm} object
     * @return a {@link edu.cmu.tetrad.bayes.StoredCellProbs} object
     */
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
     *
     * @return a {@link edu.cmu.tetrad.bayes.StoredCellProbs} object
     */
    public static StoredCellProbs serializableInstance() {
        return new StoredCellProbs(new ArrayList<>());
    }

    //=============================PUBLIC METHODS=========================//

    private static boolean hasNextValue(Proposition proposition, int variable,
                                        int curIndex) {
        return StoredCellProbs.nextValue(proposition, variable, curIndex) != -1;
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

    /**
     * <p>getCellProb.</p>
     *
     * @param variableValues an array of {@link int} objects
     * @return the probability for the given cell, specified as a particular combination of variable values, for the
     * list of variables (in order) returned by get
     */
    public double getCellProb(int[] variableValues) {
        return this.probs[getOffset(variableValues)];
    }

    /**
     * Calculates the probability for the given proposition assertion.
     *
     * @param assertion The proposition assertion for which to calculate the probability.
     * @return The probability for the given proposition assertion.
     */
    public double getProb(Proposition assertion) {

        // Initialize to 0's.
        int[] variableValues = new int[assertion.getNumVariables()];

        for (int i = 0; i < assertion.getNumVariables(); i++) {
            variableValues[i] = StoredCellProbs.nextValue(assertion, i, -1);
        }

        variableValues[variableValues.length - 1] = -1;
        double p = 0.0;

        loop:
        while (true) {
            for (int i = assertion.getNumVariables() - 1; i >= 0; i--) {
                if (StoredCellProbs.hasNextValue(assertion, i, variableValues[i])) {
                    variableValues[i] =
                            StoredCellProbs.nextValue(assertion, i, variableValues[i]);

                    for (int j = i + 1; j < assertion.getNumVariables(); j++) {
                        if (StoredCellProbs.hasNextValue(assertion, j, -1)) {
                            variableValues[j] = StoredCellProbs.nextValue(assertion, j, -1);
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
     * Calculates the conditional probability of an assertion given a condition.
     *
     * @param assertion the proposition assertion for which to calculate the probability
     * @param condition the proposition condition
     * @return the conditional probability of the assertion given the condition
     * @throws IllegalArgumentException if the assertion and condition are not for the same Bayes IM
     */
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
            variableValues[i] = StoredCellProbs.nextValue(condition, i, -1);
        }

        variableValues[variableValues.length - 1] = -1;
        double conditionTrue = 0.0;
        double assertionTrue = 0.0;

        loop:
        while (true) {
            for (int i = condition.getNumVariables() - 1; i >= 0; i--) {
                if (StoredCellProbs.hasNextValue(condition, i, variableValues[i])) {
                    variableValues[i] =
                            StoredCellProbs.nextValue(condition, i, variableValues[i]);

                    for (int j = i + 1; j < condition.getNumVariables(); j++) {
                        if (StoredCellProbs.hasNextValue(condition, j, -1)) {
                            variableValues[j] = StoredCellProbs.nextValue(condition, j, -1);
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

    /**
     * <p>Getter for the field <code>variables</code>.</p>
     *
     * @return a {@link java.util.List} object
     */
    public List<Node> getVariables() {
        return this.variables;
    }

    /**
     * <p>toString.</p>
     *
     * @return a {@link java.lang.String} object
     */
    public String toString() {
        StringBuilder buf = new StringBuilder();
        NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();

        buf.append("\nCell Probabilities:");

        buf.append("\n");

        for (Node variable : this.variables) {
            buf.append(variable).append("\t");
        }

        double sum = 0.0;
        final int maxLines = 500;

        for (int i = 0; i < this.probs.length; i++) {
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

            buf.append(nf.format(this.probs[i]));
            sum += this.probs[i];
        }

        buf.append("\n\nSum = ").append(nf.format(sum));

        return buf.toString();
    }

    //============================PRIVATE METHODS==========================//

    /**
     * @return the row in the table for the given node and combination of parent values.
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
     * @return an array containing the number of values, in order, of each variable.
     */
    private int[] getParentDims() {
        return this.parentDims;
    }

    /**
     * Sets the cell probability. Should not be made public for now, since there's no way to guarantee the probabilities
     * will add to 1.0 if they're set one at a time.
     */
    private void setCellProbability(int[] variableValues, double probability) {
        if (probability < 0.0 || probability > 1.0) {
            throw new IllegalArgumentException(
                    "Probability not in [0.0, 1.0]: " + probability);
        }

        this.probs[getOffset(variableValues)] = probability;
    }
}





