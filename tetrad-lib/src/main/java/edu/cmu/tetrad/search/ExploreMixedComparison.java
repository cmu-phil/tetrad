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

package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.sem.GeneralizedSemIm;
import edu.cmu.tetrad.sem.GeneralizedSemPm;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.pitt.csb.mgm.MGM;
import edu.pitt.csb.mgm.MixedUtils;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.*;

/**
 * @author Joseph Ramsey
 */
public class ExploreMixedComparison {
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

//    public void testAjData() {
//        double penalty = 4;
//
//        try {
//
//            for (int i = 0; i < 50; i++) {
//                File dataPath = new File("/Users/jdramsey/Documents/LAB_NOTEBOOK.2012.04.20/2016.05.25/" +
//                        "Simulated_data_for_Madelyn/simulation/data/DAG_" + i + "_data.txt");
//                DataReader reader = new DataReader();
//                DataSet Dk = reader.parseTabular(dataPath);
//
//                File graphPath = new File("/Users/jdramsey/Documents/LAB_NOTEBOOK.2012.04.20/2016.05.25/" +
//                        "Simulated_data_for_Madelyn/simulation/networks/DAG_" + i + "_graph.txt");
//
//                Graph dag = GraphUtils.loadGraphTxt(graphPath);
//
//                long start = System.currentTimeMillis();
//
////            Graph pattern = searchSemFgs(Dk);
////            Graph pattern = searchBdeuFgs(Dk, k);
////                Graph pattern = searchMixedFgs1(Dk, penalty);
//
//
//                Map<String, Number> parameters = new LinkedHashMap<>();
//                parameters.put("alpha", 0.001);
//                parameters.put("penaltyDiscount", 4);
//                parameters.put("mgmParam1", 0.1);
//                parameters.put("mgmParam2", 0.1);
//                parameters.put("mgmParam3", 0.1);
//                parameters.put("OfInterestCutoff", 0.05);
//
//                Graph pattern = new MixedFgs().search(Dk, parameters);
//
//                long stop = System.currentTimeMillis();
//
//                long elapsed = stop - start;
//                long elapsedSeconds = elapsed / 1000;
//
//                Graph truePattern = SearchGraphUtils.patternForDag(dag);
//
//                GraphUtils.GraphComparison comparison = SearchGraphUtils.getGraphComparison3(pattern, truePattern, System.out);
//                NumberFormat nf = new DecimalFormat("0.00");
//
//                System.out.println(i +
//                        "\t" + nf.format(comparison.getAdjPrec()) +
//                        "\t" + nf.format(comparison.getAdjRec()) +
//                        "\t" + nf.format(comparison.getAhdPrec()) +
//                        "\t" + nf.format(comparison.getAhdRec()) +
//                        "\t" + elapsedSeconds);
//            }
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//    }


    interface Search {
        Graph search(DataSet dataSet, Map<String, Number> parameters);

        String getName();
    }


    static class SemFgs implements Search {
        public Graph search(DataSet Dk, Map<String, Number> parameters) {
            Dk = DataUtils.convertNumericalDiscreteToContinuous(Dk);
            SemBicScore score = new SemBicScore(new CovarianceMatrixOnTheFly(Dk));
            score.setPenaltyDiscount(parameters.get("penaltyDiscount").doubleValue());
            Fgs fgs = new Fgs(score);
            Graph p = fgs.search();
            return convertBack(Dk, p);
        }

        public String getName() {
            return "SemFgs";
        }

        private Graph convertBack(DataSet Dk, Graph p) {
            Graph p2 = new EdgeListGraph(Dk.getVariables());

            for (int i = 0; i < p.getNodes().size(); i++) {
                for (int j = i + 1; j < p.getNodes().size(); j++) {
                    Node v1 = p.getNodes().get(i);
                    Node v2 = p.getNodes().get(j);

                    Edge e = p.getEdge(v1, v2);

                    if (e != null) {
                        Node w1 = Dk.getVariable(e.getNode1().getName());
                        Node w2 = Dk.getVariable(e.getNode2().getName());

                        Edge e2 = new Edge(w1, w2, e.getEndpoint1(), e.getEndpoint2());

                        p2.addEdge(e2);
                    }
                }
            }
            return p2;
        }
    }

