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

package edu.cmu.tetrad.algcomparison;

import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.SearchGraphUtils;
import edu.cmu.tetrad.util.StatUtils;
import edu.cmu.tetrad.util.TextTable;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.*;

/**
 * @author Joseph Ramsey
 */
public class Comparison {
    private boolean[] graphTypeUsed;
    private PrintStream out;

    private Graph getSubgraph(Graph graph, boolean discrete1, boolean discrete2, DataSet dataSet) {
        if (discrete1 && discrete2) {
            Graph newGraph = new EdgeListGraph(graph.getNodes());

            for (Edge edge : graph.getEdges()) {
                Node node1 = dataSet.getVariable(edge.getNode1().getName());
                Node node2 = dataSet.getVariable(edge.getNode2().getName());

                if (node1 instanceof DiscreteVariable &&
                        node2 instanceof DiscreteVariable) {
                    newGraph.addEdge(edge);
                }
            }

            return newGraph;
        } else if (!discrete1 && !discrete2) {
            Graph newGraph = new EdgeListGraph(graph.getNodes());

            for (Edge edge : graph.getEdges()) {
                Node node1 = dataSet.getVariable(edge.getNode1().getName());
                Node node2 = dataSet.getVariable(edge.getNode2().getName());

                if (node1 instanceof ContinuousVariable &&
                        node2 instanceof ContinuousVariable) {
                    newGraph.addEdge(edge);
                }
            }

            return newGraph;
        } else {
            Graph newGraph = new EdgeListGraph(graph.getNodes());

            for (Edge edge : graph.getEdges()) {
                Node node1 = dataSet.getVariable(edge.getNode1().getName());
                Node node2 = dataSet.getVariable(edge.getNode2().getName());

                if (node1 instanceof DiscreteVariable &&
                        node2 instanceof ContinuousVariable) {
                    newGraph.addEdge(edge);
                }

                if (node1 instanceof ContinuousVariable &&
                        node2 instanceof DiscreteVariable) {
                    newGraph.addEdge(edge);
                }
            }

            return newGraph;
        }
    }

