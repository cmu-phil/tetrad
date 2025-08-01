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

import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.score.SemBicScore;
import edu.cmu.tetrad.search.utils.ClusterUtils;
import edu.cmu.tetrad.util.ChoiceGenerator;
import edu.cmu.tetrad.util.StatUtils;
import edu.cmu.tetrad.util.TetradLogger;
import org.ejml.data.DMatrixRMaj;
import org.ejml.dense.row.factory.DecompositionFactory_DDRM;
import org.ejml.interfaces.decomposition.EigenDecomposition_F64;
import org.ejml.simple.SimpleMatrix;
import org.ejml.simple.SimpleSVD;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class TrekSeparationClusters2 {

    private final List<Node> nodes;
    private final List<Integer> variables;
    private final Map<Set<Integer>, Integer> rankCache = new HashMap<>();

    /**
     * The correlation matrix as a SimpleMatrix.
     */
    private final SimpleMatrix S;
    private final int sampleSize;
    private double alpha = 0.01;
    private boolean includeStructureModel = false;
    private double penalty = 2;
    private boolean includeAllNodes = false;
    private boolean verbose = false;
    private boolean checkRank = true;

    public TrekSeparationClusters2(List<Node> variables, CovarianceMatrix cov, int sampleSize) {
        this.nodes = new ArrayList<>(variables);
        this.sampleSize = sampleSize;

        this.variables = new ArrayList<>(variables.size());
        for (int i = 0; i < variables.size(); i++) {
            this.variables.add(i);
        }

        this.S = new CovarianceMatrix(cov).getMatrix().getDataCopy();
    }

    private static @NotNull StringBuilder toNamesCluster(Collection<Integer> cluster, List<Node> nodes) {
        StringBuilder _sb = new StringBuilder();

        _sb.append("[");
        int count = 0;

        for (Integer var : cluster) {
            _sb.append(nodes.get(var));

            if (count++ < cluster.size() - 1) _sb.append(", ");
        }

        _sb.append("]");
        return _sb;
    }

    private static @NotNull String toNamesClusters(Set<Set<Integer>> clusters, List<Node> nodes) {
        StringBuilder sb = new StringBuilder();

        int count0 = 0;

        for (Collection<Integer> cluster : clusters) {
            StringBuilder _sb = toNamesCluster(cluster, nodes);

            if (count0++ < clusters.size() - 1) _sb.append("; ");

            sb.append(_sb);
        }

        return sb.toString();
    }

    private static void removeNested(Set<Set<Integer>> mergedClusters) {
        boolean _changed;
        do {
            _changed = mergedClusters.removeIf(
                    sub -> mergedClusters.stream()
                            .anyMatch(cluster -> cluster != sub && cluster.containsAll(sub))
            );
        } while (_changed);
    }

    // Entry point
    public Graph search() {
        int size = 3;
        int rank = 2;

        Set<Set<Integer>> P = findClustersAtRank(size, rank);
//        P.addAll(findClustersAtRank(3, 2));

        System.out.println("P  = " + toNamesClusters(P, nodes));

        removeNested(P);

        Set<Set<Integer>> usedClusters = new HashSet<>();
        Set<Set<Integer>> mergedClusters = new HashSet<>(P);

        boolean changed = false;

//        while (!P.isEmpty()) {
//            Set<Integer> seed = P.iterator().next();
//            Set<Integer> cluster = new HashSet<>(seed);
//            usedClusters.add(seed);
//            P.remove(seed);
//
//            boolean found = false;
//
//            do {
//                changed = false;
//                for (Iterator<Set<Integer>> it = P.iterator(); it.hasNext(); ) {
//                    Set<Integer> _cluster = it.next();
//                    if (usedClusters.contains(_cluster)) continue;
//
//                    if (!Collections.disjoint(_cluster, cluster)) {
//                        cluster.addAll(_cluster);
//                        usedClusters.add(_cluster);
//
//                        if (cluster.size() < rank || cluster.size() > variables.size() - rank) {
//                            break;
//                        }
//
//                        if (cluster.size() >= variables.size() / 2) {
//                            break;
//                        }
//
//                        if (checkRank && lookupCluster(cluster) != rank) {
//                            break;
//                        }
//
//                        System.out.println("Rank for " + toNamesCluster(cluster, nodes) + " = " + rank(cluster));
//
//                        it.remove();  // modify P
//                        changed = true;
//                        found = true;
//                        break;
//                    }
//                }
//            } while (changed);
//
//            if (found) {
//                // Remove any subset pairs from mergedClusters
//                mergedClusters.removeIf(cluster::containsAll);
//                mergedClusters.add(cluster);
//
//                System.out.println("Adding " + toNamesCluster(cluster, nodes) + " to " + toNamesClusters(mergedClusters, nodes));
//            }
//        }

        // ... outside until complete search is done:
        while (!P.isEmpty()) {
            Set<Integer> seed = P.iterator().next();
            Set<Integer> cluster = new HashSet<>(seed);
            usedClusters.add(seed);
            P.remove(seed);

            boolean extended;
            do {
                extended = false;
                Iterator<Set<Integer>> it = P.iterator();
                Set<Integer> frozen = new HashSet<>(cluster);

                while (it.hasNext()) {
                    Set<Integer> candidate = it.next();
                    if (usedClusters.contains(candidate)) continue;
                    if (Collections.disjoint(candidate, frozen)) continue;

                    Set<Integer> union = new HashSet<>(frozen);
                    union.addAll(candidate);

                    // Size constraints
                    if (union.size() < rank
                        || union.size() > variables.size() - rank
                        || union.size() >= variables.size() / 2) {
                        continue;  // skip early, do not mutate cluster
                    }

                    if (checkRank && lookupRank(union) != rank) {
                        continue;  // skip non‑rank‑2 cluster
                    }

//                    System.out.println("Union: " + union + " rank = " + lookupRank(union));

                    // Passed all tests—commit to new cluster
                    cluster = union;
                    usedClusters.add(candidate);
                    it.remove();
                    extended = true;
                    break;
                }
            } while (extended);

            // Now that cluster is fully grown and valid, replace nested subsets
            mergedClusters.removeIf(cluster::containsAll);

            System.out.println("Adding: " + toNamesCluster(cluster, nodes) + " rank = " + lookupRank(cluster));

            mergedClusters.add(cluster);
        }

        removeNested(mergedClusters);

//        removeNested(mergedClusters);

        List<Set<Integer>> clusters = new ArrayList<>();
        for (Set<Integer> cluster : mergedClusters) {
            clusters.add(new HashSet<>(cluster));
        }

        log("clusters = " + toNamesClusters(mergedClusters, nodes));

        List<Node> latents = defineLatents(clusters);
        Graph graph = convertSearchGraphClusters(clusters, latents, isIncludeAllNodes());

        if (includeStructureModel) {
            addStructureEdges(clusters, latents, graph);
        }

        return graph;
    }

    private void addStructureEdges(List<Set<Integer>> clusters, List<Node> latents, Graph graph) {
        try {
            List<List<Integer>> _clusters = new ArrayList<>();
            for (Set<Integer> cluster : clusters) {
                _clusters.add(new ArrayList<>(cluster));
            }

            List<SimpleMatrix> eigenvectors = LatentGraphBuilder.extractFirstEigenvectors(S, _clusters);
            SimpleMatrix latentsCov = LatentGraphBuilder.latentLatentCorrelationMatrix(S, _clusters, eigenvectors);
            CovarianceMatrix cov = new CovarianceMatrix(latents, TrekSeparationClusters.toDoubleArray(latentsCov), sampleSize);
            SemBicScore score = new SemBicScore(cov, getPenalty());
            Graph structureGraph = new PermutationSearch(new Boss(score)).search();

            for (Edge edge : structureGraph.getEdges()) {
                graph.addEdge(edge);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Set<Set<Integer>> findClustersAtRank(int size, int rank) {
        Set<Set<Integer>> clusters = new HashSet<>();

        ChoiceGenerator generator = new ChoiceGenerator(variables.size(), size);
        int[] choice;

        while ((choice = generator.next()) != null) {
            Set<Integer> cluster = new HashSet<>();
            for (int i : choice) {
                cluster.add(variables.get(i));
            }

            if (lookupRank(cluster) == rank) {
                clusters.add(cluster);
            }
        }

        return clusters;
    }

    private int lookupRank(Set<Integer> cluster) {
        if (!rankCache.containsKey(cluster)) {
            rankCache.put(cluster, rank(cluster));
        }

        return rankCache.get(cluster);
    }

    /**
     * You already have this implemented — we call your tested version.
     */
    private boolean isCluster(Set<Integer> cluster, int rank) {
        // Uses your existing isRankOne(List<String> clusterVars) method
        List<Integer> clusterList = new ArrayList<>(cluster);
        List<Integer> otherVars = new ArrayList<>(variables);
        otherVars.removeAll(clusterList);
        return lookupRank(clusterList, otherVars, rank);  // This is your method
    }

    // Dummy placeholder. Replace this with your own implementation.
    private boolean lookupRank(List<Integer> ySet, List<Integer> xSet, int rank) {
        int[] xIndices = new int[xSet.size()];
        int[] yIndices = new int[ySet.size()];

        for (int i = 0; i < xSet.size(); i++) {
            xIndices[i] = xSet.get(i);
        }

        for (int i = 0; i < ySet.size(); i++) {
            yIndices[i] = ySet.get(i);
        }

        return StatUtils.isCcaRankEqualTo(S, xIndices, yIndices, sampleSize, rank, alpha);
    }

    private int rank(Set<Integer> cluster) {
        List<Integer> xSet = new ArrayList<>(cluster);
        List<Integer> ySet = new ArrayList<>(variables);
        ySet.removeAll(xSet);

        int[] xIndices = new int[xSet.size()];
        int[] yIndices = new int[ySet.size()];

        for (int i = 0; i < xSet.size(); i++) {
            xIndices[i] = xSet.get(i);
        }

        for (int i = 0; i < ySet.size(); i++) {
            yIndices[i] = ySet.get(i);
        }

        return StatUtils.estimateCcaRank(S, xIndices, yIndices, sampleSize, alpha);
    }

    /**
     * Converts search graph nodes to a Graph object.
     *
     * @param clusters The set of sets of Node objects representing the clusters.
     * @return A Graph object representing the search graph nodes.
     */
    private Graph convertSearchGraphClusters(List<Set<Integer>> clusters, List<Node> latents, boolean includeAllNodes) {
        Graph graph = includeAllNodes ? new EdgeListGraph(this.nodes) : new EdgeListGraph();

        for (int i = 0; i < clusters.size(); i++) {
            graph.addNode(latents.get(i));

            for (int j : clusters.get(i)) {
                if (!graph.containsNode(nodes.get(j))) graph.addNode(nodes.get(j));
                graph.addDirectedEdge(latents.get(i), nodes.get(j));
            }
        }

        return graph;
    }

    private List<Node> defineLatents(List<Set<Integer>> clusters) {
        List<Node> latents = new ArrayList<>();

        for (int i = 0; i < clusters.size(); i++) {
            Node latent = new GraphNode(ClusterUtils.LATENT_PREFIX + (i + 1));
            latent.setNodeType(NodeType.LATENT);
            latents.add(latent);
        }

        return latents;
    }

    public void setAlpha(double alpha) {
        this.alpha = alpha;
    }

    public void setIncludeStructureModel(boolean includeStructureModel) {
        this.includeStructureModel = includeStructureModel;
    }

    public double getPenalty() {
        return penalty;
    }

    public void setPenalty(double penalty) {
        this.penalty = penalty;
    }

    public boolean isIncludeAllNodes() {
        return includeAllNodes;
    }

    public void setIncludeAllNodes(boolean includeAllNodes) {
        this.includeAllNodes = includeAllNodes;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    private void log(String s) {
        if (verbose) {
            TetradLogger.getInstance().log(s);
        }
    }

    public void setCheckRank(boolean checkRank) {
        this.checkRank = checkRank;
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

                EigenDecomposition_F64<DMatrixRMaj> eig = DecompositionFactory_DDRM.eig(submatrix.getNumCols(), true);
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
    }

}

