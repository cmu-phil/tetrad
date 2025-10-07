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

package edu.cmu.tetrad.search.is;

import edu.cmu.tetrad.data.BoxDataSet;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.IndependenceFact;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.test.IndependenceResult;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.util.RandomUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.math3.special.Gamma;

/**
 * Calculates probabilities of independence for conditional independence facts.
 * This version implements the algorithm in Fattaneh Jabbari's PhD dissertation (Pages: 36-37)
 */
public class IndTestProbabilisticBDeu implements IndependenceTest {
    private final BDeuScoreWOprior score;
    private final int[] nodeDimensions;
    private boolean threshold = false;

    /**
     * The data set for which conditional  independence judgments are requested.
     */
    private final DataSet data;
    public Graph gold;
    /**
     * The nodes of the data set.
     */
    private final List<Node> nodes;

    /**
     * Indices of the nodes.
     */
    private final Map<Node, Integer> indices;

    /**
     * A map from independence facts to their probabilities of independence.
     */
    private final Map<IndependenceFact, Double> H;
    private double posterior;
    private boolean verbose = false;

    private double cutoff = 0.5;
    private final int[][] data_array;
    private double prior = 0.5;
    double samplePrior = 1.0;

    //==========================CONSTRUCTORS=============================//
    /**
     * Initializes the test using a discrete data sets.
     */
    public IndTestProbabilisticBDeu(DataSet dataSet, double prior) {
        if (!dataSet.isDiscrete()) {
            throw new IllegalArgumentException("Not a discrete data set.");

        }
        this.prior  = prior;
        this.data = dataSet;
        this.data_array = new int[dataSet.getNumRows()][dataSet.getNumColumns()];

        for (int i = 0; i < dataSet.getNumRows(); i++) {
            for (int j = 0; j < dataSet.getNumColumns(); j++) {
                this.data_array[i][j] = dataSet.getInt(i, j);
            }
        }

        this.nodeDimensions = new int[dataSet.getNumColumns()];

        for (int j = 0; j < dataSet.getNumColumns(); j++) {
            DiscreteVariable variable = (DiscreteVariable) (dataSet.getVariable(j));
            int numCategories = variable.getNumCategories();
            nodeDimensions[j] = numCategories;
        }

        this.score = new BDeuScoreWOprior(this.data);

        nodes = dataSet.getVariables();

        indices = new HashMap<>();

        for (int i = 0; i < nodes.size(); i++) {
            indices.put(nodes.get(i), i);
        }

        this.H = new HashMap<>();
    }

    @Override
    public IndependenceTest indTestSubset(List<Node> vars) {
        throw new UnsupportedOperationException();
    }


    @Override
    public IndependenceResult checkIndependence(Node x, Node y, Set<Node> z) throws InterruptedException {
        Node[] _z = z.toArray(new Node[0]);
        return checkIndependence(x, y, _z);
    }

    @Override
    public IndependenceResult checkIndependence(Node x, Node y, Node... z) {
        IndependenceFact key = new IndependenceFact(x, y, z);

        double pInd = Double.NaN;

        if (!H.containsKey(key)) {
            pInd = computeInd(x, y, z);
            H.put(key, pInd);
        }
        else {
            pInd = H.get(key);
        }

        double p = pInd;

        this.posterior = p;

        boolean ind ;
        if (this.threshold){
            ind = (p >= cutoff);
        } else{
            ind = RandomUtil.getInstance().nextDouble() < p;
        }

        if (ind) {
            return new IndependenceResult(new IndependenceFact(x, y, z), true, p, Double.NaN);
        } else {
            return new IndependenceResult(new IndependenceFact(x, y, z), false, p, Double.NaN);
        }
    }

