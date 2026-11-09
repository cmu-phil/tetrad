///////////////////////////////////////////////////////////////////////////////
// Copyright (C) 2025 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
// and Richard Scheines.                                                     //
//                                                                           //
// This program is free software; see LICENSE for details.                   //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.search.harness.tsc;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Node;

import java.util.List;
import java.util.Set;

/**
 * Common interface for the four cluster-recovery algorithms used in the
 * simulation study: TSC, BPC, FOFC, and FTFC.
 *
 * <p>Each implementation is responsible for extracting whatever input it
 * needs (a {@link edu.cmu.tetrad.data.CovarianceMatrix} or the raw
 * {@link DataSet}) from the supplied {@code DataSet}, running its search,
 * and converting the result to a uniform {@code List<Set<Node>>}.
 *
 * <p>The effective sample size passed to algorithms that require it is
 * always equal to the nominal sample size ({@code dataSet.getNumRows()}).
 *
 * @author josephramsey
 */
public interface AlgorithmRunner {

    /**
     * Runs the algorithm on {@code dataSet} and returns the recovered clusters.
     *
     * @param dataSet the simulated data; must not be {@code null}.
     * @return a list of recovered clusters, each cluster being a set of
     *         {@link Node} objects drawn from {@code dataSet.getVariables()}.
     *         Returns an empty list (never {@code null}) if the algorithm
     *         finds no clusters.
     */
    List<Set<Node>> run(DataSet dataSet);

    /**
     * Returns a short human-readable label for use in result tables.
     * Examples: {@code "TSC"}, {@code "BPC"}, {@code "FOFC"}, {@code "FTFC"}.
     *
     * @return the algorithm label; never {@code null}.
     */
    String label();
}
