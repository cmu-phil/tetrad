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
import edu.cmu.tetrad.util.TetradLogger;

import java.util.*;


/**
 * BuildPureClusters is a implementation of the automated clustering and purification methods described on the report
 * "Learning Measurement Models" CMU-CALD-03-100.
 * <p>
 * The output is only the purified model. Future versions may include options to visualize the measurement pattern in
 * the GUI (it shows up in the console window, though.)
 * <p>
 * No background knowledge is allowed yet. Future versions of this algorithm will include it.
 * <p>
 * References:
 * <p>
 * Silva, R.; Scheines, R.; Spirtes, P.; Glymour, C. (2003). "Learning measurement models". Technical report
 * CMU-CALD-03-100, Center for Automated Learning and Discovery, Carnegie Mellon University.
 * <p>
 * Bollen, K. (1990). "Outlier screening and distribution-free test for vanishing tetrads." Sociological Methods and
 * Research 19, 80-92.
 * <p>
 * Wishart, J. (1928). "Sampling errors in the theory of two factors". British Journal of Psychology 19, 180-187.
 * <p>
 * Bron, C. and Kerbosch, J. (1973) "Algorithm 457: Finding all cliques of an undirected graph". Communications of ACM
 * 16, 575-577.
 *
 * @author Ricardo Silva
 */

public final class BuildPureClusters {
    private boolean outputMessage;

    private ICovarianceMatrix covarianceMatrix;
    private int numVariables;

    private TestType sigTestType;
    private TestType purifyTestType;
    private int labels[];
    private boolean scoreTestMode;

    private List<Node> impurePairs;

    /**
     * Color code for the different edges that show up during search
     */
    final int EDGE_NONE = 0;
    final int EDGE_BLACK = 1;
    final int EDGE_GRAY = 2;
    final int EDGE_BLUE = 3;
    final int EDGE_YELLOW = 4;
    final int EDGE_RED = 4;
    final int MAX_PURIFY_TRIALS = 50;
    final int MAX_CLIQUE_TRIALS = 50;

    private TetradTest tetradTest;
    private IndependenceTest independenceTest;
    private DataSet dataSet;

    /**
     * The logger for this class. The config needs to be set.
     */
    private TetradLogger logger = TetradLogger.getInstance();
    private double alpha;
    private boolean verbose;

    //*********************************************************
    // * INITIALIZATION
    // *********************************************************/

    /**
     * Constructor BuildPureClusters
     */
    public BuildPureClusters(ICovarianceMatrix covarianceMatrix, double alpha,
                             TestType sigTestType, TestType purifyTestType) {
        if (covarianceMatrix == null) {
            throw new IllegalArgumentException("Covariance matrix cannot be null.");
        }

//        if (sigTestType == TestType.TETRAD_DELTA) {
//            throw new RuntimeException("Covariance Matrix is not enough to " +
//                    "run Bollen's tetrad test.");
//        }
        this.covarianceMatrix = covarianceMatrix;
        initAlgorithm(alpha, sigTestType, purifyTestType);
    }

    public BuildPureClusters(CovarianceMatrix covarianceMatrix, double alpha,
                             TestType sigTestType, TestType purifyTestType) {
        if (covarianceMatrix == null) {
            throw new IllegalArgumentException("Covariance matrix cannot be null.");
        }

//        if (sigTestType == TestType.TETRAD_DELTA) {
//            throw new RuntimeException(
//                    "Correlation Matrix is not enough to run Bollen's tetrad test.");
//        }
        this.covarianceMatrix = covarianceMatrix;
        initAlgorithm(alpha, sigTestType, purifyTestType);
    }

    public BuildPureClusters(DataSet dataSet, double alpha, TestType sigTestType, TestType purifyTestType) {
        if (dataSet.isContinuous()) {
            this.dataSet = dataSet;
            this.covarianceMatrix = new CovarianceMatrix(dataSet);
            initAlgorithm(alpha, sigTestType, purifyTestType);
        } else if (dataSet.isDiscrete()) {
            throw new IllegalArgumentException("Discrete data is not supported " +
                    "for this search.");

//            this.dataSet = dataSet;
//            sigTestType = TestType.DISCRETE;
//            initAlgorithm(alpha, sigTestType, purifyTestType);
        }
    }

    private void initAlgorithm(double alpha, TestType sigTestType, TestType purifyTestType) {

        // Check for missing values.
        if (getCovarianceMatrix() != null && DataUtils.containsMissingValue(getCovarianceMatrix().getMatrix())) {
            throw new IllegalArgumentException(
                    "Please remove or impute missing values first.");
        }

        this.alpha = alpha;

        this.outputMessage = true;
        this.sigTestType = sigTestType;
        this.purifyTestType = purifyTestType;
        this.scoreTestMode = (this.sigTestType == TestType.DISCRETE ||
                this.sigTestType == TestType.GAUSSIAN_FACTOR);

        if (sigTestType == TestType.DISCRETE) {
            this.purifyTestType = TestType.NONE;
            numVariables = dataSet.getNumColumns();
            independenceTest = new IndTestGSquare(dataSet, alpha);
            tetradTest = new DiscreteTetradTest(dataSet, alpha);
        } else {
            numVariables = getCovarianceMatrix().getSize();
            independenceTest = new IndTestFisherZ(getCovarianceMatrix(), .1);
            TestType type;

            if (sigTestType == TestType.TETRAD_WISHART || sigTestType == TestType.TETRAD_DELTA
                    || sigTestType == TestType.GAUSSIAN_FACTOR) {
                type = sigTestType;
            } else {
                throw new IllegalArgumentException("Expecting TETRAD_WISHART, TETRAD_DELTA, or GAUSSIAN FACTOR " +
                        sigTestType);
            }

//            if (sigTestType == TestType.TETRAD_DELTA || purifyTestType == TestType.TETRAD_DELTA) {
//                tetradTest = new ContinuousTetradTest(dataSet, type, alpha);
//            } else {
//                tetradTest = new ContinuousTetradTest(getCovarianceMatrix(), type, alpha);
//            }

            if (dataSet != null) {
                tetradTest = new ContinuousTetradTest(dataSet, type, alpha);
            } else {
                tetradTest = new ContinuousTetradTest(getCovarianceMatrix(), type, alpha);
            }
        }
        impurePairs = new ArrayList<Node>();
        labels = new int[numVariables()];
        for (int i = 0; i < numVariables(); i++) {
            labels[i] = i + 1;
        }
    }

//    private void shuffleVariables() {
//        int numVariables;
//
//        if (getCovarianceMatrix() != null) {
//            numVariables = getCovarianceMatrix().getSize();
//        } else {
//            numVariables = dataSet.getNumColumns();
//        }
//
//        print("Shuffling variable order...");
//
//        List<Integer> indicesList = new ArrayList<Integer>();
//        for (int i = 0; i < numVariables; i++) indicesList.add(i);
//        Collections.shuffle(indicesList);
//
//        int[] indices = new int[numVariables];
//
//        for (int i = 0; i < numVariables; i++) {
//            indices[i] = indicesList.get(i);
//        }
//
//        if (getCovarianceMatrix() != null) {
//            covarianceMatrix = getCovarianceMatrix().getSubmatrix(indices);
//        }
//        if (dataSet != null) {
//            dataSet = dataSet.subsetColumns(indices);
//        }
//    }


    //**
    // * ****************************************************** SEARCH INTERFACE
    // * *******************************************************
    // */

    /**
     * @return the result search graph, or null if there is no mocel.
     */
    public Graph search() {
        long start = System.currentTimeMillis();

//        print("\n**************Starting BPC search!!!*************\n");
        TetradLogger.getInstance().log("info", "BPC alpha = " + alpha + " test = " + sigTestType);

        List<int[]> clustering = (List<int[]>) findMeasurementPattern();

        // Remove clusters of size < 3.
        for (int[] cluster : new ArrayList<int[]>(clustering)) {
            if (cluster.length < 3) {
                clustering.remove(cluster);
            }
        }

        List<Node> variables = tetradTest.getVariables();

        Set<Set<Integer>> clusters = new HashSet<Set<Integer>>();

        for (int[] _c : clustering) {
            Set<Integer> cluster = new HashSet<Integer>();

            for (int i : _c) {
                cluster.add(i);
            }

            clusters.add(cluster);
        }

        ClusterUtils.logClusters(clusters, variables);

        Graph graph = convertSearchGraph(clustering);

//        if (graph.getNumNodes() > 1) {
////            MimBuildEstimator estimator = MimBuildEstimator.newInstance(
////                    this.covarianceMatrix, new SemPm(graph));
////            estimator.estimate();
//
//            SemOptimizerEm optimizer = new SemOptimizerEm();
//            SemPm semPm = new SemPm(graph);
//            SemEstimator estimator = new SemEstimator(getCovarianceMatrix(),
//                    semPm, optimizer);
//            estimator.estimate();
//
//            print("chisq = " +
//                    estimator.getEstimatedSem().getChiSquare());
//            print("p-value = " +
//                    estimator.getEstimatedSem().getScore());
//        }

        this.logger.log("graph", "\nReturning this graph: " + graph);

        long stop = System.currentTimeMillis();

        long elapsed = stop - start;

        TetradLogger.getInstance().log("elapsed", "Elapsed " + elapsed + " ms");


        return graph;
    }

    /**
     * @return the converted search graph, or null if there is no model.
     */
    private Graph convertSearchGraph(List clusters) {
        List<Node> nodes = tetradTest.getVariables();
        Graph graph = new EdgeListGraph(nodes);

        List<Node> latents = new ArrayList<Node>();
        for (int i = 0; i < clusters.size(); i++) {
            Node latent = new GraphNode(MimBuild.LATENT_PREFIX + (i + 1));
            latent.setNodeType(NodeType.LATENT);
            latents.add(latent);
            graph.addNode(latent);
        }

        for (int i = 0; i < latents.size(); i++) {
            for (int j : (int[]) clusters.get(i)) {
                graph.addDirectedEdge(latents.get(i), nodes.get(j));
            }
        }

        return graph;

//        List nodes = new ArrayList();
//        if (clusters == null || clusters.size() == 0) {
//            nodes.add(new GraphNode("No_model."));
//            return new EdgeListGraph(nodes);
////            return null;
//        }
//        Set latentsSet = new HashSet();
//        for (int i = 0; i < clusters.size(); i++) {
//            Node latent = new GraphNode(MimBuild.LATENT_PREFIX + (i + 1));
//            latent.setNodeType(NodeType.LATENT);
//            nodes.add(latent);
//            latentsSet.add(latent);
//        }
//        for (int i = 0; i < clusters.size(); i++) {
//            int indicators[] = (int[]) clusters.get(i);
//            for (int j = 0; j < indicators.length; j++) {
//                String indicatorName = tetradTest.getVarNames()[indicators[j]];
//                System.out.println("Indicator name = " + indicatorName);
//                Node indicator = new GraphNode(indicatorName);
//                nodes.add(indicator);
//            }
//            System.out.println();
//        }
//        Graph graph = new EdgeListGraph(nodes);
//        int acc = clusters.size();
//        for (int i = 0; i < clusters.size(); i++) {
//            int indicators[] = (int[]) clusters.get(i);
//            for (int j = 0; j < indicators.length; j++) {
//                graph.setEndpoint((Node) nodes.get(i), (Node) nodes.get(acc),
//                        Endpoint.ARROW);
//                graph.setEndpoint((Node) nodes.get(acc), (Node) nodes.get(i),
//                        Endpoint.TAIL);
//                acc++;
//            }
//            for (int j = i + 1; j < clusters.size(); j++) {
//                graph.removeEdges((Node) nodes.get(i), (Node) nodes.get(j));
//            }
//        }
//        print("#ImpurePairs = " + impurePairs.size() / 2);
//        print("#ImpurePairs = " + impurePairs.size() / 2);
//        for (int i = 0; i < impurePairs.size(); i += 2) {
//            graph.setEndpoint((Node) impurePairs.get(i),
//                    (Node) impurePairs.get(i + 1), Endpoint.ARROW);
//            graph.setEndpoint((Node) impurePairs.get(i + 1),
//                    (Node) impurePairs.get(i), Endpoint.ARROW);
//        }
//        return graph;
    }

//    public void setHighPrecision(boolean p) {
//        if (this.tetradTest instanceof DiscreteTetradTest) {
//            ((DiscreteTetradTest) this.tetradTest).setHighPrecision(p);
//        }
//    }
//
//    public boolean getHighPrecision() {
//        if (this.tetradTest instanceof DiscreteTetradTest) {
//            return ((DiscreteTetradTest) this.tetradTest).getHighPrecision();
//        }
//        return false;
//    }

