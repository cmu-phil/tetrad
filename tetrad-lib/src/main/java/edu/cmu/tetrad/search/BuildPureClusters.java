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
 * BuildPureClusters is an implementation of the automated clustering and purification methods described on the report
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
    private int[] labels;
    private boolean scoreTestMode;

    /**
     * Color code for the different edges that show up during search
     */
    final int EDGE_NONE = 0;
    final int EDGE_BLACK = 1;
    final int EDGE_GRAY = 2;
    final int EDGE_BLUE = 3;
    final int EDGE_YELLOW = 4;
    final int EDGE_RED = 4;
    final int MAX_CLIQUE_TRIALS = 50;

    private TetradTest tetradTest;
    private IndependenceTest independenceTest;
    private DataSet dataSet;

    /**
     * The logger for this class. The config needs to be set.
     */
    private final TetradLogger logger = TetradLogger.getInstance();
    private double alpha;
    private boolean verbose;

    //*********************************************************
    // * INITIALIZATION
    // *********************************************************/

    /**
     * Constructor BuildPureClusters
     */
    public BuildPureClusters(ICovarianceMatrix covarianceMatrix, double alpha,
                             TestType sigTestType) {
        if (covarianceMatrix == null) {
            throw new IllegalArgumentException("Covariance matrix cannot be null.");
        }

        this.covarianceMatrix = covarianceMatrix;
        initAlgorithm(alpha, sigTestType);
    }

    public BuildPureClusters(CovarianceMatrix covarianceMatrix, double alpha,
                             TestType sigTestType) {
        if (covarianceMatrix == null) {
            throw new IllegalArgumentException("Covariance matrix cannot be null.");
        }

        this.covarianceMatrix = covarianceMatrix;
        initAlgorithm(alpha, sigTestType);
    }

    public BuildPureClusters(DataSet dataSet, double alpha, TestType sigTestType) {
        if (dataSet.isContinuous()) {
            this.dataSet = dataSet;
            this.covarianceMatrix = new CovarianceMatrix(dataSet);
            initAlgorithm(alpha, sigTestType);
        } else if (dataSet.isDiscrete()) {
            throw new IllegalArgumentException("Discrete data is not supported " +
                    "for this search.");
        }
    }

    private void initAlgorithm(double alpha, TestType sigTestType) {

        // Check for missing values.
        if (getCovarianceMatrix() != null && DataUtils.containsMissingValue(getCovarianceMatrix().getMatrix())) {
            throw new IllegalArgumentException(
                    "Please remove or impute missing values first.");
        }

        this.alpha = alpha;

        this.outputMessage = true;
        this.sigTestType = sigTestType;
        this.scoreTestMode = (this.sigTestType == TestType.DISCRETE ||
                this.sigTestType == TestType.GAUSSIAN_FACTOR);

        if (sigTestType == TestType.DISCRETE) {
            numVariables = dataSet.getNumColumns();
            independenceTest = new IndTestGSquare(dataSet, alpha);
            tetradTest = new DiscreteTetradTest(dataSet, alpha);
        } else {
            assert getCovarianceMatrix() != null;
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

            if (dataSet != null) {
                tetradTest = new ContinuousTetradTest(dataSet, type, alpha);
            } else {
                tetradTest = new ContinuousTetradTest(getCovarianceMatrix(), type, alpha);
            }
        }
        labels = new int[numVariables()];
        for (int i = 0; i < numVariables(); i++) {
            labels[i] = i + 1;
        }
    }

    //**
    // * ****************************************************** SEARCH INTERFACE
    // * *******************************************************
    // */

    /**
     * @return the result search graph, or null if there is no model.
     */
    public Graph search() {
        long start = System.currentTimeMillis();

        TetradLogger.getInstance().log("info", "BPC alpha = " + alpha + " test = " + sigTestType);

        List<Integer[]> clustering = findMeasurementPattern();

        // Remove clusters of size < 3.
        clustering.removeIf(cluster -> cluster.length < 3);

        List<Node> variables = tetradTest.getVariables();

        Set<Set<Integer>> clusters = new HashSet<>();

        for (Integer[] _c : clustering) {
            Set<Integer> cluster = new HashSet<>(Arrays.asList(_c));
            clusters.add(cluster);
        }

        ClusterUtils.logClusters(clusters, variables);

        Graph graph = convertSearchGraph(clustering);

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

        List<Node> latents = new ArrayList<>();
        for (int i = 0; i < clusters.size(); i++) {
            Node latent = new GraphNode(ClusterUtils.LATENT_PREFIX + (i + 1));
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
    }

    /**
     * ****************************************************** STATISTICAL TESTS *******************************************************
     */

    private boolean clusteredPartial1(int v1, int v2, int v3, int v4) {
        if (this.scoreTestMode) {
            return !tetradTest.oneFactorTest(v1, v2, v3, v4);
        } else {
            return !tetradTest.tetradScore3(v1, v2, v3, v4);
        }
    }

    private boolean validClusterPairPartial1(int v1, int v2, int v3, int v4,
                                             int[][] cv) {
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

    private boolean clusteredPartial2(int v1, int v2, int v3, int v4, int v5) {
        if (this.scoreTestMode) {
            return !tetradTest.oneFactorTest(v1, v2, v3, v5) ||
                    tetradTest.oneFactorTest(v1, v2, v3, v4, v5) ||
                    !tetradTest.twoFactorTest(v1, v2, v3, v4, v5);
        } else {
            return !tetradTest.tetradScore3(v1, v2, v3, v5) ||

                    !tetradTest.tetradScore1(v1, v2, v4, v5) ||
                    !tetradTest.tetradScore1(v2, v3, v4, v5) ||
                    !tetradTest.tetradScore1(v1, v3, v4, v5);
        }
    }

    private boolean validClusterPairPartial2(int v1, int v2, int v3, int v5,
                                             int[][] cv) {
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
            return (test1 && test2) || (test1 && test3) || (test2 && test3);
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
                                             int v5, int v6, int[][] cv) {
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
            return (test1 && test2) || (test1 && test3) || (test2 && test3);
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

        if (getCovarianceMatrix() != null) {
            List<Node> variables = getCovarianceMatrix().getVariables();
            return getIndependenceTest().isIndependent(variables.get(v1),
                    variables.get(v2));

        } else {
            return getIndependenceTest().isIndependent(dataSet.getVariable(v1),
                    dataSet.getVariable(v2));

        }
    }

    private void vanishingPartialCorr() {
        //NOTE: vanishingPartialCorr is not being used. This implementation of BuildPureClusters is
        // assuming no conditional d-sep holds in the population.

    }

    /**
     * ****************************************************** DEBUG UTILITIES *******************************************************
     */

    private void printClustering(List<Integer[]> clustering) {
        for (Integer[] cluster : clustering) {
            printClusterNames(cluster);
        }
    }

    private void printClusterIds(Integer[] c) {
        int[] sorted = new int[c.length];
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
    }

    private void printClusterNames(Integer[] c) {
        String[] sorted = new String[c.length];
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
    }

    private void printLatentClique(int[] latents) {
        int[] sorted = new int[latents.length];
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
    }


    //*********************************************************
    // * GRAPH ALGORITHMIC TOOLS
    // *********************************************************/

    /**
     * Find components of a graph. Note: naive implementation, but it works. After all, it will still run much faster
     * than Stage 2 of the FindMeasurementPattern algorithm.
     */

    private List<Integer[]> findComponents(int[][] graph, int size) {
        boolean[] marked = new boolean[size];
        for (int i = 0; i < size; i++) {
            marked[i] = false;
        }
        int numMarked = 0;
        List<Integer[]> output = new ArrayList<>();

        int[] tempComponent = new int[size];
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
                    for (int j = 0; j < sizeTemp; j++) {
                        if (graph[i][tempComponent[j]] == 3) {
                            inComponent = true;
                            break;
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
                Integer[] newPartition = new Integer[sizeTemp];
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

    private List<Integer[]> findMaximalCliques(Integer[] elements, int[][] ng) {
        boolean[][] connected = new boolean[this.numVariables()][this.numVariables()];
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
        int[] numCalls = new int[1];
        int[] c = new int[1];
        List<Integer[]> output = new ArrayList<>();
        Integer[] compsub = new Integer[elements.length];
        int[] old = new int[elements.length];
        for (int i = 0; i < elements.length; i++) {
            old[i] = elements[i];
        }
        findMaximalCliquesOperator(numCalls, output, connected,
                compsub, c, old, 0, elements.length);
        return output;
    }

    private void findMaximalCliquesOperator(int[] numCalls, List<Integer[]> output, boolean[][]
            connected, Integer[] compsub, int[] c, int[] old, int ne, int ce) {
        if (numCalls[0] > MAX_CLIQUE_TRIALS) {
            return;
        }
        int[] newA = new int[ce];
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
                Integer[] clique = new Integer[c[0]];
                System.arraycopy(compsub, 0, clique, 0, c[0]);
                output.add(clique);
            } else if (newne < newce) {
                numCalls[0]++;
                findMaximalCliquesOperator(numCalls, output,
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

    private boolean cliqueContained(Integer[] newClique, int size, List<Integer[]> clustering) {
        for (Integer[] next : clustering) {
            if (size > next.length) {
                continue;
            }
            boolean found = true;
            for (int i = 0; i < size && found; i++) {
                found = false;
                for (Integer integer : next) {
                    if (Objects.equals(newClique[i], integer)) {
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
    private List trimCliqueList(List<Integer[]> cliqueList) {
        List trimmed = new ArrayList();
        List<Integer[]> cliqueCopy = new ArrayList<Integer[]>(cliqueList);

        for (Integer[] cluster : cliqueList) {
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
        for (Object o : cluster) {
            int[] next = (int[]) o;
            total += next.length;
        }
        return total;
    }

    private int clustersize3(List cluster) {
        int total = 0;
        for (Object o : cluster) {
            int[] next = (int[]) o;
            if (next.length > 2) {
                total += next.length;
            }
        }
        return total;
    }

    private void sortClusterings(int start, int end, List clusterings,
                                 int[] criterion) {
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

    private int scoreClustering(List clustering, boolean[] buffer) {
        int score = 0;
        Arrays.fill(buffer, true);

        //First filter: remove all overlaps
        for (Object o : clustering) {
            int[] currentCluster = (int[]) o;
            next_item:
            for (int k : currentCluster) {
                if (!buffer[k]) {
                    continue;
                }
                for (Object value : clustering) {
                    int[] nextCluster = (int[]) value;
                    if (nextCluster == currentCluster) {
                        continue;
                    }
                    for (int i : nextCluster) {
                        if (k == i) {
                            buffer[k] = false;
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
        for (Object o : clustering) {
            int[] currentCluster = (int[]) o;
            localScore = 0;
            for (int k : currentCluster) {
                if (!buffer[k]) {
                    continue;
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
                                           List<List<Integer>> baseListOfIds, List<int[]> clusteringIds, int[][] ng) {

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
            List<Integer> usedIds = new ArrayList<>();

            for (int j = 0; j < baseClustering.size(); j++) {
                int[] currentCluster = (int[]) baseClustering.get(j);
                Integer currentId = baseIds.get(j);
                int[] draftArea = new int[currentCluster.length];
                int draftCount = 0;
                next_item:
                for (int value : currentCluster) {
                    for (int k = 0; k < baseClustering.size(); k++) {
                        if (k == j) {
                            continue;
                        }
                        int[] nextCluster = (int[]) baseClustering.get(k);
                        for (int item : nextCluster) {
                            if (value == item) {
                                continue next_item;
                            }
                        }
                    }
                    draftArea[draftCount++] = value;
                }
                if (draftCount > 1) {
                    //Only clusters with at least two indicators can be added
                    int[] newCluster = new int[draftCount];
                    System.arraycopy(draftArea, 0, newCluster, 0, draftCount);
                    newClustering.add(newCluster);
                    usedIds.add(currentId);
                }
            }

            System.out.println("* Filtered mimClustering 1");
            printClustering(newClustering);

            //Second filter: remove nodes that are linked by an edge in ng but are in different clusters
            //(i.e., they were not shown to belong to different clusters)
            //Current criterion: count the number of invalid relations each node participates, greedily
            //remove nodes till none of these relations hold anymore
            boolean[][] impurities = new boolean[this.numVariables()][this.numVariables()];
            for (int j = 0; j < newClustering.size() - 1; j++) {
                int[] currentCluster = (int[]) newClustering.get(j);
                for (int jj = j + 1; jj < currentCluster.length; jj++) {
                    for (int k = 0; k < newClustering.size(); k++) {
                        if (k == j) {
                            continue;
                        }
                        int[] nextCluster = (int[]) newClustering.get(k);
                        for (int value : nextCluster) {
                            impurities[currentCluster[jj]][value] =
                                    ng[currentCluster[jj]][value] !=
                                            EDGE_NONE;
                            impurities[value][currentCluster[jj]] =
                                    impurities[currentCluster[jj]][value];
                        }
                    }
                }
            }
            List newClustering2 = removeMarkedImpurities(newClustering,
                    impurities);
            List finalNewClustering = new ArrayList();
            List<Integer> finalUsedIds = new ArrayList<>();
            for (int j = 0; j < newClustering2.size(); j++) {
                if (((int[]) newClustering2.get(j)).length > 0) {
                    finalNewClustering.add(newClustering2.get(j));
                    finalUsedIds.add(usedIds.get(j));
                }
            }
            if (finalNewClustering.size() > 0) {
                listOfClusterings.add(finalNewClustering);
                int[] usedIdsArray = new int[finalUsedIds.size()];
                for (int j = 0; j < finalUsedIds.size(); j++) {
                    usedIdsArray[j] = finalUsedIds.get(j);
                }
                clusteringIds.add(usedIdsArray);
                System.out.println("* Filtered mimClustering 2");
                printClustering(finalNewClustering);
                System.out.print("* ID/Size: ");
                printLatentClique(usedIdsArray
                );
                System.out.println();
            }

        }

        //Now, order clusterings according to the number of latents with at least three children.
        //The second criterion is the total number of their indicators.
        int[] numIndicators = new int[listOfClusterings.size()];
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

        return listOfClusterings;
    }

    private List removeMarkedImpurities(List partition, boolean[][] impurities) {
        System.out.println("sizecluster = " + clustersize(partition));
        int[][] elements = new int[clustersize(partition)][3];
        int[] partitionCount = new int[partition.size()];
        int countElements = 0;
        for (int p = 0; p < partition.size(); p++) {
            int[] next = (int[]) partition.get(p);
            partitionCount[p] = 0;
            for (int j : next) {
                elements[countElements][0] = j; // global ID
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
        boolean[] eliminated = new boolean[this.numVariables()];
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
        for (Object o : partition) {
            int[] next = (int[]) o;
            int[] draftArea = new int[next.length];
            int draftCount = 0;
            for (int k : next) {
                for (int[] element : elements) {
                    if (element[0] == k &&
                            !eliminated[element[0]]) {
                        draftArea[draftCount++] = k;
                    }
                }
            }
            if (draftCount > 0) {
                int[] realCluster = new int[draftCount];
                System.arraycopy(draftArea, 0, realCluster, 0, draftCount);
                solution.add(realCluster);
            }
        }

        return solution;
    }

    private void sortByImpurityPriority(int[][] elements, int[] partitionCount,
                                        boolean[] eliminated) {
        int[] temp = new int[3];

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

    private void swapElements(int[][] elements, int i, int j, int[] buffer) {
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

    private boolean validSolution(int[][] elements, boolean[] eliminated) {
        for (int[] element : elements) {
            if (!eliminated[element[0]] && element[2] > 0) {
                return false;
            }
        }
        return true;
    }


    /**
     * ****************************************************** MAIN ALGORITHM: INITIALIZATION
     * *******************************************************
     */

    private List initialMeasurementPattern(int[][] ng, int[][] cv) {

        boolean[][] notYellow = new boolean[numVariables()][numVariables()];

        // Stage 1: identify (partially) uncorrelated and impure pairs
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
                    vanishingPartialCorr();
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
                }
            }
        }

        // Stage 2: prune blue edges, find yellow ones
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
                                clusteredPartial1(v1, v3, v5, v2)) {
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
                                    clusteredPartial2(v1, v3, v5, v2, v4)) {
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
                                }
                            }
                        }
                    }
                }
                if (notYellow[v1][v2]) {
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
                                    clusteredPartial1(v1, v2, v3, v4)) {
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
                                        clusteredPartial2(v1, v2, v3, v4,
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
                                    }
                                }
                            }
                        }
                    }
                }
                if (notFound) {
                    ng[v1][v2] = ng[v2][v1] = EDGE_YELLOW;
                }

            }
        }

        // Stage 3: find maximal cliques
        List<Integer[]> components = findComponents(ng, numVariables());
        List clustering = new ArrayList();
        for (Integer[] component : components) {
            printClusterIds(component);
            List<Integer[]> nextClustering = findMaximalCliques(component, ng);
            clustering.addAll(trimCliqueList(nextClustering));
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

        List<Integer[]> individualOneFactors = individualPurification(clustering);
        printClustering(individualOneFactors);
        clustering = individualOneFactors;
        List<List<Integer>> ids = new ArrayList<>();
        List clusterings = chooseClusterings(clustering, ids, true, cv);
        List<int[]> orderedIds = new ArrayList<>();
        List actualClustering = filterAndOrderClusterings(clusterings, ids,
                orderedIds, ng);
        return purify(actualClustering, orderedIds);
    }

    private List<Integer[]> individualPurification(List<Integer[]> clustering) {
        boolean oldOutputMessage = this.outputMessage;
        List<Integer[]> purified = new ArrayList<Integer[]>();
        int[] ids = {1};
        for (Integer[] integers : clustering) {
            this.outputMessage = false;
            if (integers.length <= 4) {
                this.outputMessage = oldOutputMessage;
                purified.add(integers);
                continue;
            }
            List dummyClusterings = new ArrayList();
            List dummyClustering = new ArrayList();
            dummyClustering.add(integers);
            dummyClusterings.add(dummyClustering);
            List<int[]> dummyIds = new ArrayList<>();
            dummyIds.add(ids);
            List<Integer[]> purification = purify(dummyClusterings, dummyIds);
            if (purification.size() > 0) {
                purified.add(purification.get(0));
            } else {
                Integer[] newFakeCluster = new Integer[4];
                System.arraycopy(integers, 0, newFakeCluster, 0, 4);
                purified.add(newFakeCluster);
            }
            this.outputMessage = oldOutputMessage;
        }
        return purified;
    }

    private boolean compatibleClusters(Integer[] cluster1, Integer[] cluster2, int[][] cv) {
        HashSet<Integer> allNodes = new HashSet<>();

        Collections.addAll(allNodes, cluster1);

        Collections.addAll(allNodes, cluster2);

        if (allNodes.size() < cluster1.length + cluster2.length) return false;


        int cset1 = cluster1.length;
        int cset2 = cluster2.length;
        for (int o1 = 0; o1 < cset1 - 2; o1++) {
            for (int o2 = o1 + 1; o2 < cset1 - 1; o2++) {
                for (int o3 = o2 + 1; o3 < cset1; o3++) {
                    for (int o4 = 0; o4 < cset2 - 2; o4++) {
                        if (!validClusterPairPartial1(cluster1[o1],
                                cluster1[o2], cluster1[o3], cluster2[o4], cv)) {
                            continue;
                        }
                        for (int o5 = o4 + 1; o5 < cset2 - 1; o5++) {
                            if (!validClusterPairPartial2(cluster1[o1],
                                    cluster1[o2], cluster1[o3], cluster2[o5],
                                    cv)) {
                                continue;
                            }
                            for (int o6 = o5 + 1; o6 < cset2; o6++) {
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

    private List<Integer[]> findMeasurementPattern() {
        int[][] ng = new int[numVariables()][numVariables()];
        int[][] cv = new int[numVariables()][numVariables()];
        boolean[] selected = new boolean[numVariables()];

        for (int i = 0; i < numVariables(); i++) {
            selected[i] = false;
        }

        List initialClustering = initialMeasurementPattern(ng, cv);
//        print("Initial mimClustering:");
        printClustering(initialClustering);
        List<Set<String>> forbiddenList = new ArrayList<Set<String>>();
        for (int c1 = 0; c1 < initialClustering.size(); c1++) {
            int[] nextCluster = (int[]) initialClustering.get(c1);
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
                int[] nextCluster2 = (int[]) initialClustering.get(c2);
                for (int k : nextCluster) {
                    for (int i : nextCluster2) {
                        Set<String> nextPair = new HashSet<String>();
                        nextPair.add(
                                this.tetradTest.getVarNames()[k]);
                        nextPair.add(
                                this.tetradTest.getVarNames()[i]);
                        forbiddenList.add(nextPair);
                    }
                }
            }

        }

        // Stage 1: identify (partially) uncorrelated and impure pairs
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

        // Stage 2: prune blue edges

        //Rule 1
        for (int x1 = 0; x1 < numVariables() - 1; x1++) {
            outer_loop:
            for (int y1 = x1 + 1; y1 < numVariables(); y1++) {
                if (ng[x1][y1] != EDGE_BLUE) {
                    continue;
                }
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
                                continue outer_loop;
                            }
                        }
                    }
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
                                continue outer_loop;
                            }
                        }
                    }
                }
            }
        }

        for (int i = 0; i < numVariables(); i++) {
            for (int j = 0; j < numVariables(); j++) {
                if (ng[i][j] == EDGE_RED) {
                    ng[i][j] = EDGE_BLUE;
                }
            }
        }

        // Stage 3: find maximal cliques
        List clustering = new ArrayList();
        List<Integer[]> components = findComponents(ng, numVariables());
        for (Integer[] component : components) {
            printClusterIds(component);
            List<Integer[]> nextClustering = findMaximalCliques(component, ng);
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
        printClustering(clustering);
        List<List<Integer>> ids = new ArrayList<List<Integer>>();
        List clusterings = chooseClusterings(clustering, ids, false, cv);
        List<int[]> orderedIds = new ArrayList<int[]>();
        List actualClustering = filterAndOrderClusterings(clusterings, ids,
                orderedIds, ng);
        List<Integer[]> finalPureModel = purify(actualClustering, orderedIds
        );

        if (finalPureModel != null) {
            printClustering(finalPureModel);
        }

        return finalPureModel;
    }

    private List chooseClusterings(List clustering, List<List<Integer>> outputIds,
                                   boolean need3, int[][] cv) {
        List clusterings = new ArrayList();
        boolean[] marked = new boolean[clustering.size()];
        boolean[] buffer = new boolean[this.numVariables()];

        int max = Math.min(clustering.size(), 1000);

        boolean[][] compatibility = new boolean[clustering.size()][clustering.size()];
        if (need3) {
            for (int i = 0; i < clustering.size() - 1; i++) {
                for (int j = i + 1; j < clustering.size(); j++) {
                    compatibility[i][j] = compatibility[j][i] = compatibleClusters(
                            (Integer[]) clustering.get(i),
                            (Integer[]) clustering.get(j), cv);
                }
            }
        }

        //Ideally, we should find all maximum cliques among "cluster nodes".
        //Heuristic: greedily build a set of clusters starting from each cluster.
        System.out.println("Total number of clusters: " + clustering.size());
        for (int i = 0; i < max; i++) {
            //System.out.println("Step " + i);
            List<Integer> nextIds = new ArrayList<>();
            List newClustering = new ArrayList();
            nextIds.add(i);
            newClustering.add(clustering.get(i));
            for (int j = 0; j < clustering.size(); j++) {
                marked[j] = false;
            }
            marked[i] = true;
            int bestChoice;
            double bestScore = ((int[]) clustering.get(i)).length;
            do {
                bestChoice = -1;
                next_choice:
                for (int j = 0; j < clustering.size(); j++) {
                    if (marked[j]) {
                        continue;
                    }
                    for (Object o : newClustering) {
                        if (need3 &&
                                !compatibility[j][clustering.indexOf(
                                        o)]) {
                            marked[j] = true;
                            continue next_choice;
                        }
                    }
                    newClustering.add(clustering.get(j));
                    int localScore = scoreClustering(newClustering, buffer);
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
                    nextIds.add(bestChoice);
                }
            } while (bestChoice > -1);

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
        for (Object clustering : clusterings) {
            List nextClustering = (List) clustering;
            nextOldCluster:
            for (Object o : nextClustering) {
                int[] cluster = (int[]) o;
                nextNewCluster:
                for (Object value : newClustering) {
                    int[] newCluster = (int[]) value;
                    if (cluster.length == newCluster.length) {
                        nextElement:
                        for (int k : cluster) {
                            for (int i : newCluster) {
                                if (k == i) {
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

    /**
     * This implementation uses of the Purify class.
     */
    private List<Integer[]> purify(List actualClusterings, List<int[]> clusterIds) {
        //Try to find a solution. Maximum number of trials: 10
        for (int i = 0; i < actualClusterings.size(); i++) {
            List<Integer[]> partition = (List) actualClusterings.get(i);
            printLatentClique(clusterIds.get(i)
            );

            Clusters clustering = new Clusters();
            int clusterId = 0;
            Iterator<Integer[]> it = partition.iterator();
            printClustering(partition);
            while (it.hasNext()) {
                Integer[] codes = it.next();
                for (Integer code : codes) {
                    String var = tetradTest.getVarNames()[code];
                    clustering.addToCluster(clusterId, var);
                }
                clusterId++;
            }

            List<List<Node>> partition2 = new ArrayList<>();

            for (Integer[] clusterIndices : partition) {
                List<Node> cluster = new ArrayList<>();

                for (Integer clusterIndex : clusterIndices) {
                    cluster.add(tetradTest.getVariables().get(clusterIndex));
                }

                partition2.add(cluster);
            }

            System.out.println("Partition = " + partition2);

            return partition;
        }

        return new ArrayList<>();
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





