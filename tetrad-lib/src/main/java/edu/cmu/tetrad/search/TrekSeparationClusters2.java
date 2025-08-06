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
import edu.cmu.tetrad.util.RankTests;
import edu.cmu.tetrad.util.TetradLogger;
import org.ejml.data.DMatrixRMaj;
import org.ejml.dense.row.factory.DecompositionFactory_DDRM;
import org.ejml.interfaces.decomposition.EigenDecomposition_F64;
import org.ejml.simple.SimpleMatrix;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;

public class TrekSeparationClusters2 {

    private final List<Node> nodes;
    private final List<Integer> variables;
    private final Map<Set<Integer>, Integer> rankCache = new HashMap<>();
    private final int sampleSize;
    /**
     * The correlation matrix as a SimpleMatrix.
     */
    private SimpleMatrix S;
    private double alpha = 0.01;
    private boolean includeStructureModel = false;
    private double penalty = 2;
    private boolean includeAllNodes = false;
    private boolean verbose = false;

    public TrekSeparationClusters2(List<Node> variables, CovarianceMatrix cov, int sampleSize) {
        this.nodes = new ArrayList<>(variables);
        this.sampleSize = sampleSize;

        this.variables = new ArrayList<>(variables.size());
        for (int i = 0; i < variables.size(); i++) {
            this.variables.add(i);
        }

        this.S = new CovarianceMatrix(cov).getMatrix().getDataCopy();
        this.S = this.S.plus(SimpleMatrix.identity(S.getNumRows()).scale(0.001));
    }

    public static Set<Set<Integer>> selectBestDisjointClusters(List<Set<Integer>> allClusters) {
        List<Set<Integer>> sorted = new ArrayList<>(new HashSet<>(allClusters));

        sorted.sort(Comparator.comparingInt(Set::size));
        sorted = sorted.reversed();

        List<Set<Integer>> result = new ArrayList<>();
        Set<Integer> covered = new HashSet<>();

        for (Set<Integer> cluster : sorted) {
            if (Collections.disjoint(cluster, covered)) {
                result.add(cluster);
                covered.addAll(cluster);
            }
        }

        return new HashSet<>(result);
    }

    public Graph search(int size, int rank) {
//        Set<Set<Integer>> _clusters = getDepthFirstClusters(size, rank);
        Set<Set<Integer>> _clusters = getRunSagWithOrder(variables, size, rank);
//        Set<Set<Integer>> _clusters = getRandomSagClusters(30, size, rank);

        List<Set<Integer>> clusters = new ArrayList<>(_clusters);

        log("clusters = " + toNamesClusters(new HashSet<>(clusters)));

        List<Node> latents = defineLatents(clusters);
        Graph graph = convertSearchGraphClusters(clusters, latents, isIncludeAllNodes());

        if (includeStructureModel) {
            addStructureEdges(clusters, latents, graph);
        }

        return graph;
    }

    // Entry point
    public Graph search2() {
        int size = 3;
        int rank = 2;

        List<Integer> variables = new ArrayList<>(nodes.size());
        for (int i = 0; i < nodes.size(); i++) variables.add(i);

        Set<Set<Integer>> mergedClusters = getRunSagWithOrder(variables, size, rank);

        log("clusters = " + toNamesClusters(mergedClusters));

        List<Set<Integer>> clusters = new ArrayList<>(mergedClusters);

        List<Node> latents = defineLatents(clusters);
        Graph graph = convertSearchGraphClusters(clusters, latents, isIncludeAllNodes());

        if (includeStructureModel) {
            addStructureEdges(clusters, latents, graph);
        }

        return graph;
    }

    private @NotNull Set<Set<Integer>> getRunSagWithOrder(List<Integer> vars, int size, int rank) {
        Set<Set<Integer>> P = findClustersAtRank(vars, size, rank);

        removeNested(P);

        Set<Set<Integer>> mergedClusters = new HashSet<>();
        Set<Integer> used = new HashSet<>();

        while (!P.isEmpty()) {
            Set<Integer> seed = P.iterator().next();
            P.remove(seed);

            if (!Collections.disjoint(used, seed)) {
                continue;
            }

            Set<Integer> cluster = new HashSet<>(seed);
            boolean extended;

            do {
                extended = false;
                Iterator<Set<Integer>> it = P.iterator();

                W:
                while (it.hasNext()) {
                    Set<Integer> candidate = it.next();
                    if (!Collections.disjoint(used, candidate)) continue;
                    if (Collections.disjoint(candidate, cluster)) continue;

                    Set<Integer> union = new HashSet<>(cluster);
                    union.addAll(candidate);

                    int rankOfUnion = lookupRank(union);
                    System.out.println("Trying union: " + toNamesCluster(union) + " rank = " + rankOfUnion);

                    if (rankOfUnion <= rank) {

                        // Accept this union, grow cluster
                        cluster = union;
                        it.remove();
                        extended = true;
                        break;
                    }
                }
            } while (extended);

            int finalRank = lookupRank(cluster);
            mergedClusters.removeIf(cluster::containsAll);  // Avoid nesting
            System.out.println("Adding cluster: " + toNamesCluster(cluster) + " rank = " + finalRank);
            mergedClusters.add(cluster);
            used.addAll(cluster);
        }

        removeNested(mergedClusters);
        System.out.println("Merged clusters = " + toNamesClusters(mergedClusters));
        return mergedClusters;
    }

    private boolean allSubsetsOK(int size, int rank, Set<Integer> union) {
        List<Integer> _union = new ArrayList<>(union);
        ChoiceGenerator gen2 = new ChoiceGenerator(_union.size(), size);
        int[] choice2;

        while ((choice2 = gen2.next()) != null) {
            Set<Integer> _union2 = new HashSet<>();
            for (Integer i : choice2) {
                _union2.add(_union.get(i));
            }

            if (lookupRank(_union2) > rank) {
                return false;
            }
        }

        return true;
    }