    /**
     * As a side effect, this also stores pairs of impure nodes
     */
    private List convertGraphToList(Graph solutionGraph) {
        impurePairs.clear();
        Iterator<Node> it1 = solutionGraph.getNodes().iterator();
        List<Node> latentsList = new ArrayList<Node>();
        List<ArrayList<Node>> clusters = new ArrayList<ArrayList<Node>>();
        while (it1.hasNext()) {
            Node next = it1.next();
            if (next.getNodeType() == NodeType.LATENT) {
                latentsList.add(next);
                clusters.add(new ArrayList<Node>());
            }
        }
        it1 = solutionGraph.getNodes().iterator();
        while (it1.hasNext()) {
            Node next = it1.next();
            if (!(next.getNodeType() == NodeType.LATENT)) {
                for (int w = 0; w < latentsList.size(); w++) {
                    if (solutionGraph.getNodesInTo(next, Endpoint.ARROW)
                            .contains(latentsList.get(w))) {
                        (clusters.get(w)).add(next);
                    }
                }
            }
        }
        List arrayClusters = new ArrayList();
        String names[] = tetradTest.getVarNames();
        for (int w = 0; w < clusters.size(); w++) {
            List<Node> listCluster = clusters.get(w);
            int newCluster[] = new int[listCluster.size()];
            for (int v = 0; v < newCluster.length; v++) {
                for (int s = 0; s < names.length; s++) {
                    if (names[s].equals(listCluster.get(v).toString())) {
                        newCluster[v] = s;
                        break;
                    }
                }
            }
            arrayClusters.add(newCluster);
        }
        for (int i = 0; i < solutionGraph.getNodes().size() - 1; i++) {
            for (int j = i + 1; j < solutionGraph.getNodes().size(); j++) {
                Node nodei = (Node) solutionGraph.getNodes().get(i);
                Node nodej = (Node) solutionGraph.getNodes().get(j);
                if (!(nodei.getNodeType() == NodeType.LATENT) &&
                        !(nodej.getNodeType() == NodeType.LATENT) &&
                        solutionGraph.isAdjacentTo(nodei, nodej)) {
                    impurePairs.add(solutionGraph.getNodes().get(i));
                    impurePairs.add(solutionGraph.getNodes().get(j));
                }
            }
        }
        return arrayClusters;
    }

    //*********************************************************
    // * POPULATION TESTS
    // *********************************************************/

//    private boolean vanishingPartialCorrPopulation(int x, int y, int z) {
//        double pc = getCovarianceMatrix().getValue(x, y)
//                - getCovarianceMatrix().getValue(x, z) *
//                getCovarianceMatrix().getValue(y, z);
//        pc /= Math.sqrt(1 - getCovarianceMatrix().getValue(x, z) *
//                getCovarianceMatrix().getValue(x, z)) *
//                Math.sqrt(1 - getCovarianceMatrix().getValue(y, z) *
//                        getCovarianceMatrix().getValue(y, z));
//        double r = 0.5 * Math.sqrt(getCovarianceMatrix().getSampleSize() - 4) *
//                Math.log((1. + pc) / (1. - pc));
//        return Math.abs(r) <= 0.0001;
//    }

    /**
     * ****************************************************** STATISTICAL TESTS *******************************************************
     */

    private boolean unclusteredPartial1(int v1, int v2, int v3, int v4) {
        if (this.scoreTestMode) {
            return tetradTest.oneFactorTest(v1, v2, v3, v4);
        } else {
            return tetradTest.tetradScore3(v1, v2, v3, v4);
        }
    }

    private boolean validClusterPairPartial1(int v1, int v2, int v3, int v4,
                                             int cv[][]) {
        if (this.scoreTestMode) {
            return tetradTest.oneFactorTest(v1, v2, v3, v4);
        } else {
            if (cv[v1][v4] == EDGE_NONE && cv[v2][v4] == EDGE_NONE &&
                    cv[v3][v4] == EDGE_NONE) {
                return true;
            }
            boolean test1 = tetradTest.tetradHolds(v1, v2, v3, v4);
            boolean test2 = tetradTest.tetradHolds(v1, v2, v4, v3);
            if (test1 && test2) {
                return true;
            }
            boolean test3 = tetradTest.tetradHolds(v1, v3, v4, v2);
            return (test1 && test3) || (test2 && test3);
        }
    }

    private boolean unclusteredPartial2(int v1, int v2, int v3, int v4, int v5) {
        if (this.scoreTestMode) {
            return tetradTest.oneFactorTest(v1, v2, v3, v5) &&
                    !tetradTest.oneFactorTest(v1, v2, v3, v4, v5) &&
                    tetradTest.twoFactorTest(v1, v2, v3, v4, v5);
        } else {
            return tetradTest.tetradScore3(v1, v2, v3, v5) &&

                    tetradTest.tetradScore1(v1, v2, v4, v5) &&
                    tetradTest.tetradScore1(v2, v3, v4, v5) &&
                    tetradTest.tetradScore1(v1, v3, v4, v5);
        }
    }

    private boolean validClusterPairPartial2(int v1, int v2, int v3, int v5,
                                             int cv[][]) {
        if (this.scoreTestMode) {
            return tetradTest.oneFactorTest(v1, v2, v3, v5);
        } else {
            if (cv[v1][v5] == EDGE_NONE && cv[v2][v5] == EDGE_NONE &&
                    cv[v3][v5] == EDGE_NONE) {
                return true;
            }
            boolean test1 = tetradTest.tetradHolds(v1, v2, v3, v5);
            boolean test2 = tetradTest.tetradHolds(v1, v2, v5, v3);
            boolean test3 = tetradTest.tetradHolds(v1, v3, v5, v2);
            if (!((test1 && test2) || (test1 && test3) || (test2 && test3))) {
                return false;
            }
            return true;
        }
    }

    private boolean unclusteredPartial3(int v1, int v2, int v3, int v4, int v5,
                                        int v6) {
        if (this.scoreTestMode) {
            return tetradTest.oneFactorTest(v1, v2, v3, v6) &&
                    tetradTest.oneFactorTest(v4, v5, v6, v1) &&
                    tetradTest.oneFactorTest(v4, v5, v6, v2) &&
                    tetradTest.oneFactorTest(v4, v5, v6, v3) &&
                    tetradTest.twoFactorTest(v1, v2, v3, v4, v5, v6);
        } else {
            return

                    tetradTest.tetradScore3(v1, v2, v3, v6) &&
                            tetradTest.tetradScore3(v4, v5, v6, v1) &&
                            tetradTest.tetradScore3(v4, v5, v6, v2) &&
                            tetradTest.tetradScore3(v4, v5, v6, v3) &&

                            tetradTest.tetradScore1(v1, v2, v4, v6) &&
                            tetradTest.tetradScore1(v1, v2, v5, v6) &&
                            tetradTest.tetradScore1(v2, v3, v4, v6) &&
                            tetradTest.tetradScore1(v2, v3, v5, v6) &&
                            tetradTest.tetradScore1(v1, v3, v4, v6) &&
                            tetradTest.tetradScore1(v1, v3, v5, v6);
        }
    }

    private boolean validClusterPairPartial3(int v1, int v2, int v3, int v4,
                                             int v5, int v6, int cv[][]) {
        if (this.scoreTestMode) {
            return tetradTest.oneFactorTest(v1, v2, v3, v6) &&
                    tetradTest.oneFactorTest(v4, v5, v6, v1) &&
                    tetradTest.oneFactorTest(v4, v5, v6, v2) &&
                    tetradTest.oneFactorTest(v4, v5, v6, v3);
        } else {
            if (cv[v1][v6] == EDGE_NONE && cv[v2][v6] == EDGE_NONE &&
                    cv[v3][v6] == EDGE_NONE) {
                return true;
            }
            boolean test1 = tetradTest.tetradHolds(v1, v2, v3, v6);
            boolean test2 = tetradTest.tetradHolds(v1, v2, v6, v3);
            boolean test3 = tetradTest.tetradHolds(v1, v3, v6, v2);
            if (!((test1 && test2) || (test1 && test3) || (test2 && test3))) {
                return false;
            }
            test1 = tetradTest.tetradHolds(v4, v5, v6, v1);
            test2 = tetradTest.tetradHolds(v4, v5, v1, v6);
            test3 = tetradTest.tetradHolds(v4, v6, v1, v5);
            if (!((test1 && test2) || (test1 && test3) || (test2 && test3))) {
                return false;
            }
            test1 = tetradTest.tetradHolds(v4, v5, v6, v2);
            test2 = tetradTest.tetradHolds(v4, v5, v2, v6);
            test3 = tetradTest.tetradHolds(v4, v6, v2, v5);
            if (!((test1 && test2) || (test1 && test3) || (test2 && test3))) {
                return false;
            }
            test1 = tetradTest.tetradHolds(v4, v5, v6, v3);
            test2 = tetradTest.tetradHolds(v4, v5, v3, v6);
            test3 = tetradTest.tetradHolds(v4, v6, v3, v5);
            if (!((test1 && test2) || (test1 && test3) || (test2 && test3))) {
                return false;
            } else {
                return true;
            }
        }
    }

    private boolean partialRule1_1(int x1, int x2, int x3, int y1) {
        if (this.scoreTestMode) {
            return tetradTest.oneFactorTest(x1, y1, x2, x3);
        }
        return tetradTest.tetradScore3(x1, y1, x2, x3);
    }

    private boolean partialRule1_2(int x1, int x2, int y1, int y2) {
        if (this.scoreTestMode) {
            return !tetradTest.oneFactorTest(x1, x2, y1, y2) &&
                    tetradTest.twoFactorTest(x1, x2, y1, y2);
        }
        return !tetradTest.tetradHolds(x1, x2, y2, y1) &&
                !tetradTest.tetradHolds(x1, y1, x2, y2) &&
                tetradTest.tetradHolds(x1, y1, y2, x2);

    }

    private boolean partialRule1_3(int x1, int y1, int y2, int y3) {
        if (this.scoreTestMode) {
            return tetradTest.oneFactorTest(x1, y1, y2, y3);
        }
        return tetradTest.tetradScore3(x1, y1, y2, y3);

    }

    //private boolean rule2(int x1, int x2, int x3, int y1, int y2, int y3) {
    //    if (this.scoreTestMode)
    //        return  tetradTest.twoFactorTest(x1, x2, y1, y2) &&
    //                tetradTest.twoFactorTest(x2, y2, y1, y3) &&
    //                tetradTest.twoFactorTest(x1, x3, x2, y2) &&
    //                !tetradTest.twoFactorTest(x1, y1, x2, y2);
    //    return
    //        tetradTest.tetradHolds(x1, y1, y2, x2) &&
    //        tetradTest.tetradHolds(x2, y1, y3, y2) &&
    //        tetradTest.tetradHolds(x1, x2, y2, x3) &&
    //        !tetradTest.tetradHolds(x1, x2, y2, y1) &&
    //            !tetradTest.tetradHolds(x1, y1, x2, y2) &&
    //            tetradTest.tetradHolds(x1, y1, y2, x2);
    //}

    private boolean partialRule2_1(int x1, int x2, int y1, int y2) {
        if (this.scoreTestMode) {
            return !tetradTest.oneFactorTest(x1, x2, y1, y2) &&
                    tetradTest.twoFactorTest(x1, x2, y1, y2);
        }
        return tetradTest.tetradHolds(x1, y1, y2, x2) &&
                !tetradTest.tetradHolds(x1, x2, y2, y1) &&
                !tetradTest.tetradHolds(x1, y1, x2, y2) &&
                tetradTest.tetradHolds(x1, y1, y2, x2);

    }

    private boolean partialRule2_2(int x1, int x2, int x3, int y2) {
        if (this.scoreTestMode) {
            return tetradTest.twoFactorTest(x1, x3, x2, y2);
        }
        return tetradTest.tetradHolds(x1, x2, y2, x3);

    }

    private boolean partialRule2_3(int x2, int y1, int y2, int y3) {
        if (this.scoreTestMode) {
            tetradTest.twoFactorTest(x2, y2, y1, y3);
        }
        return tetradTest.tetradHolds(x2, y1, y3, y2);

    }

    /*private boolean rule3(int x1, int x2, int x3, int y1, int y2, int y3) {
        if (this.scoreTestMode)
            return  tetradTest.oneFactorTest(x1, y1, y2, y3) &&
                    tetradTest.oneFactorTest(x1, x2, x3, y2) &&
                    tetradTest.oneFactorTest(x1, x2, x3, y3) &&
                    !tetradTest.twoFactorTest(x1, y2, x2, y3);
        return
            tetradTest.tetradScore3(x1, y1, y2, y3) &&
            tetradTest.tetradScore3(x1, x2, x3, y2) &&
            tetradTest.tetradScore3(x1, x2, x3, y3) &&
            !tetradTest.tetradHolds(x1, x2, y2, y3);
    }*/

    /*
     * Test vanishing marginal and partial correlations of two variables conditioned
     * in a third variables. I am using Fisher's z test as described in
     * Tetrad II user's manual.
     *
     * Notice that this code does not include asymptotic distribution-free
     * tests of vanishing partial correlation.
     *
     * For the discrete test, we just use g-square.
     */

    private boolean uncorrelated(int v1, int v2) {
//        if (sigTestType == TestType.POPULATION) {
//            return uncorrelatedPopulation(v1, v2);
//        }

//        if (true) return false;


        if (getCovarianceMatrix() != null) {
            List<Node> variables = getCovarianceMatrix().getVariables();
            return getIndependenceTest().isIndependent(variables.get(v1),
                    variables.get(v2));

        } else {
            return getIndependenceTest().isIndependent(dataSet.getVariable(v1),
                    dataSet.getVariable(v2));

        }

//        return getIndependenceTest().isIndependent(dataSet.getVariable(v1),
//                dataSet.getVariable(v2));


//        if (getCovarianceMatrix() != null) {
//            if (getIndependenceTest().isIndependent(
//                    getCovarianceMatrix().getVariables().get(v1),
//                    getCovarianceMatrix().getVariables().get(v2),
//                    new ArrayList<Node>())) {
////                System.out.println(getCovarianceMatrix().getVariables()
////                        .get(v1) + " " +
////                        getCovarianceMatrix().getVariables().get(v2) +
////                        " == " + getCovarianceMatrix().getValue(v1, v2));
////            }
//
//                return getIndependenceTest().isIndependent(
//                        getCovarianceMatrix().getVariables().get(v1),
//                        getCovarianceMatrix().getVariables().get(v2),
//                        new ArrayList<Node>());
//            } else {
//                return getIndependenceTest().isIndependent(dataSet.getVariable(v1),
//                        dataSet.getVariable(v2), new ArrayList<Node>());
//            }
    }

