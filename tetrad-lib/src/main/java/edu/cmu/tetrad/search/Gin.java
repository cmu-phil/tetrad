package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.CorrelationMatrix;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.ntad_test.Cca;
import edu.cmu.tetrad.search.utils.ClusterSignificance;
import edu.cmu.tetrad.sem.ReidentifyVariables;
import edu.cmu.tetrad.util.RankTests;
import edu.cmu.tetrad.util.StatUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.math3.distribution.ChiSquaredDistribution;
import org.apache.commons.math3.distribution.NormalDistribution;
import org.apache.commons.math3.util.FastMath;
import org.ejml.simple.SimpleMatrix;
import org.ejml.simple.SimpleSVD;

import java.util.*;

import static org.apache.commons.math3.util.FastMath.abs;
import static org.apache.commons.math3.util.FastMath.sqrt;

/**
 * The Gin class implements an algorithm for causal discovery, leveraging
 * statistical independence tests and clustering techniques to infer
 * latent structures and build a graphical representation of causal relationships.
 *
 * This class executes a multi-step process including the identification of
 * causal clusters, creation of latent nodes linked to observed variables,
 * and orientation of directed edges among the latent variables.
 *
 * Key functionalities:
 * - Use of a raw marginal independence test to compute p-values for
 *   independence checks.
 * - Identification of causal clusters using FOFC (fast orientation for causation)
 *   and further refinement via statistical tests.
 * - Construction of a causal graph accommodating both observed and latent nodes.
 *
 * The algorithm relies heavily on covariance matrices and additional statistical
 * procedures such as Fisher's method for p-value combination and singular value
 * decomposition (SVD).
 *
 * The algorithm is tailored for use with time-series or multivariate datasets,
 * where causal inference in the presence of latent confounding variables is necessary.
 *
 * Thread-safety: Not guaranteed. This class is not designed to be thread-safe.
 */
public class Gin {

    /**
     * The significance level threshold used in statistical tests to determine causal
     * relationships or dependencies within the data. This parameter typically ranges
     * between 0 and 1, where a smaller value indicates stricter criteria for significance.
     */
    private final double alpha;
    /**
     * An instance of {@link RawMarginalIndependenceTest} used to perform tests
     * for marginal independence between variables during computations within
     * the {@code Gin} class.
     *
     * This field encapsulates the logic for evaluating statistical independence
     * between pairs of variables, which is a foundational operation for the
     * methods provided by the enclosing class.
     */
    private final RawMarginalIndependenceTest test;
    private CorrelationMatrix corr;
    private SimpleMatrix S;
    private DataSet dataSet;
    private List<Node> variables;
    private final NormalDistribution normal = new NormalDistribution(0, 1);

    /**
     * Constructs a Gin object with the specified significance level and marginal independence test.
     *
     * @param alpha the significance level to be used for the hypothesis tests; must be a value between 0 and 1
     * @param test an implementation of the {@code RawMarginalIndependenceTest} interface to perform variable independence testing
     */
    public Gin(double alpha, RawMarginalIndependenceTest test) {
        this.alpha = alpha;
        this.test = test;
    }

