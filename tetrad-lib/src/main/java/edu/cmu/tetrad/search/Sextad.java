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

import edu.cmu.tetrad.graph.GraphNode;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.TetradSerializable;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a sextad of variables.
 */
public class Sextad implements TetradSerializable {
    static final long serialVersionUID = 23L;

    private Node i;
    private Node j;
    private Node k;
    private Node l;
    private Node m;
    private Node n;

    public Sextad(Node i, Node j, Node k, Node l, Node m, Node n) {
        testDistinctness(i, j, k, l, m, n);
        this.i = i;
        this.j = j;
        this.k = k;
        this.l = l;
        this.m = m;
        this.n = n;
    }

    public Sextad(Node[] nodes) {
        if (nodes.length != 6) throw new IllegalArgumentException("Must provide exactly 6 nodes.");

        this.i = nodes[0];
        this.j = nodes[1];
        this.k = nodes[2];
        this.l = nodes[3];
        this.m = nodes[4];
        this.n = nodes[5];

        testDistinctness(i, j, k, l, m, n);

    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     */
    public static Sextad serializableInstance() {
        Node i = new GraphNode("i");
        Node j = new GraphNode("j");
        Node k = new GraphNode("k");
        Node l = new GraphNode("l");
        Node m = new GraphNode("m");
        Node n = new GraphNode("n");
        return new Sextad(i, j, k, l, m, n);
    }

    private void testDistinctness(Node i, Node j, Node k, Node l, Node m, Node n) {
        if (i == j || i == k || i == l || i == m || i == n) {
            throw new IllegalArgumentException("Nodes not distinct.");
        }

        if (j == k || j == l || j == m || j == n) {
            throw new IllegalArgumentException("Nodes not distinct.");
        }

        if (k == l || k == m || k == n) {
            throw new IllegalArgumentException("Nodes not distinct.");
        }

        if (l == m || l == n) {
            throw new IllegalArgumentException("Nodes not distinct.");
        }

        if (m == n) {
            throw new IllegalArgumentException("Nodes not distinct.");
        }
    }

    public Node getI() {
        return i;
    }

    public Node getJ() {
        return j;
    }

    public Node getK() {
        return k;
    }

    public Node getL() {
        return l;
    }

    public Node getM() {
        return m;
    }

    public Node getN() {
        return n;
    }

    public int hashCode() {
        int hash = 17 * i.hashCode() * j.hashCode() * k.hashCode();
        hash += 29 * l.hashCode() * m.hashCode() * n.hashCode();

        return hash;
    }

    public boolean equals(Object o) {
        if (!(o instanceof Sextad)) throw new IllegalArgumentException();
        if (o == null) return false;
        Sextad sextad = (Sextad) o;

        boolean leftEquals = i == sextad.i && j == sextad.j && k == sextad.k ||
                i == sextad.i && j == sextad.k && k == sextad.j ||
                i == sextad.j && j == sextad.i && k == sextad.k ||
                i == sextad.j && j == sextad.k && k == sextad.i ||
                i == sextad.k && j == sextad.i && k == sextad.j ||
                i == sextad.k && j == sextad.j && k == sextad.i;

        boolean rightEquals = l == sextad.l && m == sextad.m && n == sextad.n ||
                l == sextad.l && m == sextad.n && n == sextad.m ||
                l == sextad.m && m == sextad.l && n == sextad.n ||
                l == sextad.m && m == sextad.n && n == sextad.l ||
                l == sextad.n && m == sextad.l && n == sextad.m ||
                l == sextad.n && m == sextad.m && n == sextad.l;

        return leftEquals && rightEquals;
    }

    public String toString() {
        return "<" + i + ", " + j + ", " + k + "; " + l + ", " + m + ", " + n + ">";
    }

    public List<Node> getNodes() {
        List<Node> nodes = new ArrayList<Node>();
        nodes.add(i);
        nodes.add(j);
        nodes.add(k);
        nodes.add(l);
        nodes.add(m);
        nodes.add(n);
        return nodes;
    }
}


