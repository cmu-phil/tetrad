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
import edu.cmu.tetrad.search.Tsc;

import java.util.*;

/**
 * {@link AlgorithmRunner} implementation for the TSC algorithm.
 *
 * <p>TSC accepts a {@link CovarianceMatrix} directly, so this runner
 * computes the sample covariance matrix from the supplied {@link DataSet}
 * and passes it to {@link Tsc}.
 *
 * <p>TSC's {@code findClusters()} returns a
 * {@code Map<Set<Integer>, Integer>} whose keys are sets of
 * <em>zero-based variable indices</em> into the covariance matrix's
 * variable list, and whose values are the estimated rank of each cluster.
 * This runner converts those index sets back to sets of {@link Node}
 * objects so that the result is comparable with the true clusters
 * produced by {@link TrueClusterExtractor}.
 *
 * @author josephramsey
 */
public final class TscRunner implements AlgorithmRunner {

    private final double alpha;
    private final int    rMin;
    private final int    rMax;
    private final int    minRedundancy;

    /**
     * Creates a TSC runner with the given settings.
     *
     * @param alpha         significance level for rank tests (e.g. 0.01).
     * @param rMin          minimum rank to search for (e.g. 1 for rank-1 study)
     * @param rMax          maximum rank to search for (e.g. 1 for rank-1
     *                      study, 2 for rank-2 study).
     * @param minRedundancy minimum redundancy ({@code delta}); clusters
     *                      smaller than {@code r + 1 + minRedundancy} are
     *                      rejected.  Default 1.
     */
    public TscRunner(double alpha, int rMin, int rMax, int minRedundancy) {
        if (alpha <= 0 || alpha >= 1)
            throw new IllegalArgumentException("alpha must be in (0, 1).");
        if (rMax < 1)
            throw new IllegalArgumentException("rMax must be >= 1.");
        if (minRedundancy < 0)
            throw new IllegalArgumentException("minRedundancy must be >= 0.");
        this.alpha         = alpha;
        this.rMin          = rMin;
        this.rMax          = rMax;
        this.minRedundancy = minRedundancy;
    }

    @Override
    public List<Set<Node>> run(DataSet dataSet) {
        if (dataSet == null) throw new IllegalArgumentException("DataSet must not be null.");

        List<Node> variables = dataSet.getVariables();
        CovarianceMatrix cov = new CovarianceMatrix(dataSet);

        Tsc tsc = new Tsc(variables, cov);
        tsc.setAlpha(alpha);
        tsc.setRmin(rMin);
        tsc.setRmax(rMax);
        tsc.setEffectiveSampleSize(-1);
        tsc.setMinRedundancy(minRedundancy);
        tsc.setParallel(false);
//        tsc.setDiscoveryAlpha(.2);
//        tsc.setAlpha(0.00001);

        // findClusters() returns Map<Set<Integer>, Integer>:
        //   key   = set of zero-based indices into the variable list
        //   value = estimated rank of the cluster (not needed here)
        Map<Set<Integer>, Integer> clusterMap = tsc.findClusters();

        System.out.println("TSC found " + clusterMap);

        List<Set<Node>> result = new ArrayList<>(clusterMap.size());
        for (Set<Integer> indexSet : clusterMap.keySet()) {
            Set<Node> nodeSet = new LinkedHashSet<>(indexSet.size() * 2);
            for (int idx : indexSet) {
                nodeSet.add(variables.get(idx));
            }
            result.add(Collections.unmodifiableSet(nodeSet));
        }
        return result;
    }

    @Override
    public String label() {
        return "TSC";
    }
}
