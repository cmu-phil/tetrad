/**
 * Implements Trek Separation algorithm for finding latent variable clusters. This class analyzes covariance matrices to
 * identify clusters of observed variables that share common latent parents. It uses rank-based tests to determine trek
 * separations between variable sets.
 * <p>
 * Copyright (C) 1998-2022 by Peter Spirtes, Richard Scheines, Joseph Ramsey, and Clark Glymour.
 * <p>
 * This program is free software; you can redistribute it and/or modify it under the terms of the GNU General Public
 * License as published by the Free Software Foundation; either version 2 of the License, or (at your option) any later
 * version.
 */
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

/**
 * The TrekSeparationClusters2 class implements methods for detecting and analyzing clusters of variables using trek
 * separation tests. This class is designed to identify latent structure in a given covariance matrix with capabilities
 * for clustering, ranking, and graph construction.
 * <p>
 * It uses various parameters such as rank, penalties, and testing settings to guide the process and adjust the behavior
 * of the clustering algorithm. The main functionalities include searching for latent clusters, generating random
 * clusters, identifying disjoint clusters, and constructing resulting graphical models.
 */
public class TrekSeparationClusters {
    /**
     * List of observed variables/nodes
     */
    private final List<Node> nodes;
    /**
     * List of variable indices
     */
    private final List<Integer> variables;
    /**
     * Cache of previously computed ranks
     */
    private final Map<Set<Integer>, Integer> rankCache = new HashMap<>();
    /**
     * Sample size for statistical tests
     */
    private final int sampleSize;
    /**
     * The covariance/correlation matrix
     */
    private SimpleMatrix S;
    /**
     * Alpha level for rank tests
     */
    private double alpha = 0.01;
    /**
     * Whether to include structure model between latents
     */
    private boolean includeStructureModel = false;
    /**
     * Penalty discount for structure model
     */
    private double penalty = 2;
    /**
     * Whether to include all nodes in output graph
     */
    private boolean includeAllNodes = false;
    /**
     * Whether to output verbose logging
     */
    private boolean verbose = false;

    /**
     * Constructs a TrekSeparationClusters2 object, initializes the node and variable lists, and adjusts the covariance
     * matrix with a small scaling factor to ensure numerical stability.
     *
     * @param variables  The list of Node objects representing the variables to be analyzed.
     * @param cov        The covariance matrix of the observed variables.
     * @param sampleSize The number of samples in the dataset.
     */
    public TrekSeparationClusters(List<Node> variables, CovarianceMatrix cov, int sampleSize) {
        this.nodes = new ArrayList<>(variables);
        this.sampleSize = sampleSize;

        this.variables = new ArrayList<>(variables.size());
        for (int i = 0; i < variables.size(); i++) {
            this.variables.add(i);
        }

        this.S = new CovarianceMatrix(cov).getMatrix().getDataCopy();
        this.S = this.S.plus(SimpleMatrix.identity(S.getNumRows()).scale(0.001));
    }


    /**
     * Searches for latent clusters using specified size and rank parameters.
     *
     * @param clusterSpecs int[i][0] is the ith size, int[i][1] is th ith rank.
     * @return Graph containing identified latent structure
     */
    public Graph search(int[][] clusterSpecs) {
        Set<Set<Integer>> _clusters = estimateClusters(clusterSpecs);

        List<Set<Integer>> clusters = new ArrayList<>(_clusters);

        log("clusters = " + toNamesClusters(new HashSet<>(clusters)));

        List<Node> latents = defineLatents(clusters);
        Graph graph = convertSearchGraphClusters(clusters, latents, includeAllNodes);

        if (includeStructureModel) {
            addStructureEdges(clusters, latents, graph);
        }

        return graph;
    }

