///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015, 2022 by Peter Spirtes, Richard        //
// Scheines, Joseph Ramsey, and Clark Glymour.                               //
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
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.Fges;
import edu.cmu.tetrad.search.score.SemBicScore;
import edu.cmu.tetrad.util.Matrix;
import edu.cmu.tetrad.util.RandomUtil;
import edu.pitt.dbmi.data.reader.Delimiter;

import java.io.*;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author josephramsey
 */
public final class ExploreAutisticsNeurotypicals {

    public static DataSet getTrekNodeDataSet(List<List<Graph>> graphs) {
        List<Node> graphNodes = new ArrayList<>(graphs.get(0).get(0).getNodes());

        List<Node> nodes = new ArrayList<>();

        for (Node node : graphNodes) {
            nodes.add(new ContinuousVariable(node.getName()));
        }

        graphs = ExploreAutisticsNeurotypicals.reconcileNodes(graphs);

        Node fusiformLeft = graphs.get(0).get(0).getNode("Fusiform_L");
        Node fusiformRight = graphs.get(0).get(0).getNode("Fusiform_R");
        ContinuousVariable group = new ContinuousVariable("Group");
        nodes.add(group);
        int numGraphs = ExploreAutisticsNeurotypicals.getNumGraphs(graphs);
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

        for (int _group = 0; _group < graphs.size(); _group++) {
            List<Graph> __graphs = graphs.get(_group);

            for (Graph graph : __graphs) {
                row++;
                List<List<Node>> treks = graph.paths().treks(fusiformLeft, fusiformRight, 7);

                for (List<Node> trek : treks) {


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
            if (graph.paths().isAncestorOf(n, n1) && graph.paths().isAncestorOf(n, n2)) {
                return n;
            }
        }

        return null;
    }

    private static DataSet restrictDataRange(DataSet dataSet) {
        int total = dataSet.getNumRows();

        List<Node> nodes = dataSet.getVariables();

        for (Node node : new ArrayList<>(nodes)) {
            if ("Group".equals(node.getName())) {
                continue;
            }
            int col = dataSet.getColumn(node);
            int count = 0;

            for (int i = 0; i < total; i++) {
                if (dataSet.getDouble(i, col) > 0) {
                    count++;
                }
            }

            if (count < 0.3 * total || count > 0.7 * total) {
                nodes.remove(node);
            }
        }

        return dataSet.subsetColumns(nodes);
    }

    public static List<Edge> getAllTrekEdges(List<List<Graph>> graphs, int maxLength) {
        List<Node> nodes = graphs.get(0).get(0).getNodes();
        ContinuousVariable group = new ContinuousVariable("Group");
        nodes.add(group);
        graphs = ExploreAutisticsNeurotypicals.reconcileNodes(graphs);
        int numGraphs = ExploreAutisticsNeurotypicals.getNumGraphs(graphs);
        DataSet dataSet = new BoxDataSet(new VerticalDoubleDataBox(numGraphs, nodes.size()), nodes);

        for (int i = 0; i < dataSet.getNumRows(); i++) {
            for (int j = 0; j < dataSet.getNumColumns(); j++) {
                dataSet.setDouble(i, j, 0);
            }
        }

        Node fusiformLeft = graphs.get(0).get(0).getNode("Fusiform_L");
        Node fusiformRight = graphs.get(0).get(0).getNode("Fusiform_R");

        Set<Edge> trekEdges = new HashSet<>();

        for (List<Graph> __graphs : graphs) {
            for (Graph graph : __graphs) {
                List<List<Node>> treks = graph.paths().treks(fusiformLeft, fusiformRight, maxLength);

                for (List<Node> trek : treks) {
                    for (int i = 0; i < trek.size() - 2; i++) {
                        trekEdges.add(graph.getEdge(trek.get(i), trek.get(i + 1)));
                    }
                }
            }
        }

        return new ArrayList<>(trekEdges);
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

    private static List<List<Graph>> reconcileNodes(List<List<Graph>> graphs) {
        List<Node> _nodes = new ArrayList<>(ExploreAutisticsNeurotypicals.getAllNodes(graphs));

        int count = 0;

        List<List<Graph>> graphs2 = new ArrayList<>();

        for (List<Graph> _graphs : graphs) {
            List<Graph> _graphs2 = new ArrayList<>();

            for (Graph graph : _graphs) {
                Graph graph2 = GraphUtils.replaceNodes(graph, _nodes);
                _graphs2.add(graph2);
                count++;
            }

            graphs2.add(_graphs2);
        }

        System.out.println("# graphs reconciled = " + count);

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
            numGraphs += _graphs.size();
        }

        return numGraphs;
    }

    private static DataSet createEdgeDataSet(List<List<Graph>> graphs2, List<Edge> edges) {
        System.out.println("# edges = " + edges.size());

        int numGraphs = ExploreAutisticsNeurotypicals.getNumGraphs(graphs2);

        List<Node> edgeVars = new ArrayList<>();

        for (Edge edge : edges) {
            edgeVars.add(new ContinuousVariable(edge.toString()));
        }

        edgeVars.add(new ContinuousVariable("Group"));

        DataSet dataSet = new BoxDataSet(new VerticalDoubleDataBox(numGraphs, edges.size()), edgeVars);

        int row = -1;

        for (int i = 0; i < graphs2.size(); i++) {
            List<Graph> graphs3 = graphs2.get(i);

            for (Graph graph : graphs3) {
                row++;

                for (int j = 0; j < edges.size(); j++) {
                    dataSet.setDouble(row, j, graph.containsEdge(edges.get(j)) ? 1 : 0);
                }

                dataSet.setDouble(row, edges.size(), i);
            }
        }

        return dataSet;
    }

    private static void printData(String path, String prefix, DataSet dataSet) {
        List<Node> nodes = dataSet.getVariables();
        Node group = dataSet.getVariable("Group");

        List<Node> _nodes = new ArrayList<>();

        for (int i = 0; i < nodes.size(); i++) {
            if (nodes.get(i) == group) {
                _nodes.add(group);
            } else {
                _nodes.add(new ContinuousVariable("X" + (i + 1)));
            }
        }

        dataSet = new BoxDataSet(new DoubleDataBox(dataSet.getDoubleData().toArray()), _nodes);
        dataSet.setNumberFormat(new DecimalFormat("0"));

        File file1 = new File(path, prefix + ".data.txt");
        File file2 = new File(path, prefix + ".dict.txt");

        try {
            PrintStream out1 = new PrintStream(new FileOutputStream(file1));
            PrintStream out2 = new PrintStream(new FileOutputStream(file2));

            out1.println(dataSet);
            out1.close();

            for (int i = 0; i < nodes.size(); i++) {
                out2.println(_nodes.get(i) + "\t" + nodes.get(i));
            }

            out2.println("Group");

            out2.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            throw new RuntimeException();
        }
    }

    private static void printDataTranspose(String path, String prefix, DataSet dataSet) {
        List<Node> nodes = dataSet.getVariables();
        Node group = dataSet.getVariable("Group");

        List<Node> _nodes = new ArrayList<>();

        for (int i = 0; i < nodes.size(); i++) {
            if (nodes.get(i) == group) {
                _nodes.add(group);
            } else {
                _nodes.add(new ContinuousVariable("X" + (i + 1)));
            }
        }

        Matrix m = dataSet.getDoubleData();

        Matrix mt = m.transpose();

        List<Node> tvars = new ArrayList<>();

        for (int i = 0; i < mt.getNumColumns(); i++) tvars.add(new ContinuousVariable("S" + (i + 1)));

        dataSet = new BoxDataSet(new DoubleDataBox(mt.toArray()), tvars);

//        dataSet = new BoxDataSet(new DoubleDataBox(dataSet.getDoubleData().toArray()), _nodes);
        dataSet.setNumberFormat(new DecimalFormat("0"));

        File file1 = new File(path, prefix + ".data.txt");
        File file2 = new File(path, prefix + ".dict.txt");

        try {
            PrintStream out1 = new PrintStream(new FileOutputStream(file1));
            PrintStream out2 = new PrintStream(new FileOutputStream(file2));

            out1.println(dataSet);
            out1.close();

            for (int i = 0; i < nodes.size(); i++) {
                out2.println(_nodes.get(i) + "\t" + nodes.get(i));
            }

            out2.println("Group");

            out2.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            throw new RuntimeException();
        }
    }

    public static void main(String... args) {
        new ExploreAutisticsNeurotypicals().printDegreeData();
    }

    public void printEdgeData() {
        final String path = "/Users/jdramsey/Documents/LAB_NOTEBOOK.2012.04.20/data/Joe_108_Variable";
//        String path = "/Users/jdramsey/Documents/LAB_NOTEBOOee.2012.04.20/data/USM_Datasets";
        List<List<DataSet>> allDatasets = loadData(path, "autistic", "typical");
        List<List<Graph>> allGraphs = runAlgorithm(path, allDatasets);
        List<List<Graph>> graphs = ExploreAutisticsNeurotypicals.reconcileNodes(allGraphs);
        List<Edge> _edges = ExploreAutisticsNeurotypicals.getAllEdges(allGraphs);
        DataSet dataSet = ExploreAutisticsNeurotypicals.createEdgeDataSet(graphs, _edges);
//        dataSet = restrictDataRange(dataSet, .1, .9);
        ExploreAutisticsNeurotypicals.printData(path, "edgedata", dataSet);
    }

    public void printTrekNodeData() {
        final String path = "/Users/jdramsey/Documents/LAB_NOTEBOOK.2012.04.20/data/Joe_90_Variable";
        List<List<DataSet>> allDatasets = loadData(path, "autistic", "typical");
        List<List<Graph>> allGraphs = runAlgorithm(path, allDatasets);

        DataSet dataSet = ExploreAutisticsNeurotypicals.getTrekNodeDataSet(allGraphs);

        ExploreAutisticsNeurotypicals.printData(path, "treknodedata", dataSet);
    }

    public void printTrekEdgeData() {
        final String path = "/Users/jdramsey/Documents/LAB_NOTEBOOK.2012.04.20/data/Joe_90_Variable";
        List<List<DataSet>> allDatasets = loadData(path, "autistic", "typical");

        List<List<Graph>> allGraphs = runAlgorithm(path, allDatasets);

        List<Node> nodes = allGraphs.get(0).get(0).getNodes();
        allGraphs = ExploreAutisticsNeurotypicals.reconcileNodes(allGraphs);
        List<Edge> allTrekEdges = ExploreAutisticsNeurotypicals.getAllTrekEdges(allGraphs, 5);
        DataSet dataSet = ExploreAutisticsNeurotypicals.createEdgeDataSet(allGraphs, allTrekEdges);
        dataSet = ExploreAutisticsNeurotypicals.restrictDataRange(dataSet);
        ExploreAutisticsNeurotypicals.printData(path, "trekedgedata", dataSet);
    }

    private List<List<Graph>> runAlgorithm(String path, List<List<DataSet>> allDatasets) {
        List<List<Graph>> allGraphs = new ArrayList<>();

        for (List<DataSet> dataSets : allDatasets) {
            List<Graph> graphs = new ArrayList<>();

            for (DataSet dataSet : dataSets) {
                String name = dataSet.getName() + "." + (double) 10 + ".graph.txt";
                File file = new File(path, name);

                SemBicScore score = new SemBicScore(new CovarianceMatrix(dataSet));
                score.setPenaltyDiscount(10);
                Fges search = new Fges(score);
                search.setVerbose(false);
                Graph graph = search.search();
                GraphSaveLoadUtils.saveGraph(graph, file, false);
                graphs.add(GraphUtils.undirectedGraph(GraphSaveLoadUtils.loadGraphTxt(file)));
            }

            allGraphs.add(graphs);
        }

        return allGraphs;
    }

    private List<List<DataSet>> loadData(String path, String... prefixes) {
        List<List<DataSet>> allDataSets = new ArrayList<>();

        int numDataSets = 0;

        try {
            for (int i = 0; i < prefixes.length; i++) {
                allDataSets.add(new ArrayList<>());
            }

            File dir = new File(path);
            File[] files = dir.listFiles();

            if (files == null) throw new NullPointerException("No files in " + path);

            for (File file : files) {
                boolean attested = false;

                for (int i = 0; i < prefixes.length; i++) {
                    if (file.getName().startsWith(prefixes[i]) && !file.getName().endsWith(".graph.txt")
                        && !file.getName().contains("tet")) {
                        DataSet data = SimpleDataLoader.loadContinuousData(file, "//", '\"',
                                "*", true, Delimiter.TAB, false);

                        allDataSets.get(i).add(data);
                        attested = true;
                        numDataSets++;
                        break;
                    }
                }

                if (!attested) {
                    System.out.println("Ignoring " + file.getAbsolutePath());
                }
            }

            System.out.println("# data sets = " + numDataSets);
        } catch (IOException e) {
            e.printStackTrace();
        }

        return allDataSets;
    }

    public void printDegreeData() {
//        String path = "/Users/jdramsey/Documents/LAB_NOTEBOOK.2012.04.20/data/Joe_108_Variable";
        final String path = "/Users/jdramsey/Documents/LAB_NOTEBOOK.2012.04.20/data/USM_Datasets/all";
        List<List<DataSet>> allDatasets = loadData(path, "ROI_data_autistic", "ROI_data_typical");
        List<List<Graph>> allGraphs = runAlgorithm(path, allDatasets);
        List<List<Graph>> graphs = ExploreAutisticsNeurotypicals.reconcileNodes(allGraphs);
        List<Node> nodes = graphs.get(0).get(0).getNodes();

        System.out.print("Group\t");

        for (Node node : nodes) {
            System.out.print(node.getName() + "\t");
        }

        System.out.println();

        for (int i = 0; i < graphs.size(); i++) {
            List<Graph> _graphs = graphs.get(i);

            for (Graph _graph : _graphs) {
                System.out.print(i + "\t");

                for (Node node : nodes) {
                    System.out.print(_graph.getAdjacentNodes(node).size() + "\t");
                }

                System.out.println();
            }
        }
    }

    public void makeDataSpecial() {
        try {
            final String path = "/Users/jdramsey/Documents/LAB_NOTEBOOK.2012.04.20/data/USM_Datasets";
            File file = new File(path, "concat_usm_dataset_madelyn.txt");
            DataSet data = SimpleDataLoader.loadContinuousData(file, "//", '\"',
                    "*", true, Delimiter.TAB, false);

            ContinuousVariable avg = new ContinuousVariable("Avg");
            data.addVariable(avg);

            for (int i = 0; i < data.getNumRows(); i++) {
                double sum = 0.0;

                for (int j = 0; j < data.getNumColumns() - 2; j++) {
                    sum += data.getDouble(i, j);
                }

                sum = data.getDouble(i, data.getNumColumns() - 2) == 1 ? 10 + RandomUtil.getInstance().nextUniform(-1, 1)
                        : -10 + RandomUtil.getInstance().nextUniform(-1, 1);

                double _avg = sum / data.getNumColumns() - 2;

                data.setDouble(i, data.getNumColumns() - 1, _avg);
            }

            File file2 = new File(path, "concat_usm_dataset_madelynB.txt");

            PrintStream out = new PrintStream(new FileOutputStream(file2));
            out.println(data);
            out.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}





