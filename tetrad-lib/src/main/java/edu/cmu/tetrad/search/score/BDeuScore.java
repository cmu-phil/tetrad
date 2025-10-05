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
import org.apache.commons.math3.special.Gamma;
import org.apache.commons.math3.util.FastMath;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.List;

/**
 * Calculates the BDeu score, which the BDe (Bayes Dirichlet Equivalent) score with uniform priors. A good discussion of
 * BD* scores can be found here:
 * <p>
 * Heckerman, D., Geiger, D. &amp; Chickering, D.M. Learning Bayesian networks: The combination of knowledge and
 * statistical data. Mach Learn 20, 197â243 (1995).
 * <p>
 * As for all scores in Tetrad, higher scores mean more dependence, and negative scores indicate independence.
 *
 * @author josephramsey
 * @version $Id: $Id
 * @see BdeScore
 */
public class BDeuScore implements DiscreteScore {

    /**
     * The discrete dataset.
     */
    private final int[][] data;
    /**
     * The sample size of the data.
     */
    private final int sampleSize;
    /**
     * The number of categories for each variable.
     */
    private final int[] numCategories;
    /**
     * The discrete dataset.
     */
    private final DataSet dataSet;
    /**
     * The variables of the dataset.
     */
    private final List<Node> variables;
    /**
     * The sample prior.
     */
    private double priorEquivalentSampleSize = 1d;
    /**
     * The structure prior.
     */
    private double structurePrior = 0d;

    /**
     * Private constructor to prevent no-arg construction.
     *
     * @throws UnsupportedOperationException The BdeuScore class does not support the no-arg constructor.
     */
    private BDeuScore() {
        throw new UnsupportedOperationException("The BdeuScore class does not support the no-arg constructor.");
    }

