package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.CorrelationMatrix;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.utils.ClusterSignificance;
import edu.cmu.tetrad.util.ChoiceGenerator;
import edu.cmu.tetrad.util.RankTests;
import edu.cmu.tetrad.util.TetradLogger;
import org.ejml.simple.SimpleMatrix;

import java.util.*;


/**
 * Find General Factor Clusters (FGFC). This generalized FOFC and FTFC to first find clusters using pure 2-tads (pure
 * tetrads) and then clusters using pure 3-tads (pure sextads) out of the remaining variables. We do not use an n-tad
 * test here since we need to check rank, so we will check rank directly. (This is equivqalent to using the CCA n-tad
 * test.)
 * <p>
 * Kummerfeld, E., &amp; Ramsey, J. (2016, August). Causal clustering for 1-factor measurement models. In Proceedings of
 * the 22nd ACM SIGKDD international conference on knowledge discovery and data mining (pp. 1655-1664).
 * <p>
 * Spirtes, P., Glymour, C. N., Scheines, R., &amp; Heckerman, D. (2000). Causation, prediction, and search. MIT press.
 *
 * @author erichkummerfeld
 * @author peterspirtes
 * @author josephramsey
 * @version $Id: $Id
 * @see Ftfc
 */
public class Fgfc {
    /**
     * The correlation matrix.
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
     * Sample size.
     */
    private final int n;
    /**
     * Whether verbose output is desired.
     */
    private boolean verbose;
    /**
     * A cache of pure tetrads.
     */
    private Set<Set<Integer>> pureTets;
    /**
     * A cache of impure tetrads.
     */
    private Set<Set<Integer>> impureTets;
    /**
     * Represents the fraction of purity required when appending variables to clusters. This value determines the
     * strictness of the constraints applied during the clustering process. A higher value implies stricter requirements
     * for purity when merging variables.
     */
    private double appendPurityFraction = 1;

    /**
     * Conctructor.
     *
     * @param dataSet The continuous dataset searched over.
     * @param alpha   The alpha significance cutoff.
     */
    public Fgfc(DataSet dataSet, double alpha) {
        this.variables = dataSet.getVariables();
        this.alpha = alpha;
        this.S = new CorrelationMatrix(dataSet).getMatrix().getSimpleMatrix();
        this.n = dataSet.getNumRows();
    }

    /**
     * Runs the search and returns a graph of clusters with the ir respective latent parents.
     *
     * @return This a map from discovered clusters to their ranks.
     */
    public Map<List<Integer>, Integer> findClusters() {
        this.pureTets = new HashSet<>();
        this.impureTets = new HashSet<>();

        Map<List<Integer>, Integer> clustersToRanks  = new HashMap<>();

        for (int rank = 1; rank <= 2; rank++) {
            Set<List<Integer>> clusters = estimateClustersSag(rank);
            for (List<Integer> cluster : clusters) {
                clustersToRanks.put(cluster, rank);
            }
        }

        return clustersToRanks;
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
    private Set<List<Integer>> estimateClustersSag(int rank) {
        List<Integer> variables = allVariables();
        if (new HashSet<>(variables).size() != variables.size()) {
            throw new IllegalArgumentException("Variables must be unique.");
        }

        int clusterSize = 2 * (rank + 1);

        Set<List<Integer>> pureClusters = findPureClusters(clusterSize);
        Set<List<Integer>> mixedClusters = findMixedClusters(clusterSize);
        Set<List<Integer>> allClusters = new HashSet<>(pureClusters);
        allClusters.addAll(mixedClusters);

        Set<List<Integer>> finalClusters = new HashSet<>();

        for (List<Integer> cluster : new HashSet<>(allClusters)) {
            if (cluster.size() >= clusterSize) {
                finalClusters.add(cluster);
            }
        }

        Set<Integer> unionClustered2 = union(finalClusters);
        Set<List<Integer>> mixedClusters2 = findMixedClusters(clusterSize);

        finalClusters.addAll(mixedClusters2);

        System.out.println("final clusters rank " + rank + " = " + ClusterSignificance.variablesForIndices(finalClusters, this.variables));

        for (List<Integer> cluster : finalClusters) {
            new HashMap<List<Integer>, Integer>().put(cluster, rank);
        }

        return finalClusters;
    }

    /**
     * Finds clusters of size clusterSize or higher for the tetrad-first algorithm.
     */
    private Set<List<Integer>> findPureClusters(int clusterSize) {
        List<Integer> variables = allVariables();

        log(variables.toString());

        List<Integer> unclustered = new ArrayList<>(variables);
        unclustered.removeAll(union(new HashSet<List<Integer>>()));

        if (variables.size() < clusterSize) return new HashSet<>();

        ChoiceGenerator gen = new ChoiceGenerator(variables.size(), clusterSize);
        int[] choice;

        CHOICE:
        while ((choice = gen.next()) != null) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }

            for (int c : choice) {
                if (!unclustered.contains(variables.get(c))) {
                    continue CHOICE;
                }
            }

            List<Integer> cluster = new  ArrayList<>();

            for (int c : choice) {
                cluster.add(variables.get(c));
            }

            // Note that purity needs to be assessed with respect to all the variables to
            // remove all latent-measure impurities between pairs of latents.
            if (pure(cluster) == Purity.PURE) {
                growCluster(unclustered, cluster);

                if (this.verbose) {
                    log("Cluster found: " + ClusterSignificance.variablesForIndices(cluster, this.variables));
                }

                new HashSet<List<Integer>>().add(cluster);
                unclustered.removeAll(cluster);
            }
        }

