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

package edu.cmu.tetrad.gene.tetrad.gene.algorithm.biolingua;

/**
 * Implements a digraph to be used by the Biolingua algorithm.<p>
 *
 * @author
 * <a href="http://www.eecs.tulane.edu/Saavedra" target="_TOP">Raul Saavedra</a>
 * (<a href="mailto:rsaavedr@ai.uwf.edu">rsaavedr@ai.uwf.edu</A>)
 */

import edu.cmu.tetrad.gene.tetrad.gene.algorithm.util.OutputGraph;

import java.io.FileNotFoundException;
import java.io.IOException;

public class BiolinguaDigraph extends Digraph implements OutputGraph {

    /**
     * Creates a BiolinguaDigraph with name <code>gName</code> and
     * <code>n</code> nodes
     */
    public BiolinguaDigraph(String gName, int n) {
        super(gName, n);
    }

    /**
     * Creates a BiolinguaDigraph reading it from file <code>fname</code>.
     */
    public BiolinguaDigraph(String fname)
            throws FileNotFoundException, IOException {
        super(fname);
    }

    /**
     * Copy constructor.
     */
    public BiolinguaDigraph(BiolinguaDigraph digraph) {
        this("Clone_of_[" + digraph + "]", digraph.nNodes);
        for (int i = 0; i < digraph.nNodes; i++) {
            this.nodeNames[i] = digraph.nodeNames[i];
            for (int j = 0; j < i; j++) {
                this.setEdge(i, j, digraph.getEdge(i, j));
            }
        }
    }

    /**
     * Returns a clone of this graph
     */
    public Object clone() {
        BiolinguaDigraph g2 = new BiolinguaDigraph(
                "Clone_of_[" + this.graphName + "]", this.nNodes);
        for (int i = 0; i < this.nNodes; i++) {
            g2.nodeNames[i] = this.nodeNames[i];
            for (int j = 0; j < i; j++) {
                g2.setEdge(i, j, this.getEdge(i, j));
            }
        }
        return g2;
    }

    /**
     * Returns true if node p is parent of node c.<p>
     */
    public boolean isParent(int p, int c) {
        return (this.getEdges().getDoubleValue(p, c) != 0.0);
    }

    /**
     * Returns a string with the indexes of all parents of node i separated by
     * spaces (useful for printouts)
     */
    public String strOfParents(int i) {
        int[] ap = this.getParents(i);
        String s = "";

        for (int anAp : ap) {
            s = s + " " + anAp;
        }

        return s;
    }

    /**
     * Returns null (no lag information is stored in a BiolinguaDigraph).
     */
    public int[] getLags(int i) {
        return null;
    }

    /**
     * Returns a specially formatted string with all the contents of this Graph.
     * Actually this string is compliant with the same format expected when
     * reading the graph from a file.
     */
    public String toString() {
        String s = this.getClass().getName() + " " + this.graphName + "\n" +
                this.nNodes + "\t// <-- Total # nodes\n" + "// " +
                this.getNumEdges() + "\t// <-- Total # edges\n";

        s = s + "\n// Node names:\n";
        for (int i = 0; i < this.nNodes; i++) {
            s = s + this.getNodeName(i);
            s = s + "\t// #" + i + " \tParents = {" + this.strOfParents(i) +
                    " }\n";
        }

        s = s + "\n// edges:\n";
        s = s + this.EdgesToString();
        return s;
    }
}





