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

package edu.cmu.tetradapp.model;

import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.util.Parameters;

/**
 * <p>GraphSettable interface.</p>
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public interface GraphSettable extends GraphSource {
    /**
     * <p>getParameters.</p>
     *
     * @return a {@link edu.cmu.tetrad.util.Parameters} object
     */
    Parameters getParameters();

    /**
     * <p>setGraph.</p>
     *
     * @param newValue a {@link edu.cmu.tetrad.graph.Graph} object
     */
    void setGraph(Graph newValue);

    /**
     * <p>getNumModels.</p>
     *
     * @return a int
     */
    int getNumModels();

    /**
     * <p>getModelSourceName.</p>
     *
     * @return a {@link java.lang.String} object
     */
    String getModelSourceName();

    /**
     * <p>getModelIndex.</p>
     *
     * @return a int
     */
    int getModelIndex();

    /**
     * <p>setModelIndex.</p>
     *
     * @param index a int
     */
    void setModelIndex(int index);
}

