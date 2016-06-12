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

                if (node2 instanceof ContinuousVariable &&
                        node1 instanceof DiscreteVariable) {
                    newGraph.addEdge(edge);
                }
            }

            return newGraph;
        }
    }

    public void testBestAlgorithms(Map<String, Number> _parameters, Map<String, String> stats,
                                   List<Algorithm> algorithms, Simulation simulation, PrintStream out) {
        Map<String, Number> parameters = new LinkedHashMap<>();
        parameters.put("numMeasures", 100);
        parameters.put("numEdges", 100);
        parameters.put("numLatents", 0);
        parameters.put("maxDegree", 10);
        parameters.put("maxIndegree", 10);
        parameters.put("maxOutdegree", 10);
        parameters.put("connected", 0);
        parameters.put("sampleSize", 1000);
        parameters.put("numRuns", 5);
        parameters.put("alpha", 0.001);
        parameters.put("penaltyDiscount", 4);

        parameters.put("percentDiscreteForMixedSimulation", 50);
        parameters.put("ofInterestCutoff", 0.05);

        parameters.putAll(_parameters);

        this.out = out;
        double[][][] allRet = printStats(algorithms, stats, parameters, simulation);


        System.out.println();
        out.println("Algorithms:");
        out.println();

        for (int i = 0; i < algorithms.size(); i++) {
            out.println((i + 1) + ". " + algorithms.get(i).getDescription());
        }

        out.println();
        out.println("Statistics:");
        out.println();

        for (String stat : stats.keySet()) {
            out.println(stat + " = " + stats.get(stat));
        }

        out.println();
        out.println("Parameters:");
        out.println();

        for (String param : parameters.keySet()) {
            out.println(param + " = " + parameters.get(param));
        }

        out.println();
        out.println("Simulation:");
        out.println();
        out.println(simulation);
        out.println();

        printBestStats(allRet, algorithms, stats, parameters, simulation);

        out.close();
    }

    private double[][][] printStats(List<Algorithm> algorithms, Map<String, String> stats,
                                    Map<String, Number> parameters, Simulation simulation) {
        int numGraphTypes = 4;

        graphTypeUsed = new boolean[4];

        NumberFormat nf = new DecimalFormat("0.00");

        double[][][] statSums = new double[algorithms.size()][stats.size()][4];
        int[][][] countStat = new int[algorithms.size()][stats.size()][4];

        for (int i = 0; i < parameters.get("numRuns").intValue(); i++) {
            System.out.println();
            System.out.println("Run " + (i + 1));
            System.out.println();

            simulation.simulate(parameters);
            Graph dag = simulation.getDag();
            DataSet data = simulation.getData();

            boolean isMixed = data.isMixed();

            for (int t = 0; t < algorithms.size(); t++) {
                System.out.println((t + 1) + ". " + algorithms.get(t).getDescription());

                long start = System.currentTimeMillis();

                Graph out = algorithms.get(t).search(data, parameters);

                long stop = System.currentTimeMillis();

                long elapsed = stop - start;

                System.out.println(algorithms.get(t).getComparisonGraph(dag));
                System.out.println(out);

                out = GraphUtils.replaceNodes(out, dag.getNodes());

                Graph[] est = new Graph[numGraphTypes];

                Graph comparisonGraph = algorithms.get(t).getComparisonGraph(dag);
                dag = GraphUtils.replaceNodes(dag, out.getNodes());

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

                if (isMixed) {
                    truth[1] = getSubgraph(comparisonGraph, true, true, data);
                    truth[2] = getSubgraph(comparisonGraph, true, false, data);
                    truth[3] = getSubgraph(comparisonGraph, false, false, data);
                }

                for (int u = 0; u < numGraphTypes; u++) {
                    if (!graphTypeUsed[u]) continue;

                    EdgeStats edgeStats = new EdgeStats(est[u], truth[u], elapsed).invoke();

                    int j = -1;

                    for (String statName : stats.keySet()) {
                        j++;
                        double stat = edgeStats.getStat(statName);

                        if (!Double.isNaN(stat)) {
                            statSums[t][j][u] += stat;
                            countStat[t][j][u]++;
                        }
                    }
                }
            }
        }

        double[][][] avgStat = new double[algorithms.size()][stats.size()][numGraphTypes];

        for (int t = 0; t < algorithms.size(); t++) {
            for (int u = 0; u < 4; u++) {
                for (int j = 0; j < stats.size(); j++) {
                    avgStat[t][j][u] = statSums[t][j][u] / (double) countStat[t][j][u];
                }
            }
        }

        return avgStat;
    }

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

    private void printBestStats(double[][][] allAllRet, List<Algorithm> algorithms, Map<String, String> stats,
                                Map<String, Number> parameters, Simulation simulation) {
        class Pair {
            private int algorithm;
            private double stat;

            public Pair(int algorithm, double stat) {
                this.algorithm = algorithm;
                this.stat = stat;
            }

            public int getAlgorithm() {
                return algorithm;
            }

            public double getStat() {
                return stat;
            }
        }

        out.println("And the winners are... !");

        if (simulation.isMixed()) {
            for (int u = 0; u < 4; u++) {
                out.println();

                out.println("====== " + getHeader(u) +
                        " (listing high to low, top to top - " +
                        parameters.get("ofInterestCutoff").doubleValue() + ")");

                out.println();
                Set<String> keySet = stats.keySet();
                int statIndex = -1;

                for (String statName : keySet) {
                    String maxAlg;
                    statIndex++;

                    List<Pair> algStats = new ArrayList<>();

                    for (int t = 0; t < algorithms.size(); t++) {
                        double stat = allAllRet[t][statIndex][u];
                        if (!Double.isNaN(stat)) {
                            algStats.add(new Pair(t + 1, stat));
                        }
                    }

                    if (algStats.isEmpty()) {
                        maxAlg = "-";
                    } else {
                        Collections.sort(algStats, new Comparator<Pair>() {

                            @Override
                            public int compare(Pair o1, Pair o2) {
                                return -Double.compare(o1.getStat(), o2.getStat());
                            }
                        });

                        double maxStat = algStats.get(0).getStat();
                        maxAlg = "" + algStats.get(0).getAlgorithm()/*.getName()*/;
                        double ofInterest = maxStat - parameters.get("ofInterestCutoff").doubleValue();

                        for (int i = 1; i < algStats.size(); i++) {
                            if (algStats.get(i).getStat() == algStats.get(i - 1).getStat()) {
                                maxAlg += "==" + algStats.get(i).getAlgorithm()/*.getName()*/;
                            } else if (algStats.get(i).getStat() >= ofInterest) {
                                maxAlg += "," + algStats.get(i).getAlgorithm()/*.getName()*/;
                            } else {
                                break;
                            }
                        }
                    }

                    out.println(statName + ": " + maxAlg);
                }
            }
        } else {
            int u = 0;
            out.println();

            out.println("====== " + getHeader(u) +
                    " (listing high to low, top to top - " +
                    parameters.get("ofInterestCutoff").doubleValue() + ")");

            out.println();
            Set<String> keySet = stats.keySet();
            int statIndex = -1;

            for (String statName : keySet) {
                String maxAlg;
                statIndex++;

                List<Pair> algStats = new ArrayList<>();

                for (int t = 0; t < algorithms.size(); t++) {
                    double stat = allAllRet[t][statIndex][u];
                    if (!Double.isNaN(stat)) {
                        algStats.add(new Pair(t + 1, stat));
                    }
                }

                if (algStats.isEmpty()) {
                    maxAlg = "-";
                } else {
                    Collections.sort(algStats, new Comparator<Pair>() {

                        @Override
                        public int compare(Pair o1, Pair o2) {
                            return -Double.compare(o1.getStat(), o2.getStat());
                        }
                    });

                    double maxStat = algStats.get(0).getStat();
                    maxAlg = "" + algStats.get(0).getAlgorithm()/*.getName()*/;
                    double ofInterest = maxStat - parameters.get("ofInterestCutoff").doubleValue();

                    for (int i = 1; i < algStats.size(); i++) {
                        if (algStats.get(i).getStat() == algStats.get(i - 1).getStat()) {
                            maxAlg += "==" + algStats.get(i).getAlgorithm();
                        } else if (algStats.get(i).getStat() >= ofInterest) {
                            maxAlg += "," + algStats.get(i).getAlgorithm();
                        } else {
                            break;
                        }
                    }
                }

                out.println(statName + ": " + maxAlg);
            }
        }


        NumberFormat nf = new DecimalFormat("0.00");

        out.println();
        out.println("DETAILS:");
        out.println();
        out.println("AVERAGE STATISTICS");

        for (int u = 0; u < 4; u++) {
            if (!graphTypeUsed[u]) continue;
            for (int t = 0; t < algorithms.size(); t++) {
                String algorithm = "" + (t + 1);
                out.println();

                out.println(getHeader(u) + " Algorithm = " + algorithm);
                out.println();
                Set<String> keySet = stats.keySet();
                Iterator<String> iterator = keySet.iterator();

                for (int statIndex = 0; statIndex < stats.size(); statIndex++) {
                    String statLabel = iterator.next();
                    double stat = allAllRet[t][statIndex][u];
                    out.println("\tAverage " + statLabel + " = " + nf.format(stat));
                }
            }
        }

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
                default:
                    throw new IllegalArgumentException();
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

            int allEdges = truth.getNumNodes() * (truth.getNumNodes() - 1);

            int adjTn = allEdges / 2 - (adjFn + adjFp + adjTp);
            int arrowsTn = allEdges - (arrowsFn + arrowsFp + arrowsTp);

            adjPrecision = adjTp / (double) (adjTp + adjFp);
            adjRecall = adjTp / (double) (adjTp + adjFn);

            arrowPrecision = arrowsTp / (double) (arrowsTp + arrowsFp);
            arrowRecall = arrowsTp / (double) (arrowsTp + arrowsFn);

            mcAdj = (adjTp * adjTn - adjFp * adjFn) /
                    Math.sqrt((adjTp + adjFp) * (adjTp + adjFn) * (adjTn + adjFp) * (adjTn + adjFn));
            mcOr = (arrowsTp * arrowsTn - arrowsFp * arrowsFn) /
                    Math.sqrt((arrowsTp + arrowsFp) * (arrowsTp + arrowsFn) *
                            (arrowsTn + arrowsFp) * (arrowsTn + arrowsFn));
            f1Adj = 2 * (adjPrecision * adjRecall) / (adjPrecision + adjRecall);
            f1Arrows = 2 * (arrowPrecision * arrowRecall) / (arrowPrecision + arrowRecall);

            shd = comparison.getShd();

            return this;
        }
    }
}




