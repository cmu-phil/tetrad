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

import edu.cmu.tetrad.data.CovarianceMatrixOnTheFly;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.Fas;
import edu.cmu.tetrad.search.IndTestScore;
import edu.cmu.tetrad.search.SemBicScore;
import edu.cmu.tetrad.util.Parameters;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.*;

/**
 * An example script to simulate data and run a comparison analysis on it.
 *
 * @author jdramsey
 */
public class TestAutisticClassification {
    enum Type {LEAVE_ONE_OUT, TRAIN_TEST}

    // Parameters.
    private double penaltyDiscount = 2;
    private int depth = 3;
    private int cutoffPresent = 10;
    private int cutoffAbsent = 10;

    private int trainIndex = 2;
    private int testIndex = 0;

    private Type type = Type.LEAVE_ONE_OUT;

    public void testAutistic() {
        Parameters parameters = new Parameters();

        parameters.set("penaltyDiscount", penaltyDiscount);
        parameters.set("depth", depth);
        parameters.set("twoCycleAlpha", 1e-5);

        FaskGraphs train;
        FaskGraphs test = null;

        if (trainIndex == 1) {
            train = new FaskGraphs("/Users/jdramsey/Documents/LAB_NOTEBOOK.2012.04.20/data/Joe_108_Variable",
                    parameters, "ROI_data");
        } else if (trainIndex == 2) {
            train = new FaskGraphs("/Users/jdramsey/Documents/LAB_NOTEBOOK.2012.04.20/data/USM_Datasets2",
                    parameters, "ROI_data");
        } else if (trainIndex == 3) {
            train = new FaskGraphs("/Users/jdramsey/Documents/LAB_NOTEBOOK.2012.04.20/data/Whole_Cerebellum_Scans",
                    parameters, "ROI_data");
        } else if (trainIndex == 4) {
            train = new FaskGraphs("/Users/jdramsey/Downloads/USM_ABIDE", new Parameters());
        } else {
            throw new IllegalArgumentException("Type must be an index 1-4");
        }

        if (type == Type.TRAIN_TEST) {
            if (testIndex == 1) {
                test = new FaskGraphs("/Users/jdramsey/Documents/LAB_NOTEBOOK.2012.04.20/data/Joe_108_Variable",
                        parameters, "ROI_data");
            } else if (testIndex == 2) {
                test = new FaskGraphs("/Users/jdramsey/Documents/LAB_NOTEBOOK.2012.04.20/data/USM_Datasets2",
                        parameters, "ROI_data");
            } else if (testIndex == 3) {
                test = new FaskGraphs("/Users/jdramsey/Documents/LAB_NOTEBOOK.2012.04.20/data/Whole_Cerebellum_Scans",
                        parameters, "ROI_data");
            } else if (testIndex == 4) {
                test = new FaskGraphs("/Users/jdramsey/Downloads/USM_ABIDE", new Parameters());
            } else {
                throw new IllegalArgumentException("Type must be an index 1-4");
            }

            train.reconcileNames(test);
            test.reconcileNames(train);
        }

        if (type == Type.LEAVE_ONE_OUT) {
            leaveOneOut(train);
        } else if (type == Type.TRAIN_TEST) {
            trainTest(train, test);
        }
    }

    private void trainTest(FaskGraphs train, FaskGraphs test) {
        int numTp = 0;
        int numFp = 0;
        int numMeh = 0;

        List<Edge> allEdges = getAllEdges(train.getGraphs(), train.getTypes(), train.getGraphs());
        List<List<Edge>> ret = train(train.getGraphs(), allEdges, train.getTypes());

        printFiles(train.getGraphs(), train.getTypes(), -1, ret);

        for (int i = 0; i < test.getGraphs().size(); i++) {
            int _class = test(i, test.getFilenames(), test.getGraphs(), test.getTypes(), ret);

            if (_class == 1) numTp++;
            if (_class == -1) numFp++;
            if (_class == 0) numMeh++;
        }

        System.out.println();
        System.out.println("# TP = " + numTp);
        System.out.println("# FP = " + numFp);
        System.out.println("# Unclassified = " + numMeh);

        NumberFormat nf = new DecimalFormat("0.00");
        System.out.println("Precision = " + nf.format((numTp / (double) (numTp + numFp))));
        System.out.println();
    }

    private void leaveOneOut(FaskGraphs train) {
        int numTp = 0;
        int numFp = 0;
        int numUnclassified = 0;

        for (int i = 0; i < train.getGraphs().size(); i++) {
            List<Graph> trainingGraphs = new ArrayList<>(train.getGraphs());
            trainingGraphs.remove(train.getGraphs().get(i));
            List<Edge> allEdges = getAllEdges(train.getGraphs(), train.getTypes(), trainingGraphs);

            List<List<Edge>> ret = train(trainingGraphs, allEdges, train.getTypes());
            int _class = test(i, train.getFilenames(), train.getGraphs(), train.getTypes(), ret);

            if (_class == 1) numTp++;
            if (_class == -1) numFp++;
            if (_class == 0) numUnclassified++;

            printFiles(train.getGraphs(), train.getTypes(), i, ret);
        }

        System.out.println();
        System.out.println("# TP = " + numTp);
        System.out.println("# FP = " + numFp);
        System.out.println("# Unclassified = " + numUnclassified);

        NumberFormat nf = new DecimalFormat("0.00");
        System.out.println("Precision = " + nf.format((numTp / (double) (numTp + numFp))));
        System.out.println();
    }

