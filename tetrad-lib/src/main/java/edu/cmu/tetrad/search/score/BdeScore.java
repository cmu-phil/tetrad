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

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.graph.Node;
import org.apache.commons.math3.special.Gamma;

import java.util.List;

/**
 * Calculates the BDe score (Bayes Dirichlet Equivalent) score for analyzing discrete multinomial data. A good
 * discussion of BD* scores can be found here:
 * <p>
 * Heckerman, D., Geiger, D. &amp; Chickering, D.M. Learning Bayesian networks: The combination of knowledge and
 * statistical data. Mach Learn 20, 197â243 (1995).
 * <p>
 * As for all scores in Tetrad, higher scores mean more dependence, and negative scores indicate independence.
 *
 * @author josephramsey
 * @version $Id: $Id
 * @see BdeuScore
 */
public class BdeScore implements DiscreteScore {

    /**
     * The discrete dataset.
     */
    private final DataSet dataSet;

    /**
     * Constructs a BDe score for the given dataset.
     *
     * @param dataSet A discrete dataset.
     */
    public BdeScore(DataSet dataSet) {
        if (dataSet == null) {
            throw new NullPointerException();
        }

        if (!dataSet.isDiscrete()) {
            throw new IllegalArgumentException("Need a discrete data set.");
        }

        this.dataSet = dataSet;
    }

    /**
     * Returns the score for the given parent given its parents, where these are specified as column indices into the
     * dataset.
     *
     * @param i       The index of the variable.
     * @param parents The indices of the parents of the variables.
     * @return the score, or NaN if the score can't be calculated.
     */
    public double localScore(int i, int[] parents) {

        // Number of categories for index i.
        int r = numCategories(i);

        // Numbers of categories of parents.
        int[] dims = new int[parents.length];

        for (int p = 0; p < parents.length; p++) {
            dims[p] = numCategories(parents[p]);
        }

        // Number of parent states.
        int q = 1;
        for (int p = 0; p < parents.length; p++) {
            q *= dims[p];
        }

        // Conditional cell coefs of data for i given parents(i).
        int[][] n_ijk = new int[q][r];
        int[] n_ij = new int[q];

        int[] values = new int[parents.length];

        for (int n = 0; n < sampleSize(); n++) {
            for (int p = 0; p < parents.length; p++) {
                int parentValue = dataSet().getInt(n, parents[p]);

                if (parentValue == -99) {
                    throw new IllegalStateException("Please remove or impute " +
                                                    "missing values.");
                }

                values[p] = parentValue;
            }

            int childValue = dataSet().getInt(n, i);

            if (childValue == -99) {
                throw new IllegalStateException("Please remove or impute missing " +
                                                "values (record " + n + " column " + i + ")");

            }

            int rowIndex = getRowIndex(dims, values);
            n_ijk[rowIndex][childValue]++;
        }

        // Row sums.
        for (int j = 0; j < q; j++) {
            for (int k = 0; k < r; k++) {
                n_ij[j] += n_ijk[j][k];
            }
        }

        //Finally, compute the score
        double score = 0;

        for (int j = 0; j < q; j++) {
            for (int k = 0; k < r; k++) {
                double nPrimeijk = 1. / (r * q);
                score += Gamma.logGamma(n_ijk[j][k] + nPrimeijk);
                score -= Gamma.logGamma(nPrimeijk);
            }

            double nPrimeij = 1. / q;

            score += Gamma.logGamma(nPrimeij);
            score -= Gamma.logGamma(n_ij[j] + nPrimeij);
        }

        if (Double.isNaN(score) || Double.isInfinite(score)) {
            return Double.NaN;
        } else {
            return score;
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns the difference between localScore(y | z, x) and localScore(y | z)
     */
    @Override
    public double localScoreDiff(int x, int y, int[] z) {
        return localScore(y, append(z, x)) - localScore(y, z);
    }


    /**
     * Returns the DataSet associated with this method.
     *
     * @return The DataSet object.
     */
    @Override
    public DataSet getDataSet() {
        return this.dataSet;
    }

    /**
     * Returns the index of a row in a multidimensional array based on the given dimensions and values.
     *
     * @param dim    The dimensions for each axis.
     * @param values The values for each axis.
     * @return The index of the row.
     */
    private int getRowIndex(int[] dim, int[] values) {
        int rowIndex = 0;
        for (int i = 0; i < dim.length; i++) {
            rowIndex *= dim[i];
            rowIndex += values[i];
        }
        return rowIndex;
    }

    /**
     * Sets the structure prior for the BDe score.
     *
     * @param structurePrior The structure prior value.
     */
    public void setStructurePrior(double structurePrior) {
        throw new UnsupportedOperationException("BDe does not use a structure prior.");
    }

    /**
     * Sets the sample prior for the BDe score.
     *
     * @param samplePrior The sample prior value.
     */
    public void setSamplePrior(double samplePrior) {
        throw new UnsupportedOperationException("BDe does not use a sample prior.");
    }

    /**
     * Returns the variables present in the DataSet associated with this method.
     *
     * @return A list of Node objects representing the variables.
     */
    @Override
    public List<Node> getVariables() {
        return this.dataSet.getVariables();
    }

    /**
     * Returns the sample size of the data set.
     *
     * @return The sample size.
     */
    public int getSampleSize() {
        return this.dataSet.getNumRows();
    }

    /**
     * Determines if an edge has an effect.
     *
     * @param bump The bump value.
     * @return true if the bump value is greater than -20, false otherwise.
     */
    @Override
    public boolean isEffectEdge(double bump) {
        return bump > -20;
    }

    /**
     * Gets the maximum degree of the BDe Score.
     *
     * @return The maximum degree.
     */
    @Override
    public int getMaxDegree() {
        return 1000;
    }

    /**
     * Returns a string representation of the object.
     *
     * @return A string representation of the object.
     */
    @Override
    public String toString() {
        return "BDe Score";
    }

    /**
     * Returns the sample size of the data set.
     *
     * @return The sample size.
     */
    private int sampleSize() {
        return dataSet().getNumRows();
    }

    /**
     * Returns the number of categories for a given variable index.
     *
     * @param i The index of the variable.
     * @return The number of categories for the variable.
     */
    private int numCategories(int i) {
        return ((DiscreteVariable) dataSet().getVariable(i)).getNumCategories();
    }

    /**
     * Returns the DataSet associated with this method.
     *
     * @return The DataSet object.
     */
    private DataSet dataSet() {
        return this.dataSet;
    }
}



