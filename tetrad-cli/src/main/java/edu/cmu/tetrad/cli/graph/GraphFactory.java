/*
 * Copyright (C) 2015 University of Pittsburgh.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
 */
package edu.cmu.tetrad.cli.graph;

import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * Dec 2, 2015 3:18:05 PM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public class GraphFactory {

    private GraphFactory() {
    }

    public static Graph createRandomForwardEdges(int numofVars, double edgesPerNode) {
        List<Node> vars = new ArrayList<>();
        for (int i = 0; i < numofVars; i++) {
            vars.add(new ContinuousVariable("X" + i));
        }

        return GraphUtils.randomGraphRandomForwardEdges(vars, 0, (int) (numofVars * edgesPerNode), 30, 15, 15, false);
    }

}
