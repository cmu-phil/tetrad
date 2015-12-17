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
import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Endpoint;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Scanner;
import java.util.regex.Pattern;

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

    public static Graph loadGraph(Path graphFile) {
        return GraphUtils.loadGraphTxt(graphFile.toFile());
    }

    public static Graph loadGraphAsContinuousVariables(Path graphFile) {
        List<Node> nodes = getGraphContinuousNodes(graphFile);
        Graph graph = new EdgeListGraph(nodes);

        Pattern spacePattern = Pattern.compile("\\s");
        try (BufferedReader reader = Files.newBufferedReader(graphFile, StandardCharsets.UTF_8)) {
            String delimiter = "Graph Edges:";
            boolean edges = false;
            for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                line = line.trim();
                if (edges) {
                    if (line.isEmpty()) {
                        break;
                    }

                    String[] fields = spacePattern.split(line);
                    String from = fields[1];
                    String edge = fields[2];
                    String to = fields[3];

                    Node nodeFrom = graph.getNode(from);
                    Node nodeTo = graph.getNode(to);

                    char end1 = edge.charAt(0);
                    char end2 = edge.charAt(2);

                    Endpoint endPoint1, endPoint2;

                    if (end1 == '<') {
                        endPoint1 = Endpoint.ARROW;
                    } else if (end1 == 'o') {
                        endPoint1 = Endpoint.CIRCLE;
                    } else if (end1 == '-') {
                        endPoint1 = Endpoint.TAIL;
                    } else {
                        throw new IllegalArgumentException();
                    }

                    if (end2 == '>') {
                        endPoint2 = Endpoint.ARROW;
                    } else if (end2 == 'o') {
                        endPoint2 = Endpoint.CIRCLE;
                    } else if (end2 == '-') {
                        endPoint2 = Endpoint.TAIL;
                    } else {
                        throw new IllegalArgumentException();
                    }

                    graph.addEdge(new Edge(nodeFrom, nodeTo, endPoint1, endPoint2));
                } else {
                    edges = delimiter.equals(line);
                }
            }
        } catch (IOException exception) {
            exception.printStackTrace(System.err);
        }

        return graph;
    }

    public static List<Node> getGraphContinuousNodes(Path graphFile) {
        List<Node> nodes = new LinkedList<>();

        String delimiter = "Graph Nodes:";
        try (BufferedReader reader = Files.newBufferedReader(graphFile, StandardCharsets.UTF_8)) {
            for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                line = line.trim();
                if (delimiter.equals(line)) {
                    line = reader.readLine();
                    if (line != null) {
                        Scanner scanner = new Scanner(line.trim());
                        while (scanner.hasNext()) {
                            nodes.add(new ContinuousVariable(scanner.next()));
                        }
                        break;
                    }
                }
            }
        } catch (IOException exception) {
            exception.printStackTrace(System.err);
        }

        return nodes;
    }

}
