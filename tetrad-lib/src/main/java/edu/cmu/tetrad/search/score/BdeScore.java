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

package edu.cmu.tetrad.search.score;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.Fges;
import org.apache.commons.math3.special.Gamma;

import java.util.List;

/**
 * Calculates the BDe score (Bayes Dirichlet Equivalent) score for analyzing discrete multinomial data. A good
 * discussion of BD* scores can be found here:
 * <p>
 * Heckerman, D., Geiger, D. &amp; Chickering, D.M. Learning Bayesian networks: The combination of knowledge and
 * statistical data. Mach Learn 20, 197–243 (1995).
 * <p>
 * As for all scores in Tetrad, higher scores mean more dependence, and negative scores indicate independence.
 *
 * @author josephramsey
 * @see BdeuScore
 */
public class BdeScore implements DiscreteScore {

    // The discrete dataset.
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
     * Returns the difference between localScore(y | z, x) and localScore(y | z)
     *
     * @param x The index of the x variable
     * @param y The index of the y variable.
     * @param z The indices of the z variables
     * @return The difference in scores.
     */
    @Override
    public double localScoreDiff(int x, int y, int[] z) {
        return localScore(y, append(z, x)) - localScore(y, z);
    }


    /**
     * Returns the dataset being analyzed.
     *
     * @return This dataset.
     */
    @Override
    public DataSet getDataSet() {
        return this.dataSet;
    }

    private int getRowIndex(int[] dim, int[] values) {
        int rowIndex = 0;
        for (int i = 0; i < dim.length; i++) {
            rowIndex *= dim[i];
            rowIndex += values[i];
        }
        return rowIndex;
    }

    /**
     * @throws UnsupportedOperationException Since this method is not implemented for this score.
     */
    public void setStructurePrior(double structurePrior) {
        throw new UnsupportedOperationException("BDe does not use a structure prior.");
    }

    /**
     * @throws UnsupportedOperationException Since this method is not implemented for this score.
     */
    public void setSamplePrior(double samplePrior) {
        throw new UnsupportedOperationException("BDe does not use a sample prior.");
    }

    /**
     * Returns the variables of the dataset.
     *
     * @return These variables as  list.
     */
    @Override
    public List<Node> getVariables() {
        return this.dataSet.getVariables();
    }

    /**
     * Returns the sample size of the data.
     *
     * @return This size.
     */
    public int getSampleSize() {
        return this.dataSet.getNumRows();
    }

    /**
     * Returns a judgment of whether the given bump in score allows one to conclude that the edge is an "effect edge"
     * for FGES.
     *
     * @param bump The bump.
     * @return True iff so.
     * @see Fges
     */
    @Override
    public boolean isEffectEdge(double bump) {
        return bump > -20;
    }

    /**
     * Returns the maximum degree of the graphs as they're searched.
     *
     * @return This maximum degree.
     */
    @Override
    public int getMaxDegree() {
        return 1000;
    }

    /**
     * Returns "BDe Score".
     *
     * @return This string.
     */
    @Override
    public String toString() {
        return "BDe Score";
    }

    private int sampleSize() {
        return dataSet().getNumRows();
    }

    private int numCategories(int i) {
        return ((DiscreteVariable) dataSet().getVariable(i)).getNumCategories();
    }

    private DataSet dataSet() {
        return this.dataSet;
    }


}