    public void testBestAlgorithms(Parameters parameters, Map<String, Double> statWeights,
                                   List<Algorithm> allAlgorithms, List<String> stats,
                                   Simulation simulation, PrintStream out, Algorithm.DataType dataType) {
        out.println(new Date());

        if (statWeights.keySet().contains("W")) {
            throw new IllegalArgumentException("The utility function may not refer to W.");

        }

        // Only consider the algorithms for the given data type. Mixed data types can go either way.
        // MGM algorithms won't run on continuous data or discrete data.
        List<Algorithm> algorithms = new ArrayList<>();

        for (Algorithm algorithm : allAlgorithms) {
            if (algorithm.getDescription().contains("MGM") && algorithm.getDataType() != Algorithm.DataType.Mixed) {
                continue;
            }

            if (algorithm.getDataType() == dataType || algorithm.getDataType() == Algorithm.DataType.Mixed) {
                algorithms.add(algorithm);
            }
        }

        Map<String, String> allStatDescriptions = new LinkedHashMap<>();
        allStatDescriptions.put("AP", "Adjacency Precision");
        allStatDescriptions.put("AR", "Adjacency Recall");
        allStatDescriptions.put("OP", "Orientation (Arrow) precision");
        allStatDescriptions.put("OR", "Orientation (Arrow) recall");
        allStatDescriptions.put("McAdj", "Matthew's correlation coeffficient for adjacencies");
        allStatDescriptions.put("McOr", "Matthew's correlation coefficient for arrow");
        allStatDescriptions.put("F1Adj", "F1 statistic for adjacencies");
        allStatDescriptions.put("F1Or", "F1 statistic for arrows");
        allStatDescriptions.put("SHD", "Structural Hamming Distance");
        allStatDescriptions.put("E", "Elapsed time in seconds");
        allStatDescriptions.put("W", "Utility of algorithm (a weighted sum of the sorted indices of algorithms on statistics)");

        Map<String, String> statDescriptions = new LinkedHashMap<>();

        for (String s : stats) {
            statDescriptions.put(s, allStatDescriptions.get(s));
        }

        this.out = out;
        double[][][][] allStats = calcStats(algorithms, statDescriptions, parameters, simulation);

        if (allStats != null) {
            out.println();
            out.println("Statistics:");
            out.println();

            for (String stat : statDescriptions.keySet()) {
                out.println(stat + " = " + statDescriptions.get(stat));
            }
        }

        out.println();
        out.println("Parameters:");
        out.println(parameters);
        out.println();
        out.println("Simulation:");
        out.println();
        out.println(simulation);
        out.println();

        if (allStats != null) {
            int numTables = allStats.length;
            int numAlgorithms = allStats[0].length;
            int numStats = allStats[0][0].length - 1;

            double[][][] statTables = calcStatTables(allStats, Mode.Average, numTables, numAlgorithms, numStats);
            double[] utilities = calcUtilities(statDescriptions, statWeights, numAlgorithms, numStats, statTables[0]);

            // Add utilities to table as the last column.
            for (int u = 0; u < numTables; u++) {
                for (int t1 = 0; t1 < numAlgorithms; t1++) {
                    statTables[u][t1][numStats] = utilities[t1];
                }
            }

            int[] newOrder = sort(algorithms, utilities);

            out.println("Algorithms (sorted high to low by W for average statistics):");
            out.println();

            for (int t = 0; t < algorithms.size(); t++) {
                out.println((t + 1) + ". " + algorithms.get(newOrder[t]).getDescription());
            }

            out.println();
            out.println("Weighting of statistics:\n");
            out.print("W = ");

            Iterator it0 = statDescriptions.keySet().iterator();

            while (it0.hasNext()) {
                String statName = (String) it0.next();
                Double weight = statWeights.get(statName);
                if (weight != null) {
                    out.println(weight + " * Index(" + statName + ")");
                    break;
                }
            }

            while (it0.hasNext()) {
                String statName = (String) it0.next();
                Double weight = statWeights.get(statName);
                if (weight != null) {
                    out.println("    " + weight + " * Index(" + statName + ")");
                }
            }

            out.println();


            // Add utilities to table as the last column.

            for (int u = 0; u < numTables; u++) {
                for (int t = 0; t < numAlgorithms; t++) {
                    statTables[u][t][numStats] = utilities[t];
                }
            }

            printStats(statTables, statDescriptions, Mode.Average, newOrder);

            statTables = calcStatTables(allStats, Mode.StandardDeviation, numTables, numAlgorithms, numStats);

            printStats(statTables, statDescriptions, Mode.StandardDeviation, newOrder);

            statTables = calcStatTables(allStats, Mode.WorstCase, numTables, numAlgorithms, numStats);

            // Add utilities to table as the last column.
            for (int u = 0; u < numTables; u++) {
                for (int t1 = 0; t1 < numAlgorithms; t1++) {
                    statTables[u][t1][numStats] = utilities[t1];
                }
            }

            printStats(statTables, statDescriptions, Mode.WorstCase, newOrder);
        }

        out.close();
    }

