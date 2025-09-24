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

package edu.cmu.tetrad.search.utils;

import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;

import java.util.List;

/**
 * Provides an interface for Purify algorithm.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public interface IPurify {
    /**
     * <p>purify.</p>
     *
     * @param partition a {@link java.util.List} object
     * @return a {@link java.util.List} object
     */
    List<List<Node>> purify(List<List<Node>> partition);

    /**
     * <p>setTrueGraph.</p>
     *
     * @param mim a {@link edu.cmu.tetrad.graph.Graph} object
     */
    void setTrueGraph(Graph mim);
}




