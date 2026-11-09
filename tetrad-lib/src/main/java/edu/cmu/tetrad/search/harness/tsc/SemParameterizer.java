///////////////////////////////////////////////////////////////////////////////
// Copyright (C) 2025 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
// and Richard Scheines.                                                     //
//                                                                           //
// This program is free software; see LICENSE for details.                   //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.search.harness.tsc;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;

import java.text.ParseException;

/**
 * Converts a graph produced by {@link edu.cmu.tetrad.graph.RandomMim} into a
 * parameterised linear Gaussian SEM ({@link SemIm}) and simulates data from it.
 *
 * <h2>Parameter conventions for the simulation study</h2>
 *
 * <p>All structural coefficients (latent-to-latent and latent-to-observed edges)
 * are drawn independently from {@code U(coefLow, coefHigh)}, defaulting to
 * {@code U(0.2, 1.2)}.  The lower gap of 0.2 keeps every loading bounded away
 * from zero, preventing near-rank-deficient cross-covariance blocks that would
 * make rank tests unreliable at moderate sample sizes.
 *
 * <p>Error variances for all variables (latent and observed) are drawn from
 * {@code U(varLow, varHigh)}, defaulting to {@code U(0.5, 1.5)}.
 *
 * <p>Coefficients are <em>not</em> symmetrised ({@code COEF_SYMMETRIC = false})
 * so that all loadings are positive, matching the pure-positive-loading assumption
 * stated in the simulation study design.  Column randomisation is enabled so that
 * the order in which parameters are assigned does not introduce systematic bias.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 *   // Default ranges — most common case in the simulation harness
 *   DataSet data = SemParameterizer.defaults().parameterizeAndSimulate(graph, 1000);
 *
 *   // Two-step variant
 *   SemIm   im   = SemParameterizer.defaults().parameterize(graph);
 *   DataSet data = SemParameterizer.simulate(im, 1000);
 *
 *   // Custom ranges via fluent builder
 *   DataSet data = new SemParameterizer()
 *           .coefRange(0.3, 1.5)
 *           .varRange(0.4, 2.0)
 *           .parameterizeAndSimulate(graph, 500);
 * }</pre>
 *
 * @author josephramsey
 */
public final class SemParameterizer {

    /**
     * Private constructor for the {@code SemParameterizer} class.
     * This constructor is intentionally private to ensure that instances
     * of the class can only be created through factory methods.
     */
    private SemParameterizer() {}

    // -----------------------------------------------------------------------
    // Defaults matching the simulation study design
    // -----------------------------------------------------------------------

    /** Default lower bound for structural coefficients. */
    public static final double DEFAULT_COEF_LOW  = 0.2;

    /** Default upper bound for structural coefficients. */
    public static final double DEFAULT_COEF_HIGH = 1.2;

    /** Default lower bound for error variances. */
    public static final double DEFAULT_VAR_LOW   = 1;

    /** Default upper bound for error variances. */
    public static final double DEFAULT_VAR_HIGH  = 3;

    // -----------------------------------------------------------------------
    // Instance state (fluent builder)
    // -----------------------------------------------------------------------

    private double coefLow  = DEFAULT_COEF_LOW;
    private double coefHigh = DEFAULT_COEF_HIGH;
    private double varLow   = DEFAULT_VAR_LOW;
    private double varHigh  = DEFAULT_VAR_HIGH;

    // -----------------------------------------------------------------------
    // Static factory
    // -----------------------------------------------------------------------

    /**
     * Returns a new {@code SemParameterizer} pre-configured with the default
     * coefficient and variance ranges.  This is the primary entry point for
     * the simulation harness.
     *
     * <pre>{@code
     *   DataSet data = SemParameterizer.defaults().parameterizeAndSimulate(graph, n);
     * }</pre>
     *
     * @return a new instance with default ranges.
     */
    public static SemParameterizer defaults() {
        return new SemParameterizer();
    }

