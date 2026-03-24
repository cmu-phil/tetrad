///////////////////////////////////////////////////////////////////////////////
// Copyright (C) 2025 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
// and Richard Scheines.                                                     //
//                                                                           //
// This program is free software; see LICENSE for details.                   //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.search.harness.tsc;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.Ftfc;

import java.util.*;

/**
 * {@link AlgorithmRunner} implementation for the FOFC (Find One Factor Clusters)
 * algorithm.
 *
 * <p>FOFC accepts a raw {@link DataSet} directly; no covariance matrix
 * conversion is needed.  The nominal sample size is used as the effective
 * sample size.
 *
 * <p>FOFC's {@code search()} returns a {@link edu.cmu.tetrad.graph.Graph}
 * representing the recovered measurement structure, from which pure clusters
 * are extracted via {@link TrueClusterExtractor#extractClusters(edu.cmu.tetrad.graph.Graph)}.
 *
 * @author josephramsey
 */
public final class FtfcRunner implements AlgorithmRunner {

    private final double alpha;

    /**
     * Creates a FOFC runner with the given significance level.
     *
     * @param alpha significance level for tetrad tests (e.g. 0.01).
     */
    public FtfcRunner(double alpha) {
        if (alpha <= 0 || alpha >= 1)
            throw new IllegalArgumentException("alpha must be in (0, 1).");
        this.alpha = alpha;
    }

    @Override
    public List<Set<Node>> run(DataSet dataSet) {
        if (dataSet == null) throw new IllegalArgumentException("DataSet must not be null.");

        int ess = dataSet.getNumRows();
        Ftfc ftfc = new Ftfc(dataSet, alpha, ess);

        Map<List<Integer>, Integer> result = ftfc.findClusters();

        List<Set<Node>> clusters = new ArrayList<>(result.size());

        for (List<Integer> cluster : result.keySet()) {
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
        return "FTFC";
    }
}

