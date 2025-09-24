///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2025 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
// and Richard Scheines.                                                     //
//                                                                           //
// This program is free software: you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation, either version 3 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program.  If not, see <https://www.gnu.org/licenses/>.    //
///////////////////////////////////////////////////////////////////////////////

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
 * Generalized Find Factor Clusters (GFFC). This generalized FOFC and FTFC to first find clusters using pure 2-tads
 * (pure tetrads) and then clusters using pure 3-tads (pure sextads) out of the remaining variables. We do not use an
 * n-tad test here since we need to check rank, so we will check rank directly. (This is equivqalent to using the CCA
 * n-tad test.)
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
public class Fofc {
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
    private final Map<List<Integer>, Boolean> vanishCache = new HashMap<>();
    private final Tsc tsc;
    private final int sampleSize;
    /**
     * Whether verbose output is desired.
     */
    private boolean verbose = false;
    /**
     * A cache of pure tetrads.
     */
    private Set<Set<Integer>> pureTets;
    /**
     * A cache of impure tetrads.
     */
    private Set<Set<Integer>> impureTets;
    private int rMax = 2;
    private int ess;

    /**
     * Constructs an instance of the Fofc class using the given dataset, significance level, and equivalent sample size.
     *
     * @param dataSet The dataset from which the variables and correlation matrix will be extracted.
     * @param alpha The significance level for statistical calculations.
     * @param ess The equivalent sample size to use in certain scoring or prior computations.
     */
    public Fofc(DataSet dataSet, double alpha, int ess) {
        this.variables = dataSet.getVariables();
        this.alpha = alpha;
        CorrelationMatrix correlationMatrix = new CorrelationMatrix(dataSet);
        this.S = correlationMatrix.getMatrix().getSimpleMatrix();
        this.n = dataSet.getNumRows();
        this.tsc = new Tsc(dataSet.getVariables(), correlationMatrix);
        this.sampleSize = dataSet.getNumRows();
        setRMax(rMax);
        setEss(ess);
    }

    // Canonical, immutable key for clusters to avoid order/mutation hazards
    private static List<Integer> canonKey(Collection<Integer> xs) {
        List<Integer> s = new ArrayList<>(xs);
        Collections.sort(s);
        return Collections.unmodifiableList(s);
    }

    private void setEss(int ess) {
        this.ess = ess == -1 ? this.sampleSize : ess;
        this.tsc.setEffectiveSampleSize(ess);
    }

