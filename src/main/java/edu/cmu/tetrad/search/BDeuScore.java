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

package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.ProbUtils;
import edu.cmu.tetrad.data.BoxDataSet;
import edu.cmu.tetrad.data.DataBox;
import edu.cmu.tetrad.data.VerticalIntDataBox;

import java.util.List;

/**
 * Calculates the BDeu score.
 */
public class BDeuScore implements LocalDiscreteScore, GesScore {
    private List<Node> variables;
    private int[][] data;
    //    private final LocalScoreCache localScoreCache = new LocalScoreCache();
    private int sampleSize;

    private double samplePrior = 1;
    private double structurePrior = 1;

    private int[] numCategories;

    private double lastBumpThreshold = 0.0;

    public BDeuScore(DataSet dataSet) {
        if (dataSet == null) {
            throw new NullPointerException();
        }

        if (dataSet instanceof BoxDataSet) {
            DataBox dataBox = ((BoxDataSet) dataSet).getDataBox();

            this.variables = dataSet.getVariables();

            if (!(((BoxDataSet) dataSet).getDataBox() instanceof VerticalIntDataBox)) {
                throw new IllegalArgumentException();
            }

            VerticalIntDataBox box = (VerticalIntDataBox) dataBox;

            data = box.getVariableVectors();
            this.sampleSize = dataSet.getNumRows();
        } else {
            data = new int[dataSet.getNumColumns()][];
            this.variables = dataSet.getVariables();

            for (int j = 0; j < dataSet.getNumColumns(); j++) {
                data[j] = new int[dataSet.getNumRows()];

                for (int i = 0; i < dataSet.getNumRows(); i++) {
                    data[j][i] = dataSet.getInt(i, j);
                }
            }

            this.sampleSize = dataSet.getNumRows();
        }

        final List<Node> variables = dataSet.getVariables();
        numCategories = new int[variables.size()];
        for (int i = 0; i < variables.size(); i++) {
            numCategories[i] = (getVariable(i)).getNumCategories();
        }
    }

    private DiscreteVariable getVariable(int i) {
        return (DiscreteVariable) variables.get(i);
    }

    @Override
    public double localScore(int node, int parents[]) {

        // Number of categories for node.
        int r = numCategories[node];

        // Numbers of categories of parents.
        int[] dims = new int[parents.length];

        for (int p = 0; p < parents.length; p++) {
            dims[p] = numCategories[parents[p]];
        }

        // Number of parent states.
        int q = 1;

        for (int p = 0; p < parents.length; p++) {
            q *= dims[p];
        }

        // Conditional cell coefs of data for node given parents(node).
        int n_jk[][] = new int[q][r];
        int n_j[] = new int[q];

        int[] parentValues = new int[parents.length];

        int[][] myParents = new int[parents.length][];
        for (int i = 0; i < parents.length; i++) {
            myParents[i] = data[parents[i]];
        }

        int[] myChild = data[node];

        for (int i = 0; i < sampleSize; i++) {
            for (int p = 0; p < parents.length; p++) {
                parentValues[p] = myParents[p][i];
            }

            int childValue = myChild[i];

            if (childValue == -99) {
                throw new IllegalStateException("Please remove or impute missing " +
                        "values (record " + i + " column " + i + ")");
            }

            int rowIndex = getRowIndex(dims, parentValues);

            n_jk[rowIndex][childValue]++;
            n_j[rowIndex]++;
        }

        //Finally, compute the score
        double score = 0.0;

        score += (r - 1) * q * Math.log(getStructurePrior());

        final double cellPrior = getSamplePrior() / (r * q);
        final double rowPrior = getSamplePrior() / q;

        for (int j = 0; j < q; j++) {
            score -= ProbUtils.lngamma(rowPrior + n_j[j]);

            for (int k = 0; k < r; k++) {
                score += ProbUtils.lngamma(cellPrior + n_jk[j][k]);
            }
        }

        score += q * ProbUtils.lngamma(rowPrior);
        score -= r * q * ProbUtils.lngamma(cellPrior);

        lastBumpThreshold = ((r - 1) * q * Math.log(getStructurePrior()));

        return score;
    }

