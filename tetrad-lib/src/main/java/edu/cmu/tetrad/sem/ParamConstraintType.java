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

package edu.cmu.tetrad.sem;

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
     * <p>
     * This enum value is used to represent the equality constraint on a parameter. It indicates that the parameter
     * value should be equal to a specific value.
     */
    EQ("EQ"),
    /**
     * Represents a parameter constraint type NONE.
     * <p>
     * This enum value is used to represent the absence of a constraint on a parameter. It indicates that there is no
     * specific constraint on the parameter value.
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