    /**
     * Executes a causal discovery algorithm on the provided dataset to construct
     * a graph representing the causal relationships between variables.
     *
     * @param data the dataset containing variables and their associated covariance matrix;
     *             must contain sufficient information for causal analysis.
     * @return a graph structure representing causal relationships, including observed
     *         and latent variables, derived from the input dataset.
     */
    public Graph search(DataSet data) {
        SimpleMatrix cov = new SimpleMatrix(data.getCovarianceMatrix().getDataCopy());
        SimpleMatrix rawData = new SimpleMatrix(data.getDoubleData().getDataCopy());

        this.corr = new CorrelationMatrix(data);
        this.S = corr.getMatrix().getDataCopy();
        this.dataSet = data;
        this.variables = data.getVariables();

        List<Node> variables = data.getVariables();

        // Step 1: Find causal clusters
        List<List<Integer>> clusters = findCausalClusters(data, cov, rawData);

        // Step 2: Create latent nodes and build cluster graph
        Graph graph = new EdgeListGraph();
        List<Node> latents = new ArrayList<>();
        for (Node node : variables) graph.addNode(node);

        int latentId = 0;
        for (List<Integer> cluster : clusters) {
            Node latent = new GraphNode("L" + (++latentId));
            latent.setNodeType(NodeType.LATENT);
            graph.addNode(latent);
            latents.add(latent);
            for (int idx : cluster) {
                graph.addDirectedEdge(latent, variables.get(idx));
            }
        }

        Map<Double, Pair<List<Integer>, List<Integer>>> pValues = new HashMap<>();

        // Step 3: Orient latent variables
        for (int i = 0; i < clusters.size(); i++) {
            for (int j = 0; j < clusters.size(); j++) {
                if (i == j) continue;

                List<Integer> Z = clusters.get(i); // cause candidates
                List<Integer> Y = clusters.get(j); // effect candidates

                if (Z.isEmpty() || Y.isEmpty()) continue;

                double[] e = computeE(data, cov, Y, Z);
                List<Double> pvals = new ArrayList<>();


                for (int z : Z) {
                    double[] zData = rawData.extractVector(false, z).getDDRM().getData();
                    double pval = 0;
                    try {
                        pval = test.computePValue(e, zData);
                    } catch (InterruptedException ex) {
                        throw new RuntimeException(ex);
                    }
                    pvals.add(Math.min(pval, 1 - 1e-5));
                }

                double p = fisher(pvals);
                pValues.put(p, Pair.of(Z, Y));

                if (p > alpha && !graph.isAncestorOf(latents.get(j), latents.get(i))) {
                    graph.addDirectedEdge(latents.get(i), latents.get(j));
                }
            }
        }

        List<Double> keys = new ArrayList<>(pValues.keySet());
        keys.sort(Collections.reverseOrder());

        for (double p : keys) {
            if (p > alpha) {
                Pair<List<Integer>, List<Integer>> pair = pValues.get(p);
                List<Integer> Z = pair.getLeft();
                List<Integer> Y = pair.getRight();
                Node latentZ = latents.get(clusters.indexOf(Z));
                Node latentY = latents.get(clusters.indexOf(Y));

                if (!graph.isAncestorOf(latentY, latentZ)) {
                    graph.addDirectedEdge(latentZ, latentY);
                }
            }
        }

        return graph;
    }

    /**
     * Computes the result of a matrix operation based on the provided dataset, covariance matrix,
     * and specified subsets of variables. This method extracts specific rows and columns
     * based on the provided indices and performs singular value decomposition (SVD)
     * to compute the resulting array.
     *
     * @param data the dataset containing observations and values for variables
     * @param cov the covariance matrix of the dataset
     * @param X a list of indices indicating a subset of variables from the dataset
     * @param Z a list of indices identifying another subset of variables from the dataset
     * @return an array of computed double values resulting from the matrix operations
     */
    private double[] computeE(DataSet data, SimpleMatrix cov, List<Integer> X, List<Integer> Z) {
        SimpleMatrix covM = new SimpleMatrix(Z.size(), X.size());
        for (int i = 0; i < Z.size(); i++) {
            for (int j = 0; j < X.size(); j++) {
                covM.set(i, j, cov.get(Z.get(i), X.get(j)));
            }
        }
        SimpleSVD<SimpleMatrix> svd = covM.svd();
        SimpleMatrix v = svd.getV();
        SimpleMatrix omega = v.extractVector(false, v.getNumCols() - 1);

        SimpleMatrix subData = new SimpleMatrix(data.getNumRows(), X.size());
        for (int i = 0; i < data.getNumRows(); i++) {
            for (int j = 0; j < X.size(); j++) {
                subData.set(i, j, data.getDouble(i, X.get(j)));
            }
        }
        return subData.mult(omega).getDDRM().getData();
    }

    /**
     * Computes the Fisher's combined probability test statistic for a list of p-values
     * and returns the cumulative probability from the chi-squared distribution.
     *
     * @param pvals a list of p-values to combine; should not contain zero or NaN values.
     * @return the cumulative probability resulting from the Fisher's test, or 0 if the
     *         input list is empty or contains invalid values.
     */
    private double fisher(List<Double> pvals) {
        if (pvals.isEmpty()) return 0;
        for (Double pval : pvals) {
            if (pval == 0 || Double.isNaN(pval)) return 0;
        }
        double stat = -2 * pvals.stream().mapToDouble(Math::log).sum();
        int df = 2 * pvals.size();
        return new ChiSquaredDistribution(df).cumulativeProbability(stat);
    }

