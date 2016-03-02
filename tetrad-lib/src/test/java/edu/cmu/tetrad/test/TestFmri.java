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
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.Fgs;
import edu.cmu.tetrad.util.RandomUtil;
import org.junit.Test;

import java.io.*;
import java.text.DecimalFormat;
import java.util.*;

import static junit.framework.TestCase.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * @author Joseph Ramsey
 */
public final class TestFmri {

    public void printEdgeData() {

//        String[] autistics = {
//                "autistic_normal_ROI_data_spline_smooth_clean_001.txt",
//                "autistic_normal_ROI_data_spline_smooth_clean_002.txt",
//                "autistic_normal_ROI_data_spline_smooth_clean_003.txt",
//                "autistic_normal_ROI_data_spline_smooth_clean_004.txt",
//                "autistic_normal_ROI_data_spline_smooth_clean_005.txt",
//                "autistic_normal_ROI_data_spline_smooth_clean_006.txt",
//                "autistic_normal_ROI_data_spline_smooth_clean_007.txt",
//                "autistic_normal_ROI_data_spline_smooth_clean_008.txt",
//                "autistic_normal_ROI_data_spline_smooth_clean_009.txt",
//                "autistic_normal_ROI_data_spline_smooth_clean_010.txt"
//        };
//
//        String[] neurotypicals = {
//                "typical_normal_ROI_data_spline_smooth_clean_001.txt",
//                "typical_normal_ROI_data_spline_smooth_clean_002.txt",
//                "typical_normal_ROI_data_spline_smooth_clean_003.txt",
//                "typical_normal_ROI_data_spline_smooth_clean_004.txt",
//                "typical_normal_ROI_data_spline_smooth_clean_005.txt",
//                "typical_normal_ROI_data_spline_smooth_clean_006.txt",
//                "typical_normal_ROI_data_spline_smooth_clean_007.txt",
//                "typical_normal_ROI_data_spline_smooth_clean_008.txt",
//                "typical_normal_ROI_data_spline_smooth_clean_009.txt",
//                "typical_normal_ROI_data_spline_smooth_clean_010.txt"
//        };

//        String[] autistics = {
//                "autistic_normal_ROI_data_spline_smooth_clean_001.txt",
//                "autistic_normal_ROI_data_spline_smooth_clean_002.txt",
//                "autistic_normal_ROI_data_spline_smooth_clean_003.txt",
//                "autistic_normal_ROI_data_spline_smooth_clean_004.txt",
//                "autistic_normal_ROI_data_spline_smooth_clean_005.txt",
//                "autistic_normal_ROI_data_spline_smooth_clean_006.txt",
//                "autistic_normal_ROI_data_spline_smooth_clean_007.txt",
//                "autistic_normal_ROI_data_spline_smooth_clean_008.txt",
//                "autistic_normal_ROI_data_spline_smooth_clean_009.txt",
//                "autistic_normal_ROI_data_spline_smooth_clean_010.txt"
//        };
//
//        String[] neurotypicals = {
//                "typical_normal_ROI_data_spline_smooth_clean_001.txt",
//                "typical_normal_ROI_data_spline_smooth_clean_002.txt",
//                "typical_normal_ROI_data_spline_smooth_clean_003.txt",
//                "typical_normal_ROI_data_spline_smooth_clean_004.txt",
//                "typical_normal_ROI_data_spline_smooth_clean_005.txt",
//                "typical_normal_ROI_data_spline_smooth_clean_006.txt",
//                "typical_normal_ROI_data_spline_smooth_clean_007.txt",
//                "typical_normal_ROI_data_spline_smooth_clean_008.txt",
//                "typical_normal_ROI_data_spline_smooth_clean_009.txt",
//                "typical_normal_ROI_data_spline_smooth_clean_010.txt"
//        };


        String path = "/Users/jdramsey/Documents/LAB_NOTEBOOK.2012.04.20/data/Joe_90_Variable";

        try {
            List<DataSet> autisticDataSets = new ArrayList<>();
            List<DataSet> neurotypicalDataSets = new ArrayList<>();

            File dir = new File(path);
            File[] files = dir.listFiles();

            for (File file : files) {
                if (file.getName().startsWith("autistic")) {
                    DataReader reader = new DataReader();
                    reader.setDelimiter(DelimiterType.TAB);
                    autisticDataSets.add(reader.parseTabular(file));
                } else if (file.getName().startsWith("typical")) {
                    DataReader reader = new DataReader();
                    reader.setDelimiter(DelimiterType.TAB);
                    neurotypicalDataSets.add(reader.parseTabular(file));
                }
            }

//            for (int i = 0; i < 10; i++) {
//                DataReader reader = new DataReader();
//                reader.setDelimiter(DelimiterType.TAB);
//                autisticDataSets.add(reader.parseTabular(new File(path, autistics[i])));
//            }
//
//
//            for (int i = 0; i < 10; i++) {
//                DataReader reader = new DataReader();
//                reader.setDelimiter(DelimiterType.TAB);
//                neurotypicalDataSets.add(reader.parseTabular(new File(path, neurotypicals[i])));
//            }

            List<Graph> autisticGraphs = new ArrayList<>();
            double penaltyDiscount = 2;

            for (DataSet dataSet : autisticDataSets) {
                Fgs search = new Fgs(dataSet);
                search.setVerbose(true);
                search.setPenaltyDiscount(penaltyDiscount);
//                PcLocal search = new PcLocal(new IndTestScore(new SemBicScore(new CovarianceMatrixOnTheFly(dataSet)), penaltyDiscount));
//                autisticGraphs.add(GraphUtils.undirectedGraph(search.search()));
                autisticGraphs.add(search.search());
            }

            List<Graph> neurotypicalGraphs = new ArrayList<>();

            for (DataSet dataSet : neurotypicalDataSets) {
                Fgs search = new Fgs(dataSet);
                search.setVerbose(true);
                search.setPenaltyDiscount(penaltyDiscount);
//                PcLocal search = new PcLocal(new IndTestScore(new SemBicScore(new CovarianceMatrixOnTheFly(dataSet)), penaltyDiscount));
//                neurotypicalGraphs.add(GraphUtils.undirectedGraph(search.search()));
                neurotypicalGraphs.add(search.search());
            }

            List<List<Graph>> allGraphs = new ArrayList<>();
            allGraphs.add(autisticGraphs);
            allGraphs.add(neurotypicalGraphs);

            printEdgeDataSet(allGraphs, path, "edgedata");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Builds a data set with columns being the set of all edges in the supplied graphs
     * plus a class label column for which group they belong to.
     */
    public static void printEdgeDataSet(List<List<Graph>> graphs, String path, String prefix) {
        Set<Node> _nodes = new HashSet<>();
        int numGraphs = 0;

        for (List<Graph> _graphs : graphs) {
            for (Graph graph : _graphs) {
                numGraphs++;
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

        List<List<Graph>> graphs2 = new ArrayList<>();

        for (List<Graph> _graphs : graphs) {
            List<Graph> _graphs2 = new ArrayList<>();

            for (Graph graph : _graphs) {
                Graph graph2 = GraphUtils.replaceNodes(graph, new ArrayList<Node>(_nodes));
                _graphs2.add(graph2);
            }

            graphs2.add(_graphs2);
        }

        Set<Edge> _edges = new HashSet<>();

        for (List<Graph> _graphs : graphs) {
            for (Graph graph : _graphs) {
                _edges.addAll(graph.getEdges());
            }
        }

        List<Edge> __edges = new ArrayList<>();
        System.out.println("# edges = " + _edges.size());

        for (Edge edge : _edges) {
            int count = 0;
            int total = 0;

            for (List<Graph> _graphs : graphs) {
                for (Graph graph : _graphs) {
                    if (graph.containsEdge(edge)) {
                        count++;
                    }

                    total++;
                }
            }

            if (count >= numGraphs * 0.3 && count <= numGraphs * 0.7) {
                __edges.add(edge);
            }
        }

        printEdgeData(path, prefix, graphs2, __edges);
    }

    private static void printEdgeData(String path, String prefix, List<List<Graph>> graphs2, List<Edge> edges) {
        System.out.println("# edges = " + edges.size());

        File file1 = new File(path, prefix + ".data.txt");
        File file2 = new File(path, prefix + ".edges.txt");
        File dir = new File(path);

        PrintStream out1 = null;
        PrintStream out2 = null;
        try {
            out1 = new PrintStream(new FileOutputStream(file1));
            out2 = new PrintStream(new FileOutputStream(file2));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            throw new RuntimeException();
        }

        List<Node> edgeVars = new ArrayList<>();

        List<String> categories = new ArrayList<>();
        categories.add("0");
        categories.add("1");

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
        out1.println(dataSet);
        out1.close();

        for (int i = 0; i < edges.size(); i++) {
            out2.println("X" + (i + 1) + "\t" + edges.get(i));
        }

        out2.println("Group");

        out2.close();
    }

    public void test8() {
        int numNodes = 5;

        for (int i = 0; i < 100000; i++) {
            Graph graph = GraphUtils.randomGraphRandomForwardEdges(numNodes, 0, numNodes, 10, 10, 10, true);

            List<Node> nodes = graph.getNodes();
            Node x = nodes.get(RandomUtil.getInstance().nextInt(numNodes));
            Node y = nodes.get(RandomUtil.getInstance().nextInt(numNodes));
            Node z1 = nodes.get(RandomUtil.getInstance().nextInt(numNodes));
            Node z2 = nodes.get(RandomUtil.getInstance().nextInt(numNodes));

            if (graph.isDSeparatedFrom(x, y, list(z1)) && graph.isDSeparatedFrom(x, y, list(z2)) &&
                    !graph.isDSeparatedFrom(x, y, list(z1, z2))) {
                System.out.println("x = " + x);
                System.out.println("y = " + y);
                System.out.println("z1 = " + z1);
                System.out.println("z2 = " + z2);
                System.out.println(graph);
                return;
            }
        }
    }

    private List<Node> list(Node... z) {
        List<Node> list = new ArrayList<>();
        Collections.addAll(list, z);
        return list;
    }
}





