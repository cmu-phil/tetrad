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

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.graph.IndependenceFact;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.Matrix;
import edu.cmu.tetrad.util.RandomUtil;
import edu.pitt.dbmi.algo.bayesian.constraint.inference.BCInference;

import java.util.*;

/**
 * Uses BCInference by Cooper and Bui to calculate probabilistic conditional independence judgments.
 *
 * @author Joseph Ramsey 3/2014
 */
public class IndTestProbabilistic implements IndependenceTest {

    /**
     * Calculates probabilities of independence for conditional independence facts.
     */
//    private BCInference bci;
    private boolean threshold;

    /**
     * The data set for which conditional  independence judgments are requested.
     */
    private final DataSet data;

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
    private boolean verbose;

    private double cutoff = 0.5;
    private double priorEquivalentSampleSize = 10;

    private final BCInference bci;

    //==========================CONSTRUCTORS=============================//

    /**
     * Initializes the test using a discrete data sets.
     */
    public IndTestProbabilistic(final DataSet dataSet) {
        if (!dataSet.isDiscrete()) {
            throw new IllegalArgumentException("Not a discrete data set.");

        }

//        for (int i = 0; i < dataSet.getNumRows(); i++) {
//            for (int j = 0; j < dataSet.getNumColumns(); j++) {
//                if (dataSet.getInt(i, j) == -99) {
//                    throw new IllegalArgumentException("Please remove or impute missing values.");
//                }
//            }
//        }

        this.nodes = dataSet.getVariables();

        this.indices = new HashMap<>();

        for (int i = 0; i < this.nodes.size(); i++) {
            this.indices.put(this.nodes.get(i), i);
        }

        this.data = dataSet;
        this.H = new HashMap<>();

        final int[] _cols = new int[this.nodes.size()];
        for (int i = 0; i < _cols.length; i++) _cols[i] = this.indices.get(this.nodes.get(i));

        final int[] _rows = new int[dataSet.getNumRows()];
        for (int i = 0; i < dataSet.getNumRows(); i++) _rows[i] = i;

        final DataSet _data = this.data.subsetRowsColumns(_rows, _cols);

        final List<Node> nodes = _data.getVariables();

        for (int i = 0; i < nodes.size(); i++) {
            this.indices.put(nodes.get(i), i);
        }

        this.bci = setup(_data);
    }

    private BCInference setup(final DataSet dataSet) {
        final int[] nodeDimensions = new int[dataSet.getNumColumns() + 1];

        for (int j = 0; j < dataSet.getNumColumns(); j++) {
            final DiscreteVariable variable = (DiscreteVariable) (dataSet.getVariable(j));
            final int numCategories = variable.getNumCategories();
            nodeDimensions[j + 1] = numCategories;
        }

        final int[][] cases = new int[dataSet.getNumRows() + 1][dataSet.getNumColumns() + 2];

        for (int i = 0; i < dataSet.getNumRows(); i++) {
            for (int j = 0; j < dataSet.getNumColumns(); j++) {
                cases[i + 1][j + 1] = dataSet.getInt(i, j) + 1;
            }
        }

        final BCInference bci = new BCInference(cases, nodeDimensions);
        bci.setPriorEqivalentSampleSize(this.priorEquivalentSampleSize);
        return bci;
    }

    @Override
    public IndependenceTest indTestSubset(final List<Node> vars) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isIndependent(final Node x, final Node y, final List<Node> z) {
        final Node[] _z = z.toArray(new Node[0]);
        return isIndependent(x, y, _z);
    }

