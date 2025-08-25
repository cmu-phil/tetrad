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

import static java.lang.Math.abs;


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
    private boolean verbose = true;
    /**
     * A cache of pure tetrads.
     */
    private Set<Set<Integer>> pureTets;
    /**
     * A cache of impure tetrads.
     */
    private Set<Set<Integer>> impureTets;

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

    // Canonical, immutable key for clusters to avoid order/mutation hazards
    private static List<Integer> canonKey(Collection<Integer> xs) {
        List<Integer> s = new ArrayList<>(xs);
        Collections.sort(s);
        return Collections.unmodifiableList(s);
    }

    /**
     * Runs the search and returns a graph of clusters with the ir respective latent parents.
     *
     * @return This a map from discovered clusters to their ranks.
     */
    public Map<List<Integer>, Integer> findClusters() {
        this.pureTets = new HashSet<>();
        this.impureTets = new HashSet<>();

        Map<List<Integer>, Integer> clustersToRanks = new HashMap<>();

        for (int rank = 1; rank <= 2; rank++) {
            estimateClustersSag(rank, clustersToRanks);
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
     */
    private void estimateClustersSag(int rank, Map<List<Integer>, Integer> clustersToRanks) {
        List<Integer> variables = allVariables();
        if (new HashSet<>(variables).size() != variables.size()) {
            throw new IllegalArgumentException("Variables must be unique.");
        }

        findPureClusters(rank, clustersToRanks);
        findMixedClusters(rank, clustersToRanks);

        TetradLogger.getInstance().log("clusters rank " + rank + " = "
                                       + ClusterSignificance.variablesForIndices(clustersToRanks.keySet(), this.variables));

    }

    /**
     * Finds clusters of size clusterSize or higher for the tetrad-first algorithm.
     */
    private void findPureClusters(int rank, Map<List<Integer>, Integer> clustersToRanks) {
        List<Integer> variables = allVariables();

        int clusterSize = 2 * (rank + 1);

        List<Integer> unclustered = new ArrayList<>(variables);
        unclustered.removeAll(union(clustersToRanks.keySet()));

        List<Integer> _variables = new ArrayList<>(unclustered);

        if (unclustered.size() < clusterSize) return;

        ChoiceGenerator gen = new ChoiceGenerator(_variables.size(), clusterSize);
        int[] choice;

        CHOICE:
        while ((choice = gen.next()) != null) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }

//            for (int i = 0; i < choice.length; i++) {
//                for (int j = i  + 1; j < choice.length; j++) {
//                    if (abs(S.get(variables.get(i), variables.get(j))) < 0.01) {
//                        continue CHOICE;
//                    }
//                }
//            }

            List<Integer> cluster = new ArrayList<>();

            for (int c : choice) {
                cluster.add(_variables.get(c));
            }

            if (!new HashSet<>(unclustered).containsAll(cluster)) {
                continue;
            }

            // Note that purity needs to be assessed with respect to all the variables to
            // remove all latent-measure impurities between pairs of latents.
            if (pure(cluster) == Purity.PURE) {
                growCluster(cluster, rank, clustersToRanks);

                if (this.verbose) {
                    log("Cluster found: " + ClusterSignificance.variablesForIndices(cluster, this.variables));
                }

                clustersToRanks.put(canonKey(cluster), rank);
                unclustered.removeAll(cluster);
            }
        }
    }

    private void growCluster(List<Integer> cluster, int rank, Map<List<Integer>, Integer> clustersToRanks) {
        final int tadSize = 2 * (rank + 1);

        // Unclustered = all variables minus anything already in any cluster, minus the working cluster
        List<Integer> unclustered = allVariables();
        unclustered.removeAll(union(clustersToRanks.keySet()));
        unclustered.removeAll(cluster);

        // Don't mutate 'cluster' while we're generating subsets from it
        List<Integer> toAdd = new ArrayList<>();

        // Choose subset size: if cluster is small, use the whole cluster; otherwise tadSize-1
        final int k = Math.min(cluster.size(), tadSize - 1);

        // Pre-enumerate all k-subsets of the current cluster once
        List<List<Integer>> subsets = new ArrayList<>();
        if (k > 0) {
            ChoiceGenerator gen = new ChoiceGenerator(cluster.size(), k);
            int[] choice;
            while ((choice = gen.next()) != null) {
                if (Thread.currentThread().isInterrupted()) return;
                List<Integer> sub = new ArrayList<>(k);
                for (int j : choice) sub.add(cluster.get(j));
                subsets.add(sub);
            }
        } else {
            // If k == 0, the only subset is empty; we’ll just test {o} with nothing else (still handled below)
            subsets.add(Collections.emptyList());
        }

        // For each candidate o, test all size-(k+1) tads = subset ∪ {o}
        O:
        for (int o : unclustered) {
            if (Thread.currentThread().isInterrupted()) return;

            for (List<Integer> sub : subsets) {
//                for (int c : sub) {
//                    if (abs(S.get(c, o))  <= 0.01) {
//                        continue O;
//                    }
//                }

                // Make tad = sub ∪ {o}
                List<Integer> tad = new ArrayList<>(sub.size() + 1);
                tad.addAll(sub);
                tad.add(o);

                if (pure(tad) != Purity.PURE) {
                    continue O;
                }
            }

            toAdd.add(o);
        }

        // Now (and only now) mutate the cluster
        cluster.addAll(toAdd);
    }

    /**
     * Finds mixed clusters for the SAG algorithm.
     */
    private void findMixedClusters(int rank, Map<List<Integer>, Integer> clustersToRanks) {
        int tadSize = 2 * (rank + 1);

        if (union(clustersToRanks.keySet()).isEmpty()) {
            return;
        }

        List<Integer> unclustered = new ArrayList<>(allVariables());
        unclustered.removeAll(new HashSet<>(union(clustersToRanks.keySet())));

        List<Integer> variables = new ArrayList<>(unclustered);

        ChoiceGenerator gen = new ChoiceGenerator(variables.size(), tadSize - 1);
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

            List<Integer> cluster = new ArrayList<>();

            for (int c : choice) {
                cluster.add(variables.get(c));
            }

            for (int o : allVariables()) {
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }

//                for (int c : cluster) {
//                    if (abs(S.get(c, o))  <= 0.01) {
//                        continue CHOICE;
//                    }
//                }

                if (cluster.contains(o)) continue;

                List<Integer> _cluster = new ArrayList<>(cluster);
                _cluster.add(o);

                if (!vanishes(_cluster)) {
                    continue CHOICE;
                }
            }

            clustersToRanks.put(canonKey(cluster), rank);
            unclustered.removeAll(cluster);

            if (this.verbose) {
                log((2 * (rank + 1) - 1) + "-cluster found: " +
                    ClusterSignificance.variablesForIndices(cluster, this.variables));
            }
        }

    }

    private Purity pure(List<Integer> tad) {
        Set<Integer> key = new HashSet<>(tad);
        if (pureTets.contains(key)) return Purity.PURE;
        if (impureTets.contains(key)) return Purity.IMPURE;

        // Base vanishing check for the candidate tad
        if (vanishes(tad)) {
            // Substitution test: every single-position substitution by any other variable must also vanish
            List<Integer> vars = allVariables();
            for (int o : vars) {
                if (tad.contains(o)) continue;

                for (int j = 0; j < tad.size(); j++) {
                    List<Integer> _tad = new ArrayList<>(tad);
                    _tad.set(j, o);

                    if (!vanishes(_tad)) {
                        // Cache both the bad substitution and the original key as IMPURE
                        impureTets.add(new HashSet<>(_tad));
                        impureTets.add(key);
                        return Purity.IMPURE;
                    }
                }
            }
            // Passed all substitutions -> PURE
            pureTets.add(key);
            return Purity.PURE;
        } else {
            // Cache the original as IMPURE
            impureTets.add(key);
            return Purity.IMPURE;
        }
    }

    /**
     * Determines if a given tad of variables "vanishes".
     *
     * @param tad The list of indices representing variables in the tad.
     * @return True if the tad vanishes, false otherwise.
     */
    private boolean vanishes(List<Integer> tad) {
        int leftSize = tad.size() / 2;
        ChoiceGenerator gen = new ChoiceGenerator(tad.size(), leftSize);
        int[] choice;

        while ((choice = gen.next()) != null) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }

            int[] x = new int[leftSize];

            for (int i = 0; i < leftSize; i++) {
                x[i] = tad.get(choice[i]);
            }

            int[] y = new int[tad.size() - leftSize];
            int yIndex = 0;
            for (int value : tad) {
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
//            int rank = RankTests.estimateWilksRank(S, x, y, n, alpha);
            int rank = RankTests.estimateWilksRankFast(S, x, y, n, alpha);
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

    private enum Purity {PURE, IMPURE}
}









