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

package edu.cmu.tetrad.sem;

import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetrad.util.TetradSerializable;

import java.io.*;

/**
 * Enum for representing different types of parameter constraints.
 */
public enum ParamConstraintType {
    /**
     * Represents a parameter constraint type LT (less than).
     */
    LT("LT"),
    /**
     * Represents a parameter constraint type GT (greater than).
     */
    GT("GT"),
    /**
     * The EQ represents a parameter constraint type EQ (equal).
     *
     * This enum value is used to represent the equality constraint on a parameter. It indicates that the parameter value
     * should be equal to a specific value.
     */
    EQ("EQ"),
    /**
     * Represents a parameter constraint type NONE.
     *
     * This enum value is used to represent the absence of a constraint on a parameter. It indicates that there is no specific
     * constraint on the parameter value.
     */
    NONE("NONE");

    private final String name;

    ParamConstraintType(String name) {
        this.name = name;
    }

    public String toString() {
        return this.name;
    }
}



