package edu.cmu.tetrad.algcomparison.algorithm.oracle.cpdag;

import edu.cmu.tetrad.algcomparison.algorithm.*;
import edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.algcomparison.utils.TakesIndependenceWrapper;
import edu.cmu.tetrad.annotation.AlgType;
import edu.cmu.tetrad.annotation.Bootstrapping;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphTransforms;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

/**
 * CD-NOD wrapper for algcomparison.
 */
@edu.cmu.tetrad.annotation.Algorithm(
        name = "CD-NOD",
        command = "cdnod",
        algoType = AlgType.forbid_latent_common_causes
)
@Bootstrapping
public class Cdnod extends AbstractBootstrapAlgorithm implements Algorithm, HasKnowledge,
        TakesIndependenceWrapper, ReturnsBootstrapGraphs, TakesCovarianceMatrix, LatentStructureAlgorithm {

    @Serial
    private static final long serialVersionUID = 23L;

    private IndependenceWrapper test;
    private Knowledge knowledge = new Knowledge();

    public Cdnod() {
    }

    public Cdnod(IndependenceWrapper test) {
        this.test = test;
    }

    @Override
    protected Graph runSearch(DataModel dataModel, Parameters parameters) throws InterruptedException {
        DataSet data = (DataSet) dataModel;

        // Map collider style param -> search enum
        edu.cmu.tetrad.search.Cdnod.ColliderOrientationStyle colliderOrientationStyle =
                switch (parameters.getInt(Params.COLLIDER_ORIENTATION_STYLE)) {
                    case 1 -> edu.cmu.tetrad.search.Cdnod.ColliderOrientationStyle.SEPSETS;
                    case 2 -> edu.cmu.tetrad.search.Cdnod.ColliderOrientationStyle.CONSERVATIVE;
                    case 3 -> edu.cmu.tetrad.search.Cdnod.ColliderOrientationStyle.MAX_P;
                    default -> throw new IllegalArgumentException("Invalid collider orientation style");
                };

        // Build the IndependenceTest over this dataset
        IndependenceTest indTest = getIndependenceWrapper().getTest(dataModel, parameters);

        // Pull runtime knobs from Parameters (with sensible defaults)
        boolean stable = parameters.getBoolean(Params.STABLE_FAS, true);
        int depth = parameters.getInt(Params.DEPTH, -1);
        boolean verbose = parameters.getBoolean(Params.VERBOSE, false);
        double alpha = parameters.getDouble(Params.ALPHA, 0.05);
        double maxPMargin = 0.0;//parameters.getDouble(Params.MAXP_MARGIN, 0.0_;

        // Configure core search. We pass the dataset that ALREADY has C as the last column.
        edu.cmu.tetrad.search.Cdnod cd = new edu.cmu.tetrad.search.Cdnod.Builder()
                .test(indTest)
                .data(data)                         // << important: use already-augmented data
                .alpha(alpha)                       // kept for parity (FAS may ignore)
                .stable(stable)
                .colliderStyle(colliderOrientationStyle)
                .maxPMargin(Math.max(0.0, maxPMargin)) // 0.0 => classic MAX-P
                .depth(depth)                       // -1 => no cap
                .knowledge(knowledge)
                .verbose(verbose)
                .build();

        Graph g = cd.search();

        // If you want FDR post-processing, uncomment this block:
        /*
        double fdrQ = parameters.getDouble(Params.FDR_Q);
        if (fdrQ != 0.0) {
            boolean negativelyCorrelated = true;
            Graph fdrGraph = IndTestFdrWrapper.doFdrLoop(cd::search, negativelyCorrelated, alpha, fdrQ, verbose);
            return fdrGraph;
        }
        */

        return g;
    }

    @Override
    public Graph getComparisonGraph(Graph graph) {
        Graph dag = new EdgeListGraph(graph);
        return GraphTransforms.dagToCpdag(dag);
    }

    @Override
    public String getDescription() {
        return "CD-NOD using " + (this.test != null ? this.test.getDescription() : "configured test");
    }

    @Override
    public DataType getDataType() {
        return this.test.getDataType();
    }

    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();
        parameters.add(Params.STABLE_FAS);
        parameters.add(Params.COLLIDER_ORIENTATION_STYLE);
//        parameters.add(Params.ALLOW_BIDIRECTED); // currently ignored by CD-NOD core
        parameters.add(Params.DEPTH);
        parameters.add(Params.FDR_Q);
//        parameters.add(Params.TIME_LAG);         // not applied here; leave for future
        parameters.add(Params.VERBOSE);
//        parameters.add(Params.ALPHA);            // optional pass-through
//        parameters.add(Params.MAXP_MARGIN);      // optional pass-through
        return parameters;
    }

    @Override
    public Knowledge getKnowledge() {
        return this.knowledge;
    }

    @Override
    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = new Knowledge(knowledge);
    }

    @Override
    public IndependenceWrapper getIndependenceWrapper() {
        return this.test;
    }

    @Override
    public void setIndependenceWrapper(IndependenceWrapper test) {
        this.test = test;
    }
}