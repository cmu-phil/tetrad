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

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.Graph;

import java.util.List;

/**
 * Interface to indicate a class whose knowledge is capable of being edited by the knowledge editor.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public interface KnowledgeEditable {

    /**
     * <p>getKnowledge.</p>
     *
     * @return a copy of the knowledge for this class.
     */
    Knowledge getKnowledge();

    /**
     * Sets knowledge to a copy of the given object.
     *
     * @param knowledge the knowledge to set.
     */
    void setKnowledge(Knowledge knowledge);

    /**
     * <p>getSourceGraph.</p>
     *
     * @return the source graph. This will be used to arrange the graph in the knowledge editor in a recognizable way.
     */
    Graph getSourceGraph();

    /**
     * <p>getVarNames.</p>
     *
     * @return the variable names that the knowledge editor may use.
     */
    List<String> getVarNames();
}






