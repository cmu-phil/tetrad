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

package edu.cmu.tetradapp.editor;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetradapp.util.IndTestType;

/**
 * Helps to set independence test types.
 *
 * @author josephramsey
 */
interface IndTestTypeSetter {
    /**
     * <p>getTestType.</p>
     *
     * @return a {@link edu.cmu.tetradapp.util.IndTestType} object
     */
    IndTestType getTestType();

    /**
     * <p>setTestType.</p>
     *
     * @param testType a {@link edu.cmu.tetradapp.util.IndTestType} object
     */
    void setTestType(IndTestType testType);

    /**
     * <p>getDataModel.</p>
     *
     * @return a {@link edu.cmu.tetrad.data.DataModel} object
     */
    DataModel getDataModel();

    /**
     * <p>getSourceGraph.</p>
     *
     * @return a {@link java.lang.Object} object
     */
    Object getSourceGraph();
}




