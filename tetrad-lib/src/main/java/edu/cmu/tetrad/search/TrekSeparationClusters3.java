/// ////////////////////////////////////////////////////////////////////////////
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
/// ////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.CorrelationMatrix;
import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.score.SemBicScore;
import edu.cmu.tetrad.search.utils.ClusterSignificance;
import edu.cmu.tetrad.search.utils.ClusterUtils;
import edu.cmu.tetrad.util.*;
import org.apache.commons.math3.distribution.NormalDistribution;
import org.apache.commons.math3.util.FastMath;
import org.ejml.data.DMatrixRMaj;
import org.ejml.dense.row.factory.DecompositionFactory_DDRM;
import org.ejml.interfaces.decomposition.EigenDecomposition_F64;
import org.ejml.simple.SimpleMatrix;
import org.ejml.simple.SimpleSVD;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.apache.commons.math3.util.FastMath.abs;
import static org.apache.commons.math3.util.FastMath.sqrt;


/**
 * Implements the Find One Factor Clusters (FOFC) algorithm by Erich Kummerfeld, which uses reasoning about vanishing
 * tetrads of algorithms to infer clusters of the measured variables in a dataset that each be explained by a single
 * latent variable. A reference is the following
 * <p>
 * Kummerfeld, E., &amp; Ramsey, J. (2016, August). Causal clustering for 1-factor measurement models. In Proceedings of
 * the 22nd ACM SIGKDD international conference on knowledge discovery and data mining (pp. 1655-1664).
 * <p>
 * The algorithm uses tests of vanishing tetrads (list of 4 variables that follow a certain pattern in the
 * exchangeability of latent paths with respect to the data). The notion of vanishing tetrads is an old one but is
 * explained in this book:
 * <p>
 * Spirtes, P., Glymour, C. N., Scheines, R., &amp; Heckerman, D. (2000). Causation, prediction, and search. MIT press.
 *
 * @author erichkummerfeld
 * @author peterspirtes
 * @author josephramsey
 * @version $Id: $Id
 * @see Ftfc
 */
public class TrekSeparationClusters3 {
    /**
     * The covariance matrix.
     */
    private final CorrelationMatrix corr;
    /**
     * The correlation matrix as a SimpleMatrix.
     */
    private final SimpleMatrix S;
    /**
     * The list of all variables.
     */
    private final List<Node> variables;
    /**
     * The significance level.
     */
    private final double alpha;
    /**
     * A standard normal distribution object used for statistical calculations within the Fofc class. The distribution
     * is characterized by a mean of 0 and a standard deviation of 1.
     */
    private final NormalDistribution normal = new NormalDistribution(0, 1);
    /**
     * The sample size.
     */
    private final int sampleSize;
    private final int[][] clusterSpecs;
    private final List<Node> dataNodes;
    private final double penalty;
    /**
     * The clusters that are output by the algorithm from the last call to search().
     */
    private Set<Set<Node>> clusters;
    /**
     * Whether verbose output is desired.
     */
    private boolean verbose;
    /**
     * Indicates whether all nodes should be included in the graph construction or processing. When set to true, the
     * algorithm will incorporate all nodes into the resulting graph, regardless of specific clustering or filtering
     * criteria. If false, only nodes that meet specific clustering or filtering conditions will be included.
     */
    private boolean includeAllNodes = false;
    private boolean includeStructureModel = false;

    /**
     * Conctructor using a dataset.
     *
     * @param dataSet The continuous dataset searched over.
     * @param alpha   The alpha significance cutoff.
     * @param penalty
     */
    public TrekSeparationClusters3(DataSet dataSet, int[][] clusterSpecs, double alpha, double penalty) {
        this(new CorrelationMatrix(dataSet), alpha, clusterSpecs, penalty);
    }

    /**
     * Conctructor using a dataset.
     *
     * @param dataSet The continuous dataset searched over.
     * @param alpha   The alpha significance cutoff.
     * @param penalty
     * @param ess     The expected sample size.
     */
    public TrekSeparationClusters3(DataSet dataSet, double alpha, double penalty, int[][] clusterSpecs, int ess) {
        this(new CorrelationMatrix(dataSet), alpha, penalty, clusterSpecs, ess);
    }

