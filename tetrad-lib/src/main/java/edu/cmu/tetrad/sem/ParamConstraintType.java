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

import java.io.ObjectStreamException;

/**
 * A typesafe enum of the types of the types of constraints on freeParameters for
 * SEM models (LT, GT, EQ). For example, LT constraints require that the value
 * of a parameter in a given SemIm be less than some value.  That value may be a
 * given number (double) or the value of another parameter.
 *
 * @author Frank Wimberly following Joe Ramsey's ParamType class.
 */
public class ParamConstraintType implements TetradSerializable {
    static final long serialVersionUID = 23L;

    /**
     * A "less than" constraint.
     */
    public static final ParamConstraintType LT = new ParamConstraintType("LT");

    /**
     * A "greater than" constraint.
     */
    public static final ParamConstraintType GT = new ParamConstraintType("GT");

    /**
     * An "equal to" constraint.
     */
    public static final ParamConstraintType EQ = new ParamConstraintType("EQ");

    /**
     * No constraint.
     */
    public static final ParamConstraintType NONE =
            new ParamConstraintType("NONE");

    /**
     * The name of this type.
     */
    private final transient String name;

    /**
     * Protected constructor for the types; this allows for extension in case
     * anyone wants to add formula types.
     */
    private ParamConstraintType(String name) {
        this.name = name;
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     */
    public static ParamConstraintType serializableInstance() {
        return ParamConstraintType.LT;
    }

    /**
     * Prints out the name of the type.
     */
    public String toString() {
        return name;
    }

    // Declarations required for serialization.
    private static int NEXT_ORDINAL = 0;
    private final int ordinal = NEXT_ORDINAL++;
    private static final ParamConstraintType[] TYPES = {LT, GT, EQ, NONE};

    Object readResolve() throws ObjectStreamException {
        return TYPES[ordinal]; // Canonicalize.
    }
}




