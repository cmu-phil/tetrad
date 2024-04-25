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

import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;

import javax.help.UnsupportedOperationException;
import java.util.List;
import java.util.Set;

/**
 * Determines sepsets, collider, and noncolliders by examining d-separation facts in a DAG.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class DagSepsets implements SepsetProducer {
    // The DAG being analyzed.
    private final EdgeListGraph dag;

    /**
     * Constructs a new DagSepsets object for the given DAG.
     *
     * @param dag the DAG.
     */
    public DagSepsets(Graph dag) {
        this.dag = new EdgeListGraph(dag);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns the list of sepset for {a, b}.
     */
    @Override
    public Set<Node> getSepset(Node a, Node b) {
        return this.dag.getSepset(a, b);
    }

    /**
     * {@inheritDoc}
     * <p>
     * True iff i*-*j*-*k is an unshielded collider.
     */
    @Override
    public boolean isUnshieldedCollider(Node i, Node j, Node k) {
        Set<Node> sepset = this.dag.getSepset(i, k);
        return sepset != null && !sepset.contains(j);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Not implemented; required for an interface.
     */
    @Override
    public double getScore() {
        throw new UnsupportedOperationException();
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns true just in case msep(a, b | c) in the DAG. Don't let the name isIndependent fool you; this is a
     * d-separation method. We only use the name isIndependent so that this can be used in place of an independence
     * check.
     */
    @Override
    public boolean isIndependent(Node a, Node b, Set<Node> sepset) {
        return this.dag.paths().isMSeparatedFrom(a, b, sepset, false);
    }

    /**
     * @throws UnsupportedOperationException if this method is called.
     */
    @Override
    public double getPValue(Node a, Node b, Set<Node> sepset) {
        throw new UnsupportedOperationException("This makes not sense for this subclass.");
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns the nodes in the DAG.
     */
    @Override
    public List<Node> getVariables() {
        return this.dag.getNodes();
    }

    /**
     * {@inheritDoc}
     * <p>
     * Thsi method is not used.
     */
    @Override
    public void setVerbose(boolean verbose) {
        throw new UnsupportedOperationException("This method is not used for this class.");
    }

    /**
     * Returns the DAG being analyzed.
     *
     * @return This DAG.
     */
    public Graph getDag() {
        return this.dag;
    }
}