    /**
     * Constructor using a covariance matrix (could be a correlation matrix).
     *
     * @param cov     The covariance matrix.
     * @param alpha   The alpha level.
     * @param penalty
     */
    public TrekSeparationClusters3(CovarianceMatrix cov, double alpha, int[][] clusterSpecs, double penalty) {
        this(new CorrelationMatrix(cov), alpha, penalty, clusterSpecs, cov.getSampleSize());
    }

    /**
     * Constructor for the TrekSeparationClusters class using a covariance matrix.
     *
     * @param cov     The covariance matrix that could also be a correlation matrix.
     * @param alpha   The alpha level for significance cutoff.
     * @param penalty
     * @param ess     The expected sample size for the analysis.
     */
    public TrekSeparationClusters3(CovarianceMatrix cov, double alpha, double penalty, int[][] clusterSpecs, int ess) {
        this.variables = cov.getVariables();
        this.alpha = alpha;
        this.sampleSize = ess;
        this.penalty = penalty;
        this.dataNodes = cov.getVariables();

        for (int[] spec : clusterSpecs) {
            if (spec.length != 2) {
                throw new IllegalArgumentException("Cluster specs must have two elements");
            }

            if (spec[0] < 2 || spec[1] > spec[0]) {
                throw new IllegalArgumentException("Cluster spec must be of form a:b where a >= 2 and b <= a.");
            }
        }

        this.clusterSpecs = clusterSpecs;
        this.corr = new CorrelationMatrix(cov);
        this.S = this.corr.getMatrix().getDataCopy();
    }

    public static double[][] toDoubleArray(SimpleMatrix matrix) {
        int numRows = matrix.getNumRows();
        int numCols = matrix.getNumCols();
        double[][] result = new double[numRows][numCols];

        for (int i = 0; i < numRows; i++) {
            for (int j = 0; j < numCols; j++) {
                result[i][j] = matrix.get(i, j);
            }
        }

        return result;
    }

    private static @NotNull String toNamesClusters(Set<Set<Integer>> clusters, List<Node> variables) {
        StringBuilder sb = new StringBuilder();

        int count0 = 0;

        for (Set<Integer> cluster : clusters) {
            StringBuilder _sb = toNamesCluster(cluster, variables);

            if (count0++ < clusters.size() - 1) _sb.append("; ");

            sb.append(_sb);
        }

        return sb.toString();
    }

    private static @NotNull StringBuilder toNamesCluster(Set<Integer> cluster, List<Node> variables) {
        StringBuilder _sb = new StringBuilder();

        _sb.append("[");
        int count = 0;

        for (Integer var : cluster) {
            _sb.append(variables.get(var));

            if (count++ < cluster.size() - 1) _sb.append(", ");
        }

        _sb.append("]");
        return _sb;
    }

    /**
     * Runs the search and returns a graph of clusters with the ir respective latent parents.
     *
     * @return This graph.
     */
    public Graph search() {
        Set<Set<Integer>> allClusters;

        allClusters = estimateClusters();
        this.clusters = ClusterSignificance.variablesForIndicesSets(allClusters, variables);

        log("clusters = " + this.clusters);

        List<List<Integer>> clusters = new ArrayList<>();
        for (Set<Integer> cluster : allClusters) {
            clusters.add(new ArrayList<>(cluster));
        }

        List<Node> latents = defineLatents(clusters);
        Graph graph = convertSearchGraphClusters(clusters, latents, includeAllNodes);
        if (includeStructureModel) {
            addStructureEdges(clusters, latents, graph);
        }
        return graph;
    }

    /**
     * The clusters that are output by the algorithm from the last call to search().
     *
     * @return a {@link List} object
     */
    public Set<Set<Node>> getClusters() {
        return this.clusters;
    }

    /**
     * <p>Setter for the field <code>verbose</code>.</p>
     *
     * @param verbose a boolean
     */
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    /**
     * Retrieves a list of all variables.
     *
     * @return A list of integers representing all variables.
     */
    private List<Integer> allVariables() {
        List<Integer> _variables = new ArrayList<>();
        for (int i = 0; i < this.variables.size(); i++) _variables.add(i);
        return _variables;
    }