    public double computeInd(Node x, Node y, Node... z) {
        double p_ind = Double.NaN, d_ind = 0.0, d_all = 0.0, d_dep = 0.0;

        List<Node> z_list = new ArrayList<>();
        Collections.addAll(z_list, z);

        int _x = this.indices.get(x);
        int _y = this.indices.get(y);
        int[] _z = new int[z.length];
        int [] _xz = new int[_z.length + 1];
        int r = 1;
        for (int i = 0; i < z.length; i++) {
            _z[i] = this.indices.get(z[i]);
            _xz[i] = _z[i];
            r *= this.nodeDimensions[_z[i]];
        }

        _xz[_z.length] = _x;
        int r2 = r * this.nodeDimensions[_x];

        double cellPrior_xz = this.score.getSamplePrior() / (this.nodeDimensions[_x] * r);
        double cellPrior_yz = this.score.getSamplePrior() / (this.nodeDimensions[_y] * r);
        double cellPrior_yxz = this.score.getSamplePrior() / (this.nodeDimensions[_y] * r2);

        double rowPrior_xz = this.score.getSamplePrior() / (r);
        double rowPrior_yz = this.score.getSamplePrior() / (r);
        double rowPrior_yxz = this.score.getSamplePrior() / (r2);

        double priorInd,  priorDep;
        priorInd = Math.log(this.prior) / r;
        priorDep = Math.log(1.0 - Math.exp(priorInd));

        for (int j = 0; j < r; j++) {
            double p_xz = 0.0, p_yz = 0.0, p_yxz = 0.0;
            int[] _z_dim = getDimension(_z);
            int[] _z_values = getParentValues(_y, j, _z_dim);

            // split the data based on _z_values of _z
            DataSet data_is = new BoxDataSet((BoxDataSet) this.data);
            splitData(data_is, _z, _z_values);

            BDeuScoreWOprior score_z = new BDeuScoreWOprior(data_is);
            score_z.setSamplePrior(this.samplePrior);
            BDeuScoreWOprior.CountObjects counts_xz = score_z.localCounts(_x, _z);
            BDeuScoreWOprior.CountObjects counts_yz = score_z.localCounts(_y, _z);
            BDeuScoreWOprior.CountObjects counts_yxz = score_z.localCounts(_y, _xz);

            // compute p_{D_x|z}
            int [] n_j = counts_xz.n_j;
            int [][] n_jk = counts_xz.n_jk;
            p_xz -= Gamma.logGamma(rowPrior_xz + n_j[j]);
            p_xz += Gamma.logGamma(rowPrior_xz);
            for (int k = 0; k < this.nodeDimensions[_x]; k++) {
                p_xz += Gamma.logGamma(cellPrior_xz + n_jk[j][k]);
                p_xz -= Gamma.logGamma(cellPrior_xz);
            }

            // compute p_{D_y|z}
            n_j = counts_yz.n_j;
            n_jk = counts_yz.n_jk;
            p_yz -= Gamma.logGamma(rowPrior_yz + n_j[j]);
            p_yz += Gamma.logGamma(rowPrior_yz);
            for (int k = 0; k < this.nodeDimensions[_y]; k++) {
                p_yz += Gamma.logGamma(cellPrior_yz + n_jk[j][k]);
                p_yz -= Gamma.logGamma(cellPrior_yz);
            }

            // compute p_{D_y|x,z}
            n_j = counts_yxz.n_j;
            n_jk = counts_yxz.n_jk;

            int[] _xz_values = new int[_z_values.length + 1];
            int[] _xz_dim = new int[_z_dim.length + 1];
            for (int v = 0; v < _z.length; v++){
                _xz_values[v] = _z_values[v];
                _xz_dim[v] = _z_dim[v];
            }
            _xz_dim[_z_dim.length] = this.nodeDimensions[_x];

            for (int j2 = 0; j2 < this.nodeDimensions[_x]; j2++) {
                _xz_values[_z.length] = j2;
                int rowIndex = getRowIndex(_xz_dim, _xz_values);
                p_yxz-= Gamma.logGamma(rowPrior_yxz + n_j[rowIndex]);
                p_yxz += Gamma.logGamma(rowPrior_yxz);
                for (int k = 0; k < this.nodeDimensions[_y]; k++) {
                    p_yxz += Gamma.logGamma(cellPrior_yxz + n_jk[rowIndex][k]);
                    p_yxz -= Gamma.logGamma(cellPrior_yxz);
                }
            }

            d_ind += priorInd + (p_xz + p_yz);
            d_dep += priorDep + (p_xz + p_yxz);
            d_all += lnXpluslnY (priorInd + (p_xz + p_yz), priorDep + (p_xz + p_yxz));
        }


        p_ind = Math.exp(d_ind - d_all);
        return p_ind;
    }

