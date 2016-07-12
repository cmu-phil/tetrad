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

import edu.cmu.tetrad.algcomparison.interfaces.Algorithm;
import edu.cmu.tetrad.algcomparison.interfaces.DataType;
import edu.cmu.tetrad.algcomparison.interfaces.Simulation;
import edu.cmu.tetrad.algcomparison.interfaces.Statistic;
import edu.cmu.tetrad.algcomparison.statistic.ElapsedTimeStat;
import edu.cmu.tetrad.algcomparison.statistic.utilities.SimulationPath;
import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataWriter;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.util.StatUtils;
import edu.cmu.tetrad.util.TextTable;

import java.io.*;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.*;

/**
 * Script to do a comparison of a list of algorithms using a list of statistics and a list
 * of parameters and their values.
 *
 * @author Joseph Ramsey
 */
public class Comparison {
    private boolean[] graphTypeUsed;
    private PrintStream out;

    /**
     * Compares algorithms.
     * @param path Path to the file where the output should be printed.
     * @param simulation The simulation that is used to generate graphs and data for the comparison.
     * @param algorithms The list of algorithms to be compared.
     * @param statistics The list of statistics on which to compare the algorithms, and their utility weights.
     * @param parameters The list of parameters and their values.
     */
    public void compareAlgorithms(String path, Simulation simulation, Algorithms algorithms, Statistics statistics,
                                  Parameters parameters) {
        try {
            File comparison = new File(path);
            this.out = new PrintStream(new FileOutputStream(comparison));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        out.println(new Date());

        // Only consider the algorithms for the given data type. Mixed data types can go either way.
        // MGM algorithms won't run on continuous data or discrete data.
        List<Algorithm> _algorithms = new ArrayList<>();

        for (Algorithm algorithm : algorithms.getAlgorithms()) {
            if (algorithm.getDataType() == simulation.getDataType(parameters)
                    || algorithm.getDataType() == DataType.Mixed) {
                _algorithms.add(algorithm);
            } else {
                System.out.println("Data type mismatch for " + algorithm.getDescription() + "; skipping.");
            }
        }

        double[][][][] allStats = calcStats(_algorithms, statistics, parameters, simulation);

        if (allStats != null) {
            out.println();
            out.println("Statistics:");
            out.println();

            for (Statistic stat : statistics.getStatistics()) {
                out.println(stat.getAbbreviation() + " = " + stat.getDescription());
            }
        }

        out.println();
        out.println("Parameters:");
        out.println(parameters);
        out.println();
        out.println("Simulation:");
        out.println();
        out.println(simulation.getDescription());
        out.println();

        if (allStats != null) {
            int numTables = allStats.length;
            int numAlgorithms = allStats[0].length;
            int numStats = allStats[0][0].length - 1;

            double[][][] statTables = calcStatTables(allStats, Mode.Average, numTables, numAlgorithms, numStats);
            double[] utilities = calcUtilities(statistics, numAlgorithms, numStats, statTables[0]);

            // Add utilities to table as the last column.
            for (int u = 0; u < numTables; u++) {
                for (int t1 = 0; t1 < numAlgorithms; t1++) {
                    statTables[u][t1][numStats] = utilities[t1];
                }
            }

            int[] newOrder = sort(_algorithms, utilities);

            out.println("Algorithms (sorted high to low by W for average statistics):");
            out.println();

            for (int t = 0; t < _algorithms.size(); t++) {
                out.println((t + 1) + ". " + _algorithms.get(newOrder[t]).getDescription());
            }

            out.println();
            out.println("Weighting of statistics:\n");
            out.println("W = ");

            for (Statistic stat : statistics.getStatistics()) {
                String statName = stat.getAbbreviation();
                double weight = statistics.getWeight(stat);
                if (weight != 0.0) {
                    out.println("    " + weight + " * Utility(" + statName + ")");
                }
            }

            out.println();


            // Add utilities to table as the last column.

            for (int u = 0; u < numTables; u++) {
                for (int t = 0; t < numAlgorithms; t++) {
                    statTables[u][t][numStats] = utilities[t];
                }
            }

            printStats(statTables, statistics, Mode.Average, newOrder);

            statTables = calcStatTables(allStats, Mode.StandardDeviation, numTables, numAlgorithms, numStats);

            printStats(statTables, statistics, Mode.StandardDeviation, newOrder);

            statTables = calcStatTables(allStats, Mode.WorstCase, numTables, numAlgorithms, numStats);

            // Add utilities to table as the last column.
            for (int u = 0; u < numTables; u++) {
                for (int t1 = 0; t1 < numAlgorithms; t1++) {
                    statTables[u][t1][numStats] = utilities[t1];
                }
            }

            printStats(statTables, statistics, Mode.WorstCase, newOrder);
        }

        out.close();
    }

    /**
     * Saves simulation data.
     * @param path The path to the directory where the simulation data should be saved.
     * @param simulation The simulate used to generate the graphs and data.
     * @param parameters The parameters to be used in the simulation.
     */
    public void saveDataSetAndGraphs(String path, Simulation simulation, Parameters parameters) {
        try {
            File dir = new File(path);
            dir.mkdirs();
            dir.delete();
            dir.mkdirs();

            new File(dir, "data").mkdir();
            new File(dir, "graph").mkdir();

            for (int i = 0; i < simulation.getNumDataSets(); i++) {
                File file = new File(dir + "/data/data." + (i + 1));
                Writer out = new FileWriter(file);
                DataSet dataSet = simulation.getDataSet(i);
                DataWriter.writeRectangularData(dataSet, out, '\t');
                out.close();

                File file2 = new File(dir + "/graph/graph." + (i + 1));
                GraphUtils.saveGraph(simulation.getTrueGraph(i), file2, false);
            }

            PrintStream out = new PrintStream(new FileOutputStream(new File(dir, "parameters.txt")));
            out.println(simulation.getDescription());
            out.println();
            out.println(parameters);
            out.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    private double[][][][] calcStats(List<Algorithm> algorithms, Statistics statistics,
                                     Parameters parameters, Simulation simulation) {
        int numGraphTypes = 4;

        graphTypeUsed = new boolean[4];
        int numRuns = parameters.getInt("numRuns");

        double[][][][] allStats = new double[4][algorithms.size()][statistics.size() + 1][numRuns];

        boolean didAnalysis = false;

        for (int i = 0; i < numRuns; i++) {
            System.out.println();
            System.out.println("Run " + (i + 1));
            System.out.println();

            DataSet data = simulation.getDataSet(i);
            Graph trueGraph = simulation.getTrueGraph(i);

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

                if (trueGraph == null && simulation instanceof SimulationPath) {
                    printGraph(((SimulationPath) simulation).getPath(), out, i, algorithms.get(t), parameters);
                } else {
                    printGraph(null, out, i, algorithms.get(t), parameters);
                }

                long stop = System.currentTimeMillis();

                long elapsed = stop - start;

                if (trueGraph != null) {
                    out = GraphUtils.replaceNodes(out, trueGraph.getNodes());
                }

                Graph[] est = new Graph[numGraphTypes];

                Graph comparisonGraph = trueGraph == null ? null : algorithms.get(t).getComparisonGraph(trueGraph);

                if (trueGraph != null && out != null) {
                    trueGraph = GraphUtils.replaceNodes(trueGraph, out.getNodes());
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

                        int j = -1;

                        for (Statistic _stat : statistics.getStatistics()) {
                            j++;

                            double stat;

                            if (_stat instanceof ElapsedTimeStat) {
                                stat = elapsed / 1000.0;
                            } else {
                                stat = _stat.getValue(truth[u], est[u]);
                            }

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

    private void printStats(double[][][] statTables, Statistics statistics,
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

            Iterator<Statistic> iterator = statistics.getStatistics().iterator();

            for (int statIndex = 0; statIndex < numStats; statIndex++) {
                String statLabel = iterator.next().getAbbreviation();
                table.setToken(0, statIndex + 1, statLabel);
            }

            table.setToken(0, numStats + 1, "W");

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

    private double[] calcUtilities(Statistics statistics, int numAlgorithms, int numStats,
                                   double[][] stats) {
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

            Iterator it2 = statistics.getStatistics().iterator();
            int count = 0;

            while (it2.hasNext()) {
                Statistic stat = (Statistic) it2.next();
                j++;

                double weight = statistics.getWeight(stat);

                if (weight != 0.0) {
                    double _stat = stats[t][j];
                    double utility;

                    if (stat.getAbbreviation().equals("E") || stat.getAbbreviation().equals("SHD")) {
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

}