    private List<List<Edge>> train(List<Graph> trainingGraphs, List<Edge> allEdges, List<Boolean> types) {
        double[] truth = getTruth(trainingGraphs, types);

        List<Edge> forAutismIfPresent = new ArrayList<>();
        List<Edge> forAutismIfAbsent = new ArrayList<>();
        List<Edge> forTypicalIfPresent = new ArrayList<>();
        List<Edge> forTypicalIfAbsent = new ArrayList<>();

        for (Edge edge : allEdges) {
            double[] est = getEst(trainingGraphs, edge);

            if (cond(est, truth, 1, 1, cutoffPresent)) {
                forAutismIfPresent.add(edge);
            }

            if (cond(est, truth, 0, 1, cutoffAbsent)) {
                forAutismIfAbsent.add(edge);
            }

            if (cond(est, truth, 1, 0, cutoffPresent)) {
                forTypicalIfPresent.add(edge);
            }

            if (cond(est, truth, 0, 0, cutoffAbsent)) {
                forTypicalIfAbsent.add(edge);
            }
        }

        Set<Edge> sublist = new HashSet<>();

        sublist.addAll(forAutismIfPresent);
        sublist.addAll(forAutismIfAbsent);
        sublist.addAll(forTypicalIfPresent);
        sublist.addAll(forTypicalIfAbsent);

        List<Edge> _sublist = new ArrayList<>(sublist);

        // return from train.
        List<List<Edge>> ret = new ArrayList<>();
        ret.add(forAutismIfPresent);
        ret.add(forAutismIfAbsent);
        ret.add(forTypicalIfPresent);
        ret.add(forTypicalIfAbsent);
        ret.add(_sublist);

        return ret;
    }

    private int test(int i, List<String> filenames, List<Graph> graphs, List<Boolean> types, List<List<Edge>> ret) {
        Graph testGraph = graphs.get(i);

        List<Edge> forAutisismIfPresent = ret.get(0);
        List<Edge> forAutisismIfAbsent = ret.get(1);
        List<Edge> forTypicalIfPresent = ret.get(2);
        List<Edge> forTypicalIfAbsent = ret.get(3);

        List<Edge> presentAutistic = new ArrayList<>();
        List<Edge> absentAutistic = new ArrayList<>();
        List<Edge> presentTypical = new ArrayList<>();
        List<Edge> absentTypical = new ArrayList<>();

        for (Edge edge : forAutisismIfPresent) {
            if (testGraph.containsEdge(edge)) {
                presentAutistic.add(edge);
            }
        }

        for (Edge edge : forAutisismIfAbsent) {
            if (!testGraph.containsEdge(edge)) {
                absentAutistic.add(edge);
            }
        }

        for (Edge edge : forTypicalIfPresent) {
            if (testGraph.containsEdge(edge)) {
                presentTypical.add(edge);
            }
        }

        for (Edge edge : forTypicalIfAbsent) {
            if (!testGraph.containsEdge(edge)) {
                absentTypical.add(edge);
            }
        }

        boolean autistic = false;
        boolean typical = false;

        if (!absentAutistic.isEmpty()) {
            autistic = true;
        }

        if (!presentAutistic.isEmpty()) {
            autistic = true;
        }

        if (!presentTypical.isEmpty()) {
            typical = true;
        }

        if (!absentTypical.isEmpty()) {
            typical = true;
        }

        String name = "" + (i + 1) + ". " + filenames.get(i) + ". ";

        if (autistic && !typical) {
            System.out.println(name + ". Autistic");
        } else if (typical && !autistic) {
            System.out.println(name + ". Typical");
        }

        if (autistic && !typical) {
            for (Edge aPresent : presentAutistic) {
                System.out.println("..... present autistic " + aPresent);
            }

            for (Edge anAbsent : absentAutistic) {
                System.out.println("..... absent autistic " + anAbsent);
            }
        }

        if (typical && !autistic) {
            for (Edge aPresent : presentTypical) {
                System.out.println("..... present typical " + aPresent);
            }

            for (Edge anAbsent : absentTypical) {
                System.out.println("..... absent typical " + anAbsent);
            }
        }

        if (autistic && !typical) {
            if (types.get(i)) {
                return 1;
            } else {
                return -1;
            }
        }

        if (typical && !autistic) {
            if (!types.get(i)) {
                return 1;
            } else {
                return -1;
            }
        }

        return 0;
    }

