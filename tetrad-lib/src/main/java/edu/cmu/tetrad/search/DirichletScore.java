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

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.Node;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.List;

/**
 * Calculates the BDeu score.
 */
public class DirichletScore implements LocalDiscreteScore {
    private final List<Node> variables;
    private final int[][] data;
    private final int sampleSize;

    private double samplePrior = 1;
    private double structurePrior = 1;

    private final int[] numCategories;

    private double lastBumpThreshold;

    public DirichletScore(DataSet dataSet) {
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

            this.data = box.getVariableVectors();
            this.sampleSize = dataSet.getNumRows();
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

    private DiscreteVariable getVariable(int i) {
        return (DiscreteVariable) this.variables.get(i);
    }

    @Override
    public double localScore(int node, int[] parents) {

        // Number of categories for node.
        int r = this.numCategories[node];

        // Numbers of categories of parents.
        int[] dims = new int[parents.length];

        for (int p = 0; p < parents.length; p++) {
            dims[p] = this.numCategories[parents[p]];
        }

        // Number of parent states.
        int q = 1;

        for (int p = 0; p < parents.length; p++) {
            q *= dims[p];
        }

        // Conditional cell coefs of data for node given parents(node).
        int[][] n_jk = new int[q][r];
        int[] n_j = new int[q];

        int[] parentValues = new int[parents.length];

        int[][] myParents = new int[parents.length][];
        for (int i = 0; i < parents.length; i++) {
            myParents[i] = this.data[parents[i]];
        }

        int[] myChild = this.data[node];

        for (int i = 0; i < this.sampleSize; i++) {
            for (int p = 0; p < parents.length; p++) {
                parentValues[p] = myParents[p][i];
            }

            int childValue = myChild[i];

            if (childValue == -99) {
                throw new IllegalStateException("Please remove or impute missing " +
                        "values (record " + i + " column " + i + ")");
            }

            int rowIndex = DirichletScore.getRowIndex(dims, parentValues);

            n_jk[rowIndex][childValue]++;
            n_j[rowIndex]++;
        }

        //Finally, compute the score
        double score = 0.0;

        double cellPrior = getSamplePrior();
        double rowPrior = r * getSamplePrior();

        for (int j = 0; j < q; j++) {
            double rowSum = rowPrior + n_j[j];
            int cellCount = 0;
            double rowScore = 0;

            for (int k = 0; k < r; k++) {
                double alpha = cellPrior + n_jk[j][k];
                double pk = (alpha) / rowSum;
                if (Double.isInfinite(pk)) continue;
                double _score = (alpha - 1) * Math.log(pk);
                rowScore += _score;
                cellCount++;
            }

            if (rowScore == 0) continue;
            score += rowScore;

            score -= 2 * cellCount;

//            score -= .5 * cellCount * Math.log(sampleSize);
        }


//        double h = Math.log(1.0 / r) * sampleSize;

//        System.out.println("(1/r)^N = " + h + " score = " + score);

        this.lastBumpThreshold = 0.01;//((r - 1) * q * FastMath.log(getStructurePrior()));

        return score;
    }

    private double getPriorForStructure(int numParents) {
        double e = getStructurePrior();
        double k = numParents;
        double n = this.data.length;
        return k * Math.log(e / n) + (n - k) * Math.log(1.0 - (e / n));
    }

    @Override
    public double localScoreDiff(int x, int y, int[] z) {
        return localScore(y, append(z, x)) - localScore(y, z);
    }

    @Override
    public double localScoreDiff(int x, int y) {
        return localScore(y, x) - localScore(y);
    }

    int[] append(int[] parents, int extra) {
        int[] all = new int[parents.length + 1];
        System.arraycopy(parents, 0, all, 0, parents.length);
        all[parents.length] = extra;
        return all;
    }

    @Override
    public double localScore(int node, int parent) {
        return localScore(node, new int[]{parent});

//        // Number of categories for node.
//        int r = numCategories[node];
//
//        // Numbers of categories of parents.
//        int q = numCategories[parent];
//
//        // Conditional cell coefs of data for node given parents(node).
//        int n_jk[][] = new int[q][r];
//        int n_j[] = new int[q];
//
//        int[] parentData = data[parent];
//        int[] childData = data[node];
//
//        for (int i = 0; i < sampleSize; i++) {
//            int parentValue = parentData[i];
//            int childValue = childData[i];
//            n_jk[parentValue][childValue]++;
//            n_j[parentValue]++;
//        }
//
//        //Finally, compute the score
//        double score = r * q * Math.log(getStructurePrior());
//
//        final double cellPrior = getSamplePrior() / (r * q);
//        final double rowPrior = getSamplePrior() / q;
//
//        for (int j = 0; j < q; j++) {
//            score -= Gamma.logGamma(rowPrior + n_j[j]);
//
//            for (int k = 0; k < r; k++) {
//                score += Gamma.logGamma(cellPrior + n_jk[j][k]);
//            }
//        }
//
//        score += q * Gamma.logGamma(rowPrior);
//        score -= r * q * Gamma.logGamma(cellPrior);
//
//        lastBumpThreshold = 0.01;//((r - 1) * q * FastMath.log(getStructurePrior()));
//
//        return score;
    }

    @Override
    public double localScore(int node) {
        return localScore(node, new int[0]);

//        // Number of categories for node.
//        int r = numCategories[node];
//
//        // Conditional cell coefs of data for node given parents(node).
//        int n_jk[] = new int[numCategories[node]];
//        int n_j = 0;
//
//        int[] childData = data[node];
//
//        for (int i = 0; i < sampleSize; i++) {
//            int childValue = childData[i];
//            n_jk[childValue]++;
//            n_j++;
//        }
//
//        //Finally, compute the score
//        int q = 1;
//        double score = r * q * Math.log(getStructurePrior());
//
//        final double cellPrior = getSamplePrior() / r;
//        final double rowPrior = getSamplePrior();
//
//        score -= Gamma.logGamma(rowPrior + n_j);
//
//        for (int k = 0; k < r; k++) {
//            score += Gamma.logGamma(cellPrior + n_jk[k]);
//        }
//
//        score += Gamma.logGamma(rowPrior);
//        score -= r * Gamma.logGamma(cellPrior);
//
//        lastBumpThreshold = 0.01;//((r - 1) * q * FastMath.log(getStructurePrior()));
//
//        return score;
    }

    @Override
    public List<Node> getVariables() {
        return this.variables;
    }

    public int getSampleSize() {
        return this.sampleSize;
    }

    /**
     * Must be called directly after the corresponding scoring call.
     */
    public boolean isEffectEdge(double bump) {
        return bump > this.lastBumpThreshold;
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
        return this.structurePrior;
    }

    public double getSamplePrior() {
        return this.samplePrior;
    }

    public void setStructurePrior(double structurePrior) {
        this.structurePrior = structurePrior;
    }

    public void setSamplePrior(double samplePrior) {
        this.samplePrior = samplePrior;
    }

    @Override
    public Node getVariable(String targetName) {
        for (Node node : this.variables) {
            if (node.getName().equals(targetName)) {
                return node;
            }
        }

        return null;
    }

    @Override
    public int getMaxDegree() {
        return 1000;
    }

    @Override
    public boolean determines(List<Node> z, Node y) {
        return false;
    }

    @Override
    public String toString() {
        NumberFormat nf = new DecimalFormat("0.00");
        return "Dirichlet Score StructP " + nf.format(this.structurePrior) + " SampP " + nf.format(this.samplePrior);
    }

}



