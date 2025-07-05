// Faithful translation of causal-learn's GIN (Generalized Independent Noise) method
// with graph-building logic compatible with the Tetrad framework.

package edu.cmu.tetrad.search;

import cern.colt.matrix.linalg.SingularValueDecomposition;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.util.UniformityTest;
import org.ejml.simple.SimpleMatrix;
import org.ejml.simple.SimpleSVD;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Gin {
    private final double alpha;
    private final RawMarginalIndependenceTest test;
    private final Graph pag; // optional PAG to constrain adjacency

    public Gin(double alpha, RawMarginalIndependenceTest test, Graph pag) {
        this.alpha = alpha;
        this.test = test;
        this.pag = pag;
    }

    public static double[] computeE(DataSet data, SimpleMatrix cov, List<Integer> X, List<Integer> Z) {
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

        SimpleMatrix result = subData.mult(omega);
        return result.getDDRM().getData();
    }

    public static double uniform(List<Double> pvals) {
        return ksTest(pvals);
//        double stat = -2 * pvals.stream().mapToDouble(Math::log).sum();
//        int df = 2 * pvals.size();
//        return new GammaDistribution(df / 2.0, 2.0).cumulativeProbability(stat);
//        return Gamma.cdf(stat, df / 2.0, 1.0);
    }

    public static double ksTest(List<Double> pvals) {
//        GeneralAndersonDarlingTest _generalAndersonDarlingTest = new GeneralAndersonDarlingTest(pvals, new UniformRealDistribution(0, 1));
//        return _generalAndersonDarlingTest.getP();

        return UniformityTest.getKsPValue(pvals, 0.0, 1.0);
    }

    public Graph search(DataSet data, SimpleMatrix cov) {
        List<Node> variables = data.getVariables();

        SimpleMatrix _data = new SimpleMatrix(data.getDoubleData().getDataCopy());
        int numVars = data.getNumColumns();

        Set<Integer> candidates = new HashSet<>();
        for (int i = 0; i < numVars; i++) candidates.add(i);

        List<List<Integer>> clusters = new ArrayList<>();

        // Iterate through variable pairs and grow clusters greedily
        for (int i = 0; i < numVars; i++) {
            for (int j = i + 1; j < numVars; j++) {
                if (!candidates.contains(i) || !candidates.contains(j)) continue;
                if (!areBidirectedEligibleInPag(i, j, data)) continue;

                List<Integer> cluster = new ArrayList<>();
                cluster.add(i);
                cluster.add(j);

                Set<Integer> remainder = new HashSet<>(candidates);
                remainder.remove(i);
                remainder.remove(j);

                double[] e = computeE(data, cov, cluster, new ArrayList<>(remainder));
                List<Double> pvals = new ArrayList<>();

                for (int z : remainder) {
                    double[] zData = _data.extractVector(false, z).getDDRM().getData();
                    double pval = 0;
                    try {
                        pval = test.computePValue(e, zData);
                    } catch (InterruptedException ex) {
                        throw new RuntimeException(ex);
                    }
                    pvals.add(Math.min(pval, 1 - 1e-5));
                }

                double fisherP = uniform(pvals);
                if (fisherP < alpha) continue;

                // Try to greedily grow the cluster
                boolean grown;
                do {
                    grown = false;
                    for (int k : new HashSet<>(remainder)) {
                        if (!isAdjacentToCluster(k, cluster, data)) continue;

                        List<Integer> candidate = new ArrayList<>(cluster);
                        candidate.add(k);

                        Set<Integer> rest = new HashSet<>(candidates);
                        candidate.forEach(rest::remove);
                        if (rest.isEmpty()) continue;

                        e = computeE(data, cov, candidate, new ArrayList<>(rest));
                        pvals = new ArrayList<>();
                        for (int z : rest) {
                            double[] zData = _data.extractVector(false, z).getDDRM().getData();
                            double pval = 0;
                            try {
                                pval = test.computePValue(e, zData);
                            } catch (InterruptedException ex) {
                                throw new RuntimeException(ex);
                            }
                            pvals.add(Math.min(pval, 1 - 1e-5));
                        }

                        fisherP = uniform(pvals);
                        if (fisherP >= alpha) {
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

        // Build graph with latent variables
        Graph graph = pag != null ? new EdgeListGraph(pag) : new EdgeListGraph(variables);

        int latentId = 0;
        for (List<Integer> cluster : clusters) {
            Node latent = new GraphNode("L" + (++latentId));
            latent.setNodeType(NodeType.LATENT);
            graph.addNode(latent);
            for (int index : cluster) {
                Node observed = variables.get(index);
                graph.addDirectedEdge(latent, observed);
            }
        }

        return graph;
    }

    private boolean areAdjacentInPag(int i, int j, DataSet data) {
        if (pag == null) return true;
        Node ni = data.getVariable(i);
        Node nj = data.getVariable(j);
        return pag.isAdjacentTo(ni, nj);
    }

    private boolean areBidirectedEligibleInPag(int i, int j, DataSet data) {
        if (pag == null) return true;  // No constraint

        Node ni = data.getVariable(i);
        Node nj = data.getVariable(j);
        Edge edge = pag.getEdge(ni, nj);

        if (edge == null) return false;

        Endpoint endpoint1 = edge.getEndpoint1();
        Endpoint endpoint2 = edge.getEndpoint2();

        // Ensure ordering matches
        if (!edge.getNode1().equals(ni)) {
            endpoint1 = edge.getEndpoint2();
            endpoint2 = edge.getEndpoint1();
        }

        // Allow only these edge types: o-o, o->, <-o, <->, or undirected
        return (endpoint1 == Endpoint.CIRCLE || endpoint1 == Endpoint.ARROW)
               && (endpoint2 == Endpoint.CIRCLE || endpoint2 == Endpoint.ARROW);
    }

    private boolean isAdjacentToCluster(int k, List<Integer> cluster, DataSet data) {
        for (int idx : cluster) {
            if (areBidirectedEligibleInPag(k, idx, data)) return true;
        }
        return false;
    }
}