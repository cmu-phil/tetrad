package edu.cmu.tetrad.algcomparison.algorithm.oracle.pattern;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.score.ScoreWrapper;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.algcomparison.utils.TakesInitialGraph;
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
public class FgesMeasurement implements Algorithm, TakesInitialGraph, HasKnowledge {

    static final long serialVersionUID = 23L;
    private ScoreWrapper score;
    private Algorithm algorithm = null;
    private Graph initialGraph = null;
    private IKnowledge knowledge = new Knowledge2();

    public FgesMeasurement(ScoreWrapper score) {
        this.score = score;
    }

    public FgesMeasurement(ScoreWrapper score, Algorithm algorithm) {
        this.score = score;
        this.algorithm = algorithm;
    }

    @Override
    public Graph search(DataModel dataModel, Parameters parameters) {
        if (parameters.getInt(Params.NUMBER_RESAMPLING) < 1) {
            DataSet dataSet = DataUtils.getContinuousDataSet(dataModel);
            dataSet = dataSet.copy();

            dataSet = DataUtils.standardizeData(dataSet);
            double variance = parameters.getDouble(Params.MEASUREMENT_VARIANCE);

            if (variance > 0) {
                for (int i = 0; i < dataSet.getNumRows(); i++) {
                    for (int j = 0; j < dataSet.getNumColumns(); j++) {
                        double d = dataSet.getDouble(i, j);
                        double norm = RandomUtil.getInstance().nextNormal(0, Math.sqrt(variance));
                        dataSet.setDouble(i, j, d + norm);
                    }
                }
            }

            Fges search = new Fges(score.getScore(dataSet, parameters));
            search.setFaithfulnessAssumed(parameters.getBoolean(Params.FAITHFULNESS_ASSUMED));
            search.setKnowledge(knowledge);
            search.setVerbose(parameters.getBoolean(Params.VERBOSE));

            Object obj = parameters.get(Params.PRINT_STREAM);
            if (obj instanceof PrintStream) {
                search.setOut((PrintStream) obj);
            }

            return search.search();
        } else {
            FgesMeasurement fgesMeasurement = new FgesMeasurement(score, algorithm);

            if (initialGraph != null) {
                fgesMeasurement.setInitialGraph(initialGraph);
            }
            DataSet data = (DataSet) dataModel;
            GeneralResamplingTest search = new GeneralResamplingTest(data, fgesMeasurement, parameters.getInt(Params.NUMBER_RESAMPLING));
            search.setKnowledge(knowledge);

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
    public Graph getComparisonGraph(Graph graph) {
        return SearchGraphUtils.patternForDag(new EdgeListGraph(graph));
    }

    @Override
    public String getDescription() {
        return "FGES adding measuremnt noise using " + score.getDescription();
    }

    @Override
    public DataType getDataType() {
        return score.getDataType();
    }

    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();
        parameters.add(Params.FAITHFULNESS_ASSUMED);
        parameters.add(Params.MEASUREMENT_VARIANCE);

        parameters.add(Params.VERBOSE);

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
    public Graph getInitialGraph() {
        return initialGraph;
    }

    @Override
    public void setInitialGraph(Graph initialGraph) {
        this.initialGraph = initialGraph;
    }

    @Override
    public void setInitialGraph(Algorithm algorithm) {
        this.algorithm = algorithm;
    }

}
