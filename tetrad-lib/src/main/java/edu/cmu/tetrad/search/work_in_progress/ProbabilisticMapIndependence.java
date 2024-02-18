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

package edu.cmu.tetrad.search.work_in_progress;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.IndependenceFact;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.search.test.IndependenceResult;
import edu.cmu.tetrad.search.utils.LogUtilsSearch;
import edu.cmu.tetrad.util.TetradLogger;
import edu.pitt.dbmi.algo.bayesian.constraint.inference.BCInference;

import java.util.*;

/**
 * Uses BCInference by Cooper and Bui to calculate probabilistic conditional independence judgments.
 *
 * @author josephramsey 3/2014
 * @version $Id: $Id
 */
public class ProbabilisticMapIndependence implements IndependenceTest {

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

    private boolean verbose;

    /**
     * Initializes the test using a discrete data sets.
     *
     * @param dataSet a {@link edu.cmu.tetrad.data.DataSet} object
     */
    public ProbabilisticMapIndependence(DataSet dataSet) {
        this.data = dataSet;

        int[] counts = new int[dataSet.getNumColumns() + 2];

        for (int j = 0; j < dataSet.getNumColumns(); j++) {
            counts[j + 1] = ((DiscreteVariable) (dataSet.getVariable(j))).getNumCategories();
        }

        if (!dataSet.isDiscrete()) {
            throw new IllegalArgumentException("Not a discrete data set.");

        }

        int[][] cases = new int[dataSet.getNumRows() + 1][dataSet.getNumColumns() + 2];

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

    /**
     * {@inheritDoc}
     */
    @Override
    public IndependenceTest indTestSubset(List<Node> vars) {
        throw new UnsupportedOperationException();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IndependenceResult checkIndependence(Node x, Node y, Set<Node> _z) {
        List<Node> z = new ArrayList<>(_z);
        Collections.sort(z);

        Node[] nodes = new Node[z.size()];
        for (int i = 0; i < z.size(); i++) nodes[i] = z.get(i);
        return checkIndependence(x, y, nodes);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IndependenceResult checkIndependence(Node x, Node y, Node... z) {
        double pInd = probConstraint(BCInference.OP.independent, x, y, z);
        double p = this.probOp(pInd);

        if (Double.isNaN(p)) {
            throw new RuntimeException("Undefined p-value encountered when testing " +
                    LogUtilsSearch.independenceFact(x, y, GraphUtils.asSet(z)));
        }

        posterior = p;
        boolean independent = p > 0.5;

        if (this.verbose) {
            if (independent) {
                TetradLogger.getInstance().forceLogMessage(
                        LogUtilsSearch.independenceFactMsg(x, y, GraphUtils.asSet(z), p));
            }
        }

        return new IndependenceResult(new IndependenceFact(x, y, z), independent, pInd, getAlpha() - pInd);
    }

    /**
     * <p>probConstraint.</p>
     *
     * @param op a {@link edu.pitt.dbmi.algo.bayesian.constraint.inference.BCInference.OP} object
     * @param x  a {@link edu.cmu.tetrad.graph.Node} object
     * @param y  a {@link edu.cmu.tetrad.graph.Node} object
     * @param z  an array of {@link edu.cmu.tetrad.graph.Node} objects
     * @return a double
     */
    public double probConstraint(BCInference.OP op, Node x, Node y, Node[] z) {

        int _x = indices.get(x) + 1;
        int _y = indices.get(y) + 1;

        int[] _z = new int[z.length + 1];
        _z[0] = z.length;
        for (int i = 0; i < z.length; i++) _z[i + 1] = indices.get(z[i]) + 1;

        return bci.probConstraint(op, _x, _y, _z);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Node> getVariables() {
        return nodes;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Node getVariable(String name) {
        for (Node node : nodes) {
            if (name.equals(node.getName())) return node;
        }

        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean determines(Set<Node> z, Node y) {
        throw new UnsupportedOperationException();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double getAlpha() {
        throw new UnsupportedOperationException();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setAlpha(double alpha) {
        throw new UnsupportedOperationException();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DataModel getData() {
        return data;
    }

    /**
     * <p>getH.</p>
     *
     * @return a {@link java.util.Map} object
     */
    public Map<IndependenceFact, Double> getH() {
        return new HashMap<>(H);
    }

    private double probOp(double pInd) {
        double probOp;

        probOp = pInd;

        return probOp;
    }

    /**
     * <p>Getter for the field <code>posterior</code>.</p>
     *
     * @return a double
     */
    public double getPosterior() {
        return this.posterior;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isVerbose() {
        return this.verbose;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }
}


