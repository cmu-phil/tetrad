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
import edu.cmu.tetrad.search.Fges;
import edu.cmu.tetrad.search.SemBicScore;
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
 * @author Joseph Ramsey
 */
public final class ExploreAutisticsNeurotypicals {

    public void printEdgeData() {
        final String path = "/Users/jdramsey/Documents/LAB_NOTEBOOK.2012.04.20/data/Joe_108_Variable";
//        String path = "/Users/jdramsey/Documents/LAB_NOTEBOOee.2012.04.20/data/USM_Datasets";
        final List<List<DataSet>> allDatasets = loadData(path, "autistic", "typical");
        final List<List<Graph>> allGraphs = runAlgorithm(path, allDatasets, 10);
        final List<List<Graph>> graphs = reconcileNodes(allGraphs);
        final List<Edge> _edges = getAllEdges(allGraphs);
        final DataSet dataSet = createEdgeDataSet(graphs, _edges);
//        dataSet = restrictDataRange(dataSet, .1, .9);
        printData(path, "edgedata", dataSet);
    }

    public void printTrekNodeData() {
        final String path = "/Users/jdramsey/Documents/LAB_NOTEBOOK.2012.04.20/data/Joe_90_Variable";
        final List<List<DataSet>> allDatasets = loadData(path, "autistic", "typical");
        final List<List<Graph>> allGraphs = runAlgorithm(path, allDatasets, 10);

        final DataSet dataSet = getTrekNodeDataSet(allGraphs);

        printData(path, "treknodedata", dataSet);
    }

    public void printTrekEdgeData() {
        final String path = "/Users/jdramsey/Documents/LAB_NOTEBOOK.2012.04.20/data/Joe_90_Variable";
        final List<List<DataSet>> allDatasets = loadData(path, "autistic", "typical");

        List<List<Graph>> allGraphs = runAlgorithm(path, allDatasets, 10);

        final List<Node> nodes = allGraphs.get(0).get(0).getNodes();
        allGraphs = reconcileNodes(allGraphs);
        final List<Edge> allTrekEdges = getAllTrekEdges(allGraphs, 5);
        DataSet dataSet = createEdgeDataSet(allGraphs, allTrekEdges);
        dataSet = restrictDataRange(dataSet, .3, .7);
        printData(path, "trekedgedata", dataSet);
    }

    private List<List<Graph>> runAlgorithm(final String path, final List<List<DataSet>> allDatasets, final double penaltyDiscount) {
        final List<List<Graph>> allGraphs = new ArrayList<>();

        for (final List<DataSet> dataSets : allDatasets) {
            final List<Graph> graphs = new ArrayList<>();

            for (final DataSet dataSet : dataSets) {
                final String name = dataSet.getName() + "." + penaltyDiscount + ".graph.txt";
                final File file = new File(path, name);

                final SemBicScore score = new SemBicScore(new CovarianceMatrix(dataSet));
                score.setPenaltyDiscount(penaltyDiscount);
                final Fges search = new Fges(score);
                search.setVerbose(false);
                final Graph graph = search.search();
                GraphUtils.saveGraph(graph, file, false);
                graphs.add(GraphUtils.undirectedGraph(GraphUtils.loadGraphTxt(file)));
            }

            allGraphs.add(graphs);
        }

        return allGraphs;
    }

