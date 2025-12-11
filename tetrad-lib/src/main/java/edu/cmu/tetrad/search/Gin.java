/// ////////////////////////////////////////////////////////////////////////////
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
/// ////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.util.TetradLogger;
import org.apache.commons.math3.distribution.ChiSquaredDistribution;
import org.ejml.simple.SimpleMatrix;
import org.ejml.simple.SimpleSVD;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * GIN algorithm implementing the procedure described in the GIN papers:
 *
 * <ol>
 *   <li>Form latent clusters using the GIN clustering procedure.</li>
 *   <li>Learn a causal order of latent clusters via the root-peeling loop.</li>
 *   <li>Build a latent DAG respecting that order, with tail–tail undirected edges
 *       among the unordered latents (if any).</li>
 * </ol>
 *
 * <p>Observed nodes are the {@code DataSet} variables; unclustered variables are left
 * without latent parents (as in the original GIN code).</p>
 */
public class Gin {

    // ----------------------------- Params -----------------------------
    private final double alpha;
    private final RawMarginalIndependenceTest test;
    private boolean verbose = false;
    // ----------------------------- State -----------------------------
    private DataSet data;
    private SimpleMatrix cov;                 // covariance of observed
    private List<Node> vars;                  // observed nodes

    // ----------------------------- Ctors -----------------------------

    /**
     * Constructs a Gin instance with the specified significance level and independence test.
     *
     * @param alpha the significance level to be used for statistical tests, typically in the range [0, 1]
     * @param test  the raw marginal independence test instance, used to compute p-values for independence testing
     * @throws NullPointerException if the provided test is null
     */
    public Gin(double alpha, RawMarginalIndependenceTest test) {
        this.alpha = alpha;
        this.test = Objects.requireNonNull(test, "test");
    }

    // ----------------------------- Options ---------------------------

    /**
     * Sets the verbose mode for logging or output. When enabled, additional details may be provided for debugging or
     * informational purposes.
     *
     * @param v true to enable verbose mode, false to disable it
     */
    public void setVerbose(boolean v) {
        this.verbose = v;
    }

    // ----------------------------- API -------------------------------

    public Graph search(DataSet data) {
        return searchPaperStyle(data);
    }

