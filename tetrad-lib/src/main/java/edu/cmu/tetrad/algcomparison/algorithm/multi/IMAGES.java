package edu.cmu.tetrad.algcomparison.algorithm.multi;

import edu.cmu.tetrad.algcomparison.algorithm.MultiDataSetAlgorithm;
import edu.cmu.tetrad.algcomparison.algorithm.oracle.cpdag.FGES;
import edu.cmu.tetrad.algcomparison.score.ScoreWrapper;
import edu.cmu.tetrad.algcomparison.score.SemBicScore;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.algcomparison.utils.UsesScoreWrapper;
import edu.cmu.tetrad.annotation.AlgType;
import edu.cmu.tetrad.annotation.Bootstrapping;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.*;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.pitt.dbmi.algo.resampling.GeneralResamplingTest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * Wraps the IMaGES algorithm for continuous variables.
 * <p>
 * Requires that the parameter 'randomSelectionSize' be set to indicate how many
 * datasets should be taken at a time (randomly). This cannot given multiple
 * values.
 *
 * @author jdramsey
 */
@edu.cmu.tetrad.annotation.Algorithm(
        name = "IMaGES",
        command = "images",
        algoType = AlgType.forbid_latent_common_causes,
        dataType = DataType.All
)
@Bootstrapping
public class IMAGES implements MultiDataSetAlgorithm, HasKnowledge, UsesScoreWrapper {

    static final long serialVersionUID = 23L;
    private Knowledge knowledge = new Knowledge();

    private ScoreWrapper score;

    public IMAGES() {
    }

    @Override
    public Graph search(List<DataModel> dataSets, Parameters parameters) {
        this.knowledge = dataSets.get(0).getKnowledge();
        int meta = parameters.getInt(Params.IMAGES_META_ALG);

        if (parameters.getInt(Params.NUMBER_RESAMPLING) < 1) {
            List<DataModel> _dataSets = new ArrayList<>();

            if (parameters.getInt(Params.TIME_LAG) > 0) {
                for (DataModel dataSet : dataSets) {
                    DataSet timeSeries = TimeSeriesUtils.createLagData((DataSet) dataSet, parameters.getInt(Params.TIME_LAG));
                    if (dataSet.getName() != null) {
                        timeSeries.setName(dataSet.getName());
                    }
                    _dataSets.add(timeSeries);
                }

                dataSets = _dataSets;
                this.knowledge = _dataSets.get(0).getKnowledge();
            }

            List<Score> scores = new ArrayList<>();

            for (DataModel dataModel : dataSets) {
                Score s = score.getScore(dataModel, parameters);
                scores.add(s);
            }

            ImagesScore score = new ImagesScore(scores);

            if (meta == 1) {
                edu.cmu.tetrad.search.Fges search = new edu.cmu.tetrad.search.Fges(score);
                search.setKnowledge(new Knowledge((Knowledge) knowledge));
                search.setVerbose(parameters.getBoolean(Params.VERBOSE));
                return search.search();
            }
//            else if (meta == 2) {
//                Grasp search = new edu.cmu.tetrad.search.Grasp(score);
//                search.setKnowledge(this.knowledge);
//                search.setVerbose(parameters.getBoolean(Params.VERBOSE));
//                search.bestOrder(score.getVariables());
//                search.setDepth(6);
//                search.setSingularDepth(1);
//                search.setNonSingularDepth(1);
//                return search.getGraph(true);
//            }
            else if (meta == 2) {
                PermutationSearch search = new PermutationSearch(new Boss(score));
                search.setKnowledge(new Knowledge((Knowledge) knowledge));
                return search.search();
            }
//            else if (meta == 4) {
//                Boss search = new edu.cmu.tetrad.search.Boss(score);
//                search.setKnowledge(this.knowledge);
//                search.setVerbose(parameters.getBoolean(Params.VERBOSE));
//                search.bestOrder(score.getVariables());
//                return search.getGraph();
//            }
            else if (meta == 3) {
                BridgesOld search = new edu.cmu.tetrad.search.BridgesOld(score);
                search.setKnowledge(this.knowledge);
                search.setVerbose(parameters.getBoolean(Params.VERBOSE));
                return search.search();
            } else {
                throw new IllegalArgumentException("Unrecognized meta option: " + meta);
            }
        } else {
            IMAGES imagesSemBic = new IMAGES();

            List<DataSet> dataSets2 = new ArrayList<>();

            for (DataModel dataModel : dataSets) {
                dataSets2.add((DataSet) dataModel);
            }

            List<DataSet> _dataSets = new ArrayList<>();

            if (parameters.getInt(Params.TIME_LAG) > 0) {
                for (DataSet dataSet : dataSets2) {
                    DataSet timeSeries = TimeSeriesUtils.createLagData(dataSet, parameters.getInt(Params.TIME_LAG));
                    if (dataSet.getName() != null) {
                        timeSeries.setName(dataSet.getName());
                    }
                    _dataSets.add(timeSeries);
                }

                dataSets2 = _dataSets;
            }

            GeneralResamplingTest search = new GeneralResamplingTest(
                    dataSets2,
                    imagesSemBic,
                    parameters.getInt(Params.NUMBER_RESAMPLING),
                    parameters.getDouble(Params.PERCENT_RESAMPLE_SIZE),
                    parameters.getBoolean(Params.RESAMPLING_WITH_REPLACEMENT), parameters.getInt(Params.RESAMPLING_ENSEMBLE), parameters.getBoolean(Params.ADD_ORIGINAL_DATASET));
            search.setParameters(parameters);
            search.setVerbose(parameters.getBoolean(Params.VERBOSE));
            search.setKnowledge(dataSets.get(0).getKnowledge());
            search.setScoreWrapper(score);
            return search.search();
        }
    }

