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

package edu.cmu.tetrad.graph;

import edu.cmu.tetrad.util.TetradSerializable;
import org.apache.commons.math3.util.FastMath;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.*;

/**
 * Stores a triple (x, y, z) of nodes. Note that (x, y, z) = (z, y, x). Useful for marking graphs.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public final class IndependenceFact implements Comparable<IndependenceFact>,
        TetradSerializable {
    private static final long serialVersionUID = 23L;

    private final Node x;
    private final Node y;
    private final Set<Node> _z;

    /**
     * <p>Constructor for IndependenceFact.</p>
     *
     * @param x a {@link edu.cmu.tetrad.graph.Node} object
     * @param y a {@link edu.cmu.tetrad.graph.Node} object
     * @param z a {@link java.util.Set} object
     */
    public IndependenceFact(Node x, Node y, Set<Node> z) {
        if (x == null || y == null || z == null) {
            throw new NullPointerException();
        }

        this.x = x;
        this.y = y;
        this._z = z;
    }

    /**
     * <p>Constructor for IndependenceFact.</p>
     *
     * @param x a {@link edu.cmu.tetrad.graph.Node} object
     * @param y a {@link edu.cmu.tetrad.graph.Node} object
     * @param z a {@link edu.cmu.tetrad.graph.Node} object
     */
    public IndependenceFact(Node x, Node y, Node... z) {
        if (x == null || y == null || z == null) {
            throw new NullPointerException();
        }

        this.x = x;
        this.y = y;

        Set<Node> cond = new HashSet<>();

        Collections.addAll(cond, z);


        this._z = cond;
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @return a {@link edu.cmu.tetrad.graph.IndependenceFact} object
     */
    public static IndependenceFact serializableInstance() {
        return new IndependenceFact(new GraphNode("X"), new GraphNode("Y"));
    }

    /**
     * <p>Getter for the field <code>x</code>.</p>
     *
     * @return a {@link edu.cmu.tetrad.graph.Node} object
     */
    public Node getX() {
        return this.x;
    }

    /**
     * <p>Getter for the field <code>y</code>.</p>
     *
     * @return a {@link edu.cmu.tetrad.graph.Node} object
     */
    public Node getY() {
        return this.y;
    }

    /**
     * <p>getZ.</p>
     *
     * @return a {@link java.util.Set} object
     */
    public Set<Node> getZ() {
        return new HashSet<>(this._z);
    }

    /**
     * <p>hashCode.</p>
     *
     * @return a int
     */
    public int hashCode() {
        return 1;
    }

    /**
     * {@inheritDoc}
     */
    public boolean equals(Object obj) {
        if (!(obj instanceof IndependenceFact)) {
            return false;
        }

        IndependenceFact fact = (IndependenceFact) obj;

        Set<String> zString1 = new HashSet<>();

        for (Node n : this._z) {
            zString1.add(n.getName());
        }

        Set<String> zString2 = new HashSet<>();

        for (Node n : fact._z) {
            zString2.add(n.getName());
        }

        String xN1 = this.x.getName();
        String xN2 = fact.x.getName();

        String yN1 = this.y.getName();
        String yN2 = fact.y.getName();

        return zString1.equals(zString2) && ((xN1.equals(xN2) && yN1.equals(yN2)) || xN1.equals(yN2) && yN1.equals(xN2));

//        return _z.equals(fact._z) && ((x.equals(fact.x) && (y.equals(fact.y))) || (x.equals(fact.y) && (y.equals(fact.x))));
    }

    /**
     * <p>toString.</p>
     *
     * @return a {@link java.lang.String} object
     */
    public String toString() {
        StringBuilder builder = new StringBuilder();

        builder.append(this.x).append(" _||_ ").append(this.y);

        if (!this._z.isEmpty()) {
            builder.append(" | ");

            List<Node> z = new ArrayList<>(this._z);
            Collections.sort(z);

            for (int i = 0; i < z.size(); i++) {
                builder.append(z.get(i));

                if (i < z.size() - 1) {
                    builder.append(", ");
                }
            }
        }

        return builder.toString();
    }

    /**
     * Note that this compareTo method gives a lexical ordering for independence facts and doesn't reflect independence
     * fact equality. So sorted sets should not be used to check for independence fact existence, for instance.
     * -jdramsey.
     *
     * @param fact a {@link edu.cmu.tetrad.graph.IndependenceFact} object
     * @return a int
     */
    public int compareTo(IndependenceFact fact) {
        int c = getX().getName().compareTo(fact.getX().getName());

        if (c != 0) return c;

        c = getY().getName().compareTo(fact.getY().getName());

        if (c != 0) return c;

        Set<Node> _z = getZ();
        List<Node> z = new ArrayList<>(_z);
        Collections.sort(z);
        Set<Node> _factZ = fact.getZ();
        List<Node> factZ = new ArrayList<>(_factZ);
        Collections.sort(factZ);

        int max = FastMath.max(z.size(), factZ.size());

        for (int i = 0; i < max; i++) {
            if (z.size() <= i && factZ.size() > i) {
                return -1;
            }
            if (factZ.size() <= i && z.size() > i) {
                return +1;
            } else {
                String s = z.get(i).getName();
                String s2 = factZ.get(i).getName();
                c = s.compareTo(s2);

                if (c != 0) return c;
            }
        }

        return 0;
    }

    /**
     * Adds semantic checks to the default deserialization method. This method must have the standard signature for a
     * readObject method, and the body of the method must begin with "s.defaultReadObject();". Other than that, any
     * semantic checks can be specified and do not need to stay the same from version to version. A readObject method of
     * this form may be added to any class, even if Tetrad sessions were previously saved out using a version of the
     * class that didn't include it. (That's what the "s.defaultReadObject();" is for. See J. Bloch, Effective Java, for
     * help.
     *
     * @param s
     * @throws IOException            If any.
     * @throws ClassNotFoundException If any.
     */
    private void readObject(ObjectInputStream s)
            throws IOException, ClassNotFoundException {
        s.defaultReadObject();
    }
}



