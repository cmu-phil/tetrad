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

import java.util.ArrayList;
import java.util.List;

/**
 * An ordered list of graphs, each paired with a name.
 */
public final class GraphResultList {
    private final List<Graph> graphs = new ArrayList<>();
    private final List<String> names = new ArrayList<>();

    /**
     * Constructs a new, empty GraphResultList.
     */
    public GraphResultList() {
    }

    /**
     * Appends a graph with the given name.
     *
     * @param g    the graph
     * @param name the name of the graph
     */
    public void add(Graph g, String name) {
        graphs.add(g);
        names.add(name);
    }

    /**
     * Returns the graphs, in the order added. The returned list is the live backing list.
     *
     * @return the graphs
     */
    public List<Graph> getGraphs() {
        return graphs;
    }

    /**
     * Returns the graph names, in the order added. The returned list is the live backing list.
     *
     * @return the names
     */
    public List<String> getNames() {
        return names;
    }

    /**
     * Returns the number of graphs in the list.
     *
     * @return the number of graphs
     */
    public int size() {
        return graphs.size();
    }

    /**
     * Returns the graph at the given index.
     *
     * @param i the index
     * @return the graph at index i
     */
    public Graph get(int i) {
        return graphs.get(i);
    }

    /**
     * Returns the name of the graph at the given index.
     *
     * @param i the index
     * @return the name of the graph at index i
     */
    public String getName(int i) {
        return names.get(i);
    }
}