    private boolean vanishingPartialCorr(int x, int y, int z) {
        //NOTE: vanishingPartialCorr not being be used. This implementation of BuildPureClusters is
        // assuming no conditional d-sep holds in the population.
        if (true) {
            return false;
        }
//        if (sigTestType == TestType.POPULATION) {
//            return vanishingPartialCorrPopulation(x, y, z);
//        }
        if (getCovarianceMatrix() != null) {
            Node xVar = getCovarianceMatrix().getVariables().get(x);
            Node yVar = getCovarianceMatrix().getVariables().get(y);

            List<Node> zVar = new ArrayList<Node>();
            zVar.add(getCovarianceMatrix().getVariables().get(z));

            boolean indep = getIndependenceTest().isIndependent(xVar, yVar, zVar);

            if (!indep) {
                return indep;
            }

//            System.out.println(new StringBuilder().append(xVar).append(" ").append(yVar).append(" = ").append(getCovarianceMatrix().getValue(x, y)).append("(").append(getCovarianceMatrix().getVariableName(
//                    z)).append(")").toString());

            return indep;
        } else {
            List<Node> conditional = new ArrayList<Node>();
            conditional.add(dataSet.getVariable(z));
            return getIndependenceTest().isIndependent(dataSet.getVariable(x),
                    dataSet.getVariable(y),
                    conditional);
        }
    }

    /**
     * ****************************************************** DEBUG UTILITIES *******************************************************
     */

    private void printClustering(List<int[]> clustering) {
        for (int[] cluster : clustering) {
            printClusterNames(cluster);
        }

//        Iterator it = clustering.iterator();
//        while (it.hasNext()) {
//            int c[] = (int[]) it.next();
//            printClusterNames(c);
//        }
    }

    private void printClusterIds(int c[]) {
        int sorted[] = new int[c.length];
        for (int i = 0; i < c.length; i++) {
            sorted[i] = labels[c[i]];
        }
        for (int i = 0; i < sorted.length - 1; i++) {
            int min = 1000000;
            int min_idx = -1;
            for (int j = i; j < sorted.length; j++) {
                if (sorted[j] < min) {
                    min = sorted[j];
                    min_idx = j;
                }
            }
            int temp;
            temp = sorted[i];
            sorted[i] = min;
            sorted[min_idx] = temp;
        }
        for (int i = 0; i < sorted.length; i++) {
//            printMessage(sorted[i] + " ");
        }
//        printlnMessage();
    }

    private void printClusterNames(int c[]) {
        String sorted[] = new String[c.length];
        for (int i = 0; i < c.length; i++) {
            sorted[i] = tetradTest.getVarNames()[c[i]];
        }
        for (int i = 0; i < sorted.length - 1; i++) {
            String min = sorted[i];
            int min_idx = i;
            for (int j = i + 1; j < sorted.length; j++) {
                if (sorted[j].compareTo(min) < 0) {
                    min = sorted[j];
                    min_idx = j;
                }
            }
            String temp;
            temp = sorted[i];
            sorted[i] = min;
            sorted[min_idx] = temp;
        }
        for (int i = 0; i < sorted.length; i++) {
//            printMessage(sorted[i] + " ");
        }
//        printlnMessage();
    }

    //private void printImpurities(int graph[][]) {
    //    printlnMessage("*** IMPURITIES:");
    //    for (int i = 0; i < numV - 1; i++)
    //        for (int j = i + 1; j < numV; j++)
    //                //if (graph[i][j] == EDGE_GRAY || graph[i][j] == EDGE_YELLOW)
    //                //if (graph[i][j] == EDGE_BLUE)
    //            printlnMessage(labels[i] + " x " + labels[j] + ": " + graph[i][j]);
    //}

    private void printLatentClique(int latents[], int size) {
//        printMessage(">>> CLUSTER CLIQUE: ");
        int sorted[] = new int[latents.length];
        System.arraycopy(latents, 0, sorted, 0, latents.length);
        for (int i = 0; i < sorted.length - 1; i++) {
            int min = 1000000;
            int min_idx = -1;
            for (int j = i; j < sorted.length; j++) {
                if (sorted[j] < min) {
                    min = sorted[j];
                    min_idx = j;
                }
            }
            int temp;
            temp = sorted[i];
            sorted[i] = min;
            sorted[min_idx] = temp;
        }
        for (int i = 0; i < sorted.length; i++) {
//            printMessage("T" + (sorted[i] + 1) + " ");
        }
//        print(" SIZE = " + size);
    }

    /*private void printValidClusterPairs(int latents[][], int size) {
        printlnMessage("*** VALID LATENT PAIRS:");
        for (int i = 0; i < size - 1; i++)
            for (int j = i + 1; j < size; j++)
                if (latents[i][j] == EDGE_BLACK)
                    printlnMessage("T" + i + " x T" + j);
    }

    private void printRelations(int graph[][]) {
        printlnMessage("*** RELATIONS:");
        for (int i = 0; i < numV - 1; i++)
            for (int j = i + 1; j < numV; j++)
                printlnMessage(labels[i] + " x " + labels[j] + ": " + graph[i][j]);
    }*/

//    void printMessage(String message) {
//        if (outputMessage) {
//            System.out.print(message);
//        }
//    }
//
//    void print(String message) {
//        if (outputMessage) {
//            System.out.println(message);
//        }
//    }
//
//    void printlnMessage() {
//        if (outputMessage) {
//            System.out.println();
//        }
//    }
//
//    void printlnMessage(boolean flag) {
//        if (outputMessage) {
//            System.out.println(flag);
//        }
//    }

    //*********************************************************
    // * GRAPH ALGORITHMICAL TOOLS
    // *********************************************************/

    /**
     * Find components of a graph. Note: naive implementation, but it works. After all, it will still run much faster
     * than Stage 2 of the FindMeasurementPattern algorithm.
     */

    private List<int[]> findComponents(int graph[][], int size, int color) {
        boolean marked[] = new boolean[size];
        for (int i = 0; i < size; i++) {
            marked[i] = false;
        }
        int numMarked = 0;
        List<int[]> output = new ArrayList<int[]>();

        int tempComponent[] = new int[size];
        while (numMarked != size) {
            int sizeTemp = 0;
            boolean noChange;
            do {
                noChange = true;
                for (int i = 0; i < size; i++) {
                    if (marked[i]) {
                        continue;
                    }
                    boolean inComponent = false;
                    for (int j = 0; j < sizeTemp && !inComponent; j++) {
                        if (graph[i][tempComponent[j]] == color) {
                            inComponent = true;
                        }
                    }
                    if (sizeTemp == 0 || inComponent) {
                        tempComponent[sizeTemp++] = i;
                        marked[i] = true;
                        noChange = false;
                        numMarked++;
                    }
                }
            } while (!noChange);
            if (sizeTemp > 1) {
                int newPartition[] = new int[sizeTemp];
                for (int i = 0; i < sizeTemp; i++) {
                    newPartition[i] = tempComponent[i];
                }
                output.add(newPartition);
            }
        }
        return output;
    }

    /**
     * Find all maximal cliques of a graph. However, it can generate an exponential number of cliques as a function of
     * the number of impurities in the true graph. Therefore, we also use a counter to stop the computation after a
     * given number of calls. </p> This is an implementation of Algorithm 2 from Bron and Kerbosch (1973).
     */

    private List<int[]> findMaximalCliques(int elements[], int ng[][]) {
        boolean connected[][] = new boolean[this.numVariables()][this.numVariables()];
        for (int i = 0; i < connected.length; i++) {
            for (int j = i; j < connected.length; j++) {
                if (i != j) {
                    connected[i][j] = connected[j][i] =
                            (ng[i][j] != EDGE_NONE);
                } else {
                    connected[i][j] = true;
                }
            }
        }
        int numCalls[] = new int[1];
        numCalls[0] = 0;
        int c[] = new int[1];
        c[0] = 0;
        List<int[]> output = new ArrayList<int[]>();
        int compsub[] = new int[elements.length];
        int old[] = new int[elements.length];
        for (int i = 0; i < elements.length; i++) {
            old[i] = elements[i];
        }
        findMaximalCliquesOperator(numCalls, elements, output, connected,
                compsub, c, old, 0, elements.length);
        return output;
    }

    private void findMaximalCliquesOperator(int numCalls[], int elements[],
                                            List<int[]> output, boolean connected[][], int compsub[], int c[],
                                            int old[], int ne, int ce) {
        if (numCalls[0] > MAX_CLIQUE_TRIALS) {
            return;
        }
        int newA[] = new int[ce];
        int nod, fixp = -1;
        int newne, newce, i, j, count, pos = -1, p, s = -1, sel, minnod;
        minnod = ce;
        nod = 0;
        for (i = 0; i < ce && minnod != 0; i++) {
            p = old[i];
            count = 0;
            for (j = ne; j < ce && count < minnod; j++) {
                if (!connected[p][old[j]]) {
                    count++;
                    pos = j;
                }
            }
            if (count < minnod) {
                fixp = p;
                minnod = count;
                if (i < ne) {
                    s = pos;
                } else {
                    s = i;
                    nod = 1;
                }
            }
        }
        for (nod = minnod + nod; nod >= 1; nod--) {
            p = old[s];
            old[s] = old[ne];
            sel = old[ne] = p;
            newne = 0;
            for (i = 0; i < ne; i++) {
                if (connected[sel][old[i]]) {
                    newA[newne++] = old[i];
                }
            }
            newce = newne;
            for (i = ne + 1; i < ce; i++) {
                if (connected[sel][old[i]]) {
                    newA[newce++] = old[i];
                }
            }
            compsub[c[0]++] = sel;
            if (newce == 0) {
                int clique[] = new int[c[0]];
                System.arraycopy(compsub, 0, clique, 0, c[0]);
                output.add(clique);
            } else if (newne < newce) {
                numCalls[0]++;
                findMaximalCliquesOperator(numCalls, elements, output,
                        connected, compsub, c, newA, newne, newce);
            }
            c[0]--;
            ne++;
            if (nod > 1) {
                s = ne;
                while (connected[fixp][old[s]]) {
                    s++;
                }
            }
        }
    }

    /**
     * @return true iff "newClique" is contained in some element of "clustering".
     */

    private boolean cliqueContained(int newClique[], int size, List<int[]> clustering) {
        Iterator<int[]> it = clustering.iterator();
        while (it.hasNext()) {
            int next[] = it.next();
            if (size > next.length) {
                continue;
            }
            boolean found = true;
            for (int i = 0; i < size && found; i++) {
                found = false;
                for (int j = 0; j < next.length && !found; j++) {
                    if (newClique[i] == next[j]) {
                        found = true;
                        break;
                    }
                }
            }
            if (found) {
                return true;
            }
        }
        return false;
    }

    /**
     * Remove cliques that are contained into another ones in cliqueList.
     */
    private List trimCliqueList(List<int[]> cliqueList) {
        List trimmed = new ArrayList();
        List<int[]> cliqueCopy = new ArrayList<int[]>();
        cliqueCopy.addAll(cliqueList);

        Iterator<int[]> it = cliqueList.iterator();
        while (it.hasNext()) {
            int cluster[] = it.next();
            cliqueCopy.remove(cluster);
            if (!cliqueContained(cluster, cluster.length, cliqueCopy)) {
                trimmed.add(cluster);
            }
            cliqueCopy.add(cluster);
        }
        return trimmed;
    }

    private int clustersize(List cluster) {
        int total = 0;
        Iterator it = cluster.iterator();
        while (it.hasNext()) {
            int next[] = (int[]) it.next();
            total += next.length;
        }
        return total;
    }

    private int clustersize3(List cluster) {
        int total = 0;
        Iterator it = cluster.iterator();
        while (it.hasNext()) {
            int next[] = (int[]) it.next();
            if (next.length > 2) {
                total += next.length;
            }
        }
        return total;
    }

    private void sortClusterings(int start, int end, List clusterings,
                                 int criterion[]) {
        for (int i = start; i < end - 1; i++) {
            int max = -1;
            int max_idx = -1;
            for (int j = i; j < end; j++) {
                if (criterion[j] > max) {
                    max = criterion[j];
                    max_idx = j;
                }
            }
            Object temp;
            temp = clusterings.get(i);
            clusterings.set(i, clusterings.get(max_idx));
            clusterings.set(max_idx, temp);
            int old_c;
            old_c = criterion[i];
            criterion[i] = criterion[max_idx];
            criterion[max_idx] = old_c;
        }
    }

    /**
     * Transforms clusterings (each "clustering" is a set of "clusters"), remove overlapping indicators for each
     * clustering, and order clusterings according to the number of nonoverlapping indicators, throwing away any latent
     * with zero indicators. Also, for each pair of indicators such that they are linked in ng, one of them is chosen to
     * be removed (the heuristic is, choose the one that belongs to the largest cluster). </p> The list that is returned
     * is a list of lists. Each element in the big list is a list of integer arrays, where each integer array represents
     * one cluster.
     */

