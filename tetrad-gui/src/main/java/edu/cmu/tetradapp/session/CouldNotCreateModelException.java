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

package edu.cmu.tetradapp.session;


/**
 * Indicates that a model could not be created. As to which model it was, call the getModelClass() method.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class CouldNotCreateModelException extends Exception {

    /**
     * The class of the model that could not be created.
     */
    private final Class modelClass;

    /**
     * <p>Constructor for CouldNotCreateModelException.</p>
     *
     * @param modelClass a {@link java.lang.Class} object
     */
    public CouldNotCreateModelException(Class modelClass) {
        this.modelClass = modelClass;
    }

    /**
     * <p>Getter for the field <code>modelClass</code>.</p>
     *
     * @return a {@link java.lang.Class} object
     */
    public Class getModelClass() {
        return this.modelClass;
    }

    /**
     * <p>getMessage.</p>
     *
     * @return a {@link java.lang.String} object
     */
    public String getMessage() {
        return "Couldn't create that model; perhaps one of its parents is missing.";
    }
}






