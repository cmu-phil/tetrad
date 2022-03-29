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

import edu.cmu.tetrad.graph.Node;

import java.util.List;

/**
 * Created by josephramsey on 3/24/15.
 */
public class SepsetsSet implements SepsetProducer {
    private final SepsetMap sepsets;
    private final IndependenceTest test;
    private double p;
    private boolean verbose = false;

    public SepsetsSet(final SepsetMap sepsets, final IndependenceTest test) {
        this.sepsets = sepsets;
        this.test = test;
    }

    @Override
    public List<Node> getSepset(final Node a, final Node b) {
        //isIndependent(a, b, sepsets.get(a, b));
        return this.sepsets.get(a, b);
    }

    @Override
    public boolean isCollider(final Node i, final Node j, final Node k) {
        final List<Node> sepset = this.sepsets.get(i, k);
        if (sepset == null) return false;
        else return !sepset.contains(j);
    }

    @Override
    public boolean isNoncollider(final Node i, final Node j, final Node k) {
        final List<Node> sepset = this.sepsets.get(i, k);
        isIndependent(i, k, this.sepsets.get(i, k));
        return sepset != null && sepset.contains(j);
    }

    @Override
    public boolean isIndependent(final Node a, final Node b, final List<Node> c) {
        return this.test.isIndependent(a, b, c);
    }

    @Override
    public double getPValue() {
        return this.test.getPValue();
    }

    @Override
    public double getScore() {
        return -(this.test.getPValue() - this.test.getAlpha());
    }

    @Override
    public List<Node> getVariables() {
        return this.test.getVariables();
    }

    public boolean isVerbose() {
        return this.verbose;
    }

    @Override
    public void setVerbose(final boolean verbose) {
        this.verbose = verbose;
    }

}