    private int scoreClustering(List clustering, int ng[][], boolean buffer[]) {
        int score = 0;
        for (int i = 0; i < buffer.length; i++) {
            buffer[i] = true;
        }

        //First filter: remove all overlaps
        for (Iterator it1 = clustering.iterator(); it1.hasNext(); ) {
            int currentCluster[] = (int[]) it1.next();
            next_item:
            for (int i = 0; i < currentCluster.length; i++) {
                if (!buffer[currentCluster[i]]) {
                    continue;
                }
                for (Iterator it2 = clustering.iterator(); it2.hasNext(); ) {
                    int nextCluster[] = (int[]) it2.next();
                    if (nextCluster == currentCluster) {
                        continue;
                    }
                    for (int j = 0; j < nextCluster.length; j++) {
                        if (currentCluster[i] == nextCluster[j]) {
                            buffer[currentCluster[i]] = false;
                            continue next_item;
                        }
                    }
                }
            }
        }

        //Second filter: remove nodes that are linked by an edge in ng but are in different clusters
        //(i.e., they were not shown to belong to different clusters)
        //Current criterion: for every such pair, remove the one in the largest cluster, unless the largest one
        //has only three indicators
        int localScore;
        for (int c1 = 0; c1 < clustering.size(); c1++) {
            int currentCluster[] = (int[]) clustering.get(c1);
            localScore = 0;
            next_item:
            for (int i = 0; i < currentCluster.length; i++) {
                if (!buffer[currentCluster[i]]) {
                    continue;
                }
                for (int c2 = c1 + 1; c2 < clustering.size(); c2++) {
                    int nextCluster[] = (int[]) clustering.get(c2);
                    for (int j = 0; j < nextCluster.length; j++) {
                        if (!buffer[nextCluster[j]]) {
                            continue;
                        }
                        //Commented out. Does not seem to work well in practice...
//                        if (ng[currentCluster[i]][nextCluster[j]] != EDGE_NONE) {
//                            if (((currentCluster.length > 3 && currentCluster.length > nextCluster.length) ||
//                                    (currentCluster.length < 3 && currentCluster.length < nextCluster.length) ||
//                                    currentCluster.length == nextCluster.length)) {
//                                buffer[currentCluster[i]] = false;
//                            }
//                            else {
//                                buffer[nextCluster[j]] = false;
//                            }
//                            continue next_item;
//                        }
                    }
                }
                localScore++;
            }
            if (localScore > 1) {
                score += localScore;
            }
        }

        return score;
    }

    private List filterAndOrderClusterings(List baseListOfClusterings,
                                           List<List<Integer>> baseListOfIds, List clusteringIds, int ng[][]) {
        assert clusteringIds != null;
        List listOfClusterings = new ArrayList();
        clusteringIds.clear();

        for (int i = 0; i < baseListOfClusterings.size(); i++) {

            //First filter: remove all overlaps
            List newClustering = new ArrayList();
            List baseClustering = (List) baseListOfClusterings.get(i);

            System.out.println("* Base mimClustering");
            printClustering(baseClustering);

            List<Integer> baseIds = baseListOfIds.get(i);
            List<Integer> usedIds = new ArrayList<Integer>();

            for (int j = 0; j < baseClustering.size(); j++) {
                int currentCluster[] = (int[]) baseClustering.get(j);
                Integer currentId = baseIds.get(j);
                int draftArea[] = new int[currentCluster.length];
                int draftCount = 0;
                next_item:
                for (int jj = 0; jj < currentCluster.length; jj++) {
                    for (int k = 0; k < baseClustering.size(); k++) {
                        if (k == j) {
                            continue;
                        }
                        int nextCluster[] = (int[]) baseClustering.get(k);
                        for (int q = 0; q < nextCluster.length; q++) {
                            if (currentCluster[jj] == nextCluster[q]) {
                                continue next_item;
                            }
                        }
                    }
                    draftArea[draftCount++] = currentCluster[jj];
                }
                if (draftCount > 1) {
                    //Only clusters with at least two indicators can be added
                    int newCluster[] = new int[draftCount];
                    System.arraycopy(draftArea, 0, newCluster, 0, draftCount);
                    newClustering.add(newCluster);
                    usedIds.add(currentId);
                }
            }

            System.out.println("* Filtered mimClustering 1");
            printClustering(newClustering);

            //Second filter: remove nodes that are linked by an edge in ng but are in different clusters
            //(i.e., they were not shown to belong to different clusters)
            //Current criterion: count the number of invalid relations each node partipates, greedily
            //remove nodes till none of these relations hold anymore
            boolean impurities[][] = new boolean[this.numVariables()][this.numVariables()];
            for (int j = 0; j < newClustering.size() - 1; j++) {
                int currentCluster[] = (int[]) newClustering.get(j);
                for (int jj = j + 1; jj < currentCluster.length; jj++) {
                    for (int k = 0; k < newClustering.size(); k++) {
                        if (k == j) {
                            continue;
                        }
                        int nextCluster[] = (int[]) newClustering.get(k);
                        for (int q = 0; q < nextCluster.length; q++) {
                            if (ng[currentCluster[jj]][nextCluster[q]] !=
                                    EDGE_NONE) {
                                impurities[currentCluster[jj]][nextCluster[q]] =
                                        true;
                            } else {
                                impurities[currentCluster[jj]][nextCluster[q]] =
                                        false;
                            }
                            impurities[nextCluster[q]][currentCluster[jj]] =
                                    impurities[currentCluster[jj]][nextCluster[q]];
                        }
                    }
                }
            }
            List newClustering2 = removeMarkedImpurities(newClustering,
                    impurities);
            List finalNewClustering = new ArrayList();
            List<Integer> finalUsedIds = new ArrayList<Integer>();
            for (int j = 0; j < newClustering2.size(); j++) {
                if (((int[]) newClustering2.get(j)).length > 0) {
                    finalNewClustering.add(newClustering2.get(j));
                    finalUsedIds.add(usedIds.get(j));
                }
            }
            if (finalNewClustering.size() > 0) {
                listOfClusterings.add(finalNewClustering);
                int usedIdsArray[] = new int[finalUsedIds.size()];
                for (int j = 0; j < finalUsedIds.size(); j++) {
                    usedIdsArray[j] = finalUsedIds.get(j);
                }
                clusteringIds.add(usedIdsArray);
                System.out.println("* Filtered mimClustering 2");
                printClustering(finalNewClustering);
                System.out.print("* ID/Size: ");
                printLatentClique(usedIdsArray,
                        clustersize3(finalNewClustering));
                System.out.println();
            }

        }

        //Now, order clusterings according to the number of latents with at least three children.
        //The second criterion is the total number of their indicators.
        int numIndicators[] = new int[listOfClusterings.size()];
        for (int i = 0; i < listOfClusterings.size(); i++) {
            numIndicators[i] = clustersize3((List) listOfClusterings.get(i));
        }
        sortClusterings(0, listOfClusterings.size(), listOfClusterings,
                numIndicators);
        for (int i = 0; i < listOfClusterings.size(); i++) {
            numIndicators[i] = clustersize((List) listOfClusterings.get(i));
        }
        int start = 0;
        while (start < listOfClusterings.size()) {
            int size3 = clustersize3((List) listOfClusterings.get(start));
            int end = start + 1;
            for (int j = start + 1; j < listOfClusterings.size(); j++) {
                if (size3 != clustersize3((List) listOfClusterings.get(j))) {
                    break;
                }
                end++;
            }
            sortClusterings(start, end, listOfClusterings, numIndicators);
            start = end;
        }

//        for (int i = 0; i < listOfClusterings.size(); i++) {
//            printMessage("    >>> ");
//            printLatentClique((int[]) clusteringIds.get(i), numIndicators[i]);
//        }
//        printlnMessage();

        return listOfClusterings;
    }

    private List removeMarkedImpurities(List partition, boolean impurities[][]) {
        System.out.println("sizecluster = " + clustersize(partition));
        int elements[][] = new int[clustersize(partition)][3];
        int partitionCount[] = new int[partition.size()];
        int countElements = 0;
        for (int p = 0; p < partition.size(); p++) {
            int next[] = (int[]) partition.get(p);
            partitionCount[p] = 0;
            for (int i = 0; i < next.length; i++) {
                elements[countElements][0] = next[i]; // global ID
                elements[countElements][1] = p;       // set partition ID
                countElements++;
                partitionCount[p]++;
            }
        }
        //Count how many impure relations is entailed by each indicator
        for (int i = 0; i < elements.length; i++) {
            elements[i][2] = 0;
            for (int j = 0; j < elements.length; j++) {
                if (impurities[elements[i][0]][elements[j][0]]) {
                    elements[i][2]++; // number of impure relations
                }
            }
        }

        //Iteratively eliminate impurities till some solution (or no solution) is found
        boolean eliminated[] = new boolean[this.numVariables()];
        for (int i = 0; i < eliminated.length; i++) {
            eliminated[i] = false;
        }
        while (!validSolution(elements, eliminated)) {
            //Sort them in the descending order of number of impurities (heuristic to avoid exponential search)
            sortByImpurityPriority(elements, partitionCount, eliminated);
            eliminated[elements[0][0]] = true;
            for (int i = 0; i < elements.length; i++) {
                if (impurities[elements[i][0]][elements[0][0]]) {
                    elements[i][2]--;
                }
            }
            partitionCount[elements[0][1]]--;
        }

        List solution = new ArrayList();
        Iterator it = partition.iterator();
        while (it.hasNext()) {
            int next[] = (int[]) it.next();
            int draftArea[] = new int[next.length];
            int draftCount = 0;
            for (int i = 0; i < next.length; i++) {
                for (int j = 0; j < elements.length; j++) {
                    if (elements[j][0] == next[i] &&
                            !eliminated[elements[j][0]]) {
                        draftArea[draftCount++] = next[i];
                    }
                }
            }
            if (draftCount > 0) {
                int realCluster[] = new int[draftCount];
                System.arraycopy(draftArea, 0, realCluster, 0, draftCount);
                solution.add(realCluster);
            }
        }
//        if (solution.size() > 0) {
        return solution;
//        }
//        else {
//            return null;
//        }

    }

    private void sortByImpurityPriority(int elements[][], int partitionCount[],
                                        boolean eliminated[]) {
        int temp[] = new int[3];

        //First, throw all eliminated elements to the end of the array
        for (int i = 0; i < elements.length - 1; i++) {
            if (eliminated[elements[i][0]]) {
                for (int j = i + 1; j < elements.length; j++) {
                    if (!eliminated[elements[j][0]]) {
                        swapElements(elements, i, j, temp);
                        break;
                    }
                }
            }
        }
        int total = 0;
        while (total < elements.length && !eliminated[elements[total][0]]) {
            total++;
        }

        //Sort them in the descending order of number of impurities
        for (int i = 0; i < total - 1; i++) {
            int max = -1;
            int max_idx = -1;
            for (int j = i; j < total; j++) {
                if (elements[j][2] > max) {
                    max = elements[j][2];
                    max_idx = j;
                }
            }
            swapElements(elements, i, max_idx, temp);
        }

        //Now, within each cluster, select first those that belong to clusters with less than three latents.
        //Then, in decreasing order of cluster size.
        int start = 0;
        while (start < total) {
            int size = partitionCount[elements[start][1]];
            int end = start + 1;
            for (int j = start + 1; j < total; j++) {
                if (size != partitionCount[elements[j][1]]) {
                    break;
                }
                end++;
            }
            //Put elements with partitionCount of 1 and 2 at the top of the list
            for (int i = start + 1; i < end; i++) {
                if (partitionCount[elements[i][1]] == 1) {
                    swapElements(elements, i, start, temp);
                    start++;
                }
            }
            for (int i = start + 1; i < end; i++) {
                if (partitionCount[elements[i][1]] == 2) {
                    swapElements(elements, i, start, temp);
                    start++;
                }
            }
            //Now, order elements in the descending order of partitionCount
            for (int i = start; i < end - 1; i++) {
                int max = -1;
                int max_idx = -1;
                for (int j = i; j < end; j++) {
                    if (partitionCount[elements[j][1]] > max) {
                        max = partitionCount[elements[j][1]];
                        max_idx = j;
                    }
                }
                swapElements(elements, i, max_idx, temp);
            }
            start = end;
        }
    }

    private void swapElements(int elements[][], int i, int j, int buffer[]) {
        buffer[0] = elements[i][0];
        buffer[1] = elements[i][1];
        buffer[2] = elements[i][2];
        elements[i][0] = elements[j][0];
        elements[i][1] = elements[j][1];
        elements[i][2] = elements[j][2];
        elements[j][0] = buffer[0];
        elements[j][1] = buffer[1];
        elements[j][2] = buffer[2];
    }

    private boolean validSolution(int elements[][], boolean eliminated[]) {
        for (int i = 0; i < elements.length; i++) {
            if (!eliminated[elements[i][0]] && elements[i][2] > 0) {
                return false;
            }
        }
        return true;
    }


    /**
     * ****************************************************** MAIN ALGORITHM: INITIALIZATION
     * *******************************************************
     */