    @Override
    public Graph search(DataModel dataSet, Parameters parameters) {
        if (parameters.getInt(Params.NUMBER_RESAMPLING) < 1) {
            return search(Collections.singletonList(SimpleDataLoader.getMixedDataSet(dataSet)), parameters);
        } else {
            IMAGES images = new IMAGES();

            List<DataSet> dataSets = Collections.singletonList(SimpleDataLoader.getMixedDataSet(dataSet));
            GeneralResamplingTest search = new GeneralResamplingTest(dataSets,
                    images,
                    parameters.getInt(Params.NUMBER_RESAMPLING),
                    parameters.getDouble(Params.PERCENT_RESAMPLE_SIZE),
                    parameters.getBoolean(Params.RESAMPLING_WITH_REPLACEMENT), parameters.getInt(Params.RESAMPLING_ENSEMBLE), parameters.getBoolean(Params.ADD_ORIGINAL_DATASET));
//            search.setKnowledge(this.knowledge);

            if (score == null) {
                System.out.println();
            }

            search.setParameters(parameters);
            search.setVerbose(parameters.getBoolean(Params.VERBOSE));
            search.setScoreWrapper(score);
            return search.search();
        }
    }

    @Override
    public Graph getComparisonGraph(Graph graph) {
        return new EdgeListGraph(graph);
    }

    @Override
    public String getDescription() {
        return "IMaGES";
    }

    @Override
    public DataType getDataType() {
        return DataType.All;
    }

    @Override
    public List<String> getParameters() {
        List<String> parameters = new LinkedList<>();
        parameters.addAll(new SemBicScore().getParameters());

        parameters.addAll((new FGES()).getParameters());
        parameters.add(Params.RANDOM_SELECTION_SIZE);
        parameters.add(Params.TIME_LAG);
        parameters.add(Params.IMAGES_META_ALG);

        parameters.add(Params.VERBOSE);

        return parameters;
    }

    @Override
    public Knowledge getKnowledge() {
        return this.knowledge;
    }

    @Override
    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = new Knowledge((Knowledge) knowledge);
    }

    @Override
    public ScoreWrapper getScoreWrapper() {
        return this.score;
    }

    @Override
    public void setScoreWrapper(ScoreWrapper score) {
        this.score = score;
    }
}
