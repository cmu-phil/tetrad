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

import java.io.ObjectStreamException;

/**
 * A typesafe enum of the types of the various comparisons parameter may have
 * with respect to one another for SEM estimation.
 *
 * @author Joseph Ramsey
 */
public class ParamComparison {

    /**
     * Indicates that the two freeParameters are not compared.
     */
    public static final ParamComparison NC = new ParamComparison("NC");

    /**
     * Indicates that the first parameter is less than the second.
     */
    public static final ParamComparison LT = new ParamComparison("LT");

    /**
     * Indicates that the first parameter is equal to the second.
     */
    public static final ParamComparison EQ = new ParamComparison("EQ");

    /**
     * Indicates that the first parameter is less than or equal to the second.
     */
    public static final ParamComparison LE = new ParamComparison("LE");

    /**
     * The name of this type.
     */
    private final transient String name;

    /**
     * Protected constructor for the types; this allows for extension in case
     * anyone wants to add formula types.
     */
    protected ParamComparison(String name) {
        this.name = name;
    }

    /**
     * Prints out the name of the type.
     */
    public String toString() {
        return name;
    }

    // Declarations required for serialization.
    private static int nextOrdinal = 0;
    private final int ordinal = nextOrdinal++;
    private static final ParamComparison[] TYPES = {NC, LT, EQ, LE};

    Object readResolve() throws ObjectStreamException {
        return TYPES[ordinal]; // Canonicalize.
    }
}





