/// ////////////////////////////////////////////////////////////////////////////
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

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.HashSet;
import java.util.Set;

/**
 * Represents a ordered tetrad of variables, (i, j, k, l).
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class TetradNode {
    private final Node i;
    private final Node j;
    private final Node k;
    private final Node l;
    private final double pValue;

    /**
     * <p>Constructor for Tetrad.</p>
     *
     * @param i a {@link edu.cmu.tetrad.graph.Node} object
     * @param j a {@link edu.cmu.tetrad.graph.Node} object
     * @param k a {@link edu.cmu.tetrad.graph.Node} object
     * @param l a {@link edu.cmu.tetrad.graph.Node} object
     */
    public TetradNode(Node i, Node j, Node k, Node l) {
        this.i = i;
        this.j = j;
        this.k = k;
        this.l = l;
        this.pValue = Double.NaN;
    }

    /**
     * <p>Constructor for Tetrad.</p>
     *
     * @param i      a {@link edu.cmu.tetrad.graph.Node} object
     * @param j      a {@link edu.cmu.tetrad.graph.Node} object
     * @param k      a {@link edu.cmu.tetrad.graph.Node} object
     * @param l      a {@link edu.cmu.tetrad.graph.Node} object
     * @param pValue a double
     */
    public TetradNode(Node i, Node j, Node k, Node l, double pValue) {
        this.i = i;
        this.j = j;
        this.k = k;
        this.l = l;
        this.pValue = pValue;
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
     * <p>hashCode.</p>
     *
     * @return a int
     */
    public int hashCode() {
        int hash = 17 * this.i.hashCode() * this.j.hashCode();
        hash += 29 * this.k.hashCode() * this.l.hashCode();
        return hash;
    }

    /**
     * {@inheritDoc}
     */
    public boolean equals(Object o) {
        if (o == null) return false;
        if (!(o instanceof TetradNode tetrad)) return false;
        return (this.i == tetrad.i && this.j == tetrad.j && this.k == tetrad.k && this.l == tetrad.l)
               || (this.i == tetrad.j && this.j == tetrad.i && this.k == tetrad.k && this.l == tetrad.l)
               || (this.i == tetrad.i && this.j == tetrad.j && this.k == tetrad.l && this.l == tetrad.k)
               || (this.i == tetrad.j && this.j == tetrad.i && this.k == tetrad.l && this.l == tetrad.k)
               || (this.i == tetrad.k && this.j == tetrad.l && this.k == tetrad.i && this.l == tetrad.j)
               || (this.i == tetrad.k && this.j == tetrad.l && this.k == tetrad.j && this.l == tetrad.i)
               || (this.i == tetrad.l && this.j == tetrad.k && this.k == tetrad.i && this.l == tetrad.j)
               || (this.i == tetrad.l && this.j == tetrad.k && this.k == tetrad.j && this.l == tetrad.i);
    }

    /**
     * <p>toString.</p>
     *
     * @return a {@link java.lang.String} object
     */
    public String toString() {
        NumberFormat nf = new DecimalFormat("0.0000");

        if (Double.isNaN(this.pValue)) {
            return "s(" + this.i + "," + this.j + ")*s(" + this.k + "," + this.l + ")-s(" + this.i + "," + this.k + ")*s(" + this.j + "," + this.l + ")";
        } else {
            return "<" + this.i + ", " + this.j + ", " + this.k + ", " + this.l + ", " + nf.format(this.pValue) + ">";
        }
    }

    /**
     * <p>Getter for the field <code>pValue</code>.</p>
     *
     * @return a double
     */
    public double getPValue() {
        return this.pValue;
    }

    /**
     * <p>getNodes.</p>
     *
     * @return a {@link java.util.Set} object
     */
    public Set<Node> getNodes() {
        Set<Node> nodes = new HashSet<>();
        nodes.add(this.i);
        nodes.add(this.j);
        nodes.add(this.k);
        nodes.add(this.l);
        return nodes;
    }
}


