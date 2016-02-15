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

import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.Edges;
import edu.cmu.tetrad.graph.Endpoint;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.Marshaller;
import org.graphdrawing.graphml.xmlns.EdgeType;
import org.graphdrawing.graphml.xmlns.GraphEdgedefaultType;
import org.graphdrawing.graphml.xmlns.GraphType;
import org.graphdrawing.graphml.xmlns.GraphmlType;
import org.graphdrawing.graphml.xmlns.NodeType;
import org.graphdrawing.graphml.xmlns.ObjectFactory;

/**
 * Last modified by Kevin Bui on Jan 12, 2016 2:54:32 PM.
 *
 * May 26, 2015 11:02:00 PM
 *
 * @author Jeremy Espino MD
 */
public class GraphmlSerializer {

    public static String serialize(Graph graph, String graphId) {

        List<Node> nodes = graph.getNodes();
        Set<Edge> edgesSet = graph.getEdges();

        try {
            GraphmlType graphmlType = new GraphmlType();

            GraphType graphType = new GraphType();
            graphType.setId(graphId);

            graphType.setEdgedefault(GraphEdgedefaultType.DIRECTED);

            List<Object> nodesOrEdges = graphType.getDataOrNodeOrEdge();

            for (Node node : nodes) {
                NodeType nodeType = new NodeType();
                nodeType.setId(node.getName());
                nodesOrEdges.add(nodeType);
            }

            List<Edge> edges = new ArrayList<>(edgesSet);
            Edges.sortEdges(edges);
            for (Edge edge : edges) {
                EdgeType edgeType = new EdgeType();
                if (edge.getEndpoint1() == Endpoint.TAIL && edge.getEndpoint2() == Endpoint.ARROW) {
                    edgeType.setSource(edge.getNode1().getName());
                    edgeType.setTarget(edge.getNode2().getName());
                    edgeType.setDirected(true);
                } else if (edge.getEndpoint1() == Endpoint.ARROW && edge.getEndpoint2() == Endpoint.TAIL) {
                    edgeType.setSource(edge.getNode2().getName());
                    edgeType.setTarget(edge.getNode1().getName());
                    edgeType.setDirected(true);
                } else if (edge.getEndpoint1() == Endpoint.TAIL && edge.getEndpoint2() == Endpoint.TAIL) {
                    edgeType.setSource(edge.getNode2().getName());
                    edgeType.setTarget(edge.getNode1().getName());
                    edgeType.setDirected(false);
                } else {
                    // cannot handle all edges yet
                    System.out.println("Encountered edge we currently don't handle when serializing graphml: " + edge.toString());
                    edgeType = null;
                }
                if (edgeType != null) {
                    nodesOrEdges.add(edgeType);
                }
            }

            graphmlType.getGraphOrData().add(graphType);

            // TODO: handle ambiguousTriples
            // TODO: handle underLineTriples
            // TODO: handle dottedUnderLineTriples
            // Context is the name of package
            String context = "org.graphdrawing.graphml.xmlns";
            // Initialise JAXB Context
            JAXBContext jc = JAXBContext.newInstance(context);

            // Always use factory methods to initialise XML classes
            ObjectFactory factory = new ObjectFactory();
            JAXBElement<GraphmlType> root = factory.createGraphml(graphmlType);

            // Now Create JAXB XML Marshallar
            Marshaller m = jc.createMarshaller();
            m.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);

            // Write the XML File
            java.io.StringWriter sw = new StringWriter();

            Marshaller marshaller = jc.createMarshaller();
            marshaller.setProperty(Marshaller.JAXB_ENCODING, "UTF-8");
            marshaller.marshal(root, sw);

            return sw.toString();

        } catch (Exception exception) {
            exception.printStackTrace(System.err);
        }

        return null;
    }

}
