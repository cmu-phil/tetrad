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
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.pitt.dbmi.algo.resampling.GeneralResamplingTest;
import edu.pitt.dbmi.algo.resampling.ResamplingEdgeEnsemble;

import java.util.ArrayList;
import java.util.List;

import static edu.cmu.tetrad.util.Params.*;

/**
 * Wraps the IMaGES algorithm for continuous variables.
 * </p>
 * Requires that the parameter 'randomSelectionSize' be set to indicate how many
 * datasets should be taken at a time (randomly). This cannot given multiple values.
 *
 * @author jdramsey
 */
@Bootstrapping
@edu.cmu.tetrad.annotation.Algorithm(
        name = "FASK",
        command = "fask",
        algoType = AlgType.forbid_latent_common_causes,
        dataType = DataType.Continuous
)
public class Fask implements Algorithm, HasKnowledge, UsesScoreWrapper, TakesIndependenceWrapper, TakesExternalGraph {
    static final long serialVersionUID = 23L;
    private IndependenceWrapper test;
    private ScoreWrapper score;
    private Graph externalGraph;
    private IKnowledge knowledge = new Knowledge2();
    private Algorithm algorithm;

    // Don't delete.
    public Fask() {

    }

    public Fask(IndependenceWrapper test) {
        this.test = test;
    }

    private Graph getGraph(edu.cmu.tetrad.search.Fask search) {
        return search.search();
    }

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
                search.setExternalGraph(this.externalGraph);
            } else if (this.algorithm != null) {
                search.setExternalGraph(this.algorithm.search(dataSet, parameters));
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
            Fask fask = new Fask(this.test);

            DataSet data = (DataSet) dataSet;
            GeneralResamplingTest search = new GeneralResamplingTest(data, fask, parameters.getInt(NUMBER_RESAMPLING));
            search.setKnowledge(this.knowledge);

            search.setPercentResampleSize(parameters.getDouble(PERCENT_RESAMPLE_SIZE));
            search.setResamplingWithReplacement(parameters.getBoolean(RESAMPLING_WITH_REPLACEMENT));

            ResamplingEdgeEnsemble edgeEnsemble = ResamplingEdgeEnsemble.Highest;
            switch (parameters.getInt(RESAMPLING_ENSEMBLE, 1)) {
                case 0:
                    edgeEnsemble = ResamplingEdgeEnsemble.Preserved;
                    break;
                case 1:
                    break;
                case 2:
                    edgeEnsemble = ResamplingEdgeEnsemble.Majority;
            }
            search.setEdgeEnsemble(edgeEnsemble);
            search.setAddOriginalDataset(parameters.getBoolean(ADD_ORIGINAL_DATASET));

            search.setParameters(parameters);
            search.setVerbose(parameters.getBoolean(VERBOSE));
            return search.search();
        }
    }

    @Override
    public Graph getComparisonGraph(Graph graph) {
        return new EdgeListGraph(graph);
    }

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

    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }

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

    @Override
    public IKnowledge getKnowledge() {
        return this.knowledge;
    }

    @Override
    public void setKnowledge(IKnowledge knowledge) {
        this.knowledge = knowledge;
    }

    @Override
    public void setIndependenceWrapper(IndependenceWrapper independenceWrapper) {
        this.test = independenceWrapper;
    }

    @Override
    public IndependenceWrapper getIndependenceWrapper() {
        return this.test;
    }

    @Override
    public Graph getExternalGraph() {
        return this.externalGraph;
    }

    @Override
    public void setExternalGraph(Graph externalGraph) {
        this.externalGraph = externalGraph;
    }

    @Override
    public void setExternalGraph(Algorithm algorithm) {
        this.algorithm = algorithm;
    }


    @Override
    public void setScoreWrapper(ScoreWrapper score) {
        this.score = score;
    }

    @Override
    public ScoreWrapper getScoreWrapper() {
        return this.score;
    }
}