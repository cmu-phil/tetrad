package edu.cmu.tetrad.algcomparison.algorithm.multi;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.algorithm.MultiDataSetAlgorithm;
import edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper;
import edu.cmu.tetrad.algcomparison.score.ScoreWrapper;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.annotation.Bootstrapping;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphTransforms;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.pitt.dbmi.algo.resampling.GeneralResamplingTest;

import java.io.PrintStream;
import java.io.Serial;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Requires that the parameter 'randomSelectionSize' be set to indicate how many datasets should be taken at a time
 * (randomly). This cannot given multiple values.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
@Bootstrapping
public class FgesConcatenated implements MultiDataSetAlgorithm, HasKnowledge {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The score to use.
     */
    private final ScoreWrapper score;

    /**
     * The knowledge.
     */
    private Knowledge knowledge = new Knowledge();

    /**
     * The external graph.
     */
    private Algorithm externalGraph;

    /**
     * True if the result should be compared to the true graph, false if to the CPDAG of the true graph.
     */
    private boolean compareToTrue;

    /**
     * <p>Constructor for FgesConcatenated.</p>
     *
     * @param score a {@link edu.cmu.tetrad.algcomparison.score.ScoreWrapper} object
     */
    public FgesConcatenated(ScoreWrapper score) {
        this.score = score;
    }

    /**
     * <p>Constructor for FgesConcatenated.</p>
     *
     * @param score         a {@link edu.cmu.tetrad.algcomparison.score.ScoreWrapper} object
     * @param compareToTrue a boolean
     */
    public FgesConcatenated(ScoreWrapper score, boolean compareToTrue) {
        this.score = score;
        this.compareToTrue = compareToTrue;
    }

    /**
     * <p>Constructor for FgesConcatenated.</p>
     *
     * @param score         a {@link edu.cmu.tetrad.algcomparison.score.ScoreWrapper} object
     * @param externalGraph a {@link edu.cmu.tetrad.algcomparison.algorithm.Algorithm} object
     */
    public FgesConcatenated(ScoreWrapper score, Algorithm externalGraph) {
        this.score = score;
        this.externalGraph = externalGraph;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Graph search(List<DataModel> dataModels, Parameters parameters) {
        if (parameters.getInt(Params.NUMBER_RESAMPLING) < 1) {
            List<DataSet> dataSets = new ArrayList<>();

            for (DataModel dataModel : dataModels) {
                dataSets.add((DataSet) dataModel);
            }

            DataSet dataSet = DataTransforms.concatenate(dataSets);

            Graph initial = null;
            if (this.externalGraph != null) {

                initial = this.externalGraph.search(dataSet, parameters);
            }

            edu.cmu.tetrad.search.Fges search = new edu.cmu.tetrad.search.Fges(this.score.getScore(dataSet, parameters));
            search.setKnowledge(this.knowledge);
            search.setVerbose(parameters.getBoolean(Params.VERBOSE));
            search.setMaxDegree(parameters.getInt(Params.MAX_DEGREE));

            Object obj = parameters.get("printStedu.cmream");
            if (obj instanceof PrintStream) {
                search.setOut((PrintStream) obj);
            }

            if (initial != null) {
                search.setBoundGraph(initial);
            }

            return search.search();
        } else {
            FgesConcatenated fgesConcatenated = new FgesConcatenated(this.score, this.externalGraph);
            fgesConcatenated.setCompareToTrue(this.compareToTrue);

            List<DataSet> datasets = new ArrayList<>();

            for (DataModel dataModel : dataModels) {
                datasets.add((DataSet) dataModel);
            }
            GeneralResamplingTest search = new GeneralResamplingTest(datasets,
                    fgesConcatenated,
                    parameters.getInt(Params.NUMBER_RESAMPLING),
                    parameters.getDouble(Params.PERCENT_RESAMPLE_SIZE),
                    parameters.getBoolean(Params.RESAMPLING_WITH_REPLACEMENT), parameters.getInt(Params.RESAMPLING_ENSEMBLE), parameters.getBoolean(Params.ADD_ORIGINAL_DATASET));
            search.setKnowledge(this.knowledge);
            search.setScoreWrapper(score);

            search.setParameters(parameters);
            search.setVerbose(parameters.getBoolean(Params.VERBOSE));
            return search.search();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setScoreWrapper(ScoreWrapper score) {
        // Not used.
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setIndTestWrapper(IndependenceWrapper test) {
        // Not used.
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Graph search(DataModel dataSet, Parameters parameters) {
        if (parameters.getInt(Params.NUMBER_RESAMPLING) < 1) {
            return search(Collections.singletonList(SimpleDataLoader.getContinuousDataSet(dataSet)), parameters);
        } else {
            FgesConcatenated fgesConcatenated = new FgesConcatenated(this.score, this.externalGraph);
            fgesConcatenated.setCompareToTrue(this.compareToTrue);

            List<DataSet> dataSets = Collections.singletonList(SimpleDataLoader.getContinuousDataSet(dataSet));
            GeneralResamplingTest search = new GeneralResamplingTest(dataSets,
                    fgesConcatenated,
                    parameters.getInt(Params.NUMBER_RESAMPLING),
                    parameters.getDouble(Params.PERCENT_RESAMPLE_SIZE),
                    parameters.getBoolean(Params.RESAMPLING_WITH_REPLACEMENT), parameters.getInt(Params.RESAMPLING_ENSEMBLE), parameters.getBoolean(Params.ADD_ORIGINAL_DATASET));
            search.setKnowledge(this.knowledge);
            search.setScoreWrapper(score);

            search.setParameters(parameters);
            search.setVerbose(parameters.getBoolean(Params.VERBOSE));
            return search.search();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Graph getComparisonGraph(Graph graph) {
        if (this.compareToTrue) {
            return new EdgeListGraph(graph);
        } else {
            Graph dag = new EdgeListGraph(graph);
            return GraphTransforms.cpdagForDag(dag);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription() {
        return "FGES (Fast Greedy Equivalence Search) on concatenated data using " + this.score.getDescription();
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
        parameters.add(Params.FAITHFULNESS_ASSUMED);
        parameters.add(Params.MAX_DEGREE);

        parameters.add(Params.NUM_RUNS);
        parameters.add(Params.RANDOM_SELECTION_SIZE);

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
     * <p>Setter for the field <code>compareToTrue</code>.</p>
     *
     * @param compareToTrue true if the result should be compared to the true graph, false if to the CPDAG of the true
     *                      graph.
     */
    public void setCompareToTrue(boolean compareToTrue) {
        this.compareToTrue = compareToTrue;
    }
}
