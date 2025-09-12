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

import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetrad.util.TetradSerializable;
import org.apache.commons.math3.util.FastMath;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serial;
import java.util.*;

/**
 * Stores a triple (x, y, z) of nodes. Note that (x, y, z) = (z, y, x). Useful for marking graphs.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public final class IndependenceFact implements TetradSerializable, Comparable<IndependenceFact> {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The first node conditioned nodes.
     */
    private final Node x;

    /**
     * The second conditioned node.
     */
    private final Node y;

    /**
     * The conditioning set.
     */
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

    // Replace equals, hashCode, compareTo in IndependenceFact with:

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof IndependenceFact other)) return false;

        // Unordered pair for (x,y) by name
        String ax = this.x.getName(), ay = this.y.getName();
        String bx = other.x.getName(), by = other.y.getName();

        boolean xyEqual =
                (ax.equals(bx) && ay.equals(by)) ||
                (ax.equals(by) && ay.equals(bx));

        if (!xyEqual) return false;

        // Unordered Z by names
        if (this._z.size() != other._z.size()) return false;

        // Build name sets without allocations when possible
        // (TreeSet makes equality/order robust, but HashSet is fine too)
        Set<String> z1 = new HashSet<>(this._z.size());
        for (Node n : this._z) z1.add(n.getName());

        for (Node n : other._z) {
            if (!z1.contains(n.getName())) return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        // Unordered (x,y) by name
        String n1 = this.x.getName(), n2 = this.y.getName();
        String a = (n1.compareTo(n2) <= 0) ? n1 : n2;
        String b = (n1.compareTo(n2) <= 0) ? n2 : n1;

        int h = 17;
        h = 31 * h + a.hashCode();
        h = 31 * h + b.hashCode();

        // Unordered Z by names: combine in order-independent way
        // Using sum/xor mix; or sort for determinism.
        // Sorting once keeps determinism across runs.
        if (!_z.isEmpty()) {
            List<String> zNames = new ArrayList<>(_z.size());
            for (Node n : _z) zNames.add(n.getName());
            Collections.sort(zNames);
            for (String s : zNames) {
                h = 31 * h + s.hashCode();
            }
        }
        return h;
    }

    @Override
    public int compareTo(IndependenceFact other) {
        // Provide a total order consistent with equals:
        // compare min(x,y), then max(x,y), then |Z|, then sorted Z names lexicographically
        String ax = this.x.getName(), ay = this.y.getName();
        String a1 = (ax.compareTo(ay) <= 0) ? ax : ay;
        String a2 = (ax.compareTo(ay) <= 0) ? ay : ax;

        String bx = other.x.getName(), by = other.y.getName();
        String b1 = (bx.compareTo(by) <= 0) ? bx : by;
        String b2 = (bx.compareTo(by) <= 0) ? by : bx;

        int c = a1.compareTo(b1); if (c != 0) return c;
        c = a2.compareTo(b2);     if (c != 0) return c;

        // Compare |Z|
        c = Integer.compare(this._z.size(), other._z.size());
        if (c != 0) return c;

        // Compare Z sets by sorted names
        if (!this._z.isEmpty()) {
            List<String> z1 = new ArrayList<>(this._z.size());
            for (Node n : this._z) z1.add(n.getName());
            Collections.sort(z1);

            List<String> z2 = new ArrayList<>(other._z.size());
            for (Node n : other._z) z2.add(n.getName());
            Collections.sort(z2);

            for (int i = 0; i < z1.size(); i++) {
                c = z1.get(i).compareTo(z2.get(i));
                if (c != 0) return c;
            }
        }
        return 0;
    }

//    /**
//     * <p>hashCode.</p>
//     *
//     * @return a int
//     */
//    public int hashCode() {
//        return 1;
//    }
//
//    /**
//     * {@inheritDoc}
//     */
//    public boolean equals(Object obj) {
//        if (!(obj instanceof IndependenceFact fact)) {
//            return false;
//        }
//
//        Set<String> zString1 = new HashSet<>();
//
//        for (Node n : this._z) {
//            zString1.add(n.getName());
//        }
//
//        Set<String> zString2 = new HashSet<>();
//
//        for (Node n : fact._z) {
//            zString2.add(n.getName());
//        }
//
//        String xN1 = this.x.getName();
//        String xN2 = fact.x.getName();
//
//        String yN1 = this.y.getName();
//        String yN2 = fact.y.getName();
//
//        Set<String> a1 = new HashSet<>();
//        a1.add(xN1);
//        a1.add(yN1);
//
//        Set<String> a2 = new HashSet<>();
//        a2.add(xN2);
//        a2.add(yN2);
//
//        return a1.equals(a2) && zString1.equals(zString2);
//
////        return _z.equals(fact._z) && ((x.equals(fact.x) && (y.equals(fact.y))) || (x.equals(fact.y) && (y.equals(fact.x))));
//    }

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

//    /**
//     * Note that this compareTo method gives a lexical ordering for independence facts and doesn't reflect independence
//     * fact equality. So sorted sets should not be used to check for independence fact existence, for instance.
//     * -jdramsey.
//     *
//     * @param fact a {@link edu.cmu.tetrad.graph.IndependenceFact} object
//     * @return a int
//     */
//    public int compareTo(IndependenceFact fact) {
//        List<Node> thisNodes = new ArrayList<>();
//        thisNodes.add(this.x);
//        thisNodes.add(this.y);
//        thisNodes.addAll(this._z);
//
//        List<Node> factNodes = new ArrayList<>();
//        factNodes.add(fact.x);
//        factNodes.add(fact.y);
//        factNodes.addAll(fact._z);
//
//        for (int i = 0; i < thisNodes.size(); i++) {
//            if (factNodes.size() <= i) {
//                return +1;
//            }
//
//            int c = thisNodes.get(i).getName().compareTo(factNodes.get(i).getName());
//            if (c != 0) {
//                return c;
//            }
//        }
//
//        return 0;
//    }

    /**
     * Writes the object to the specified ObjectOutputStream.
     *
     * @param out The ObjectOutputStream to write the object to.
     * @throws IOException If an I/O error occurs.
     */
    @Serial
    private void writeObject(ObjectOutputStream out) throws IOException {
        try {
            out.defaultWriteObject();
        } catch (IOException e) {
            TetradLogger.getInstance().log("Failed to serialize object: " + getClass().getCanonicalName()
                                           + ", " + e.getMessage());
            throw e;
        }
    }

    /**
     * Reads the object from the specified ObjectInputStream. This method is used during deserialization
     * to restore the state of the object.
     *
     * @param in The ObjectInputStream to read the object from.
     * @throws IOException            If an I/O error occurs.
     * @throws ClassNotFoundException If the class of the serialized object cannot be found.
     */
    @Serial
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        try {
            in.defaultReadObject();
        } catch (IOException e) {
            TetradLogger.getInstance().log("Failed to deserialize object: " + getClass().getCanonicalName()
                                           + ", " + e.getMessage());
            throw e;
        }
    }
}



