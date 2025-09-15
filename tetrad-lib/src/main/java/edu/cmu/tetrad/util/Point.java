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

package edu.cmu.tetrad.util;

import org.apache.commons.math3.util.FastMath;
import org.jetbrains.annotations.NotNull;

/**
 * Implements an n-dimensional point.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class Point implements Comparable<Point> {

    /**
     * A vector representing the point coordinates.
     */
    private final Vector vector;

    /**
     * Constructs a point with coordinates as in the given vector.
     *
     * @param vector a vector representing the point coordinates, in order.
     */
    public Point(Vector vector) {
        this.vector = vector.copy();
    }

    /**
     * <p>getValue.</p>
     *
     * @param index Ibid.
     * @return Ibid.
     */
    public double getValue(int index) {
        return this.vector.get(index);
    }

    /**
     * <p>getSize.</p>
     *
     * @return Ibid.
     */
    public int getSize() {
        return this.vector.size();
    }

    /**
     * True iff the given object is a point with the same coordinates as this one.
     *
     * @param p Ibid.
     * @return Ibid.
     */
    public int compareTo(@NotNull Point p) {
        if (p == this) {
            return 0;
        }

        for (int i = 0; i < getSize(); i++) {
            if (getValue(i) != p.getValue(i)) {
                return (int) FastMath.signum(p.getValue(i) - getValue(i));
            }
        }

        return 0;
    }

    /**
     * <p>toString.</p>
     *
     * @return Ibid.
     */
    public String toString() {
        StringBuilder buf = new StringBuilder();

        buf.append("<");

        for (int i = 0; i < getSize(); i++) {
            buf.append(getValue(i));

            if (i < getSize() - 1) {
                buf.append(", ");
            }
        }

        buf.append(">");
        return buf.toString();
    }

    /**
     * <p>Getter for the field <code>vector</code>.</p>
     *
     * @return Ibid.
     */
    public Vector getVector() {
        return this.vector.copy();
    }
}