    static class BdeuFgs implements Search {
        public Graph search(DataSet Dk, Map<String, Number> parameters) {
            Discretizer discretizer = new Discretizer(Dk);
            List<Node> nodes = Dk.getVariables();

            for (Node node : nodes) {
                if (node instanceof ContinuousVariable) {
                    discretizer.equalIntervals(node, parameters.get("numCategories").intValue());
                }
            }

            Dk = discretizer.discretize();

            BDeuScore score = new BDeuScore(Dk);
            score.setSamplePrior(1.0);
            score.setStructurePrior(1.0);
            Fgs fgs = new Fgs(score);
            Graph p = fgs.search();
            return convertBack(Dk, p);
        }

        public String getName() {
            return "BdeuFgs";
        }

        private Graph convertBack(DataSet Dk, Graph p) {
            Graph p2 = new EdgeListGraph(Dk.getVariables());

            for (int i = 0; i < p.getNodes().size(); i++) {
                for (int j = i + 1; j < p.getNodes().size(); j++) {
                    Node v1 = p.getNodes().get(i);
                    Node v2 = p.getNodes().get(j);

                    Edge e = p.getEdge(v1, v2);

                    if (e != null) {
                        Node w1 = Dk.getVariable(e.getNode1().getName());
                        Node w2 = Dk.getVariable(e.getNode2().getName());

                        Edge e2 = new Edge(w1, w2, e.getEndpoint1(), e.getEndpoint2());

                        p2.addEdge(e2);
                    }
                }
            }
            return p2;
        }
    }

    static class MixedFgs implements Search {
        public Graph search(DataSet dataSet, Map<String, Number> parameters) {
            MixedBicScore score = new MixedBicScore(dataSet);
            score.setPenaltyDiscount(parameters.get("penaltyDiscount").doubleValue());
            Fgs fgs = new Fgs(score);
            return fgs.search();
        }

        public String getName() {
            return "MixedFgs";
        }
    }

    static class WFGS implements Search {
        public Graph search(DataSet dataSet, Map<String, Number> parameters) {
            WFgs fgs = new WFgs(dataSet);
            fgs.setPenaltyDiscount(parameters.get("penaltyDiscount").doubleValue());
            return fgs.search();
        }

        public String getName() {
            return "WFGS";
        }
    }

    static class MixedPc implements Search {
        public Graph search(DataSet dataSet, Map<String, Number> parameters) {
            IndependenceTest test = new IndTestMixedLrt(dataSet, parameters.get("alpha").doubleValue());
            Pc pc = new Pc(test);
            return pc.search();
        }

        public String getName() {
            return "AJMixedPc";
        }
    }

    static class MixedPcs implements Search {
        public Graph search(DataSet dataSet, Map<String, Number> parameters) {
            IndependenceTest test = new IndTestMixedLrt(dataSet, parameters.get("alpha").doubleValue());
            PcStable pc = new PcStable(test);
            return pc.search();
        }

        public String getName() {
            return "AJPcs";
        }
    }

    static class MixedCpc implements Search {
        public Graph search(DataSet dataSet, Map<String, Number> parameters) {
            IndependenceTest test = new IndTestMixedLrt(dataSet, parameters.get("alpha").doubleValue());
            Cpc pc = new Cpc(test);
            return pc.search();
        }

        public String getName() {
            return "AJMixedCpc";
        }
    }

    static class MGMFgs implements Search {
        public Graph search(DataSet ds, Map<String, Number> parameters) {
            MGM m = new MGM(ds, new double[]{
                    parameters.get("mgmParam1").doubleValue(),
                    parameters.get("mgmParam2").doubleValue(),
                    parameters.get("mgmParam3").doubleValue()
            });
            Graph gm = m.search();
            DataSet dataSet = MixedUtils.makeContinuousData(ds);
            SemBicScore2 score = new SemBicScore2(new CovarianceMatrixOnTheFly(dataSet));
            score.setPenaltyDiscount(parameters.get("penaltyDiscount").doubleValue());
            Fgs fg = new Fgs(score);
            fg.setBoundGraph(gm);
            fg.setVerbose(false);
            Graph p = fg.search();
            return convertBack(ds, p);
        }

