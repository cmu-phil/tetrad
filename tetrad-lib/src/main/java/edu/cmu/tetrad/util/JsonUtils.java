package edu.cmu.tetrad.util;

import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.graph.EdgeTypeProbability.EdgeType;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Dec 9, 2016 5:43:47 PM
 *
 * @author Chirayu (Kong) Wongchokprasitti, PhD
 */
public class JsonUtils {

    public static Graph parseJSONObjectToTetradGraph(final String jsonResponse) {
        return JsonUtils.parseJSONObjectToTetradGraph(new JSONObject(jsonResponse));
    }

    public static Graph parseJSONObjectToTetradGraph(final JSONObject jObj) {
        if (!jObj.isNull("graph")) {
            return JsonUtils.parseJSONObjectToTetradGraph(jObj.getJSONObject("graph"));
        }

        // Node
        final List<Node> nodes = JsonUtils.parseJSONArrayToTetradNodes(jObj.getJSONArray("nodes"));
        final EdgeListGraph graph = new EdgeListGraph(nodes);

        // Edge
        final Set<Edge> edges = JsonUtils.parseJSONArrayToTetradEdges(graph, jObj.getJSONArray("edgesSet"));
        for (final Edge edge : edges) {
            graph.addEdge(edge);
        }

        // ambiguousTriples
        final Set<Triple> ambiguousTriples = JsonUtils.parseJSONArrayToTetradTriples(jObj.getJSONArray("ambiguousTriples"));
        for (final Triple triple : ambiguousTriples) {
            graph.addAmbiguousTriple(triple.getX(), triple.getY(), triple.getZ());
        }

        // underLineTriples
        final Set<Triple> underLineTriples = JsonUtils.parseJSONArrayToTetradTriples(jObj.getJSONArray("underLineTriples"));
        for (final Triple triple : underLineTriples) {
            graph.addUnderlineTriple(triple.getX(), triple.getY(), triple.getZ());
        }

        // dottedUnderLineTriples
        final Set<Triple> dottedUnderLineTriples = JsonUtils.parseJSONArrayToTetradTriples(jObj.getJSONArray("dottedUnderLineTriples"));
        for (final Triple triple : dottedUnderLineTriples) {
            graph.addDottedUnderlineTriple(triple.getX(), triple.getY(), triple.getZ());
        }

        // stuffRemovedSinceLastTripleAccess
        final boolean stuffRemovedSinceLastTripleAccess = jObj.getBoolean("stuffRemovedSinceLastTripleAccess");
        graph.setStuffRemovedSinceLastTripleAccess(stuffRemovedSinceLastTripleAccess);

        // highlightedEdges
        final Set<Edge> highlightedEdges = JsonUtils.parseJSONArrayToTetradEdges(graph, jObj.getJSONArray("highlightedEdges"));
        for (final Edge edge : highlightedEdges) {
            graph.setHighlighted(edge, true);
        }

        return graph;
    }

    public static Set<Triple> parseJSONArrayToTetradTriples(final JSONArray jArray) {
        final Set<Triple> triples = new HashSet<>();

        for (int i = 0; i < jArray.length(); i++) {
            final Triple triple = JsonUtils.parseJSONArrayToTetradTriple(jArray.getJSONObject(i));
            triples.add(triple);
        }

        return triples;
    }

    public static Triple parseJSONArrayToTetradTriple(final JSONObject jObj) {
        final Node x = JsonUtils.parseJSONObjectToTetradNode(jObj.getJSONObject("x"));
        final Node y = JsonUtils.parseJSONObjectToTetradNode(jObj.getJSONObject("y"));
        final Node z = JsonUtils.parseJSONObjectToTetradNode(jObj.getJSONObject("z"));

        return new Triple(x, y, z);
    }

    public static Set<Edge> parseJSONArrayToTetradEdges(final Graph graph, final JSONArray jArray) {
        final Set<Edge> edges = new HashSet<>();

        for (int i = 0; i < jArray.length(); i++) {
            final Edge edge = JsonUtils.parseJSONObjectToTetradEdge(graph, jArray.getJSONObject(i));
            edges.add(edge);
        }

        return edges;
    }