    private List initialMeasurementPattern(int ng[][], int cv[][]) {

        boolean notYellow[][] = new boolean[numVariables()][numVariables()];

//        print("\n****************\nFIND PATTERN!!!\n****************\n");

        /* Stage 1: identify (partially) uncorrelated and impure pairs */
//        print(">> Stage 0.1");
        for (int v1 = 0; v1 < numVariables() - 1; v1++) {
            for (int v2 = v1 + 1; v2 < numVariables(); v2++) {
                ng[v1][v2] = ng[v2][v1] = EDGE_BLACK;
            }
        }
        for (int v1 = 0; v1 < numVariables() - 1; v1++) {
            for (int v2 = v1 + 1; v2 < numVariables(); v2++) {
                if (uncorrelated(v1, v2)) {
                    cv[v1][v2] = cv[v2][v1] = EDGE_NONE;
                } else {
                    cv[v1][v2] = cv[v2][v1] = EDGE_BLACK;
                }
                ng[v1][v2] = ng[v2][v1] = cv[v1][v2];
            }
        }
        for (int v1 = 0; v1 < numVariables() - 1; v1++) {
            for (int v2 = v1 + 1; v2 < numVariables(); v2++) {
                if (cv[v1][v2] == EDGE_NONE) {
                    continue;
                }
                for (int v3 = 0; v3 < numVariables(); v3++) {
                    if (v1 == v3 || v2 == v3) {
                        continue;
                    }
                    if (vanishingPartialCorr(v1, v2, v3)) {
                        cv[v1][v2] = cv[v2][v1] = EDGE_NONE;
                        break;
                    }
                }
            }
        }

        for (int i = 0; i < numVariables(); i++) {
            for (int j = i + 1; j < numVariables(); j++) {
                if (cv[i][j] == EDGE_NONE) {
//                    print(tetradTest.getVarNames()[i] + " || " +
//                            tetradTest.getVarNames()[j] + "? YES");
                }
            }
        }

        for (int v1 = 0; v1 < numVariables() - 1; v1++) {
            for (int v2 = v1 + 1; v2 < numVariables(); v2++) {
                if (ng[v1][v2] != EDGE_BLACK) {
                    continue;
                }
                boolean notFound = true;
                for (int v3 = 0; v3 < numVariables() - 1 && notFound; v3++) {
                    if (v1 == v3 || v2 == v3 || ng[v1][v3] == EDGE_NONE || ng[v1][v3] ==
                            EDGE_GRAY || ng[v2][v3] == EDGE_NONE || ng[v2][v3] ==
                            EDGE_GRAY) {
                        continue;
                    }
                    for (int v4 = v3 + 1; v4 < numVariables() && notFound; v4++) {
                        if (v1 == v4 || v2 == v4 || ng[v1][v4] == EDGE_NONE ||
                                ng[v1][v4] == EDGE_GRAY ||
                                ng[v2][v4] == EDGE_NONE ||
                                ng[v2][v4] == EDGE_GRAY ||
                                ng[v3][v4] == EDGE_NONE ||
                                ng[v3][v4] == EDGE_GRAY) {
                            continue;
                        }
                        if (tetradTest.tetradScore3(v1, v2, v3, v4)) {
                            notFound = false;
                            ng[v1][v2] = ng[v2][v1] = EDGE_BLUE;
                            ng[v1][v3] = ng[v3][v1] = EDGE_BLUE;
                            ng[v1][v4] = ng[v4][v1] = EDGE_BLUE;
                            ng[v2][v3] = ng[v3][v2] = EDGE_BLUE;
                            ng[v2][v4] = ng[v4][v2] = EDGE_BLUE;
                            ng[v3][v4] = ng[v4][v3] = EDGE_BLUE;
                        }
                    }
                }
                if (notFound) {
                    ng[v1][v2] = ng[v2][v1] = EDGE_GRAY;
//                    print("Pairwise test: " + tetradTest.getVarNames()[v1] +
//                            " x " +
//                            tetradTest.getVarNames()[v2] +
//                            " = " +
//                            notFound);
                }
            }
        }

        /* Stage 2: prune blue edges, find yellow ones */
//        print(">> Stage 0.2");
        for (int i = 0; i < numVariables() - 1; i++) {
            for (int j = i + 1; j < numVariables(); j++) {
                notYellow[i][j] = notYellow[j][i] = false;
            }
        }

        for (int v1 = 0; v1 < numVariables() - 1; v1++) {
            for (int v2 = v1 + 1; v2 < numVariables(); v2++) {
                //Trying to find unclustered({v1, v3, v5}, {v2, v4, v6})
                if (ng[v1][v2] != EDGE_BLUE) {
                    continue;
                }
//                printMessage("Searching for " + tetradTest.getVarNames()[v1] +
//                        " x " + tetradTest.getVarNames()[v2] + "...");
                boolean notFound = true;
                for (int v3 = 0; v3 < numVariables() - 1 && notFound; v3++) {
                    if (v1 == v3 || v2 == v3 || //ng[v1][v3] != EDGE_BLUE ||
                            ng[v1][v3] == EDGE_GRAY || ng[v2][v3] == EDGE_GRAY ||
                            cv[v1][v3] != EDGE_BLACK ||
                            cv[v2][v3] != EDGE_BLACK) {
                        continue;
                    }
                    for (int v5 = v3 + 1; v5 < numVariables() && notFound; v5++) {
                        if (v1 == v5 || v2 == v5 || //ng[v1][v5] != EDGE_BLUE || ng[v3][v5] != EDGE_BLUE ||
                                ng[v1][v5] == EDGE_GRAY ||
                                ng[v2][v5] == EDGE_GRAY ||
                                ng[v3][v5] == EDGE_GRAY ||
                                cv[v1][v5] != EDGE_BLACK ||
                                cv[v2][v5] != EDGE_BLACK ||
                                cv[v3][v5] != EDGE_BLACK ||
                                !unclusteredPartial1(v1, v3, v5, v2)) {
                            continue;
                        }
                        for (int v4 = 0; v4 < numVariables() - 1 && notFound; v4++) {
                            if (v1 == v4 || v2 == v4 || v3 == v4 || v5 == v4 ||
                                    ng[v1][v4] == EDGE_GRAY ||
                                    ng[v2][v4] == EDGE_GRAY ||
                                    ng[v3][v4] == EDGE_GRAY ||
                                    ng[v5][v4] == EDGE_GRAY || //ng[v2][v4] != EDGE_BLUE ||
                                    cv[v1][v4] != EDGE_BLACK ||
                                    cv[v2][v4] != EDGE_BLACK ||
                                    cv[v3][v4] != EDGE_BLACK ||
                                    cv[v5][v4] != EDGE_BLACK ||
                                    !unclusteredPartial2(v1, v3, v5, v2, v4)) {
                                continue;
                            }
                            for (int v6 = v4 + 1;
                                 v6 < numVariables() && notFound; v6++) {
                                if (v1 == v6 || v2 == v6 || v3 == v6 ||
                                        v5 == v6 || ng[v1][v6] == EDGE_GRAY ||
                                        ng[v2][v6] == EDGE_GRAY ||
                                        ng[v3][v6] == EDGE_GRAY ||
                                        ng[v4][v6] == EDGE_GRAY ||
                                        ng[v5][v6] == EDGE_GRAY || //ng[v2][v6] != EDGE_BLUE || ng[v4][v6] != EDGE_BLUE ||
                                        cv[v1][v6] != EDGE_BLACK ||
                                        cv[v2][v6] != EDGE_BLACK ||
                                        cv[v3][v6] != EDGE_BLACK ||
                                        cv[v4][v6] != EDGE_BLACK ||
                                        cv[v5][v6] != EDGE_BLACK) {
                                    continue;
                                }
                                if (unclusteredPartial3(v1, v3, v5, v2, v4, v6)) {
                                    notFound = false;
                                    ng[v1][v2] = ng[v2][v1] = EDGE_NONE;
                                    ng[v1][v4] = ng[v4][v1] = EDGE_NONE;
                                    ng[v1][v6] = ng[v6][v1] = EDGE_NONE;
                                    ng[v3][v2] = ng[v2][v3] = EDGE_NONE;
                                    ng[v3][v4] = ng[v4][v3] = EDGE_NONE;
                                    ng[v3][v6] = ng[v6][v3] = EDGE_NONE;
                                    ng[v5][v2] = ng[v2][v5] = EDGE_NONE;
                                    ng[v5][v4] = ng[v4][v5] = EDGE_NONE;
                                    ng[v5][v6] = ng[v6][v5] = EDGE_NONE;
                                    notYellow[v1][v3] = notYellow[v3][v1] =
                                            true;
                                    notYellow[v1][v5] = notYellow[v5][v1] =
                                            true;
                                    notYellow[v3][v5] = notYellow[v5][v3] =
                                            true;
                                    notYellow[v2][v4] = notYellow[v4][v2] =
                                            true;
                                    notYellow[v2][v6] = notYellow[v6][v2] =
                                            true;
                                    notYellow[v4][v6] = notYellow[v6][v4] =
                                            true;
//                                    print("(" + labels[v1] + ", " +
//                                            labels[v3] +
//                                            ", " +
//                                            labels[v5] +
//                                            ", " +
//                                            labels[v2] +
//                                            ", " +
//                                            labels[v4] +
//                                            ", " +
//                                            labels[v6] +
//                                            ") --> NONE");
                                }
                            }
                        }
                    }
                }
                if (notYellow[v1][v2]) {
                    if (notFound) {
//                        print("BLUE");
                    }
                    notFound = false;
                }

                if (notFound) {
                    //Trying to find unclustered({v1, v2, v3}, {v4, v5, v6})
                    for (int v3 = 0; v3 < numVariables() && notFound; v3++) {
                        if (v1 == v3 || v2 == v3 || ng[v1][v3] == EDGE_GRAY ||
                                ng[v2][v3] == EDGE_GRAY ||
                                cv[v1][v3] != EDGE_BLACK ||
                                cv[v2][v3] != EDGE_BLACK)
                        //ng[v1][v3] != EDGE_BLUE || ng[v2][v3] != EDGE_BLUE)*/
                        {
                            continue;
                        }
                        for (int v4 = 0; v4 < numVariables() - 2 && notFound; v4++) {
                            if (v1 == v4 || v2 == v4 || v3 == v4 ||
                                    ng[v1][v4] == EDGE_GRAY ||
                                    ng[v2][v4] == EDGE_GRAY ||
                                    ng[v3][v4] == EDGE_GRAY ||
                                    cv[v1][v4] != EDGE_BLACK ||
                                    cv[v2][v4] != EDGE_BLACK ||
                                    cv[v3][v4] != EDGE_BLACK ||
                                    !unclusteredPartial1(v1, v2, v3, v4)) {
                                continue;
                            }
                            for (int v5 = v4 + 1;
                                 v5 < numVariables() - 1 && notFound; v5++) {
                                if (v1 == v5 || v2 == v5 || v3 == v5 ||
                                        ng[v1][v5] == EDGE_GRAY ||
                                        ng[v2][v5] == EDGE_GRAY ||
                                        ng[v3][v5] == EDGE_GRAY ||
                                        ng[v4][v5] == EDGE_GRAY ||
                                        cv[v1][v5] != EDGE_BLACK ||
                                        cv[v2][v5] != EDGE_BLACK ||
                                        cv[v3][v5] != EDGE_BLACK ||
                                        cv[v4][v5] != EDGE_BLACK || //ng[v4][v5] != EDGE_BLUE ||
                                        !unclusteredPartial2(v1, v2, v3, v4,
                                                v5)) {
                                    continue;
                                }
                                for (int v6 = v5 + 1;
                                     v6 < numVariables() && notFound; v6++) {
                                    if (v1 == v6 || v2 == v6 || v3 == v6 ||
                                            ng[v1][v6] == EDGE_GRAY ||
                                            ng[v2][v6] == EDGE_GRAY ||
                                            ng[v3][v6] == EDGE_GRAY ||
                                            ng[v4][v6] == EDGE_GRAY ||
                                            ng[v5][v6] == EDGE_GRAY ||
                                            cv[v1][v6] != EDGE_BLACK ||
                                            cv[v2][v6] != EDGE_BLACK ||
                                            cv[v3][v6] != EDGE_BLACK ||
                                            cv[v4][v6] != EDGE_BLACK ||
                                            cv[v5][v6] != EDGE_BLACK)
                                    //ng[v4][v6] != EDGE_BLUE || ng[v5][v6] != EDGE_BLUE)*/
                                    {
                                        continue;
                                    }
                                    if (unclusteredPartial3(v1, v2, v3, v4, v5,
                                            v6)) {
                                        notFound = false;
                                        ng[v1][v4] = ng[v4][v1] = EDGE_NONE;
                                        ng[v1][v5] = ng[v5][v1] = EDGE_NONE;
                                        ng[v1][v6] = ng[v6][v1] = EDGE_NONE;
                                        ng[v2][v4] = ng[v4][v2] = EDGE_NONE;
                                        ng[v2][v5] = ng[v5][v2] = EDGE_NONE;
                                        ng[v2][v6] = ng[v6][v2] = EDGE_NONE;
                                        ng[v3][v4] = ng[v4][v3] = EDGE_NONE;
                                        ng[v3][v5] = ng[v5][v3] = EDGE_NONE;
                                        ng[v3][v6] = ng[v6][v3] = EDGE_NONE;
                                        notYellow[v1][v2] = notYellow[v2][v1] =
                                                true;
                                        notYellow[v1][v3] = notYellow[v3][v1] =
                                                true;
                                        notYellow[v2][v3] = notYellow[v3][v2] =
                                                true;
                                        notYellow[v4][v5] = notYellow[v5][v4] =
                                                true;
                                        notYellow[v4][v6] = notYellow[v6][v4] =
                                                true;
                                        notYellow[v5][v6] = notYellow[v6][v5] =
                                                true;
//                                        print("(" + labels[v1] + ", " +
//                                                labels[v2] +
//                                                ", " +
//                                                labels[v3] +
//                                                ", " +
//                                                labels[v4] +
//                                                ", " +
//                                                labels[v5] +
//                                                ", " +
//                                                labels[v6] +
//                                                ") --> BLUE");
                                    }
                                }
                            }
                        }
                    }
                }
                if (notFound) {
                    ng[v1][v2] = ng[v2][v1] = EDGE_YELLOW;
//                    print("YELLOW!");
                }

            }
        }

        /*int numyellow = 0, numgray = 0, numblue = 0, numnone = 0;
        for (int i = 0; i < ng.length; i++) {
            for (int j = i + 1; j < ng.length; j++) {
                if (ng[i][j] == EDGE_YELLOW) {
                    numyellow++;
                }
                else if (ng[i][j] == EDGE_BLUE) {
                    numblue++;
                }
                else if (ng[i][j] == EDGE_NONE) {
                    numnone++;
                }
                else if (ng[i][j] == EDGE_GRAY) {
                    numgray++;
                }
            }
        } */
        //System.out.println("numyelllow = " + numyellow);
        //System.out.println("numgray = " + numgray);
        //System.out.println("numblue = " + numblue);
        //System.out.println("numnone = " + numnone);
        //System.exit(0);

        /* Stage 3: find maximal cliques */
//        print(">> Stage 0.3.1");
        List clustering = new ArrayList();
        List<int[]> components = findComponents(ng, numVariables(), EDGE_BLUE);
        Iterator<int[]> it = components.iterator();
//        print(">> Stage 0.3.2");
        while (it.hasNext()) {
            int component[] = it.next();
//            print(">>> Searching for cliques in ");
            printClusterIds(component);
            List<int[]> nextClustering = findMaximalCliques(component, ng);
//            System.out.println(
//                    "nextClustering.size() = " + nextClustering.size());
            clustering.addAll(trimCliqueList(nextClustering));
//            System.out.println("mimClustering.size() = " + clustering.size());
        }
        //Sort cliques by size: heuristic to keep as many indicators as possible
        for (int i = 0; i < clustering.size() - 1; i++) {
            int max = 0;
            int max_idx = -1;
            for (int j = i; j < clustering.size(); j++) {
                if (((int[]) clustering.get(j)).length > max) {
                    max = ((int[]) clustering.get(j)).length;
                    max_idx = j;
                }
            }
            Object temp;
            temp = clustering.get(i);
            clustering.set(i, clustering.get(max_idx));
            clustering.set(max_idx, temp);
        }
//        print("**** INITIAL CLUSTERING OUTPUT: ");
//        printClustering(clustering);
//        print("**** INDIVIDUAL CLUSTER PURIFICATION: ");
        List<int[]> individualOneFactors = individualPurification(clustering);
        printClustering(individualOneFactors);
        clustering = individualOneFactors;
//        print(">> Stage 0.4 - Finding pairwise cluster relations");
        List<List<Integer>> ids = new ArrayList<List<Integer>>();
        List clusterings = chooseClusterings(clustering, ng, ids, true, cv);
//        print(">> Stage 0.5 - Finding a pure measurement model");
        List orderedIds = new ArrayList();
        List actualClustering = filterAndOrderClusterings(clusterings, ids,
                orderedIds, ng);
        return purify(actualClustering, orderedIds, null);
    }

