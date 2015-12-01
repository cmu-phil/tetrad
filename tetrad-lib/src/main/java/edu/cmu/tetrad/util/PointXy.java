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

package edu.cmu.tetrad.util;


/**
 * Stores a (x, y) point without having to use awt classes. Immutable.
 *
 * @author Joseph Ramsey
 */
public class PointXy implements TetradSerializable {
    static final long serialVersionUID = 23L;

    /**
     * The x coordinate.
     *
     * @serial
     */
    private final int x;

    /**
     * The y coordinate.
     *
     * @serial
     */
    private final int y;

    //=============================CONSTRUCTORS=======================//

    /**
     * Constructs a new point with the given coordinates.
     *
     * @param x The x coordinate for the point.
     * @param y The y coordinate for the point.
     */
    public PointXy(int x, int y) {
        this.x = x;
        this.y = y;
    }

    /**
     * Copy constructor.
     *
     * @param point The point to be copied.
     */
    public PointXy(PointXy point) {
        this.x = point.x;
        this.y = point.y;
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @return the examplar.
     */
    @SuppressWarnings({"UnusedDeclaration"})
    public static PointXy serializableInstance() {
        return new PointXy(1, 2);
    }

    //=============================PUBLIC METHODS====================//

    /**
     * @return Ibid.
     */
    public int getX() {
        return x;
    }

    /**
     * @return Ibid.
     */
    public int getY() {
        return y;
    }

    /**
     * @return true just in case o is a Point with the same x and y
     * coordinates.
     */
    public boolean equals(Object o) {
        if (!(o instanceof PointXy)) {
            throw new IllegalArgumentException("Not a Point object.");
        }

        if (o == this) {
            return true;
        }

        PointXy c = (PointXy) o;
        return c.x == this.x && c.y == this.y;
    }

    /**
     * @return a string representation of a point.
     */
    public String toString() {
        return "Point<" + x + "," + y + ">";
    }
}