        return new HashSet<>();
    }

    private void growCluster(List<Integer> unclustered, List<Integer> cluster) {
        // iterate with an Iterator so we can remove from unclustered safely
        Iterator<Integer> iterator = unclustered.iterator();

        while (iterator.hasNext()) {
            int o = iterator.next();
            if (cluster.contains(o)) continue;

            int size = cluster.size();
            int tests = 0, pureCount = 0;

            // Check all 5-combinations from the current cluster with the candidate o
            for (int i = 0; i < size; i++) {
                for (int j = i + 1; j < size; j++) {
                    for (int k = j + 1; k < size; k++) {
                        tests++;
                        List<Integer> tetrad = List.of(
                                cluster.get(i), cluster.get(j), cluster.get(k), o
                        );
                        if (pure(tetrad) == Purity.PURE) pureCount++;
                    }
                }
            }

            double frac = tests == 0 ? 0.0 : (pureCount / (double) tests);

            if (frac >= appendPurityFraction) {
                cluster.add(o);     // NOTE: use add(), not addLast()
                iterator.remove();
            }
        }
    }

    /**
     * Finds clusters of size 3 for the SAG algorithm.
     *
     * @return A set of lists of integers representing the mixed clusters.
     */
    private Set<List<Integer>> findMixedClusters(int clusterSize) {
        Set<List<Integer>> mixedClusters = new HashSet<>();

        if (union(new HashSet<List<Integer>>()).isEmpty()) {
            return new HashSet<>();
        }

        List<Integer> unclustered = new ArrayList<>(allVariables());
        unclustered.removeAll(new HashSet<>(union(new HashSet<List<Integer>>())));

        List<Integer> variables = new ArrayList<>(unclustered);

        ChoiceGenerator gen = new ChoiceGenerator(unclustered.size(), clusterSize - 1);
        int[] choice;

        CHOICE:
        while ((choice = gen.next()) != null) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }

            for (int c : choice) {
                if (!unclustered.contains(variables.get(c))) {
                    continue CHOICE;
                }
            }

            List<Integer> cluster = new  ArrayList<>();

            for (int c : choice) {
                cluster.add(variables.get(c));
            }

            for (int o : allVariables()) {
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }

                if (cluster.contains(o)) continue;

                List<Integer> _cluster = new ArrayList<>(cluster);
                _cluster.add(o);

                if (!vanishes(_cluster)) {
                    continue CHOICE;
                }
            }

            mixedClusters.add(cluster);
            unclustered.removeAll(cluster);

            if (this.verbose) {
                log("5-cluster found: " + ClusterSignificance.variablesForIndices(cluster, this.variables));
            }
        }

        return mixedClusters;
    }

    private Purity pure(List<Integer> tetrad) {
        Set<Integer> key = new HashSet<>(tetrad);
        if (pureTets.contains(key)) return Purity.PURE;
        if (impureTets.contains(key)) return Purity.IMPURE;

        // Base vanishing check for the candidate tetrad
        if (vanishes(tetrad)) {
            List<Integer> vars = allVariables();
            for (int o : vars) {
                if (tetrad.contains(o)) continue;

                for (int j = 0; j < tetrad.size(); j++) {
                    List<Integer> _tetrad = new ArrayList<>(tetrad);
                    _tetrad.set(j, o);

                    if (!vanishes(_tetrad)) {
                        impureTets.add(new HashSet<>(_tetrad));
                        return Purity.IMPURE;
                    }
                }
            }

            // Passed all substitutions -> PURE
            pureTets.add(key);
            return Purity.PURE;
        } else {
            impureTets.add(key);
            return Purity.IMPURE;
        }
    }

    /**
     * Determines if a given cluster of variables "vanishes".
     *
     * @param cluster The list of indices representing variables in the cluster.
     * @return True if the cluster vanishes, false otherwise.
     */
    private boolean vanishes(List<Integer> cluster) {
        int leftSize = cluster.size() / 2;
        ChoiceGenerator gen = new ChoiceGenerator(cluster.size(), leftSize);
        int[] choice;

        while ((choice = gen.next()) != null) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }

            int[] x = new int[leftSize];

            for (int i = 0; i < leftSize; i++) {
                x[i] = cluster.get(choice[i]);
            }

            int[] y = new int[cluster.size() - leftSize];
            int yIndex = 0;
            for (int value : cluster) {
                boolean found = false;

                for (int xVal : x) {
                    if (xVal == value) {
                        found = true;
                        break;
                    }
                }

                if (!found) {
                    y[yIndex++] = value;
                }
            }

            int r = Math.min(x.length, y.length) - 1;
            int rank = RankTests.estimateWilksRank(S, x, y, n, alpha);
            if (rank != r) return false;
        }

        return true;
    }

    /**
     * Returns the union of all integers in the given list of clusters.
     *
     * @param pureClusters The set of clusters, where each cluster is represented as a list of integers.
     * @return A set containing the union of all integers in the clusters.
     */
    private Set<Integer> union(Set<List<Integer>> pureClusters) {
        Set<Integer> unionPure = new HashSet<>();

        for (Collection<Integer> cluster : pureClusters) {
            unionPure.addAll(cluster);
        }

        return unionPure;
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

    public void setAppendPurityFraction(double appendPurityFraction) {
        this.appendPurityFraction = appendPurityFraction;
    }

    private enum Purity {PURE, IMPURE}
}









