package edu.cmu.tetrad.algcomparison.algorithm.oracle.pag;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.annotation.Bootstrapping;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.DagToPag2;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.pitt.dbmi.algo.resampling.GeneralResamplingTest;
import edu.pitt.dbmi.algo.resampling.ResamplingEdgeEnsemble;

import java.util.LinkedList;
import java.util.List;

/**
 * Conserative FCI.
 *
 * @author jdramsey
 */
@Bootstrapping
public class Cfci implements Algorithm, HasKnowledge {

    static final long serialVersionUID = 23L;
    private final IndependenceWrapper test;
    private IKnowledge knowledge = new Knowledge2();

    public Cfci(final IndependenceWrapper test) {
        this.test = test;
    }

    @Override
    public Graph search(final DataModel dataSet, final Parameters parameters) {
        if (parameters.getInt(Params.NUMBER_RESAMPLING) < 1) {
            final edu.cmu.tetrad.search.Cfci search = new edu.cmu.tetrad.search.Cfci(this.test.getTest(dataSet, parameters));
            search.setKnowledge(this.knowledge);
            search.setCompleteRuleSetUsed(parameters.getBoolean(Params.COMPLETE_RULE_SET_USED));
            search.setDepth(parameters.getInt(Params.DEPTH));
            search.setVerbose(parameters.getBoolean(Params.VERBOSE));
            return search.search();
        } else {
            final Cfci algorithm = new Cfci(this.test);

            final DataSet data = (DataSet) dataSet;
            final GeneralResamplingTest search = new GeneralResamplingTest(data, algorithm, parameters.getInt(Params.NUMBER_RESAMPLING));
            search.setKnowledge(this.knowledge);

            search.setPercentResampleSize(parameters.getDouble(Params.PERCENT_RESAMPLE_SIZE));
            search.setResamplingWithReplacement(parameters.getBoolean(Params.RESAMPLING_WITH_REPLACEMENT));

            ResamplingEdgeEnsemble edgeEnsemble = ResamplingEdgeEnsemble.Highest;
            switch (parameters.getInt(Params.RESAMPLING_ENSEMBLE, 1)) {
                case 0:
                    edgeEnsemble = ResamplingEdgeEnsemble.Preserved;
                    break;
                case 1:
                    edgeEnsemble = ResamplingEdgeEnsemble.Highest;
                    break;
                case 2:
                    edgeEnsemble = ResamplingEdgeEnsemble.Majority;
            }
            search.setEdgeEnsemble(edgeEnsemble);
            search.setAddOriginalDataset(parameters.getBoolean(Params.ADD_ORIGINAL_DATASET));

            search.setParameters(parameters);
            search.setVerbose(parameters.getBoolean(Params.VERBOSE));
            return search.search();
        }
    }

    @Override
    public Graph getComparisonGraph(final Graph graph) {
        return new DagToPag2(new EdgeListGraph(graph)).convert();
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
        final List<String> parameters = new LinkedList<>();
        if (this.test != null) {
            parameters.addAll(this.test.getParameters());
        }
        parameters.add(Params.DEPTH);
        parameters.add(Params.COMPLETE_RULE_SET_USED);

        parameters.add(Params.VERBOSE);
        return parameters;
    }

    @Override
    public IKnowledge getKnowledge() {
        return this.knowledge;
    }

    @Override
    public void setKnowledge(final IKnowledge knowledge) {
        this.knowledge = knowledge;
    }
}