    private List<int[]> individualPurification(List clustering) {
        boolean oldOutputMessage = this.outputMessage;
        List<int[]> purified = new ArrayList<int[]>();
        int ids[] = {1};
        for (int i = 0; i < clustering.size(); i++) {
//            print(" * Solving cluster #" + (i + 1));
            this.outputMessage = false;
            int rawCluster[] = (int[]) clustering.get(i); //It is important here that the same order is mantained in each list
            if (rawCluster.length <= 4) {
                this.outputMessage = oldOutputMessage;
                purified.add(rawCluster);
                continue;
            }
            List dummyClusterings = new ArrayList();
            List dummyClustering = new ArrayList();
            dummyClustering.add(rawCluster);
            dummyClusterings.add(dummyClustering);
            List dummyIds = new ArrayList();
            dummyIds.add(ids);
            List<int[]> purification = purify(dummyClusterings, dummyIds, null);
            if (purification.size() > 0) {
                purified.add(purification.get(0));
            } else {
                int newFakeCluster[] = new int[4];
                System.arraycopy(rawCluster, 0, newFakeCluster, 0, 4);
                purified.add(newFakeCluster);
            }
            this.outputMessage = oldOutputMessage;
        }
        return purified;
    }

    private boolean compatibleClusters(int cluster1[], int cluster2[],
                                       int cv[][]) {
        HashSet<Integer> allNodes = new HashSet<Integer>();

        for (int i = 0; i < cluster1.length; i++) {
            allNodes.add(cluster1[i]);
        }

        for (int i = 0; i < cluster2.length; i++) {
            allNodes.add(cluster2[i]);
        }

        if (allNodes.size() < cluster1.length + cluster2.length) return false;


        int cset1 = cluster1.length;
        int cset2 = cluster2.length;
        for (int o1 = 0; o1 < cset1 - 2; o1++) {
            for (int o2 = o1 + 1; o2 < cset1 - 1; o2++) {
                for (int o3 = o2 + 1; o3 < cset1; o3++) {
                    for (int o4 = 0; o4 < cset2 - 2; o4++) {
//                        if (cluster1[o4] == o1 || cluster1[o4] == o2 || cluster1[o4] == o3) continue;

                        if (!validClusterPairPartial1(cluster1[o1],
                                cluster1[o2], cluster1[o3], cluster2[o4], cv)) {
                            continue;
                        }
                        for (int o5 = o4 + 1; o5 < cset2 - 1; o5++) {
//                            if (cluster1[o5] == o1 || cluster1[o5] == o2 || cluster1[o5] == o3) continue;

                            if (!validClusterPairPartial2(cluster1[o1],
                                    cluster1[o2], cluster1[o3], cluster2[o5],
                                    cv)) {
                                continue;
                            }
                            for (int o6 = o5 + 1; o6 < cset2; o6++) {
//                                if (cluster1[o6] == o1 || cluster1[o6] == o2 || cluster1[o6] == o3) continue;

                                if (validClusterPairPartial3(cluster1[o1],
                                        cluster1[o2], cluster1[o3],
                                        cluster2[o4], cluster2[o5],
                                        cluster2[o6], cv)) {
                                    return true;
                                }
                            }
                        }
                    }
                }
            }
        }
        System.out.println("INCOMPATIBLE!:");
        printClusterNames(cluster1);
        printClusterNames(cluster2);
        return false;
    }

    /**
     * ****************************************************** MAIN ALGORITHM: CORE *******************************************************
     */

