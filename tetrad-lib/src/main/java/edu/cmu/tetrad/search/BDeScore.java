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

import java.util.List;

/**
 * Calculates the BDe score.
 */
public class BDeScore implements LocalDiscreteScore {
    private DataSet dataSet;

    public BDeScore(DataSet dataSet) {
        if (dataSet == null) {
            throw new NullPointerException();
        }

        if (!dataSet.isDiscrete()) {
            throw new IllegalArgumentException("Need a discrete data set.");
        }

        this.dataSet = dataSet;
    }

    public double localScore(int i, int parents[]) {

        // Number of categories for i.
        int r = numCategories(i);

        // Numbers of categories of parents.
        int dims[] = new int[parents.length];

        for (int p = 0; p < parents.length; p++) {
            dims[p] = numCategories(parents[p]);
        }

        // Number of parent states.
        int q = 1;
        for (int p = 0; p < parents.length; p++) {
            q *= dims[p];
        }

        // Conditional cell coefs of data for i given parents(i).
        int n_ijk[][] = new int[q][r];
        int n_ij[] = new int[q];

        int values[] = new int[parents.length];

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

            for (int m = 0; m < dataSet().getMultiplier(n); m++) {
                n_ijk[rowIndex][childValue]++;
            }
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
                score += ProbUtils.lngamma(n_ijk[j][k] + nPrimeijk);
                score -= ProbUtils.lngamma(nPrimeijk);
            }

            double nPrimeij = 1. / q;

            score += ProbUtils.lngamma(nPrimeij);
            score -= ProbUtils.lngamma(n_ij[j] + nPrimeij);
        }

        return score;
    }

    @Override
    public double localScoreDiff(int x, int y, int[] z) {
        return localScore(y, append(z, x)) - localScore(y, z);
    }

    int[] append(int[] parents, int extra) {
        int[] all = new int[parents.length + 1];
        System.arraycopy(parents, 0, all, 0, parents.length);
        all[parents.length] = extra;
        return all;
    }

    @Override
    public double localScore(int i, int parent) {
        return localScore(i, new int[]{parent});
    }

    @Override
    public double localScore(int i) {
        return localScore(i, new int[0]);
    }

    public DataSet getDataSet() {
        return dataSet;
    }

    private int getRowIndex(int[] dim, int[] values) {
        int rowIndex = 0;
        for (int i = 0; i < dim.length; i++) {
            rowIndex *= dim[i];
            rowIndex += values[i];
        }
        return rowIndex;
    }

    private int sampleSize() {
        return dataSet().getNumRows();
    }

    private int numCategories(int i) {
        return ((DiscreteVariable) dataSet().getVariable(i)).getNumCategories();
    }

    private DataSet dataSet() {
        return dataSet;
    }

    public void setStructurePrior(double structurePrior) {
    }

    public void setSamplePrior(double samplePrior) {
    }

    @Override
    public List<Node> getVariables() {
        return dataSet.getVariables();
    }

    public int getSampleSize() {
        return dataSet.getNumRows();
    }

    @Override
    public boolean isEffectEdge(double bump) {
        return bump > -20;
    }

    @Override
    public boolean isDiscrete() {
        return true;
    }

    @Override
    public double getParameter1() {
        return 0;
    }

    @Override
    public void setParameter1(double alpha) {

    }
}


