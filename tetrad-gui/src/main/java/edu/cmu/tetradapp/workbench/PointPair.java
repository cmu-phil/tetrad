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

package edu.cmu.tetradapp.workbench;

import java.awt.*;

/**
 * Holds a pair of points representing a line segment.
 *
 * @author Pucktada Treeratpituk
 * @version $Id: $Id
 */
public class PointPair {

    /**
     * The from point.
     */
    private final Point from;

    /**
     * The to point.
     */
    private final Point to;

    /**
     * This creates a PointPair with each Point at (0, 0).
     */
    public PointPair() {
        this(new Point(0, 0), new Point(0, 0));
    }

    /**
     * This creates a PointPair with <code>from</code> set to <code>p1</code>, and <code>to</code> set to
     * <code>p2</code>.
     *
     * @param p1 The <code>from</code> Point
     * @param p2 The <code>to</code> Point
     */
    public PointPair(Point p1, Point p2) {
        if (p1 == null || p2 == null) {
            throw new NullPointerException(
                    "Points p1 and p2 must be non-null.");
        }

        this.from = p1;
        this.to = p2;
    }

    /**
     * <p>Getter for the field <code>from</code>.</p>
     *
     * @return a {@link java.awt.Point} object
     */
    public Point getFrom() {
        return this.from;
    }

    /**
     * <p>Getter for the field <code>to</code>.</p>
     *
     * @return a {@link java.awt.Point} object
     */
    public Point getTo() {
        return this.to;
    }

    /**
     * <p>toString.</p>
     *
     * @return a {@link java.lang.String} object
     */
    public String toString() {
        return "<From " + this.from + " to " + this.to + ">";
    }
}






