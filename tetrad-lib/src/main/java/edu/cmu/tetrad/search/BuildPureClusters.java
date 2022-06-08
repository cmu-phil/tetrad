///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015, 2022 by Peter Spirtes, Richard        //
// Scheines, Joseph Ramsey, and Clark Glymour.                               //
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
 * BuildPureClusters is an implementation of the automated clustering and purification methods
 * described on the report "Learning Measurement Models" CMU-CALD-03-100.
 * <p>
 * The output is only the purified model. Future versions may include options to visualize the
 * measurement pattern in the GUI (it shows up in the console window, though.)
 * <p>
 * No background knowledge is allowed yet. Future versions of this algorithm will include it.
 * <p>
 * References:
 * <p>
 * Silva, R.; Scheines, R.; Spirtes, P.; Glymour, C. (2003). "Learning measurement models".
 * Technical report CMU-CALD-03-100, Center for Automated Learning and Discovery, Carnegie Mellon
 * University.
 * <p>
 * Bollen, K. (1990). "Outlier screening and distribution-free test for vanishing tetrads."
 * Sociological Methods and Research 19, 80-92.
 * <p>
 * Wishart, J. (1928). "Sampling errors in the theory of two factors". British Journal of
 * Psychology 19, 180-187.
 * <p>
 * Bron, C. and Kerbosch, J. (1973) "Algorithm 457: Finding all cliques of an undirected graph".
 * Communications of ACM 16, 575-577.
 * <p>
 * --Cleaned up by jdramsey 2022-03-28
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
    private double alpha;
    private boolean verbose;

    //**************************** INITIALIZATION ***********************************/

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
            this.numVariables = this.dataSet.getNumColumns();
            this.independenceTest = new IndTestGSquare(this.dataSet, alpha);
            this.tetradTest = new DiscreteTetradTest(this.dataSet, alpha);
        } else {
            assert getCovarianceMatrix() != null;
            this.numVariables = getCovarianceMatrix().getSize();
            this.independenceTest = new IndTestFisherZ(getCovarianceMatrix(), .1);
            TestType type;

            if (sigTestType == TestType.TETRAD_WISHART || sigTestType == TestType.TETRAD_DELTA
                    || sigTestType == TestType.GAUSSIAN_FACTOR) {
                type = sigTestType;
            } else {
                throw new IllegalArgumentException("Expecting TETRAD_WISHART, TETRAD_DELTA, or GAUSSIAN FACTOR " +
                        sigTestType);
            }

            if (this.dataSet != null) {
                this.tetradTest = new ContinuousTetradTest(this.dataSet, type, alpha);
            } else {
                this.tetradTest = new ContinuousTetradTest(getCovarianceMatrix(), type, alpha);
            }
        }
        this.labels = new int[numVariables()];
        for (int i = 0; i < numVariables(); i++) {
            this.labels[i] = i + 1;
        }
    }

    /**
     * @return the result search graph, or null if there is no model.
     */
    public Graph search() {
        long start = System.currentTimeMillis();

        TetradLogger.getInstance().log("info", "BPC alpha = " + this.alpha + " test = " + this.sigTestType);

        List<int[]> clustering = findMeasurementPattern();
        clustering.removeIf(cluster -> cluster.length < 3);
        List<Node> variables = this.tetradTest.getVariables();
        Set<Set<Integer>> clusters = new HashSet<>();

        for (int[] _c : clustering) {
            Set<Integer> cluster = new HashSet<>();

            for (int i : _c) {
                cluster.add(i);
            }

            clusters.add(cluster);
        }

        ClusterUtils.logClusters(clusters, variables);
        Graph graph = convertSearchGraph(clustering);

        TetradLogger.getInstance().log("graph", "\nReturning this graph: " + graph);

        long stop = System.currentTimeMillis();
        long elapsed = stop - start;

        TetradLogger.getInstance().log("elapsed", "Elapsed " + elapsed + " ms");

        return graph;
    }

    /**
     * @return the converted search graph, or null if there is no model.
     */
    private Graph convertSearchGraph(List<int[]> clusters) {
        List<Node> nodes = this.tetradTest.getVariables();
        Graph graph = new EdgeListGraph(nodes);

        List<Node> latents = new ArrayList<>();
        for (int i = 0; i < clusters.size(); i++) {
            Node latent = new GraphNode(ClusterUtils.LATENT_PREFIX + (i + 1));
            latent.setNodeType(NodeType.LATENT);
            latents.add(latent);
            graph.addNode(latent);
        }

        for (int i = 0; i < latents.size(); i++) {
            for (int j : clusters.get(i)) {
                graph.addDirectedEdge(latents.get(i), nodes.get(j));
            }
        }

        return graph;
    }

    /******************************* STATISTICAL TESTS ***********************************/

    private boolean clusteredPartial1(int v1, int v2, int v3, int v4) {
        if (this.scoreTestMode) {
            return !this.tetradTest.oneFactorTest(v1, v2, v3, v4);
        } else {
            return !this.tetradTest.tetradScore3(v1, v2, v3, v4);
        }
    }

    private boolean validClusterPairPartial1(int v1, int v2, int v3, int v4, int[][] cv) {
        if (this.scoreTestMode) {
            return this.tetradTest.oneFactorTest(v1, v2, v3, v4);
        } else {
            if (cv[v1][v4] == this.EDGE_NONE && cv[v2][v4] == this.EDGE_NONE &&
                    cv[v3][v4] == this.EDGE_NONE) {
                return true;
            }

            boolean test1 = this.tetradTest.tetradHolds(v1, v2, v3, v4);
            boolean test2 = this.tetradTest.tetradHolds(v1, v2, v4, v3);

            if (test1 && test2) {
                return true;
            }

            boolean test3 = this.tetradTest.tetradHolds(v1, v3, v4, v2);
            return (test1 && test3) || (test2 && test3);
        }
    }

    private boolean clusteredPartial2(int v1, int v2, int v3, int v4, int v5) {
        if (this.scoreTestMode) {
            return !this.tetradTest.oneFactorTest(v1, v2, v3, v5) ||
                    this.tetradTest.oneFactorTest(v1, v2, v3, v4, v5) ||
                    !this.tetradTest.twoFactorTest(v1, v2, v3, v4, v5);
        } else {
            return !this.tetradTest.tetradScore3(v1, v2, v3, v5) ||

                    !this.tetradTest.tetradScore1(v1, v2, v4, v5) ||
                    !this.tetradTest.tetradScore1(v2, v3, v4, v5) ||
                    !this.tetradTest.tetradScore1(v1, v3, v4, v5);
        }
    }

    private boolean validClusterPairPartial2(int v1, int v2, int v3, int v5, int[][] cv) {
        if (this.scoreTestMode) {
            return this.tetradTest.oneFactorTest(v1, v2, v3, v5);
        } else {
            if (cv[v1][v5] == this.EDGE_NONE && cv[v2][v5] == this.EDGE_NONE &&
                    cv[v3][v5] == this.EDGE_NONE) {
                return true;
            }

            boolean test1 = this.tetradTest.tetradHolds(v1, v2, v3, v5);
            boolean test2 = this.tetradTest.tetradHolds(v1, v2, v5, v3);
            boolean test3 = this.tetradTest.tetradHolds(v1, v3, v5, v2);

            return (test1 && test2) || (test1 && test3) || (test2 && test3);
        }
    }

    private boolean unclusteredPartial3(int v1, int v2, int v3, int v4, int v5,
                                        int v6) {
        if (this.scoreTestMode) {
            return this.tetradTest.oneFactorTest(v1, v2, v3, v6) &&
                    this.tetradTest.oneFactorTest(v4, v5, v6, v1) &&
                    this.tetradTest.oneFactorTest(v4, v5, v6, v2) &&
                    this.tetradTest.oneFactorTest(v4, v5, v6, v3) &&
                    this.tetradTest.twoFactorTest(v1, v2, v3, v4, v5, v6);
        } else {
            return

                    this.tetradTest.tetradScore3(v1, v2, v3, v6) &&
                            this.tetradTest.tetradScore3(v4, v5, v6, v1) &&
                            this.tetradTest.tetradScore3(v4, v5, v6, v2) &&
                            this.tetradTest.tetradScore3(v4, v5, v6, v3) &&

                            this.tetradTest.tetradScore1(v1, v2, v4, v6) &&
                            this.tetradTest.tetradScore1(v1, v2, v5, v6) &&
                            this.tetradTest.tetradScore1(v2, v3, v4, v6) &&
                            this.tetradTest.tetradScore1(v2, v3, v5, v6) &&
                            this.tetradTest.tetradScore1(v1, v3, v4, v6) &&
                            this.tetradTest.tetradScore1(v1, v3, v5, v6);
        }
    }

    private boolean validClusterPairPartial3(int v1, int v2, int v3, int v4,
                                             int v5, int v6, int[][] cv) {
        if (this.scoreTestMode) {
            return this.tetradTest.oneFactorTest(v1, v2, v3, v6) &&
                    this.tetradTest.oneFactorTest(v4, v5, v6, v1) &&
                    this.tetradTest.oneFactorTest(v4, v5, v6, v2) &&
                    this.tetradTest.oneFactorTest(v4, v5, v6, v3);
        } else {
            if (cv[v1][v6] == this.EDGE_NONE && cv[v2][v6] == this.EDGE_NONE &&
                    cv[v3][v6] == this.EDGE_NONE) {
                return true;
            }

            boolean test1 = this.tetradTest.tetradHolds(v1, v2, v3, v6);
            boolean test2 = this.tetradTest.tetradHolds(v1, v2, v6, v3);
            boolean test3 = this.tetradTest.tetradHolds(v1, v3, v6, v2);

            if (!((test1 && test2) || (test1 && test3) || (test2 && test3))) {
                return false;
            }

            test1 = this.tetradTest.tetradHolds(v4, v5, v6, v1);
            test2 = this.tetradTest.tetradHolds(v4, v5, v1, v6);
            test3 = this.tetradTest.tetradHolds(v4, v6, v1, v5);

            if (!((test1 && test2) || (test1 && test3) || (test2 && test3))) {
                return false;
            }

            test1 = this.tetradTest.tetradHolds(v4, v5, v6, v2);
            test2 = this.tetradTest.tetradHolds(v4, v5, v2, v6);
            test3 = this.tetradTest.tetradHolds(v4, v6, v2, v5);

            if (!((test1 && test2) || (test1 && test3) || (test2 && test3))) {
                return false;
            }

            test1 = this.tetradTest.tetradHolds(v4, v5, v6, v3);
            test2 = this.tetradTest.tetradHolds(v4, v5, v3, v6);
            test3 = this.tetradTest.tetradHolds(v4, v6, v3, v5);

            return (test1 && test2) || (test1 && test3) || (test2 && test3);
        }
    }

    private boolean partialRule1_1(int x1, int x2, int x3, int y1) {
        if (this.scoreTestMode) {
            return this.tetradTest.oneFactorTest(x1, y1, x2, x3);
        }

        return this.tetradTest.tetradScore3(x1, y1, x2, x3);
    }

    private boolean partialRule1_2(int x1, int x2, int y1, int y2) {
        if (this.scoreTestMode) {
            return !this.tetradTest.oneFactorTest(x1, x2, y1, y2) &&
                    this.tetradTest.twoFactorTest(x1, x2, y1, y2);
        }

        return !this.tetradTest.tetradHolds(x1, x2, y2, y1) &&
                !this.tetradTest.tetradHolds(x1, y1, x2, y2) &&
                this.tetradTest.tetradHolds(x1, y1, y2, x2);

    }

    private boolean partialRule1_3(int x1, int y1, int y2, int y3) {
        if (this.scoreTestMode) {
            return this.tetradTest.oneFactorTest(x1, y1, y2, y3);
        }

        return this.tetradTest.tetradScore3(x1, y1, y2, y3);

    }

    private boolean partialRule2_1(int x1, int x2, int y1, int y2) {
        if (this.scoreTestMode) {
            return !this.tetradTest.oneFactorTest(x1, x2, y1, y2) &&
                    this.tetradTest.twoFactorTest(x1, x2, y1, y2);
        }

        return this.tetradTest.tetradHolds(x1, y1, y2, x2) &&
                !this.tetradTest.tetradHolds(x1, x2, y2, y1) &&
                !this.tetradTest.tetradHolds(x1, y1, x2, y2) &&
                this.tetradTest.tetradHolds(x1, y1, y2, x2);

    }

    private boolean partialRule2_2(int x1, int x2, int x3, int y2) {
        if (this.scoreTestMode) {
            return this.tetradTest.twoFactorTest(x1, x3, x2, y2);
        }

        return this.tetradTest.tetradHolds(x1, x2, y2, x3);

    }

    private boolean partialRule2_3(int x2, int y1, int y2, int y3) {
        if (this.scoreTestMode) {
            this.tetradTest.twoFactorTest(x2, y2, y1, y3);
        }

        return this.tetradTest.tetradHolds(x2, y1, y3, y2);
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
            return getIndependenceTest().checkIndependence(variables.get(v1),
                    variables.get(v2)).independent();

        } else {
            return getIndependenceTest().checkIndependence(this.dataSet.getVariable(v1),
                    this.dataSet.getVariable(v2)).independent();

        }
    }

    /********************************** DEBUG UTILITIES ***********************************/

    private void printClustering(List<int[]> clustering) {
        for (int[] cluster : clustering) {
            printClusterNames(cluster);
        }
    }

    private void printClusterIds(int[] c) {
        int[] sorted = new int[c.length];
        for (int i = 0; i < c.length; i++) {
            sorted[i] = this.labels[c[i]];
        }
        for (int i = 0; i < sorted.length - 1; i++) {
            int min = Integer.MAX_VALUE;
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

    private void printClusterNames(int[] c) {
        String[] sorted = new String[c.length];
        for (int i = 0; i < c.length; i++) {
            sorted[i] = this.tetradTest.getVarNames()[c[i]];
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
            int min = Integer.MAX_VALUE;
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

    private List<int[]> findComponents(int[][] graph, int size) {
        boolean[] marked = new boolean[size];

        for (int i = 0; i < size; i++) {
            marked[i] = false;
        }

        int numMarked = 0;
        List<int[]> output = new ArrayList<>();
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
                int[] newPartition = new int[sizeTemp];
                System.arraycopy(tempComponent, 0, newPartition, 0, sizeTemp);
                output.add(newPartition);
            }
        }

        return output;
    }

    /*
     * Find all maximal cliques of a graph. However, it can generate an exponential number of
     * cliques as a function of the number of impurities in the true graph. Therefore, we also
     * use a counter to stop the computation after a given number of calls. This is an
     * implementation of Algorithm 2 from Bron and Kerbosch (1973).
     */
    private List<int[]> findMaximalCliques(int[] elements, int[][] ng) {
        boolean[][] connected = new boolean[this.numVariables()][this.numVariables()];

        for (int i = 0; i < connected.length; i++) {
            for (int j = i; j < connected.length; j++) {
                if (i != j) {
                    connected[i][j] = connected[j][i] =
                            (ng[i][j] != this.EDGE_NONE);
                } else {
                    connected[i][j] = true;
                }
            }
        }

        int[] numCalls = new int[1];
        int[] c = new int[1];
        List<int[]> output = new ArrayList<>();
        int[] compsub = new int[elements.length];
        int[] old = new int[elements.length];
        System.arraycopy(elements, 0, old, 0, elements.length);
        findMaximalCliquesOperator(numCalls, output, connected,
                compsub, c, old, 0, elements.length);
        return output;
    }

    private void findMaximalCliquesOperator(int[] numCalls,
                                            List<int[]> output, boolean[][] connected, int[] compsub, int[] c,
                                            int[] old, int ne, int ce) {
        if (numCalls[0] > this.MAX_CLIQUE_TRIALS) {
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
                int[] clique = new int[c[0]];
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

    /*
     * Return true iff "newClique" is contained in some element of "clustering".
     */
    private boolean cliqueContained(int[] newClique, int size, List<int[]> clustering) {
        for (int[] next : clustering) {
            if (size > next.length) {
                continue;
            }
            boolean found = true;
            for (int i = 0; i < size && found; i++) {
                found = false;
                for (int k : next) {
                    if (newClique[i] == k) {
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

    /* Remove cliques that are contained into another ones in cliqueList. */
    private List<int[]> trimCliqueList(List<int[]> cliqueList) {
        List<int[]> trimmed = new ArrayList<>();
        List<int[]> cliqueCopy = new ArrayList<>(cliqueList);

        for (int[] cluster : cliqueList) {
            cliqueCopy.remove(cluster);
            if (!cliqueContained(cluster, cluster.length, cliqueCopy)) {
                trimmed.add(cluster);
            }
            cliqueCopy.add(cluster);
        }
        return trimmed;
    }

    private int clustersize(List<int[]> cluster) {
        int total = 0;
        for (int[] next : cluster) {
            total += next.length;
        }
        return total;
    }

    private int clustersize3(List<int[]> cluster) {
        int total = 0;
        for (int[] next : cluster) {
            if (next.length > 2) {
                total += next.length;
            }
        }
        return total;
    }

    private void sortClusterings(int start, int end, List<List<int[]>> clusterings,
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
            List<int[]> temp = clusterings.get(i);
            clusterings.set(i, clusterings.get(max_idx));
            clusterings.set(max_idx, temp);
            int old_c;
            old_c = criterion[i];
            criterion[i] = criterion[max_idx];
            criterion[max_idx] = old_c;
        }
    }

    /*
     * Transforms clusterings (each "clustering" is a set of "clusters"), remove overlapping indicators for each
     * clustering, and order clusterings according to the number of nonoverlapping indicators, throwing away any latent
     * with zero indicators. Also, for each pair of indicators such that they are linked in ng, one of them is chosen to
     * be removed (the heuristic is, choose the one that belongs to the largest cluster). The list that is returned
     * is a list of lists. Each element in the big list is a list of integer arrays, where each integer array represents
     * one cluster.
     */
    private int scoreClustering(List<int[]> clustering, boolean[] buffer) {
        int score = 0;
        Arrays.fill(buffer, true);

        //First filter: remove all overlaps
        for (int[] currentCluster : clustering) {
            next_item:
            for (int k : currentCluster) {
                if (!buffer[k]) {
                    continue;
                }
                for (int[] nextCluster : clustering) {
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
        for (int[] currentCluster : clustering) {
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

    private List<List<int[]>> filterAndOrderClusterings(List<List<int[]>> baseListOfClusterings,
                                                        List<List<Integer>> baseListOfIds, List<int[]> clusteringIds, int[][] ng) {

        assert clusteringIds != null;
        List<List<int[]>> listOfClusterings = new ArrayList<>();
        clusteringIds.clear();

        for (int i = 0; i < baseListOfClusterings.size(); i++) {

            //First filter: remove all overlaps
            List<int[]> newClustering = new ArrayList<>();
            List<int[]> baseClustering = baseListOfClusterings.get(i);

            System.out.println("* Base mimClustering");
            printClustering(baseClustering);

            List<Integer> baseIds = baseListOfIds.get(i);
            List<Integer> usedIds = new ArrayList<>();

            for (int j = 0; j < baseClustering.size(); j++) {
                int[] currentCluster = baseClustering.get(j);
                Integer currentId = baseIds.get(j);
                int[] draftArea = new int[currentCluster.length];
                int draftCount = 0;
                next_item:

                for (int value : currentCluster) {
                    for (int k = 0; k < baseClustering.size(); k++) {
                        if (k == j) {
                            continue;
                        }

                        int[] nextCluster = baseClustering.get(k);

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
            //Current criterion: count the number of invalid relations each node participates in, greedily
            //remove nodes till none of these relations hold anymore
            boolean[][] impurities = new boolean[this.numVariables()][this.numVariables()];

            for (int j = 0; j < newClustering.size() - 1; j++) {
                int[] currentCluster = newClustering.get(j);

                for (int jj = j + 1; jj < currentCluster.length; jj++) {
                    for (int k = 0; k < newClustering.size(); k++) {
                        if (k == j) {
                            continue;
                        }

                        int[] nextCluster = newClustering.get(k);

                        for (int value : nextCluster) {
                            impurities[currentCluster[jj]][value] =
                                    ng[currentCluster[jj]][value] !=
                                            this.EDGE_NONE;
                            impurities[value][currentCluster[jj]] =
                                    impurities[currentCluster[jj]][value];
                        }
                    }
                }
            }

            List<int[]> newClustering2 = removeMarkedImpurities(newClustering,
                    impurities);
            List<int[]> finalNewClustering = new ArrayList<>();
            List<Integer> finalUsedIds = new ArrayList<>();

            for (int j = 0; j < newClustering2.size(); j++) {
                if (newClustering2.get(j).length > 0) {
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
            numIndicators[i] = clustersize3(listOfClusterings.get(i));
        }
        sortClusterings(0, listOfClusterings.size(), listOfClusterings,
                numIndicators);
        for (int i = 0; i < listOfClusterings.size(); i++) {
            numIndicators[i] = clustersize(listOfClusterings.get(i));
        }

        int start = 0;

        while (start < listOfClusterings.size()) {
            int size3 = clustersize3(listOfClusterings.get(start));
            int end = start + 1;

            for (int j = start + 1; j < listOfClusterings.size(); j++) {
                if (size3 != clustersize3(listOfClusterings.get(j))) {
                    break;
                }
                end++;
            }

            sortClusterings(start, end, listOfClusterings, numIndicators);
            start = end;
        }

        return listOfClusterings;
    }

    private List<int[]> removeMarkedImpurities(List<int[]> partition, boolean[][] impurities) {
        System.out.println("sizecluster = " + clustersize(partition));
        int[][] elements = new int[clustersize(partition)][3];
        int[] partitionCount = new int[partition.size()];
        int countElements = 0;

        for (int p = 0; p < partition.size(); p++) {
            int[] next = partition.get(p);
            partitionCount[p] = 0;

            for (int j : next) {
                elements[countElements][0] = j; // global ID
                elements[countElements][1] = p; // set partition ID
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

        List<int[]> solution = new ArrayList<>();

        for (int[] next : partition) {
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

        //Now, within each cluster, select first those that belong to the clusters with less than three latents.
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

    /******************** MAIN ALGORITHM: INITIALIZATION************************************/

    private List<int[]> initialMeasurementPattern(int[][] ng, int[][] cv) {
        boolean[][] notYellow = new boolean[numVariables()][numVariables()];

        /* Stage 1: identify (partially) uncorrelated and impure pairs */
        for (int v1 = 0; v1 < numVariables() - 1; v1++) {
            for (int v2 = v1 + 1; v2 < numVariables(); v2++) {
                ng[v1][v2] = ng[v2][v1] = this.EDGE_BLACK;
            }
        }
        for (int v1 = 0; v1 < numVariables() - 1; v1++) {
            for (int v2 = v1 + 1; v2 < numVariables(); v2++) {
                if (uncorrelated(v1, v2)) {
                    cv[v1][v2] = cv[v2][v1] = this.EDGE_NONE;
                } else {
                    cv[v1][v2] = cv[v2][v1] = this.EDGE_BLACK;
                }
                ng[v1][v2] = ng[v2][v1] = cv[v1][v2];
            }
        }

        for (int v1 = 0; v1 < numVariables() - 1; v1++) {
            for (int v2 = v1 + 1; v2 < numVariables(); v2++) {
                if (ng[v1][v2] != this.EDGE_BLACK) {
                    continue;
                }
                boolean notFound = true;
                for (int v3 = 0; v3 < numVariables() - 1 && notFound; v3++) {
                    if (v1 == v3 || v2 == v3 || ng[v1][v3] == this.EDGE_NONE || ng[v1][v3] ==
                            this.EDGE_GRAY || ng[v2][v3] == this.EDGE_NONE || ng[v2][v3] ==
                            this.EDGE_GRAY) {
                        continue;
                    }
                    for (int v4 = v3 + 1; v4 < numVariables() && notFound; v4++) {
                        if (v1 == v4 || v2 == v4 || ng[v1][v4] == this.EDGE_NONE ||
                                ng[v1][v4] == this.EDGE_GRAY ||
                                ng[v2][v4] == this.EDGE_NONE ||
                                ng[v2][v4] == this.EDGE_GRAY ||
                                ng[v3][v4] == this.EDGE_NONE ||
                                ng[v3][v4] == this.EDGE_GRAY) {
                            continue;
                        }
                        if (this.tetradTest.tetradScore3(v1, v2, v3, v4)) {
                            notFound = false;
                            ng[v1][v2] = ng[v2][v1] = this.EDGE_BLUE;
                            ng[v1][v3] = ng[v3][v1] = this.EDGE_BLUE;
                            ng[v1][v4] = ng[v4][v1] = this.EDGE_BLUE;
                            ng[v2][v3] = ng[v3][v2] = this.EDGE_BLUE;
                            ng[v2][v4] = ng[v4][v2] = this.EDGE_BLUE;
                            ng[v3][v4] = ng[v4][v3] = this.EDGE_BLUE;
                        }
                    }
                }
                if (notFound) {
                    ng[v1][v2] = ng[v2][v1] = this.EDGE_GRAY;
                }
            }
        }

        /* Stage 2: prune blue edges, find yellow ones */
        for (int i = 0; i < numVariables() - 1; i++) {
            for (int j = i + 1; j < numVariables(); j++) {
                notYellow[i][j] = notYellow[j][i] = false;
            }
        }

        for (int v1 = 0; v1 < numVariables() - 1; v1++) {
            for (int v2 = v1 + 1; v2 < numVariables(); v2++) {

                //Trying to find unclustered({v1, v3, v5}, {v2, v4, v6})
                if (ng[v1][v2] != this.EDGE_BLUE) {
                    continue;
                }

                boolean notFound = true;

                for (int v3 = 0; v3 < numVariables() - 1 && notFound; v3++) {
                    if (v1 == v3 || v2 == v3 || //ng[v1][v3] != EDGE_BLUE ||
                            ng[v1][v3] == this.EDGE_GRAY || ng[v2][v3] == this.EDGE_GRAY ||
                            cv[v1][v3] != this.EDGE_BLACK ||
                            cv[v2][v3] != this.EDGE_BLACK) {
                        continue;
                    }

                    for (int v5 = v3 + 1; v5 < numVariables() && notFound; v5++) {
                        if (v1 == v5 || v2 == v5 || //ng[v1][v5] != EDGE_BLUE || ng[v3][v5] != EDGE_BLUE ||
                                ng[v1][v5] == this.EDGE_GRAY ||
                                ng[v2][v5] == this.EDGE_GRAY ||
                                ng[v3][v5] == this.EDGE_GRAY ||
                                cv[v1][v5] != this.EDGE_BLACK ||
                                cv[v2][v5] != this.EDGE_BLACK ||
                                cv[v3][v5] != this.EDGE_BLACK ||
                                clusteredPartial1(v1, v3, v5, v2)) {
                            continue;
                        }

                        for (int v4 = 0; v4 < numVariables() - 1 && notFound; v4++) {
                            if (v1 == v4 || v2 == v4 || v3 == v4 || v5 == v4 ||
                                    ng[v1][v4] == this.EDGE_GRAY ||
                                    ng[v2][v4] == this.EDGE_GRAY ||
                                    ng[v3][v4] == this.EDGE_GRAY ||
                                    ng[v5][v4] == this.EDGE_GRAY || //ng[v2][v4] != EDGE_BLUE ||
                                    cv[v1][v4] != this.EDGE_BLACK ||
                                    cv[v2][v4] != this.EDGE_BLACK ||
                                    cv[v3][v4] != this.EDGE_BLACK ||
                                    cv[v5][v4] != this.EDGE_BLACK ||
                                    clusteredPartial2(v1, v3, v5, v2, v4)) {
                                continue;
                            }

                            for (int v6 = v4 + 1; v6 < numVariables() && notFound; v6++) {
                                if (v1 == v6 || v2 == v6 || v3 == v6 ||
                                        v5 == v6 || ng[v1][v6] == this.EDGE_GRAY ||
                                        ng[v2][v6] == this.EDGE_GRAY ||
                                        ng[v3][v6] == this.EDGE_GRAY ||
                                        ng[v4][v6] == this.EDGE_GRAY ||
                                        ng[v5][v6] == this.EDGE_GRAY || //ng[v2][v6] != EDGE_BLUE || ng[v4][v6] != EDGE_BLUE ||
                                        cv[v1][v6] != this.EDGE_BLACK ||
                                        cv[v2][v6] != this.EDGE_BLACK ||
                                        cv[v3][v6] != this.EDGE_BLACK ||
                                        cv[v4][v6] != this.EDGE_BLACK ||
                                        cv[v5][v6] != this.EDGE_BLACK) {
                                    continue;
                                }

                                if (unclusteredPartial3(v1, v3, v5, v2, v4, v6)) {
                                    notFound = false;
                                    ng[v1][v2] = ng[v2][v1] = this.EDGE_NONE;
                                    ng[v1][v4] = ng[v4][v1] = this.EDGE_NONE;
                                    ng[v1][v6] = ng[v6][v1] = this.EDGE_NONE;
                                    ng[v3][v2] = ng[v2][v3] = this.EDGE_NONE;
                                    ng[v3][v4] = ng[v4][v3] = this.EDGE_NONE;
                                    ng[v3][v6] = ng[v6][v3] = this.EDGE_NONE;
                                    ng[v5][v2] = ng[v2][v5] = this.EDGE_NONE;
                                    ng[v5][v4] = ng[v4][v5] = this.EDGE_NONE;
                                    ng[v5][v6] = ng[v6][v5] = this.EDGE_NONE;
                                    notYellow[v1][v3] = notYellow[v3][v1] = true;
                                    notYellow[v1][v5] = notYellow[v5][v1] = true;
                                    notYellow[v3][v5] = notYellow[v5][v3] = true;
                                    notYellow[v2][v4] = notYellow[v4][v2] = true;
                                    notYellow[v2][v6] = notYellow[v6][v2] = true;
                                    notYellow[v4][v6] = notYellow[v6][v4] = true;
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
                        if (v1 == v3 || v2 == v3 || ng[v1][v3] == this.EDGE_GRAY ||
                                ng[v2][v3] == this.EDGE_GRAY ||
                                cv[v1][v3] != this.EDGE_BLACK ||
                                cv[v2][v3] != this.EDGE_BLACK) {
                            continue;
                        }

                        for (int v4 = 0; v4 < numVariables() - 2 && notFound; v4++) {
                            if (v1 == v4 || v2 == v4 || v3 == v4 ||
                                    ng[v1][v4] == this.EDGE_GRAY ||
                                    ng[v2][v4] == this.EDGE_GRAY ||
                                    ng[v3][v4] == this.EDGE_GRAY ||
                                    cv[v1][v4] != this.EDGE_BLACK ||
                                    cv[v2][v4] != this.EDGE_BLACK ||
                                    cv[v3][v4] != this.EDGE_BLACK ||
                                    clusteredPartial1(v1, v2, v3, v4)) {
                                continue;
                            }

                            for (int v5 = v4 + 1; v5 < numVariables() - 1 && notFound; v5++) {
                                if (v1 == v5 || v2 == v5 || v3 == v5 ||
                                        ng[v1][v5] == this.EDGE_GRAY ||
                                        ng[v2][v5] == this.EDGE_GRAY ||
                                        ng[v3][v5] == this.EDGE_GRAY ||
                                        ng[v4][v5] == this.EDGE_GRAY ||
                                        cv[v1][v5] != this.EDGE_BLACK ||
                                        cv[v2][v5] != this.EDGE_BLACK ||
                                        cv[v3][v5] != this.EDGE_BLACK ||
                                        cv[v4][v5] != this.EDGE_BLACK || //ng[v4][v5] != EDGE_BLUE ||
                                        clusteredPartial2(v1, v2, v3, v4,
                                                v5)) {
                                    continue;
                                }

                                for (int v6 = v5 + 1; v6 < numVariables() && notFound; v6++) {
                                    if (v1 == v6 || v2 == v6 || v3 == v6 ||
                                            ng[v1][v6] == this.EDGE_GRAY ||
                                            ng[v2][v6] == this.EDGE_GRAY ||
                                            ng[v3][v6] == this.EDGE_GRAY ||
                                            ng[v4][v6] == this.EDGE_GRAY ||
                                            ng[v5][v6] == this.EDGE_GRAY ||
                                            cv[v1][v6] != this.EDGE_BLACK ||
                                            cv[v2][v6] != this.EDGE_BLACK ||
                                            cv[v3][v6] != this.EDGE_BLACK ||
                                            cv[v4][v6] != this.EDGE_BLACK ||
                                            cv[v5][v6] != this.EDGE_BLACK) {
                                        continue;
                                    }

                                    if (unclusteredPartial3(v1, v2, v3, v4, v5, v6)) {
                                        notFound = false;
                                        ng[v1][v4] = ng[v4][v1] = this.EDGE_NONE;
                                        ng[v1][v5] = ng[v5][v1] = this.EDGE_NONE;
                                        ng[v1][v6] = ng[v6][v1] = this.EDGE_NONE;
                                        ng[v2][v4] = ng[v4][v2] = this.EDGE_NONE;
                                        ng[v2][v5] = ng[v5][v2] = this.EDGE_NONE;
                                        ng[v2][v6] = ng[v6][v2] = this.EDGE_NONE;
                                        ng[v3][v4] = ng[v4][v3] = this.EDGE_NONE;
                                        ng[v3][v5] = ng[v5][v3] = this.EDGE_NONE;
                                        ng[v3][v6] = ng[v6][v3] = this.EDGE_NONE;
                                        notYellow[v1][v2] = notYellow[v2][v1] = true;
                                        notYellow[v1][v3] = notYellow[v3][v1] = true;
                                        notYellow[v2][v3] = notYellow[v3][v2] = true;
                                        notYellow[v4][v5] = notYellow[v5][v4] = true;
                                        notYellow[v4][v6] = notYellow[v6][v4] = true;
                                        notYellow[v5][v6] = notYellow[v6][v5] = true;
                                    }
                                }
                            }
                        }
                    }
                }
                if (notFound) {
                    ng[v1][v2] = ng[v2][v1] = this.EDGE_YELLOW;
                }

            }
        }

        /* Stage 3: find maximal cliques */
        List<int[]> clustering = new ArrayList<>();
        List<int[]> components = findComponents(ng, numVariables());
        for (int[] component : components) {
            printClusterIds(component);
            List<int[]> nextClustering = findMaximalCliques(component, ng);
            clustering.addAll(trimCliqueList(nextClustering));
        }
        //Sort cliques by size: heuristic to keep as many indicators as possible
        for (int i = 0; i < clustering.size() - 1; i++) {
            int max = 0;
            int max_idx = -1;
            for (int j = i; j < clustering.size(); j++) {
                if (clustering.get(j).length > max) {
                    max = clustering.get(j).length;
                    max_idx = j;
                }
            }
            int[] temp = clustering.get(i);
            clustering.set(i, clustering.get(max_idx));
            clustering.set(max_idx, temp);
        }

        List<int[]> individualOneFactors = individualPurification(clustering);
        printClustering(individualOneFactors);
        clustering = individualOneFactors;
        List<List<Integer>> ids = new ArrayList<>();
        List<List<int[]>> clusterings = chooseClusterings(clustering, ids, true, cv);
        List<int[]> orderedIds = new ArrayList<>();
        List<List<int[]>> actualClustering = filterAndOrderClusterings(clusterings, ids,
                orderedIds, ng);
        return purify(actualClustering, orderedIds);
    }

    private List<int[]> individualPurification(List<int[]> clustering) {
        boolean oldOutputMessage = this.outputMessage;
        List<int[]> purified = new ArrayList<>();
        int[] ids = {1};
        for (int[] rawCluster : clustering) {
            this.outputMessage = false;
            if (rawCluster.length <= 4) {
                this.outputMessage = oldOutputMessage;
                purified.add(rawCluster);
                continue;
            }
            List<List<int[]>> dummyClusterings = new ArrayList<>();
            List<int[]> dummyClustering = new ArrayList<>();
            dummyClustering.add(rawCluster);
            dummyClusterings.add(dummyClustering);
            List<int[]> dummyIds = new ArrayList<>();
            dummyIds.add(ids);
            List<int[]> purification = purify(dummyClusterings, dummyIds);
            if (purification.size() > 0) {
                purified.add(purification.get(0));
            } else {
                int[] newFakeCluster = new int[4];
                System.arraycopy(rawCluster, 0, newFakeCluster, 0, 4);
                purified.add(newFakeCluster);
            }
            this.outputMessage = oldOutputMessage;
        }
        return purified;
    }

    private boolean compatibleClusters(int[] cluster1, int[] cluster2,
                                       int[][] cv) {
        HashSet<Integer> allNodes = new HashSet<>();

        for (int j : cluster1) {
            allNodes.add(j);
        }

        for (int j : cluster2) {
            allNodes.add(j);
        }

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

    /******************************* MAIN ALGORITHM: CORE ***************************************/

    private List<int[]> findMeasurementPattern() {
        int[][] ng = new int[numVariables()][numVariables()];
        int[][] cv = new int[numVariables()][numVariables()];
        boolean[] selected = new boolean[numVariables()];

        for (int i = 0; i < numVariables(); i++) {
            selected[i] = false;
        }

        List<int[]> initialClustering = initialMeasurementPattern(ng, cv);
        printClustering(initialClustering);
        for (int[] nextCluster : initialClustering) {
            for (int j : nextCluster) {
                selected[j] = true;
            }
        }

        /* Stage 1: identify (partially) uncorrelated and impure pairs */
        for (int i = 0; i < numVariables(); i++) {
            for (int j = 0; j < numVariables(); j++) {
                if (selected[i] && selected[j] &&
                        (ng[i][j] == this.EDGE_BLUE || ng[i][j] == this.EDGE_YELLOW)) {
                    ng[i][j] = this.EDGE_RED;
                } else if ((!selected[i] || !selected[j]) &&
                        ng[i][j] == this.EDGE_YELLOW) {
                    ng[i][j] = this.EDGE_BLUE;
                }
            }
        }

        /* Stage 2: prune blue edges */

        //Rule 1
        for (int x1 = 0; x1 < numVariables() - 1; x1++) {
            outer_loop:
            for (int y1 = x1 + 1; y1 < numVariables(); y1++) {
                if (ng[x1][y1] != this.EDGE_BLUE) {
                    continue;
                }
                for (int x2 = 0; x2 < numVariables(); x2++) {
                    if (x1 == x2 || y1 == x2 || cv[x1][x2] == this.EDGE_NONE || cv[y1][x2] ==
                            this.EDGE_NONE) {
                        continue;
                    }
                    for (int x3 = 0; x3 < numVariables(); x3++) {
                        if (x1 == x3 || x2 == x3 || y1 == x3 ||
                                cv[x1][x3] == this.EDGE_NONE ||
                                cv[x2][x3] == this.EDGE_NONE ||
                                cv[y1][x3] == this.EDGE_NONE ||
                                !partialRule1_1(x1, x2, x3, y1)) {
                            continue;
                        }
                        for (int y2 = 0; y2 < numVariables(); y2++) {
                            if (x1 == y2 || x2 == y2 || x3 == y2 || y1 == y2 ||
                                    cv[x1][y2] == this.EDGE_NONE ||
                                    cv[x2][y2] == this.EDGE_NONE ||
                                    cv[x3][y2] == this.EDGE_NONE ||
                                    cv[y1][y2] == this.EDGE_NONE ||
                                    !partialRule1_2(x1, x2, y1, y2)) {
                                continue;
                            }
                            for (int y3 = 0; y3 < numVariables(); y3++) {
                                if (x1 == y3 || x2 == y3 || x3 == y3 ||
                                        y1 == y3 || y2 == y3 || cv[x1][y3] ==
                                        this.EDGE_NONE || cv[x2][y3] == this.EDGE_NONE ||
                                        cv[x3][y3] == this.EDGE_NONE ||
                                        cv[y1][y3] == this.EDGE_NONE ||
                                        cv[y2][y3] == this.EDGE_NONE ||
                                        !partialRule1_3(x1, y1, y2, y3)) {
                                    continue;
                                }
                                ng[x1][y1] = ng[y1][x1] = this.EDGE_NONE;
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
                if (ng[x1][y1] != this.EDGE_BLUE) {
                    continue;
                }
                for (int x2 = 0; x2 < numVariables(); x2++) {
                    if (x1 == x2 || y1 == x2 || cv[x1][x2] == this.EDGE_NONE || cv[y1][x2] ==
                            this.EDGE_NONE || ng[x1][x2] == this.EDGE_GRAY) {
                        continue;
                    }
                    for (int y2 = 0; y2 < numVariables(); y2++) {
                        if (x1 == y2 || x2 == y2 || y1 == y2 ||
                                cv[x1][y2] == this.EDGE_NONE ||
                                cv[x2][y2] == this.EDGE_NONE ||
                                cv[y1][y2] == this.EDGE_NONE ||
                                ng[y1][y2] == this.EDGE_GRAY ||
                                !partialRule2_1(x1, x2, y1, y2)) {
                            continue;
                        }
                        for (int x3 = 0; x3 < numVariables(); x3++) {
                            if (x1 == x3 || x2 == x3 || y1 == x3 || y2 == x3 ||
                                    ng[x1][x3] == this.EDGE_GRAY ||
                                    cv[x1][x3] == this.EDGE_NONE ||
                                    cv[x2][x3] == this.EDGE_NONE ||
                                    cv[y1][x3] == this.EDGE_NONE ||
                                    cv[y2][x3] == this.EDGE_NONE ||
                                    !partialRule2_2(x1, x2, x3, y2)) {
                                continue;
                            }
                            for (int y3 = 0; y3 < numVariables(); y3++) {
                                if (x1 == y3 || x2 == y3 || x3 == y3 ||
                                        y1 == y3 || y2 == y3 || ng[y1][y3] ==
                                        this.EDGE_GRAY || cv[x1][y3] == this.EDGE_NONE ||
                                        cv[x2][y3] == this.EDGE_NONE ||
                                        cv[x3][y3] == this.EDGE_NONE ||
                                        cv[y1][y3] == this.EDGE_NONE ||
                                        cv[y2][y3] == this.EDGE_NONE ||
                                        !partialRule2_3(x2, y1, y2, y3)) {
                                    continue;
                                }
                                ng[x1][y1] = ng[y1][x1] = this.EDGE_NONE;
                                continue outer_loop;
                            }
                        }
                    }
                }
            }
        }

        for (int i = 0; i < numVariables(); i++) {
            for (int j = 0; j < numVariables(); j++) {
                if (ng[i][j] == this.EDGE_RED) {
                    ng[i][j] = this.EDGE_BLUE;
                }
            }
        }

        /* Stage 3: find maximal cliques */
        List<int[]> clustering = new ArrayList<>();
        List<int[]> components = findComponents(ng, numVariables());
        for (int[] component : components) {
            printClusterIds(component);
            List<int[]> nextClustering = findMaximalCliques(component, ng);
            clustering.addAll(trimCliqueList(nextClustering));
        }
        //Sort cliques by size: better visualization when printing
        for (int i = 0; i < clustering.size() - 1; i++) {
            int max = 0;
            int max_idx = -1;
            for (int j = i; j < clustering.size(); j++) {
                if (clustering.get(j).length > max) {
                    max = clustering.get(j).length;
                    max_idx = j;
                }
            }
            int[] temp = clustering.get(i);
            clustering.set(i, clustering.get(max_idx));
            clustering.set(max_idx, temp);
        }
        printClustering(clustering);
        List<List<Integer>> ids = new ArrayList<>();
        List<List<int[]>> clusterings = chooseClusterings(clustering, ids, false, cv);
        List<int[]> orderedIds = new ArrayList<>();
        List<List<int[]>> actualClusterings = filterAndOrderClusterings(clusterings, ids,
                orderedIds, ng);
        List<int[]> finalPureModel = purify(actualClusterings, orderedIds
        );

        printClustering(finalPureModel);

        return finalPureModel;
    }

    private List<List<int[]>> chooseClusterings(List<int[]> clustering, List<List<Integer>> outputIds,
                                                boolean need3, int[][] cv) {
        List<List<int[]>> clusterings = new ArrayList<>();
        boolean[] marked = new boolean[clustering.size()];
        boolean[] buffer = new boolean[this.numVariables()];

        int max = Math.min(clustering.size(), 1000);

        boolean[][] compatibility = new boolean[clustering.size()][clustering.size()];
        if (need3) {
            for (int i = 0; i < clustering.size() - 1; i++) {
                for (int j = i + 1; j < clustering.size(); j++) {
                    compatibility[i][j] = compatibility[j][i] = compatibleClusters(
                            clustering.get(i),
                            clustering.get(j), cv);
                }
            }
        }

        //Ideally, we should find all maximum cliques among "cluster nodes".
        //Heuristic: greedily build a set of clusters starting from each cluster.
        System.out.println("Total number of clusters: " + clustering.size());
        for (int i = 0; i < max; i++) {
            //System.out.println("Step " + i);
            List<Integer> nextIds = new ArrayList<>();
            List<int[]> newClustering = new ArrayList<>();
            nextIds.add(i);
            newClustering.add(clustering.get(i));
            for (int j = 0; j < clustering.size(); j++) {
                marked[j] = false;
            }
            marked[i] = true;
            int bestChoice;
            double bestScore = clustering.get(i).length;
            do {
                bestChoice = -1;
                next_choice:
                for (int j = 0; j < clustering.size(); j++) {
                    if (marked[j]) {
                        continue;
                    }
                    for (int[] ints : newClustering) {
                        if (need3 &&
                                !compatibility[j][clustering.indexOf(
                                        ints)]) {
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
    private boolean isNewClustering(List<List<int[]>> clusterings, List<int[]> newClustering) {

        nextClustering:
        for (List<int[]> clustering : clusterings) {

            nextOldCluster:
            for (Object value : clustering) {
                int[] cluster = (int[]) value;

                nextNewCluster:
                for (Object o : newClustering) {
                    int[] newCluster = (int[]) o;

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
     * This implementation uses the Purify class.
     */

    private List<int[]> purify(List<List<int[]>> actualClusterings, List<int[]> clusterIds) {

        if (!actualClusterings.isEmpty()) {
            List<int[]> partition = actualClusterings.get(0);
            printLatentClique(clusterIds.get(0));
            Clusters clustering = new Clusters();
            int clusterId = 0;
            printClustering(partition);

            for (int[] codes : partition) {
                for (int code : codes) {
                    String var = this.tetradTest.getVarNames()[code];
                    clustering.addToCluster(clusterId, var);
                }

                clusterId++;
            }

            List<List<Node>> partition2 = new ArrayList<>();

            for (Object o : partition) {
                int[] clusterIndices = (int[]) o;
                List<Node> cluster = new ArrayList<>();

                for (int clusterIndex : clusterIndices) {
                    cluster.add(this.tetradTest.getVariables().get(clusterIndex));
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
        return this.covarianceMatrix;
    }

    public int numVariables() {
        return this.numVariables;
    }

    public IndependenceTest getIndependenceTest() {
        return this.independenceTest;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    public boolean isVerbose() {
        return this.verbose;
    }
}





