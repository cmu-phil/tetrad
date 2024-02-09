package edu.cmu.tetrad.algcomparison.algorithm.multi;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.Lofs;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.pitt.dbmi.algo.resampling.GeneralResamplingTest;

import java.util.ArrayList;
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
public class FasLofs implements Algorithm, HasKnowledge {
    private static final long serialVersionUID = 23L;
    private final Lofs.Rule rule;
    private Knowledge knowledge = new Knowledge();

    /**
     * <p>Constructor for FasLofs.</p>
     *
     * @param rule a {@link edu.cmu.tetrad.search.Lofs.Rule} object
     */
    public FasLofs(Lofs.Rule rule) {
        this.rule = rule;
    }

    private Graph getGraph(edu.cmu.tetrad.search.work_in_progress.FasLofs search) {
        return search.search();
    }

    /** {@inheritDoc} */
    @Override
    public Graph search(DataModel dataSet, Parameters parameters) {
        if (parameters.getInt(Params.NUMBER_RESAMPLING) < 1) {
            edu.cmu.tetrad.search.work_in_progress.FasLofs search = new edu.cmu.tetrad.search.work_in_progress.FasLofs((DataSet) dataSet, this.rule);
            search.setDepth(parameters.getInt(Params.DEPTH));
            search.setPenaltyDiscount(parameters.getDouble(Params.PENALTY_DISCOUNT));
            search.setKnowledge(this.knowledge);
            return getGraph(search);
        } else {
            FasLofs fasLofs = new FasLofs(this.rule);
            //fasLofs.setKnowledge(knowledge);

            DataSet data = (DataSet) dataSet;
            GeneralResamplingTest search = new GeneralResamplingTest(data, fasLofs, parameters.getInt(Params.NUMBER_RESAMPLING), parameters.getDouble(Params.PERCENT_RESAMPLE_SIZE), parameters.getBoolean(Params.RESAMPLING_WITH_REPLACEMENT), parameters.getInt(Params.RESAMPLING_ENSEMBLE), parameters.getBoolean(Params.ADD_ORIGINAL_DATASET));
            search.setKnowledge(this.knowledge);

            search.setParameters(parameters);
            search.setVerbose(parameters.getBoolean(Params.VERBOSE));
            return search.search();
        }
    }

    /** {@inheritDoc} */
    @Override
    public Graph getComparisonGraph(Graph graph) {
        return new EdgeListGraph(graph);
    }

    /** {@inheritDoc} */
    @Override
    public String getDescription() {
        return "FAS followed by " + this.rule;
    }

    /** {@inheritDoc} */
    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }

    /** {@inheritDoc} */
    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();
        parameters.add(Params.DEPTH);
        parameters.add(Params.PENALTY_DISCOUNT);

        parameters.add(Params.VERBOSE);

        return parameters;
    }

    /** {@inheritDoc} */
    @Override
    public Knowledge getKnowledge() {
        return this.knowledge;
    }

    /** {@inheritDoc} */
    @Override
    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = new Knowledge((Knowledge) knowledge);
    }
}
