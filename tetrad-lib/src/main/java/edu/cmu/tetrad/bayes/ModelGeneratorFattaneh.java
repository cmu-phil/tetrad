///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014 by Peter Spirtes, Richard Scheines, Joseph   //
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

package edu.cmu.tetrad.bayes;

import edu.cmu.tetrad.graph.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * Provides static methods for generating variants of an input graph. For
 * example, the first method generates all 1 step modifications of the graph.
 *
 * @author Frank Wimberly
 */

final public class ModelGeneratorFattaneh {

	//	public static class ChangeType{
	//		Graph g;
	//		String[] s;
	//		ChangeType(Graph g, String[] s){
	//			this.g = g;
	//			this.s = s;
	//		}
	//		public Graph getGraph(){
	//			return this.g;
	//		}
	//		public String[] getString(){
	//			return this.s;
	//		}
	//	}
	/**
	 * This method takes an acyclic graph as input and returns a list of graphs
	 * each of which is a modification of the original graph with either an edge
	 * deleted, added or reversed.  Edges are not added or reversed if a cycle
	 * would result.
	 */

	public List<String[]> generate(Graph graph) {

		//Make sure the argument contains no cycles.
		if (graph.existsDirectedCycle()) {
			throw new IllegalArgumentException(
					"Input must not contain cycles.");
		}

		List<String[]> graphs = new LinkedList<String[]>();

		Set<Edge> allEdges = graph.getEdges();
		List<Node> allNodes = graph.getNodes();
		Collections.sort(allNodes);

		List<Edge> sortedEdges = new ArrayList<Edge>(allEdges);
		Edges.sortEdges(sortedEdges);

		//Add those graphs in which each edge is removed in turn.
		for (int i = 0; i < sortedEdges.size(); i++) {
			Edge allEdge1 = sortedEdges.get(i);
			//			Graph toAdd = new EdgeListGraph(graph);
			//			toAdd.removeEdge(allEdge1);
			String[] value = new String[3];
			value[0] = "Del";
			value[1]=allEdge1.getNode1().getName();
			value[2]=allEdge1.getNode2().getName();
			graphs.add(value);
		}


		//Add those graphs in which each edge is reversed
		for (int i = 0; i < sortedEdges.size(); i++) {
			Edge allEdge = sortedEdges.get(i);
			if (graph.getEdge(allEdge.getNode1(), allEdge.getNode2()).isDirected()) {
				Graph toAdd = new EdgeListGraph(graph);

				//			Endpoint e1 = allEdge.getEndpoint1();
				//			Endpoint e2 = allEdge.getEndpoint2();

				Node n1 = allEdge.getNode1();
				Node n2 = allEdge.getNode2();

				//			Edge newEdge = new Edge(n1, n2, e2, e1);

				toAdd.removeEdge(allEdge);
				if (!toAdd.existsDirectedPathFromTo(n1, n2)) {
					//				toAdd.addEdge(newEdge);
					String[] value = new String[3];
					value[0] = "Rev";
					value[1]=n1.getName();
					value[2]=n2.getName();
					graphs.add(value);
				}

			}

		}
		for (int i = 0; i < allNodes.size(); i++) {
			Node node1 = allNodes.get(i);

			for (int j = i + 1; j < allNodes.size(); j++) {
				Node node2 = allNodes.get(j);

				//If there is no edge between node1 and node2
				if (!graph.isParentOf(node1, node2) &&
						!graph.isParentOf(node2, node1) && !graph.isAdjacentTo(node1, node2)) {

					Graph toAdd1 = new EdgeListGraph(graph);
					//Make sure adding this edge won't introduce a cycle.
					if (!toAdd1.existsDirectedPathFromTo(node1, node2)) {  
						//						Edge newN2N1 = new Edge(node2, node1, Endpoint.TAIL,
						//								Endpoint.ARROW);
						//						toAdd1.addEdge(newN2N1);
						String[] value = new String[3];
						value[0] = "Add";
						value[1]=node2.getName();
						value[2]=node1.getName();
						graphs.add(value);
					}

					//Now create the graph with the edge added in the other direction
					Graph toAdd2 = new EdgeListGraph(graph);
					//Make sure adding this edge won't introduce a cycle.
					if (!toAdd2.existsDirectedPathFromTo(node2, node1)) {
						//						Edge newN1N2 = new Edge(node1, node2, Endpoint.TAIL,
						//								Endpoint.ARROW);
						//						toAdd2.addEdge(newN1N2);
						String[] value = new String[3];
						value[0] = "Add";
						value[1]=node1.getName();
						value[2]=node2.getName();
						graphs.add(value);
					}

				}

			}
		}

		return graphs;
	}
}