        public String getName() {
            return "MGMFgs";
        }

        private Graph convertBack(DataSet Dk, Graph p) {
            Graph p2 = new EdgeListGraph(Dk.getVariables());

            for (int i = 0; i < p.getNodes().size(); i++) {
                for (int j = i + 1; j < p.getNodes().size(); j++) {
                    Node v1 = p.getNodes().get(i);
                    Node v2 = p.getNodes().get(j);

                    Edge e = p.getEdge(v1, v2);

                    if (e != null) {
                        Node w1 = Dk.getVariable(e.getNode1().getName());
                        Node w2 = Dk.getVariable(e.getNode2().getName());

                        Edge e2 = new Edge(w1, w2, e.getEndpoint1(), e.getEndpoint2());

                        p2.addEdge(e2);
                    }
                }
            }
            return p2;
        }
    }

    static class MGMPc implements Search {
        public Graph search(DataSet ds, Map<String, Number> parameters) {
            MGM m = new MGM(ds, new double[]{
                    parameters.get("mgmParam1").doubleValue(),
                    parameters.get("mgmParam2").doubleValue(),
                    parameters.get("mgmParam3").doubleValue()
            });
            Graph gm = m.search();
            IndependenceTest indTest = new IndTestMixedLrt(ds, parameters.get("alpha").doubleValue());
            PcStable pcs = new PcStable(indTest);
            pcs.setDepth(-1);
            pcs.setInitialGraph(gm);
            pcs.setVerbose(false);
            return pcs.search();
        }

        public String getName() {
            return "MGMPc";
        }
    }

    public void testBestAlgorithms(Map<String, Number> parameters, Map<String, String> stats,
                                   List<Search> algorithms, Simulation simulation) {


        int numCategoryLevels = parameters.get("maxCategoriesForSearch").intValue()
                - parameters.get("minCategoriesForSearch").intValue() + 1;
        double[][][][] allAllRet = new double[numCategoryLevels][][][];
        int latentIndex = -1;


        for (int numCategories = parameters.get("minCategoriesForSearch").intValue();
             numCategories <= parameters.get("maxCategoriesForSearch").intValue();
             numCategories++) {
            parameters.put("numCategories", numCategories);
            latentIndex++;

            System.out.println();

            for (String param : parameters.keySet()) {
                System.out.println(param + " = " + parameters.get(param));
            }

            double[][][] allRet = printStats(algorithms, stats, parameters, simulation);


            allAllRet[latentIndex] = allRet;
        }

        System.out.println();
        System.out.println("=======");
        System.out.println();
        System.out.println("Algorithms with max - " + parameters.get("ofInterestCutoff").doubleValue()
                + " <= stat <= max.");
        System.out.println();
        for (String stat : stats.keySet()) {
            System.out.println(stat + " = " + stats.get(stat));
        }

        printBestStats(allAllRet, algorithms, stats, parameters);
    }

