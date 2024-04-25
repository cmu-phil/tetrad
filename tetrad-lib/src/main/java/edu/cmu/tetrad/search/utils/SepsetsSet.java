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

package edu.cmu.tetrad.search.utils;

import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.search.test.IndependenceResult;

import java.util.List;
import java.util.Set;

/**
 * <p>Provides a sepset producer using conditional independence tests to generate
 * the Sepset map.</p>
 *
 * @author josephramsey
 * @version $Id: $Id
 * @see SepsetProducer
 * @see SepsetMap
 */
public class SepsetsSet implements SepsetProducer {
    private final SepsetMap sepsets;
    private final IndependenceTest test;
    private boolean verbose;
    private IndependenceResult result;

    /**
     * <p>Constructor for SepsetsSet.</p>
     *
     * @param sepsets a {@link edu.cmu.tetrad.search.utils.SepsetMap} object
     * @param test    a {@link edu.cmu.tetrad.search.IndependenceTest} object
     */
    public SepsetsSet(SepsetMap sepsets, IndependenceTest test) {
        this.sepsets = sepsets;
        this.test = test;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Set<Node> getSepset(Node a, Node b) {
        //isIndependent(a, b, sepsets.get(a, b));
        return this.sepsets.get(a, b);
    }

    /**
     * @throws UnsupportedOperationException if this method is called
     */
    @Override
    public double getPValue(Node a, Node b, Set<Node> sepset) {
        throw new UnsupportedOperationException("This makes not sense for this subclass.");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isUnshieldedCollider(Node i, Node j, Node k) {
        Set<Node> sepset = this.sepsets.get(i, k);
        if (sepset == null) throw new IllegalArgumentException("That triple was covered: " + i + " " + j + " " + k);
        else return !sepset.contains(j);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isIndependent(Node a, Node b, Set<Node> sepset) {
        IndependenceResult result = this.test.checkIndependence(a, b, sepset);
        this.result = result;
        return result.isIndependent();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double getScore() {
        return -(this.result.getPValue() - this.test.getAlpha());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Node> getVariables() {
        return this.test.getVariables();
    }

    /**
     * <p>isVerbose.</p>
     *
     * @return a boolean
     */
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