    private Set<List<Integer>> estimateClusters() {
        List<Integer> variables = allVariables();
        if (new HashSet<>(variables).size() != variables.size()) {
            throw new IllegalArgumentException("Variables must be unique.");
        }

        Set<List<Integer>> clusters = new HashSet<>();
        Set<Integer> usedVariables = new HashSet<>();

        for (int i = 0; i < variables.size(); i++) {
            for (int j = i + 1; j < variables.size(); j++) {
                if (usedVariables.contains(variables.get(i)) && usedVariables.contains(variables.get(j))) {
                    continue;
                }

                int[] yIndices = new int[]{variables.get(i), variables.get(j)};
                int[] xIndices = new int[variables.size() - 2];

                int index = 0;

                for (int k = 0; k < variables.size(); k++) {
                    if (k != i && k != j) {
                        xIndices[index++] = variables.get(k);
                    }
                }

                double p = RankTests.getCcaPValueRankLE(S, xIndices, yIndices, dataSet.getNumRows(), 1);

                System.out.println("p = " + p);

                if (p >= alpha) {
                    List<Integer> _cluster = new ArrayList<>();
                    _cluster.add(variables.get(i));
                    _cluster.add(variables.get(j));

                    if (clusterDependent(_cluster)) {
                        clusters.add(_cluster);
                        usedVariables.add(variables.get(i));
                        usedVariables.add(variables.get(j));
                    }
                }
            }
        }

        clusters = mergeOverlappingClusters(clusters);

        System.out.println("final clusters = " + ClusterSignificance.variablesForIndices(clusters, this.variables));

        return clusters;
    }

    private Set<List<Integer>> mergeOverlappingClusters(Set<List<Integer>> clusters) {
        boolean merged;
        do {
            merged = false;
            Set<List<Integer>> newClusters = new HashSet<>();
            Set<List<Integer>> used = new HashSet<>();

            for (List<Integer> cluster1 : clusters) {
                if (used.contains(cluster1)) continue;

                List<Integer> mergedCluster = new ArrayList<>(cluster1);

                for (List<Integer> cluster2 : clusters) {
                    if (cluster1 == cluster2 || used.contains(cluster2)) continue;

                    Set<Integer> intersection = new HashSet<>(cluster1);
                    intersection.retainAll(cluster2);

                    if (!intersection.isEmpty()) {
                        mergedCluster.addAll(cluster2);
                        used.add(cluster2);
                        merged = true;
                    }
                }

                used.add(cluster1);
                newClusters.add(mergedCluster);
            }

            clusters = newClusters;
        } while (merged);

        return clusters;
    }

