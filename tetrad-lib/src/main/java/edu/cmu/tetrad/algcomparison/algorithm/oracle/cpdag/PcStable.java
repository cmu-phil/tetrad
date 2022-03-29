package edu.cmu.tetrad.algcomparison.algorithm.oracle.cpdag;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.algcomparison.utils.TakesIndependenceWrapper;
import edu.cmu.tetrad.annotation.Bootstrapping;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.PcAll;
import edu.cmu.tetrad.search.SearchGraphUtils;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.pitt.dbmi.algo.resampling.GeneralResamplingTest;
import edu.pitt.dbmi.algo.resampling.ResamplingEdgeEnsemble;

import java.util.ArrayList;
import java.util.List;

/**
 * PC-Stable.
 *
 * @author jdramsey
 */
@Bootstrapping
public class PcStable implements Algorithm, HasKnowledge, TakesIndependenceWrapper {

    static final long serialVersionUID = 23L;
    private IndependenceWrapper test;
    private IKnowledge knowledge = new Knowledge2();

    public PcStable() {
    }

    public PcStable(final IndependenceWrapper test) {
        this.test = test;
    }

    @Override
    public Graph search(final DataModel dataSet, final Parameters parameters) {
        if (parameters.getInt(Params.NUMBER_RESAMPLING) < 1) {
            final edu.cmu.tetrad.search.PcAll search = new edu.cmu.tetrad.search.PcAll(this.test.getTest(dataSet, parameters));
            search.setDepth(parameters.getInt(Params.DEPTH));
            search.setKnowledge(this.knowledge);
            search.setFasType(PcAll.FasType.STABLE);
            search.setConcurrent(PcAll.Concurrent.NO);
            search.setColliderDiscovery(PcAll.ColliderDiscovery.FAS_SEPSETS);
            search.setConflictRule(PcAll.ConflictRule.PRIORITY);
            search.setVerbose(parameters.getBoolean(Params.VERBOSE));
            return search.search();
        } else {
            final PcStable pcStable = new PcStable(this.test);

            final DataSet data = (DataSet) dataSet;
            final GeneralResamplingTest search = new GeneralResamplingTest(data, pcStable, parameters.getInt(Params.NUMBER_RESAMPLING));
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
        return SearchGraphUtils.cpdagForDag(new EdgeListGraph(graph));
    }

    @Override
    public String getDescription() {
        return "PC-Stable (\"Peter and Clark\" Stable), Priority Rule, using " + this.test.getDescription();
    }

    @Override
    public DataType getDataType() {
        return this.test.getDataType();
    }

    @Override
    public List<String> getParameters() {
        final List<String> parameters = new ArrayList<>();
        parameters.add(Params.DEPTH);

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

    @Override
    public void setIndependenceWrapper(final IndependenceWrapper independenceWrapper) {
        this.test = independenceWrapper;
    }

    @Override
    public IndependenceWrapper getIndependenceWrapper() {
        return this.test;
    }
}