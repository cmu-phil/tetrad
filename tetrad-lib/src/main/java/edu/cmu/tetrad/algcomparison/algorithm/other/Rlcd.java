package edu.cmu.tetrad.algcomparison.algorithm.other;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.RlcdCore;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TetradLogger;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/// ////////////////////////////////////////////////////////////////////////////
// RLCD (Rank-based Latent Causal Discovery)                                  //
// Stage-1 structure identification (CPDAG over observables); optional GIN.   //
// GPL-3.0-or-later header recommended for this file.                         //

/// ////////////////////////////////////////////////////////////////////////////
//@edu.cmu.tetrad.annotation.Algorithm(
//        name = "RLCD (Dong et al. 2024)",
//        command = "rlcd",
//        algoType = AlgType.search_for_structure_over_latents
//)
public class Rlcd implements Algorithm, HasKnowledge {

    /**
     * A flag indicating whether verbose logging and debugging information should be enabled.
     */
    private final boolean verbose;
    /**
     * Represents the knowledge object associated with this RLCD instance. This field stores causal or domain-specific
     * knowledge that may be used to guide or constrain the RLCD algorithm during its execution.
     * <p>
     * The {@code knowledge} field is initialized with a new {@code Knowledge} object by default, and it can be updated
     * through the {@code setKnowledge(Knowledge)} method. A copy of the stored knowledge object can also be retrieved
     * using the {@code getKnowledge()} method.
     * <p>
     * This knowledge can encode prior information such as forbidden edges, required edges, and other causal or
     * structural constraints that influence the results of the algorithm.
     */
    private Knowledge knowledge = new Knowledge();

    /**
     * Constructs an instance of Rlcd with default settings.
     */
    public Rlcd() {
        this(false);
    }

    /**
     * Constructs an instance of the Rlcd class with the specified verbosity setting.
     *
     * @param verbose A boolean indicating whether verbose output is enabled. If true, additional informational output
     *                may be provided during the operation of the class methods.
     */
    public Rlcd(boolean verbose) {
        this.verbose = verbose;
    }

    /**
     * Executes the RLCD search algorithm on the provided data model with the specified parameters. The method supports
     * performing a constraint-based search based on rank-based tests to discover causal structures in the data, while
     * allowing optional GIN-based optimizations.
     *
     * @param dataModel The data model on which the search will be performed. It can be of type DataSet or
     *                  ICovarianceMatrix. The data model must not be null.
     * @param params    The set of parameters that configure the RLCD algorithm, including settings such as alpha
     *                  thresholds, stage 1 methods, maximum samples, and rank test configurations. The parameters must
     *                  not be null.
     * @return A {@code Graph} object representing the resulting CPDAG (Completed Partially Directed Acyclic Graph)
     * which encodes the causal relationships and orientations derived from the search.
     * @throws NullPointerException     if {@code dataModel} or {@code params} is null.
     * @throws IllegalArgumentException if the provided {@code dataModel} type is unsupported.
     */
    @Override
    public Graph search(DataModel dataModel, Parameters params) {
        Objects.requireNonNull(dataModel, "dataModel");
        Objects.requireNonNull(params, "params");

        RlcdParams p = new RlcdParams(params);
        if (verbose) {
            TetradLogger.getInstance().log(
                    String.format("RLCD: alpha=%.4g, stage1=%s, maxSamples=%d, method=%s, tau=%.2e, useGIN=%s",
                            p.alpha(), p.stage1Method(), p.maxSamples(), p.rankTestMethod(), p.svdTau(), p.useGin()));
        }

        final CovarianceMatrix cov;
        int N;
        if (dataModel instanceof DataSet ds) {
            DataSet use = ds;
            if (p.maxSamples() > 0 && p.maxSamples() < ds.getNumRows()) {
                List<Integer> rows = new ArrayList<>(ds.getNumRows());
                for (int i = 0; i < p.maxSamples(); i++) {
                    rows.add(i);
                }

                use = ds.subsetRows(rows);
            }
            cov = new CovarianceMatrix(use);
            N = use.getNumRows();
        } else if (dataModel instanceof ICovarianceMatrix cm) {
            cov = new CovarianceMatrix(cm);
            N = (int) Math.round(cov.getSampleSize());
        } else {
            throw new IllegalArgumentException("Unsupported DataModel type: " + dataModel.getClass());
        }

        List<Node> vars = cov.getVariables();
        RlcdCore core = new RlcdCore(p, knowledge, verbose);

        // Stage 1: accumulate constraints with RLCD’s rank-based tests
        RlcdCore.Stage1Output s1 = core.stage1Structure(cov, N);

        // Build CPDAG from constraints
        Graph cpdag = core.buildCpdagFromConstraints(vars, s1);

        // Optional: apply GIN orientations (hook-in to your GIN impl)
        if (p.useGin()) {
            cpdag = core.applyGinOrientations(cov, cpdag, N);
        }

//        cpdag.setAttribute("algorithm", "RLCD");
//        cpdag.setAttribute("alpha", p.alpha());
        return cpdag;
    }

    /**
     * Retrieves a comparison graph based on the provided graph. This method is typically used for scenarios where the
     * input graph needs to undergo minimal or no transformation before being returned as the comparison graph.
     *
     * @param graph The input graph to be used as the basis for the comparison graph. Must not be null.
     * @return A {@code Graph} instance which in this implementation is the same as the input graph.
     */
    @Override
    public Graph getComparisonGraph(Graph graph) {
        return graph;
    }

    /**
     * Retrieves a copy of the {@code Knowledge} object associated with this RLCD instance. This method returns a new
     * instance with the same properties as the original knowledge object, ensuring that the returned copy can be safely
     * manipulated without affecting the original knowledge state maintained by the RLCD instance.
     *
     * @return A new {@code Knowledge} object that is a copy of the current knowledge stored in this RLCD instance.
     */
    @Override
    public Knowledge getKnowledge() {
        return knowledge.copy();
    }

    /**
     * Sets the knowledge object associated with this RLCD instance. If the provided knowledge object is not null, a
     * copy of it is assigned; otherwise, a new, empty {@code Knowledge} object is created and assigned.
     *
     * @param knowledge The {@code Knowledge} object to be set. If null, a new {@code Knowledge} object is initialized.
     */
    @Override
    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = knowledge != null ? knowledge.copy() : new Knowledge();
    }

    /**
     * Provides a description of the RLCD algorithm, including its function and purpose.
     *
     * @return A string describing the RLCD algorithm, specifically "Rank-based Latent Causal Discovery (Stage-1
     * MEC/CPDAG; optional GIN orientation)."
     */
    @Override
    public String getDescription() {
        return "Rank-based Latent Causal Discovery (Stage-1 MEC/CPDAG; optional GIN orientation).";
    }

    /**
     * Returns the data type associated with the RLCD algorithm.
     *
     * @return A {@code DataType} enum value indicating the type of the data. In this implementation, the method always
     * returns {@code DataType.Continuous}.
     */
    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }

    /**
     * Retrieves a list of parameter names or settings associated with this RLCD instance. This method provides the
     * parameters that can be configured or are relevant to the operation of the RLCD algorithm.
     *
     * @return A list of strings representing parameter names or settings. The returned list is immutable and may be
     * empty if no parameters are defined.
     */
    @Override
    public List<String> getParameters() {
        return List.of();
    }
}
