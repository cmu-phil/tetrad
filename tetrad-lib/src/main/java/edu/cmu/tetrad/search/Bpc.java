package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.CorrelationMatrix;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.ntad_test.NtadTest;
import edu.cmu.tetrad.util.RandomUtil;
import org.apache.commons.math3.distribution.NormalDistribution;
import org.apache.commons.math3.util.FastMath;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
    private final NtadTest ntadTest;
    private final double alpha;
    private final int p;
    private final List<String> variableNames;
    private final CorrelationMatrix corr;

    private List<List<Integer>> clusters = new ArrayList<>();
    private final boolean[] used;
    private final IndependenceTest independenceTest;

    public Bpc(NtadTest test, IndependenceTest indTest, DataSet dataSet, List<String> vars, double alpha) {
        this.ntadTest = test;
        this.independenceTest = indTest;
        this.alpha = alpha;
        this.p = test.variables().size();
        this.variableNames = vars;
        this.used = new boolean[p];

        this.corr = new CorrelationMatrix(dataSet);
    }

    public void findClusters() {
        // ----- Stage 1: Seed-and-Grow from pure tetrads -----
        for (int i = 0; i < p; i++) {
            for (int j = i + 1; j < p; j++) {
                for (int k = j + 1; k < p; k++) {
                    for (int l = k + 1; l < p; l++) {
                        if (used[i] || used[j] || used[k] || used[l]) continue;

                        List<Integer> seed = List.of(i, j, k, l);
                        if (!ntadTest.allGreaterThanAlpha(generateTetrads(seed), alpha)) continue;
                        if (!allPairsDependent(seed)) continue;

                        List<Integer> cluster = new ArrayList<>(seed);
                        boolean expanded;
                        do {
                            expanded = false;
                            for (int x = 0; x < p; x++) {
                                if (used[x] || cluster.contains(x)) continue;

                                List<Integer> candidate = new ArrayList<>(cluster);
                                candidate.add(x);
                                if (ntadTest.allGreaterThanAlpha(generateTetrads(candidate), alpha)
                                    && allPairsDependent(candidate)) {
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
        for (int i = 0; i < p; i++) {
            for (int j = i + 1; j < p; j++) {
                for (int k = j + 1; k < p; k++) {
                    if (used[i] || used[j] || used[k]) continue;

                    List<Integer> triple = List.of(i, j, k);
                    if (!allPairsDependent(triple)) continue;

                    for (int x = 0; x < p; x++) {
                        if (used[x] || triple.contains(x)) continue;

                        List<Integer> candidate = new ArrayList<>(triple);
                        candidate.add(x);
                        if (ntadTest.allGreaterThanAlpha(generateTetrads(candidate), alpha)
                            && allPairsDependent(candidate)) {
                            clusters.add(candidate);
                            for (int v : candidate) used[v] = true;
                            break;
                        }
                    }
                }
            }
        }
    }

    public List<List<String>> getClusters() {
        return clusters.stream()
                .map(cluster -> cluster.stream().map(variableNames::get).toList())
                .toList();
    }

    public void printClusters() {
        List<List<String>> namedClusters = getClusters();
        for (int i = 0; i < namedClusters.size(); i++) {
            System.out.println("Cluster " + (i + 1) + ": " + namedClusters.get(i));
        }
    }

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

    private boolean allPairsDependent(List<Integer> vars) {
        if (true) return !containsZeroCorrelation(vars);

        for (int i = 0; i < vars.size(); i++) {
            for (int j = i + 1; j < vars.size(); j++) {
                Node x = independenceTest.getVariable(variableNames.get(vars.get(i)));
                Node y = independenceTest.getVariable(variableNames.get(vars.get(j)));
                try {
                    if (independenceTest.checkIndependence(x, y).isIndependent()) return false;
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }
        return true;
    }

    private final NormalDistribution normal = new NormalDistribution(0, 1);

    private boolean containsZeroCorrelation(List<Integer> cluster) {
//        if (true) return !allPairsDependent(cluster);

        int count = 0;

        for (int i = 0; i < cluster.size(); i++) {
            for (int j = i + 1; j < cluster.size(); j++) {
                double r = this.corr.getValue(cluster.get(i), cluster.get(j));
                int n = this.corr.getSampleSize();
                int zSize = 0;

                double q = .5 * (FastMath.log(1.0 + abs(r)) - FastMath.log(1.0 - abs(r)));
                double df = n - 3. - zSize;

                double fisherZ = sqrt(df) * q;

                if (2 * (1.0 - this.normal.cumulativeProbability(fisherZ)) > alpha) {
                    return true;
                }
            }
        }

        return false;
    }
}
