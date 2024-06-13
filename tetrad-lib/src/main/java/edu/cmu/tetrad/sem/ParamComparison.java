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

/**
 * An enum of the types of the various comparisons a parameter may have with respect to one another for SEM estimation.
 */
public enum ParamComparison {
    /**
     * Represents the "Non-comparable" comparison type for a parameter in SEM estimation.
     *
     * This type of comparison indicates that the parameter is not comparable to any other parameter
     * in the structural equation model.
     */
    NC("NC"),
    /**
     * An enum representing the "EQ" comparison type for a parameter in SEM estimation.
     *
     * This type of comparison indicates that the parameter is equal to another parameter
     * in the structural equation model.
     */
    EQ("EQ"),
    /**
     * Represents the "LT" comparison type for a parameter in SEM estimation.
     *
     * This type of comparison indicates that the parameter is less than another parameter
     * in the structural equation model.
     */
    LT("LT"),
    /**
     * An enum value representing the "LE" comparison type for a parameter in SEM estimation.
     *
     * This type of comparison indicates that the parameter is less than or equal to another parameter
     * in the structural equation model.
     */
    LE("LE");

    private final String name;

    ParamComparison(String name) {
        this.name = name;
    }

    public String toString() {
        return this.name;
    }
}




