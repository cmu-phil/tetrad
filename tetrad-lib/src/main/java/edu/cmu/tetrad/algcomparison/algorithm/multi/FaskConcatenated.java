package edu.cmu.tetrad.algcomparison.algorithm.multi;

import edu.cmu.tetrad.algcomparison.algorithm.MultiDataSetAlgorithm;
import edu.cmu.tetrad.algcomparison.score.ScoreWrapper;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.algcomparison.utils.UsesScoreWrapper;
import edu.cmu.tetrad.annotation.AlgType;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.Fask;
import edu.cmu.tetrad.util.Parameters;
import edu.pitt.dbmi.algo.bootstrap.BootstrapEdgeEnsemble;
import edu.pitt.dbmi.algo.bootstrap.GeneralBootstrapTest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Wraps the IMaGES algorithm for continuous variables.
 * </p>
 * Requires that the parameter 'randomSelectionSize' be set to indicate how many
 * datasets should be taken at a time (randomly). This cannot given multiple
 * values.
 *
 * @author jdramsey
 */
@edu.cmu.tetrad.annotation.Algorithm(
        name = "FASK Concatenated",
        command = "fask-concatenated",
        algoType = AlgType.forbid_latent_common_causes,
        description = "Searches multiple continuous datasets for models with possible cycles and 2-cycles, assuming " +
                "the variables are skewed. Latent common causes are not supported. Uses the Fast Adjacency Search (FAS, that is, " +
                "the adjacency search of the PC algorithms) with the linear, Gaussian BIC score as a test of conditional " +
                "independence. One may adjust sparsity of the graph by adjusting the 'penaltyDiscount' parameter. The " +
                "orientation procedure assumes the variables are skewed. Sensitivity for detection of 2-cycles may be " +
                "adjusted using the 2-cycle alpha parameter. Data from different datasets are centered and concatenated, then given to\" +\n" +
                "FASK for search."
)
public class FaskConcatenated implements MultiDataSetAlgorithm, HasKnowledge, UsesScoreWrapper {

    static final long serialVersionUID = 23L;
    private ScoreWrapper score;
    private IKnowledge knowledge = new Knowledge2();

    public FaskConcatenated() {

    }

    public FaskConcatenated(ScoreWrapper score) {
        this.score = score;
    }

    @Override
    public Graph search(List<DataModel> dataSets, Parameters parameters) {
        if (parameters.getInt("bootstrapSampleSize") < 1) {
            List<DataSet> centered = new ArrayList<>();

            for (DataModel dataSet : dataSets) {
                centered.add(DataUtils.standardizeData((DataSet) dataSet));
            }

            DataSet dataSet = DataUtils.concatenate(centered);
            Fask search = new Fask(dataSet, score.getScore(dataSet, parameters));
            search.setDepth(parameters.getInt("depth"));
            search.setPenaltyDiscount(parameters.getDouble("penaltyDiscount"));
            search.setExtraEdgeThreshold(parameters.getDouble("extraEdgeThreshold"));
            search.setDelta(parameters.getDouble("faskDelta"));
            search.setAlpha(parameters.getDouble("twoCycleAlpha"));
            search.setKnowledge(knowledge);
            return search.search();
        } else {
            FaskConcatenated algorithm = new FaskConcatenated(score);
            algorithm.setKnowledge(knowledge);

            List<DataSet> datasets = new ArrayList<>();

            for (DataModel dataModel : dataSets) {
                datasets.add((DataSet) dataModel);
            }
            GeneralBootstrapTest search = new GeneralBootstrapTest(datasets, algorithm,
                    parameters.getInt("bootstrapSampleSize"));

            BootstrapEdgeEnsemble edgeEnsemble = BootstrapEdgeEnsemble.Highest;
            switch (parameters.getInt("bootstrapEnsemble", 1)) {
                case 0:
                    edgeEnsemble = BootstrapEdgeEnsemble.Preserved;
                    break;
                case 1:
                    edgeEnsemble = BootstrapEdgeEnsemble.Highest;
                    break;
                case 2:
                    edgeEnsemble = BootstrapEdgeEnsemble.Majority;
            }
            search.setEdgeEnsemble(edgeEnsemble);
            search.setParameters(parameters);
            search.setVerbose(parameters.getBoolean("verbose"));
            return search.search();
        }
    }

    @Override
    public Graph search(DataModel dataSet, Parameters parameters) {
        if (!parameters.getBoolean("bootstrapping")) {
            return search(Collections.singletonList((DataModel) DataUtils.getContinuousDataSet(dataSet)), parameters);
        } else {
            FaskConcatenated algorithm = new FaskConcatenated(score);
            algorithm.setKnowledge(knowledge);

            List<DataSet> dataSets = Collections.singletonList(DataUtils.getContinuousDataSet(dataSet));
            GeneralBootstrapTest search = new GeneralBootstrapTest(dataSets, algorithm,
                    parameters.getInt("bootstrapSampleSize"));

            BootstrapEdgeEnsemble edgeEnsemble = BootstrapEdgeEnsemble.Highest;
            switch (parameters.getInt("bootstrapEnsemble", 1)) {
                case 0:
                    edgeEnsemble = BootstrapEdgeEnsemble.Preserved;
                    break;
                case 1:
                    edgeEnsemble = BootstrapEdgeEnsemble.Highest;
                    break;
                case 2:
                    edgeEnsemble = BootstrapEdgeEnsemble.Majority;
            }
            search.setEdgeEnsemble(edgeEnsemble);
            search.setParameters(parameters);
            search.setVerbose(parameters.getBoolean("verbose"));
            return search.search();
        }
    }

    @Override
    public Graph getComparisonGraph(Graph graph) {
        return new EdgeListGraph(graph);
    }

    @Override
    public String getDescription() {
        return "FASK Concatenated";
    }

    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }

    @Override
    public List<String> getParameters() {
        List<String> parameters = score.getParameters();
        parameters.add("twoCycleAlpha");
        parameters.add("depth");
//        parameters.add("penaltyDiscount");
        parameters.add("extraEdgeThreshold");
        parameters.add("faskDelta");
        parameters.add("reverseOrientationsBySkewnessOfVariables");
        parameters.add("reverseOrientationsBySignOfCorrelation");
        parameters.add("numRuns");
        parameters.add("randomSelectionSize");

        // Bootstrapping
        parameters.add("bootstrapSampleSize");
        parameters.add("bootstrapEnsemble");
        parameters.add("verbose");

        return parameters;
    }

    @Override
    public IKnowledge getKnowledge() {
        return knowledge;
    }

    @Override
    public void setKnowledge(IKnowledge knowledge) {
        this.knowledge = knowledge;
    }

    @Override
    public void setScoreWrapper(ScoreWrapper score) {
        this.score = score;
    }
}
