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
 * Simple implementation of a directed Graph.  edges are just represented by
 * float values (a zero == no edge) stored in a matrix.
 *
 * Two edges of different orientation can exist between two nodes, but no more than
 * one edge of a given orientation can exist between two nodes.
 *
 *
 * @author
 * <a href="http://www.eecs.tulane.edu/Saavedra" target="_TOP">Raul Saavedra</a>
 * (<a href="mailto:rsaavedr@ai.uwf.edu">rsaavedr@ai.uwf.edu</A>)
 */

import edu.cmu.tetrad.gene.tetrad.gene.algorithm.util.BasicGraph;
import edu.cmu.tetrad.gene.tetrad.gene.algorithm.util.MatrixF;

import java.io.FileNotFoundException;
import java.io.IOException;

public class Digraph extends BasicGraph {

    /**
     * Edge values
     */
    private MatrixF edges;

    /**
     * Number of parents of each node
     */
    private int[] nParents;

    /**
     * Creates a OldDigraph with <code>gName</code> name, and <code>n</code>
     * nodes.
     */
    public Digraph(String gName, int nNodes) {
        super(gName, nNodes);
    }

    /**
     * Creates a OldDigraph reading it from file <code>fname</code>.
     */
    public Digraph(String fname) throws FileNotFoundException, IOException {
        super(fname);
    }

    /**
     * Copy constructor.
     */
    public Digraph(Digraph digraph) {
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
        Digraph g2 =
                new Digraph("Clone_of_[" + this.graphName + "]", this.nNodes);
        for (int i = 0; i < this.nNodes; i++) {
            g2.nodeNames[i] = this.nodeNames[i];
            for (int j = 0; j < i; j++) {
                g2.setEdge(i, j, this.getEdge(i, j));
            }
        }
        return g2;
    }

    protected void initializeEdges() {
        edges = new MatrixF("EdgeMatrix_" + this.graphName, this.nNodes);
        nParents = new int[this.nNodes];
    }

    /**
     * Sets a value of edge between nodes i and j
     */
    public void setEdge(int i, int j, double value) {
        double e = this.getEdges().getDoubleValue(i, j);
        this.getEdges().setDoubleValue(i, j, value);
        if ((e == 0.0) && (value != 0.0)) {
            this.nEdges++;
            this.nParents[j]++;
        }
        else {
            if ((e != 0.0) && (value == 0.0)) {
                this.nEdges--;
                this.nParents[j]--;
            }
        }
    }

    /**
     * Returns the value of edge between nodes i and j
     */
    public double getEdge(int i, int j) {
        return this.getEdges().getDoubleValue(i, j);
    }

    /**
     * Returns a string representation of the set of edges in this graph
     */
    public String EdgesToString() {
        String s = "";
        int ne = 0;
        for (int i = 0; i < this.nNodes; i++) {
            for (int j = 0; j < this.nNodes; j++) {
                double e = this.getEdges().getDoubleValue(i, j);
                if (e != 0.0) {
                    s = s + i + "  " + j + " \t" + e +
                            "\n";  //+"\t// # "+ne+"\n";
                    ne++;
                }
            }
        }
        return s;
    }

    /**
     * Returns the number of parents of node i
     */
    public int getNumParents(int i) {
        if ((i < 0) || (i >= this.getSize())) {
            this.badNodeIndex(i);
        }
        return this.nParents[i];
    }

    /**
     * Returns an array with the indexes of the parents of node i. If node i has
     * no parents it returns an array of size 0 (e.g. not null)
     */
    public int[] getParents(int j) {
        if ((j < 0) || (j >= this.nNodes)) {
            this.badNodeIndex(j);
        }
        int[] ap = new int[this.nParents[j]];
        int np = 0;
        for (int i = 0; i < this.nNodes; i++) {
            if (this.getEdges().getDoubleValue(i, j) != 0.0) {
                ap[np] = i;
                np++;
            }
        }
        return ap;
    }

    public MatrixF getEdges() {
        return edges;
    }

}




