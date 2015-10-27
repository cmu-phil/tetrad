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

package edu.cmu.tetradapp.workbench;

import java.awt.*;

/**
 * Holds a pair of points representing a line segment.
 *
 * @author Pucktada Treeratpituk
 */
public class PointPair {

    /**
     * The from point.
     */
    private Point from;

    /**
     * The to point.
     */
    private Point to;

    /**
     * This creates a PointPair with each Point at (0, 0).
     */
    public PointPair() {
        this(new Point(0, 0), new Point(0, 0));
    }

    /**
     * This creates a PointPair with <code>from</code> set to <code>p1</code>,
     * and <code>to</code> set to <code>p2</code>.
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

    public Point getFrom() {
        return from;
    }

    public Point getTo() {
        return to;
    }

    public String toString() {
        return "<From " + from + " to " + to + ">";
    }
}