    private double[][][][] calcStats(List<Algorithm> algorithms, Map<String, String> stats,
                                     Parameters parameters, Simulation simulation) {
        int numGraphTypes = 4;

        graphTypeUsed = new boolean[4];
        int numRuns = parameters.getInt("numRuns");

        double[][][][] allStats = new double[4][algorithms.size()][stats.size()][numRuns];

        boolean didAnalysis = false;

        for (int i = 0; i < numRuns; i++) {
            System.out.println();
            System.out.println("Run " + (i + 1));
            System.out.println();

            DataSet data = simulation.getDataSet(i, parameters);
            Graph dag = simulation.getDag();

            boolean isMixed = data.isMixed();

            for (int t = 0; t < algorithms.size(); t++) {
                System.out.println((t + 1) + ". " + algorithms.get(t).getDescription());

                long start = System.currentTimeMillis();
                Graph out;

                try {
                    DataSet copy = data.copy();
                    out = algorithms.get(t).search(copy, parameters);
                } catch (Exception e) {
                    System.out.println("Could not run " + algorithms.get(t).getDescription());
                    e.printStackTrace();
                    continue;
                }

                if (dag == null && simulation instanceof SimulationPath) {
                    printGraph(((SimulationPath) simulation).getPath(), out, i, algorithms.get(t), parameters);
                } else {
                    printGraph(null, out, i, algorithms.get(t), parameters);
                }

                long stop = System.currentTimeMillis();

                long elapsed = stop - start;

                if (dag != null) {
                    out = GraphUtils.replaceNodes(out, dag.getNodes());
                }

                Graph[] est = new Graph[numGraphTypes];

                Graph comparisonGraph = dag == null ? null : algorithms.get(t).getComparisonGraph(dag);

                if (dag != null && out != null) {
                    dag = GraphUtils.replaceNodes(dag, out.getNodes());
                }

                est[0] = out;
                graphTypeUsed[0] = true;

                if (isMixed) {
                    est[1] = getSubgraph(out, true, true, data);
                    est[2] = getSubgraph(out, true, false, data);
                    est[3] = getSubgraph(out, false, false, data);

                    graphTypeUsed[1] = true;
                    graphTypeUsed[2] = true;
                    graphTypeUsed[3] = true;
                }

                Graph[] truth = new Graph[numGraphTypes];

                truth[0] = comparisonGraph;

                if (isMixed && comparisonGraph != null) {
                    truth[1] = getSubgraph(comparisonGraph, true, true, data);
                    truth[2] = getSubgraph(comparisonGraph, true, false, data);
                    truth[3] = getSubgraph(comparisonGraph, false, false, data);
                }

                if (comparisonGraph != null) {
                    for (int u = 0; u < numGraphTypes; u++) {
                        if (!graphTypeUsed[u]) continue;

                        EdgeStats edgeStats = new EdgeStats(est[u], truth[u], elapsed).invoke();

                        int j = -1;

                        for (String statName : stats.keySet()) {
                            j++;
                            double stat = edgeStats.getStat(statName);

                            if (!Double.isNaN(stat)) {
                                allStats[u][t][j][i] = stat;
                            }
                        }

                        didAnalysis = true;
                    }
                }
            }
        }

        return didAnalysis ? allStats : null;
    }