    /**
     * GIN algorithm exactly following the paper / causal-learn GIN:
     *
     * <ol>
     *   <li>Form latent clusters using the GIN clustering procedure.</li>
     *   <li>Learn a causal order of latent clusters via the root-peeling loop.</li>
     *   <li>Build a latent DAG respecting that order, with tail–tail undirected edges
     *       among the unordered latents (if any).</li>
     * </ol>
     *
     * <p>Observed nodes are the {@code DataSet} variables; unclustered variables are left
     * without latent parents (as in the original GIN code).</p>
     */
    private Graph searchPaperStyle(DataSet data) {
        this.data = data;
        // covariance (not correlation), matching np.cov(data.T) in GIN.py
        this.cov = new SimpleMatrix(data.getCovarianceMatrix().getSimpleMatrix());
        this.vars = data.getVariables();

        final int n = vars.size();

        // var_set = set(range(n))
        java.util.Set<Integer> varSet = new java.util.LinkedHashSet<>();
        for (int i = 0; i < n; i++) varSet.add(i);

        // ---------- Step 1: GIN clustering (paper / GIN.py) ----------
        int clusterSize = 2;
        List<List<Integer>> clustersList = new ArrayList<>();

        while (clusterSize < varSet.size()) {
            List<List<Integer>> tmpClustersList = new ArrayList<>();

            List<Integer> varList = new ArrayList<>(varSet);
            if (clusterSize <= varList.size()) {
                // enumerate combinations of 'varList' of size 'clusterSize'
                int m = varList.size();
                int[] idx = new int[clusterSize];
                for (int i = 0; i < clusterSize; i++) idx[i] = i;

                while (true) {
                    // current cluster = varList[idx[0..clusterSize-1]]
                    List<Integer> cluster = new ArrayList<>(clusterSize);
                    for (int k = 0; k < clusterSize; k++) {
                        cluster.add(varList.get(idx[k]));
                    }

                    // remain_var_set = var_set - set(cluster)
                    java.util.Set<Integer> remainVarSet = new java.util.LinkedHashSet<>(varSet);
                    cluster.forEach(remainVarSet::remove);

                    if (!remainVarSet.isEmpty()) {
                        // e = cal_e_with_gin(data, cov, cluster, remain_var_set)
                        List<Integer> Z = new ArrayList<>(remainVarSet);
                        double[] e = calEWithGin(cluster, Z);

                        // pvals = [indep_test(data[:,[z]], e) for z in remain_var_set]
                        List<Double> pvals = new ArrayList<>();
                        for (int z : remainVarSet) {
                            double p = indepPValueEvsSingleZ(e, z);
                            pvals.add(p);
                        }

                        double fisherP = fisherPValue(pvals);

                        if (fisherP >= alpha) {
                            tmpClustersList.add(cluster);
                        }
                    }

                    // next combination
                    int t = clusterSize - 1;
                    while (t >= 0 && idx[t] == t + (m - clusterSize)) t--;
                    if (t < 0) break;
                    idx[t]++;
                    for (int i = t + 1; i < clusterSize; i++) {
                        idx[i] = idx[i - 1] + 1;
                    }
                }
            }

            // merge_overlaping_cluster(tmp_clusters_list)
            tmpClustersList = mergeOverlappingClusters(tmpClustersList);

            // clusters_list = clusters_list + tmp_clusters_list
            clustersList.addAll(tmpClustersList);

            // for a cluster in tmp_clusters_list: var_set -= set(cluster)
            for (List<Integer> c : tmpClustersList) {
                c.forEach(varSet::remove);
            }

            clusterSize++;
        }

        if (verbose) {
            TetradLogger.getInstance().log("[GIN-paper] initial clusters=" + clustersAsNames(clustersList));
            TetradLogger.getInstance().log("[GIN-paper] unclustered vars=" + varSet);
        }

        // ---------- Step 2: Learn causal order via root-peeling loop ----------

        List<List<Integer>> clustersRemaining = new ArrayList<>(clustersList);
        List<List<Integer>> causalOrder = new ArrayList<>(); // corresponds to K in the paper

        boolean updated = true;
        while (updated && !clustersRemaining.isEmpty()) {
            updated = false;

            // X, Z built from already chosen causalOrder
            List<Integer> X = new ArrayList<>();
            List<Integer> Z = new ArrayList<>();
            for (List<Integer> clusterK : causalOrder) {
                Split s = splitCluster(clusterK);
                X.addAll(s.firstHalf);
                Z.addAll(s.secondHalf);
            }

            // search for a root cluster among the remaining ones
            for (int i = 0; i < clustersRemaining.size(); i++) {
                List<Integer> clusterI = clustersRemaining.get(i);
                Split si = splitCluster(clusterI);
                List<Integer> clusterI1 = si.firstHalf;
                List<Integer> clusterI2 = si.secondHalf;

                boolean isRoot = true;

                for (int j = 0; j < clustersRemaining.size(); j++) {
                    if (i == j) continue;
                    List<Integer> clusterJ = clustersRemaining.get(j);
                    Split sj = splitCluster(clusterJ);
                    List<Integer> clusterJ1 = sj.firstHalf;

                    // X + cluster_i1 + cluster_j1
                    List<Integer> Xall = new ArrayList<>(X.size() + clusterI1.size() + clusterJ1.size());
                    Xall.addAll(X);
                    Xall.addAll(clusterI1);
                    Xall.addAll(clusterJ1);

                    // Z + cluster_i2
                    List<Integer> Zall = new ArrayList<>(Z.size() + clusterI2.size());
                    Zall.addAll(Z);
                    Zall.addAll(clusterI2);

                    double[] e = calEWithGin(Xall, Zall);

                    List<Double> pvals = new ArrayList<>();
                    for (int zCol : Zall) {
                        pvals.add(indepPValueEvsSingleZ(e, zCol));
                    }

                    double fisherP = fisherPValue(pvals);
                    if (fisherP < alpha) {
                        // fails root condition
                        isRoot = false;
                        break;
                    }
                }

                if (isRoot) {
                    causalOrder.add(clusterI);
                    clustersRemaining.remove(i);
                    updated = true;
                    break;
                }
            }
        }

        if (verbose) {
            TetradLogger.getInstance().log("[GIN-paper] causal order=" + clustersAsNames(causalOrder));
            TetradLogger.getInstance().log("[GIN-paper] unordered clusters=" + clustersAsNames(clustersRemaining));
        }

        // ---------- Step 3: Build graph as in GIN.py ----------

        Graph g = new EdgeListGraph(this.vars);  // observed nodes already present

        // Unclustered observed variables (still in varSet) are already in 'vars' with no latent parent.

        int latentId = 1;
        List<Node> lNodes = new ArrayList<>();

        // Ordered latents (causal_order)
        for (List<Integer> cluster : causalOrder) {
            Node lNode = new GraphNode("L" + latentId);
            lNode.setNodeType(NodeType.LATENT);
            g.addNode(lNode);

            for (Node parentLatent : lNodes) {
                g.addDirectedEdge(parentLatent, lNode);
            }
            lNodes.add(lNode);

            for (int o : cluster) {
                g.addDirectedEdge(lNode, vars.get(o));
            }

            latentId++;
        }

        // Remaining clusters: undirected latent–latent among themselves,
        // directed from ordered latents into them.
        List<Node> undirectedLNodes = new ArrayList<>();

        for (List<Integer> cluster : clustersRemaining) {
            Node lNode = new GraphNode("L" + latentId);
            lNode.setNodeType(NodeType.LATENT);
            g.addNode(lNode);

            for (Node parentLatent : lNodes) {
                g.addDirectedEdge(parentLatent, lNode);
            }
            for (Node und : undirectedLNodes) {
                g.addUndirectedEdge(und, lNode);
            }
            undirectedLNodes.add(lNode);

            for (int o : cluster) {
                g.addDirectedEdge(lNode, vars.get(o));
            }

            latentId++;
        }

        return g;
    }

