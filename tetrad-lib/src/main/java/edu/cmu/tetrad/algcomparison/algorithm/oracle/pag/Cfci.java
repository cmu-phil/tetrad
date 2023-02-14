package edu.cmu.tetrad.algcomparison.algorithm.oracle.pag;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.annotation.Bootstrapping;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.pitt.dbmi.algo.resampling.GeneralResamplingTest;

import java.util.LinkedList;
import java.util.List;

import static edu.cmu.tetrad.search.SearchGraphUtils.dagToPag;

/**
 * Conserative FCI.
 *
 * @author jdramsey
 */
@Bootstrapping
public class Cfci implements Algorithm, HasKnowledge {

    static final long serialVersionUID = 23L;
    private final IndependenceWrapper test;
    private Knowledge knowledge = new Knowledge();

    public Cfci(IndependenceWrapper test) {
        this.test = test;
    }

    @Override
    public Graph search(DataModel dataSet, Parameters parameters) {
        if (parameters.getInt(Params.NUMBER_RESAMPLING) < 1) {
            edu.cmu.tetrad.search.Cfci search = new edu.cmu.tetrad.search.Cfci(this.test.getTest(dataSet, parameters));
            search.setKnowledge(this.knowledge);
            search.setCompleteRuleSetUsed(parameters.getBoolean(Params.COMPLETE_RULE_SET_USED));
            search.setDepth(parameters.getInt(Params.DEPTH));
            search.setVerbose(parameters.getBoolean(Params.VERBOSE));
            search.setDoDiscriminatingPathRule(parameters.getBoolean(Params.DO_DISCRIMINATING_PATH_RULE));
            return search.search();
        } else {
            Cfci algorithm = new Cfci(this.test);

            DataSet data = (DataSet) dataSet;
            GeneralResamplingTest search = new GeneralResamplingTest(data, algorithm, parameters.getInt(Params.NUMBER_RESAMPLING), parameters.getDouble(Params.PERCENT_RESAMPLE_SIZE), parameters.getBoolean(Params.RESAMPLING_WITH_REPLACEMENT), parameters.getInt(Params.RESAMPLING_ENSEMBLE), parameters.getBoolean(Params.ADD_ORIGINAL_DATASET));
            search.setKnowledge(this.knowledge);

            search.setParameters(parameters);
            search.setVerbose(parameters.getBoolean(Params.VERBOSE));
            return search.search();
        }
    }

    @Override
    public Graph getComparisonGraph(Graph graph) {
        return dagToPag(new EdgeListGraph(graph));
    }

    @Override
    public String getDescription() {
        return "CFCI (Conservative Fast Causal Inference), using " + this.test.getDescription();
    }

    @Override
    public DataType getDataType() {
        return this.test.getDataType();
    }

    @Override
    public List<String> getParameters() {
        List<String> parameters = new LinkedList<>();
        if (this.test != null) {
            parameters.addAll(this.test.getParameters());
        }
        parameters.add(Params.DEPTH);
        parameters.add(Params.COMPLETE_RULE_SET_USED);
        parameters.add(Params.DO_DISCRIMINATING_PATH_RULE);

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
}
