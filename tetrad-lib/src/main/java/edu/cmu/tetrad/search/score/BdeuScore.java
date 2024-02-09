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

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.Fges;
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
 * statistical data. Mach Learn 20, 197–243 (1995).
 * <p>
 * As for all scores in Tetrad, higher scores mean more dependence, and negative scores indicate independence.
 *
 * @author josephramsey
 * @see BdeScore
 * @version $Id: $Id
 */
public class BdeuScore implements DiscreteScore {

    // The discrete dataset.
    private final int[][] data;
    // The sample size of the data.
    private final int sampleSize;
    // The number of categories for each variable.
    private final int[] numCategories;
    // The discrete dataset.
    private final DataSet dataSet;
    // The variables of the dataset.
    private final List<Node> variables;
    // The sample prior.
    private double samplePrior = 1d;
    // The structure prior.
    private double structurePrior = 0d;

    /**
     * Constructs a BDe score for the given dataset.
     *
     * @param dataSet A discrete dataset.
     */
    public BdeuScore(DataSet dataSet) {
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
     *
     * Calculates the BDeu score of a node given its parents.
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

            int rowIndex = BdeuScore.getRowIndex(dims, parentValues);

            n_jk[rowIndex][childValue]++;
            n_j[rowIndex]++;
            N++;
        }

        //Finally, compute the score
        double score = 0.0;

        score += getPriorForStructure(parents.length, N);

        double cellPrior = getSamplePrior() / (c * r);
        double rowPrior = getSamplePrior() / r;

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
     * {@inheritDoc}
     *
     * Calculates localScore(y | z, x) - localScore(y | z).
     */
    @Override
    public double localScoreDiff(int x, int y, int[] z) {
        return localScore(y, append(z, x)) - localScore(y, z);
    }

    /**
     * {@inheritDoc}
     *
     * Returns the variables of the data.
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
     * {@inheritDoc}
     *
     * For FGES, this determines whether an edge counts as an effect edge.
     * @see Fges
     */
    public boolean isEffectEdge(double bump) {
        return bump > 0;
    }

    /**
     * {@inheritDoc}
     *
     * Returns the dataset being analyzed.
     */
    @Override
    public DataSet getDataSet() {
        return dataSet;
    }

    /**
     * Returns the structure prior.
     *
     * @return This prior.
     */
    public double getStructurePrior() {
        return this.structurePrior;
    }

    /**
     * {@inheritDoc}
     *
     * Sets the structure prior
     */
    public void setStructurePrior(double structurePrior) {
        this.structurePrior = structurePrior;
    }

    /**
     * Returns the sample prior.
     *
     * @return This prior.
     */
    public double getSamplePrior() {
        return this.samplePrior;
    }

    /**
     * {@inheritDoc}
     *
     * Set the sample prior
     */
    @Override
    public void setSamplePrior(double samplePrior) {
        this.samplePrior = samplePrior;
    }

    /**
     * {@inheritDoc}
     *
     * Returns a string representation of this score.
     */
    @Override
    public String toString() {
        NumberFormat nf = new DecimalFormat("0.00");
        return "BDeu Score Sample prior = " + nf.format(this.samplePrior) + " Structure prior = " + nf.format(this.structurePrior);
    }

    /**
     * {@inheritDoc}
     *
     * Returns the needed max degree for some searches.
     */
    @Override
    public int getMaxDegree() {
        return (int) FastMath.ceil(FastMath.log(this.sampleSize));
    }

    /**
     * {@inheritDoc}
     *
     * This score does not implement a method to decide whether a node is determined by its parents.
     */
    @Override
    public boolean determines(List<Node> z, Node y) {
        throw new UnsupportedOperationException("The BDeu score does not implement a 'determines' method.");
    }

    private DiscreteVariable getVariable(int i) {
        return (DiscreteVariable) this.variables.get(i);
    }

    private double getPriorForStructure(int numParents, int N) {
        double e = getStructurePrior();
        if (e == 0) return 0.0;
        else {
            int vm = N - 1;
            return numParents * FastMath.log(e / (vm)) + (vm - numParents) * FastMath.log(1.0 - (e / (vm)));
        }
    }
}