    @Override
    public boolean isIndependent(final Node x, final Node y, final Node... z) {
        final IndependenceFact key = new IndependenceFact(x, y, z);

        final List<Node> allVars = new ArrayList<>();
        allVars.add(x);
        allVars.add(y);
        Collections.addAll(allVars, z);

        final List<Integer> rows = getRows(this.data, allVars, this.indices);
        if (rows.isEmpty()) return true;

        final BCInference bci;
        final Map<Node, Integer> indices;

        if (rows.size() == this.data.getNumRows()) {
            bci = this.bci;
            indices = this.indices;
        } else {

            final int[] _cols = new int[allVars.size()];
            for (int i = 0; i < _cols.length; i++) _cols[i] = this.indices.get(allVars.get(i));

            final int[] _rows = new int[rows.size()];
            for (int i = 0; i < rows.size(); i++) _rows[i] = rows.get(i);

            final DataSet _data = this.data.subsetRowsColumns(_rows, _cols);

            final List<Node> nodes = _data.getVariables();

            indices = new HashMap<>();

            for (int i = 0; i < nodes.size(); i++) {
                indices.put(nodes.get(i), i);
            }

            bci = setup(_data);
        }

        final double pInd;

        if (!this.H.containsKey(key)) {
            pInd = probConstraint(bci, BCInference.OP.independent, x, y, z, indices);
            H.put(key, pInd);
        } else {
            pInd = H.get(key);
        }

        double p = pInd;

        posterior = p;

        boolean ind;
        if (threshold) {
            ind = (p >= cutoff);
        } else {
            ind = RandomUtil.getInstance().nextDouble() < p;
        }

        return ind;
    }


    public double probConstraint(BCInference bci, final BCInference.OP op, final Node x, final Node y, final Node[] z, final Map<Node, Integer> indices) {

        final int _x = indices.get(x) + 1;
        final int _y = indices.get(y) + 1;

        final int[] _z = new int[z.length + 1];
        _z[0] = z.length;
        for (int i = 0; i < z.length; i++) {
            _z[i + 1] = indices.get(z[i]) + 1;
        }

        return bci.probConstraint(op, _x, _y, _z);
    }

    @Override
    public boolean isDependent(final Node x, final Node y, final List<Node> z) {
        final Node[] _z = z.toArray(new Node[0]);
        return !isIndependent(x, y, _z);
    }

    @Override
    public boolean isDependent(final Node x, final Node y, final Node... z) {
        return !isIndependent(x, y, z);
    }

    @Override
    public double getPValue() {
        return this.posterior;
    }

    @Override
    public List<Node> getVariables() {
        return this.nodes;
    }

    @Override
    public Node getVariable(final String name) {
        for (final Node node : this.nodes) {
            if (name.equals(node.getName())) return node;
        }

        return null;
    }

    @Override
    public List<String> getVariableNames() {
        final List<String> names = new ArrayList<>();

        for (final Node node : this.nodes) {
            names.add(node.getName());
        }
        return names;
    }

    @Override
    public boolean determines(final List<Node> z, final Node y) {
        throw new UnsupportedOperationException();
    }

    @Override
    public double getAlpha() {
        throw new UnsupportedOperationException("The Probabiistic Test doesn't use an alpha parameter");
    }

    @Override
    public void setAlpha(final double alpha) {
        throw new UnsupportedOperationException();
    }

    @Override
    public DataModel getData() {
        return this.data;
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

    @Override
    public List<Matrix> getCovMatrices() {
        return null;
    }

    @Override
    public double getScore() {
        return getPValue();
    }

    public Map<IndependenceFact, Double> getH() {
        return new HashMap<>(this.H);
    }

    public double getPosterior() {
        return this.posterior;
    }

    @Override
    public boolean isVerbose() {
        return this.verbose;
    }

    @Override
    public void setVerbose(final boolean verbose) {
        this.verbose = verbose;
    }

    public void setThreshold(final boolean noRandomizedGeneratingConstraints) {
        this.threshold = noRandomizedGeneratingConstraints;
    }

    public void setCutoff(final double cutoff) {
        this.cutoff = cutoff;
    }

    public void setPriorEquivalentSampleSize(final double priorEquivalentSampleSize) {
        this.priorEquivalentSampleSize = priorEquivalentSampleSize;
    }

    private List<Integer> getRows(final DataSet dataSet, final List<Node> allVars, final Map<Node, Integer> nodesHash) {
        final List<Integer> rows = new ArrayList<>();

        K:
        for (int k = 0; k < dataSet.getNumRows(); k++) {
            for (final Node node : allVars) {
                if (dataSet.getInt(k, nodesHash.get(node)) == -99) continue K;
            }

            rows.add(k);
        }

        return rows;
    }
}