    @Override
    public double localScoreDiff(int i, int[] parents, int extra) {
        return localScore(i, append(parents, extra)) - localScore(i, parents);
    }

    int[] append(int[] parents, int extra) {
        int[] all = new int[parents.length + 1];
        System.arraycopy(parents, 0, all, 0, parents.length);
        all[parents.length] = extra;
        return all;
    }

    @Override
    public double localScore(int node, int parent) {

        // Number of categories for node.
        int r = numCategories[node];

        // Numbers of categories of parents.
        int q = numCategories[parent];

        // Conditional cell coefs of data for node given parents(node).
        int n_jk[][] = new int[q][r];
        int n_j[] = new int[q];

        int[] parentData = data[parent];
        int[] childData = data[node];

        for (int i = 0; i < sampleSize; i++) {
            int parentValue = parentData[i];
            int childValue = childData[i];
            n_jk[parentValue][childValue]++;
            n_j[parentValue]++;
        }

        //Finally, compute the score
        double score = 0.0;

        score += (r - 1) * q * Math.log(getStructurePrior());

        final double cellPrior = getSamplePrior() / (r * q);
        final double rowPrior = getSamplePrior() / q;

        for (int j = 0; j < q; j++) {
            score -= ProbUtils.lngamma(rowPrior + n_j[j]);

            for (int k = 0; k < r; k++) {
                score += ProbUtils.lngamma(cellPrior + n_jk[j][k]);
            }
        }

        score += q * ProbUtils.lngamma(rowPrior);
        score -= r * q * ProbUtils.lngamma(cellPrior);

        lastBumpThreshold = ((r - 1) * q * Math.log(getStructurePrior()));

        return score;
    }

    @Override
    public double localScore(int node) {

        // Number of categories for node.
        int r = numCategories[node];

        // Conditional cell coefs of data for node given parents(node).
        int n_jk[] = new int[numCategories[node]];
        int n_j = 0;

        int[] childData = data[node];

        for (int i = 0; i < sampleSize; i++) {
            int childValue = childData[i];
            n_jk[childValue]++;
            n_j++;
        }

        //Finally, compute the score
        double score = 0.0;

        score += (r - 1) * Math.log(getStructurePrior());

        final double cellPrior = getSamplePrior() / r;
        final double rowPrior = getSamplePrior();

        score -= ProbUtils.lngamma(rowPrior + n_j);

        for (int k = 0; k < r; k++) {
            score += ProbUtils.lngamma(cellPrior + n_jk[k]);
        }

        score += ProbUtils.lngamma(rowPrior);
        score -= r * ProbUtils.lngamma(cellPrior);

        lastBumpThreshold = ((r - 1) * Math.log(getStructurePrior()));

        return score;
    }

    @Override
    public List<Node> getVariables() {
        return this.variables;
    }

    @Override
    public int getSampleSize() {
        return sampleSize;
    }

    @Override
    /**
     * Must be called directly after the corresponding scoring call.
     */
    public boolean isEffectEdge(double bump) {
        return bump > lastBumpThreshold;
    }

    @Override
    public boolean isDiscrete() {
        return true;
    }

    @Override
    public DataSet getDataSet() {
        throw new UnsupportedOperationException();
    }

    private static int getRowIndex(int[] dim, int[] values) {
        int rowIndex = 0;
        for (int i = 0; i < dim.length; i++) {
            rowIndex *= dim[i];
            rowIndex += values[i];
        }
        return rowIndex;
    }

    public double getStructurePrior() {
        return structurePrior;
    }

    public double getSamplePrior() {
        return samplePrior;
    }

    public void setStructurePrior(double structurePrior) {
        this.structurePrior = structurePrior;
    }

    public void setSamplePrior(double samplePrior) {
        this.samplePrior = samplePrior;
    }
}



