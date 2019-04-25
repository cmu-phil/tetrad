package edu.cmu.tetrad.algcomparison.algorithm.multi;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.algcomparison.utils.TakesIndependenceWrapper;
import edu.cmu.tetrad.algcomparison.utils.TakesInitialGraph;
import edu.cmu.tetrad.annotation.AlgType;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.util.Parameters;
import edu.pitt.dbmi.algo.resampling.GeneralResamplingTest;
import edu.pitt.dbmi.algo.resampling.ResamplingEdgeEnsemble;

import java.util.ArrayList;
import java.util.List;

/**
 * Wraps the IMaGES algorithm for continuous variables.
 * </p>
 * Requires that the parameter 'randomSelectionSize' be set to indicate how many
 * datasets should be taken at a time (randomly). This cannot given multiple values.
 *
 * @author jdramsey
 */
@edu.cmu.tetrad.annotation.Algorithm(
        name = "FASK",
        command = "fask",
        algoType = AlgType.forbid_latent_common_causes,
        dataType = DataType.Continuous
)
public class Fask implements Algorithm, HasKnowledge, TakesIndependenceWrapper, TakesInitialGraph {
    static final long serialVersionUID = 23L;
    private IndependenceWrapper test = null;
    private Graph initialGraph = null;
    private IKnowledge knowledge = new Knowledge2();
    private Algorithm algorithm = null;

    public Fask() {

    }

    public Fask(IndependenceWrapper test) {
        this.test = test;
    }

    public Fask(Algorithm algorithm) {
        this.algorithm = algorithm;
    }

    @Override
    public Graph search(DataModel dataSet, Parameters parameters) {
        if (parameters.getInt("numberResampling") < 1) {
            edu.cmu.tetrad.search.Fask search;

            if (algorithm != null) {
                initialGraph = algorithm.search(dataSet, parameters);
                search = new edu.cmu.tetrad.search.Fask((DataSet) dataSet, initialGraph);
                search.setInitialGraph(initialGraph);
            } else {
                search = new edu.cmu.tetrad.search.Fask((DataSet) dataSet, test.getTest(dataSet, parameters));
            }

            search.setDepth(parameters.getInt("depth"));
            search.setPenaltyDiscount(parameters.getDouble("penaltyDiscount"));
            search.setExtraEdgeThreshold(parameters.getDouble("extraEdgeThreshold"));
            search.setUseFasAdjacencies(parameters.getBoolean("useFasAdjacencies"));
            search.setUseSkewAdjacencies(parameters.getBoolean("useCorrDiffAdjacencies"));
            search.setAlpha(parameters.getDouble("twoCycleAlpha"));
            search.setKnowledge(knowledge);
            return search.search();
        } else {
            Fask fask = new Fask(test);

            DataSet data = (DataSet) dataSet;
            GeneralResamplingTest search = new GeneralResamplingTest(data, fask, parameters.getInt("numberResampling"));
            search.setKnowledge(knowledge);

            search.setPercentResampleSize(parameters.getDouble("percentResampleSize"));
            search.setResamplingWithReplacement(parameters.getBoolean("resamplingWithReplacement"));

            ResamplingEdgeEnsemble edgeEnsemble = ResamplingEdgeEnsemble.Highest;
            switch (parameters.getInt("resamplingEnsemble", 1)) {
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
            search.setAddOriginalDataset(parameters.getBoolean("addOriginalDataset"));

            search.setParameters(parameters);
            search.setVerbose(parameters.getBoolean("verbose"));
            return search.search();
        }
    }

    @Override
    public Graph getComparisonGraph(Graph graph) {
        return new EdgeListGraph(graph);
    }

    @Override
    public String getDescription() {
        if (test != null) {
            return "FASK using " + test.getDescription();
        } else if (algorithm != null) {
            return "FASK using " + algorithm.getDescription();
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

        if (algorithm != null) {
            parameters.addAll(algorithm.getParameters());
        }

        if (test != null) {
            parameters.addAll(test.getParameters());
        }

        parameters.add("depth");
        parameters.add("twoCycleAlpha");
        parameters.add("extraEdgeThreshold");

        parameters.add("useFasAdjacencies");
        parameters.add("useCorrDiffAdjacencies");

        // Resampling
        parameters.add("numberResampling");
        parameters.add("percentResampleSize");
        parameters.add("resamplingWithReplacement");
        parameters.add("resamplingEnsemble");
        parameters.add("addOriginalDataset");
        parameters.add("verbose");

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
    public void setIndependenceWrapper(IndependenceWrapper independenceWrapper) {
        this.test = independenceWrapper;
    }

    @Override
    public IndependenceWrapper getIndependenceWrapper() {
        return test;
    }

    @Override
    public Graph getInitialGraph() {
        return null;
    }

    @Override
    public void setInitialGraph(Graph initialGraph) {
        this.initialGraph = initialGraph;
    }

    @Override
    public void setInitialGraph(Algorithm algorithm) {
        this.algorithm = algorithm;
    }
}