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

package edu.cmu.tetrad.search.test;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.IndependenceFacts;
import edu.cmu.tetrad.graph.IndependenceFact;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.search.utils.LogUtilsSearch;
import edu.cmu.tetrad.util.TetradLogger;

import javax.help.UnsupportedOperationException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Checks conditional independence against a list of conditional independence facts, manually entered.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public final class IndTestIndependenceFacts implements IndependenceTest {

    private final IndependenceFacts facts;
    private boolean verbose;

    /**
     * Constructor.
     *
     * @param facts The facts to check.
     * @see IndependenceFacts
     */
    public IndTestIndependenceFacts(IndependenceFacts facts) {
        this.facts = facts;
    }

    /** {@inheritDoc} */
    public IndependenceTest indTestSubset(List<Node> vars) {
        throw new UnsupportedOperationException();
    }

    /**
     * {@inheritDoc}
     *
     * Checks independence by looking up facts in the list of facts supplied in the constructor.
     *
     * @see IndependenceResult
     * @param x a {@link edu.cmu.tetrad.graph.Node} object
     * @param y a {@link edu.cmu.tetrad.graph.Node} object
     * @param __z a {@link java.util.Set} object
     * @return a {@link edu.cmu.tetrad.search.test.IndependenceResult} object
     */
    public IndependenceResult checkIndependence(Node x, Node y, Set<Node> __z) {
        List<Node> z = new ArrayList<Node>(__z);
        Collections.sort(z);

        Node[] _z = new Node[__z.size()];

        for (int i = 0; i < z.size(); i++) {
            _z[i] = z.get(i);
        }

        boolean independent = this.facts.isIndependent(x, y, _z);

        if (this.verbose) {
            if (independent) {
                TetradLogger.getInstance().forceLogMessage(
                        LogUtilsSearch.independenceFactMsg(x, y, __z, getPValue()));
            }
        }

        return new IndependenceResult(new IndependenceFact(x, y, __z), independent, getPValue(), getAlpha() - getPValue());
    }

    /**
     * No p-values are available.
     *
     * @return Double.NaN.
     */
    public double getPValue() {
        return Double.NaN;
    }

    /**
     * Returns the list of variables for the facts.
     *
     * @return This list.
     */
    public List<Node> getVariables() {
        return this.facts.getVariables();
    }

    /**
     * {@inheritDoc}
     *
     * Returns the node with the given name.
     */
    public Node getVariable(String name) {
        if (name == null) throw new NullPointerException();

        List<Node> variables = this.facts.getVariables();

        for (Node node : variables) {
            if (name.equals(node.getName())) {
                return node;
            }
        }

        return null;
    }

    /** {@inheritDoc} */
    public boolean determines(List<Node> z, Node y) {
        throw new UnsupportedOperationException("Method not implmeented.");
    }

    /**
     * <p>getAlpha.</p>
     *
     * @throws java.lang.UnsupportedOperationException Method not implemented.
     * @return a double
     */
    public double getAlpha() {
        throw new java.lang.UnsupportedOperationException("Method not implemented");
    }

    /** {@inheritDoc} */
    public void setAlpha(double alpha) {
        throw new UnsupportedOperationException();
    }

    /**
     * Returns the facts supplied in the constructor, which constutite a data model.
     *
     * @return These facts.
     */
    public DataModel getData() {
        return this.facts;
    }

    /**
     * Returns whether verbose output is to be printed.
     *
     * @return True, if so.
     */
    public boolean isVerbose() {
        return this.verbose;
    }

    /**
     * {@inheritDoc}
     *
     * Sets whether verbose output is to be printed.
     */
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }
}





