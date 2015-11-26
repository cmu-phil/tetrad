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

package edu.cmu.tetrad.sem;

import edu.cmu.tetrad.util.TetradSerializable;

/**
 * Implements an ordered pair of objects (a, b) suitable for storing in
 * HashSets.  The hashCode() method is overridden so that the hashcode of (a1,
 * b1) == the hashcode of (a2, b2) just in case a1 == a2 and b1 == b2.
 *
 * @author Joseph Ramsey
 */
public class ParameterPair implements TetradSerializable {
    static final long serialVersionUID = 23L;

    /**
     * The first element of the ordered pair.  Can be null.
     *
     * @serial May be null.
     */
    private Parameter a;

    /**
     * The second element of the ordered pair.  Can be null.
     *
     * @serial May be null.
     */
    private Parameter b;

    //=============================CONSTRUCTORS==========================//

    /**
     * Constructs a new (blank) ordered pair where a = null and b = null.
     */
    public ParameterPair() {
        a = null;
        b = null;
    }

    /**
     * Constructs a new ordered pair (a, b).
     *
     * @param a the first element of the ordered pair.
     * @param b the second element of the ordered pair.
     */
    public ParameterPair(Parameter a, Parameter b) {
        setPair(a, b);
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     */
    public static ParameterPair serializableInstance() {
        return new ParameterPair();
    }

    //============================PUBLIC METHODS========================//

    /**
     * Method getNumObjects
     *
     * @return the first element of the ordered pair.
     */
    public Parameter getA() {
        return a;
    }

    /**
     * Method getNumObjects
     *
     * @return the second element of the ordered pair.
     */
    public Parameter getB() {
        return b;
    }

    /**
     * Tests whether this object pair is equal to a second object pair by
     * looking to see whether each element a and b is equal to its corresponding
     * element.
     *
     * @param object the object pair putatively equal to this one.
     * @return true if the object pairs are equals, false if not.
     */
    public boolean equals(Object object) {
        if (object == null) {
            return false;
        }

        if (!(object instanceof ParameterPair)) {
            return false;
        }

        ParameterPair pair = (ParameterPair) object;
        return a.equals(pair.a) && b.equals(pair.b);
    }

    /**
     * @return a hashcode such that the hashcode of (a1, b1) == the hashcode of
     * (a2, b2) just in case a1 == a2 and b1 == b2.
     *
     * @return this hashcode.
     */
    public int hashCode() {
        int hashCode = 31 + ((a == null) ? 0 : a.hashCode());

        return 31 * hashCode + ((b == null) ? 0 : b.hashCode());
    }

    /**
     * Sets the elements of this ordered pair to a new pair (a, b).
     *
     * @param a the new first element.
     * @param b the new seconde element.
     * @return the revised pair (a, b), just in case anyone wants an in-line
     *         reference to it.  (This method just sets a and b to the new
     *         values and returns a reference to itself.)
     */
    public ParameterPair setPair(Parameter a, Parameter b) {
        this.a = a;
        this.b = b;

        return this;
    }
}





