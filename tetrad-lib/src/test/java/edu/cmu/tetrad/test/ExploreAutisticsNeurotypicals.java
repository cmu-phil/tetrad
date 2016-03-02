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

package edu.cmu.tetrad.test;

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.Fgs;

import java.io.*;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author Joseph Ramsey
 */
public final class ExploreAutisticsNeurotypicals {

    public void printEdgeData() {

        String path = "/Users/jdramsey/Documents/LAB_NOTEBOOK.2012.04.20/data/Joe_90_Variable";
        List<List<DataSet>> allDatasets = loadData(path, "autistic", "typical");

        List<List<Graph>> allGraphs = runAlgorithm(allDatasets, 2);

        Set<Node> _nodes = getAllNodes(allGraphs);
        List<List<Graph>> graphs2 = reconcileNodes(allGraphs, new ArrayList<Node>(_nodes));
        List<Edge> _edges = getAllEdges(allGraphs);
        DataSet dataSet = createEdgeDataSet(graphs2, _edges);
        dataSet = restrictDataRange(dataSet, 0.3, 0.7);

        List<String> dictionaryNames = new ArrayList<>();

        for (Edge edge : _edges) {
            dictionaryNames.add(edge.toString());
        }

        printData(path, "edgedata", dictionaryNames, dataSet);
    }

    private List<List<Graph>> runAlgorithm(List<List<DataSet>> allDatasets) {
        double penaltyDiscount = 2;

        List<List<Graph>> allGraphs = new ArrayList<>();

        for (List<DataSet> dataSets : allDatasets) {
            List<Graph> graphs = new ArrayList<>();

            for (DataSet dataSet : dataSets) {
                Fgs search = new Fgs(dataSet);
                search.setVerbose(true);
                search.setPenaltyDiscount(penaltyDiscount);
                graphs.add(search.search());

//                Ccd search = new Ccd(new IndTestScore(new SemBicScore(new CovarianceMatrixOnTheFly(dataSet)), penaltyDiscount));
//

                graphs.add(GraphUtils.undirectedGraph(search.search()));
            }

            allGraphs.add(graphs);
        }
        return allGraphs;
    }


    public void printTrekNodeData() {
        String path = "/Users/jdramsey/Documents/LAB_NOTEBOOK.2012.04.20/data/Joe_90_Variable";
        List<List<DataSet>> allDatasets = loadData(path, "autistic", "typical");

        double penaltyDiscount = 2;

        List<List<Graph>> allGraphs = runAlgorithm(allDatasets, penaltyDiscount);

        DataSet dataSet = getTrekNodeDataSet(allGraphs);

        List<String> dictionaryNames = new ArrayList<>();

        for (Node node : dataSet.getVariables()) {
            dictionaryNames.add(node.getName());
        }

        printData(path, "trekdata", dictionaryNames, dataSet);
    }

    public void printTrekEdgeData() {
        String path = "/Users/jdramsey/Documents/LAB_NOTEBOOK.2012.04.20/data/Joe_90_Variable";
        List<List<DataSet>> allDatasets = loadData(path, "autistic", "typical");

        List<List<Graph>> allGraphs = runAlgorithm(allDatasets);

        List<Node> nodes = allGraphs.get(0).get(0).getNodes();
        allGraphs = reconcileNodes(allGraphs, nodes);
        List<Edge> allTrekEdges = getAllTrekEdges(allGraphs, 5);
        DataSet dataSet = createEdgeDataSet(allGraphs, allTrekEdges);
        nodes = dataSet.getVariables();
        dataSet = restrictDataRange(dataSet, 0.4, 0.6);

        List<String> dictionaryNames = new ArrayList<>();

        for (Edge edge : allTrekEdges) {
            dictionaryNames.add(edge.toString());
        }

        printData(path, "trekedgedata", dictionaryNames, dataSet);
    }


    private List<List<Graph>> runAlgorithm(List<List<DataSet>> allDatasets, double penaltyDiscount) {
        List<List<Graph>> allGraphs = new ArrayList<>();

        for (List<DataSet> dataSets : allDatasets) {
            List<Graph> graphs = new ArrayList<>();

            for (DataSet dataSet : dataSets) {
                Fgs search = new Fgs(dataSet);
                search.setVerbose(true);
                search.setPenaltyDiscount(penaltyDiscount);
                graphs.add(search.search());
            }

            allGraphs.add(graphs);
        }
        return allGraphs;
    }