    // --------- GIN projection e = cal_e_with_gin(data, cov, X, Z) ---------

    /**
     * cal_e_with_gin(data, cov, X, Z) from GIN.py:
     * <pre>
     *   cov_m = cov[np.ix_(Z, X)]
     *   _, _, v = np.linalg.svd(cov_m)
     *   omega = v.T[:, -1]
     *   return np.dot(data[:, X], omega)
     * </pre>
     */
    private double[] calEWithGin(List<Integer> X, List<Integer> Z) {
        int nRows = data.getNumRows();
        if (X.isEmpty() || Z.isEmpty()) {
            return new double[nRows];
        }

        // cov_m = cov[np.ix_(Z, X)]
        SimpleMatrix covM = subCov(cov, Z, X);
        SimpleSVD<SimpleMatrix> svd = covM.svd();
        SimpleMatrix V = svd.getV();

        int k = V.getNumCols();
        if (k == 0) {
            return new double[nRows];
        }

        // omega = last column of V
        SimpleMatrix omegaVec = V.extractVector(false, k - 1); // (len(X) x 1)

        double[] omega = new double[X.size()];
        for (int i = 0; i < X.size(); i++) {
            omega[i] = omegaVec.get(i, 0);
        }

        // e = data[:, X] * omega
        double[] e = new double[nRows];
        for (int r = 0; r < nRows; r++) {
            double sum = 0.0;
            for (int j = 0; j < X.size(); j++) {
                int col = X.get(j);
                sum += data.getDouble(r, col) * omega[j];
            }
            e[r] = sum;
        }

        return e;
    }

