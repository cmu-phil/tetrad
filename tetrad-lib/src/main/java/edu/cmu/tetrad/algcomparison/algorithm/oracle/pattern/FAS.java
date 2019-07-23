package edu.cmu.tetrad.algcomparison.algorithm.oracle.pattern;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.algcomparison.utils.TakesIndependenceWrapper;
import edu.cmu.tetrad.annotation.AlgType;
import edu.cmu.tetrad.annotation.Bootstrapping;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.IKnowledge;
import edu.cmu.tetrad.data.Knowledge2;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.SearchGraphUtils;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.pitt.dbmi.algo.resampling.GeneralResamplingTest;
import edu.pitt.dbmi.algo.resampling.ResamplingEdgeEnsemble;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

/**
 * FAS.
 *
 * @author jdramsey
 */
@edu.cmu.tetrad.annotation.Algorithm(
        name = "FAS",
        command = "fas",
        algoType = AlgType.produce_undirected_graphs
)
@Bootstrapping
public class FAS implements Algorithm, HasKnowledge, TakesIndependenceWrapper {

    static final long serialVersionUID = 23L;
    private IndependenceWrapper test;
    private IKnowledge knowledge = new Knowledge2();

    public FAS() {
    }

    public FAS(IndependenceWrapper test) {
        this.test = test;
    }

    @Override
    public Graph search(DataModel dataSet, Parameters parameters) {
        if (parameters.getInt(Params.NUMBER_RESAMPLING) < 1) {
            edu.cmu.tetrad.search.Fas search = new edu.cmu.tetrad.search.Fas(test.getTest(dataSet, parameters));
            search.setDepth(parameters.getInt(Params.DEPTH));
            search.setKnowledge(knowledge);
            search.setVerbose(parameters.getBoolean(Params.VERBOSE));

            Object obj = parameters.get(Params.PRINT_STREAM);
            if (obj instanceof PrintStream) {
                search.setOut((PrintStream) obj);
            }

            return search.search();
        } else {
            FAS algorithm = new FAS(test);

            DataSet data = (DataSet) dataSet;
            GeneralResamplingTest search = new GeneralResamplingTest(data, algorithm, parameters.getInt(Params.NUMBER_RESAMPLING));
            search.setKnowledge(knowledge);

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
    public Graph getComparisonGraph(Graph graph) {
        return SearchGraphUtils.patternForDag(new EdgeListGraph(graph));
    }

    @Override
    public String getDescription() {
        return "Fast adjacency search (FAS) using " + test.getDescription();
    }

    @Override
    public DataType getDataType() {
        return test.getDataType();
    }

    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();
        parameters.add(Params.DEPTH);

        parameters.add(Params.VERBOSE);
        return parameters;
    }

    @Override
    public IKnowledge getKnowledge() {
        return knowledge;
    }

    @Override
    public void setKnowledge(IKnowledge knowledge) {
        this.knowledge = knowledge;
    }

    @Override
    public void setIndependenceWrapper(IndependenceWrapper test) {
        this.test = test;
    }

    @Override
    public IndependenceWrapper getIndependenceWrapper() {
        return test;
    }
}
