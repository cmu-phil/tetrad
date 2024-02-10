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

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeType;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.search.test.IndependenceResult;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * Gives a method of interpreting a test as a score. Various independence tests will calculate p-values; they simply
 * report alpha - p as a score, which will be higher for greater dependence. This class wraps such an independence test
 * and returns the score reported by that test.
 * <p>
 * As for all scores in Tetrad, higher scores mean more dependence, and negative scores indicate independence.
 *
 * @author josephramsey
 * @version $Id: $Id
 * @see IndependenceTest
 */
public class IndTestScore implements Score {
    // The independence test.
    private final IndependenceTest test;
    // The variables of the covariance matrix.
    private final List<Node> variables;
    // True if verbose output should be sent to out.
    private boolean verbose;

    /**
     * Constructs the score using a covariance matrix.
     *
     * @param test The independence test.
     */
    public IndTestScore(IndependenceTest test) {
        this.variables = new ArrayList<>();

        for (Node node : test.getVariables()) {
            if (node.getNodeType() == NodeType.MEASURED) {
                this.variables.add(node);
            }
        }

        this.test = test;
    }

    /**
     * Calculates the sample likelihood and BIC score for i, given its parents in a simple SEM model
     *
     * @param i       The index of the variable.
     * @param parents The indices of the parents of i.
     * @return a double
     */
    public double localScore(int i, int[] parents) {
        throw new UnsupportedOperationException();
    }


    /**
     * {@inheritDoc}
     * <p>
     * Returns a "score difference", which amounts to a conditional local scoring criterion results. Only difference
     * methods is implemented, since the other methods don't make sense here.
     */
    @Override
    public double localScoreDiff(int x, int y, int[] z) {
        IndependenceResult result = this.test.checkIndependence(this.variables.get(x), this.variables.get(y), new HashSet<>(getVariableList(z)));
        return result.getScore();
    }

    /**
     * <p>localScore.</p>
     *
     * @param i      a int
     * @param parent a int
     * @return a double
     * @throws java.lang.UnsupportedOperationException if called.
     */
    public double localScore(int i, int parent) {
        throw new UnsupportedOperationException();
    }


    /**
     * {@inheritDoc}
     */
    public double localScore(int i) {
        throw new UnsupportedOperationException();
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns true if the edge with the given bump is an effect edge.
     */
    @Override
    public boolean isEffectEdge(double bump) {
        return true;
    }

    /**
     * Returns the data set.
     *
     * @return The data set.
     */
    public DataSet getDataSet() {
        throw new UnsupportedOperationException();
    }

    /**
     * Returns true if verbose output should be sent to out.
     *
     * @return True if verbose output should be sent to out.
     */
    public boolean isVerbose() {
        return this.verbose;
    }

    /**
     * Sets whether verbose output should be sent to out.
     *
     * @param verbose True if verbose output should be sent to out.
     */
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns the list of variables.
     */
    @Override
    public List<Node> getVariables() {
        return this.variables;
    }

    /**
     * Returns the sample size.
     *
     * @return The sample size.
     */
    public int getSampleSize() {
        return 0;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns the maximum degree, which is set to 1000.
     */
    @Override
    public int getMaxDegree() {
        return 1000;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns the 'determines' judgment from the first score.
     */
    @Override
    public boolean determines(List<Node> z, Node y) {
        return false;
    }

    private List<Node> getVariableList(int[] indices) {
        List<Node> variables = new ArrayList<>();
        for (int i : indices) {
            variables.add(this.variables.get(i));
        }
        return variables;
    }
}



