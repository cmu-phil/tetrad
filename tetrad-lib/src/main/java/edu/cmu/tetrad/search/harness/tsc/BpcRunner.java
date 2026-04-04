///////////////////////////////////////////////////////////////////////////////
// Copyright (C) 2025 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
// and Richard Scheines.                                                     //
//                                                                           //
// This program is free software; see LICENSE for details.                   //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.search.harness.tsc;

import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.Bpc;

import java.util.*;

/**
 * {@link AlgorithmRunner} implementation for the BPC (Build Pure Clusters)
 * algorithm.
 *
 * <p>BPC accepts a {@link CovarianceMatrix} and a significance level.  This
 * runner computes the sample covariance matrix from the supplied
 * {@link DataSet} and passes the nominal sample size as the effective sample
 * size.
 *
 * <p>BPC's {@code search()} returns a {@link edu.cmu.tetrad.graph.Graph}
 * representing the recovered measurement structure.  The pure clusters are
 * extracted from that graph as the sets of measured children sharing the
 * same latent parent.
 *
 * @author josephramsey
 */
public final class BpcRunner implements AlgorithmRunner {

    private final double alpha;

    /**
     * Creates a BPC runner with the given significance level.
     *
     * @param alpha significance level for tetrad tests (e.g. 0.01).
     */
    public BpcRunner(double alpha) {
        if (alpha <= 0 || alpha >= 1)
            throw new IllegalArgumentException("alpha must be in (0, 1).");
        this.alpha = alpha;
    }

    @Override
    public List<Set<Node>> run(DataSet dataSet) {
        if (dataSet == null) throw new IllegalArgumentException("DataSet must not be null.");

        CovarianceMatrix cov = new CovarianceMatrix(dataSet);
        int ess = dataSet.getNumRows();

        Bpc bpc = new Bpc(cov, alpha, ess);

        // search() returns a Graph whose latent nodes each have a set of
        // measured children representing one recovered pure cluster.
        // TrueClusterExtractor.extractClusters() works on any Graph with
        // the right node types, so we reuse it here.
        List<List<Integer>> result = bpc.getClusters();

        List<Set<Node>> clusters = new ArrayList<>(result.size());

        for (List<Integer> cluster : result) {
            Set<Node> nodes = new HashSet<>();
            for (int i : cluster) {
                nodes.add(dataSet.getVariable(i));
            }
            clusters.add(Collections.unmodifiableSet(nodes));
        }

        return clusters;
    }

    @Override
    public String label() {
        return "BPC";
    }
}
