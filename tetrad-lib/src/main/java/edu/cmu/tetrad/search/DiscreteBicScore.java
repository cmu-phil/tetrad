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

package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.Node;
import org.apache.commons.math3.util.FastMath;

import javax.help.UnsupportedOperationException;
import java.util.List;

import static org.apache.commons.math3.util.FastMath.abs;
import static org.apache.commons.math3.util.FastMath.log;

/**
 * Calculates the discrete BIC score.
 *
 * @author josephramsey
 */
public class DiscreteBicScore implements DiscreteScore {
    private final DataSet dataSet;
    private List<Node> variables;
    private final int[][] data;
    private final int sampleSize;
    private double penaltyDiscount = 1;
    private final int[] numCategories;
    private double structurePrior = 1;

    /**
     * Constructor.
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
    }

    /**
     * Returns the score of the given nodes given its parents.
     *
     * @param node    The index of the node.
     * @param parents The indices of the node's parents.
     * @return The score.
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

            int rowIndex = DiscreteBicScore.getRowIndex(dims, parentValues);

            n_jk[rowIndex][childValue]++;
            n_j[rowIndex]++;
            N++;
        }

        //Finally, compute the score
        double lik = 0.0;

        for (int rowIndex = 0; rowIndex < r; rowIndex++) {
            for (int childValue = 0; childValue < c; childValue++) {
                int cellCount = n_jk[rowIndex][childValue];
                int rowCount = n_j[rowIndex];

                if (cellCount == 0) continue;
                lik += cellCount * FastMath.log(cellCount / (double) rowCount);
            }
        }

        int params = r * (c - 1);

        double score = 2 * lik - this.penaltyDiscount * params * FastMath.log(N) + 2 * getPriorForStructure(parents.length);

        if (Double.isNaN(score) || Double.isInfinite(score)) {
            return Double.NaN;
        } else {
            return score;
        }
    }

    /**
     * Returns localScore(y | z, x) - localScore(y | z).
     *
     * @return This score.
     */
    @Override
    public double localScoreDiff(int x, int y, int[] z) {
        return localScore(y, append(z, x)) - localScore(y, z);
    }

    /**
     * Returns the variables.
     *
     * @return This list.
     */
    @Override
    public List<Node> getVariables() {
        return this.variables;
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
     * Must be called directly after the corresponding scoring call. Used in FGES.
     *
     * @param bump The score bump.
     * @see Fges
     */
    public boolean isEffectEdge(double bump) {
        return bump > 0;//lastBumpThreshold;
    }

    /**
     * Returns the dataset being analyzed.
     *
     * @return this dataset.
     */
    @Override
    public DataSet getDataSet() {
        return this.dataSet;
    }

    /**
     * Sets the structure prior.
     *
     * @param structurePrior This prior.
     */
    @Override
    public void setStructurePrior(double structurePrior) {
        this.structurePrior = structurePrior;
    }

    /**
     * This method is not used for this score.
     *
     * @throws UnsupportedOperationException Since this method is not used.
     */
    @Override
    public void setSamplePrior(double samplePrior) {
        throw new UnsupportedOperationException("This method is not used.");
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
     * Sets the penalty discount, which is a multiplier on the penalty term of BIC.
     *
     * @param penaltyDiscount This discount.
     */
    public void setPenaltyDiscount(double penaltyDiscount) {
        this.penaltyDiscount = penaltyDiscount;
    }

    /**
     * Returns the maximum degree for some algorithms.
     *
     * @return 1000.
     */
    @Override
    public int getMaxDegree() {
        return 1000;
    }

    /**
     * This method is not used; a method for calculating whether nodes Z determind node y has not
     * been implemented here.
     *
     * @throws UnsupportedOperationException Since this method is not used.
     */
    @Override
    public boolean determines(List<Node> z, Node y) {
        throw new UnsupportedOperationException("This method is not used.");
    }

    /**
     * Returns a string representation of this score.
     *
     * @return This string.
     */
    @Override
    public String toString() {
        return "BIC Score";
    }

    private static int getRowIndex(int[] dim, int[] values) {
        int rowIndex = 0;
        for (int i = 0; i < dim.length; i++) {
            rowIndex *= dim[i];
            rowIndex += values[i];
        }
        return rowIndex;
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