    /**
     * Independence test p-value for e vs. a single Z column, wrapping the RawMarginalIndependenceTest in the same spirit
     * as indep_test in GIN.py.
     */
    private double indepPValueEvsSingleZ(double[] e, int zCol) {
        int n = e.length;
        double[][] Z1 = new double[n][1];
        for (int i = 0; i < n; i++) {
            Z1[i][0] = data.getDouble(i, zCol);
        }

        try {
            double p = test.computePValue(e, Z1);
            if (!Double.isFinite(p)) return 1.0;
            if (p < 0.0) return 0.0;
            return Math.min(p, 1.0);
        } catch (InterruptedException ex) {
            throw new RuntimeException(ex);
        }
    }

    /**
     * Fisher's method for combining p-values, as in fisher_test in GIN.py.
     */
    private double fisherPValue(List<Double> pvals) {
        if (pvals == null || pvals.isEmpty()) {
            return 1.0;
        }
        double stat = 0.0;
        int k = 0;
        for (double p : pvals) {
            double q = p;
            if (!Double.isFinite(q) || q <= 0.0) q = 1e-5;
            if (q < 1e-5) q = 1e-5;  // cap small p-values
            stat += -2.0 * Math.log(q);
            k++;
        }
        double df = 2.0 * k;
        ChiSquaredDistribution chi = new ChiSquaredDistribution(df);
        double cdf = chi.cumulativeProbability(stat);
        return 1.0 - cdf;
    }

    // --------- Cluster splitting (array_split(..., 2) for k=2) ---------

    /**
     * Helper matching array_split(x, 2) in GIN.py: the first half is ceil(len/2), the second is the remainder.
     */
    private Split splitCluster(List<Integer> cluster) {
        int len = cluster.size();
        int firstLen = (len + 1) / 2; // ceil(len / 2)
        List<Integer> first = new ArrayList<>(cluster.subList(0, firstLen));
        List<Integer> second = new ArrayList<>(cluster.subList(firstLen, len));
        return new Split(first, second);
    }

    /**
     * Merge overlapping clusters: if two clusters share any variable, they are merged into their union. This is a
     * simpler but equivalent version of merge_overlaping_cluster in GIN.py.
     */
    private List<List<Integer>> mergeOverlappingClusters(List<List<Integer>> clusters) {
        List<java.util.Set<Integer>> sets = new ArrayList<>();
        for (List<Integer> c : clusters) {
            sets.add(new java.util.LinkedHashSet<>(c));
        }

        boolean changed;
        do {
            changed = false;

            int i = 0;
            while (i < sets.size()) {
                java.util.Set<Integer> a = sets.get(i);
                int j = i + 1;

                while (j < sets.size()) {
                    java.util.Set<Integer> b = sets.get(j);

                    if (!disjoint(a, b)) {
                        // Merge b into a and remove b from the list.
                        a.addAll(b);
                        sets.remove(j);
                        changed = true;
                        // Do NOT increment j here: the next element has shifted into index j.
                    } else {
                        j++;
                    }
                }

                i++;
            }
        } while (changed);

        List<List<Integer>> out = new ArrayList<>();
        for (java.util.Set<Integer> s : sets) {
            out.add(new ArrayList<>(s));
        }
        return out;
    }

    private boolean disjoint(java.util.Set<Integer> a, java.util.Set<Integer> b) {
        for (Integer x : a) {
            if (b.contains(x)) return false;
        }
        return true;
    }

    // ---------------------- Utilities ---------------------------

    private SimpleMatrix subCov(SimpleMatrix S, List<Integer> rows, List<Integer> cols) {
        SimpleMatrix out = new SimpleMatrix(rows.size(), cols.size());
        for (int i = 0; i < rows.size(); i++) {
            for (int j = 0; j < cols.size(); j++) {
                out.set(i, j, S.get(rows.get(i), cols.get(j)));
            }
        }
        return out;
    }

    private String clustersAsNames(List<List<Integer>> cl) {
        return cl.stream()
                .map(c -> c.stream().map(i -> vars.get(i).getName()).toList().toString())
                .collect(Collectors.joining(" | "));
    }

    private record Split(List<Integer> firstHalf, List<Integer> secondHalf) {
    }
}