    /**
     * Estimates clusters using the tetrads-first algorithm.
     *
     * @return A set of lists of integers representing the clusters.
     */
    private Set<Set<Integer>> estimateClusters() {
        List<Integer> variables = allVariables();
        if (new HashSet<>(variables).size() != variables.size()) {
            throw new IllegalArgumentException("Variables must be unique.");
        }

        List<Set<Set<Integer>>> clusterList = new ArrayList<>();
        Set<Set<Integer>> allClusters = new HashSet<>();

        for (int i = 0; i < clusterSpecs.length; i++) {
            log("cluster spec: " + Arrays.toString(clusterSpecs[i]));
            Set<Set<Integer>> _clusters = findClustersOfSize(variables, clusterSpecs, i, clusterList);

            Set<Set<Integer>> baseClusters = new HashSet<>(_clusters);

//            if (clusterSpecs[i][1] == 2) {
//                baseClusters = new HashSet<>(_clusters);
//            }

            log("For " + Arrays.toString(clusterSpecs[i]) + "\nFound clusters: " + toNamesClusters(_clusters, dataNodes));
            clusterList.add(mergeOverlappingClusters(_clusters, baseClusters, clusterSpecs[i][0], clusterSpecs[i][1]));
            log("For " + Arrays.toString(clusterSpecs[i]) + "\nMerged clusters: " +
                toNamesClusters(mergeOverlappingClusters(_clusters, baseClusters, clusterSpecs[i][0], clusterSpecs[i][1]), dataNodes));

            for (int j = 0; j < i; j++) {
                if (clusterSpecs[j][1] == clusterSpecs[i][1]) {
                    clusterList.get(j).addAll(clusterList.get(i));
                    clusterList.set(j, mergeOverlappingClusters(clusterList.get(j), baseClusters, clusterSpecs[i][0], clusterSpecs[i][1]));
                    log("For " + Arrays.toString(clusterSpecs[i]) + "\nMerging rank " + clusterSpecs[j][1] + ": " + toNamesClusters(clusterList.get(j), dataNodes));
                    clusterList.get(i).clear();
                }
            }

            allClusters.clear();

            for (Set<Set<Integer>> cluster2 : clusterList) {
                allClusters.addAll(cluster2);
            }
        }

        log("final clusters = " + ClusterSignificance.variablesForIndicesSets(allClusters, this.variables));

        return allClusters;
    }

    private @NotNull Set<Set<Integer>> findClustersOfSize(List<Integer> variables, int[][] clusterSpecs, int i, List<Set<Set<Integer>>> clusterList) {
        ChoiceGenerator gen = new ChoiceGenerator(variables.size(), clusterSpecs[i][0]);
        int[] _choice;

        List<int[]> choices = new ArrayList<>();
        while ((_choice = gen.next()) != null) {
            choices.add(Arrays.copyOf(_choice, _choice.length));
        }

        Set<Set<Integer>> finalClusters = ConcurrentHashMap.newKeySet();

        choices.parallelStream().forEach(choice -> {
            int[] yIndices = new int[choice.length];

            boolean skip = false;
            for (int k = 0; k < i; k++) {
                for (Set<Integer> prevCluster : clusterList.get(k)) {
                    boolean allContained = true;
                    for (int c : choice) {
                        if (!prevCluster.contains(variables.get(c))) {
                            allContained = false;
                            break;
                        }
                    }
                    if (allContained) {
                        skip = true;
                        break;
                    }
                }
                if (skip) break;
            }
            if (skip) return;

            for (int q = 0; q < choice.length; q++) {
                yIndices[q] = variables.get(choice[q]);
            }

            int _rank = getRank(yIndices, variables, clusterList, i);

            if (_rank == clusterSpecs[i][1]) {
                List<Integer> _cluster = MathUtils.getInts(yIndices);

                System.out.println("_rank = " + _rank + ": " + toNamesCluster(new HashSet<>(_cluster), this.variables));

                if (clusterDependent(_cluster)) {
                    finalClusters.add(new HashSet<>(_cluster));
                }
            }
        });

        return finalClusters;
    }