    /**
     * Constructs a BDe score for the given dataset.
     *
     * @param dataSet A discrete dataset.
     */
    public BDeuScore(DataSet dataSet) {
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
            this.numCategories[i] = (getVariable(i)).getNumCategories();
        }
    }

    /**
     * Retrieves the row index corresponding to the given dimensions and values.
     *
     * @param dim    The dimensions of the array.
     * @param values The values corresponding to each dimension.
     * @return The row index.
     */
    private static int getRowIndex(int[] dim, int[] values) {
        int rowIndex = 0;
        for (int i = 0; i < dim.length; i++) {
            rowIndex *= dim[i];
            rowIndex += values[i];
        }
        return rowIndex;
    }

    /**
     * Calculates the local score of a node given its parents.
     *
     * @param node    The node.
     * @param parents The parents of the node.
     * @return The local score of the node.
     */
    @Override
    public double localScore(int node, int[] parents) {

        // Number of categories for node.
        int c = this.numCategories[node];

        // Numbers of categories of parents.
        int[] dims = new int[parents.length];

        for (int p = 0; p < parents.length; p++) {
            dims[p] = this.numCategories[parents[p]];
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

        int N = 0;

        ROW:
        for (int i = 0; i < this.sampleSize; i++) {
            for (int p = 0; p < parents.length; p++) {
                if (myParents[p][i] == -99) continue ROW;
                parentValues[p] = myParents[p][i];
            }

            int childValue = myChild[i];

            if (childValue == -99) {
                continue;
            }

            int rowIndex = BDeuScore.getRowIndex(dims, parentValues);

            n_jk[rowIndex][childValue]++;
            n_j[rowIndex]++;
            N++;
        }

        //Finally, compute the score
        double score = 0.0;

        score += getPriorForStructure(parents.length, N);

        double cellPrior = getPriorEquivalentSampleSize() / (c * r);
        double rowPrior = getPriorEquivalentSampleSize() / r;

        for (int j = 0; j < r; j++) {
            score -= Gamma.logGamma(rowPrior + n_j[j]);

            for (int k = 0; k < c; k++) {
                score += Gamma.logGamma(cellPrior + n_jk[j][k]);
            }
        }

        score += r * Gamma.logGamma(rowPrior);
        score -= c * r * Gamma.logGamma(cellPrior);

        if (Double.isNaN(score) || Double.isInfinite(score)) {
            return Double.NaN;
        } else {
            return score;
        }
    }

    /**
     * Calculates the difference in local scores between two nodes y and x, when x is added to a set of nodes z.
     *
     * @param x A node.
     * @param y The node.
     * @param z A set of nodes.
     * @return The difference in local scores between y and x added to z.
     */
    @Override
    public double localScoreDiff(int x, int y, int[] z) {
        return localScore(y, append(z, x)) - localScore(y, z);
    }

    /**
     * Retrieves the list of variables used in the object.
     *
     * @return A list of Node objects representing the variables used.
     */
    @Override
    public List<Node> getVariables() {
        return this.variables;
    }

    /**
     * Returns the sample size of the data.
     *
     * @return This size.
     */
    public int getSampleSize() {
        return this.sampleSize;
    }

    /**
     * Determines whether the bump exceeds zero.
     *
     * @param bump The bump value.
     * @return {@code true} if the bump is greater than zero, otherwise {@code false}.
     */
    public boolean isEffectEdge(double bump) {
        return bump > 0;
    }

    /**
     * Retrieves the dataset associated with this BdeuScore object.
     *
     * @return The dataset.
     */
    @Override
    public DataSet getDataSet() {
        return dataSet;
    }

    /**
     * Retrieves the structure prior associated with this BdeuScore object.
     *
     * @return The structure prior.
     */
    public double getStructurePrior() {
        return this.structurePrior;
    }

    /**
     * Sets the structure prior for the BdeuScore object.
     *
     * @param structurePrior The structure prior to be set.
     */
    public void setStructurePrior(double structurePrior) {
        this.structurePrior = structurePrior;
    }

    /**
     * Returns the sample prior.
     *
     * @return This prior.
     */
    public double getPriorEquivalentSampleSize() {
        return this.priorEquivalentSampleSize;
    }

    /**
     * Sets the sample prior for the BdeuScore object.
     *
     * @param samplePrior The sample prior to be set.
     */
    @Override
    public void setPriorEquivalentSampleSize(double samplePrior) {
        this.priorEquivalentSampleSize = samplePrior;
    }

    /**
     * Returns a string representation of this BDeu Score object.
     *
     * @return A string representation of this BDeu Score object.
     */
    @Override
    public String toString() {
        NumberFormat nf = new DecimalFormat("0.00");
        return "BDeu Score Sample prior = " + nf.format(this.priorEquivalentSampleSize) + " Structure prior = " + nf.format(this.structurePrior);
    }

    /**
     * Returns the maximum degree of the BDeuScore object.
     * <p>
     * The maximum degree is calculated as the ceiling value of the logarithm of the sample size.
     *
     * @return The maximum degree.
     */
    @Override
    public int getMaxDegree() {
        return (int) FastMath.ceil(FastMath.log(this.sampleSize));
    }

    /**
     * Determines whether a set of nodes z determines a specific node y.
     *
     * @param z The set of nodes.
     * @param y The node to be determined.
     * @return {@code true} if the set of nodes z determines the node y, {@code false} otherwise.
     * @throws UnsupportedOperationException The BDeu score does not implement a 'determines' method.
     */
    @Override
    public boolean determines(List<Node> z, Node y) {
        throw new UnsupportedOperationException("The BDeu score does not implement a 'determines' method.");
    }

    /**
     * Retrieves the variable at the specified index.
     *
     * @param i The index of the variable to retrieve.
     * @return The variable at the specified index.
     */
    private DiscreteVariable getVariable(int i) {
        return (DiscreteVariable) this.variables.get(i);
    }

    /**
     * Retrieves the prior for the structure given the number of parents and total number of variables.
     *
     * @param numParents The number of parents.
     * @param N          The total number of variables.
     * @return The prior for the structure.
     */
    private double getPriorForStructure(int numParents, int N) {
        double e = getStructurePrior();
        if (e == 0) return 0.0;
        else {
            int vm = N - 1;
            return numParents * FastMath.log(e / (vm)) + (vm - numParents) * FastMath.log(1.0 - (e / (vm)));
        }
    }
}




