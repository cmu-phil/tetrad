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

package edu.cmu.tetrad.data;

import edu.cmu.tetrad.graph.Node;

/**
 * Interface implemented by classes, instantiations of which are capable of serving as variables for columns in a
 * DataSet. Such a class provides needed details as to how the data in its column are to be interpreted. In particular,
 * a variable has a name and specifies a value in the column that is to be interpreted as a missing datum. The variable
 * also specifies how external data (in String form, say) is to be converted into raw data of the type used in the
 * column in question.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public interface Variable extends Node {

    /**
     * Required serial version UID for serialization. Must be 23L.
     */
    long serialVersionUID = 23L;

    /**
     * Returns the name of the variable.
     *
     * @return the missing value marker as an object--i.e. a double if continuous, an Integer if discrete, etc.
     */
    Object getMissingValueMarker();

    /**
     * Tests whether the given value is the missing data marker.
     *
     * @param value The object value one wants to check as a missing value.
     * @return true iff the given object is equals to <code>getMissingValueMarker()</code>.
     */
    boolean isMissingValue(Object value);

    /**
     * Checks to see whether the passed value can be converted into a value for this variable.
     *
     * @param value The object value to be checked. For instance, for a continuous variable, this would be a Double, for
     *              a discrete variable, and Integer, etc.
     * @return true iff the object is a valid value for this variable.
     */
    boolean checkValue(Object value);
}