    private int getRank(int[] cluster, List<Integer> variables, List<Set<Set<Integer>>> clusterList, int avoidToIndex) {
        Set<Integer> ySet = new HashSet<>();
        for (int y : cluster) {
            ySet.add(y);
        }

        int[] other = new int[variables.size() - cluster.length];

        int index = 0;

        for (int q = 0; q < variables.size(); q++) {
            boolean found = false;
            for (int y : cluster) {
                if (q == y) {
                    found = true;
                    break;
                }
            }
            if (found) continue;

            other[index++] = variables.get(q);
        }

        return RankTests.estimateRccaRank(S, other, cluster, sampleSize, alpha, 0.01);
    }

    private Set<Set<Integer>> mergeOverlappingClusters(Set<Set<Integer>> clusters,
                                                       Set<Set<Integer>> baseClusters,
                                                       int size, int rank) {
        System.out.println("Base clusters: " + toNamesClusters(baseClusters, dataNodes));
        boolean merged;

        do {
            merged = false;
            Set<Set<Integer>> newClusters = new HashSet<>();

            for (Set<Integer> cluster1 : clusters) {
                Set<Integer> mergedCluster = new HashSet<>(cluster1);
                boolean passing = true;
                boolean localMerged = false;

                C:
                for (Set<Integer> cluster2 : clusters) {
                    if (cluster1 == cluster2) continue;

                    Set<Integer> intersection = new HashSet<>(cluster1);
                    intersection.retainAll(cluster2);

                    if (!intersection.isEmpty() && !mergedCluster.containsAll(cluster2)) {
                        mergedCluster.addAll(cluster2);
                        localMerged = true;

                        // If size == 2, check that all pairs in mergedCluster are in baseClusters
                        if (false) {
                            List<Integer> mergedList = new ArrayList<>(mergedCluster);
                            int n = mergedList.size();
                            outer:
                            for (int i = 0; i < n; i++) {
                                for (int j = i + 1; j < n; j++) {
                                    Set<Integer> pair = new HashSet<>();
                                    pair.add(mergedList.get(i));
                                    pair.add(mergedList.get(j));
                                    if (!baseClusters.contains(pair)) {
                                        passing = false;
                                        break outer;
                                    }
                                }
                            }
                        }
                        // If size > 2, no subset check — merge willy-nilly
                    }
                }

                if (passing && mergedCluster.size() >= size) {
                    newClusters.add(mergedCluster);
                }
                if (localMerged) {
                    merged = true;
                }
            }

            if (!newClusters.equals(clusters)) {
                clusters = newClusters;
            } else {
                merged = false;
            }
        } while (merged);

        return clusters;
    }

    private boolean clusterDependent(List<Integer> cluster) {
//        if (true) return true;

        int numDependencies = 0;
        int all = 0;

        for (int i = 0; i < cluster.size(); i++) {
            for (int j = i + 1; j < cluster.size(); j++) {
                double r = this.corr.getValue(cluster.get(i), cluster.get(j));

                if (Double.isNaN(r)) {
                    continue;
                }

                int n = this.corr.getSampleSize();
                int zSize = 0; // Unconditional check.

                double q = .5 * (FastMath.log(1.0 + abs(r)) - FastMath.log(1.0 - abs(r)));
                double df = n - 3. - zSize;

                double fisherZ = sqrt(df) * q;

                if (2 * (1.0 - this.normal.cumulativeProbability(abs(fisherZ))) < alpha) {
                    numDependencies++;
                }

                all++;
            }
        }

        return numDependencies == all;
    }

    /**
     * Converts search graph nodes to a Graph object.
     *
     * @param clusters The set of sets of Node objects representing the clusters.
     * @return A Graph object representing the search graph nodes.
     */
    private Graph convertSearchGraphNodes(Set<Set<Node>> clusters, List<Node> latents, boolean includeAllNodes) {
        Graph graph = includeAllNodes ? new EdgeListGraph(this.variables) : new EdgeListGraph();

        List<Set<Node>> _clusters = new ArrayList<>(clusters);

        for (int i = 0; i < _clusters.size(); i++) {
            graph.addNode(latents.get(i));

            for (Node node : _clusters.get(i)) {
                if (!graph.containsNode(node)) graph.addNode(node);
                graph.addDirectedEdge(latents.get(i), node);
            }
        }

        return graph;
    }

