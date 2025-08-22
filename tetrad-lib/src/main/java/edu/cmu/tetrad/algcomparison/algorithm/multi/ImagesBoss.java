package edu.cmu.tetrad.algcomparison.algorithm.multi;

import edu.cmu.tetrad.algcomparison.algorithm.LatentStructureAlgorithm;
import edu.cmu.tetrad.algcomparison.algorithm.MultiDataSetAlgorithm;
import edu.cmu.tetrad.algcomparison.algorithm.oracle.cpdag.Fges;
import edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper;
import edu.cmu.tetrad.algcomparison.score.ScoreWrapper;
import edu.cmu.tetrad.algcomparison.score.SemBicScore;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.algcomparison.utils.TakesScoreWrapper;
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

import java.io.Serial;
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
 * @version $Id: $Id
 */
@edu.cmu.tetrad.annotation.Algorithm(
        name = "IMaGES-BOSS",
        command = "images-boss",
        algoType = AlgType.forbid_latent_common_causes,
        dataType = DataType.All
)
@Bootstrapping
public class ImagesBoss implements MultiDataSetAlgorithm, HasKnowledge, TakesScoreWrapper, LatentStructureAlgorithm {

    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The knowledge.
     */
    private Knowledge knowledge = new Knowledge();

    /**
     * The score to use.
     */
    private ScoreWrapper score = new SemBicScore();

    /**
     * <p>Constructor for ImagesBoss.</p>
     *
     * @param score a {@link edu.cmu.tetrad.algcomparison.score.ScoreWrapper} object
     */
    public ImagesBoss(ScoreWrapper score) {
        this.score = score;
    }

    /**
     * <p>Constructor for ImagesBoss.</p>
     */
    public ImagesBoss() {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Graph search(List<DataModel> dataSets, Parameters parameters) {
        int meta = parameters.getInt(Params.IMAGES_META_ALG);

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
            search.setKnowledge(this.knowledge);
            try {
                return search.search();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        } else if (meta == 2) {
            PermutationSearch search = new PermutationSearch(new Boss(score));
            search.setKnowledge(this.knowledge);
            try {
                return search.search();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        } else {
            throw new IllegalArgumentException("Unrecognized meta option: " + meta);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Graph search(DataModel dataSet, Parameters parameters) {
        return search(Collections.singletonList(SimpleDataLoader.getMixedDataSet(dataSet)), parameters);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Graph getComparisonGraph(Graph graph) {
        return new EdgeListGraph(graph);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription() {
        return "IMaGES";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DataType getDataType() {
        return DataType.All;
    }

    /**
     * {@inheritDoc}
     */
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

    /**
     * {@inheritDoc}
     */
    @Override
    public Knowledge getKnowledge() {
        return this.knowledge;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = new Knowledge(knowledge);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ScoreWrapper getScoreWrapper() {
        return this.score;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setScoreWrapper(ScoreWrapper score) {
        this.score = score;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setIndTestWrapper(IndependenceWrapper test) {
        // Not used.
    }
}