    private List<List<DataSet>> loadData(final String path, final String... prefixes) {
        final List<List<DataSet>> allDataSets = new ArrayList<>();

        int numDataSets = 0;

        try {
            for (int i = 0; i < prefixes.length; i++) {
                allDataSets.add(new ArrayList<DataSet>());
            }

            final File dir = new File(path);
            final File[] files = dir.listFiles();

            if (files == null) throw new NullPointerException("No files in " + path);

            for (final File file : files) {
                boolean attested = false;

                for (int i = 0; i < prefixes.length; i++) {
                    if (file.getName().startsWith(prefixes[i]) && !file.getName().endsWith(".graph.txt")
                            && !file.getName().contains("tet")) {
                        final DataSet data = DataUtils.loadContinuousData(file, "//", '\"' ,
                                "*", true, Delimiter.TAB);

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
        } catch (final IOException e) {
            e.printStackTrace();
        }

        return allDataSets;
    }


    public void printDegreeData() {
//        String path = "/Users/jdramsey/Documents/LAB_NOTEBOOK.2012.04.20/data/Joe_108_Variable";
        final String path = "/Users/jdramsey/Documents/LAB_NOTEBOOK.2012.04.20/data/USM_Datasets/all";
        final List<List<DataSet>> allDatasets = loadData(path, "ROI_data_autistic", "ROI_data_typical");
        final List<List<Graph>> allGraphs = runAlgorithm(path, allDatasets, 10);
        final List<List<Graph>> graphs = reconcileNodes(allGraphs);
        final List<Node> nodes = graphs.get(0).get(0).getNodes();

        System.out.print("Group\t");

        for (final Node node : nodes) {
            System.out.print(node.getName() + "\t");
        }

        System.out.println();

        for (int i = 0; i < graphs.size(); i++) {
            final List<Graph> _graphs = graphs.get(i);

            for (final Graph _graph : _graphs) {
                System.out.print(i + "\t");

                for (final Node node : nodes) {
                    System.out.print(_graph.getAdjacentNodes(node).size() + "\t");
                }

                System.out.println();
            }
        }
    }


    public static DataSet getTrekNodeDataSet(List<List<Graph>> graphs) {
        final List<Node> graphNodes = new ArrayList<>(graphs.get(0).get(0).getNodes());

        final List<Node> nodes = new ArrayList<>();

        for (final Node node : graphNodes) {
            nodes.add(new ContinuousVariable(node.getName()));
        }

        graphs = reconcileNodes(graphs);

        final Node fusiformLeft = graphs.get(0).get(0).getNode("Fusiform_L");
        final Node fusiformRight = graphs.get(0).get(0).getNode("Fusiform_R");
        final ContinuousVariable group = new ContinuousVariable("Group");
        nodes.add(group);
        final int numGraphs = getNumGraphs(graphs);
        nodes.remove(fusiformLeft);
        nodes.remove(fusiformRight);
        final DataSet dataSet = new BoxDataSet(new VerticalDoubleDataBox(numGraphs, nodes.size()), nodes);

        final int numRows = dataSet.getNumRows();
        for (int i = 0; i < numRows; i++) {
            for (int j = 0; j < dataSet.getNumColumns(); j++) {
                dataSet.setDouble(i, j, 0);
            }
        }

        int row = -1;

        for (int _group = 0; _group < graphs.size(); _group++) {
            final List<Graph> __graphs = graphs.get(_group);

            for (final Graph graph : __graphs) {
                row++;
                final List<List<Node>> treks = GraphUtils.treks(graph, fusiformLeft, fusiformRight, 7);

                for (final List<Node> trek : treks) {
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
                        final Node node = trek.get(i);
                        final int col = nodes.indexOf(node);
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

    private static Node getTrekSource(final List<Node> trek, final Graph graph) {
        final Node n1 = trek.get(0);
        final Node n2 = trek.get(trek.size() - 1);

        for (final Node n : trek) {
            if (graph.isAncestorOf(n, n1) && graph.isAncestorOf(n, n2)) {
                return n;
            }
        }

        return null;
    }

    private static DataSet restrictDataRange(final DataSet dataSet, final double lowerRange, final double upperRange) {
        final int total = dataSet.getNumRows();

        final List<Node> nodes = dataSet.getVariables();

        for (final Node node : new ArrayList<>(nodes)) {
            if ("Group".equals(node.getName())) {
                continue;
            }
            final int col = dataSet.getColumn(node);
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

    public static List<Edge> getAllTrekEdges(List<List<Graph>> graphs, final int maxLength) {
        final List<Node> nodes = graphs.get(0).get(0).getNodes();
        final ContinuousVariable group = new ContinuousVariable("Group");
        nodes.add(group);
        graphs = reconcileNodes(graphs);
        final int numGraphs = getNumGraphs(graphs);
        final DataSet dataSet = new BoxDataSet(new VerticalDoubleDataBox(numGraphs, nodes.size()), nodes);

        for (int i = 0; i < dataSet.getNumRows(); i++) {
            for (int j = 0; j < dataSet.getNumColumns(); j++) {
                dataSet.setDouble(i, j, 0);
            }
        }

        final Node fusiformLeft = graphs.get(0).get(0).getNode("Fusiform_L");
        final Node fusiformRight = graphs.get(0).get(0).getNode("Fusiform_R");

        final Set<Edge> trekEdges = new HashSet<>();

        for (int _group = 0; _group < graphs.size(); _group++) {
            final List<Graph> __graphs = graphs.get(_group);

            for (final Graph graph : __graphs) {
                final List<List<Node>> treks = GraphUtils.treks(graph, fusiformLeft, fusiformRight, maxLength);

                for (final List<Node> trek : treks) {
                    for (int i = 0; i < trek.size() - 2; i++) {
                        trekEdges.add(graph.getEdge(trek.get(i), trek.get(i + 1)));
                    }
                }
            }
        }

        return new ArrayList<>(trekEdges);
    }

    private static List<Edge> getAllEdges(final List<List<Graph>> graphs) {
        final Set<Edge> _edges = new HashSet<>();

        for (final List<Graph> _graphs : graphs) {
            for (final Graph graph : _graphs) {
                _edges.addAll(graph.getEdges());
            }
        }
        return new ArrayList<>(_edges);
    }

    private static List<List<Graph>> reconcileNodes(final List<List<Graph>> graphs) {
        final List<Node> _nodes = new ArrayList<>(getAllNodes(graphs));

        int count = 0;

        final List<List<Graph>> graphs2 = new ArrayList<>();

        for (final List<Graph> _graphs : graphs) {
            final List<Graph> _graphs2 = new ArrayList<>();

            for (final Graph graph : _graphs) {
                final Graph graph2 = GraphUtils.replaceNodes(graph, _nodes);
                _graphs2.add(graph2);
                count++;
            }

            graphs2.add(_graphs2);
        }

        System.out.println("# graphs reconciled = " + count);

        return graphs2;
    }

    private static Set<Node> getAllNodes(final List<List<Graph>> graphs) {
        final Set<Node> _nodes = new HashSet<>();
        for (final List<Graph> _graphs : graphs) {
            for (final Graph graph : _graphs) {
                for (final Node node : graph.getNodes()) {
                    boolean found = false;

                    for (final Node _node : _nodes) {
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

    private static int getNumGraphs(final List<List<Graph>> graphs) {
        int numGraphs = 0;

        for (final List<Graph> _graphs : graphs) {
            numGraphs += _graphs.size();
        }

        return numGraphs;
    }

    private static DataSet createEdgeDataSet(final List<List<Graph>> graphs2, final List<Edge> edges) {
        System.out.println("# edges = " + edges.size());

        final int numGraphs = getNumGraphs(graphs2);

        final List<Node> edgeVars = new ArrayList<>();

        for (int i = 0; i < edges.size(); i++) {
            edgeVars.add(new ContinuousVariable(edges.get(i).toString()));
        }

        edgeVars.add(new ContinuousVariable("Group"));

        final DataSet dataSet = new BoxDataSet(new VerticalDoubleDataBox(numGraphs, edges.size()), edgeVars);

        int row = -1;

        for (int i = 0; i < graphs2.size(); i++) {
            final List<Graph> graphs3 = graphs2.get(i);

            for (int k = 0; k < graphs3.size(); k++) {
                row++;

                for (int j = 0; j < edges.size(); j++) {
                    dataSet.setDouble(row, j, graphs3.get(k).containsEdge(edges.get(j)) ? 1 : 0);
                }

                dataSet.setDouble(row, edges.size(), i);
            }
        }

        return dataSet;
    }

    private static void printData(final String path, final String prefix, DataSet dataSet) {
        final List<Node> nodes = dataSet.getVariables();
        final Node group = dataSet.getVariable("Group");

        final List<Node> _nodes = new ArrayList<>();

        for (int i = 0; i < nodes.size(); i++) {
            if (nodes.get(i) == group) {
                _nodes.add(group);
            } else {
                _nodes.add(new ContinuousVariable("X" + (i + 1)));
            }
        }

        dataSet = new BoxDataSet(new DoubleDataBox(dataSet.getDoubleData().toArray()), _nodes);
        dataSet.setNumberFormat(new DecimalFormat("0"));

        final File file1 = new File(path, prefix + ".data.txt");
        final File file2 = new File(path, prefix + ".dict.txt");

        try {
            final PrintStream out1 = new PrintStream(new FileOutputStream(file1));
            final PrintStream out2 = new PrintStream(new FileOutputStream(file2));

            out1.println(dataSet);
            out1.close();

            for (int i = 0; i < nodes.size(); i++) {
                out2.println(_nodes.get(i) + "\t" + nodes.get(i));
            }

            out2.println("Group");

            out2.close();
        } catch (final FileNotFoundException e) {
            e.printStackTrace();
            throw new RuntimeException();
        }
    }

    private static void printDataTranspose(final String path, final String prefix, DataSet dataSet) {
        final List<Node> nodes = dataSet.getVariables();
        final Node group = dataSet.getVariable("Group");

        final List<Node> _nodes = new ArrayList<>();

        for (int i = 0; i < nodes.size(); i++) {
            if (nodes.get(i) == group) {
                _nodes.add(group);
            } else {
                _nodes.add(new ContinuousVariable("X" + (i + 1)));
            }
        }

        final Matrix m = dataSet.getDoubleData();

        final Matrix mt = m.transpose();

        final List<Node> tvars = new ArrayList<>();

        for (int i = 0; i < mt.columns(); i++) tvars.add(new ContinuousVariable("S" + (i + 1)));

        dataSet = new BoxDataSet(new DoubleDataBox(mt.toArray()), tvars);

//        dataSet = new BoxDataSet(new DoubleDataBox(dataSet.getDoubleData().toArray()), _nodes);
        dataSet.setNumberFormat(new DecimalFormat("0"));

        final File file1 = new File(path, prefix + ".data.txt");
        final File file2 = new File(path, prefix + ".dict.txt");

        try {
            final PrintStream out1 = new PrintStream(new FileOutputStream(file1));
            final PrintStream out2 = new PrintStream(new FileOutputStream(file2));

            out1.println(dataSet);
            out1.close();

            for (int i = 0; i < nodes.size(); i++) {
                out2.println(_nodes.get(i) + "\t" + nodes.get(i));
            }

            out2.println("Group");

            out2.close();
        } catch (final FileNotFoundException e) {
            e.printStackTrace();
            throw new RuntimeException();
        }
    }

    public void makeDataSpecial() {
        try {
            final String path = "/Users/jdramsey/Documents/LAB_NOTEBOOK.2012.04.20/data/USM_Datasets";
            final File file = new File(path, "concat_usm_dataset_madelyn.txt");
            final DataSet data = DataUtils.loadContinuousData(file, "//", '\"' ,
                    "*", true, Delimiter.TAB);

            final ContinuousVariable avg = new ContinuousVariable("Avg");
            data.addVariable(avg);

            for (int i = 0; i < data.getNumRows(); i++) {
                double sum = 0.0;

                for (int j = 0; j < data.getNumColumns() - 2; j++) {
                    sum += data.getDouble(i, j);
                }

                sum = data.getDouble(i, data.getNumColumns() - 2) == 1 ? 10 + RandomUtil.getInstance().nextUniform(-1, 1)
                        : -10 + RandomUtil.getInstance().nextUniform(-1, 1);

                final double _avg = sum / data.getNumColumns() - 2;

                data.setDouble(i, data.getNumColumns() - 1, _avg);
            }

            final File file2 = new File(path, "concat_usm_dataset_madelynB.txt");

            final PrintStream out = new PrintStream(new FileOutputStream(file2));
            out.println(data);
            out.close();
        } catch (final Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(final String... args) {
        new ExploreAutisticsNeurotypicals().printDegreeData();
    }
}





