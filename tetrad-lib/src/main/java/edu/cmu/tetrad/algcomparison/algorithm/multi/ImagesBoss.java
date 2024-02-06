package edu.cmu.tetrad.algcomparison.algorithm.multi;

import edu.cmu.tetrad.algcomparison.algorithm.MultiDataSetAlgorithm;
import edu.cmu.tetrad.algcomparison.algorithm.oracle.cpdag.Fges;
import edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper;
import edu.cmu.tetrad.algcomparison.score.ScoreWrapper;
import edu.cmu.tetrad.algcomparison.score.SemBicScore;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.algcomparison.utils.UsesScoreWrapper;
import edu.cmu.tetrad.annotation.AlgType;
import edu.cmu.tetrad.annotation.Bootstrapping;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.Boss;
import edu.cmu.tetrad.search.PermutationSearch;
import edu.cmu.tetrad.search.score.ImagesScore;
import edu.cmu.tetrad.search.score.Score;
import edu.cmu.tetrad.search.utils.TsUtils;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.pitt.dbmi.algo.resampling.GeneralResamplingTest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * Wraps the IMaGES algorithm for continuous variables. This version uses the BOSS algorithm in place of FGES.
 * <p>
 * Requires that the parameter 'randomSelectionSize' be set to indicate how many datasets should be taken at a time
 * (randomly). This cannot be given multiple values.
 *
 * @author josephramsey
 */
@edu.cmu.tetrad.annotation.Algorithm(
        name = "IMaGES-BOSS",
        command = "images-boss",
        algoType = AlgType.forbid_latent_common_causes,
        dataType = DataType.All
)
@Bootstrapping
public class ImagesBoss implements MultiDataSetAlgorithm, HasKnowledge, UsesScoreWrapper {

    private static final long serialVersionUID = 23L;
    private Knowledge knowledge = new Knowledge();

    private ScoreWrapper score = new SemBicScore();

    public ImagesBoss(ScoreWrapper score) {
        this.score = score;
    }

    public ImagesBoss() {
    }

    @Override
    public Graph search(List<DataModel> dataSets, Parameters parameters) {
        int meta = parameters.getInt(Params.IMAGES_META_ALG);

        if (parameters.getInt(Params.NUMBER_RESAMPLING) < 1) {
            List<DataModel> _dataSets = new ArrayList<>();

            if (parameters.getInt(Params.TIME_LAG) > 0) {
                for (DataModel dataSet : dataSets) {
                    DataSet timeSeries = TsUtils.createLagData((DataSet) dataSet, parameters.getInt(Params.TIME_LAG));
                    if (dataSet.getName() != null) {
                        timeSeries.setName(dataSet.getName());
                    }
                    _dataSets.add(timeSeries);
                }

                dataSets = _dataSets;
            }

            List<Score> scores = new ArrayList<>();

            for (DataModel dataModel : dataSets) {
                Score s = score.getScore(dataModel, parameters);
                scores.add(s);
            }

            ImagesScore score = new ImagesScore(scores);

            if (meta == 1) {
                PermutationSearch search = new PermutationSearch(new Boss(score));
                search.setSeed(parameters.getLong(Params.SEED));
//                edu.cmu.tetrad.search.Fges search = new edu.cmu.tetrad.search.Fges(score);
                search.setKnowledge(this.knowledge);
//                search.setVerbose(parameters.getBoolean(Params.VERBOSE));
                return search.search();
            } else if (meta == 2) {
                PermutationSearch search = new PermutationSearch(new Boss(score));
                search.setKnowledge(this.knowledge);
                return search.search();
            } else {
                throw new IllegalArgumentException("Unrecognized meta option: " + meta);
            }
        } else {
            ImagesBoss imagesSemBic = new ImagesBoss();

            List<DataSet> dataSets2 = new ArrayList<>();

            for (DataModel dataModel : dataSets) {
                dataSets2.add((DataSet) dataModel);
            }

            List<DataSet> _dataSets = new ArrayList<>();

            if (parameters.getInt(Params.TIME_LAG) > 0) {
                for (DataSet dataSet : dataSets2) {
                    DataSet timeSeries = TsUtils.createLagData(dataSet, parameters.getInt(Params.TIME_LAG));
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
            search.setKnowledge(this.knowledge);
            search.setScoreWrapper(score);
            return search.search();
        }
    }

    @Override
    public Graph search(DataModel dataSet, Parameters parameters) {
        if (parameters.getInt(Params.NUMBER_RESAMPLING) < 1) {
            return search(Collections.singletonList(SimpleDataLoader.getMixedDataSet(dataSet)), parameters);
        } else {
            ImagesBoss images = new ImagesBoss();

            List<DataSet> dataSets = Collections.singletonList(SimpleDataLoader.getMixedDataSet(dataSet));
            GeneralResamplingTest search = new GeneralResamplingTest(dataSets,
                    images,
                    parameters.getInt(Params.NUMBER_RESAMPLING),
                    parameters.getDouble(Params.PERCENT_RESAMPLE_SIZE),
                    parameters.getBoolean(Params.RESAMPLING_WITH_REPLACEMENT),
                    parameters.getInt(Params.RESAMPLING_ENSEMBLE),
                    parameters.getBoolean(Params.ADD_ORIGINAL_DATASET));

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

        parameters.addAll((new Fges()).getParameters());
        parameters.add(Params.RANDOM_SELECTION_SIZE);
        parameters.add(Params.TIME_LAG);
        parameters.add(Params.IMAGES_META_ALG);
        parameters.add(Params.SEED);
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

    @Override
    public void setIndTestWrapper(IndependenceWrapper test) {
        // Not used.
    }
}