    private boolean clusterDependent(List<Integer> cluster) {
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




    private List<Integer> allVariables() {
        List<Integer> _variables = new ArrayList<>();
        for (int i = 0; i < this.variables.size(); i++) _variables.add(i);
        return _variables;
    }

    private List<List<Integer>> findCausalClusters(DataSet data, SimpleMatrix cov, SimpleMatrix rawData) {
        Fofc fofc = new Fofc(data, new Cca(data.getDoubleData().getDataCopy(), false), alpha);
        Graph fofcGraph = fofc.search();
        List<Node> vars = data.getVariables();

        List<Node> fofcLatents = ReidentifyVariables.getLatents(fofcGraph);

        List<List<Integer>> clusters = new ArrayList<>();

        for (Node l : fofcLatents) {
            List<Node> children = fofcGraph.getChildren(l);
            List<Integer> cluster = new ArrayList<>();
            for (Node n : children) {
                int e = vars.indexOf(n);
                cluster.add(e);
            }

            clusters.add(cluster);
        }

        int numVars = data.getNumColumns();
        Set<Integer> candidates = new HashSet<>();
        for (int i = 0; i < numVars; i++) candidates.add(i);
        List<Node> nodes = data.getVariables();

        for (List<Integer> cluster : clusters) {
            cluster.forEach(candidates::remove);
        }

        for (List<Integer> cluster : new ArrayList<>(clusters)) {
            Set<Integer> remainder = new HashSet<>(candidates);
            cluster.forEach(remainder::remove);

            double[] e = computeE(data, cov, cluster, new ArrayList<>(remainder));
            List<Double> pvals = new ArrayList<>();
            for (int z : remainder) {
                double[] zData = rawData.extractVector(false, z).getDDRM().getData();
                try {
                    pvals.add(Math.max(test.computePValue(e, zData), 1e-5));
                } catch (InterruptedException ex) {
                    throw new RuntimeException(ex);
                }
            }
            if (fisher(pvals) < alpha) continue;

            // Greedy grow
            boolean grown;
            do {
                grown = false;
                K:
                for (int k : new HashSet<>(remainder)) {
                    List<Integer> candidate = new ArrayList<>(cluster);
                    candidate.add(k);

                    for (int k1 = 0; k1 < cluster.size(); k1++) {
                        for (int l1 = k1 + 1; l1 < cluster.size(); l1++) {
                            try {
                                if (!((IndependenceTest) test).checkIndependence(nodes.get(cluster.get(k1)), nodes.get(cluster.get(l1))).isDependent()) {
                                    continue K;
                                }
                            } catch (InterruptedException ex) {
                                throw new RuntimeException(ex);
                            }
                        }
                    }

                    Set<Integer> rest = new HashSet<>(candidates);
                    candidate.forEach(rest::remove);
                    if (rest.isEmpty()) continue;

                    e = computeE(data, cov, candidate, new ArrayList<>(rest));
                    pvals = new ArrayList<>();
                    for (int z : rest) {
                        double[] zData = rawData.extractVector(false, z).getDDRM().getData();
                        try {
                            pvals.add(Math.max(test.computePValue(e, zData), 1e-5));
                        } catch (InterruptedException ex) {
                            throw new RuntimeException(ex);
                        }
                    }
                    if (fisher(pvals) >= alpha) {
                        cluster = candidate;
                        remainder.remove(k);
                        grown = true;
                        break;
                    }
                }
            } while (grown);

            clusters.add(cluster);
            cluster.forEach(candidates::remove);
        }

        for (List<Integer> cluster : clusters) {
            candidates.removeAll(cluster);
        }

        for (int i = 0; i < numVars; i++) {
            J:
            for (int j = i + 1; j < numVars; j++) {
                if (!candidates.contains(i) || !candidates.contains(j)) continue;

                List<Integer> cluster = new ArrayList<>(List.of(i, j));

                for (int k = 0; k < cluster.size(); k++) {
                    for (int l = k + 1; l < cluster.size(); l++) {
                        try {
                            if (!((IndependenceTest) test).checkIndependence(nodes.get(cluster.get(k)), nodes.get(cluster.get(l))).isDependent()) {
                                continue J;
                            }
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                    }
                }

                Set<Integer> remainder = new HashSet<>(candidates);
                cluster.forEach(remainder::remove);

                double[] e = computeE(data, cov, cluster, new ArrayList<>(remainder));
                List<Double> pvals = new ArrayList<>();
                for (int z : remainder) {
                    double[] zData = rawData.extractVector(false, z).getDDRM().getData();
                    try {
                        pvals.add(Math.max(test.computePValue(e, zData), 1e-5));
                    } catch (InterruptedException ex) {
                        throw new RuntimeException(ex);
                    }
                }
                if (fisher(pvals) < alpha) continue;

                // Greedy grow
                boolean grown;
                do {
                    grown = false;
                    K:
                    for (int k : new HashSet<>(remainder)) {
                        List<Integer> candidate = new ArrayList<>(cluster);
                        candidate.add(k);

                        for (int k1 = 0; k1 < cluster.size(); k1++) {
                            for (int l1 = k1 + 1; l1 < cluster.size(); l1++) {
                                try {
                                    if (!((IndependenceTest) test).checkIndependence(nodes.get(cluster.get(k1)), nodes.get(cluster.get(l1))).isDependent()) {
                                        continue K;
                                    }
                                } catch (InterruptedException ex) {
                                    throw new RuntimeException(ex);
                                }
                            }
                        }

                        Set<Integer> rest = new HashSet<>(candidates);
                        candidate.forEach(rest::remove);
                        if (rest.isEmpty()) continue;

                        e = computeE(data, cov, candidate, new ArrayList<>(rest));
                        pvals = new ArrayList<>();
                        for (int z : rest) {
                            double[] zData = rawData.extractVector(false, z).getDDRM().getData();
                            try {
                                pvals.add(Math.max(test.computePValue(e, zData), 1e-5));
                            } catch (InterruptedException ex) {
                                throw new RuntimeException(ex);
                            }
                        }
                        if (fisher(pvals) >= alpha) {
                            cluster = candidate;
                            remainder.remove(k);
                            grown = true;
                            break;
                        }
                    }
                } while (grown);

                clusters.add(cluster);
                cluster.forEach(candidates::remove);
            }
        }


        return clusters;
    }
}
