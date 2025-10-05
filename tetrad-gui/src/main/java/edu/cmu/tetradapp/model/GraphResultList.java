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

public final class GraphResultList {
    private final List<Graph> graphs = new ArrayList<>();
    private final List<String> names = new ArrayList<>();

    public void add(Graph g, String name) {
        graphs.add(g);
        names.add(name);
    }

    public List<Graph> getGraphs() {
        return graphs;
    }

    public List<String> getNames() {
        return names;
    }

    public int size() {
        return graphs.size();
    }

    public Graph get(int i) {
        return graphs.get(i);
    }

    public String getName(int i) {
        return names.get(i);
    }
}