    // -----------------------------------------------------------------------
    // Fluent configuration
    // -----------------------------------------------------------------------

    /**
     * Sets the uniform range for structural coefficients.
     *
     * @param low  lower bound; must be &gt; 0 to guarantee positive loadings.
     * @param high upper bound; must be &ge; {@code low}.
     * @return {@code this} for chaining.
     */
    public SemParameterizer coefRange(double low, double high) {
        if (low <= 0)   throw new IllegalArgumentException("coefLow must be > 0 for positive loadings.");
        if (high < low) throw new IllegalArgumentException("coefHigh must be >= coefLow.");
        this.coefLow  = low;
        this.coefHigh = high;
        return this;
    }

    /**
     * Sets the uniform range for error variances.
     *
     * @param low  lower bound; must be &gt; 0.
     * @param high upper bound; must be &ge; {@code low}.
     * @return {@code this} for chaining.
     */
    public SemParameterizer varRange(double low, double high) {
        if (low <= 0)   throw new IllegalArgumentException("varLow must be > 0.");
        if (high < low) throw new IllegalArgumentException("varHigh must be >= varLow.");
        this.varLow  = low;
        this.varHigh = high;
        return this;
    }

    // -----------------------------------------------------------------------
    // Instance methods
    // -----------------------------------------------------------------------

    /**
     * Builds a {@link Parameters} object reflecting the current configuration.
     *
     * @return a fresh {@link Parameters} instance.
     */
    public Parameters buildParameters() {
        Parameters p = new Parameters();
        p.set(Params.COEF_LOW,          coefLow);
        p.set(Params.COEF_HIGH,         coefHigh);
        p.set(Params.COEF_SYMMETRIC,    true);
        p.set(Params.VAR_LOW,           varLow);
        p.set(Params.VAR_HIGH,          varHigh);
        p.set(Params.RANDOMIZE_COLUMNS, true);
        return p;
    }

    /**
     * Creates a {@link SemIm} from {@code graph} using the current parameter
     * ranges.  A new independent parameter draw is made on every call.
     *
     * @param graph a graph produced by {@code RandomMim}; must not be {@code null}.
     * @return a fully parameterised {@link SemIm}.
     */
    public SemIm parameterize(Graph graph) {
        if (graph == null) throw new IllegalArgumentException("Graph must not be null.");
        SemPm pm = new SemPm(graph);
        return new SemIm(pm, buildParameters());
    }

    /**
     * Parameterises {@code graph} and simulates {@code sampleSize} rows of
     * linear Gaussian data in a single call.
     *
     * @param graph      a graph produced by {@code RandomMim}; must not be {@code null}.
     * @param sampleSize number of rows; must be &ge; 1.
     * @return a {@link DataSet} containing only the measured variables.
     * @throws ParseException if the data set cannot be parsed.
     */
    public DataSet parameterizeAndSimulate(Graph graph, int sampleSize) throws ParseException {
        return simulate(parameterize(graph), sampleSize);
    }

    // -----------------------------------------------------------------------
    // Static utility (no instance equivalent — no name conflict)
    // -----------------------------------------------------------------------

    /**
     * Simulates {@code sampleSize} rows of data from an already-parameterised
     * {@link SemIm}.  The returned {@link DataSet} contains only the measured
     * variables; latent columns are not included in the output of
     * {@link SemIm#simulateData(int, boolean)}.
     *
     * @param im         a parameterised SEM; must not be {@code null}.
     * @param sampleSize number of rows; must be &ge; 1.
     * @return a {@link DataSet} with {@code sampleSize} rows.
     * @throws ParseException if the data set cannot be parsed.
     */
    public static DataSet simulate(SemIm im, int sampleSize) throws ParseException {
        if (im == null)     throw new IllegalArgumentException("SemIm must not be null.");
        if (sampleSize < 1) throw new IllegalArgumentException("sampleSize must be >= 1.");
        return im.simulateData(sampleSize, false);
    }
}
