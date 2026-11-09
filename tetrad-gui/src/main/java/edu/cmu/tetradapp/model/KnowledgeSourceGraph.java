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

/**
 * Implemented by knowledge box models whose knowledge was derived from a graph, so that the
 * knowledge editor can show that graph, with an explanation of how the knowledge was read off
 * it, instead of opening on the (mostly empty) tiers display. This is a display-only interface;
 * it is deliberately distinct from {@link edu.cmu.tetradapp.model.KnowledgeEditable#getSourceGraph()},
 * which has a different, layout-related meaning.
 *
 * <p>Implementing this interface has no effect on serialization, so adding it to an existing
 * model class leaves previously saved sessions loadable.
 *
 * @author josephramsey
 */
public interface KnowledgeSourceGraph {

    /**
     * The graph the knowledge was derived from, or null if no graph is available (for instance,
     * when it would be too large to be worth constructing). The graph is used for display only
     * and is never edited by the knowledge editor.
     *
     * @return the graph, or null
     */
    Graph getKnowledgeSourceGraph();

    /**
     * A short plain-text explanation, shown above the graph, of how the knowledge was derived
     * from it. This may be computed from the model's current knowledge, so it should remain
     * truthful for models loaded from older sessions.
     *
     * @return the explanation; may be null or blank if there is nothing to say
     */
    String getKnowledgeSourceDescription();

    /**
     * The title of the tab in which the graph is displayed.
     *
     * @return the tab title
     */
    default String getKnowledgeSourceTabTitle() {
        return "Source Graph";
    }
}
