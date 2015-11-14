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

package edu.cmu.tetrad.graph;

import edu.cmu.tetrad.util.TetradSerializable;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.*;

/**
 * Stores a triple (x, y, z) of nodes. Note that (x, y, z) = (z, y, x). Useful
 * for marking graphs.
 *
 * @author Joseph Ramsey
 */
public final class IndependenceFact implements Comparable, TetradSerializable {
    static final long serialVersionUID = 23L;

    private Node x;
    private Node y;
    /** deprecated **/
    private List<Node> z;
    private Set<Node> _z;

    /**
     * Constructs a triple of nodes.
     */
    public IndependenceFact(Node x, Node y, List<Node> z) {
        if (x == null || y == null || z == null) {
            throw new NullPointerException();
        }

        this.x = x;
        this.y = y;
        this._z = new HashSet<Node>(z);
    }

    public IndependenceFact(Node x, Node y, Node...z) {
        if (x == null || y == null || z == null) {
            throw new NullPointerException();
        }

        this.x = x;
        this.y = y;

        Set<Node> cond = new HashSet<Node>();

        for (Node _z : z) {
            cond.add(_z);
        }


        this._z = cond;
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     */
    public static IndependenceFact serializableInstance() {
        return new IndependenceFact(new GraphNode("X"), new GraphNode("Y"));
    }

    public final Node getX() {
        return x;
    }

    public final Node getY() {
        return y;
    }

    public final List<Node> getZ() {
        return new LinkedList<Node>(_z);
    }

    public final int hashCode() {
        return 1;
    }

    public final boolean equals(Object obj) {
        if (!(obj instanceof IndependenceFact)) {
            return false;
        }

        IndependenceFact fact = (IndependenceFact) obj;

        Set<String> zString1 = new HashSet<String>();

        for (Node n : _z) {
            zString1.add(n.getName());
        }

        Set<String> zString2 = new HashSet<String>();

        for (Node n : fact._z) {
            zString2.add(n.getName());
        }

        String xN1 = x.getName();
        String xN2 = fact.x.getName();

        String yN1 = y.getName();
        String yN2 = fact.y.getName();

        return zString1.equals(zString2) && ((xN1.equals(xN2) && yN1.equals(yN2)) || xN1.equals(yN2) && yN1.equals(xN2));

//        return _z.equals(fact._z) && ((x.equals(fact.x) && (y.equals(fact.y))) || (x.equals(fact.y) && (y.equals(fact.x))));
    }

    public String toString() {
        StringBuilder builder = new StringBuilder();

        builder.append(x + " _||_ " + y);

        if (!_z.isEmpty()) {
            builder.append(" | ");

            List<Node> z = new ArrayList<Node>(this._z);
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
     * Note that this compareTo method gives a lexical ordering for independence facts and doesn't
     * reflect independence fact equality. So sorted sets should not be used to check for
     * independence fact existence, for instance. -jdramsey.
     */
    public int compareTo(Object o) {
        IndependenceFact fact = (IndependenceFact) o;

        int c = getX().getName().compareTo(fact.getX().getName());

        if (c != 0) return c;

        c = getY().getName().compareTo(fact.getY().getName());

        if (c != 0) return c;

        List<Node> z = getZ();
        List<Node> factZ = fact.getZ();

        int max = Math.max(z.size(), factZ.size());

        for (int i = 0; i < max; i++) {
            if (z.size() <= i && factZ.size() > i) {
                return -1;
            }
            if (factZ.size() <= i && z.size() > i) {
                return +1;
            }
            else {
                String s = z.get(i).getName();
                String s2 = factZ.get(i).getName();
                c = s.compareTo(s2);

                if (c != 0) return c;
            }
        }

        return 0;
    }

    /**
     * Adds semantic checks to the default deserialization method. This method
     * must have the standard signature for a readObject method, and the body of
     * the method must begin with "s.defaultReadObject();". Other than that, any
     * semantic checks can be specified and do not need to stay the same from
     * version to version. A readObject method of this form may be added to any
     * class, even if Tetrad sessions were previously saved out using a version
     * of the class that didn't include it. (That's what the
     * "s.defaultReadObject();" is for. See J. Bloch, Effective Java, for help.
     *
     * @throws java.io.IOException
     * @throws ClassNotFoundException
     */
    private void readObject(ObjectInputStream s)
            throws IOException, ClassNotFoundException {
        s.defaultReadObject();

        if (_z == null && !(z == null)) {
            _z = new HashSet<Node>(z);
        }
    }
}