    private List<Edge> getAllEdges(List<Graph> _graphs, List<Boolean> types, List<Graph> trainingGraphs) {
        Map<Edge, Double> autisticEdgeCount = new HashMap<>();
        Map<Edge, Double> typicalEdgeCount = new HashMap<>();

        Set<Edge> allEdgesSet = new HashSet<>();

        for (int k = 0; k < trainingGraphs.size(); k++) {
            for (Edge edge : trainingGraphs.get(k).getEdges()) {
                if (types.get(k)) {
                    countEdge(autisticEdgeCount, edge);
                } else {
                    countEdge(typicalEdgeCount, edge);
                }
            }

            allEdgesSet.addAll(_graphs.get(k).getEdges());
        }
        return new ArrayList<>(allEdgesSet);
    }

    private void printFiles(List<Graph> _graphs, List<Boolean> types, int i, List<List<Edge>> ret) {
        try {
            List<Edge> sublist = ret.get(4);

            File dir2 = new File("/Users/jdramsey/Downloads/alldata");
            dir2.mkdirs();
            PrintStream out = new PrintStream(new File(dir2, "data" + (i + 1) + ".txt"));

            for (int j = 0; j < sublist.size(); j++) {
                out.print("X" + (j + 1) + "\t");
            }

            out.println("T");

            for (int k = 0; k < _graphs.size(); k++) {
                for (Edge edge : sublist) {
                    out.print(_graphs.get(k).containsEdge(edge) ? "1\t" : "0\t");
                }

                out.println(types.get(k) ? "1" : "0");
            }

            out.close();

            File dir3 = new File("/Users/jdramsey/Downloads/allkeys");
            dir3.mkdirs();
            PrintStream keyOut = new PrintStream(new File(dir3, "key" + (i + 1) + ".txt"));

            for (int j = 0; j < sublist.size(); j++) {
                keyOut.println("X" + (j + 1) + ". " + sublist.get(j));
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    private double[] getTruth(List<Graph> trainingGraphs, List<Boolean> types) {
        double[] truth = new double[trainingGraphs.size()];
        int w = 0;

        for (int k = 0; k < trainingGraphs.size(); k++) {
            truth[w++] = types.get(k) ? 1.0 : 0.0;
        }
        return truth;
    }

    private double[] getEst(List<Graph> trainingGraphs, Edge edge) {
        double[] est = new double[trainingGraphs.size()];
        int w = 0;

        for (Graph trainingGraph : trainingGraphs) {
            est[w++] = trainingGraph.containsEdge(edge) ? 1.0 : 0.0;
        }

        return est;
    }

    // Returns true if a2 = j on condition that a1 = i.
    private boolean cond(double[] a1, double[] a2, int i, int j, int min) {
        int occurs = 0;
        int isTheCase = 0;

        int N = a2.length;

        for (int w = 0; w < N; w++) {
            if (a1[w] == i) {
                occurs++;

                if (a2[w] == j) {
                    isTheCase++;
                }
            }
        }

        double prob = isTheCase / (double) occurs;

//        if (occurs >= 4 && isTheCase >= .75 * occurs) {
//            System.out.println();
//        }

        return occurs >= min && prob >= 0.75;
    }

    private Double count(Map<Edge, Double> edges1Count, Edge edge) {
        if (edges1Count.get(edge) == null) return 0.0;
        return edges1Count.get(edge);
    }

    private void countEdge(Map<Edge, Double> map, Edge edge) {
        if (count(map, edge) == null) {
            map.put(edge, 0.0);
        }

        map.put(edge, count(map, edge) + 1);
    }

    private List<Graph> replaceNodes(List<Graph> graphs, List<Node> nodes) {
        List<Graph> replaced = new ArrayList<>();

        for (Graph graph : graphs) {
            replaced.add(GraphUtils.replaceNodes(graph, nodes));
        }

        return replaced;
    }

//    @Test
    public void testForBiwei() {
        Parameters parameters = new Parameters();

        parameters.set("penaltyDiscount", 2);
        parameters.set("depth", -1);

        parameters.set("numRuns", 10);
        parameters.set("randomSelectionSize", 1);
        parameters.set("Structure", "Placeholder");

        FaskGraphs files = new FaskGraphs("/Users/jdramsey/Downloads/USM_ABIDE", new Parameters());

        List<DataSet> datasets = files.getDatasets();
        List<String> filenames = files.getFilenames();

        for (int i = 0; i < datasets.size(); i++) {
            DataSet dataSet = datasets.get(i);

            SemBicScore score = new SemBicScore(new CovarianceMatrixOnTheFly(dataSet));
            Fas fas = new Fas(new IndTestScore(score));
            Graph graph = fas.search();

            System.out.println(graph);
            List<Node> nodes = graph.getNodes();

            StringBuilder b = new StringBuilder();

            for (int j = 0; j < nodes.size(); j++) {
                for (int k = 0; k < nodes.size(); k++) {
                    if (graph.isAdjacentTo(nodes.get(j), nodes.get(k))) {
                        b.append("1 ");
                    } else {
                        b.append("0 ");
                    }
                }

                b.append("\n");
            }

            try {
                File dir = new File("/Users/jdramsey/Downloads/biwei/USM_ABIDE");
                dir.mkdirs();
                File file = new File(dir, filenames.get(i) + ".graph.txt");
                PrintStream out = new PrintStream(file);
                out.println(b);
                out.close();
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }

        }
    }

    public static void main(String... args) {
        new TestAutisticClassification().testAutistic();
    }
}




