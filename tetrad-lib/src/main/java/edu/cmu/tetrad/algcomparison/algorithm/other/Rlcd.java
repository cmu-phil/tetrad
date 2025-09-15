package edu.cmu.tetrad.algcomparison.algorithm.other;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.annotation.AlgType;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.RlcdCore;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TetradLogger;

import java.util.*;

///////////////////////////////////////////////////////////////////////////////
// RLCD (Rank-based Latent Causal Discovery)                                  //
// Stage-1 structure identification (CPDAG over observables); optional GIN.   //
// GPL-3.0-or-later header recommended for this file.                         //
///////////////////////////////////////////////////////////////////////////////
//@edu.cmu.tetrad.annotation.Algorithm(
//        name = "RLCD (Dong et al. 2024)",
//        command = "rlcd",
//        algoType = AlgType.search_for_structure_over_latents
//)
public class Rlcd implements Algorithm, HasKnowledge {

    private Knowledge knowledge = new Knowledge();
    private final boolean verbose;

    public Rlcd() { this(false); }
    public Rlcd(boolean verbose) { this.verbose = verbose; }

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
            N = (int)Math.round(cov.getSampleSize());
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

    @Override public Graph getComparisonGraph(Graph graph) { return graph; }
    @Override public void setKnowledge(Knowledge knowledge) { this.knowledge = knowledge != null ? knowledge.copy() : new Knowledge(); }
    @Override public Knowledge getKnowledge() { return knowledge.copy(); }
    @Override public String getDescription() {
        return "Rank-based Latent Causal Discovery (Stage-1 MEC/CPDAG; optional GIN orientation).";
    }

    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }

    @Override
    public List<String> getParameters() {
        return List.of();
    }
}
