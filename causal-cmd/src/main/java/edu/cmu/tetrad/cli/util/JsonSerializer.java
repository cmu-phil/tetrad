/*
 * Copyright (C) 2016 University of Pittsburgh.
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
package edu.cmu.tetrad.cli.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import edu.cmu.tetrad.cli.json.JsonEdge;
import edu.cmu.tetrad.cli.json.JsonEdgeSet;
import edu.cmu.tetrad.cli.json.JsonGraph;
import edu.cmu.tetrad.cli.json.JsonNode;
import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.Edges;
import edu.cmu.tetrad.graph.Endpoint;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import javax.xml.bind.JAXBException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactoryConfigurationError;

/**
 *
 * Outputs a json version of the graph
 *
 *
 * @author Jeremy Espino MD
 */
public class JsonSerializer {

    public static String serialize(Graph graph, String graphId) throws JAXBException {
        List<Node> nodes = graph.getNodes();
        Set<Edge> edgesSet = graph.getEdges();
        HashMap<Node, Integer> nodeIndexMap = new HashMap<>();

        // declare the output JSON object
        JsonGraph jsonGraph = new JsonGraph();
        jsonGraph.name = graphId;

        int index = 0;
        for (Node node : nodes) {
            jsonGraph.nodes.add(new JsonNode(node.getName()));
            nodeIndexMap.put(node, index);
            index++;
        }

        jsonGraph.edgeSets.add(new JsonEdgeSet());
        List<Edge> edges = new ArrayList<>(edgesSet);
        Edges.sortEdges(edges);
        for (Edge edge : edges) {
            JsonEdge jsonEdge = new JsonEdge();
            jsonEdge.source = nodeIndexMap.get(edge.getNode1());
            jsonEdge.target = nodeIndexMap.get(edge.getNode2());

            if (edge.getEndpoint1() == Endpoint.TAIL && edge.getEndpoint2() == Endpoint.ARROW) {
                jsonEdge.etype = "-->";
            } else if (edge.getEndpoint1() == Endpoint.ARROW && edge.getEndpoint2() == Endpoint.TAIL) {
                jsonEdge.etype = "<--";
            } else if (edge.getEndpoint1() == Endpoint.TAIL && edge.getEndpoint2() == Endpoint.TAIL) {
                jsonEdge.etype = "---";
            } else if (edge.getEndpoint1() == Endpoint.CIRCLE && edge.getEndpoint2() == Endpoint.ARROW) {
                jsonEdge.etype = "o->";
            } else if (edge.getEndpoint1() == Endpoint.ARROW && edge.getEndpoint2() == Endpoint.CIRCLE) {
                jsonEdge.etype = "<-o";
            } else if (edge.getEndpoint1() == Endpoint.CIRCLE && edge.getEndpoint2() == Endpoint.CIRCLE) {
                jsonEdge.etype = "o-o";
            } else {
                // cannot handle all edges yet
                System.out.println("Encountered edge we currently don't handle when serializing to json: " + edge.toString());
                jsonEdge.etype = null;
            }
            if (jsonEdge.etype != null) {
                jsonGraph.edgeSets.get(0).edges.add(jsonEdge);
            }
        }

        Gson gson = new GsonBuilder().setPrettyPrinting().create();

        return gson.toJson(jsonGraph);
    }

    public static void writeToStream(String json, PrintStream writer) throws IllegalArgumentException, TransformerException, TransformerFactoryConfigurationError {
        writer.print(json);
        writer.close();
    }

}