    /**
     * Estimates clusters based on the provided specifications, processes overlapping clusters, and returns a set of
     * merged unique clusters.
     *
     * @param clusterSpecs a 2D array where each row defines cluster specifications. The first element in each row
     *                     specifies the size of the cluster, and the second element specifies the rank.
     * @return a set of sets, where each inner set represents a unique cluster identified and merged according to the
     * given specifications.
     * @throws IllegalArgumentException if the variables used for clustering are not unique.
     */
    private Set<Set<Integer>> estimateClusters(int[][] clusterSpecs) {
        List<Integer> variables = allVariables();
        if (new HashSet<>(variables).size() != variables.size()) {
            throw new IllegalArgumentException("Variables must be unique.");
        }

        List<Set<Set<Integer>>> clusterList = new ArrayList<>();
        Set<Set<Integer>> allClusters = new HashSet<>();

        for (int i = 0; i < clusterSpecs.length; i++) {
            log("cluster spec: " + Arrays.toString(clusterSpecs[i]));
            int size = clusterSpecs[i][0];
            int rank = clusterSpecs[i][1];
            Set<Set<Integer>> _clusters = getRunSequentialClusterSearch(variables, size, rank);

            Set<Set<Integer>> baseClusters = new HashSet<>(_clusters);

            log("For " + Arrays.toString(clusterSpecs[i]) + "\nFound clusters: " + toNamesClusters(_clusters));
            clusterList.add(mergeOverlappingClusters(_clusters, baseClusters, clusterSpecs[i][0], clusterSpecs[i][1]));
            log("For " + Arrays.toString(clusterSpecs[i]) + "\nMerged clusters: " +
                toNamesClusters(mergeOverlappingClusters(_clusters, baseClusters, clusterSpecs[i][0], clusterSpecs[i][1])));

            for (int j = 0; j < i; j++) {
                if (clusterSpecs[j][1] == clusterSpecs[i][1]) {
                    clusterList.get(j).addAll(clusterList.get(i));
                    clusterList.set(j, mergeOverlappingClusters(clusterList.get(j), baseClusters, clusterSpecs[i][0], clusterSpecs[i][1]));
                    log("For " + Arrays.toString(clusterSpecs[i]) + "\nMerging rank " + clusterSpecs[j][1] + ": " + toNamesClusters(clusterList.get(j)));
                    clusterList.get(i).clear();
                }
            }

            allClusters.clear();

            for (Set<Set<Integer>> cluster2 : clusterList) {
                allClusters.addAll(cluster2);
            }
        }

        log("final clusters = " + toNamesClusters(allClusters));

        return allClusters;
    }