    private List<List<DataSet>> loadData(String path, String...prefixes) {
        List<List<DataSet>> allDataSets = new ArrayList<>();

        try {
            for (int i = 0; i < prefixes.length; i++) {
                allDataSets.add(new ArrayList<DataSet>());
            }

            File dir = new File(path);
            File[] files = dir.listFiles();

            if (files == null) throw new NullPointerException("No files in " + path);

            for (File file : files) {
                boolean attested = false;

                for (int i = 0; i < prefixes.length; i++) {
                    if (file.getName().startsWith(prefixes[i])) {
                        DataReader reader = new DataReader();
                        reader.setDelimiter(DelimiterType.TAB);
                        reader.setMaxIntegralDiscrete(0);
                        allDataSets.get(i).add(reader.parseTabular(file));
                        attested = true;
                        break;
                    }
                }

                if (!attested) {
                    System.out.println("Ignoring " + file.getAbsolutePath());
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return allDataSets;
    }

    public static DataSet getTrekNodeDataSet(List<List<Graph>> graphs) {
        List<Node> nodes = new ArrayList<Node>(graphs.get(0).get(0).getNodes());
        Node fusiformLeft = graphs.get(0).get(0).getNode("Fusiform_L");
        Node fusiformRight = graphs.get(0).get(0).getNode("Fusiform_R");
        ContinuousVariable group = new ContinuousVariable("Group");
        nodes.add(group);
        List<List<Graph>> graphs2 = reconcileNodes(graphs, nodes);
        int numGraphs = getNumGraphs(graphs2);
        nodes.remove(fusiformLeft);
        nodes.remove(fusiformRight);
            DataSet dataSet = new BoxDataSet(new VerticalDoubleDataBox(numGraphs, nodes.size()), nodes);

        int numRows = dataSet.getNumRows();
        for (int i = 0; i < numRows; i++) {
            for (int j = 0; j < dataSet.getNumColumns(); j++) {
                dataSet.setDouble(i, j, 0);
            }
        }

        int row = -1;

        for (int _group = 0; _group < graphs2.size(); _group++) {
            List<Graph> __graphs = graphs2.get(_group);

            for (Graph graph : __graphs) {
                row++;
                List<List<Node>> treks = GraphUtils.treks(graph, fusiformLeft, fusiformRight, 7);

                for(List<Node> trek : treks) {
//                    Node n1 = trek.get(1);
//                    Node n2 = trek.get(trek.size() - 2);
//
//                    int col1 = nodes.indexOf(n1);
//                    int col2 = nodes.indexOf(n2);
//
//                    dataSet.setDouble(row, col1, 1);
//                    dataSet.setDouble(row, col2, 1);

//                    Node source = getTrekSource(trek, graph);
//
//                    int col = nodes.indexOf(source);
////                    dataSet.setDouble(row, col, dataSet.getInt(row, col) + 1);
//                    dataSet.setDouble(row, col, 1);


                    for (int i = 1; i < trek.size() - 1; i++) {
                        Node node = trek.get(i);
                        int col = nodes.indexOf(node);
//                        dataSet.setDouble(row, col, dataSet.getInt(row, col) + 1);
                        dataSet.setDouble(row, col, 1);
                    }
                }

                dataSet.setDouble(row, dataSet.getColumn(group), _group);
            }
        }

        return dataSet;

//        return restrictDataRange(nodes, dataSet, 0.1, 1.0);
    }

    private static Node getTrekSource(List<Node> trek, Graph graph) {
        Node n1 = trek.get(0);
        Node n2 = trek.get(trek.size() - 1);

        for (Node n : trek) {
            if (graph.isAncestorOf(n, n1) && graph.isAncestorOf(n, n2)) {
                return n;
            }
        }

        return null;
    }

    private static DataSet restrictDataRange(DataSet dataSet, double lowerRange, double upperRange) {
        int total = dataSet.getNumRows();

        List<Node> nodes = dataSet.getVariables();

        for (Node node : new ArrayList<>(nodes)) {
            int col = dataSet.getColumn(node);
            int count = 0;

            for (int i = 0; i < total; i++) {
                if (dataSet.getDouble(i, col) > 0) {
                    count++;
                }
            }

            if (count < lowerRange * total || count > upperRange * total) {
                nodes.remove(node);
            }
        }

        return dataSet.subsetColumns(nodes);
    }

    public static List<Edge> getAllTrekEdges(List<List<Graph>> graphs, int maxLength) {
        List<Node> nodes = graphs.get(0).get(0).getNodes();
        ContinuousVariable group = new ContinuousVariable("Group");
        nodes.add(group);
        List<List<Graph>> graphs2 = reconcileNodes(graphs, nodes);
        int numGraphs = getNumGraphs(graphs2);
        DataSet dataSet = new BoxDataSet(new VerticalDoubleDataBox(numGraphs, nodes.size()), nodes);

        for (int i = 0; i < dataSet.getNumRows(); i++) {
            for (int j = 0; j < dataSet.getNumColumns(); j++) {
                dataSet.setDouble(i, j, 0);
            }
        }

        Node fusiformLeft = graphs.get(0).get(0).getNode("Fusiform_L");
        Node fusiformRight = graphs.get(0).get(0).getNode("Fusiform_R");

        Set<Edge> trekEdges = new HashSet<>();

        for (int _group = 0; _group < graphs2.size(); _group++) {
            List<Graph> __graphs = graphs2.get(_group);

            for (Graph graph : __graphs) {
                List<List<Node>> treks = GraphUtils.treks(graph, fusiformLeft, fusiformRight, maxLength);

                for (List<Node> trek : treks) {
                    for (int i = 0; i < trek.size() - 2; i++) {
                        trekEdges.add(graph.getEdge(trek.get(i), trek.get(i+1)));
                    }
                }
            }
        }

        return new ArrayList<>(trekEdges);
    }


    private static List<Edge> restrictEdgesToRange(List<List<Graph>> graphs, Set<Edge> _edges,
                                                   double minFraction, double maxFraction) {
        int numGraphs = getNumGraphs(graphs);
        List<Edge> __edges = new ArrayList<>();
        System.out.println("# edges = " + _edges.size());

        for (Edge edge : _edges) {
            int count = 0;

            for (List<Graph> _graphs : graphs) {
                for (Graph graph : _graphs) {
                    if (graph.containsEdge(edge)) {
                        count++;
                    }
                }
            }

            if (count >= minFraction * numGraphs && count <= maxFraction * numGraphs) {
                __edges.add(edge);
            }
        }
        return __edges;
    }

    private static List<Edge> getAllEdges(List<List<Graph>> graphs) {
        Set<Edge> _edges = new HashSet<>();

        for (List<Graph> _graphs : graphs) {
            for (Graph graph : _graphs) {
                _edges.addAll(graph.getEdges());
            }
        }
        return new ArrayList<>(_edges);
    }

    private static List<List<Graph>> reconcileNodes(List<List<Graph>> graphs, List<Node> _nodes) {
        List<List<Graph>> graphs2 = new ArrayList<>();

        for (List<Graph> _graphs : graphs) {
            List<Graph> _graphs2 = new ArrayList<>();

            for (Graph graph : _graphs) {
                Graph graph2 = GraphUtils.replaceNodes(graph, _nodes);
                _graphs2.add(graph2);
            }

            graphs2.add(_graphs2);
        }

        return graphs2;
    }

    private static Set<Node> getAllNodes(List<List<Graph>> graphs) {
        Set<Node> _nodes = new HashSet<>();
        for (List<Graph> _graphs : graphs) {
            for (Graph graph : _graphs) {
                for (Node node : graph.getNodes()) {
                    boolean found = false;

                    for (Node _node : _nodes) {
                        if (_node.getName().equals(node.getName())) {
                            found = true;
                        }
                    }

                    if (!found) {
                        _nodes.add(node);
                    }
                }
            }
        }
        return _nodes;
    }

    private static int getNumGraphs(List<List<Graph>> graphs) {
        int numGraphs = 0;

        for (List<Graph> _graphs : graphs) {
            for (Graph graph : _graphs) {
                numGraphs++;
            }
        }
        return numGraphs;
    }

    private static DataSet createEdgeDataSet(List<List<Graph>> graphs2, List<Edge> edges) {
        System.out.println("# edges = " + edges.size());

        List<Node> edgeVars = new ArrayList<>();

        for (int i = 0; i < edges.size(); i++) {
            Node node = new ContinuousVariable("X" + (i + 1));
            edgeVars.add(node);
        }

        edgeVars.add(new ContinuousVariable("Group"));

        DataSet dataSet = new BoxDataSet(new VerticalDoubleDataBox(20, edges.size()), edgeVars);

        int row = -1;

        for (int i = 0; i < graphs2.size(); i++) {
            List<Graph> graphs3 = graphs2.get(i);

            for (int k = 0; k < graphs3.size(); k++) {
                row++;

                for (int j = 0; j < edges.size(); j++) {
                    dataSet.setDouble(row, j, graphs3.get(k).containsEdge(edges.get(j)) ? 1 : 0);
                }

                dataSet.setDouble(row, edges.size(), i);
            }
        }

        dataSet.setNumberFormat(new DecimalFormat("0"));
        return dataSet;
    }

    private static void printData(String path, String prefix, List<String> dictionaryNames, DataSet dataSet) {
        File file1 = new File(path, prefix + ".data.txt");
        File file2 = new File(path, prefix + ".dict.txt");

        PrintStream out1 = null;
        PrintStream out2 = null;
        try {
            out1 = new PrintStream(new FileOutputStream(file1));
            out2 = new PrintStream(new FileOutputStream(file2));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            throw new RuntimeException();
        }

        out1.println(dataSet);
        out1.close();

        for (int i = 0; i < dictionaryNames.size(); i++) {
            out2.println("X" + (i + 1) + "\t" + dictionaryNames.get(i));
        }

        out2.println("Group");

        out2.close();
    }

    public static void main(String...args) {
        new ExploreAutisticsNeurotypicals().printEdgeData();
    }
}





