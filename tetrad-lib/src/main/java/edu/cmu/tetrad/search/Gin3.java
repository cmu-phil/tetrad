package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.*;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.math3.distribution.ChiSquaredDistribution;
import org.ejml.simple.SimpleMatrix;
import org.ejml.simple.SimpleSVD;

import java.util.*;

public class Gin3 {
    private final double alpha;
    private final RawMarginalIndependenceTest test;

    public Gin3(double alpha, RawMarginalIndependenceTest test) {
        this.alpha = alpha;
        this.test = test;
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

    public Graph search(DataSet data) {
        SimpleMatrix cov = new SimpleMatrix(data.getCovarianceMatrix().getDataCopy());
        SimpleMatrix rawData = new SimpleMatrix(data.getDoubleData().getDataCopy());
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
                
                if (p > alpha) {
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

    private List<List<Integer>> findCausalClusters(DataSet data, SimpleMatrix cov, SimpleMatrix rawData) {
        List<List<Integer>> clusters = new ArrayList<>();
        int numVars = data.getNumColumns();
        Set<Integer> candidates = new HashSet<>();
        for (int i = 0; i < numVars; i++) candidates.add(i);
        List<Node> nodes = data.getVariables();

        for (int i = 0; i < numVars; i++) {
            J:
            for (int j = i + 1; j < numVars; j++) {
                if (!candidates.contains(i) || !candidates.contains(j)) continue;
                List<Integer> cluster = new ArrayList<>(List.of(i, j));

                for (int k = 0; k < clusters.size(); k++) {
                    for (int l = i + 1; l < clusters.size(); l++) {
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

                        for (int k1 = 0; k1 < clusters.size(); k1++) {
                            for (int l1 = i + 1; l1 < clusters.size(); l1++) {
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
