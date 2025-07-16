package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.CorrelationMatrix;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.search.ntad_test.NtadTest;
import org.apache.commons.math3.distribution.NormalDistribution;
import org.apache.commons.math3.util.FastMath;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import static org.apache.commons.math3.util.FastMath.abs;
import static org.apache.commons.math3.util.FastMath.sqrt;

/**
 * Implements the Silva et al. (2003, 2006) BuildPureClusters approach using the NtadTest framework. Follows a
 * Seed-and-Grow strategy:
 * <p>
 * Stage 1: - Identify pure tetrads (sets of 4 variables with all tetrads passing and all pairs dependent). - Grow each
 * seed by adding one unclustered variable at a time while preserving purity and dependence.
 * <p>
 * Stage 2: - Rescue unclustered size-3 sets by growing them into valid size-4 pure clusters.
 */
public class Bpc {
    /**
     * The tetrad test.
     */
    private final NtadTest ntadTest;
    /**
     * The alpha cutoff.
     */
    private final double alpha;
    /**
     * The number of varaibles.
     */
    private final int numVars;
    /**
     * The variable names, in order.
     */
    private final List<String> variableNames;
    /**
     * The variables that have already been used to form clusters.
     */
    private final boolean[] used;
    /**
     * A standard Normal distribution.
     */
    private final NormalDistribution normal = new NormalDistribution(0, 1);
    /**
     * The correlation matrix.
     */
    private final CorrelationMatrix corr;
    /**
     * The clusters (to be discovered).
     */
    private List<List<Integer>> clusters = new ArrayList<>();


    /**
     * Constructor.
     *
     * @param test    The tetrad test.
     * @param dataSet The dataset.
     * @param alpha   The alpha cutoff level.
     */
    public Bpc(NtadTest test, DataSet dataSet, double alpha) {
        this.ntadTest = test;
        this.alpha = alpha;
        this.numVars = test.variables().size();
        this.variableNames = dataSet.getVariableNames();
        this.used = new boolean[numVars];
        this.corr = new CorrelationMatrix(dataSet);
    }

    /**
     * Finds the clusters using the BPC method.
     */
    public void findClusters() {
        // ----- Stage 1: Seed-and-Grow from pure tetrads -----
        for (int i = 0; i < numVars; i++) {
            for (int j = i + 1; j < numVars; j++) {
                for (int k = j + 1; k < numVars; k++) {
                    for (int l = k + 1; l < numVars; l++) {
                        if (used[i] || used[j] || used[k] || used[l]) continue;

                        List<Integer> seed = List.of(i, j, k, l);
                        if (!ntadTest.allGreaterThanAlpha(generateTetrads(seed), alpha)) continue;
                        if (!clusterDependent(seed)) continue;

                        List<Integer> cluster = new ArrayList<>(seed);
                        boolean expanded;
                        do {
                            expanded = false;
                            for (int x = 0; x < numVars; x++) {
                                if (used[x] || cluster.contains(x)) continue;

                                List<Integer> candidate = new ArrayList<>(cluster);
                                candidate.add(x);
                                if (ntadTest.allGreaterThanAlpha(generateTetrads(candidate), alpha) && clusterDependent(candidate)) {
                                    cluster = candidate;
                                    expanded = true;
                                    break;
                                }
                            }
                        } while (expanded);

                        clusters.add(cluster);
                        for (int v : cluster) used[v] = true;
                    }
                }
            }

            List<List<Integer>> finalClusters = new ArrayList<>();

            for (List<Integer> cluster : new HashSet<>(clusters)) {
                if (cluster.size() >= 5) {
                    finalClusters.add(cluster);
                }
            }

            clusters = finalClusters;
        }

        // ----- Stage 2: Rescue size-3 clusters -----
        for (int i = 0; i < numVars; i++) {
            for (int j = i + 1; j < numVars; j++) {
                for (int k = j + 1; k < numVars; k++) {
                    if (used[i] || used[j] || used[k]) continue;

                    List<Integer> triple = List.of(i, j, k);
                    if (!clusterDependent(triple)) continue;

                    for (int x = 0; x < numVars; x++) {
                        if (used[x] || triple.contains(x)) continue;

                        List<Integer> candidate = new ArrayList<>(triple);
                        candidate.add(x);
                        if (ntadTest.allGreaterThanAlpha(generateTetrads(candidate), alpha) && clusterDependent(candidate)) {
                            clusters.add(candidate);
                            for (int v : candidate) used[v] = true;
                            break;
                        }
                    }
                }
            }
        }
    }

    /**
     * Returns the clusters.
     *
     * @return Tha clusters.
     */
    public List<List<String>> getClusters() {
        return clusters.stream()
                .map(cluster -> cluster.stream().map(variableNames::get).toList())
                .toList();
    }

    /**
     * Generates the list of tetrads in the given list of variables.
     *
     * @param vars The variables (indices).
     * @return The list of tetrads.
     */
    private List<int[][]> generateTetrads(List<Integer> vars) {
        List<int[][]> tetrads = new ArrayList<>();
        if (vars.size() < 4) return tetrads;

        for (int i = 0; i < vars.size(); i++) {
            for (int j = i + 1; j < vars.size(); j++) {
                for (int k = j + 1; k < vars.size(); k++) {
                    for (int l = k + 1; l < vars.size(); l++) {
                        int a = vars.get(i), b = vars.get(j), c = vars.get(k), d = vars.get(l);
                        tetrads.add(new int[][]{{a, b}, {c, d}});
                        tetrads.add(new int[][]{{a, c}, {b, d}});
                        tetrads.add(new int[][]{{a, d}, {b, c}});
                    }
                }
            }
        }
        return tetrads;
    }

    /**
     * True if thge variables in the cluster are pairwise dependent.
     *
     * @param cluster The cluster.
     * @return True if pairwise dependent.
     */
    private boolean clusterDependent(List<Integer> cluster) {
        boolean found = false;

        for (int i = 0; i < cluster.size(); i++) {
            for (int j = i + 1; j < cluster.size(); j++) {
                double r = this.corr.getValue(cluster.get(i), cluster.get(j));
                int n = this.corr.getSampleSize();
                int zSize = 0;

                double q = .5 * (FastMath.log(1.0 + abs(r)) - FastMath.log(1.0 - abs(r)));
                double df = n - 3. - zSize;

                double fisherZ = sqrt(df) * q;

                if (2 * (1.0 - this.normal.cumulativeProbability(fisherZ)) > alpha) {
                    found = true;
                    break;
                }
            }
        }

        return !found;
    }
}
