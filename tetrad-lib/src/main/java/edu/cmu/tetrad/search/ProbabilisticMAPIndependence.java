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
import edu.pitt.dbmi.algo.bayesian.constraint.inference.BCInference;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Uses BCInference by Cooper and Bui to calculate probabilistic conditional independence judgments.
 *
 * @author Joseph Ramsey 3/2014
 */
public class ProbabilisticMAPIndependence implements IndependenceTest {

    /**
     * Calculates probabilities of independence for conditional independence facts.
     */
    private final BCInference bci;

    /**
     * The data set for which conditional independence judgments are requested.
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

    private boolean verbose = false;

    /**
     * Initializes the test using a discrete data sets.
     */
    public ProbabilisticMAPIndependence(final DataSet dataSet) {
        this.data = dataSet;

        final int[] counts = new int[dataSet.getNumColumns() + 2];

        for (int j = 0; j < dataSet.getNumColumns(); j++) {
            counts[j + 1] = ((DiscreteVariable) (dataSet.getVariable(j))).getNumCategories();
        }

        if (!dataSet.isDiscrete()) {
            throw new IllegalArgumentException("Not a discrete data set.");

        }

        final int[][] cases = new int[dataSet.getNumRows() + 1][dataSet.getNumColumns() + 2];

        for (int i = 0; i < dataSet.getNumRows(); i++) {
            for (int j = 0; j < dataSet.getNumColumns(); j++) {
                cases[i + 1][j + 1] = dataSet.getInt(i, j) + 1;
            }
        }

        this.bci = new BCInference(cases, counts);

        this.nodes = dataSet.getVariables();

        this.indices = new HashMap<>();

        for (int i = 0; i < this.nodes.size(); i++) {
            this.indices.put(this.nodes.get(i), i);
        }

        this.H = new HashMap<>();
    }

    @Override
    public IndependenceTest indTestSubset(final List<Node> vars) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isIndependent(final Node x, final Node y, final List<Node> z) {
        final Node[] _z = z.toArray(new Node[z.size()]);
        return isIndependent(x, y, _z);
    }

    @Override
    public boolean isIndependent(final Node x, final Node y, final Node... z) {
//        IndependenceFact key = new IndependenceFact(x, y, z);
//
//        if (!H.containsKey(key)) {
        final double pInd = probConstraint(BCInference.OP.independent, x, y, z);
//            H.put(key, pInd);
//        }
//
//        double pInd = H.get(key);

        final double p = probOp(BCInference.OP.independent, pInd);

        this.posterior = p;

        return p > 0.5;

//        if (RandomUtil.getInstance().nextDouble() < p) {
//            return true;
//        }
//        else {
//            return false;
//        }
    }

    public double probConstraint(final BCInference.OP op, final Node x, final Node y, final Node[] z) {

        final int _x = this.indices.get(x) + 1;
        final int _y = this.indices.get(y) + 1;

        final int[] _z = new int[z.length + 1];
        _z[0] = z.length;
        for (int i = 0; i < z.length; i++) _z[i + 1] = this.indices.get(z[i]) + 1;

        return this.bci.probConstraint(op, _x, _y, _z);
    }

    @Override
    public boolean isDependent(final Node x, final Node y, final List<Node> z) {
        final Node[] _z = z.toArray(new Node[z.size()]);
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
        throw new UnsupportedOperationException();
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

    private double probOp(final BCInference.OP type, final double pInd) {
        final double probOp;

        if (BCInference.OP.independent == type) {
            probOp = pInd;
        } else {
            probOp = 1.0 - pInd;
        }

        return probOp;
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
}