    private double[][][] printStats(List<Search> algorithms, Map<String, String> stats,
                                    Map<String, Number> parameters, Simulation simulation) {
        NumberFormat nf = new DecimalFormat("0.00");

        double[][][] statSums = new double[algorithms.size()][stats.size()][4];
        int[][][] countStat = new int[algorithms.size()][stats.size()][4];

        for (int i = 0; i < parameters.get("numRuns").intValue(); i++) {
            simulation.simulate();
            Graph dag = simulation.getDag();
            DataSet data = simulation.getData();

            for (int t = 0; t < algorithms.size(); t++) {
                long start = System.currentTimeMillis();

                Graph out = algorithms.get(t).search(data, parameters);

                long stop = System.currentTimeMillis();

                long elapsed = stop - start;

                out = GraphUtils.replaceNodes(out, dag.getNodes());

                Graph[] est = new Graph[4];

                est[0] = out;
                est[1] = getSubgraph(out, true, true, data);
                est[2] = getSubgraph(out, true, false, data);
                est[3] = getSubgraph(out, false, false, data);

                Graph[] truth = new Graph[4];

                truth[0] = dag;
                truth[1] = getSubgraph(dag, true, true, data);
                truth[2] = getSubgraph(dag, true, false, data);
                truth[3] = getSubgraph(dag, false, false, data);

                for (int u = 0; u < 4; u++) {
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

        double[][][] avgStat = new double[algorithms.size()][stats.size()][4];

        for (int t = 0; t < algorithms.size(); t++) {
            for (int u = 0; u < 4; u++) {
                for (int j = 0; j < stats.size(); j++) {
                    avgStat[t][j][u] = statSums[t][j][u] / (double) countStat[t][j][u];
                }
            }
        }

        System.out.println();

        for (int t = 0; t < algorithms.size(); t++) {
            for (int u = 0; u < 4; u++) {
                String header = getHeader(u);
                System.out.println("\n" + header + "\n");
                int j = -1;

                for (String statName : stats.keySet()) {
                    j++;
                    String algorithm = algorithms.get(t).getName();
                    System.out.println(algorithm + " " + statName + " " + nf.format(avgStat[t][j][u]));
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

    private void printBestStats(double[][][][] allAllRet, List<Search> algorithms, Map<String, String> stats,
                                Map<String, Number> parameters) {
        class Pair {
            private Search algorithm;
            private double stat;

            public Pair(Search algorithm, double stat) {
                this.algorithm = algorithm;
                this.stat = stat;
            }

            public Search getAlgorithm() {
                return algorithm;
            }

            public double getStat() {
                return stat;
            }
        }

        System.out.println();
        System.out.println("And the winners are... !");

        for (int u = 0; u < 4; u++) {
            for (int numCategories = parameters.get("minCategoriesForSearch").intValue();
                 numCategories <= parameters.get("maxCategoriesForSearch").intValue(); numCategories++) {
                parameters.put("numCategories", numCategories);

                System.out.println();
                System.out.println("====== " + getHeader(u) + " " + numCategories +
                        " categories (listing high to low, top to top - 0.05)");
                System.out.println();
                Set<String> keySet = stats.keySet();
                int statIndex = -1;

                for (String statName : keySet) {
                    String maxAlg;
                    statIndex++;

                    List<Pair> algStats = new ArrayList<>();

                    for (int t = 0; t < algorithms.size(); t++) {
                        double stat = allAllRet[numCategories - parameters.get("minCategoriesForSearch").intValue()]
                                [t][statIndex][u];
                        if (!Double.isNaN(stat)) {
                            algStats.add(new Pair(algorithms.get(t), stat));
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
                        maxAlg = algStats.get(0).getAlgorithm().getName();
                        double ofInterest = maxStat - parameters.get("ofInterestCutoff").doubleValue();

                        for (int i = 1; i < algStats.size(); i++) {
                            if (algStats.get(i).getStat() == algStats.get(i - 1).getStat()) {
                                maxAlg += "==" + algStats.get(i).getAlgorithm().getName();
                            } else if (algStats.get(i).getStat() >= ofInterest) {
                                maxAlg += "," + algStats.get(i).getAlgorithm().getName();
                            }
                        }
                    }

                    System.out.println(statName + ": " + maxAlg);
                }
            }
        }

        NumberFormat nf = new DecimalFormat("0.00");

        System.out.println();
        System.out.println("DETAILS:");
        System.out.println();
        System.out.println("AVERAGE STATISTICS");

        for (int u = 0; u < 4; u++) {
            for (int numCategories = parameters.get("minCategoriesForSearch").intValue();
                 numCategories <= parameters.get("maxCategoriesForSearch").intValue(); numCategories++) {
                parameters.put("numCategories", numCategories);

                for (int t = 0; t < algorithms.size(); t++) {
                    String algorithm = algorithms.get(t).getName();

                    System.out.println();
                    System.out.println(getHeader(u) + " # categories = " + numCategories + " Algorithm = " + algorithm);
                    System.out.println();
                    Set<String> keySet = stats.keySet();
                    Iterator<String> iterator = keySet.iterator();

                    for (int statIndex = 0; statIndex < allAllRet[numCategories - 2][0].length; statIndex++) {
                        String statLabel = iterator.next();
                        double stat = allAllRet[numCategories - 2][t][statIndex][u];
                        System.out.println("\tAverage " + statLabel + " = " + nf.format(stat));
                    }
                }
            }
        }
    }

    private class EdgeStats {
        private Graph graph;
        private Graph graph1;
        private double adjPrecision;
        private double adjRecall;
        private double arrowPrecision;
        private double arrowRecall;
        private double mcAdj;
        private double mcOr;
        private double f1Adj;
        private double f1Arrows;
        private double elapsed;

        public EdgeStats(Graph graph, Graph graph1, long elapsed) {
            this.graph = graph;
            this.graph1 = graph1;
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
                case "E":

                    // Needs to sort high to low.
                    return -elapsed;
                default:
                    throw new IllegalArgumentException();
            }
        }

        public EdgeStats invoke() {
            int adjTp = 0;
            int adjFp = 0;
            int adjTn;
            int adjFn = 0;
            int arrowsTp = 0;
            int arrowsFp = 0;
            int arrowsTn;
            int arrowsFn = 0;

            for (Edge edge : graph.getEdges()) {
                if (graph1.isAdjacentTo(edge.getNode1(), edge.getNode2())) {
                    adjTp++;
                } else {
                    adjFp++;
                }

                if (edge.isDirected()) {
                    Edge _edge = graph1.getEdge(edge.getNode1(), edge.getNode2());

                    if (edge != null && edge.equals(_edge)) {
                        arrowsTp++;
                    } else {
                        arrowsFp++;
                    }
                }
            }

            List<Node> nodes1 = graph1.getNodes();

            for (int w = 0; w < nodes1.size(); w++) {
                for (int s = w + 1; w < nodes1.size(); w++) {
                    Node W = nodes1.get(w);
                    Node S = nodes1.get(s);

                    if (graph1.isAdjacentTo(W, S)) {
                        if (!graph.isAdjacentTo(W, S)) {
                            adjFn++;
                        }

                        Edge e1 = graph1.getEdge(W, S);
                        Edge e2 = graph.getEdge(W, S);

                        if (!(e2 != null && e2.equals(e1))) {
                            arrowsFn++;
                        }
                    }

                    Edge e1 = graph1.getEdge(W, S);
                    Edge e2 = graph.getEdge(W, S);

                    if (!(e1 != null && e2 == null) || (e1 != null && e2 != null && !e1.equals(e2))) {
                        arrowsFn++;
                    }
                }
            }

            int allEdges = graph1.getNumNodes() * (graph1.getNumNodes() - 1);

            adjTn = allEdges / 2 - (adjFn + adjFp + adjTp);
            arrowsTn = allEdges - (arrowsFn + arrowsFp + arrowsTp);

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
            return this;
        }
    }

    interface Simulation {
        void simulate();
        Graph getDag();
        DataSet getData();
    }

    public static class LeeHastieData implements Simulation {
        private Graph dag;
        private Map<String, Number> parameters;
        private Graph graph;
        private DataSet dataSet;

        public LeeHastieData(Map<String, Number> parameters) {
            this.parameters = parameters;
        }

        public void simulate() {
            this.dag = GraphUtils.randomGraphRandomForwardEdges(
                    parameters.get("numNodes").intValue(), 0, parameters.get("numEdges").intValue(),
                    10, 10, 10, false);

            HashMap<String, Integer> nd = new HashMap<>();

            List<Node> nodes = dag.getNodes();

            Collections.shuffle(nodes);

            for (int i = 0; i < nodes.size(); i++) {
                if (i < nodes.size() / 2) {
                    nd.put(nodes.get(i).getName(), parameters.get("numCategories").intValue());
                } else {
                    nd.put(nodes.get(i).getName(), 0);
                }
            }

            Graph graph = MixedUtils.makeMixedGraph(dag, nd);

            GeneralizedSemPm pm = MixedUtils.GaussianCategoricalPm(graph, "Split(-1.5,-.5,.5,1.5)");
            GeneralizedSemIm im = MixedUtils.GaussianCategoricalIm(pm);

            DataSet ds = im.simulateDataAvoidInfinity(parameters.get("sampleSize").intValue(), false);
            this.dataSet = MixedUtils.makeMixedData(ds, nd);
        }

        public Graph getDag() {
            return dag;
        }

        public DataSet getData() {
            return dataSet;
        }
    }

    public static class SemThenDiscretize implements Simulation {
        private Map<String, Number> parameters;
        private Graph graph;
        private DataSet dataSet;

        public SemThenDiscretize(Map<String, Number> parameters) {
            this.parameters = parameters;
        }

        public void simulate() {
            this.graph = GraphUtils.randomGraphRandomForwardEdges(
                    parameters.get("numNodes").intValue(), 0, parameters.get("numEdges").intValue(),
                    10, 10, 10, false);

            SemPm pm = new SemPm(graph);
            SemIm im = new SemIm(pm);
            DataSet continuousData = im.simulateData(parameters.get("sampleSize").intValue(), false);

            List<Node> shuffledNodes = new ArrayList<>(continuousData.getVariables());
            Collections.shuffle(shuffledNodes);

            Discretizer discretizer = new Discretizer(continuousData);

            for (int i = 0; i < shuffledNodes.size() / 2; i++) {
                discretizer.equalCounts(shuffledNodes.get(i), parameters.get("numCategories").intValue());
            }

            this.dataSet = discretizer.discretize();
        }

        public Graph getDag() {
            return graph;
        }

        public DataSet getData() {
            return dataSet;
        }
    }

    public static void main(String... args) {
        Map<String, Number> parameters = new LinkedHashMap<>();
        parameters.put("numNodes", 5);
        parameters.put("numEdges", 5);
        parameters.put("sampleSize", 1000);
        parameters.put("minCategoriesForSearch", 2);
        parameters.put("maxCategoriesForSearch", 5);
        parameters.put("numRuns", 5);
        parameters.put("alpha", 0.001);
        parameters.put("penaltyDiscount", 4);
        parameters.put("mgmParam1", 0.1);
        parameters.put("mgmParam2", 0.1);
        parameters.put("mgmParam3", 0.1);
        parameters.put("ofInterestCutoff", 0.05);
        parameters.put("simulationStyle", 1);

        Map<String, String> stats = new LinkedHashMap<>();
        stats.put("AP", "Adjacency Precision");
        stats.put("AR", "Adjacency Recall");
        stats.put("OP", "Orientation (Arrow) precision");
        stats.put("OR", "Orientation (Arrow) recall");
        stats.put("McAdj", "Matthew's correlation coeffficient for adjacencies");
        stats.put("McOr", "Matthew's correlation coefficient for arrow");
        stats.put("F1Adj", "F1 statistic for adjacencies");
        stats.put("F1Or", "F1 statistic for arrows");
        stats.put("E", "Elapsed time in seconds");

        List<Search> algorithms = new ArrayList<>();
        algorithms.add(new SemFgs());
        algorithms.add(new BdeuFgs());
        algorithms.add(new MixedFgs());
        algorithms.add(new WFGS());
        algorithms.add(new MixedPc());
        algorithms.add(new MixedPcs());
        algorithms.add(new MixedCpc());
        algorithms.add(new MGMFgs());
        algorithms.add(new MGMPc());

//        Simulation simulation = new LeeHastieData(parameters);
        Simulation simulation = new SemThenDiscretize(parameters);

        new ExploreMixedComparison().testBestAlgorithms(parameters, stats, algorithms, simulation);
    }

}




