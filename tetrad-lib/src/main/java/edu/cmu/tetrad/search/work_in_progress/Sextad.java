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

import edu.cmu.tetrad.graph.GraphNode;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.TetradSerializable;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents an ordered sextad of variables.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class Sextad implements TetradSerializable {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The first node in the sextad.
     */
    private final Node i;

    /**
     * The second node in the sextad.
     */
    private final Node j;

    /**
     * The third node in the sextad.
     */
    private final Node k;

    /**
     * The fourth node in the sextad.
     */
    private final Node l;

    /**
     * The fifth node in the sextad.
     */
    private final Node m;

    /**
     * The sixth node in the sextad.
     */
    private final Node n;

    /**
     * <p>Constructor for Sextad.</p>
     *
     * @param i a {@link edu.cmu.tetrad.graph.Node} object
     * @param j a {@link edu.cmu.tetrad.graph.Node} object
     * @param k a {@link edu.cmu.tetrad.graph.Node} object
     * @param l a {@link edu.cmu.tetrad.graph.Node} object
     * @param m a {@link edu.cmu.tetrad.graph.Node} object
     * @param n a {@link edu.cmu.tetrad.graph.Node} object
     */
    public Sextad(Node i, Node j, Node k, Node l, Node m, Node n) {
        testDistinctness(i, j, k, l, m, n);
        this.i = i;
        this.j = j;
        this.k = k;
        this.l = l;
        this.m = m;
        this.n = n;
    }

    /**
     * <p>Constructor for Sextad.</p>
     *
     * @param nodes an array of {@link edu.cmu.tetrad.graph.Node} objects
     */
    public Sextad(Node[] nodes) {
        if (nodes.length != 6) throw new IllegalArgumentException("Must provide exactly 6 nodes.");

        this.i = nodes[0];
        this.j = nodes[1];
        this.k = nodes[2];
        this.l = nodes[3];
        this.m = nodes[4];
        this.n = nodes[5];

        testDistinctness(this.i, this.j, this.k, this.l, this.m, this.n);

    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @return a {@link edu.cmu.tetrad.search.work_in_progress.Sextad} object
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

    /**
     * <p>Getter for the field <code>i</code>.</p>
     *
     * @return a {@link edu.cmu.tetrad.graph.Node} object
     */
    public Node getI() {
        return this.i;
    }

    /**
     * <p>Getter for the field <code>j</code>.</p>
     *
     * @return a {@link edu.cmu.tetrad.graph.Node} object
     */
    public Node getJ() {
        return this.j;
    }

    /**
     * <p>Getter for the field <code>k</code>.</p>
     *
     * @return a {@link edu.cmu.tetrad.graph.Node} object
     */
    public Node getK() {
        return this.k;
    }

    /**
     * <p>Getter for the field <code>l</code>.</p>
     *
     * @return a {@link edu.cmu.tetrad.graph.Node} object
     */
    public Node getL() {
        return this.l;
    }

    /**
     * <p>Getter for the field <code>m</code>.</p>
     *
     * @return a {@link edu.cmu.tetrad.graph.Node} object
     */
    public Node getM() {
        return this.m;
    }

    /**
     * <p>Getter for the field <code>n</code>.</p>
     *
     * @return a {@link edu.cmu.tetrad.graph.Node} object
     */
    public Node getN() {
        return this.n;
    }

    /**
     * <p>hashCode.</p>
     *
     * @return a int
     */
    public int hashCode() {
        int hash = 17 * this.i.hashCode() * this.j.hashCode() * this.k.hashCode();
        hash += 29 * this.l.hashCode() * this.m.hashCode() * this.n.hashCode();

        return hash;
    }

    /**
     * {@inheritDoc}
     */
    public boolean equals(Object o) {
        if (!(o instanceof Sextad sextad)) throw new IllegalArgumentException();

        boolean leftEquals = this.i == sextad.i && this.j == sextad.j && this.k == sextad.k ||
                             this.i == sextad.i && this.j == sextad.k && this.k == sextad.j ||
                             this.i == sextad.j && this.j == sextad.i && this.k == sextad.k ||
                             this.i == sextad.j && this.j == sextad.k && this.k == sextad.i ||
                             this.i == sextad.k && this.j == sextad.i && this.k == sextad.j ||
                             this.i == sextad.k && this.j == sextad.j && this.k == sextad.i;

        boolean rightEquals = this.l == sextad.l && this.m == sextad.m && this.n == sextad.n ||
                              this.l == sextad.l && this.m == sextad.n && this.n == sextad.m ||
                              this.l == sextad.m && this.m == sextad.l && this.n == sextad.n ||
                              this.l == sextad.m && this.m == sextad.n && this.n == sextad.l ||
                              this.l == sextad.n && this.m == sextad.l && this.n == sextad.m ||
                              this.l == sextad.n && this.m == sextad.m && this.n == sextad.l;

        return leftEquals && rightEquals;
    }

    /**
     * <p>toString.</p>
     *
     * @return a {@link java.lang.String} object
     */
    public String toString() {
        return "<" + this.i + ", " + this.j + ", " + this.k + "; " + this.l + ", " + this.m + ", " + this.n + ">";
    }

    /**
     * <p>getNodes.</p>
     *
     * @return a {@link java.util.List} object
     */
    public List<Node> getNodes() {
        List<Node> nodes = new ArrayList<>();
        nodes.add(this.i);
        nodes.add(this.j);
        nodes.add(this.k);
        nodes.add(this.l);
        nodes.add(this.m);
        nodes.add(this.n);
        return nodes;
    }
}


