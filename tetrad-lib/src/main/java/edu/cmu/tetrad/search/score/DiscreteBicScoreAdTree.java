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

import edu.cmu.tetrad.data.CellTableAdTree;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.Fges;
import org.apache.commons.math3.util.FastMath;

import java.util.ArrayList;
import java.util.List;

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
public class DiscreteBicScoreAdTree implements DiscreteScore {
    /**
     * The discrete dataset.
     */
    private final DataSet dataSet;
    /**
     * The sample size.
     */
    private final int sampleSize;
    /**
     * The number of categories for each variable.
     */
    private final int[] numCategories;
//    private final HashMap<Integer, Map<Integer, Integer>> attestedCategories;
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
    private DiscreteBicScoreAdTree() {
        throw new UnsupportedOperationException();
    }

    /**
     * Constructs the score using a dataset.
     *
     * @param dataSet The discrete dataset to analyze.
     */
    public DiscreteBicScoreAdTree(DataSet dataSet) {
        if (dataSet == null) {
            throw new NullPointerException("Data was not provided.");
        }

        this.dataSet = dataSet;
        this.variables = new ArrayList<>(dataSet.getVariables());

        List<Node> variables = dataSet.getVariables();
        this.numCategories = new int[variables.size()];
        for (int i = 0; i < variables.size(); i++) {
            this.numCategories[i] = getVariable(i).getNumCategories();
        }

        sampleSize = dataSet.getNumRows();
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns the score of the given nodes given its parents.
     */
    @Override
    public double localScore(int node, int[] parents) {
        List<DiscreteVariable> vars = new ArrayList<>();
        for (int parent : parents) {
            vars.add(getVariable(parent));
        }
        vars.add(getVariable(node));

        int[] testIndices = new int[parents.length + 1];
        System.arraycopy(parents, 0, testIndices, 0, parents.length);
        testIndices[parents.length] = node;

        CellTableAdTree cellTable = new CellTableAdTree(dataSet, testIndices, null);

        // Number of categories for node.
        int c = numCategories[node];

        // Numbers of categories of parents.
        int[] dims = new int[parents.length];

        for (int p = 0; p < parents.length; p++) {
            dims[p] = numCategories[parents[p]];
        }

        // Number of parent states.
        int r = 1;

        for (int p = 0; p < parents.length; p++) {
            r *= dims[p];
        }

        //Finally, compute the score
        double lik = 0.0;

        for (int rowIndex = 0; rowIndex < r; rowIndex++) {
            int rowCount = 0;

            int[] coords = getCoords(rowIndex, parents, -1);
            rowCount += cellTable.calcMargin(coords);

            if (rowCount == 0) continue;

            for (int childValue = 0; childValue < c; childValue++) {
                int[] coords2 = getCoords(rowIndex, parents, c);
                int cellCount = cellTable.getValue(coords2);

                if (cellCount == 0) continue;

                lik += cellCount * FastMath.log(cellCount / (double) rowCount);
            }
        }

        int params = r * (c - 1);

        double score = 2 * lik - this.penaltyDiscount * params * FastMath.log(sampleSize) + 2 * getPriorForStructure(parents.length);

        if (Double.isNaN(score) || Double.isInfinite(score)) {
            return Double.NaN;
        } else {
            return score;
        }
    }

    private int[] getCoords(int rowIndex, int[] dims, int j) {
        int[] coords = new int[dims.length + 1];
        int[] parentValues = getParentValues(dims, rowIndex);
        System.arraycopy(parentValues, 0, coords, 0, dims.length);
        coords[dims.length] = j;
        return coords;
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

    /**
     * Returns an integer array containing the parent values for a given node index and row index.
     *
     * @param rowIndex the index of the row in question.
     * @return an integer array containing the parent values.
     */
    public int[] getParentValues(int[] parents, int rowIndex) {
        int[] dims = new int[parents.length];
        for (int i = 0; i < parents.length; i++) {
            dims[i] = this.numCategories[parents[i]];
        }

        int[] values = new int[parents.length];

        for (int i = dims.length - 1; i >= 0; i--) {
            values[i] = rowIndex % dims[i];
            rowIndex /= dims[i];
        }

        return values;
    }
}



