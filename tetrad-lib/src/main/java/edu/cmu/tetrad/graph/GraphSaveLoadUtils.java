package edu.cmu.tetrad.graph;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.SimpleDataLoader;
import edu.cmu.tetrad.util.*;
import edu.pitt.dbmi.data.reader.Data;
import edu.pitt.dbmi.data.reader.Delimiter;
import edu.pitt.dbmi.data.reader.tabular.ContinuousTabularDatasetFileReader;
import nu.xom.*;

import java.io.*;
import java.nio.file.Files;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Methods to load or save graphs.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class GraphSaveLoadUtils {

    /**
     * Private constructor to prevent instantiation.
     */
    private GraphSaveLoadUtils() {

    }

    /**
     * <p>loadGraph.</p>
     *
     * @param file a {@link java.io.File} object
     * @return a {@link edu.cmu.tetrad.graph.Graph} object
     */
    public static Graph loadGraph(File file) {

        Element root;
        Graph graph;

        try {
            root = getRootElement(file);
            graph = parseGraphXml(root, null);
        } catch (ParsingException e1) {
            throw new IllegalArgumentException("Could not parse " + file, e1);
        } catch (IOException e1) {
            throw new IllegalArgumentException("Could not read " + file, e1);
        }

        return graph;
    }

    /**
     * <p>loadGraphTxt.</p>
     *
     * @param file a {@link java.io.File} object
     * @return a {@link edu.cmu.tetrad.graph.Graph} object
     */
    public static Graph loadGraphTxt(File file) {
        try {
            Reader in1 = new FileReader(file);
            return readerToGraphTxt(in1);

        } catch (Exception e) {
            e.printStackTrace();
            throw new IllegalStateException();
        }
    }

    /**
     * <p>loadGraphRuben.</p>
     *
     * @param file a {@link java.io.File} object
     * @return a {@link edu.cmu.tetrad.graph.Graph} object
     */
    public static Graph loadGraphRuben(File file) {
        try {
            final String commentMarker = "//";
            final char quoteCharacter = '"';
            final String missingValueMarker = "*";
            final boolean hasHeader = false;

            DataSet dataSet = SimpleDataLoader.loadContinuousData(file, commentMarker, quoteCharacter, missingValueMarker,
                    hasHeader, Delimiter.COMMA, false);

            List<Node> nodes = dataSet.getVariables();
            Graph graph = new EdgeListGraph(nodes);

            for (int i = 0; i < nodes.size(); i++) {
                for (int j = i + 1; j < nodes.size(); j++) {
                    if (dataSet.getDouble(i, j) != 0D) {
                        graph.addDirectedEdge(nodes.get(i), nodes.get(j));
                    }
                }
            }

            return graph;

        } catch (Exception e) {
            e.printStackTrace();
            throw new IllegalStateException();
        }
    }

    /**
     * <p>loadGraphJson.</p>
     *
     * @param file a {@link java.io.File} object
     * @return a {@link edu.cmu.tetrad.graph.Graph} object
     */
    public static Graph loadGraphJson(File file) {
        try {
            Reader in1 = new FileReader(file);
            return readerToGraphJson(in1);
        } catch (Exception e) {
            e.printStackTrace();
        }

        throw new IllegalStateException();
    }

    private static int[][] incidenceMatrix(Graph graph) throws IllegalArgumentException {
        List<Node> nodes = graph.getNodes();
        int[][] m = new int[nodes.size()][nodes.size()];

        for (Edge edge : graph.getEdges()) {
            if (!Edges.isDirectedEdge(edge)) {
                throw new IllegalArgumentException("Not a directed graph.");
            }
        }

        for (int i = 0; i < nodes.size(); i++) {
            for (int j = 0; j < nodes.size(); j++) {
                Node x1 = nodes.get(i);
                Node x2 = nodes.get(j);
                Edge edge = graph.getEdge(x1, x2);

                if (edge == null) {
                    m[i][j] = 0;
                } else if (edge.getProximalEndpoint(x1) == Endpoint.ARROW) {
                    m[i][j] = 1;
                } else if (edge.getProximalEndpoint(x1) == Endpoint.TAIL) {
                    m[i][j] = -1;
                }
            }
        }

        return m;
    }


    // Bayes net toolbox.

    /**
     * <p>loadGraphBNTPcMatrix.</p>
     *
     * @param vars    a {@link java.util.List} object
     * @param dataSet a {@link edu.cmu.tetrad.data.DataSet} object
     * @return a {@link edu.cmu.tetrad.graph.Graph} object
     */
    public static Graph loadGraphBNTPcMatrix(List<Node> vars, DataSet dataSet) {
        Graph graph = new EdgeListGraph(vars);

        for (int i = 0; i < dataSet.getNumRows(); i++) {
            for (int j = 0; j < dataSet.getNumColumns(); j++) {
                int g = dataSet.getInt(i, j);
                int h = dataSet.getInt(j, i);

                if (g == 1 && h == 1 && !graph.isAdjacentTo(vars.get(i), vars.get(j))) {
                    graph.addUndirectedEdge(vars.get(i), vars.get(j));
                } else if (g == -1 && h == 0) {
                    graph.addDirectedEdge(vars.get(i), vars.get(j));
                }
            }
        }

        return graph;
    }

    /**
     * <p>graphRMatrixTxt.</p>
     *
     * @param graph a {@link edu.cmu.tetrad.graph.Graph} object
     * @return a {@link java.lang.String} object
     * @throws java.lang.IllegalArgumentException if any.
     */
    public static String graphRMatrixTxt(Graph graph) throws IllegalArgumentException {
        int[][] m = GraphSaveLoadUtils.incidenceMatrix(graph);

        TextTable table = new TextTable(m[0].length + 1, m.length + 1);

        for (int i = 0; i < m.length; i++) {
            for (int j = 0; j < m[0].length; j++) {
                table.setToken(i + 1, j + 1, String.valueOf(m[i][j]));
            }
        }

        for (int i = 0; i < m.length; i++) {
            table.setToken(i + 1, 0, String.valueOf(i + 1));
        }

        List<Node> nodes = graph.getNodes();

        for (int j = 0; j < m[0].length; j++) {
            table.setToken(0, j + 1, nodes.get(j).getName());
        }

        return table.toString();

    }

    /**
     * <p>loadRSpecial.</p>
     *
     * @param file a {@link java.io.File} object
     * @return a {@link edu.cmu.tetrad.graph.Graph} object
     */
    public static Graph loadRSpecial(File file) {
        DataSet eg = null;

        try {
            ContinuousTabularDatasetFileReader reader = new ContinuousTabularDatasetFileReader(file.toPath(), Delimiter.COMMA);
            reader.setHasHeader(false);
            Data data = reader.readInData();
            eg = (DataSet) DataConvertUtils.toDataModel(data);
        } catch (IOException ioException) {
            ioException.printStackTrace();
        }

        if (eg == null) throw new NullPointerException();

        List<Node> vars = eg.getVariables();

        Graph graph = new EdgeListGraph(vars);

        for (int i = 0; i < vars.size(); i++) {
            for (int j = 0; j < vars.size(); j++) {
                if (i == j) continue;
                if (eg.getDouble(i, j) == 1 && eg.getDouble(j, i) == 1) {
                    if (!graph.isAdjacentTo(vars.get(i), vars.get(j))) {
                        graph.addUndirectedEdge(vars.get(i), vars.get(j));
                    }
                } else if (eg.getDouble(i, j) == 1 && eg.getDouble(j, i) == 0) {
                    graph.addDirectedEdge(vars.get(i), vars.get(j));
                }
            }
        }

        return graph;
    }

    /**
     * <p>loadGraphPcalg.</p>
     *
     * @param file a {@link java.io.File} object
     * @return a {@link edu.cmu.tetrad.graph.Graph} object
     */
    public static Graph loadGraphPcalg(File file) {
        try {
            DataSet dataSet = SimpleDataLoader.loadContinuousData(file, "//", '\"',
                    "*", true, Delimiter.COMMA, false);

            List<Node> nodes = dataSet.getVariables();
            Graph graph = new EdgeListGraph(nodes);

            for (int i = 0; i < nodes.size(); i++) {
                for (int j = i + 1; j < nodes.size(); j++) {
                    Node n1 = nodes.get(i);
                    Node n2 = nodes.get(j);

                    int e1 = dataSet.getInt(j, i);
                    int e2 = dataSet.getInt(i, j);

                    Endpoint e1a;

                    switch (e1) {
                        case 0:
                            e1a = Endpoint.NULL;
                            break;
                        case 1:
                            e1a = Endpoint.CIRCLE;
                            break;
                        case 2:
                            e1a = Endpoint.ARROW;
                            break;
                        case 3:
                            e1a = Endpoint.TAIL;
                            break;
                        default:
                            throw new IllegalArgumentException("Unexpected endpoint type: " + e1);
                    }

                    Endpoint e2a;

                    switch (e2) {
                        case 0:
                            e2a = Endpoint.NULL;
                            break;
                        case 1:
                            e2a = Endpoint.CIRCLE;
                            break;
                        case 2:
                            e2a = Endpoint.ARROW;
                            break;
                        case 3:
                            e2a = Endpoint.TAIL;
                            break;
                        default:
                            throw new IllegalArgumentException("Unexpected endpoint type: " + e1);
                    }

                    if (e1a != Endpoint.NULL && e2a != Endpoint.NULL) {
                        Edge edge = new Edge(n1, n2, e1a, e2a);
                        graph.addEdge(edge);
                    }
                }
            }

            return graph;
        } catch (Exception e) {
            e.printStackTrace();
            throw new IllegalStateException();
        }
    }

    /**
     * <p>loadGraphRMatrix.</p>
     *
     * @param graph a {@link edu.cmu.tetrad.graph.Graph} object
     * @return a {@link java.lang.String} object
     * @throws java.lang.IllegalArgumentException if any.
     */
    public static String loadGraphRMatrix(Graph graph) throws IllegalArgumentException {
        int[][] m = GraphSaveLoadUtils.incidenceMatrix(graph);

        TextTable table = new TextTable(m[0].length + 1, m.length + 1);

        for (int i = 0; i < m.length; i++) {
            for (int j = 0; j < m[0].length; j++) {
                table.setToken(i + 1, j + 1, String.valueOf(m[i][j]));
            }
        }

        for (int i = 0; i < m.length; i++) {
            table.setToken(i + 1, 0, String.valueOf(i + 1));
        }

        List<Node> nodes = graph.getNodes();

        for (int j = 0; j < m[0].length; j++) {
            table.setToken(0, j + 1, nodes.get(j).getName());
        }

        return table.toString();
    }


    /**
     * <p>readerToGraphTxt.</p>
     *
     * @param graphString a {@link java.lang.String} object
     * @return a {@link edu.cmu.tetrad.graph.Graph} object
     * @throws java.io.IOException if any.
     */
    public static Graph readerToGraphTxt(String graphString) throws IOException {
        return readerToGraphTxt(new CharArrayReader(graphString.toCharArray()));
    }

    /**
     * <p>readerToGraphTxt.</p>
     *
     * @param reader a {@link java.io.Reader} object
     * @return a {@link edu.cmu.tetrad.graph.Graph} object
     * @throws java.io.IOException if any.
     */
    public static Graph readerToGraphTxt(Reader reader) throws IOException {
        Graph graph = new EdgeListGraph();
        try (BufferedReader in = new BufferedReader(reader)) {
            for (String line = in.readLine(); line != null; line = in.readLine()) {
                line = line.trim();
                switch (line) {
                    case "Graph Nodes:":
                        extractGraphNodes(graph, in);
                        break;
                    case "Graph Edges:":
                        extractGraphEdges(graph, in);
                        break;
                }
            }
        }

        return graph;
    }

    /**
     * <p>saveGraph.</p>
     *
     * @param graph The graph to be saved.
     * @param file  The file to save it in.
     * @param xml   True if to be saved in XML, false if in text.
     * @return I have no idea whey I'm returning this; it's already closed...
     */
    public static PrintWriter saveGraph(Graph graph, File file, boolean xml) {
        PrintWriter out;

        try {
            out = new PrintWriter(Files.newOutputStream(file.toPath()));

            if (xml) {
                out.print(graphToXml(graph));
            } else {
                out.print(graph);
            }
            out.flush();
            out.close();
        } catch (IOException e1) {
            throw new IllegalArgumentException("Output file could not " + "be opened: " + file);
        }
        return out;
    }

    /**
     * <p>readerToGraphRuben.</p>
     *
     * @param reader a {@link java.io.Reader} object
     * @return a {@link edu.cmu.tetrad.graph.Graph} object
     * @throws java.io.IOException if any.
     */
    public static Graph readerToGraphRuben(Reader reader) throws IOException {
        Graph graph = new EdgeListGraph();
        try (BufferedReader in = new BufferedReader(reader)) {
            for (String line = in.readLine(); line != null; line = in.readLine()) {
                line = line.trim();
                switch (line) {
                    case "Graph Nodes:":
                        extractGraphNodes(graph, in);
                        break;
                    case "Graph Edges:":
                        extractGraphEdges(graph, in);
                        break;
                }
            }
        }

        return graph;
    }

    private static void extractGraphEdges(Graph graph, BufferedReader in) throws IOException {
        Pattern lineNumPattern = Pattern.compile("^\\d+.\\s?");
        Pattern spacePattern = Pattern.compile("\\s+");
        Pattern semicolonPattern = Pattern.compile(";");
        Pattern colonPattern = Pattern.compile(":");
        for (String line = in.readLine(); line != null; line = in.readLine()) {
            line = line.trim();
            if (line.isEmpty()) {
                return;
            }

            line = lineNumPattern.matcher(line).replaceAll("");
            String[] fields = spacePattern.split(line, 4);
            Edge edge = getEdge(fields[0], fields[1], fields[2], graph);
            if (fields.length > 3) {
                fields = semicolonPattern.split(fields[3]);
                if (fields.length > 1) {
                    for (String prop : fields) {
                        setEdgeTypeProperties(edge, prop, spacePattern, colonPattern);
                    }
                } else {
                    getEdgeProperties(fields[0], spacePattern)
                            .forEach(edge::addProperty);
                }
            }

            graph.addEdge(edge);
        }
    }

    private static void setEdgeTypeProperties(Edge edge, String prop, Pattern spacePattern, Pattern colonPattern) {
        prop = prop.replace("[", "").replace("]", "");
        String[] fields = colonPattern.split(prop);
        if (fields.length == 2) {
            String bootstrapEdge = fields[0];
            String bootstrapEdgeTypeProb = fields[1];

            // edge type
            fields = spacePattern.split(bootstrapEdge, 4);
            if (fields.length >= 3) {
                // edge-type probability
                EdgeTypeProbability.EdgeType edgeType = getEdgeType(fields[1]);
                List<Edge.Property> properties = new LinkedList<>();
                if (fields.length > 3) {
                    // pags
                    properties.addAll(getEdgeProperties(fields[3], spacePattern));
                }

                edge.addEdgeTypeProbability(new EdgeTypeProbability(edgeType, properties, Double.parseDouble(bootstrapEdgeTypeProb)));
            } else {
                // edge probability
                if ("edge".equals(bootstrapEdge)) {
                    fields = spacePattern.split(bootstrapEdgeTypeProb, 2);
                    if (fields.length > 1) {
                        edge.setProbability(Double.parseDouble(fields[0]));
                        getEdgeProperties(fields[1], spacePattern).forEach(edge::addProperty);
                    } else {
                        edge.setProbability(Double.parseDouble(bootstrapEdgeTypeProb));
                    }
                } else if ("no edge".equals(bootstrapEdge)) {
                    edge.addEdgeTypeProbability(new EdgeTypeProbability(EdgeTypeProbability.EdgeType.nil, Double.parseDouble(bootstrapEdgeTypeProb)));
                }
            }
        }
    }

    private static EdgeTypeProbability.EdgeType getEdgeType(String edgeType) {
        Endpoint endpointFrom = getEndpoint(edgeType.charAt(0));
        Endpoint endpointTo = getEndpoint(edgeType.charAt(2));

        if (endpointFrom == Endpoint.TAIL && endpointTo == Endpoint.ARROW) {
            return EdgeTypeProbability.EdgeType.ta;
        } else if (endpointFrom == Endpoint.ARROW && endpointTo == Endpoint.TAIL) {
            return EdgeTypeProbability.EdgeType.at;
        } else if (endpointFrom == Endpoint.CIRCLE && endpointTo == Endpoint.ARROW) {
            return EdgeTypeProbability.EdgeType.ca;
        } else if (endpointFrom == Endpoint.ARROW && endpointTo == Endpoint.CIRCLE) {
            return EdgeTypeProbability.EdgeType.ac;
        } else if (endpointFrom == Endpoint.CIRCLE && endpointTo == Endpoint.CIRCLE) {
            return EdgeTypeProbability.EdgeType.cc;
        } else if (endpointFrom == Endpoint.ARROW && endpointTo == Endpoint.ARROW) {
            return EdgeTypeProbability.EdgeType.aa;
        } else if (endpointFrom == Endpoint.TAIL && endpointTo == Endpoint.TAIL) {
            return EdgeTypeProbability.EdgeType.tt;
        } else {
            return EdgeTypeProbability.EdgeType.nil;
        }
    }

    private static List<Edge.Property> getEdgeProperties(String props, Pattern spacePattern) {
        List<Edge.Property> properties = new LinkedList<>();

        for (String prop : spacePattern.split(props)) {
            if ("dd".equals(prop)) {
                properties.add(Edge.Property.dd);
            } else if ("nl".equals(prop)) {
                properties.add(Edge.Property.nl);
            } else if ("pd".equals(prop)) {
                properties.add(Edge.Property.pd);
            } else if ("pl".equals(prop)) {
                properties.add(Edge.Property.pl);
            }
        }

        return properties;
    }

    private static void extractGraphNodes(Graph graph, BufferedReader in) throws IOException {
        for (String line = in.readLine(); line != null; line = in.readLine()) {
            line = line.trim();
            if (line.isEmpty()) {
                break;
            }

            String[] tokens = line.split("[,;]");

            for (String token : tokens) {
                if (token.startsWith("(") && token.endsWith(")")) {
                    token = token.replace("(", "");
                    token = token.replace(")", "");
                    Node node = new GraphNode(token);
                    node.setNodeType(NodeType.LATENT);
                    graph.addNode(node);
                } else {
                    Node node = new GraphNode(token);
                    node.setNodeType(NodeType.MEASURED);
                    graph.addNode(node);
                }
            }

//            Arrays.stream(line.split("[,;]")).map(GraphNode::new).forEach(graph::addNode);
        }
    }

    /**
     * <p>readerToGraphJson.</p>
     *
     * @param reader a {@link java.io.Reader} object
     * @return a {@link edu.cmu.tetrad.graph.Graph} object
     * @throws java.io.IOException if any.
     */
    public static Graph readerToGraphJson(Reader reader) throws IOException {
        BufferedReader in = new BufferedReader(reader);

        StringBuilder json = new StringBuilder();
        String line;

        while ((line = in.readLine()) != null) {
            json.append(line.trim());
        }

        return JsonUtils.parseJSONObjectToTetradGraph(json.toString());
    }


    /**
     * Converts a graph to a Graphviz .dot file
     *
     * @param graph a {@link edu.cmu.tetrad.graph.Graph} object
     * @return a {@link java.lang.String} object
     */
    public static String graphToDot(Graph graph) {
        StringBuilder builder = new StringBuilder();
        builder.append("digraph g {\n");
        List<Edge> edges = new ArrayList<>(graph.getEdges());
        Collections.sort(edges);

        for (Edge edge : edges) {
            String n1 = edge.getNode1().getName();
            String n2 = edge.getNode2().getName();

            Endpoint end1 = edge.getEndpoint1();
            Endpoint end2 = edge.getEndpoint2();

            // These may be in the graph, but they represent edges not in the ensemble for which
            // bootstrap information is available.
            if (end1 == Endpoint.NULL || end2 == Endpoint.NULL) continue;

            builder.append(" \"").append(n1).append("\" -> \"").append(n2).append("\" [");

            if (end1 != Endpoint.TAIL) {
                builder.append("dir=both, ");
            }

            builder.append("arrowtail=");
            if (end1 == Endpoint.ARROW) {
                builder.append("normal");
            } else if (end1 == Endpoint.TAIL) {
                builder.append("none");
            } else if (end1 == Endpoint.CIRCLE) {
                builder.append("odot");
            } else {
                builder.append("xdot");
            }
            builder.append(", arrowhead=");
            if (end2 == Endpoint.ARROW) {
                builder.append("normal");
            } else if (end2 == Endpoint.TAIL) {
                builder.append("none");
            } else if (end2 == Endpoint.CIRCLE) {
                builder.append("odot");
            } else {
                builder.append("xdot");
            }

            // Bootstrapping
            List<EdgeTypeProbability> edgeTypeProbabilities = edge.getEdgeTypeProbabilities();
            if (edgeTypeProbabilities != null && !edgeTypeProbabilities.isEmpty()) {
                StringBuilder label = new StringBuilder(n1 + " - " + n2);
                for (EdgeTypeProbability edgeTypeProbability : edgeTypeProbabilities) {
                    EdgeTypeProbability.EdgeType edgeType = edgeTypeProbability.getEdgeType();
                    double probability = edgeTypeProbability.getProbability();
                    if (probability > 0) {
                        StringBuilder edgeTypeString = new StringBuilder();
                        switch (edgeType) {
                            case nil:
                                edgeTypeString = new StringBuilder("no edge");
                                break;
                            case ta:
                                edgeTypeString = new StringBuilder("-->");
                                break;
                            case at:
                                edgeTypeString = new StringBuilder("<--");
                                break;
                            case ca:
                                edgeTypeString = new StringBuilder("o->");
                                break;
                            case ac:
                                edgeTypeString = new StringBuilder("<-o");
                                break;
                            case cc:
                                edgeTypeString = new StringBuilder("o-o");
                                break;
                            case aa:
                                edgeTypeString = new StringBuilder("<->");
                                break;
                            case tt:
                                edgeTypeString = new StringBuilder("---");
                                break;
                        }

                        List<Edge.Property> properties = edgeTypeProbability.getProperties();
                        if (properties != null && properties.size() > 0) {
                            for (Edge.Property property : properties) {
                                edgeTypeString.append(" ").append(property.toString());
                            }
                        }

                        NumberFormat nf = new DecimalFormat("0.000");

                        label.append("\\n[").append(edgeTypeString).append("]:").append(nf.format(edgeTypeProbability.getProbability()));
                    }
                }
                builder.append(", label=\"").append(label).append("\", fontname=courier");
            }

            builder.append("]; \n");
        }
        builder.append("}");

        return builder.toString();
    }

    /**
     * <p>graphToDot.</p>
     *
     * @param graph a {@link edu.cmu.tetrad.graph.Graph} object
     * @param file  a {@link java.io.File} object
     */
    public static void graphToDot(Graph graph, File file) {
        try {
            Writer writer = new FileWriter(file);
            writer.write(graphToDot(graph));
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * <p>convertToXml.</p>
     *
     * @param graph a {@link edu.cmu.tetrad.graph.Graph} object
     * @return an XML element representing the given graph. (Well, only a basic graph for now...)
     */
    public static Element convertToXml(Graph graph) {
        Element element = new Element("graph");

        Element variables = new Element("variables");
        element.appendChild(variables);

        for (Node node : graph.getNodes()) {
            Element variable = new Element("variable");
            Text text = new Text(node.getName());
            variable.appendChild(text);
            variables.appendChild(variable);
        }

        Element edges = new Element("edges");
        element.appendChild(edges);

        for (Edge edge : graph.getEdges()) {
            Element _edge = new Element("edge");
            Text text = new Text(edge.toString());
            _edge.appendChild(text);
            edges.appendChild(_edge);
        }

        Set<Triple> ambiguousTriples = graph.getAmbiguousTriples();

        if (!ambiguousTriples.isEmpty()) {
            Element underlinings = new Element("ambiguities");
            element.appendChild(underlinings);

            for (Triple triple : ambiguousTriples) {
                Element underlining = new Element("ambiguities");
                Text text = new Text(niceTripleString(triple));
                underlining.appendChild(text);
                underlinings.appendChild(underlining);
            }
        }

        Set<Triple> underlineTriples = graph.getUnderLines();

        if (!underlineTriples.isEmpty()) {
            Element underlinings = new Element("underlines");
            element.appendChild(underlinings);

            for (Triple triple : underlineTriples) {
                Element underlining = new Element("underline");
                Text text = new Text(niceTripleString(triple));
                underlining.appendChild(text);
                underlinings.appendChild(underlining);
            }
        }

        Set<Triple> dottedTriples = graph.getDottedUnderlines();

        if (!dottedTriples.isEmpty()) {
            Element dottedUnderlinings = new Element("dottedUnderlines");
            element.appendChild(dottedUnderlinings);

            for (Triple triple : dottedTriples) {
                Element dottedUnderlining = new Element("dottedUnderline");
                Text text = new Text(niceTripleString(triple));
                dottedUnderlining.appendChild(text);
                dottedUnderlinings.appendChild(dottedUnderlining);
            }
        }

        return element;
    }

    private static String niceTripleString(Triple triple) {
        return triple.getX() + ", " + triple.getY() + ", " + triple.getZ();
    }

    /**
     * <p>graphToXml.</p>
     *
     * @param graph a {@link edu.cmu.tetrad.graph.Graph} object
     * @return a {@link java.lang.String} object
     */
    public static String graphToXml(Graph graph) {
        Document document = new Document(convertToXml(graph));
        OutputStream out = new ByteArrayOutputStream();
        Serializer serializer = new Serializer(out);
        serializer.setLineSeparator("\n");
        serializer.setIndent(2);

        try {
            serializer.write(document);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return out.toString();
    }

    /**
     * <p>graphToLavaan.</p>
     *
     * @param g a {@link edu.cmu.tetrad.graph.Graph} object
     * @return a {@link java.lang.String} object
     */
    public static String graphToLavaan(Graph g) {
        boolean includeIntercepts = true;
        boolean includeErrors = true;

        Map<Node, List<Node>> parents = new HashMap<>();
        Map<Node, List<Node>> siblings = new HashMap<>();

        StringBuilder lavaan = new StringBuilder();
        for (Node a : g.getNodes()) {
            if (includeIntercepts) lavaan.append(a.getName()).append(" ~ 1\n");
            parents.put(a, new ArrayList<>());
            siblings.put(a, new ArrayList<>());
            for (Edge e : g.getEdges(a)) {
                Node b = e.getDistalNode(a);
                if (e.getProximalEndpoint(a) != Endpoint.ARROW) continue;
                if (e.getProximalEndpoint(b) == Endpoint.TAIL) parents.get(a).add(b);
                if (siblings.containsKey(b)) continue;
                if (e.getProximalEndpoint(b) == Endpoint.ARROW) siblings.get(a).add(b);
            }
        }

        if (includeIntercepts) lavaan.append("\n");
        boolean hasDirected = false;
        for (Node a : g.getNodes()) {
            Iterator<Node> itr = parents.get(a).iterator();
            if (itr.hasNext()) {
                hasDirected = true;
                lavaan.append(a.getName()).append(" ~ ").append(itr.next().getName());
            } else continue;
            while (itr.hasNext()) lavaan.append(" + ").append(itr.next().getName());
            lavaan.append("\n");
        }

        if (hasDirected) lavaan.append("\n");
        boolean hasBidirected = false;
        for (Node a : g.getNodes()) {
            Iterator<Node> itr = siblings.get(a).iterator();
            if (itr.hasNext()) {
                hasBidirected = true;
                lavaan.append(a.getName()).append(" ~~ ").append(itr.next().getName());
            } else continue;
            while (itr.hasNext()) lavaan.append(" + ").append(itr.next().getName());
            lavaan.append("\n");
        }

        if (hasBidirected) lavaan.append("\n");
        for (Node a : g.getNodes()) {
            if (includeErrors) lavaan.append(a.getName()).append(" ~~ ").append(a.getName()).append("\n");
        }

        return lavaan.toString();
    }

    /**
     * <p>graphToPcalg.</p>
     *
     * @param g a {@link edu.cmu.tetrad.graph.Graph} object
     * @return a {@link java.lang.String} object
     */
    public static String graphToPcalg(Graph g) {
        Map<Endpoint, Integer> mark2Int = new HashMap<>();
        mark2Int.put(Endpoint.NULL, 0);
        mark2Int.put(Endpoint.CIRCLE, 1);
        mark2Int.put(Endpoint.ARROW, 2);
        mark2Int.put(Endpoint.TAIL, 3);

        int n = g.getNumNodes();
        int[][] A = new int[n][n];

        List<Node> nodes = g.getNodes();
        for (Edge edge : g.getEdges()) {
            int i = nodes.indexOf(edge.getNode1());
            int j = nodes.indexOf(edge.getNode2());
            A[j][i] = mark2Int.get(edge.getEndpoint1());
            A[i][j] = mark2Int.get(edge.getEndpoint2());
        }

        TextTable table = new TextTable(n + 1, n);
        table.setDelimiter(TextTable.Delimiter.COMMA);

        for (int j = 0; j < n; j++) {
            table.setToken(0, j, nodes.get(j).getName());
        }

        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                table.setToken(i + 1, j, String.valueOf(A[i][j]));
            }
        }

        return table.toString();
    }

    /**
     * <p>parseGraphXml.</p>
     *
     * @param graphElement a {@link nu.xom.Element} object
     * @param nodes        a {@link java.util.Map} object
     * @return a {@link edu.cmu.tetrad.graph.Graph} object
     * @throws nu.xom.ParsingException if any.
     */
    public static Graph parseGraphXml(Element graphElement, Map<String, Node> nodes) throws ParsingException {
        if (!"graph".equals(graphElement.getLocalName())) {
            throw new IllegalArgumentException("Expecting graph element: " + graphElement.getLocalName());
        }

        if (!("variables".equals(graphElement.getChildElements().get(0).getLocalName()))) {
            throw new ParsingException("Expecting variables element: " + graphElement.getChildElements().get(0).getLocalName());
        }

        Element variablesElement = graphElement.getChildElements().get(0);
        Elements variableElements = variablesElement.getChildElements();
        List<Node> variables = new ArrayList<>();

        for (int i = 0; i < variableElements.size(); i++) {
            Element variableElement = variableElements.get(i);

            if (!("variable".equals(variablesElement.getChildElements().get(i).getLocalName()))) {
                throw new ParsingException("Expecting variable element.");
            }

            String value = variableElement.getValue();

            if (nodes == null) {
                variables.add(new GraphNode(value));
            } else {
                variables.add(nodes.get(value));
            }
        }

        Graph graph = new EdgeListGraph(variables);

//        graphNotes.add(noteAttribute.getValue());
        if (!("edges".equals(graphElement.getChildElements().get(1).getLocalName()))) {
            throw new ParsingException("Expecting edges element.");
        }

        Element edgesElement = graphElement.getChildElements().get(1);
        Elements edgesElements = edgesElement.getChildElements();

        for (int i = 0; i < edgesElements.size(); i++) {
            Element edgeElement = edgesElements.get(i);

            if (!("edge".equals(edgeElement.getLocalName()))) {
                throw new ParsingException("Expecting edge element: " + edgeElement.getLocalName());
            }

            String value = edgeElement.getValue();

            final String regex = "([A-Za-z0-9_-]*:?[A-Za-z0-9_-]*) ?(.)-(.) ?([A-Za-z0-9_-]*:?[A-Za-z0-9_-]*)";
//            String regex = "([A-Za-z0-9_-]*) ?([<o])-([o>]) ?([A-Za-z0-9_-]*)";

            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(regex);
            Matcher matcher = pattern.matcher(value);

            if (!matcher.matches()) {
                throw new ParsingException("Edge doesn't match pattern.");
            }

            String var1 = matcher.group(1);
            String leftEndpoint = matcher.group(2);
            String rightEndpoint = matcher.group(3);
            String var2 = matcher.group(4);

            Node node1 = graph.getNode(var1);
            Node node2 = graph.getNode(var2);
            Endpoint endpoint1;

            switch (leftEndpoint) {
                case "<":
                    endpoint1 = Endpoint.ARROW;
                    break;
                case "o":
                    endpoint1 = Endpoint.CIRCLE;
                    break;
                case "-":
                    endpoint1 = Endpoint.TAIL;
                    break;
                default:
                    throw new IllegalStateException("Expecting an endpoint: " + leftEndpoint);
            }

            Endpoint endpoint2;

            switch (rightEndpoint) {
                case ">":
                    endpoint2 = Endpoint.ARROW;
                    break;
                case "o":
                    endpoint2 = Endpoint.CIRCLE;
                    break;
                case "-":
                    endpoint2 = Endpoint.TAIL;
                    break;
                default:
                    throw new IllegalStateException("Expecting an endpoint: " + rightEndpoint);
            }

            Edge edge = new Edge(node1, node2, endpoint1, endpoint2);
            graph.addEdge(edge);
        }

        int size = graphElement.getChildElements().size();
        if (2 >= size) {
            return graph;
        }

        int p = 2;

        if ("ambiguities".equals(graphElement.getChildElements().get(p).getLocalName())) {
            Element ambiguitiesElement = graphElement.getChildElements().get(p);
            Set<Triple> triples = parseTriples(variables, ambiguitiesElement, "ambiguity");
            graph.setAmbiguousTriples(triples);
            p++;
        }

        if (p >= size) {
            return graph;
        }

        if ("underlines".equals(graphElement.getChildElements().get(p).getLocalName())) {
            Element ambiguitiesElement = graphElement.getChildElements().get(p);
            Set<Triple> triples = parseTriples(variables, ambiguitiesElement, "underline");
            graph.setUnderLineTriples(triples);
            p++;
        }

        if (p >= size) {
            return graph;
        }

        if ("dottedunderlines".equals(graphElement.getChildElements().get(p).getLocalName())) {
            Element ambiguitiesElement = graphElement.getChildElements().get(p);
            Set<Triple> triples = parseTriples(variables, ambiguitiesElement, "dottedunderline");
            graph.setDottedUnderLineTriples(triples);
        }

        return graph;
    }

    /**
     * A triples element has a list of three (comman separated) nodes as text.
     */
    private static Set<Triple> parseTriples(List<Node> variables, Element triplesElement, String s) {
        Elements elements = triplesElement.getChildElements(s);

        Set<Triple> triples = new HashSet<>();

        for (int q = 0; q < elements.size(); q++) {
            Element tripleElement = elements.get(q);
            String value = tripleElement.getValue();

            String[] tokens = value.split(",");

            if (tokens.length != 3) {
                throw new IllegalArgumentException("Expecting a triple: " + value);
            }

            String x = tokens[0].trim();
            String y = tokens[1].trim();
            String z = tokens[2].trim();

            Node _x = getNode(variables, x);
            Node _y = getNode(variables, y);
            Node _z = getNode(variables, z);

            Triple triple = new Triple(_x, _y, _z);
            triples.add(triple);
        }
        return triples;
    }

    private static Node getNode(List<Node> variables, String x) {
        for (Node node : variables) {
            if (node.getName().equals(x)) return node;
        }

        return null;
    }

    /**
     * <p>getRootElement.</p>
     *
     * @param file a {@link java.io.File} object
     * @return a {@link nu.xom.Element} object
     * @throws nu.xom.ParsingException if any.
     * @throws java.io.IOException     if any.
     */
    public static Element getRootElement(File file) throws ParsingException, IOException {
        Builder builder = new Builder();
        Document document = builder.build(file);
        return document.getRootElement();
    }

    private static Edge getEdge(String nodeNameFrom, String edgeType, String nodeNameTo, Graph graph) {
        Node nodeFrom = getNode(nodeNameFrom, graph);
        Node nodeTo = getNode(nodeNameTo, graph);
        Endpoint endpointFrom = getEndpoint(edgeType.charAt(0));
        Endpoint endpointTo = getEndpoint(edgeType.charAt(2));

        return new Edge(nodeFrom, nodeTo, endpointFrom, endpointTo);
    }

    private static Endpoint getEndpoint(char endpoint) {
        if (endpoint == '>' || endpoint == '<') {
            return Endpoint.ARROW;
        } else if (endpoint == 'o') {
            return Endpoint.CIRCLE;
        } else if (endpoint == '-') {
            return Endpoint.TAIL;
        } else if (endpoint == '.') {
            return Endpoint.NULL;
        } else {
            throw new IllegalArgumentException(String.format("Unrecognized endpoint: %s.", endpoint));
        }
    }

    private static Node getNode(String nodeName, Graph graph) {
        Node node = graph.getNode(nodeName);
        if (node == null) {
            graph.addNode(new GraphNode(nodeName));
            node = graph.getNode(nodeName);
        }

        return node;
    }


    /**
     * <p>grabLayout.</p>
     *
     * @param nodes a {@link java.util.List} object
     * @return a {@link java.util.HashMap} object
     */
    public static HashMap<String, PointXy> grabLayout(List<Node> nodes) {
        HashMap<String, PointXy> layout = new HashMap<>();

        for (Node node : nodes) {
            layout.put(node.getName(), new PointXy(node.getCenterX(), node.getCenterY()));
        }

        return layout;
    }

    /**
     * <p>getCollidersFromGraph.</p>
     *
     * @param node  a {@link edu.cmu.tetrad.graph.Node} object
     * @param graph a {@link edu.cmu.tetrad.graph.Graph} object
     * @return A list of triples of the form X*-&gt;Y&lt;-*Z.
     */
    public static List<Triple> getCollidersFromGraph(Node node, Graph graph) {
        List<Triple> colliders = new ArrayList<>();

        List<Node> adj = new ArrayList<>(graph.getAdjacentNodes(node));
        if (adj.size() < 2) {
            return new LinkedList<>();
        }

        ChoiceGenerator gen = new ChoiceGenerator(adj.size(), 2);
        int[] choice;

        while ((choice = gen.next()) != null) {
            Node x = adj.get(choice[0]);
            Node z = adj.get(choice[1]);

            Endpoint endpt1 = graph.getEdge(x, node).getProximalEndpoint(node);
            Endpoint endpt2 = graph.getEdge(z, node).getProximalEndpoint(node);

            if (endpt1 == Endpoint.ARROW && endpt2 == Endpoint.ARROW) {
                colliders.add(new Triple(x, node, z));
            }
        }

        return colliders;
    }


}
