package edu.cmu.tetrad.algcomparison.algorithm.multi;

import edu.cmu.tetrad.algcomparison.algorithm.MultiDataSetAlgorithm;
import edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper;
import edu.cmu.tetrad.algcomparison.score.ScoreWrapper;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.algcomparison.utils.TakesIndependenceWrapper;
import edu.cmu.tetrad.annotation.Bootstrapping;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.Fask;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;

import java.io.Serial;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Wraps the IMaGES algorithm for continuous variables.
 * <p>
 * Requires that the parameter 'randomSelectionSize' be set to indicate how many datasets should be taken at a time
 * (randomly). This cannot given multiple values.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
//@edu.cmu.tetrad.annotation.Algorithm(
//        name = "FASK Concatenated",
//        command = "fask-concatenated",
//        algoType = AlgType.forbid_latent_common_causes
//)
@Bootstrapping
public class FaskConcatenated implements MultiDataSetAlgorithm, HasKnowledge, TakesIndependenceWrapper {

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
     * The knowledge.
     */
    private Knowledge knowledge = new Knowledge();

    /**
     * <p>Constructor for FaskConcatenated.</p>
     */
    public FaskConcatenated() {

    }

    /**
     * <p>Constructor for FaskConcatenated.</p>
     *
     * @param score a {@link edu.cmu.tetrad.algcomparison.score.ScoreWrapper} object
     * @param test  a {@link edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper} object
     */
    public FaskConcatenated(ScoreWrapper score, IndependenceWrapper test) {
        this.score = score;
        this.test = test;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Graph search(List<DataModel> dataSets, Parameters parameters) {
        List<DataSet> centered = new ArrayList<>();

        for (DataModel dataSet : dataSets) {
            centered.add(DataTransforms.standardizeData((DataSet) dataSet));
        }

        DataSet dataSet = DataTransforms.concatenate(centered);

        dataSet.setNumberFormat(new DecimalFormat("0.000000000000000000"));

        Fask search = new Fask(dataSet,
                this.score.getScore(dataSet, parameters),
                this.test.getTest(dataSet, parameters));
        search.setDepth(parameters.getInt(Params.DEPTH));
        search.setSkewEdgeThreshold(parameters.getDouble(Params.SKEW_EDGE_THRESHOLD));
        search.setKnowledge(this.knowledge);

        return search.search();
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
        return search(Collections.singletonList(SimpleDataLoader.getContinuousDataSet(dataSet)), parameters);
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
        return "FASK Concatenated";
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
        parameters.add(Params.DEPTH);
        parameters.add(Params.TWO_CYCLE_ALPHA);
        parameters.add(Params.SKEW_EDGE_THRESHOLD);

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
}
