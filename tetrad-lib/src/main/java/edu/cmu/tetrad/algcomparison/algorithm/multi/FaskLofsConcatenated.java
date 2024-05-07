package edu.cmu.tetrad.algcomparison.algorithm.multi;

import edu.cmu.tetrad.algcomparison.algorithm.MultiDataSetAlgorithm;
import edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper;
import edu.cmu.tetrad.algcomparison.score.ScoreWrapper;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.annotation.Bootstrapping;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.Lofs;
import edu.cmu.tetrad.search.work_in_progress.FasLofs;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;

import java.io.Serial;
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
@Bootstrapping
public class FaskLofsConcatenated implements MultiDataSetAlgorithm, HasKnowledge {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The rule to use.
     */
    private final Lofs.Rule rule;

    /**
     * The knowledge.
     */
    private Knowledge knowledge = new Knowledge();

    /**
     * <p>Constructor for FaskLofsConcatenated.</p>
     *
     * @param rule a {@link edu.cmu.tetrad.search.Lofs.Rule} object
     */
    public FaskLofsConcatenated(Lofs.Rule rule) {
        this.rule = rule;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Graph search(List<DataModel> dataModels, Parameters parameters) {
        List<DataSet> dataSets = new ArrayList<>();

        for (DataModel dataModel : dataModels) {
            dataSets.add((DataSet) dataModel);
        }

        DataSet dataSet = DataTransforms.concatenate(dataSets);

        FasLofs search = new FasLofs(dataSet, this.rule);
        search.setDepth(parameters.getInt(Params.DEPTH));
        search.setPenaltyDiscount(parameters.getDouble(Params.PENALTY_DISCOUNT));
        search.setKnowledge(this.knowledge);
        return getGraph(search);
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

    private Graph getGraph(FasLofs search) {
        return search.search();
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
        return "FAS followed by " + this.rule;
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
        parameters.add(Params.PENALTY_DISCOUNT);

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
}
