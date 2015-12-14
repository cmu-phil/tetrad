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

import edu.cmu.tetrad.graph.EdgeListGraphSingleConnections;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;

import java.util.List;

/**
 * Determines sepsets, collider, and noncolliders by examining d-separation facts in
 * a DAG.
 *
 * @author Joseph Ramsey
 */
public class DagSepsets implements SepsetProducer {
    private EdgeListGraphSingleConnections dag;
    private boolean verbose = false;

    public DagSepsets(Graph dag) {
        this.dag = new EdgeListGraphSingleConnections(dag);
    }

    @Override
    public List<Node> getSepset(Node a, Node b) {
        return dag.getSepset(a, b);
    }

    @Override
    public boolean isCollider(Node i, Node j, Node k) {
        List<Node> sepset = dag.getSepset(i, k);
        return sepset != null && !sepset.contains(j);
    }

    @Override
    public boolean isNoncollider(Node i, Node j, Node k) {
//        return true;
        List<Node> sepset = dag.getSepset(i, k);
        return sepset != null && sepset.contains(j);
    }

    @Override
    public boolean isIndependent(Node a, Node b, List<Node> c) {
        return dag.isDSeparatedFrom(a, b, c);
    }

    @Override
    public double getPValue() {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<Node> getVariables() {
        return dag.getNodes();
    }

    public boolean isVerbose() {
        return verbose;
    }

    @Override
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }
}