    public Set<Set<Integer>> getRandomSagClusters(int numTrials, int size, int rank) {
        List<Integer> allVars = variables; // e.g., 0..V-1
        List<Set<Integer>> allClusters = new ArrayList<>();

        for (int t = 0; t < numTrials; t++) {
            Collections.shuffle(allVars);
            Set<Set<Integer>> trialClusters = getRunSagWithOrder(allVars, size, rank);
            allClusters.addAll(trialClusters);
        }

        return selectBestDisjointClusters(allClusters);
    }

    private void removeNested(Set<Set<Integer>> mergedClusters) {
        boolean _changed;
        do {
            _changed = mergedClusters.removeIf(
                    sub -> mergedClusters.stream()
                            .anyMatch(cluster -> !cluster.equals(sub) && cluster.containsAll(sub))
            );
        } while (_changed);
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

    private Set<Set<Integer>> findClustersAtRank(List<Integer> vars, int size, int rank) {
        Set<Set<Integer>> clusters = new HashSet<>();

        ChoiceGenerator generator = new ChoiceGenerator(vars.size(), size);
        int[] choice;

        while ((choice = generator.next()) != null) {
            Set<Integer> cluster = new HashSet<>();
            for (int i : choice) {
                cluster.add(vars.get(i));
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

    private @NotNull StringBuilder toNamesCluster(Collection<Integer> cluster) {
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

    private @NotNull String toNamesClusters(Set<Set<Integer>> clusters) {
        StringBuilder sb = new StringBuilder();

        int count0 = 0;

        for (Collection<Integer> cluster : clusters) {
            StringBuilder _sb = toNamesCluster(cluster);

            if (count0++ < clusters.size() - 1) _sb.append("; ");

            sb.append(_sb);
        }

        return sb.toString();
    }

    private int rank(Set<Integer> cluster) {
        List<Integer> ySet = new ArrayList<>(cluster);
        List<Integer> xSet = new ArrayList<>(variables);
        xSet.removeAll(ySet);

        int[] xIndices = new int[xSet.size()];
        int[] yIndices = new int[ySet.size()];

        for (int i = 0; i < xSet.size(); i++) {
            xIndices[i] = xSet.get(i);
        }

        for (int i = 0; i < ySet.size(); i++) {
            yIndices[i] = ySet.get(i);
        }

        int rank = RankTests.estimateCcaRank(S, xIndices, yIndices, sampleSize, alpha);

        System.out.println("rank: " + rank);

        return rank;
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

    private Set<Set<Integer>> getDepthFirstClusters(int size, int rank) {
        List<Integer> vars = new ArrayList<>();
        for (int i = 0; i < nodes.size(); i++) {
            vars.add(i);
        }

        Set<Set<Integer>> P = findClustersAtRank(vars, size, rank);
        removeNested(P);

        // Step 2: Expand each seed cluster depth-first
        Set<Set<Integer>> allExpandedClusters = new HashSet<>();
        for (Set<Integer> seed : P) {
            Set<Set<Integer>> leaves = new HashSet<>();
            expandClusterDFS(seed, P, rank, new HashSet<>(), leaves, new HashSet<>());
            allExpandedClusters.addAll(leaves);
        }

        // Step 3: Select disjoint clusters from largest to smallest
        List<Set<Integer>> mergedClusters = new ArrayList<>(selectDisjointClusters(allExpandedClusters));
        return new HashSet<>(mergedClusters);
    }

    private void expandClusterDFS(Set<Integer> cluster,
                                  Set<Set<Integer>> P,
                                  int rank,
                                  Set<Set<Integer>> visited,
                                  Set<Set<Integer>> leafClusters, Set<Integer> used) {
        boolean extended = false;

        System.out.println("P size = " + P.size());

        for (Set<Integer> candidate : P) {
            if (Collections.disjoint(cluster, candidate)) continue;

            Set<Integer> union = new HashSet<>(cluster);
            union.addAll(candidate);

            if (union.equals(cluster)) continue;
            if (visited.contains(union)) continue;

            int unionRank = lookupRank(union);
            if (unionRank != rank) continue;

            visited.add(union);
            Set<Integer> _used = new HashSet<>(used);
            _used.addAll(union);

            System.out.println("_used = " + _used + " rank = " + lookupRank(union));

            expandClusterDFS(union, P, rank, visited, leafClusters, _used);
            extended = true;
        }

        if (!extended) {
            leafClusters.add(cluster);
        }
    }

    private Set<Set<Integer>> selectDisjointClusters(Collection<Set<Integer>> clusters) {
        List<Set<Integer>> sorted = new ArrayList<>(new HashSet<>(clusters));
        sorted.sort((a, b) -> Integer.compare(b.size(), a.size()));

        List<Set<Integer>> result = new ArrayList<>();
        Set<Integer> used = new HashSet<>();

        for (Set<Integer> cluster : sorted) {
            if (Collections.disjoint(used, cluster)) {
                result.add(cluster);
                used.addAll(cluster);
            }
        }

        return new HashSet<>(result);
    }

    private String toNamesClusters(Collection<Set<Integer>> clusters) {
        return clusters.stream()
                .map(this::toNamesCluster)
                .collect(Collectors.joining(", ", "[", "]"));
    }

    private String toNamesCluster(Set<Integer> cluster) {
        return cluster.stream()
                .map(i -> nodes.get(i).getName())
                .collect(Collectors.joining(" ", "{", "}"));
    }

    private static class LatentGraphBuilder {
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
    }
}

