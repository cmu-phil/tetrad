
package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.*;
import org.apache.commons.math3.distribution.ChiSquaredDistribution;
import org.ejml.simple.SimpleMatrix;
import org.ejml.simple.SimpleSVD;

import java.util.*;

public class GinMatlabVersion {
    private final double alpha;
    private final RawMarginalIndependenceTest test;
    private final boolean verbose = false;

    public GinMatlabVersion(double alpha, RawMarginalIndependenceTest test) {
        this.alpha = alpha;
        this.test = test;
    }

    public Graph search(DataSet data) {
        SimpleMatrix cov = new SimpleMatrix(data.getCovarianceMatrix().getDataCopy());
        SimpleMatrix rawData = new SimpleMatrix(data.getDoubleData().getDataCopy());
        List<Node> variables = data.getVariables();

        List<List<Integer>> clusters = findCausalClusters(data, cov, rawData);
        clusters = mergeOverlappingClusters(clusters, data, cov, rawData);

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

        for (int i = 0; i < clusters.size(); i++) {
            for (int j = 0; j < clusters.size(); j++) {
                if (i == j) continue;
                List<Integer> Z = clusters.get(i);
                List<Integer> Y = clusters.get(j);
                if (Z.isEmpty() || Y.isEmpty()) continue;

                double[] e = computeE(data, cov, Y, Z);
                List<Double> pvals = new ArrayList<>();
                for (int z : Z) {
                    double[] zData = rawData.extractVector(false, z).getDDRM().getData();
                    try {
                        double pval = test.computePValue(e, zData);
                        pvals.add(Math.min(pval, 1 - 1e-5));
                    } catch (InterruptedException ex) {
                        throw new RuntimeException(ex);
                    }
                }

                double p = fisher(pvals);
                if (p > alpha) {
                    graph.addDirectedEdge(latents.get(i), latents.get(j));
                }
            }
        }

        return graph;
    }

    private List<List<Integer>> findCausalClusters(DataSet data, SimpleMatrix cov, SimpleMatrix rawData) {
        int n = data.getNumColumns();
        List<List<Integer>> clusters = new ArrayList<>();

        for (int size = 2; size <= Math.min(5, n); size++) {
            for (Set<Integer> subset : combinations(n, size)) {
                List<Integer> S = new ArrayList<>(subset);
                Set<Integer> Zset = new HashSet<>();
                for (int i = 0; i < n; i++) if (!subset.contains(i)) Zset.add(i);
                List<Integer> Z = new ArrayList<>(Zset);
                if (Z.isEmpty()) continue;

                double[] e = computeE(data, cov, S, Z);
                List<Double> pvals = new ArrayList<>();
                for (int z : Z) {
                    double[] zData = rawData.extractVector(false, z).getDDRM().getData();
                    try {
                        double pval = test.computePValue(e, zData);
                        pvals.add(Math.max(pval, 1e-5));
                    } catch (InterruptedException ex) {
                        throw new RuntimeException(ex);
                    }
                }

                if (fisher(pvals) >= alpha) {
                    clusters.add(S);
                    if (verbose) System.out.println("Accepted cluster: " + S);
                }
            }
        }

        return clusters;
    }

    private List<List<Integer>> mergeOverlappingClusters(List<List<Integer>> clusters, DataSet data, SimpleMatrix cov, SimpleMatrix rawData) {
        boolean changed;
        do {
            changed = false;
            outer:
            for (int i = 0; i < clusters.size(); i++) {
                for (int j = i + 1; j < clusters.size(); j++) {
                    Set<Integer> mergedSet = new HashSet<>(clusters.get(i));
                    mergedSet.addAll(clusters.get(j));
                    if (mergedSet.size() == clusters.get(i).size() + clusters.get(j).size()) continue;

                    Set<Integer> Zset = new HashSet<>();
                    for (int k = 0; k < data.getNumColumns(); k++) if (!mergedSet.contains(k)) Zset.add(k);
                    if (Zset.isEmpty()) continue;

                    double[] e = computeE(data, cov, new ArrayList<>(mergedSet), new ArrayList<>(Zset));
                    List<Double> pvals = new ArrayList<>();
                    for (int z : Zset) {
                        double[] zData = rawData.extractVector(false, z).getDDRM().getData();
                        try {
                            double pval = test.computePValue(e, zData);
                            pvals.add(Math.max(pval, 1e-5));
                        } catch (InterruptedException ex) {
                            throw new RuntimeException(ex);
                        }
                    }

                    if (fisher(pvals) >= alpha) {
                        clusters.set(i, new ArrayList<>(mergedSet));
                        clusters.remove(j);
                        changed = true;
                        break outer;
                    }
                }
            }
        } while (changed);
        return clusters;
    }

    private static double[] computeE(DataSet data, SimpleMatrix cov, List<Integer> X, List<Integer> Z) {
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

    private static double fisher(List<Double> pvals) {
        if (pvals.isEmpty()) return 0;
        for (Double pval : pvals) {
            if (pval == 0 || Double.isNaN(pval)) return 0;
        }
        double stat = -2 * pvals.stream().mapToDouble(Math::log).sum();
        int df = 2 * pvals.size();
        return new ChiSquaredDistribution(df).cumulativeProbability(stat);
    }

    private static Set<Set<Integer>> combinations(int n, int k) {
        Set<Set<Integer>> result = new HashSet<>();
        combinationsHelper(0, n, k, new LinkedList<>(), result);
        return result;
    }

    private static void combinationsHelper(int start, int n, int k, LinkedList<Integer> current, Set<Set<Integer>> result) {
        if (current.size() == k) {
            result.add(new HashSet<>(current));
            return;
        }
        for (int i = start; i < n; i++) {
            current.add(i);
            combinationsHelper(i + 1, n, k, current, result);
            current.removeLast();
        }
    }
}