    public int[] getParentValues(int nodeIndex, int rowIndex, int[] dims) {
        int[] values = new int[dims.length];

        for (int i = dims.length - 1; i >= 0; i--) {
            values[i] = rowIndex % dims[i];
            rowIndex /= dims[i];
        }

        return values;
    }
    public int getRowIndex(int[] dim, int[] values) {
        int rowIndex = 0;
        for (int i = 0; i < dim.length; i++) {
            rowIndex *= dim[i];
            rowIndex += values[i];
        }
        return rowIndex;
    }
    private int[] getDimension(int[] parents) {
        int[] dims = new int[parents.length];

        for (int p = 0; p < parents.length; p++) {
            dims[p] = this.nodeDimensions[parents[p]];
        }
        return dims;
    }

    public void setSampleprior(double samplePrior) {
        this.samplePrior  = samplePrior;
    }

    private void splitData(DataSet data_xy, int[] parents, int[] parentValues){
        int sampleSize = data.getNumRows();

        Set<Integer> rows_is = new HashSet<>();
        Set<Integer> rows_res = new HashSet<>();

        for (int i = 0; i < data.getNumRows(); i++){
            rows_res.add(i);
        }

        for (int i = 0; i < sampleSize; i++){
            int[] parentValuesCase = new int[parents.length];

            for (int p = 0; p < parents.length ; p++){
                parentValuesCase[p] = data_array[i][parents[p]];
            }

            if (Arrays.equals(parentValuesCase, parentValues)){
                rows_is.add(i);
                rows_res.remove(i);
            }
        }

        int[] is_array = new int[rows_is.size()];
        int c = 0;
        for(int row : rows_is) is_array[c++] = row;

        int[] res_array = new int[rows_res.size()];
        c = 0;
        for(int row : rows_res) res_array[c++] = row;

        Arrays.sort(is_array);
        Arrays.sort(res_array);

        data_xy.removeRows(res_array);

    }


    /**
     * Takes ln(x) and ln(y) as input, and returns ln(x + y)
     *
     * @param lnX is natural log of x
     * @param lnY is natural log of y
     * @return natural log of x plus y
     */
    private static final int MININUM_EXPONENT = -1022;
    protected double lnXpluslnY(double lnX, double lnY) {
        double lnYminusLnX, temp;

        if (lnY > lnX) {
            temp = lnX;
            lnX = lnY;
            lnY = temp;
        }

        lnYminusLnX = lnY - lnX;

        if (lnYminusLnX < MININUM_EXPONENT) {
            return lnX;
        } else {
            return Math.log1p(Math.exp(lnYminusLnX)) + lnX;
        }
    }

    @Override
    public List<Node> getVariables() {
        return nodes;
    }

    @Override
    public Node getVariable(String name) {
        for (Node node : nodes) {
            if (name.equals(node.getName())) return node;
        }

        return null;
    }

    @Override
    public List<String> getVariableNames() {
        List<String> names = new ArrayList<>();

        for (Node node : nodes) {
            names.add(node.getName());
        }
        return names;
    }

    @Override
    public boolean determines(Set<Node> z, Node y) {
        return IndependenceTest.super.determines(z, y);
    }

    @Override
    public double getAlpha() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setAlpha(double alpha) {
        throw new UnsupportedOperationException();
    }
    public void setGoldStandard(Graph gs) {
        gs = GraphUtils.replaceNodes(gs, this.data.getVariables());
        this.gold = gs;
    }

    @Override
    public DataModel getData() {
        return data;
    }

    @Override
    public ICovarianceMatrix getCov() {
        return null;
    }

    @Override
    public List<DataSet> getDataSets() {
        return null;
    }

    @Override
    public int getSampleSize() {
        return 0;
    }

    public Map<IndependenceFact, Double> getH() {
        return new HashMap<>(H);
    }

    public double getPosterior() {
        return posterior;
    }

    @Override
    public boolean isVerbose() {
        return verbose;
    }

    @Override
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    /**
     * @param noRandomizedGeneratingConstraints
     */
    public void setThreshold(boolean noRandomizedGeneratingConstraints) {
        this.threshold = noRandomizedGeneratingConstraints;
    }

    public void setCutoff(double cutoff) {
        this.cutoff = cutoff;
    }
}