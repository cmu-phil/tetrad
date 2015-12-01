///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015 by Peter Spirtes, Richard Scheines, Joseph   //
// Ramsey, and Clark Glymour.                                                //
//                                                                           //
// This program is free software; you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation; either version 2 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program; if not, write to the Free Software               //
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.search;

import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Edges;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.util.CombinationGenerator;

import java.util.ArrayList;
import java.util.List;

/**
 * Given a graphs, lists all DAGs that result from directing the undirected edges in that graphs every possible way.
 * Does it the old fashioned way, by actually producing all of the graphs you would get by orienting each uoriented edge
 * each way, in every combination, and the rembembering just the ones that were acyclic.
 *
 * @author Joseph Ramsey
 */
public class DirectedGraphIterator {
    private List<Graph> graphs = new ArrayList<Graph>();
    private int index = -1;


    /**
     * The given graphs must be a graphs. If it does not consist entirely of directed and undirected edges and if it is
     * not acyclic, it is rejected.
     *
     * @throws IllegalArgumentException if the graphs is not a graphs.
     */
    public DirectedGraphIterator(Graph graph) {
        graph = new EdgeListGraph(graph);
//        graph = DataGraphUtils.undirectedGraph(graph);
        List<Edge> undirectedEdges = new ArrayList<Edge>();

        for (Edge edge : graph.getEdges()) {
            if (Edges.isUndirectedEdge(edge)) {
                undirectedEdges.add(edge);
            }
        }

        int[] dims = new int[undirectedEdges.size()];

        for (int i = 0; i < undirectedEdges.size(); i++) {
            dims[i] = 2;
        }

        CombinationGenerator generator = new CombinationGenerator(dims);
        int[] combination;

        while ((combination = generator.next()) != null) {
            for (int k = 0; k < combination.length; k++) {
                Edge edge = undirectedEdges.get(k);
                graph.removeEdge(edge.getNode1(), edge.getNode2());

                if (combination[k] == 0) {
                    graph.addDirectedEdge(edge.getNode1(), edge.getNode2());
                } else {
                    graph.addDirectedEdge(edge.getNode2(), edge.getNode1());
                }
            }

//            if (!graph.existsDirectedCycle()) {
            this.graphs.add(new EdgeListGraph(graph));
//            }
        }

        System.out.println("# directed graphs = " + graphs.size());
    }

    /**
     * Successive calls to this method return successive DAGs in the pattern, in a more or less natural enumeration of
     * them in which an arbitrary undirected edge is picked, oriented one way, Meek rules applied, then a remaining
     * unoriented edge is picked, oriented one way, and so on, until a DAG is obtained, and then by backtracking the
     * other orientation of each chosen edge is tried. Nonrecursive, obviously.
     * <p>
     *
     * @return a Graph instead of a DAG because sometimes, due to faulty patterns, a cyclic graphs is produced, and the
     * end-user may need to decide what to do with it. The simplest thing is to construct a DAG (Dag(graphs)) and catch
     * an exception.
     */
    public Graph next() {
        ++index;

        if (index < graphs.size()) {
            return graphs.get(index);
        } else {
            return null;
        }
    }

    /**
     * @return true just in case there is still a DAG remaining in the enumeration of DAGs for this pattern.
     */
    public boolean hasNext() {
        return index + 1 < graphs.size();
    }
}