    private void printGraph(String path, Graph graph, int i, Algorithm algorithm, Parameters parameters) {
        if (parameters.getInt("printGraphs") != 1) {
            return;
        }

        try {
            String description = algorithm.getDescription();
            File file;

            if (path != null) {
                File _path = new File(path);
                file = new File("comparison/" + _path.getName() + "." + description + ".graph" + "." + (i + 1) + ".txt");
            } else {
                file = new File("comparison/" + description + ".graph" + "." + (i + 1) + ".txt");
            }

            PrintStream out = new PrintStream(file);
            System.out.println("Printing graph to " + file.getAbsolutePath());
            out.println(graph);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    private enum Mode {Average, StandardDeviation, WorstCase}

    private String getHeader(int u) {
        String header;

        switch (u) {
            case 0:
                header = "All edges";
                break;
            case 1:
                header = "Discrete-discrete";
                break;
            case 2:
                header = "Discrete-continuous";
                break;
            case 3:
                header = "Continuous-continuous";
                break;
            default:
                throw new IllegalStateException();
        }
        return header;
    }

    private double[][][] calcStatTables(double[][][][] allStats, Mode mode, int numTables, int numAlgorithms, int numStats) {
        double[][][] statTables = new double[numTables][numAlgorithms][numStats + 1];

        for (int u = 0; u < numTables; u++) {
            for (int i = 0; i < numAlgorithms; i++) {
                for (int j = 0; j < numStats; j++) {
                    if (mode == Mode.Average) {
                        statTables[u][i][j] = StatUtils.mean(allStats[u][i][j]);
                    } else if (mode == Mode.WorstCase) {
                        statTables[u][i][j] = StatUtils.min(allStats[u][i][j]);
                    } else if (mode == Mode.StandardDeviation) {
                        statTables[u][i][j] = StatUtils.sd(allStats[u][i][j]);
                    } else {
                        throw new IllegalStateException();
                    }
                }
            }
        }

        return statTables;
    }

    private void printStats(double[][][] statTables, Map<String, String> statDescriptions,
                            Mode mode, int[] newOrder) {

        if (mode == Mode.Average) {
            out.println("AVERAGE STATISTICS");
        } else if (mode == Mode.StandardDeviation) {
            out.println("STANDARD DEVIATIONS");
        } else if (mode == Mode.WorstCase) {
            out.println("WORST CASE");
        } else {
            throw new IllegalStateException();
        }

        int numTables = statTables.length;
        int numAlgorithms = statTables[0].length;
        int numStats = statTables[0][0].length - 1;


        NumberFormat nf = new DecimalFormat("0.00");

        out.println();

        for (int u = 0; u < numTables; u++) {
            if (!graphTypeUsed[u]) continue;

            TextTable table = new TextTable(numAlgorithms + 1, numStats + 2);

            table.setToken(0, 0, "Alg");

            for (int t = 0; t < numAlgorithms; t++) {
                table.setToken(t + 1, 0, "" + (t + 1));
            }

            Set<String> keySet = statDescriptions.keySet();
            Iterator<String> iterator = keySet.iterator();

            for (int statIndex = 0; statIndex < numStats + 1; statIndex++) {
                String statLabel = iterator.next();
                table.setToken(0, statIndex + 1, statLabel);
            }

            for (int t = 0; t < numAlgorithms; t++) {
                for (int statIndex = 0; statIndex < numStats + 1; statIndex++) {
                    double stat = statTables[u][newOrder[t]][statIndex];
                    table.setToken(t + 1, statIndex + 1, nf.format(Math.abs(stat)));
                }
            }

            out.println(getHeader(u));
            out.println();
            out.println(table);
        }
    }

    private double[] calcUtilities(Map<String, String> statDescriptions, Map<String, Double> statWeights,
                                   int numAlgorithms, int numStats, double[][] stats) {
        List<List<Double>> all = new ArrayList<>();

        for (int m = 0; m < numStats; m++) {
            ArrayList<Double> list = new ArrayList<>();

            try {
                for (int t = 0; t < numAlgorithms; t++) {
                    double _stat = stats[t][m];

                    if (!list.contains(_stat)) {
                        list.add(_stat);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            Collections.sort(list);

            all.add(list);
        }

        // Calculate utilities for the first table.
        double[] utilities = new double[numAlgorithms];

        for (int t = 0; t < numAlgorithms; t++) {
            double sum = 0.0;
            int j = -1;

            Iterator it2 = statDescriptions.keySet().iterator();
            int count = 0;

            while (it2.hasNext()) {
                String statName = (String) it2.next();
                j++;

                Double weight = statWeights.get(statName);

                if (weight != null) {
                    double _stat = stats[t][j];
                    double utility;

                    if (statName.equals("E") || statName.equals("SHD")) {
                        utility = all.get(j).indexOf(_stat) / (double) all.get(j).size();
                    } else {
                        utility = _stat;
                    }

                    sum += weight * utility;
                    count++;
                }

            }

            utilities[t] = sum / count;
        }
        return utilities;
    }

    private int[] sort(final List<Algorithm> algorithms, final double[] utilities) {
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < algorithms.size(); i++) order.add(i);

        Collections.sort(order, new Comparator<Integer>() {
            @Override
            public int compare(Integer o1, Integer o2) {
                return -Double.compare(utilities[o1], utilities[o2]);
            }
        });

        int[] newOrder = new int[algorithms.size()];
        for (int i = 0; i < order.size(); i++) newOrder[i] = order.get(i);

        return newOrder;
    }

    private class EdgeStats {
        private Graph est;
        private Graph truth;
        private double adjPrecision;
        private double adjRecall;
        private double arrowPrecision;
        private double arrowRecall;
        private double mcAdj;
        private double mcOr;
        private double f1Adj;
        private double f1Arrows;
        private int shd;
        private double elapsed;

        public EdgeStats(Graph est, Graph truth, long elapsed) {
            this.est = est;
            this.truth = GraphUtils.replaceNodes(truth, est.getNodes());
            this.elapsed = elapsed / 1000.0;
        }

        public double getStat(String stat) {
            switch (stat) {
                case "AP":
                    return adjPrecision;
                case "AR":
                    return adjRecall;
                case "OP":
                    return arrowPrecision;
                case "OR":
                    return arrowRecall;
                case "McAdj":
                    return mcAdj;
                case "McOr":
                    return mcOr;
                case "F1Adj":
                    return f1Adj;
                case "F1Or":
                    return f1Arrows;
                case "SHD":
                    return -shd;
                case "E":
                    return -elapsed;
                case "W":
                    return Double.NaN;
                default:
                    throw new IllegalArgumentException("No such stat: " + stat);
            }
        }

        public EdgeStats invoke() {
            int adjTp = 0;
            int adjFp = 0;
            int adjFn = 0;
            int arrowsTp = 0;
            int arrowsFp = 0;
            int arrowsFn = 0;

            Set<Edge> allUnoriented = new HashSet<>();
            for (Edge edge : truth.getEdges()) {
                allUnoriented.add(Edges.undirectedEdge(edge.getNode1(), edge.getNode2()));
            }

            for (Edge edge : est.getEdges()) {
                allUnoriented.add(Edges.undirectedEdge(edge.getNode1(), edge.getNode2()));
            }

            for (Edge edge : allUnoriented) {
                if (est.isAdjacentTo(edge.getNode1(), edge.getNode2()) &&
                        !truth.isAdjacentTo(edge.getNode1(), edge.getNode2())) {
                    adjFp++;
                }

                if (truth.isAdjacentTo(edge.getNode1(), edge.getNode2()) &&
                        !est.isAdjacentTo(edge.getNode1(), edge.getNode2())) {
                    adjFn++;
                }

                if (truth.isAdjacentTo(edge.getNode1(), edge.getNode2()) &&
                        est.isAdjacentTo(edge.getNode1(), edge.getNode2())) {
                    adjTp++;
                }
            }

            Set<Edge> allOriented = new HashSet<>();
            allOriented.addAll(truth.getEdges());
            allOriented.addAll(est.getEdges());

            for (Edge edge : allOriented) {
                Endpoint e1Est = edge.getProximalEndpoint(edge.getNode1());
                Endpoint e2Est = edge.getProximalEndpoint(edge.getNode2());

                Edge edge2 = truth.getEdge(edge.getNode1(), edge.getNode2());

                Endpoint e1True = null;
                Endpoint e2True = null;

                if (edge2 != null) {
                    e1True = edge2.getProximalEndpoint(edge.getNode1());
                    e2True = edge2.getProximalEndpoint(edge.getNode2());
                }

                if (e1Est == Endpoint.ARROW && e1True != Endpoint.ARROW) {
                    arrowsFp++;
                }

                if (e2Est == Endpoint.ARROW && e2True != Endpoint.ARROW) {
                    arrowsFp++;
                }

                if (e1True == Endpoint.ARROW && e1Est != Endpoint.ARROW) {
                    arrowsFn++;
                }

                if (e2True == Endpoint.ARROW && e2Est != Endpoint.ARROW) {
                    arrowsFn++;
                }

                if (e1True == Endpoint.ARROW && e1Est == Endpoint.ARROW) {
                    arrowsTp++;
                }

                if (e2True == Endpoint.ARROW && e2Est == Endpoint.ARROW) {
                    arrowsTp++;
                }
            }

            GraphUtils.GraphComparison comparison = SearchGraphUtils.getGraphComparison3(est, truth, System.out);

            int allEdges = truth.getNumNodes() * (truth.getNumNodes() - 1) / 2;

            int adjTn = allEdges - adjFn;
            int arrowsTn = allEdges - arrowsFn;

            adjPrecision = adjTp / (double) (adjTp + adjFp);
            adjRecall = adjTp / (double) (adjTp + adjFn);

            arrowPrecision = arrowsTp / (double) (arrowsTp + arrowsFp);
            arrowRecall = arrowsTp / (double) (arrowsTp + arrowsFn);

            mcAdj = mcc(adjTp, adjFp, adjTn, adjFn);
            mcOr = mcc(arrowsTp, arrowsFp, arrowsTn, arrowsFn);

            f1Adj = 2 * (adjPrecision * adjRecall) / (adjPrecision + adjRecall);
            f1Arrows = 2 * (arrowPrecision * arrowRecall) / (arrowPrecision + arrowRecall);

            shd = comparison.getShd();

            return this;
        }

        private double mcc(double adjTp, double adjFp, double adjTn, double adjFn) {
            double a = adjTp * adjTn - adjFp * adjFn;
            double b = (adjTp + adjFp) * (adjTp + adjFn) * (adjTn + adjFp) * (adjTn + adjFn);

            if (b == 0) b = 1;

            return a / Math.sqrt(b);
        }
    }
}




