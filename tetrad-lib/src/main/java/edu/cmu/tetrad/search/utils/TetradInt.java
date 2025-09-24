///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2025 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
// and Richard Scheines.                                                     //
//                                                                           //
// This program is free software: you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation, either version 3 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program.  If not, see <https://www.gnu.org/licenses/>.    //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.search.utils;

import edu.cmu.tetrad.util.TetradSerializable;

import java.util.Objects;

/**
 * Represents an ordered tetrad (quartet) of nodes, where the order of nodes within {i, j} and {k, l} does not matter,
 * but the order of the pairs &lt;{i, j}, {k, l}&gt; does matter.
 *
 * @param i The first node.
 * @param j The second node.
 * @param k The third node.
 * @param l The fourth node.
 */
public record TetradInt(int i, int j, int k, int l) implements TetradSerializable {

    /**
     * Initializes a TetradInt record while ensuring that all provided nodes are distinct.
     *
     * @param i The first node of the tetrad.
     * @param j The second node of the tetrad.
     * @param k The third node of the tetrad.
     * @param l The fourth node of the tetrad.
     * @throws IllegalArgumentException If any two nodes among i, j, k, and l are not distinct.
     */
    public TetradInt {
        if (i == j || i == k || i == l || j == k || j == l || k == l) {
            throw new IllegalArgumentException("Nodes must be distinct.");
        }
    }

    /**
     * Computes the hash code for this TetradInt instance. The hash code calculation takes into account the unordered
     * pairs {i, j} and {k, l}, ensuring that the order of nodes within each pair does not affect the result. However,
     * the order of the two pairs &lt;{i, j}, {k, l}&gt; impacts the hash code.
     *
     * @return The hash code for this TetradInt instance based on the sorted pairs of nodes &lt;{i, j}, {k, l}&gt;.
     */
    @Override
    public int hashCode() {
        // Sort nodes within each pair, then hash the ordered pairs
        int min1 = Math.min(i, j);
        int max1 = Math.max(i, j);
        int min2 = Math.min(k, l);
        int max2 = Math.max(k, l);

        // Hash the ordered pairs <{i, j}, {k, l}>
        return Objects.hash(min1, max1, min2, max2);
    }

    /**
     * Compares this TetradInt object with the specified object for equality. Two TetradInt objects are considered equal
     * if their unordered pairs {i, j} and {k, l} are identical, in the same order or reversed, but the order of the
     * pairs &lt;{i, j}, {k, l}&gt; must match.
     *
     * @param o The object to compare with this TetradInt for equality.
     * @return true if the specified object is equal to this TetradInt, false otherwise.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TetradInt(int i1, int j1, int k1, int l1))) return false;

        // Sort nodes within each pair
        int min1This = Math.min(this.i, this.j);
        int max1This = Math.max(this.i, this.j);
        int min2This = Math.min(this.k, this.l);
        int max2This = Math.max(this.k, this.l);

        int min1Other = Math.min(i1, j1);
        int max1Other = Math.max(i1, j1);
        int min2Other = Math.min(k1, l1);
        int max2Other = Math.max(k1, l1);

        // Compare ordered pairs <{i, j}, {k, l}>
        return min1This == min1Other && max1This == max1Other &&
               min2This == min2Other && max2This == max2Other;
    }

    /**
     * Returns a string representation of this TetradInt object. The string format represents the sets of pairs &lt;{i,
     * j}, {k, l}&gt; where the individual pairs are sorted in ascending order, ensuring consistent representation
     * regardless of the original order of the individual nodes i, j, k, and l.
     *
     * @return A string representation of this TetradInt instance, displaying the ordered pairs of nodes.
     */
    @Override
    public String toString() {
        return String.format("TetradInt(<{%d, %d}, {%d, %d}>)", i, j, k, l);
    }
}

