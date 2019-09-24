package edu.cmu.tetrad.algcomparison.algorithm.pairwise;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.utils.TakesInitialGraph;
import edu.cmu.tetrad.annotation.Bootstrapping;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.DataUtils;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.Lofs2;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.pitt.dbmi.algo.resampling.GeneralResamplingTest;
import edu.pitt.dbmi.algo.resampling.ResamplingEdgeEnsemble;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * SkewE.
 *
 * @author jdramsey
 */
//@Experimental
//@edu.cmu.tetrad.annotation.Algorithm(
//        name = "SkewE",
//        command = "skew-e",
//        algoType = AlgType.orient_pairwise
//)
@Bootstrapping
public class SkewE implements Algorithm, TakesInitialGraph {

    static final long serialVersionUID = 23L;
    private Algorithm algorithm = null;
    private Graph initialGraph = null;

    public SkewE() {
    }

    public SkewE(Algorithm algorithm) {
        this.algorithm = algorithm;
    }

    @Override
    public Graph search(DataModel dataSet, Parameters parameters) {
    	if (parameters.getInt(Params.NUMBER_RESAMPLING) < 1) {
            Graph graph = algorithm.search(dataSet, parameters);

            if (graph != null) {
                initialGraph = graph;
            } else {
                throw new IllegalArgumentException("This SkewE algorithm needs both data and a graph source as inputs; it \n"
                        + "will orient the edges in the input graph using the data");
            }

            List<DataSet> dataSets = new ArrayList<>();
            dataSets.add(DataUtils.getContinuousDataSet(dataSet));

            Lofs2 lofs = new Lofs2(initialGraph, dataSets);
            lofs.setRule(Lofs2.Rule.SkewE);

            return lofs.orient();
        } else {
            SkewE skewE = new SkewE(algorithm);
            if (initialGraph != null) {
                skewE.setInitialGraph(initialGraph);
            }

            DataSet data = (DataSet) dataSet;
            GeneralResamplingTest search = new GeneralResamplingTest(data, skewE, parameters.getInt(Params.NUMBER_RESAMPLING));

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
        return new EdgeListGraph(graph);
    }

    @Override
    public String getDescription() {
        return "SkewE" + (algorithm != null ? " with initial graph from "
                + algorithm.getDescription() : "");
    }

    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }

    @Override
    public List<String> getParameters() {
        List<String> parameters = new LinkedList<>();

        if (algorithm != null && !algorithm.getParameters().isEmpty()) {
            parameters.addAll(algorithm.getParameters());
        }

        parameters.add(Params.VERBOSE);

        return parameters;
    }

    @Override
    public Graph getInitialGraph() {
        return initialGraph;
    }

    @Override
    public void setInitialGraph(Graph initialGraph) {
        this.initialGraph = initialGraph;
    }

    @Override
    public void setInitialGraph(Algorithm algorithm) {
        if (algorithm == null) {
            throw new IllegalArgumentException("This SkewE algorithm needs both data and a graph source as inputs; it \n"
                    + "will orient the edges in the input graph using the data.");
        }

        this.algorithm = algorithm;
    }

}