    public static Edge parseJSONObjectToTetradEdge(final Graph graph, final JSONObject jObj) {
        final Node node1 = graph.getNode(jObj.getJSONObject("node1").getString("name"));
        final Node node2 = graph.getNode(jObj.getJSONObject("node2").getString("name"));
        final Endpoint endpoint1 = Endpoint.TYPES[jObj.getJSONObject("endpoint1").getInt("ordinal")];
        final Endpoint endpoint2 = Endpoint.TYPES[jObj.getJSONObject("endpoint2").getInt("ordinal")];
        final Edge edge = new Edge(node1, node2, endpoint1, endpoint2);

        try {
            // properties
            final JSONArray jArray = jObj.getJSONArray("properties");
            if (jArray != null) {
                for (int i = 0; i < jArray.length(); i++) {
                    edge.addProperty(JsonUtils.parseJSONObjectToEdgeProperty(jArray.getString(i)));
                }
            }
        } catch (final JSONException e) {
            // TODO Auto-generated catch block
            //e.printStackTrace();
        }

        try {
            // properties
            final JSONArray jArray = jObj.getJSONArray("edgeTypeProbabilities");
            if (jArray != null) {
                for (int i = 0; i < jArray.length(); i++) {
                    edge.addEdgeTypeProbability(JsonUtils.parseJSONObjectToEdgeTypeProperty(jArray.getJSONObject(i)));
                }
            }
        } catch (final JSONException e) {
            // TODO Auto-generated catch block
            //e.printStackTrace();
        }

        return edge;
    }

    public static EdgeTypeProbability parseJSONObjectToEdgeTypeProperty(final JSONObject jObj) {
        final String _edgeType = jObj.getString("edgeType");
        EdgeType edgeType = EdgeType.nil;
        switch (_edgeType) {
            case "ta":
                edgeType = EdgeType.ta;
                break;
            case "at":
                edgeType = EdgeType.at;
                break;
            case "ca":
                edgeType = EdgeType.ca;
                break;
            case "ac":
                edgeType = EdgeType.ac;
                break;
            case "cc":
                edgeType = EdgeType.cc;
                break;
            case "aa":
                edgeType = EdgeType.aa;
                break;
            case "tt":
                edgeType = EdgeType.tt;
                break;
        }
        final double probability = jObj.getDouble("probability");
        final EdgeTypeProbability edgeTypeProbability = new EdgeTypeProbability(edgeType, probability);

        try {
            // properties
            final JSONArray jArray = jObj.getJSONArray("properties");
            if (jArray != null) {
                for (int i = 0; i < jArray.length(); i++) {
                    edgeTypeProbability.addProperty(JsonUtils.parseJSONObjectToEdgeProperty(jArray.getString(i)));
                }
            }
        } catch (final JSONException e) {
            // TODO Auto-generated catch block
            //e.printStackTrace();
        }
        return edgeTypeProbability;
    }

    public static Edge.Property parseJSONObjectToEdgeProperty(final String prop) {
        if (prop.equalsIgnoreCase("dd")) {
            return Edge.Property.dd;
        }
        if (prop.equalsIgnoreCase("nl")) {
            return Edge.Property.nl;
        }
        if (prop.equalsIgnoreCase("pd")) {
            return Edge.Property.pd;
        }
        if (prop.equalsIgnoreCase("pl")) {
            return Edge.Property.pl;
        }
        return null;
    }

    public static List<Node> parseJSONArrayToTetradNodes(final JSONArray jArray) {
        final List<Node> nodes = new ArrayList<>();

        for (int i = 0; i < jArray.length(); i++) {
            final Node node = JsonUtils.parseJSONObjectToTetradNode(jArray.getJSONObject(i));
            nodes.add(node);
        }

        return nodes;
    }

    public static Node parseJSONObjectToTetradNode(final JSONObject jObj) {
        final JSONObject nodeType = jObj.getJSONObject("nodeType");
        final int ordinal = nodeType.getInt("ordinal");
        final int centerX = jObj.getInt("centerX");
        final int centerY = jObj.getInt("centerY");
        final String name = jObj.getString("name");

        final GraphNode graphNode = new GraphNode(name);
        graphNode.setNodeType(NodeType.TYPES[ordinal]);
        graphNode.setCenter(centerX, centerY);

        return graphNode;
    }

}
