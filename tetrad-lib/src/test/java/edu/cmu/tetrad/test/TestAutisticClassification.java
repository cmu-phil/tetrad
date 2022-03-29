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

import edu.cmu.tetrad.data.CovarianceMatrix;
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
    private final double penaltyDiscount = 2;
    private final int depth = 3;
    private final int cutoffPresent = 10;
    private final int cutoffAbsent = 10;

    private final int trainIndex = 2;
    private final int testIndex = 0;

    private final Type type = Type.LEAVE_ONE_OUT;

    public void testAutistic() {
        final Parameters parameters = new Parameters();

        parameters.set("penaltyDiscount", this.penaltyDiscount);
        parameters.set("depth", this.depth);
        parameters.set("twoCycleAlpha", 1e-5);

        final FaskGraphs train;
        FaskGraphs test = null;

        if (this.trainIndex == 1) {
            train = new FaskGraphs("/Users/jdramsey/Documents/LAB_NOTEBOOK.2012.04.20/data/Joe_108_Variable",
                    parameters, "ROI_data");
        } else if (this.trainIndex == 2) {
            train = new FaskGraphs("/Users/jdramsey/Documents/LAB_NOTEBOOK.2012.04.20/data/USM_Datasets2",
                    parameters, "ROI_data");
        } else if (this.trainIndex == 3) {
            train = new FaskGraphs("/Users/jdramsey/Documents/LAB_NOTEBOOK.2012.04.20/data/Whole_Cerebellum_Scans",
                    parameters, "ROI_data");
        } else if (this.trainIndex == 4) {
            train = new FaskGraphs("/Users/jdramsey/Downloads/USM_ABIDE", new Parameters());
        } else {
            throw new IllegalArgumentException("Type must be an index 1-4");
        }

        if (this.type == Type.TRAIN_TEST) {
            if (this.testIndex == 1) {
                test = new FaskGraphs("/Users/jdramsey/Documents/LAB_NOTEBOOK.2012.04.20/data/Joe_108_Variable",
                        parameters, "ROI_data");
            } else if (this.testIndex == 2) {
                test = new FaskGraphs("/Users/jdramsey/Documents/LAB_NOTEBOOK.2012.04.20/data/USM_Datasets2",
                        parameters, "ROI_data");
            } else if (this.testIndex == 3) {
                test = new FaskGraphs("/Users/jdramsey/Documents/LAB_NOTEBOOK.2012.04.20/data/Whole_Cerebellum_Scans",
                        parameters, "ROI_data");
            } else if (this.testIndex == 4) {
                test = new FaskGraphs("/Users/jdramsey/Downloads/USM_ABIDE", new Parameters());
            } else {
                throw new IllegalArgumentException("Type must be an index 1-4");
            }

            train.reconcileNames(test);
            test.reconcileNames(train);
        }

        if (this.type == Type.LEAVE_ONE_OUT) {
            leaveOneOut(train);
        } else if (this.type == Type.TRAIN_TEST) {
            trainTest(train, test);
        }
    }

    private void trainTest(final FaskGraphs train, final FaskGraphs test) {
        int numTp = 0;
        int numFp = 0;
        int numMeh = 0;

        final List<Edge> allEdges = getAllEdges(train.getGraphs(), train.getTypes(), train.getGraphs());
        final List<List<Edge>> ret = train(train.getGraphs(), allEdges, train.getTypes());

        printFiles(train.getGraphs(), train.getTypes(), -1, ret);

        for (int i = 0; i < test.getGraphs().size(); i++) {
            final int _class = test(i, test.getFilenames(), test.getGraphs(), test.getTypes(), ret);

            if (_class == 1) numTp++;
            if (_class == -1) numFp++;
            if (_class == 0) numMeh++;
        }

        System.out.println();
        System.out.println("# TP = " + numTp);
        System.out.println("# FP = " + numFp);
        System.out.println("# Unclassified = " + numMeh);

        final NumberFormat nf = new DecimalFormat("0.00");
        System.out.println("Precision = " + nf.format((numTp / (double) (numTp + numFp))));
        System.out.println();
    }

    private void leaveOneOut(final FaskGraphs train) {
        int numTp = 0;
        int numFp = 0;
        int numUnclassified = 0;

        for (int i = 0; i < train.getGraphs().size(); i++) {
            final List<Graph> trainingGraphs = new ArrayList<>(train.getGraphs());
            trainingGraphs.remove(train.getGraphs().get(i));
            final List<Edge> allEdges = getAllEdges(train.getGraphs(), train.getTypes(), trainingGraphs);

            final List<List<Edge>> ret = train(trainingGraphs, allEdges, train.getTypes());
            final int _class = test(i, train.getFilenames(), train.getGraphs(), train.getTypes(), ret);

            if (_class == 1) numTp++;
            if (_class == -1) numFp++;
            if (_class == 0) numUnclassified++;

            printFiles(train.getGraphs(), train.getTypes(), i, ret);
        }

        System.out.println();
        System.out.println("# TP = " + numTp);
        System.out.println("# FP = " + numFp);
        System.out.println("# Unclassified = " + numUnclassified);

        final NumberFormat nf = new DecimalFormat("0.00");
        System.out.println("Precision = " + nf.format((numTp / (double) (numTp + numFp))));
        System.out.println();
    }

    private List<List<Edge>> train(final List<Graph> trainingGraphs, final List<Edge> allEdges, final List<Boolean> types) {
        final double[] truth = getTruth(trainingGraphs, types);

        final List<Edge> forAutismIfPresent = new ArrayList<>();
        final List<Edge> forAutismIfAbsent = new ArrayList<>();
        final List<Edge> forTypicalIfPresent = new ArrayList<>();
        final List<Edge> forTypicalIfAbsent = new ArrayList<>();

        for (final Edge edge : allEdges) {
            final double[] est = getEst(trainingGraphs, edge);

            if (cond(est, truth, 1, 1, this.cutoffPresent)) {
                forAutismIfPresent.add(edge);
            }

            if (cond(est, truth, 0, 1, this.cutoffAbsent)) {
                forAutismIfAbsent.add(edge);
            }

            if (cond(est, truth, 1, 0, this.cutoffPresent)) {
                forTypicalIfPresent.add(edge);
            }

            if (cond(est, truth, 0, 0, this.cutoffAbsent)) {
                forTypicalIfAbsent.add(edge);
            }
        }

        final Set<Edge> sublist = new HashSet<>();

        sublist.addAll(forAutismIfPresent);
        sublist.addAll(forAutismIfAbsent);
        sublist.addAll(forTypicalIfPresent);
        sublist.addAll(forTypicalIfAbsent);

        final List<Edge> _sublist = new ArrayList<>(sublist);

        // return from train.
        final List<List<Edge>> ret = new ArrayList<>();
        ret.add(forAutismIfPresent);
        ret.add(forAutismIfAbsent);
        ret.add(forTypicalIfPresent);
        ret.add(forTypicalIfAbsent);
        ret.add(_sublist);

        return ret;
    }

    private int test(final int i, final List<String> filenames, final List<Graph> graphs, final List<Boolean> types, final List<List<Edge>> ret) {
        final Graph testGraph = graphs.get(i);

        final List<Edge> forAutisismIfPresent = ret.get(0);
        final List<Edge> forAutisismIfAbsent = ret.get(1);
        final List<Edge> forTypicalIfPresent = ret.get(2);
        final List<Edge> forTypicalIfAbsent = ret.get(3);

        final List<Edge> presentAutistic = new ArrayList<>();
        final List<Edge> absentAutistic = new ArrayList<>();
        final List<Edge> presentTypical = new ArrayList<>();
        final List<Edge> absentTypical = new ArrayList<>();

        for (final Edge edge : forAutisismIfPresent) {
            if (testGraph.containsEdge(edge)) {
                presentAutistic.add(edge);
            }
        }

        for (final Edge edge : forAutisismIfAbsent) {
            if (!testGraph.containsEdge(edge)) {
                absentAutistic.add(edge);
            }
        }

        for (final Edge edge : forTypicalIfPresent) {
            if (testGraph.containsEdge(edge)) {
                presentTypical.add(edge);
            }
        }

        for (final Edge edge : forTypicalIfAbsent) {
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

        final String name = "" + (i + 1) + ". " + filenames.get(i) + ". ";

        if (autistic && !typical) {
            System.out.println(name + ". Autistic");
        } else if (typical && !autistic) {
            System.out.println(name + ". Typical");
        }

        if (autistic && !typical) {
            for (final Edge aPresent : presentAutistic) {
                System.out.println("..... present autistic " + aPresent);
            }

            for (final Edge anAbsent : absentAutistic) {
                System.out.println("..... absent autistic " + anAbsent);
            }
        }

        if (typical && !autistic) {
            for (final Edge aPresent : presentTypical) {
                System.out.println("..... present typical " + aPresent);
            }

            for (final Edge anAbsent : absentTypical) {
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

    private List<Edge> getAllEdges(final List<Graph> _graphs, final List<Boolean> types, final List<Graph> trainingGraphs) {
        final Map<Edge, Double> autisticEdgeCount = new HashMap<>();
        final Map<Edge, Double> typicalEdgeCount = new HashMap<>();

        final Set<Edge> allEdgesSet = new HashSet<>();

        for (int k = 0; k < trainingGraphs.size(); k++) {
            for (final Edge edge : trainingGraphs.get(k).getEdges()) {
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

    private void printFiles(final List<Graph> _graphs, final List<Boolean> types, final int i, final List<List<Edge>> ret) {
        try {
            final List<Edge> sublist = ret.get(4);

            final File dir2 = new File("/Users/jdramsey/Downloads/alldata");
            dir2.mkdirs();
            final PrintStream out = new PrintStream(new File(dir2, "data" + (i + 1) + ".txt"));

            for (int j = 0; j < sublist.size(); j++) {
                out.print("X" + (j + 1) + "\t");
            }

            out.println("T");

            for (int k = 0; k < _graphs.size(); k++) {
                for (final Edge edge : sublist) {
                    out.print(_graphs.get(k).containsEdge(edge) ? "1\t" : "0\t");
                }

                out.println(types.get(k) ? "1" : "0");
            }

            out.close();

            final File dir3 = new File("/Users/jdramsey/Downloads/allkeys");
            dir3.mkdirs();
            final PrintStream keyOut = new PrintStream(new File(dir3, "key" + (i + 1) + ".txt"));

            for (int j = 0; j < sublist.size(); j++) {
                keyOut.println("X" + (j + 1) + ". " + sublist.get(j));
            }
        } catch (final FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    private double[] getTruth(final List<Graph> trainingGraphs, final List<Boolean> types) {
        final double[] truth = new double[trainingGraphs.size()];
        int w = 0;

        for (int k = 0; k < trainingGraphs.size(); k++) {
            truth[w++] = types.get(k) ? 1.0 : 0.0;
        }
        return truth;
    }

    private double[] getEst(final List<Graph> trainingGraphs, final Edge edge) {
        final double[] est = new double[trainingGraphs.size()];
        int w = 0;

        for (final Graph trainingGraph : trainingGraphs) {
            est[w++] = trainingGraph.containsEdge(edge) ? 1.0 : 0.0;
        }

        return est;
    }

    // Returns true if a2 = j on condition that a1 = i.
    private boolean cond(final double[] a1, final double[] a2, final int i, final int j, final int min) {
        int occurs = 0;
        int isTheCase = 0;

        final int N = a2.length;

        for (int w = 0; w < N; w++) {
            if (a1[w] == i) {
                occurs++;

                if (a2[w] == j) {
                    isTheCase++;
                }
            }
        }

        final double prob = isTheCase / (double) occurs;

//        if (occurs >= 4 && isTheCase >= .75 * occurs) {
//            System.out.println();
//        }

        return occurs >= min && prob >= 0.75;
    }

    private Double count(final Map<Edge, Double> edges1Count, final Edge edge) {
        if (edges1Count.get(edge) == null) return 0.0;
        return edges1Count.get(edge);
    }

    private void countEdge(final Map<Edge, Double> map, final Edge edge) {
        if (count(map, edge) == null) {
            map.put(edge, 0.0);
        }

        map.put(edge, count(map, edge) + 1);
    }

    private List<Graph> replaceNodes(final List<Graph> graphs, final List<Node> nodes) {
        final List<Graph> replaced = new ArrayList<>();

        for (final Graph graph : graphs) {
            replaced.add(GraphUtils.replaceNodes(graph, nodes));
        }

        return replaced;
    }

    //    @Test
    public void testForBiwei() {
        final Parameters parameters = new Parameters();

        parameters.set("penaltyDiscount", 2);
        parameters.set("depth", -1);

        parameters.set("numRuns", 10);
        parameters.set("randomSelectionSize", 1);
        parameters.set("Structure", "Placeholder");

        final FaskGraphs files = new FaskGraphs("/Users/jdramsey/Downloads/USM_ABIDE", new Parameters());

        final List<DataSet> datasets = files.getDatasets();
        final List<String> filenames = files.getFilenames();

        for (int i = 0; i < datasets.size(); i++) {
            final DataSet dataSet = datasets.get(i);

            final SemBicScore score = new SemBicScore(new CovarianceMatrix(dataSet));
            final Fas fas = new Fas(new IndTestScore(score));
            final Graph graph = fas.search();

            System.out.println(graph);
            final List<Node> nodes = graph.getNodes();

            final StringBuilder b = new StringBuilder();

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
                final File dir = new File("/Users/jdramsey/Downloads/biwei/USM_ABIDE");
                dir.mkdirs();
                final File file = new File(dir, filenames.get(i) + ".graph.txt");
                final PrintStream out = new PrintStream(file);
                out.println(b);
                out.close();
            } catch (final FileNotFoundException e) {
                e.printStackTrace();
            }

        }
    }

    public static void main(final String... args) {
        new TestAutisticClassification().testAutistic();
    }
}




