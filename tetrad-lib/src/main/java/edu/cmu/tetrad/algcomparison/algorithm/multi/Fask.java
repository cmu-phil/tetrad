package edu.cmu.tetrad.algcomparison.algorithm.multi;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper;
import edu.cmu.tetrad.algcomparison.score.ScoreWrapper;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.algcomparison.utils.TakesExternalGraph;
import edu.cmu.tetrad.algcomparison.utils.TakesIndependenceWrapper;
import edu.cmu.tetrad.algcomparison.utils.UsesScoreWrapper;
import edu.cmu.tetrad.annotation.AlgType;
import edu.cmu.tetrad.annotation.Bootstrapping;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.util.Parameters;
import edu.pitt.dbmi.algo.resampling.GeneralResamplingTest;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

import static edu.cmu.tetrad.util.Params.*;

/**
 * Wraps the IMaGES algorithm for continuous variables.
 * <p>
 * Requires that the parameter 'randomSelectionSize' be set to indicate how many datasets should be taken at a time
 * (randomly). This cannot given multiple values.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
@Bootstrapping
@edu.cmu.tetrad.annotation.Algorithm(
        name = "FASK",
        command = "fask",
        algoType = AlgType.forbid_latent_common_causes,
        dataType = DataType.Continuous
)
public class Fask implements Algorithm, HasKnowledge, UsesScoreWrapper, TakesIndependenceWrapper, TakesExternalGraph {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The independence test to use.
     */
    private IndependenceWrapper test;

    /**
     * The score to use.
     */
    private ScoreWrapper score;

    /**
     * The external graph.
     */
    private Graph externalGraph;

    /**
     * The knowledge.
     */
    private Knowledge knowledge = new Knowledge();

    /**
     * The algorithm.
     */
    private Algorithm algorithm;

    // Don't delete.

    /**
     * <p>Constructor for Fask.</p>
     */
    public Fask() {

    }

    /**
     * <p>Constructor for Fask.</p>
     *
     * @param test  a {@link edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper} object
     * @param score a {@link edu.cmu.tetrad.algcomparison.score.ScoreWrapper} object
     */
    public Fask(IndependenceWrapper test, ScoreWrapper score) {
        this.test = test;
        this.score = score;
    }

    private Graph getGraph(edu.cmu.tetrad.search.Fask search) {
        return search.search();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Graph search(DataModel dataSet, Parameters parameters) {
        DataSet _data = (DataSet) dataSet;

        for (int j = 0; j < _data.getNumColumns(); j++) {
            for (int i = 0; i < _data.getNumRows(); i++) {
                if (Double.isNaN(_data.getDouble(i, j))) {
                    throw new IllegalArgumentException("Please remove or impute missing values.");
                }
            }
        }

        if (parameters.getInt(NUMBER_RESAMPLING) < 1) {
            edu.cmu.tetrad.search.Fask search;

            search = new edu.cmu.tetrad.search.Fask((DataSet) dataSet, this.score.getScore(dataSet, parameters),
                    this.test.getTest(dataSet, parameters));

            search.setDepth(parameters.getInt(DEPTH));
            search.setSkewEdgeThreshold(parameters.getDouble(SKEW_EDGE_THRESHOLD));
            search.setOrientationAlpha(parameters.getDouble(ORIENTATION_ALPHA));
            search.setTwoCycleScreeningCutoff(parameters.getDouble(TWO_CYCLE_SCREENING_THRESHOLD));
            search.setDelta(parameters.getDouble(FASK_DELTA));
            search.setEmpirical(!parameters.getBoolean(FASK_NONEMPIRICAL));

            if (this.externalGraph != null) {
                this.externalGraph = algorithm.search(dataSet, parameters);
            }

            if (this.externalGraph != null) {
                search.setExternalGraph(this.externalGraph);
            }

            int lrRule = parameters.getInt(FASK_LEFT_RIGHT_RULE);

            if (lrRule == 1) {
                search.setLeftRight(edu.cmu.tetrad.search.Fask.LeftRight.FASK1);
            } else if (lrRule == 2) {
                search.setLeftRight(edu.cmu.tetrad.search.Fask.LeftRight.FASK2);
            } else if (lrRule == 3) {
                search.setLeftRight(edu.cmu.tetrad.search.Fask.LeftRight.RSKEW);
            } else if (lrRule == 4) {
                search.setLeftRight(edu.cmu.tetrad.search.Fask.LeftRight.SKEW);
            } else if (lrRule == 5) {
                search.setLeftRight(edu.cmu.tetrad.search.Fask.LeftRight.TANH);
            } else {
                throw new IllegalStateException("Unconfigured left right rule index: " + lrRule);
            }

            int adjacencyMethod = parameters.getInt(FASK_ADJACENCY_METHOD);

            if (adjacencyMethod == 1) {
                search.setAdjacencyMethod(edu.cmu.tetrad.search.Fask.AdjacencyMethod.FAS_STABLE);
            } else if (adjacencyMethod == 2) {
                search.setAdjacencyMethod(edu.cmu.tetrad.search.Fask.AdjacencyMethod.FGES);
            } else if (adjacencyMethod == 3) {
                search.setAdjacencyMethod(edu.cmu.tetrad.search.Fask.AdjacencyMethod.EXTERNAL_GRAPH);
            } else if (adjacencyMethod == 4) {
                search.setAdjacencyMethod(edu.cmu.tetrad.search.Fask.AdjacencyMethod.NONE);
            } else {
                throw new IllegalStateException("Unconfigured left right rule index: " + lrRule);
            }

            search.setKnowledge(this.knowledge);
            return getGraph(search);
        } else {
            Fask fask = new Fask(this.test, this.score);

            DataSet data = (DataSet) dataSet;
            GeneralResamplingTest search = new GeneralResamplingTest(data,
                    fask,
                    knowledge, parameters);

            search.setVerbose(parameters.getBoolean(VERBOSE));
            return search.search();
        }
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
        if (this.test != null) {
            return "FASK using " + this.test.getDescription();
        } else if (this.algorithm != null) {
            return "FASK using " + this.algorithm.getDescription();
        } else {
            throw new IllegalStateException("Need to initialize with either a test or an algorithm.");
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();

        if (this.algorithm != null) {
            parameters.addAll(this.algorithm.getParameters());
        }

        parameters.add(DEPTH);
        parameters.add(SKEW_EDGE_THRESHOLD);
        parameters.add(TWO_CYCLE_SCREENING_THRESHOLD);
        parameters.add(ORIENTATION_ALPHA);
        parameters.add(FASK_DELTA);
        parameters.add(FASK_LEFT_RIGHT_RULE);
        parameters.add(FASK_ADJACENCY_METHOD);
        parameters.add(FASK_NONEMPIRICAL);
        parameters.add(VERBOSE);
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
    public IndependenceWrapper getIndependenceWrapper() {
        return this.test;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setIndependenceWrapper(IndependenceWrapper independenceWrapper) {
        this.test = independenceWrapper;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setExternalGraph(Algorithm algorithm) {
        this.algorithm = algorithm;
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
}
