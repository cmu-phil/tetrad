package edu.cmu.tetrad.algcomparison.algorithm.multi;

import edu.cmu.tetrad.algcomparison.algorithm.MultiDataSetAlgorithm;
import edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper;
import edu.cmu.tetrad.algcomparison.score.ScoreWrapper;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.algcomparison.utils.TakesIndependenceWrapper;
import edu.cmu.tetrad.annotation.AlgType;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.IndTestIod;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.search.utils.TsUtils;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.pitt.dbmi.algo.resampling.GeneralResamplingTest;

import java.io.Serial;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * <p>Runs FCI on multiple datasets using the IOD pooled dataset independence test. The reference is here:</p>
 *
 * <p>Tillman, R., &amp; Spirtes, P. (2011, June). Learning equivalence classes of acyclic models with latent and
 * selection variables from multiple datasets with overlapping variables. In Proceedings of the Fourteenth International
 * Conference on Artificial Intelligence and Statistics (pp. 3-15). JMLR Workshop and Conference Proceedings.</p>
 *
 * @author josephramsey
 * @version $Id: $Id
 * @see IndTestIod
 */
@edu.cmu.tetrad.annotation.Algorithm(
        name = "FCI-IOD",
        command = "fci-iod",
        algoType = AlgType.forbid_latent_common_causes,
        dataType = DataType.All
)
// Bootstrapping makes no sense here, since the algorithm pools the data from various sources, which may be federated
// in principle, so we've removed the bootstrapping annotation from it and deleted the bootstrapping code.
public class FciIod implements MultiDataSetAlgorithm, HasKnowledge, TakesIndependenceWrapper {

    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The knowledge.
     */
    private Knowledge knowledge = new Knowledge();

    /**
     * The independence test to use.
     */
    private IndependenceWrapper test;

    /**
     * <p>Constructor for FciIod.</p>
     *
     * @param test a {@link edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper} object
     */
    public FciIod(IndependenceWrapper test) {
        this.test = test;
    }

    /**
     * <p>Constructor for FciIod.</p>
     */
    public FciIod() {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Graph search(List<DataModel> dataSets, Parameters parameters) {
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

        List<IndependenceTest> tests = new ArrayList<>();

        for (DataModel dataModel : dataSets) {
            IndependenceTest s = test.getTest(dataModel, parameters);
            tests.add(s);
        }

        IndTestIod test = new IndTestIod(tests);

        edu.cmu.tetrad.search.Fci search = new edu.cmu.tetrad.search.Fci(test);
        search.setDepth(parameters.getInt(Params.DEPTH));
        search.setKnowledge(this.knowledge);
        search.setMaxPathLength(parameters.getInt(Params.MAX_PATH_LENGTH));
        search.setCompleteRuleSetUsed(parameters.getBoolean(Params.COMPLETE_RULE_SET_USED));
        search.setPossibleMsepSearchDone(parameters.getBoolean(Params.POSSIBLE_MSEP_DONE));
        search.setDoDiscriminatingPathRule(parameters.getBoolean(Params.DO_DISCRIMINATING_PATH_RULE));
        search.setVerbose(parameters.getBoolean(Params.VERBOSE));
        search.setStable(parameters.getBoolean(Params.STABLE_FAS));

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
        this.test = test;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Graph search(DataModel dataSet, Parameters parameters) {
        if (parameters.getInt(Params.NUMBER_RESAMPLING) < 1) {
            return search(Collections.singletonList(SimpleDataLoader.getMixedDataSet(dataSet)), parameters);
        } else {
            FciIod images = new FciIod();

            List<DataSet> dataSets = Collections.singletonList(SimpleDataLoader.getMixedDataSet(dataSet));
            GeneralResamplingTest search = new GeneralResamplingTest(dataSets,
                    images,
                    parameters.getInt(Params.NUMBER_RESAMPLING),
                    parameters.getDouble(Params.PERCENT_RESAMPLE_SIZE),
                    parameters.getBoolean(Params.RESAMPLING_WITH_REPLACEMENT),
                    parameters.getInt(Params.RESAMPLING_ENSEMBLE),
                    parameters.getBoolean(Params.ADD_ORIGINAL_DATASET), parameters.getInt(Params.BOOTSTRAPPING_NUM_THEADS));

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
        return new EdgeListGraph(graph);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription() {
        return "FCI-IOD";
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
        List<String> parameters = new LinkedList<>(test.getParameters());
        parameters.add(Params.DEPTH);
        parameters.add(Params.STABLE_FAS);
        parameters.add(Params.MAX_PATH_LENGTH);
        parameters.add(Params.POSSIBLE_MSEP_DONE);
        parameters.add(Params.DO_DISCRIMINATING_PATH_RULE);
        parameters.add(Params.COMPLETE_RULE_SET_USED);
        parameters.add(Params.TIME_LAG);
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
    public void setIndependenceWrapper(IndependenceWrapper test) {
        this.test = test;
    }
}
