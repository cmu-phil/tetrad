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

package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.data.KnowledgeEdge;
import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.Edges;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * Identifies violations of knowledge for a given graph. Both forbidden and required knowledge is checked, by separate
 * methods. Sorted lists of edges violating knowledge are returned.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class CheckKnowledge {

    /**
     * Private constructor to prevent instantiation.
     */
    private CheckKnowledge() {
    }

    /**
     * Returns a sorted list of edges that violate the given knowledge.
     *
     * @param graph     the graph.
     * @param knowledge the knowledge.
     * @return a sorted list of edges that violate the given knowledge.
     */
    public static List<Edge> forbiddenViolations(Graph graph, Knowledge knowledge) {
        List<Edge> forbiddenViolations = new ArrayList<>();

        for (Edge edge : graph.getEdges()) {
            if (edge.isDirected()) {
                Node x = Edges.getDirectedEdgeTail(edge);
                Node y = Edges.getDirectedEdgeHead(edge);

                if (knowledge.isForbidden(x.getName(), y.getName())) {
                    forbiddenViolations.add(edge);
                }
            }
        }

        Collections.sort(forbiddenViolations);

        return forbiddenViolations;
    }

    /**
     * Returns a sorted list of edges that are required by knowledge but which do not appear in the graph.
     *
     * @param graph     the graph.
     * @param knowledge the knowledge.
     * @return a sorted list of edges that are required by knowledge but which do not appear in the graph.
     */
    public static List<Edge> requiredViolations(Graph graph, Knowledge knowledge) {
        List<Edge> requiredViolations = new ArrayList<>();

        Iterator<KnowledgeEdge> knowledgeEdgeIterator = knowledge.requiredEdgesIterator();

        while (knowledgeEdgeIterator.hasNext()) {
            KnowledgeEdge edge = knowledgeEdgeIterator.next();
            Node x = graph.getNode(edge.getFrom());
            Node y = graph.getNode(edge.getTo());

            if (!graph.containsEdge(Edges.directedEdge(x, y))) {
                requiredViolations.add(Edges.directedEdge(x, y));
            }
        }

        Collections.sort(requiredViolations);

        return requiredViolations;
    }
}

