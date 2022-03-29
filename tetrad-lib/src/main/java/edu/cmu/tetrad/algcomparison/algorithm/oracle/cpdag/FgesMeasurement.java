package edu.cmu.tetrad.algcomparison.algorithm.oracle.cpdag;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.score.ScoreWrapper;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.annotation.Bootstrapping;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.Fges;
import edu.cmu.tetrad.search.SearchGraphUtils;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.cmu.tetrad.util.RandomUtil;
import edu.pitt.dbmi.algo.resampling.GeneralResamplingTest;
import edu.pitt.dbmi.algo.resampling.ResamplingEdgeEnsemble;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

/**
 * FGES (the heuristic version).
 *
 * @author jdramsey
 */
@Bootstrapping
public class FgesMeasurement implements Algorithm, HasKnowledge {

    static final long serialVersionUID = 23L;
    private final ScoreWrapper score;
    private IKnowledge knowledge = new Knowledge2();

    public FgesMeasurement(final ScoreWrapper score) {
        this.score = score;
    }

    @Override
    public Graph search(final DataModel dataModel, final Parameters parameters) {
        if (parameters.getInt(Params.NUMBER_RESAMPLING) < 1) {
            DataSet dataSet = DataUtils.getContinuousDataSet(dataModel);
            dataSet = dataSet.copy();

            dataSet = DataUtils.standardizeData(dataSet);
            final double variance = parameters.getDouble(Params.MEASUREMENT_VARIANCE);

            if (variance > 0) {
                for (int i = 0; i < dataSet.getNumRows(); i++) {
                    for (int j = 0; j < dataSet.getNumColumns(); j++) {
                        final double d = dataSet.getDouble(i, j);
                        final double norm = RandomUtil.getInstance().nextNormal(0, Math.sqrt(variance));
                        dataSet.setDouble(i, j, d + norm);
                    }
                }
            }

            final Fges search = new Fges(this.score.getScore(dataSet, parameters));
            search.setFaithfulnessAssumed(parameters.getBoolean(Params.FAITHFULNESS_ASSUMED));
            search.setKnowledge(this.knowledge);
            search.setVerbose(parameters.getBoolean(Params.VERBOSE));

            final Object obj = parameters.get(Params.PRINT_STREAM);
            if (obj instanceof PrintStream) {
                search.setOut((PrintStream) obj);
            }

            return search.search();
        } else {
            final FgesMeasurement fgesMeasurement = new FgesMeasurement(this.score);

            final DataSet data = (DataSet) dataModel;
            final GeneralResamplingTest search = new GeneralResamplingTest(data, fgesMeasurement, parameters.getInt(Params.NUMBER_RESAMPLING));
            search.setKnowledge(this.knowledge);

            search.setPercentResampleSize(parameters.getDouble(Params.PERCENT_RESAMPLE_SIZE));
            search.setResamplingWithReplacement(parameters.getBoolean(Params.RESAMPLING_WITH_REPLACEMENT));

            ResamplingEdgeEnsemble edgeEnsemble = ResamplingEdgeEnsemble.Highest;
            switch (parameters.getInt(Params.RESAMPLING_ENSEMBLE, 1)) {
                case 0:
                    edgeEnsemble = ResamplingEdgeEnsemble.Preserved;
                    break;
                case 1:
                    edgeEnsemble = ResamplingEdgeEnsemble.Highest;
                    break;
                case 2:
                    edgeEnsemble = ResamplingEdgeEnsemble.Majority;
            }
            search.setEdgeEnsemble(edgeEnsemble);
            search.setAddOriginalDataset(parameters.getBoolean(Params.ADD_ORIGINAL_DATASET));

            search.setParameters(parameters);
            search.setVerbose(parameters.getBoolean(Params.VERBOSE));
            return search.search();
        }
    }

    @Override
    public Graph getComparisonGraph(final Graph graph) {
        return SearchGraphUtils.cpdagForDag(new EdgeListGraph(graph));
    }

    @Override
    public String getDescription() {
        return "FGES adding measuremnt noise using " + this.score.getDescription();
    }

    @Override
    public DataType getDataType() {
        return this.score.getDataType();
    }

    @Override
    public List<String> getParameters() {
        final List<String> parameters = new ArrayList<>();
        parameters.add(Params.FAITHFULNESS_ASSUMED);
        parameters.add(Params.MEASUREMENT_VARIANCE);

        parameters.add(Params.VERBOSE);

        return parameters;
    }

    @Override
    public IKnowledge getKnowledge() {
        return this.knowledge;
    }

    @Override
    public void setKnowledge(final IKnowledge knowledge) {
        this.knowledge = knowledge;
    }

}
