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
import org.apache.commons.math3.special.Gamma;

import java.util.List;

/**
 * Calculates the BDeu score.
 */
public class BDeuScore implements LocalDiscreteScore, IBDeuScore, Score {
    private List<Node> variables;
    private int[][] data;
    private int sampleSize;

    private double samplePrior = 1;
    private double structurePrior = 1;

    private int[] numCategories;

    public BDeuScore(DataSet dataSet) {
        if (dataSet == null) {
            throw new NullPointerException("Data was not provided.");
        }

        if (dataSet instanceof BoxDataSet) {
            DataBox dataBox = ((BoxDataSet) dataSet).getDataBox();

            this.variables = dataSet.getVariables();

            if (!(dataBox instanceof VerticalIntDataBox)) {
                dataBox = new VerticalIntDataBox(dataBox);
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

        // Conditional cell coefs of data for node given parents(node).
        int n_jk[][] = new int[r][c];
        int n_j[] = new int[r];

        int[] parentValues = new int[parents.length];

        int[][] myParents = new int[parents.length][];
        for (int i = 0; i < parents.length; i++) {
            myParents[i] = data[parents[i]];
        }

        int[] myChild = data[node];


        ROW:
        for (int i = 0; i < sampleSize; i++) {
            for (int p = 0; p < parents.length; p++) {
                if (myParents[p][i] == -99) continue ROW;
                parentValues[p] = myParents[p][i];
            }

            int childValue = myChild[i];

            if (childValue == -99) {
                continue ROW;
//                throw new IllegalStateException("Please remove or impute missing " +
//                        "values (record " + i + " column " + i + ")");
            }

            int rowIndex = getRowIndex(dims, parentValues);

            n_jk[rowIndex][childValue]++;
            n_j[rowIndex]++;
        }

        //Finally, compute the score
        double score = 0.0;

        score += getPriorForStructure(parents.length);

        final double cellPrior = getSamplePrior() / (c * r);
        final double rowPrior = getSamplePrior() / r;

        for (int j = 0; j < r; j++) {
            score -= Gamma.logGamma(rowPrior + n_j[j]);

            for (int k = 0; k < c; k++) {
                score += Gamma.logGamma(cellPrior + n_jk[j][k]);
            }
        }

        score += r * Gamma.logGamma(rowPrior);
        score -= c * r * Gamma.logGamma(cellPrior);

        return score;
    }

    private double getPriorForStructure(int numParents) {
        double e = getStructurePrior();
        int vm = data.length - 1;
        return numParents * Math.log(e / (vm)) + (vm - numParents) * Math.log(1.0 - (e / (vm)));
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
    }

    @Override
    public double localScore(int node) {
        return localScore(node, new int[0]);
    }

    @Override
    public List<Node> getVariables() {
        return this.variables;
    }

    public int getSampleSize() {
        return sampleSize;
    }

    /**
     * Must be called directly after the corresponding scoring call.
     */
    public boolean isEffectEdge(double bump) {
        return bump > 0;//lastBumpThreshold;
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

    @Override
    public double getStructurePrior() {
        return structurePrior;
    }

    @Override
    public double getSamplePrior() {
        return samplePrior;
    }

    @Override
    public void setStructurePrior(double structurePrior) {
        this.structurePrior = structurePrior;
    }

    @Override
    public void setSamplePrior(double samplePrior) {
        this.samplePrior = samplePrior;
    }

    public void setVariables(List<Node> variables) {
        for (int i = 0; i < variables.size(); i++) {
            if (!variables.get(i).getName().equals(this.variables.get(i).getName())) {
                throw new IllegalArgumentException("Variable in index " + (i + 1) + " does not have the same name " +
                        "as the variable being substituted for it.");
            }
        }

        this.variables = variables;
    }

    public Node getVariable(String targetName) {
        for (Node node : variables) {
            if (node.getName().equals(targetName)) {
                return node;
            }
        }

        return null;
    }

    @Override
    public int getMaxDegree() {
        return (int) Math.ceil(Math.log(sampleSize));
    }

    @Override
    public boolean determines(List<Node> z, Node y) {
        return false;
    }
}