    private List findMeasurementPattern() {
        int ng[][] = new int[numVariables()][numVariables()];
        int cv[][] = new int[numVariables()][numVariables()];
        boolean selected[] = new boolean[numVariables()];

        for (int i = 0; i < numVariables(); i++) {
            selected[i] = false;
        }

        List initialClustering = initialMeasurementPattern(ng, cv);
//        print("Initial mimClustering:");
        printClustering(initialClustering);
        List<Set<String>> forbiddenList = new ArrayList<Set<String>>();
        for (int c1 = 0; c1 < initialClustering.size(); c1++) {
            int nextCluster[] = (int[]) initialClustering.get(c1);
            for (int i = 0; i < nextCluster.length; i++) {
                selected[nextCluster[i]] = true;
                for (int j = i + 1; j < nextCluster.length; j++) {
                    Set<String> nextPair = new HashSet<String>();
                    nextPair.add(this.tetradTest.getVarNames()[nextCluster[i]]);
                    nextPair.add(this.tetradTest.getVarNames()[nextCluster[j]]);
                    forbiddenList.add(nextPair);
                }
            }
            for (int c2 = c1 + 1; c2 < initialClustering.size(); c2++) {
                int nextCluster2[] = (int[]) initialClustering.get(c2);
                for (int i = 0; i < nextCluster.length; i++) {
                    for (int j = 0; j < nextCluster2.length; j++) {
                        Set<String> nextPair = new HashSet<String>();
                        nextPair.add(
                                this.tetradTest.getVarNames()[nextCluster[i]]);
                        nextPair.add(
                                this.tetradTest.getVarNames()[nextCluster2[j]]);
                        forbiddenList.add(nextPair);
                    }
                }
            }

        }
        /* remove this
        for (int v1 = 0; v1 < numV - 1; v1++) {
            for (int v2 = v1 + 1; v2 < numV; v2++) {
                ng[v1][v2] = ng[v2][v1] = EDGE_BLUE;
            }
        }
        for (int v1 = 0; v1 < numV - 1; v1++) {
            for (int v2 = v1 + 1; v2 < numV; v2++) {
                if (uncorrelated(v1, v2)) {
                    cv[v1][v2] = cv[v2][v1] = EDGE_NONE;
                }
                else {
                    cv[v1][v2] = cv[v2][v1] = EDGE_BLUE;
                }
                ng[v1][v2] = ng[v2][v1] = cv[v1][v2];
            }
        }
        for (int v1 = 0; v1 < numV - 1; v1++) {
            for (int v2 = v1 + 1; v2 < numV; v2++) {
                if (cv[v1][v2] == EDGE_NONE) {
                    continue;
                }
                for (int v3 = 0; v3 < numV; v3++) {
                    if (v1 == v3 || v2 == v3) {
                        continue;
                    }
                    if (vanishingPartialCorr(v1, v2, v3)) {
                        cv[v1][v2] = cv[v2][v1] = EDGE_NONE;
                        break;
                    }
                }
            }
        }*/

        //double oldSig = this.tetradTest.getParameter1();
        //this.tetradTest.setParameter1(oldSig / 3.);

//        print(">> Stage 0");
//        print(
//                "\n****************\nFIND FINAL PATTERN!!!\n****************\n");

        /* Stage 1: identify (partially) uncorrelated and impure pairs */
//        print(">> Stage 1: reidentify edge colors");
        for (int i = 0; i < numVariables(); i++) {
            for (int j = 0; j < numVariables(); j++) {
                if (selected[i] && selected[j] &&
                        (ng[i][j] == EDGE_BLUE || ng[i][j] == EDGE_YELLOW)) {
                    ng[i][j] = EDGE_RED;
                } else if ((!selected[i] || !selected[j]) &&
                        ng[i][j] == EDGE_YELLOW) {
                    ng[i][j] = EDGE_BLUE;
                }
            }
        }

        /*for (int v1 = 0; v1 < numV - 1; v1++)
            for (int v2 = v1 + 1; v2 < numV; v2++) {
                if (ng[v1][v2] != EDGE_BLACK)
                    continue;
                boolean notFound = true;
                for (int v3 = 0; v3 < numV && notFound; v3++) {
                    if (v1 == v3 || v2 == v3 ||
                        ng[v1][v3] == EDGE_GRAY || ng[v2][v3] == EDGE_GRAY)
                        continue;
                    for (int v4 = 0; v4 < numV && notFound; v4++) {
                        if (v1 == v4 || v2 == v4 || v3 == v4 ||
                            ng[v1][v4] == EDGE_GRAY || ng[v2][v4] == EDGE_GRAY || ng[v3][v4] == EDGE_GRAY)
                            continue;
                        if (tetradTest.tetradHolds(indicesSU[v1], indicesSU[v2], indicesSU[v4], indicesSU[v3])) {
                            notFound = false;
                            ng[v1][v2] = ng[v2][v1] = EDGE_BLUE;
                            if (ng[v1][v4] == EDGE_BLACK)
                               ng[v1][v4] = ng[v4][v1] = EDGE_BLUE;
                            if (ng[v2][v3] == EDGE_BLACK)
                               ng[v2][v3] = ng[v3][v2] = EDGE_BLUE;
                            if (ng[v3][v4] == EDGE_BLACK)
                               ng[v3][v4] = ng[v4][v3] = EDGE_BLUE;
                        }
                    }
                }
                if (notFound) {
                    ng[v1][v2] = ng[v2][v1] = EDGE_GRAY;
                    printMessage("Pairwise test: " + labels[v1] + " x " + labels[v2] + " = GRAY");
                }
            }*/

        /* Stage 2: prune blue edges */
//        print(">> Stage 2");

        //Rule 1
        for (int x1 = 0; x1 < numVariables() - 1; x1++) {
            outer_loop:
            for (int y1 = x1 + 1; y1 < numVariables(); y1++) {
                if (ng[x1][y1] != EDGE_BLUE) {
                    continue;
                }
                boolean found = false;
//                printMessage("Searching for " + tetradTest.getVarNames()[x1] +
//                        " x " + tetradTest.getVarNames()[y1] + "...");
                for (int x2 = 0; x2 < numVariables(); x2++) {
                    if (x1 == x2 || y1 == x2 || cv[x1][x2] == EDGE_NONE || cv[y1][x2] ==
                            EDGE_NONE) {
                        continue;
                    }
                    for (int x3 = 0; x3 < numVariables(); x3++) {
                        if (x1 == x3 || x2 == x3 || y1 == x3 ||
                                cv[x1][x3] == EDGE_NONE ||
                                cv[x2][x3] == EDGE_NONE ||
                                cv[y1][x3] == EDGE_NONE ||
                                !partialRule1_1(x1, x2, x3, y1)) {
                            continue;
                        }
                        for (int y2 = 0; y2 < numVariables(); y2++) {
                            if (x1 == y2 || x2 == y2 || x3 == y2 || y1 == y2 ||
                                    cv[x1][y2] == EDGE_NONE ||
                                    cv[x2][y2] == EDGE_NONE ||
                                    cv[x3][y2] == EDGE_NONE ||
                                    cv[y1][y2] == EDGE_NONE ||
                                    !partialRule1_2(x1, x2, y1, y2)) {
                                continue;
                            }
                            for (int y3 = 0; y3 < numVariables(); y3++) {
                                if (x1 == y3 || x2 == y3 || x3 == y3 ||
                                        y1 == y3 || y2 == y3 || cv[x1][y3] ==
                                        EDGE_NONE || cv[x2][y3] == EDGE_NONE ||
                                        cv[x3][y3] == EDGE_NONE ||
                                        cv[y1][y3] == EDGE_NONE ||
                                        cv[y2][y3] == EDGE_NONE ||
                                        !partialRule1_3(x1, y1, y2, y3)) {
                                    continue;
                                }
                                ng[x1][y1] = ng[y1][x1] = EDGE_NONE;
//                                print("(" + labels[x1] + ", " +
//                                        labels[x2] + ", " + labels[x3] + ", " +
//                                        labels[y1] + ", " + labels[y2] + ", " +
//                                        labels[y3] + ") --> RULE1");

                                /*System.out.println(" >> (" + labels[x1] + ", " + labels[y1] + ", " + labels[x2] + ", " + labels[x3] + ")" +
                                                    tetradTest.tetradScore(x1, y1, x2, x3) + " " +
                                                    ((ContinuousTetradTest) tetradTest).prob[0] + " " +
                                                    ((ContinuousTetradTest) tetradTest).prob[1] + " " +
                                                    ((ContinuousTetradTest) tetradTest).prob[2]);
                                System.out.println(" >> (" + labels[x1] + ", " + labels[y1] + ", " + labels[y2] + ", " + labels[y3] + ")" +
                                                    tetradTest.tetradScore(x1, y1, y2, y3)  + " " +
                                                    ((ContinuousTetradTest) tetradTest).prob[0] + " " +
                                                    ((ContinuousTetradTest) tetradTest).prob[1] + " " +
                                                    ((ContinuousTetradTest) tetradTest).prob[2]);
                                System.out.println(" >> alt(" + labels[x1] + ", " + labels[y1] + ", " + labels[y2] + ", " + labels[y3] + ")" +
                                                    tetradTest.oneFactorTest(x1, y1, y2, y3));
                                System.out.println(" >> (" + labels[x1] + ", " + labels[x2] + ", " + labels[y2] + ", " + labels[y1] + ")" +
                                                    tetradTest.tetradHolds(x1, x2, y2, y1) + " " +
                                                    ((ContinuousTetradTest) tetradTest).prob[0]);
                                System.out.println(" >> (" + labels[x1] + ", " + labels[y1] + ", " + labels[x2] + ", " + labels[y2] + ")" +
                                                    tetradTest.tetradHolds(x1, y1, x2, y2) + " " +
                                                    ((ContinuousTetradTest) tetradTest).prob[0]);
                                System.out.println(" >> (" + labels[x1] + ", " + labels[y1] + ", " + labels[y2] + ", " + labels[x2] + ")" +
                                                    tetradTest.tetradHolds(x1, y1, y2, x2) + " " +
                                                    ((ContinuousTetradTest) tetradTest).prob[0]);
                                System.out.println("-------------------");*/

                                found = true;
                                continue outer_loop;
                            }
                        }
                    }
                }
                if (!found) {
//                    printlnMessage();
                }
            }
        }

        System.out.println("Trying RULE 2 now!");
        for (int x1 = 0; x1 < numVariables() - 1; x1++) {
            outer_loop:
            for (int y1 = x1 + 1; y1 < numVariables(); y1++) {
                if (ng[x1][y1] != EDGE_BLUE) {
                    continue;
                }
                boolean found = false;
//                printMessage("Searching for " + tetradTest.getVarNames()[x1] +
//                        " x " + tetradTest.getVarNames()[y1] + "...");
                for (int x2 = 0; x2 < numVariables(); x2++) {
                    if (x1 == x2 || y1 == x2 || cv[x1][x2] == EDGE_NONE || cv[y1][x2] ==
                            EDGE_NONE || ng[x1][x2] == EDGE_GRAY) {
                        continue;
                    }
                    for (int y2 = 0; y2 < numVariables(); y2++) {
                        if (x1 == y2 || x2 == y2 || y1 == y2 ||
                                cv[x1][y2] == EDGE_NONE ||
                                cv[x2][y2] == EDGE_NONE ||
                                cv[y1][y2] == EDGE_NONE ||
                                ng[y1][y2] == EDGE_GRAY ||
                                !partialRule2_1(x1, x2, y1, y2)) {
                            continue;
                        }
                        for (int x3 = 0; x3 < numVariables(); x3++) {
                            if (x1 == x3 || x2 == x3 || y1 == x3 || y2 == x3 ||
                                    ng[x1][x3] == EDGE_GRAY ||
                                    cv[x1][x3] == EDGE_NONE ||
                                    cv[x2][x3] == EDGE_NONE ||
                                    cv[y1][x3] == EDGE_NONE ||
                                    cv[y2][x3] == EDGE_NONE ||
                                    !partialRule2_2(x1, x2, x3, y2)) {
                                continue;
                            }
                            for (int y3 = 0; y3 < numVariables(); y3++) {
                                if (x1 == y3 || x2 == y3 || x3 == y3 ||
                                        y1 == y3 || y2 == y3 || ng[y1][y3] ==
                                        EDGE_GRAY || cv[x1][y3] == EDGE_NONE ||
                                        cv[x2][y3] == EDGE_NONE ||
                                        cv[x3][y3] == EDGE_NONE ||
                                        cv[y1][y3] == EDGE_NONE ||
                                        cv[y2][y3] == EDGE_NONE ||
                                        !partialRule2_3(x2, y1, y2, y3)) {
                                    continue;
                                }
                                ng[x1][y1] = ng[y1][x1] = EDGE_NONE;
//                                print("(" + labels[x1] + ", " +
//                                        labels[x2] + ", " + labels[x3] + ", " +
//                                        labels[y1] + ", " + labels[y2] + ", " +
//                                        labels[y3] + ") --> RULE2");
                                found = true;
                                continue outer_loop;
                            }
                        }
                    }
                }
                if (!found) {
//                    printlnMessage();
                }
            }
        }

        //NOTE: Rule 3 seems to be much less statistically reliable. Right now, I'm deactivating it
        /*System.out.println("Trying RULE 3 now!");
        for (int x1 = 0; x1 < numV - 1; x1++) {
            outer_loop:
            for (int y1 = x1 + 1; y1 < numV; y1++) {
                if (ng[x1][y1] != EDGE_BLUE) {
                    continue;
                }
                boolean found = false;
                printMessage("Searching for " + tetradTest.getVariableNames()[x1] + " x " + tetradTest.getVariableNames()[y1] + "...");
                for (int x2 = 0; x2 < numV; x2++) {
                    if (x1 == x2 || y1 == x2 ||
                            cv[x1][x2] == EDGE_NONE || cv[y1][x2] == EDGE_NONE) {
                        continue;
                    }
                    for (int y2 = 0; y2 < numV; y2++) {
                        if (x1 == y2 || x2 == y2 || y1 == y2 ||
                                cv[x1][y2] == EDGE_NONE || cv[x2][y2] == EDGE_NONE ||
                                cv[y1][y2] == EDGE_NONE) {
                            continue;
                        }
                        for (int x3 = 0; x3 < numV; x3++) {
                            if (x1 == x3 || x2 == x3 || y1 == x3 || y2 == x3 ||
                                    cv[x1][x3] == EDGE_NONE || cv[x2][x3] == EDGE_NONE ||
                                    cv[y1][x3] == EDGE_NONE || cv[y2][x3] == EDGE_NONE) {
                                continue;
                            }
                            for (int y3 = 0; y3 < numV; y3++) {
                                if (x1 == y3 || x2 == y3 || x3 == y3 || y1 == y3 || y2 == y3 ||
                                        cv[x1][y3] == EDGE_NONE || cv[x2][y3] == EDGE_NONE ||
                                        cv[x3][y3] == EDGE_NONE || cv[y1][y3] == EDGE_NONE ||
                                        cv[y2][y3] == EDGE_NONE || !rule3(x1, x2, x3, y1, y2, y3)) {
                                    continue;
                                }
                                ng[x1][y1] = ng[y1][x1] = EDGE_NONE;
                                printlnMessage("(" + labels[x1] + ", " +
                                        labels[x2] + ", " + labels[x3] + ", " +
                                        labels[y1] + ", " + labels[y2] + ", " +
                                        labels[y3] + ") --> RULE3");
                                found = true;
                                continue outer_loop;
                            }
                        }
                    }
                }
                if (!found) {
                    printlnMessage();
                }
            }
        }*/

        for (int i = 0; i < numVariables(); i++) {
            for (int j = 0; j < numVariables(); j++) {
                if (ng[i][j] == EDGE_RED) {
                    ng[i][j] = EDGE_BLUE;
                }
            }
        }

        /* Stage 3: find maximal cliques */
//        print(">> Stage 3.1");
        List clustering = new ArrayList();
        List<int[]> components = findComponents(ng, numVariables(), EDGE_BLUE);
        Iterator<int[]> it = components.iterator();
//        print(">> Stage 3.2");
        while (it.hasNext()) {
            int component[] = it.next();
//            print(">>> Searching for cliques in ");
            printClusterIds(component);
            List<int[]> nextClustering = findMaximalCliques(component, ng);
            clustering.addAll(trimCliqueList(nextClustering));
        }
        //Sort cliques by size: better visualization when printing
        for (int i = 0; i < clustering.size() - 1; i++) {
            int max = 0;
            int max_idx = -1;
            for (int j = i; j < clustering.size(); j++) {
                if (((int[]) clustering.get(j)).length > max) {
                    max = ((int[]) clustering.get(j)).length;
                    max_idx = j;
                }
            }
            Object temp;
            temp = clustering.get(i);
            clustering.set(i, clustering.get(max_idx));
            clustering.set(max_idx, temp);
        }
//        print("**** CLUSTERING OUTPUT: ");
        printClustering(clustering);
//        print(">> Stage 4 - Choosing clusters");
        List<List<Integer>> ids = new ArrayList<List<Integer>>();
        List clusterings = chooseClusterings(clustering, ng, ids, false, cv);
//        print(">> Stage 5 - Finding a pure measurement model");
        List orderedIds = new ArrayList();
        List actualClustering = filterAndOrderClusterings(clusterings, ids,
                orderedIds, ng);
        List finalPureModel = purify(actualClustering, orderedIds,
                forbiddenList);

//        print("\n\n**** FINAL PURE/MARKED MEASUREMENT MODEL: ");
        if (finalPureModel != null) {
            printClustering(finalPureModel);
        } else {
//            print("No model.");
        }
//        printlnMessage();

        //this.tetradTest.setParameter1(oldSig);

        return finalPureModel;
    }

    private List chooseClusterings(List clustering, int ng[][], List<List<Integer>> outputIds,
                                   boolean need3, int cv[][]) {
        List clusterings = new ArrayList();
        boolean marked[] = new boolean[clustering.size()];
        boolean buffer[] = new boolean[this.numVariables()];

        int max;
        if (clustering.size() < 1000) {
            max = clustering.size();
        } else {
            max = 1000;
        }

        boolean compatibility[][] = new boolean[clustering.size()][clustering.size()];
        if (need3) {
            for (int i = 0; i < clustering.size() - 1; i++) {
                for (int j = i + 1; j < clustering.size(); j++) {
                    compatibility[i][j] = compatibility[j][i] = compatibleClusters(
                            (int[]) clustering.get(i),
                            (int[]) clustering.get(j), cv);
                }
            }
        }

        //Ideally, we should find all maximum cliques among "cluster nodes".
        //Heuristic: greedily build a set of clusters starting from each cluster.
        System.out.println("Total number of clusters: " + clustering.size());
        for (int i = 0; i < max; i++) {
            //System.out.println("Step " + i);
            List<Integer> nextIds = new ArrayList<Integer>();
            List newClustering = new ArrayList();
            nextIds.add(new Integer(i));
            newClustering.add(clustering.get(i));
            for (int j = 0; j < clustering.size(); j++) {
                marked[j] = false;
            }
            marked[i] = true;
            //System.out.println("-- Seed ");
            //printClusterNames((int[]) mimClustering.get(i));
            int bestChoice;
            double bestScore = ((int[]) clustering.get(i)).length;
            do {
                bestChoice = -1;
                next_choice:
                for (int j = 0; j < clustering.size(); j++) {
                    if (marked[j]) {
                        continue;
                    }
                    for (int k = 0; k < newClustering.size(); k++) {
                        if (need3 &&
                                !compatibility[j][clustering.indexOf(
                                        newClustering.get(k))]) {
                            marked[j] = true;
                            continue next_choice;
                        }
                    }
                    //System.out.println("-- Evaluating ");
                    //printClusterNames((int[]) mimClustering.get(j));
                    newClustering.add(clustering.get(j));
                    int localScore = scoreClustering(newClustering, ng, buffer);
                    //System.out.println("Score = " + localScore);
                    newClustering.remove(clustering.get(j));
                    if (localScore >= bestScore) {
                        bestChoice = j;
                        bestScore = localScore;
                    }
                }
                if (bestChoice != -1) {
                    marked[bestChoice] = true;
                    newClustering.add(clustering.get(bestChoice));
                    nextIds.add(new Integer(bestChoice));
                }
            } while (bestChoice > -1);
            //System.out.println("-- New candidate mimClustering");
            //printClustering(newClustering);
            //System.out.println("-- Best score = " + bestScore);
            //System.out.println();
            //Avoid repeated clusters
            if (isNewClustering(clusterings, newClustering)) {
                clusterings.add(newClustering);
                outputIds.add(nextIds);
            }
        }
        return clusterings;
    }