    private void setRMax(int rMax) {
        if (rMax < 1) {
            throw new IllegalArgumentException("rMax must be at least 1");
        }
        this.rMax = rMax;
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

        for (int rank = 1; rank <= 1; rank++) {
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
        tsc.setVerbose(verbose);
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
        variables.removeAll(union(clustersToRanks.keySet()));
        int size = rank + 1;

        Set<Set<Integer>> tscClusters = tsc.findClustersAtRank(variables, size, rank);
        System.out.println("TSC Clusters: " + Tsc.toNamesClusters(tscClusters, this.variables));

        if (new HashSet<>(variables).size() != variables.size()) {
            throw new IllegalArgumentException("Variables must be unique.");
        }

        findPureClustersTsc(rank, tscClusters, clustersToRanks);
        findMixedClusters(rank, clustersToRanks);

        if (verbose) {
            TetradLogger.getInstance().log("clusters rank " + rank + " = "
                                           + ClusterSignificance.variablesForIndices(clustersToRanks.keySet(), this.variables));
        }

    }

    private void findPureClustersTsc(int rank, Set<Set<Integer>> tscClusters,
                                     Map<List<Integer>, Integer> clustersToRanks) {
        final int clusterSize = 2 * (rank + 1);

        // Make sure TSC uses same alpha/n as GFFC
        tsc.setAlpha(this.alpha);
        tsc.setEffectiveSampleSize(-1);

        // Unclustered relative to already accepted clusters (any rank)
        List<Integer> unclustered = allVariables();
        unclustered.removeAll(union(clustersToRanks.keySet()));
        if (unclustered.size() < clusterSize) return;

        // Prepare: index TSC clusters into a list for i<j pairing
        List<Set<Integer>> triples = new ArrayList<>(tscClusters);

        // To avoid re-testing the same union from different pairs
        Set<List<Integer>> testedConcatenations = new HashSet<>();

        for (int i = 0; i < triples.size(); i++) {
            final Set<Integer> A = triples.get(i);
            for (int j = i + 1; j < triples.size(); j++) {
                final Set<Integer> B = triples.get(j);

                // 1) triples must be disjoint
                if (!Collections.disjoint(A, B)) continue;

                // 2) union as a SET (uniqueness), then to list
                Set<Integer> Uset = new HashSet<>(A);
                Uset.addAll(B);
                if (Uset.size() != clusterSize) continue; // must be exact size (e.g., 6 for rank 2)

                List<Integer> C = new ArrayList<>(A);
                C.addAll(B);

                // 3) skip if we already tried this union
                if (!testedConcatenations.add(C)) continue;

                // 4) ensure all entries are still unclustered
                if (!new HashSet<>(unclustered).containsAll(C)) continue;

                // 5) purity w.r.t. ALL variables (your pure() already does this + substitution)
                if (pure(C) == Purity.PURE) {
                    // 6) grow from the sextet
                    growCluster(C, rank, clustersToRanks);

                    if (this.verbose) {
                        log("Cluster found: " + ClusterSignificance
                                .variablesForIndices(C, this.variables));
                    }

                    clustersToRanks.put(C, rank);
                    unclustered.removeAll(C);
                }
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
            // If k == 0, the only subset is empty; weâll just test {o} with nothing else (still handled below)
            subsets.add(Collections.emptyList());
        }

        // For each candidate o, test all size-(k+1) tads = subset âª {o}
        O:
        for (int o : unclustered) {
            if (Thread.currentThread().isInterrupted()) return;

            for (List<Integer> sub : subsets) {

                // Make tad = sub âª {o}
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
     * Finds mixed clusters for the SAG algorithm.
     */
    private void findMixedClusters(int rank, Map<List<Integer>, Integer> clustersToRanks) {
        int tadSize = 2 * (rank + 1);

        Set<Integer> unionClustered = union(clustersToRanks.keySet());

        if (unionClustered.isEmpty()) {
            return;
        }

        List<Integer> unclustered = new ArrayList<>(allVariables());
        unclustered.removeAll(new HashSet<>(unionClustered));

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

            for (int o : unionClustered) {
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

            clustersToRanks.put(canonKey(cluster), rank);
            unclustered.removeAll(cluster);

            if (this.verbose) {
                log((2 * (rank + 1) - 1) + "-cluster found: " +
                    ClusterSignificance.variablesForIndices(cluster, this.variables));
            }
        }
    }

    /**
     * Determines if a given tad of variables "vanishes".
     *
     * @param tad The list of indices representing variables in the tad.
     * @return True if the tad vanishes, false otherwise.
     */
    private boolean vanishes(List<Integer> tad) {
        // canonical key
        List<Integer> key = canonKey(tad);
        Boolean cached = vanishCache.get(key);
        if (cached != null) return cached;

        int leftSize = tad.size() / 2;
        ChoiceGenerator gen = new ChoiceGenerator(tad.size(), leftSize);
        int[] choice;

        while ((choice = gen.next()) != null) {
            if (Thread.currentThread().isInterrupted()) break;

            int[] x = new int[leftSize];
            for (int i = 0; i < leftSize; i++) x[i] = tad.get(choice[i]);

            int[] y = new int[tad.size() - leftSize];
            int yIndex = 0;
            for (int v : tad) {
                boolean inX = false;
                for (int xv : x)
                    if (xv == v) {
                        inX = true;
                        break;
                    }
                if (!inX) y[yIndex++] = v;
            }

            int r = Math.min(x.length, y.length) - 1;
            int rank = RankTests.estimateWilksRankFast(S, x, y, ess, alpha);
            if (rank != r) {
                vanishCache.put(key, false);
                return false;
            }
        }
        vanishCache.put(key, true);
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










