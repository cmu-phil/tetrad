///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2025 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
// and Richard Scheines.                                                     //
//                                                                           //
// This program is free software: you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation, either version 3 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program.  If not, see <https://www.gnu.org/licenses/>.    //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.search.score;

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.Fges;
import org.apache.commons.math3.util.FastMath;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.apache.commons.math3.util.FastMath.abs;
import static org.apache.commons.math3.util.FastMath.log;

/**
 * Calculates the discrete BIC score. The likelihood for this score is calculated as SUM(ln(P(X | Z) P(Z))) across all
 * cells in all conditional probability tables for the discrete model. The parameters are counted as SUM(rows * (cols -
 * 1)) for all conditional probability tables in the model, where rows summing to zero are discounted, as their marginal
 * probabilities cannot be calcualted. Then the BIC score is calculated as 2L - ck ln N, where c is a multiplier on the
 * penalty ("penalty discount").
 * <p>
 * As for all scores in Tetrad, higher scores mean more dependence, and negative scores indicate independence.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class DiscreteBicScore implements DiscreteScore {
    /**
     * The discrete dataset.
     */
    private final DataSet dataSet;
    /**
     * The variables of the dataset.
     */
    private final int[][] data;
    /**
     * The sample size.
     */
    private final int sampleSize;
    /**
     * The number of categories for each variable.
     */
    private final int[] numCategories;
    private final HashMap<Integer, Map<Integer, Integer>> attestedCategories;
    /**
     * The variables of the dataset.
     */
    private List<Node> variables;
    /**
     * The penalty discount.
     */
    private double penaltyDiscount = 1;
    /**
     * The structure prior.
     */
    private double structurePrior = 0;

    /**
     * Private constructor to prevent instantiation.
     */
    private DiscreteBicScore() {
        throw new UnsupportedOperationException();
    }

    /**
     * Constructs the score using a dataset.
     *
     * @param dataSet The discrete dataset to analyze.
     */
    public DiscreteBicScore(DataSet dataSet) {
        if (dataSet == null) {
            throw new NullPointerException("Data was not provided.");
        }

        this.dataSet = dataSet;

        if (dataSet instanceof BoxDataSet && ((BoxDataSet) dataSet).getDataBox() instanceof VerticalIntDataBox) {
            DataBox dataBox = ((BoxDataSet) dataSet).getDataBox();
            this.variables = dataSet.getVariables();
            VerticalIntDataBox box = (VerticalIntDataBox) dataBox;

            this.data = box.getVariableVectors();
            this.sampleSize = box.numRows();
        } else {
            this.data = new int[dataSet.getNumColumns()][];
            this.variables = dataSet.getVariables();

            for (int j = 0; j < dataSet.getNumColumns(); j++) {
                this.data[j] = new int[dataSet.getNumRows()];

                for (int i = 0; i < dataSet.getNumRows(); i++) {
                    this.data[j][i] = dataSet.getInt(i, j);
                }
            }

            this.sampleSize = dataSet.getNumRows();
        }

        List<Node> variables = dataSet.getVariables();
        this.numCategories = new int[variables.size()];
        for (int i = 0; i < variables.size(); i++) {
            this.numCategories[i] = getVariable(i).getNumCategories();
        }

        attestedCategories = new HashMap<Integer, Map<Integer, Integer>>();

        for (int i = 0; i < data.length; i++) {
            attestedCategories.put(i, new HashMap<Integer, Integer>());
            int c = 0;

            // Go through the data and map each new category to an integer if it hasn't been seen before.
            for (int j = 0; j < data[i].length; j++) {
                if (data[i][j] != -99) {
                    if (attestedCategories.get(i).containsKey(data[i][j])) {
                        continue;
                    }

                    attestedCategories.get(i).put(data[i][j], c++);
                }
            }
        }

        System.out.println("DiscreteBicScore: attestedCategories = " + attestedCategories);
    }

    private static int getRowIndex(int[] dim, int[] values) {
        int rowIndex = 0;
        for (int i = 0; i < dim.length; i++) {
            rowIndex *= dim[i];
            rowIndex += values[i];
        }
        return rowIndex;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns the score of the given nodes given its parents.
     */
    @Override
    public double localScore(int node, int[] parents) {

        if (!(this.variables.get(node) instanceof DiscreteVariable)) {
            throw new IllegalArgumentException("Not discrete: " + this.variables.get(node));
        }

        for (int t : parents) {
            if (!(this.variables.get(t) instanceof DiscreteVariable)) {
                throw new IllegalArgumentException("Not discrete: " + this.variables.get(t));
            }
        }

        // Number of categories for node.
//        int c = this.numCategories[node];
        int c = attestedCategories.get(node).keySet().size();

        // Numbers of categories of parents.
        int[] dims = new int[parents.length];

        for (int p = 0; p < parents.length; p++) {
//            dims[p] = this.numCategories[parents[p]];
            dims[p] = attestedCategories.get(parents[p]).keySet().size();
        }

        // Number of parent states.
        int r = 1;

        for (int p = 0; p < parents.length; p++) {
            r *= dims[p];
        }

        // Conditional cell coefs of data for node given parents(node).
        int[][] n_jk = new int[r][c];
        int[] n_j = new int[r];

        int[] parentValues = new int[parents.length];

        int[][] myParents = new int[parents.length][];
        for (int i = 0; i < parents.length; i++) {
            myParents[i] = this.data[parents[i]];
        }

        int[] myChild = this.data[node];

        ROW:
        for (int i = 0; i < this.sampleSize; i++) {
            for (int p = 0; p < parents.length; p++) {
                if (myParents[p][i] == -99) parentValues[p] = -99;
                else parentValues[p] = attestedCategories.get(parents[p]).get(myParents[p][i]);
            }

            int childValue;

            if (myChild[i] == -99) childValue = -99;
            else childValue = myChild[i];

            int rowIndex = DiscreteBicScore.getRowIndex(dims, parentValues);

            n_jk[rowIndex][childValue]++;
            n_j[rowIndex]++;
        }

        //Finally, compute the score
        double lik = 0.0;

        for (int rowIndex = 0; rowIndex < r; rowIndex++) {
            int rowCount = n_j[rowIndex];
            if (rowCount == 0) continue;

            for (int childValue = 0; childValue < c; childValue++) {
                int cellCount = n_jk[rowIndex][childValue];
                if (cellCount == 0) continue;

                lik += cellCount * FastMath.log(cellCount / (double) rowCount);
            }
        }

//        int attestedRows = 0;
//
//        for (int rowIndex = 0; rowIndex < r; rowIndex++) {
//            if (n_j[rowIndex] > 0) {
//                attestedRows++;
//            }
//        }
//
//        int params = attestedRows * (c - 1);
        int params = r * (c - 1);

        double score = 2 * lik - this.penaltyDiscount * params * FastMath.log(sampleSize) + 2 * getPriorForStructure(parents.length);

        if (Double.isNaN(score) || Double.isInfinite(score)) {
            return Double.NaN;
        } else {
            return score;
        }
    }

    /**
     * Returns the number of parameters for a node given its parents.
     *
     * @param node    The index of the node.
     * @param parents The indices of the node's parents.
     * @return a int
     */
    public int numParameters(int node, int[] parents) {
        if (!(this.variables.get(node) instanceof DiscreteVariable)) {
            throw new IllegalArgumentException("Not discrete: " + this.variables.get(node));
        }

        for (int t : parents) {
            if (!(this.variables.get(t) instanceof DiscreteVariable)) {
                throw new IllegalArgumentException("Not discrete: " + this.variables.get(t));
            }
        }

        // Number of categories for node.
//        int c = this.numCategories[node];
        int c = attestedCategories.get(node).keySet().size();

        // Numbers of categories of parents.
        int[] dims = new int[parents.length];

        for (int p = 0; p < parents.length; p++) {
//            dims[p] = this.numCategories[parents[p]];
            dims[p] = attestedCategories.get(parents[p]).keySet().size();
        }

        // Number of parent states.
        int r = 1;

        for (int p = 0; p < parents.length; p++) {
            r *= dims[p];
        }

        return r * (c - 1);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns localScore(y | z, x) - localScore(y | z).
     */
    @Override
    public double localScoreDiff(int x, int y, int[] z) {
        return localScore(y, append(z, x)) - localScore(y, z);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns the variables.
     */
    @Override
    public List<Node> getVariables() {
        return this.variables;
    }

    /**
     * Sets the variables to a new list of the same size.
     *
     * @param variables The new list of variables.
     */
    public void setVariables(List<Node> variables) {
        for (int i = 0; i < variables.size(); i++) {
            if (!variables.get(i).getName().equals(this.variables.get(i).getName())) {
                throw new IllegalArgumentException("Variable in index " + (i + 1) + " does not have the same name " +
                                                   "as the variable being substituted for it.");
            }
        }

        this.variables = variables;
    }

    /**
     * Returns the sample size.
     *
     * @return This size.
     */
    public int getSampleSize() {
        return this.sampleSize;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Must be called directly after the corresponding scoring call. Used in FGES.
     *
     * @see Fges
     */
    public boolean isEffectEdge(double bump) {
        return bump > 0;//lastBumpThreshold;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns the dataset being analyzed.
     */
    @Override
    public DataSet getDataSet() {
        return this.dataSet;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Sets the structure prior.
     */
    @Override
    public void setStructurePrior(double structurePrior) {
        this.structurePrior = structurePrior;
    }

    /**
     * {@inheritDoc}
     * <p>
     * This method is not used for this score.
     */
    @Override
    public void setSamplePrior(double samplePrior) {
        throw new UnsupportedOperationException("This method is not used.");
    }

    /**
     * Sets the penalty discount, which is a multiplier on the penalty term of BIC.
     *
     * @param penaltyDiscount This discount.
     */
    public void setPenaltyDiscount(double penaltyDiscount) {
        this.penaltyDiscount = penaltyDiscount;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns the maximum degree for some algorithms.
     */
    @Override
    public int getMaxDegree() {
        return 1000;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns a string representation of this score.
     */
    @Override
    public String toString() {
        return "BIC Score";
    }

    private double getPriorForStructure(int parents) {
        if (abs(this.structurePrior) <= 0) {
            return 0;
        } else {
            double p = (this.structurePrior) / (this.variables.size());
            return -((parents) * log(p) + (this.variables.size() - (parents)) * log(1.0 - p));
        }
    }

    private DiscreteVariable getVariable(int i) {
        return (DiscreteVariable) this.variables.get(i);
    }
}