    /**
     * Check if newClustering is contained in clusterings.
     */
    private boolean isNewClustering(List clusterings, List newClustering) {
        nextClustering:
        for (Iterator it = clusterings.iterator(); it.hasNext(); ) {
            List nextClustering = (List) it.next();
            nextOldCluster:
            for (Iterator it2 = nextClustering.iterator(); it2.hasNext(); ) {
                int cluster[] = (int[]) it2.next();
                nextNewCluster:
                for (Iterator it3 = newClustering.iterator(); it3.hasNext(); ) {
                    int newCluster[] = (int[]) it3.next();
                    if (cluster.length == newCluster.length) {
                        nextElement:
                        for (int i = 0; i < cluster.length; i++) {
                            for (int j = 0; j < newCluster.length; j++) {
                                if (cluster[i] == newCluster[j]) {
                                    continue nextElement;
                                }
                            }
                            continue nextNewCluster;
                        }
                        continue nextOldCluster;
                    }
                }
                continue nextClustering;
            }
            return false;
        }
        return true;
    }

//    /**
//     * Score a pair of clusters according to the non-overlapping nodes that are not linked by any edge in ng.
//     */
//    int clusterPairwiseScoring(int cluster1[], int cluster2[], int ng[][],
//                               boolean usedNodes[], boolean toRemove[]) {
//        int score = cluster1.length + cluster2.length;
//        for (int ci = 0; ci < cluster1.length; ci++) {
//            if (toRemove[cluster1[ci]]) {
//                score--;    //do not count it, since it will be removed anyway
//            } else if (usedNodes[cluster1[ci]]) {
//                score -= 2;
//            }
//            //if we choose this cluster, this indicator will be removed, as well its other instance used before
//            //Notice that we have to guarantee that this node was not removed before (toRemove == false)
//        }
//        for (int ci = 0; ci < cluster2.length; ci++) {
//            if (toRemove[cluster2[ci]]) {
//                score--;
//            } else if (usedNodes[cluster2[ci]]) {
//                score -= 2;
//            }
//        }
//
//        for (int ci = 0; ci < cluster1.length; ci++) {
//            if (usedNodes[cluster1[ci]]) {
//                continue;
//            }
//            for (int cj = 0; cj < cluster2.length; cj++) {
//                if (usedNodes[cluster2[cj]]) {
//                    continue;
//                }
//                if (cluster1[ci] == cluster2[cj]) {
//                    score -= 2;  //both instances will have to be removed
//                }
//                if (ng[cluster1[ci]][cluster2[cj]] != EDGE_NONE) {
//                    score--;     //one of the two might have to be removed
//                }
//            }
//        }
//        return score;
//    }

//    int extraClusterScoring(int cluster1[], int cluster2[], int ng[][],
//                            boolean usedNodes[], boolean toRemove[]) {
//        int score = cluster1.length;
//        for (int ci = 0; ci < cluster1.length; ci++) {
//            if (toRemove[cluster1[ci]]) {
//                score--;
//            } else if (usedNodes[cluster1[ci]]) {
//                score -= 2;
//            }
//        }
//
//        for (int ci = 0; ci < cluster1.length; ci++) {
//            if (usedNodes[cluster1[ci]]) {
//                continue;
//            }
//            for (int cj = 0; cj < cluster2.length; cj++) {
//                if (usedNodes[cluster2[cj]]) {
//                    continue;
//                }
//                if (ng[cluster1[ci]][cluster2[cj]] != EDGE_NONE) {
//                    score -= 1;
//                }
//            }
//        }
//        return score;
//    }

//    void updateContribution(int cluster1[], int cluster2[],
//                            boolean contribution[], int ng[][], boolean usedNodes[],
//                            boolean toRemove[]) {
//        for (int ci = 0; ci < cluster1.length; ci++) {
//            if (toRemove[cluster1[ci]]) {
//                contribution[ci] = false;
//            } else if (usedNodes[cluster1[ci]]) {
//                contribution[ci] = false;
//            }
//        }
//
//        for (int ci = 0; ci < cluster1.length; ci++) {
//            if (usedNodes[cluster1[ci]]) {
//                continue;
//            }
//            for (int cj = 0; cj < cluster2.length; cj++) {
//                if (usedNodes[cluster2[cj]]) {
//                    continue;
//                }
//                if (ng[cluster1[ci]][cluster2[cj]] != EDGE_NONE) {
//                    contribution[ci] = false;
//                }
//            }
//        }
//    }

//    /**
//     * Mark all elements that show up in 'newCluster' and also in some element in 'clustering'. Arrays 'toRemove' and
//     * 'usedNodes' will be updated.
//     *
//     * @param clustering
//     * @param newCluster
//     * @param toRemove
//     * @param usedNodes
//     */
//
//    void updateOverlap(List clustering, int newCluster[], boolean toRemove[],
//                       boolean usedNodes[]) {
//        for (int i = 0; i < clustering.size(); i++) {
//            int nextCluster[] = (int[]) clustering.get(i);
//            for (int ci = 0; ci < nextCluster.length; ci++) {
//                if (toRemove[nextCluster[ci]]) {
//                    continue;
//                }
//                for (int cj = 0; cj < newCluster.length; cj++) {
//                    if (nextCluster[ci] == newCluster[cj]) {
//                        toRemove[nextCluster[ci]] = true;
//                    }
//                }
//            }
//        }
//        for (int cj = 0; cj < newCluster.length; cj++) {
//            usedNodes[newCluster[cj]] = true;
//        }
//    }

//    /**
//     * Remove from any element of 'clustering' all nodes that are marked to be removed.
//     *
//     * @param clustering
//     * @param toRemove
//     */
//
//    void removeNodes(List clustering, boolean toRemove[]) {
//        List removeList = new ArrayList();
//        List addList = new ArrayList();
//        for (int i = 0; i < clustering.size(); i++) {
//            int nextCluster[] = (int[]) clustering.get(i);
//            int toCopy = 0;
//            for (int j = 0; j < nextCluster.length; j++) {
//                if (!toRemove[nextCluster[j]]) {
//                    toCopy++;
//                }
//            }
//            if (toCopy != nextCluster.length) {
//                removeList.add(nextCluster);
//                if (toCopy > 0) {
//                    int newCluster[] = new int[toCopy];
//                    int pos = 0;
//                    for (int j = 0; j < nextCluster.length; j++) {
//                        if (!toRemove[nextCluster[j]]) {
//                            newCluster[pos++] = nextCluster[j];
//                        }
//                    }
//                    addList.add(newCluster);
//                }
//            }
//        }
//        clustering.removeAll(removeList);
//        clustering.addAll(addList);
//    }

    //*********************************************************
    // * PROCEDURES FOR PURIFICATION
    // *********************************************************/

    /**
     * This implementation uses the Purify class.
     */

    private List<int[]> purify(List actualClusterings, List clusterIds,
                               List<Set<String>> forbiddenList) {
//        if (true) return (List<int[]>) actualClusterings;


//        printlnMessage();
//        print("** PURIFY: total number of mimClustering candidates = " +
//                actualClusterings.size());

        //Try to find a solution. Maximum number of trials: 10
        for (int i = 0; i < actualClusterings.size() && i < 10; i++) {
            List partition = (List) actualClusterings.get(i);

//            printMessage("Trying to purify");
            printLatentClique((int[]) clusterIds.get(i),
                    clustersize(partition));
//            print("Remaining: " + (actualClusterings.size() - i - 1));

            Clusters clustering = new Clusters();
            int clusterId = 0;
            Iterator it = partition.iterator();
            printClustering(partition);
            while (it.hasNext()) {
                int codes[] = (int[]) it.next();
                for (int k = 0; k < codes.length; k++) {
                    String var = tetradTest.getVarNames()[codes[k]];
                    clustering.addToCluster(clusterId, var);
                }
                clusterId++;
            }

            List<List<Node>> partition2 = new ArrayList<List<Node>>();

            for (int j = 0; j < partition.size(); j++) {
                int[] clusterIndices = (int[]) partition.get(j);
                List<Node> cluster = new ArrayList<Node>();

                for (int k = 0; k < clusterIndices.length; k++) {
                    cluster.add(tetradTest.getVariables().get(clusterIndices[k]));
                }

                partition2.add(cluster);
            }

            System.out.println("Partition 2 = " + partition2);

//            IPurify purify = new PurifyTetradBased3(tetradTest);
//            List<List<Node>> newPartition = purify.purify(partition2);
//
//            System.out.println("Purify result: " + newPartition);
//
//            return convertListToInt(newPartition);

            Purify purifier = new Purify(tetradTest, clustering);
//            purifier.setConstraintSearchVariation(constraintSearchVariation);
            purifier.setForbiddenList(forbiddenList);
            purifier.setOutputMessage(this.outputMessage);
            Graph solutionGraph = purifier.search();
//            if (!(tetradTest instanceof DiscreteTetradTest)) {
//                ((ContinuousTetradTest) tetradTest).setTestType(oldPurifyTest);
//            }
            if (solutionGraph != null && solutionGraph.getNodes().size() > 1) {
                List clusteringOutput = convertGraphToList(solutionGraph);
                return clusteringOutput;
            } else if (actualClusterings.size() > 1) {
                rebuildClusteringList(actualClusterings, i, clusterIds);
            }
            return new ArrayList<int[]>();

        }

        return new ArrayList<int[]>();
    }

    private List<int[]> convertListToInt(List<List<Node>> partition) {
        List<Node> nodes = tetradTest.getVariables();
        List<int[]> _partition = new ArrayList<int[]>();

        for (int i = 0; i < partition.size(); i++) {
            List<Node> cluster = partition.get(i);
            int[] _cluster = new int[cluster.size()];

            for (int j = 0; j < cluster.size(); j++) {
                for (int k = 0; k < nodes.size(); k++) {
                    if (nodes.get(k).getName().equals(cluster.get(j).getName())) {
                        _cluster[j] = k;
                        break;
                    }
                }
            }

            _partition.add(_cluster);
        }

        return _partition;
    }

    private List<List<Node>> convertIntToList(List<int[]> partition) {
        List<Node> nodes = tetradTest.getVariables();
        List<List<Node>> _partition = new ArrayList<List<Node>>();

        for (int i = 0; i < partition.size(); i++) {
            int[] cluster = partition.get(i);
            List<Node> _cluster = new ArrayList<Node>();

            for (int j = 0; j < cluster.length; j++) {
                _cluster.add(nodes.get(cluster[j]));
            }

            _partition.add(_cluster);
        }

        return _partition;
    }

    /**
     * Given a set of clusters, substitute it by n sets of size n-1 in the cliqueList. This is a heuristic for resusing
     * a set of latents that failed purification even though they form a clique. For a set of n latents, we generate n
     * sets where in each one we copy n-1 elements of the original set. Needless to say, this has an exponential
     * growth.
     */

    private void rebuildClusteringList(List clusterings, int position,
                                       List clusterIds) {
        List currentClustering = (List) clusterings.get(position);
        if (currentClustering.size() < 2) {
            return;
        }
        boolean found = false;
//        print("* Substituting invalid solution for new one");
        int currentIds[] = (int[]) clusterIds.get(position);
        for (int j = position + 1; j < clusterings.size() && !found; j++) {
            if (((List) clusterings.get(j)).size() < currentClustering.size()) {
                found = true;
                for (int i = 0; i < currentClustering.size(); i++) {
                    List newClustering = new ArrayList();
                    int newIds[] = new int[currentClustering.size() - 1];
                    for (int k = 0; k < currentClustering.size(); k++) {
                        if (i != k) {
                            newClustering.add(currentClustering.get(k));
                            if (k > i) {
                                newIds[k - 1] = currentIds[k];
                            } else {
                                newIds[k] = currentIds[k];
                            }
                        }
                    }
                    clusterings.add(j, newClustering);
                    clusterIds.add(j, newIds);
                }
            }
        }
        if (!found) {
            for (int i = 0; i < currentClustering.size(); i++) {
                List newClustering = new ArrayList();
                int newIds[] = new int[currentClustering.size() - 1];
                for (int k = 0; k < currentClustering.size(); k++) {
                    if (i != k) {
                        newClustering.add(currentClustering.get(k));
                        if (k > i) {
                            newIds[k - 1] = currentIds[k];
                        } else {
                            newIds[k] = currentIds[k];
                        }
                    }
                }
                clusterings.add(newClustering);
                clusterIds.add(newIds);
            }
        }
    }

    /**
     * Data storage
     */
    public ICovarianceMatrix getCovarianceMatrix() {
        return covarianceMatrix;
    }

    public int numVariables() {
        return numVariables;
    }

    public IndependenceTest getIndependenceTest() {
        return independenceTest;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    public boolean isVerbose() {
        return verbose;
    }
}





