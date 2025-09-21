package edu.cmu.tetrad.algcomparison.algorithm.oracle.cpdag;

import edu.cmu.tetrad.algcomparison.algorithm.AbstractBootstrapAlgorithm;
import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.algorithm.LatentStructureAlgorithm;
import edu.cmu.tetrad.algcomparison.algorithm.ReturnsBootstrapGraphs;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.annotation.AlgType;
import edu.cmu.tetrad.annotation.Bootstrapping;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphTransforms;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

/**
 * CAM (Causal Additive Models) wrapper for algcomparison.
 *
 * This wraps a search that:
 *  - Scores permutations and parent sets using additive (main-effects) basis regression with BIC
 *  - Assumes causal sufficiency (no latent confounders)
 *  - Returns a DAG which is converted to a CPDAG as comparison graph
 *
 * Expected core search class: edu.cmu.tetrad.search.Cam
 */
@edu.cmu.tetrad.annotation.Algorithm(
        name = "CAM (additive BIC)",
        command = "cam",
        algoType = AlgType.forbid_latent_common_causes
)
@Bootstrapping
public class CamAdditive extends AbstractBootstrapAlgorithm implements Algorithm,
        HasKnowledge, ReturnsBootstrapGraphs, LatentStructureAlgorithm {

    @Serial
    private static final long serialVersionUID = 1L;

    private Knowledge knowledge = new Knowledge();

    public CamAdditive() {
    }

    @Override
    protected Graph runSearch(DataModel dataModel, Parameters parameters) throws InterruptedException {
        DataSet data = (DataSet) dataModel;

        // Pull knobs (use conservative defaults where params are absent)
        final int degree            = 3;//parameters.getInt(Params.DEGREE, 3);
        final double ridge          = 1e-6;//parameters.getDouble(Params.RIDGE, 1e-6);
        final double penDiscount    = parameters.getDouble(Params.PENALTY_DISCOUNT, 1.0);
        final boolean verbose       = parameters.getBoolean(Params.VERBOSE, false);

        // Optional caps if your Params enum contains them; otherwise the search will keep its internal defaults.
        final int maxParents        = 20;//parameters.getInt(Params.MAX_PARENTS, 20);
        final int orderIters        = 2000;//parameters.getInt(Params.NUM_RESTARTS, 2000); // reuse an existing int param if desired

        // Configure and run the core CAM search
        edu.cmu.tetrad.search.Cam cam = new edu.cmu.tetrad.search.Cam(data, degree)
                .setRidge(ridge)
                .setPenaltyDiscount(penDiscount)
                .setMaxForwardParents(maxParents)
                .setMaxOrderIters(orderIters)
                .setVerbose(verbose);

        // If you later add knowledge support to the core Cam class, pass it here.
        // cam.setKnowledge(knowledge); // (placeholder)

        Graph dag = cam.search();
        return dag; // return the learned DAG for bootstrapping; comparison graph below is CPDAG
    }

    @Override
    public Graph getComparisonGraph(Graph graph) {
        // Compare as CPDAG like your CD-NOD wrapper does.
        Graph dag = new EdgeListGraph(graph);
        return GraphTransforms.dagToCpdag(dag);
    }

    @Override
    public String getDescription() {
        return "CAM additive-BIC search (main-effects basis; SEM-style BIC scoring)";
    }

    @Override
    public DataType getDataType() {
        // Works for continuous data (basis/OLS BIC). If you extend to mixed types, adjust accordingly.
        return DataType.Continuous;
    }

    @Override
    public List<String> getParameters() {
        List<String> params = new ArrayList<>();
//        params.add(Params.DEGREE);
//        params.add(Params.RIDGE);
        params.add(Params.PENALTY_DISCOUNT);
//        params.add(Params.MAX_PARENTS);
//        params.add(Params.NUM_RESTARTS);   // used here as "max order iterations"
        params.add(Params.VERBOSE);
        return params;
    }

    @Override
    public Knowledge getKnowledge() {
        return this.knowledge;
    }

    @Override
    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = new Knowledge(knowledge);
    }
}