    private List<Node> defineLatents(List<List<Integer>> clusters) {
        List<Node> latents = new ArrayList<>();

        for (int i = 0; i < clusters.size(); i++) {
            Node latent = new GraphNode(ClusterUtils.LATENT_PREFIX + (i + 1));
            latent.setNodeType(NodeType.LATENT);
            latents.add(latent);
        }

        return latents;
    }

    private Graph convertSearchGraphClusters(List<List<Integer>> clusters, List<Node> latents, boolean includeAllNodes) {
        Graph graph = includeAllNodes ? new EdgeListGraph(this.variables) : new EdgeListGraph();

        for (int i = 0; i < clusters.size(); i++) {
            graph.addNode(latents.get(i));

            for (int j : clusters.get(i)) {
                if (!graph.containsNode(variables.get(j))) graph.addNode(variables.get(j));
                graph.addDirectedEdge(latents.get(i), variables.get(j));
            }
        }

        return graph;
    }

    private void addStructureEdges(List<List<Integer>> clusters, List<Node> latents, Graph graph) {
        try {
            List<SimpleMatrix> eigenvectors = LatentGraphBuilder.extractFirstEigenvectors(S, clusters);
            SimpleMatrix latentsCov = LatentGraphBuilder.latentLatentCorrelationMatrix(S, clusters, eigenvectors);
            CovarianceMatrix cov = new CovarianceMatrix(latents, toDoubleArray(latentsCov), sampleSize);
            SemBicScore score = new SemBicScore(cov, penalty);
            Graph structureGraph = new PermutationSearch(new Boss(score)).search();

            for (Edge edge : structureGraph.getEdges()) {
                graph.addEdge(edge);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Converts search graph nodes to a Graph object.
     *
     * @param allClusters The set of sets of Node objects representing the clusters.
     * @return A Graph object representing the search graph nodes.
     */
    private Graph convertToGraph(List<List<Integer>> allClusters, List<Node> latents, boolean includeAllNodes) {
        Set<Set<Node>> _clustering = new HashSet<>();

        for (List<Integer> cluster : allClusters) {
            Set<Node> nodes = new HashSet<>();

            for (int i : cluster) {
                nodes.add(this.variables.get(i));
            }

            _clustering.add(nodes);
        }

        return convertSearchGraphNodes(_clustering, latents, includeAllNodes);
    }

    /**
     * Logs a message if the verbose flag is set to true.
     *
     * @param s The message to log.
     */
    private void log(String s) {
        if (this.verbose) {
            TetradLogger.getInstance().log(s);
        }
    }

    /**
     * Indicates whether all nodes should be included in the graph construction or processing. When set to true, the
     * algorithm will incorporate all nodes into the resulting graph, regardless of specific clustering or filtering
     * criteria. If false, only nodes that meet specific clustering or filtering conditions will be included.
     *
     * @param includeAllNodes True if all nodes should be included in the graph output.
     */
    public void setIncludeAllNodes(boolean includeAllNodes) {
        this.includeAllNodes = includeAllNodes;
    }

    public void setIncludeStructureModel(boolean includeStructure) {
        this.includeStructureModel = includeStructure;
    }

    private static class LatentGraphBuilder {

        // Assume data is standardized
        public static SimpleMatrix[] extractLatentScores(SimpleMatrix data, List<List<Integer>> clusters) {
            SimpleMatrix[] latentScores = new SimpleMatrix[clusters.size()];

            for (int i = 0; i < clusters.size(); i++) {
                List<Integer> cluster = clusters.get(i);

                // Extract submatrix for this cluster
                SimpleMatrix subData = extractColumns(data, cluster);

                // Get the first principal component
                SimpleSVD<SimpleMatrix> svd = subData.svd();
                SimpleMatrix u = svd.getU();
                SimpleMatrix s = svd.getW();

                // Score = first column of U * first singular value
                SimpleMatrix scores = u.cols(0, 0).scale(s.get(0, 0));
                latentScores[i] = scores;
            }

            return latentScores;
        }

        public static List<SimpleMatrix> extractFirstEigenvectors(SimpleMatrix S, List<List<Integer>> clusters) {
            List<SimpleMatrix> eigenvectors = new ArrayList<>();

            for (List<Integer> cluster : clusters) {
                SimpleMatrix submatrix = extractSubmatrix(S, cluster);

                EigenDecomposition_F64<DMatrixRMaj> eig = DecompositionFactory_DDRM.eig(submatrix.numCols(), true);
                eig.decompose(submatrix.getDDRM());

                // Get eigenvector corresponding to largest eigenvalue
                double maxEigenvalue = Double.NEGATIVE_INFINITY;
                SimpleMatrix principalEigenvector = null;

                for (int i = 0; i < eig.getNumberOfEigenvalues(); i++) {
                    if (eig.getEigenvalue(i).isReal()) {
                        double value = eig.getEigenvalue(i).getReal();
                        if (value > maxEigenvalue) {
                            maxEigenvalue = value;
                            principalEigenvector = SimpleMatrix.wrap(eig.getEigenVector(i));
                        }
                    }
                }

                eigenvectors.add(principalEigenvector);
            }

            return eigenvectors;
        }

//        private static SimpleMatrix extractSubmatrix(SimpleMatrix S, List<Integer> indices) {
//            int n = indices.size();
//            SimpleMatrix sub = new SimpleMatrix(n, n);
//            for (int i = 0; i < n; i++) {
//                for (int j = 0; j < n; j++) {
//                    sub.set(i, j, S.get(indices.get(i), indices.get(j)));
//                }
//            }
//            return sub;
//        }

        public static SimpleMatrix latentLatentCorrelationMatrix(
                SimpleMatrix S,
                List<List<Integer>> clusters,
                List<SimpleMatrix> eigenvectors) {

            int K = clusters.size();
            SimpleMatrix R = new SimpleMatrix(K, K);

            for (int i = 0; i < K; i++) {
                for (int j = i; j < K; j++) {
                    List<Integer> ci = clusters.get(i);
                    List<Integer> cj = clusters.get(j);
                    SimpleMatrix vi = eigenvectors.get(i);
                    SimpleMatrix vj = eigenvectors.get(j);

                    SimpleMatrix Sij = extractCrossBlock(S, ci, cj);
                    SimpleMatrix Sii = extractSubmatrix(S, ci);
                    SimpleMatrix Sjj = extractSubmatrix(S, cj);

                    double numerator = vi.transpose().mult(Sij).mult(vj).get(0);
                    double denomLeft = vi.transpose().mult(Sii).mult(vi).get(0);
                    double denomRight = vj.transpose().mult(Sjj).mult(vj).get(0);

                    double corr = numerator / Math.sqrt(denomLeft * denomRight);
                    R.set(i, j, corr);
                    R.set(j, i, corr); // symmetric
                }
            }

            return R;
        }

        // Helper: Extract a submatrix of S with rows in A and cols in B
        private static SimpleMatrix extractCrossBlock(SimpleMatrix S, List<Integer> rows, List<Integer> cols) {
            SimpleMatrix result = new SimpleMatrix(rows.size(), cols.size());
            for (int i = 0; i < rows.size(); i++) {
                for (int j = 0; j < cols.size(); j++) {
                    result.set(i, j, S.get(rows.get(i), cols.get(j)));
                }
            }
            return result;
        }

        // Helper: Extract submatrix of S using indices
        private static SimpleMatrix extractSubmatrix(SimpleMatrix S, List<Integer> indices) {
            return extractCrossBlock(S, indices, indices);
        }

        private static SimpleMatrix extractColumns(SimpleMatrix data, List<Integer> colIndices) {
            int numRows = data.getNumRows();
            int numCols = colIndices.size();
            SimpleMatrix result = new SimpleMatrix(numRows, numCols);

            for (int j = 0; j < colIndices.size(); j++) {
                int col = colIndices.get(j);
                for (int i = 0; i < numRows; i++) {
                    result.set(i, j, data.get(i, col));
                }
            }

            return result;
        }

        private static int[] toIntArray(List<Integer> list) {
            return list.stream().mapToInt(i -> i).toArray();
        }
    }
}




