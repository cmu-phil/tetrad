package edu.cmu.tetrad.search;

import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.RankTests;
import org.ejml.simple.SimpleMatrix;

import java.util.*;

/**
 * Refines clusters using rank-based diagnostics.
 *
 * Input: clusters of observed variables (as column indices)
 * Output: improved clusters (split / merged)
 */
public class RankClusterRefiner {

    private final double alpha;
    private SimpleMatrix s;
    private List<Node> variables;

    public RankClusterRefiner(double alpha) {
        this.alpha = alpha;
    }

    /**
     * Main entry point.
     */
    public List<List<Integer>> refine(List<List<Integer>> clusters, SimpleMatrix s, List<Node> variables) {
        this.s = s;
        this.variables = variables;

        List<List<Integer>> refined = new ArrayList<>();

        // Step 1: split clusters if needed
        for (List<Integer> cluster : clusters) {
            refined.addAll(splitIfNeeded(cluster));
        }

        // Step 2: merge clusters if needed
        boolean changed;
        do {
            changed = false;

            outer:
            for (int i = 0; i < refined.size(); i++) {
                for (int j = i + 1; j < refined.size(); j++) {

                    List<Integer> c1 = refined.get(i);
                    List<Integer> c2 = refined.get(j);

                    if (shouldMerge(c1, c2)) {
                        List<Integer> merged = union(c1, c2);

                        refined.remove(j);
                        refined.remove(i);
                        refined.add(merged);

                        changed = true;
                        break outer;
                    }
                }
            }

        } while (changed);

        return refined;
    }

    // ============================
    // SPLITTING
    // ============================

    private List<List<Integer>> splitIfNeeded(List<Integer> cluster) {

        List<List<Integer>> result = new ArrayList<>();

        if (cluster.size() <= 2) {
            result.add(cluster);
            return result;
        }

        int rank = estimateRank(cluster);

        // If rank == 1, keep cluster
        if (rank <= 1) {
            result.add(cluster);
            return result;
        }

        // Otherwise split heuristically
        List<List<Integer>> split = splitCluster(cluster);

        result.addAll(split);
        return result;
    }

    private int estimateRank(List<Integer> cluster) {
        if (cluster.size() <= 1) {
            return 1;
        }

        int[] clusterArray = cluster.stream().mapToInt(Integer::intValue).toArray();

        List<Integer> rest = new ArrayList<>(cluster);
        rest.remove(0);

        int[] left = new int[]{clusterArray[0]};
        int[] right = rest.stream().mapToInt(Integer::intValue).toArray();

        SimpleMatrix S = s.extractMatrix(0, s.numRows(), clusterArray[0], clusterArray[clusterArray.length - 1] + 1);
        int nEff = s.numRows();

        return RankTests.estimateWilksRank(S, left, right, nEff, alpha);
    }

    /**
     * Simple greedy split based on correlation structure.
     * You can improve this later.
     */
    private List<List<Integer>> splitCluster(List<Integer> cluster) {

        List<List<Integer>> result = new ArrayList<>();

        // naive split: try removing each variable
        for (int i = 0; i < cluster.size(); i++) {

            List<Integer> sub = new ArrayList<>(cluster);
            sub.remove(i);

            if (estimateRank(sub) < estimateRank(cluster)) {
                result.add(sub);

                List<Integer> remainder = new ArrayList<>(cluster);
                remainder.removeAll(sub);

                if (!remainder.isEmpty()) {
                    result.add(remainder);
                }

                return result;
            }
        }

        // fallback: no split found
        result.add(cluster);
        return result;
    }

    // ============================
    // MERGING
    // ============================

    private boolean shouldMerge(List<Integer> c1, List<Integer> c2) {

        int rank1 = estimateRank(c1);
        int rank2 = estimateRank(c2);

        List<Integer> union = union(c1, c2);
        int rankUnion = estimateRank(union);

        // Merge if union rank is less than sum
        return rankUnion < rank1 + rank2;
    }

    // ============================
    // UTILITIES
    // ============================

    private List<Integer> union(List<Integer> a, List<Integer> b) {
        Set<Integer> set = new HashSet<>(a);
        set.addAll(b);
        return new ArrayList<>(set);
    }

    private int[] toArray(List<Integer> list) {
        int[] arr = new int[list.size()];
        for (int i = 0; i < list.size(); i++) {
            arr[i] = list.get(i);
        }
        return arr;
    }
}