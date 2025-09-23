/// ////////////////////////////////////////////////////////////////////////////
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
/// ////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.utils.SepsetMap;

/**
 * The IFas interface represents a framework for performing fast adjacency search in graph structures. It extends the
 * IGraphSearch interface to provide additional methods for handling adjacency relationships, conditional independence
 * tests, and background knowledge during the search process.
 */
public interface IFas extends IGraphSearch {
    /**
     * Run adjacency search and return the skeleton graph.
     */
    @Override
    Graph search() throws InterruptedException;

    /**
     * Sep-sets discovered during the search.
     */
    SepsetMap getSepsets();

    /**
     * Sets the background knowledge to be used during the search process.
     *
     * @param knowledge the background knowledge, encapsulated within a {@code Knowledge} object, containing information
     *                  about prohibited edges, required edges, or any other relevant constraints for the adjacency
     *                  search.
     */
    void setKnowledge(Knowledge knowledge);

    /**
     * Sets the depth limit for the search process. The depth determines how far into the graph the algorithm will
     * explore adjacency relationships.
     *
     * @param depth the maximum depth to explore; a non-negative integer. A depth of -1 indicates no depth limit.
     */
    void setDepth(int depth);

    /**
     * Sets the verbosity of the search process. When set to {@code true}, additional debugging or informational output
     * may be produced during the execution of the adjacency search.
     *
     * @param verbose a boolean flag indicating whether verbose output should be enabled.
     */
    void setVerbose(boolean verbose);

    /**
     * Sets whether the adjacency search should be stable. When set to {@code true}, the search process ensures that
     * reversing the order of variable consideration does not affect the result of the adjacency search.
     *
     * @param stable a boolean flag indicating whether stability should be enforced during the adjacency search
     *               process.
     */
    void setStable(boolean stable);
}