    /**
     * Merges overlapping clusters based on the given criteria and parameters. The merging continues iteratively until
     * no further merging is possible.
     *
     * @param clusters     the initial set of clusters to be merged
     * @param baseClusters the base set of clusters for reference during the merging process
     * @param size         the minimum acceptable size for a cluster to qualify for merging
     * @param rank         the upper limit for the rank of any resulting merged cluster
     * @return a set of merged clusters, with any overlapping clusters combined
     */
    private Set<Set<Integer>> mergeOverlappingClusters(Set<Set<Integer>> clusters,
                                                       Set<Set<Integer>> baseClusters,
                                                       int size, int rank) {
        System.out.println("Base clusters: " + toNamesClusters(baseClusters));
        boolean merged;

        do {
            merged = false;
            Set<Set<Integer>> newClusters = new HashSet<>();

            for (Set<Integer> cluster1 : clusters) {
                Set<Integer> mergedCluster = new HashSet<>(cluster1);
                boolean localMerged = false;

                C:
                for (Set<Integer> cluster2 : clusters) {
                    if (cluster1 == cluster2) continue;

                    Set<Integer> intersection = new HashSet<>(cluster1);
                    intersection.retainAll(cluster2);

                    if (!intersection.isEmpty() && !mergedCluster.containsAll(cluster2)) {
                        mergedCluster.addAll(cluster2);

                        if (lookupRank(mergedCluster) > rank) {
                            continue;
                        }

                        localMerged = true;
                    }
                }

                if (mergedCluster.size() >= size) {
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
     * Sets the alpha value, which may be used as a significance level or parameter threshold in the underlying analysis
     * or computation within the class.
     *
     * @param alpha The alpha value to be set. It should be provided as a double, and typically represents a probability
     *              level or tuning parameter depending on the context of its use.
     */
    public void setAlpha(double alpha) {
        this.alpha = alpha;
    }

    /**
     * Sets whether to include structure models in the analysis or computation.
     *
     * @param includeStructureModel A boolean value indicating whether structure models should be included. If true,
     *                              structure models will be considered in the process; if false, they will be
     *                              excluded.
     */
    public void setIncludeStructureModel(boolean includeStructureModel) {
        this.includeStructureModel = includeStructureModel;
    }

    /**
     * Sets the penalty value.
     *
     * @param penalty the penalty to be set, must be a positive double value
     */
    public void setPenalty(double penalty) {
        this.penalty = penalty;
    }

    /**
     * Sets whether all nodes should be included or not.
     *
     * @param includeAllNodes a boolean value where true indicates that all nodes should be included, and false
     *                        indicates otherwise.
     */
    public void setIncludeAllNodes(boolean includeAllNodes) {
        this.includeAllNodes = includeAllNodes;
    }

    /**
     * Sets the verbosity mode for the current operation or process.
     *
     * @param verbose a boolean value where true enables verbose mode, providing detailed log or output information, and
     *                false disables it.
     */
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    /**
     * Performs a sequential cluster search within the given variables, using the specified size and rank criteria. The
     * method iteratively finds clusters that meet the rank condition, merges overlapping or related clusters, and
     * ensures that no nested clusters exist in the final result.
     *
     * @param vars A list of integers representing the variables to analyze.
     * @param size The size of the initial clusters to consider during the search.
     * @param rank The target rank used to determine cluster validity and merging criteria.
     * @return A set of sets where each inner set represents a cluster of integers identified during the search.
     */
    private @NotNull Set<Set<Integer>> getRunSequentialClusterSearch(List<Integer> vars, int size, int rank) {
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

    /**
     * Removes nested clusters from a set of merged clusters. A cluster is considered nested if it is a subset of
     * another cluster within the set. The method iteratively checks and removes such nested clusters until no changes
     * occur.
     *
     * @param mergedClusters A set of sets, where each inner set represents a cluster of integers. The input is expected
     *                       to potentially contain nested clusters, which will be removed to leave only non-nested
     *                       clusters.
     */
    private void removeNested(Set<Set<Integer>> mergedClusters) {
        boolean _changed;
        do {
            _changed = mergedClusters.removeIf(
                    sub -> mergedClusters.stream()
                            .anyMatch(cluster -> !cluster.equals(sub) && cluster.containsAll(sub))
            );
        } while (_changed);
    }

    /**
     * Adds structure edges to the given graph based on provided clusters and latent nodes. The method processes
     * clusters, derives a structure graph using a permutation search, and adds the resulting edges to the specified
     * graph.
     *
     * @param clusters The list of sets where each set represents a cluster of integers that denote related elements.
     * @param latents  The list of latent nodes to be used for building the latent structure and covariance matrix.
     * @param graph    The graph to which the derived structure edges will be added.
     */
    private void addStructureEdges(List<Set<Integer>> clusters, List<Node> latents, Graph graph) {
        try {
            List<List<Integer>> _clusters = new ArrayList<>();
            for (Set<Integer> cluster : clusters) {
                _clusters.add(new ArrayList<>(cluster));
            }

            List<SimpleMatrix> eigenvectors = LatentGraphBuilder.extractFirstEigenvectors(S, _clusters);
            SimpleMatrix latentsCov = LatentGraphBuilder.latentLatentCorrelationMatrix(S, _clusters, eigenvectors);
            CovarianceMatrix cov = new CovarianceMatrix(latents, TrekSeparationClusters2.toDoubleArray(latentsCov), sampleSize);
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
     * Finds all clusters of a specified size from the given list of variables, where each cluster satisfies the given
     * rank constraint.
     *
     * @param vars A list of integers representing the variables to analyze.
     * @param size The size of clusters to generate from the variables.
     * @param rank The rank constraint that each cluster must satisfy.
     * @return A set of sets, where each inner set represents a cluster of integers that meets the specified rank
     * constraint.
     */
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

    /**
     * Retrieves the rank of a specified cluster. The method first checks if the rank for the given cluster is already
     * computed and stored in a cache. If not, it computes the rank using the defined rank computation method and
     * updates the cache.
     *
     * @param cluster A set of integers representing the cluster for which the rank is to be determined.
     * @return An integer representing the calculated or cached rank of the given cluster.
     */
    private int lookupRank(Set<Integer> cluster) {
        if (!rankCache.containsKey(cluster)) {
            rankCache.put(cluster, rank(cluster));
        }

        return rankCache.get(cluster);
    }

    /**
     * Converts a collection of integer cluster indices to their corresponding names based on the node mappings and
     * returns them as a string in a formatted name cluster.
     *
     * @param cluster A collection of integer indices representing the cluster elements. Each index corresponds to a
     *                specific node in the nodes mapping.
     * @return A {@code StringBuilder} containing the formatted names cluster as a string. The names are enclosed in
     * square brackets and separated by commas.
     */
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

    /**
     * Converts a set of clusters, where each cluster is represented as a set of integer indices, to a formatted string
     * representation using their corresponding names. This method combines the names of all clusters into a single
     * string, with individual clusters separated by a semicolon.
     *
     * @param clusters A set of sets where each inner set represents a cluster of integers. Each integer corresponds to
     *                 a specific node in the nodes mapping.
     * @return A non-null string containing the formatted cluster names. Each cluster is enclosed in square brackets,
     * its elements are separated by commas, and clusters are separated by semicolons.
     */
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

    /**
     * Computes the rank of the specified cluster using Canonical Correlation Analysis (CCA). This method evaluates the
     * association between the supplied cluster and the complement of the cluster within the given set of variables. The
     * computed rank is determined based on the input covariance matrix, sample size, and alpha level for significance
     * testing.
     *
     * @param cluster A set of integers representing the cluster for which the rank is to be calculated. Each integer
     *                corresponds to a variable index in the analysis.
     * @return An integer representing the estimated rank of the provided cluster.
     */
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

        return RankTests.estimateCcaRank(S, xIndices, yIndices, sampleSize, alpha);
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

    /**
     * Defines and creates a list of latent nodes based on the given clusters. Each latent node is assigned a unique
     * identifier and marked as a latent node type.
     *
     * @param clusters A list of sets, where each set represents a cluster of integers. The size of the list determines
     *                 the number of latent nodes to be created.
     * @return A list of Node objects, each representing a latent variable corresponding to a cluster.
     */
    private List<Node> defineLatents(List<Set<Integer>> clusters) {
        List<Node> latents = new ArrayList<>();

        for (int i = 0; i < clusters.size(); i++) {
            Node latent = new GraphNode(ClusterUtils.LATENT_PREFIX + (i + 1));
            latent.setNodeType(NodeType.LATENT);
            latents.add(latent);
        }

        return latents;
    }

    /**
     * Logs the provided message if verbose logging is enabled.
     *
     * @param s the message to be logged
     */
    private void log(String s) {
        if (verbose) {
            TetradLogger.getInstance().log(s);
        }
    }

    /**
     * Identifies clusters of nodes using a depth-first search expansion strategy.
     *
     * @param size the number of nodes in the input graph.
     * @param rank the rank used to determine the clustering threshold.
     * @return a set of depth-first expanded clusters, where each cluster is represented as a set of integers.
     */
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

    /**
     * Expands a given cluster using a depth-first search (DFS) approach by finding and merging overlapping sets from
     * the provided set of candidate clusters, based on specific criteria such as rank and disjoint conditions. Updates
     * the visited clusters during the traversal and collects leaf clusters when no further expansion is possible.
     *
     * @param cluster      The current cluster being expanded.
     * @param P            The set of candidate clusters to explore for potential expansions.
     * @param rank         The rank of the current cluster, used as a filtering condition for union operations.
     * @param visited      The set of clusters that have already been visited during the expansion process to avoid
     *                     duplicates.
     * @param leafClusters The collection where identified leaf clusters (that cannot be further expanded) are stored.
     * @param used         The set of elements that are already part of the current expansion process.
     */
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

    /**
     * Selects a subset of disjoint clusters from the given collection of clusters. A cluster is selected if it does not
     * share any elements with clusters that have already been selected. Clusters are prioritized by size, with larger
     * clusters being considered first.
     *
     * @param clusters the collection of clusters to process, where each cluster is represented as a set of integers
     * @return a set of disjoint clusters selected from the input collection
     */
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

    /**
     * Converts a collection of integer-based clusters into a single formatted string representation. Each cluster is
     * transformed into a string representation of names and concatenated into a single output string.
     *
     * @param clusters a collection of sets, where each set represents a cluster containing integers
     * @return a formatted string representation of the clusters, with each cluster represented as a string, combined
     * together and delimited by commas, enclosed in square brackets
     */
    private String toNamesClusters(Collection<Set<Integer>> clusters) {
        return clusters.stream()
                .map(this::toNamesCluster)
                .collect(Collectors.joining(", ", "[", "]"));
    }

    /**
     * Converts a set of cluster indices into a formatted string representation of the cluster names.
     *
     * @param cluster a set of integer indices representing the cluster
     * @return a string with the cluster names enclosed in curly braces and separated by spaces
     */
    private String toNamesCluster(Set<Integer> cluster) {
        return cluster.stream()
                .map(i -> nodes.get(i).getName())
                .collect(Collectors.joining(" ", "{", "}"));
    }

    /**
     * The LatentGraphBuilder class provides methods for processing and analyzing latent structures in matrices using
     * eigenvector-based techniques. These methods are particularly useful for extracting important patterns and
     * relationships within data, such as through clustering and correlation matrix computation.
     */
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

        /**
         * Computes the latent-latent correlation matrix for a given similarity matrix, clusters, and corresponding
         * eigenvectors. The method calculates pairwise correlations between latent variables associated with different
         * clusters.
         *
         * @param S            The similarity matrix, assumed to be square and symmetric.
         * @param clusters     A list of clusters, where each cluster is represented as a list of indices indicating the
         *                     rows and columns of the similarity matrix that belong to the cluster.
         * @param eigenvectors A list of eigenvector matrices, where each matrix corresponds to the eigenvectors
         *                     calculated for each cluster.
         * @return A symmetric matrix representing the pairwise correlations between latents associated with the
         * specified clusters.
         */
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

