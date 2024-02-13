package edu.cmu.tetrad.algcomparison.algorithm.pairwise;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.utils.TakesExternalGraph;
import edu.cmu.tetrad.annotation.Bootstrapping;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.SimpleDataLoader;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.Lofs;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.pitt.dbmi.algo.resampling.GeneralResamplingTest;

import java.io.Serial;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * EB.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
//@Experimental
//@edu.cmu.tetrad.annotation.Algorithm(
//        name = "EB",
//        command = "eb",
//        algoType = AlgType.orient_pairwise
//)
@Bootstrapping
public class Eb implements Algorithm, TakesExternalGraph {

    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The algorithm to use for the initial graph.
     */
    private Algorithm algorithm;

    /**
     * The external graph.
     */
    private Graph externalGraph;

    /**
     * <p>Constructor for Eb.</p>
     */
    public Eb() {
    }

    /**
     * <p>Constructor for Eb.</p>
     *
     * @param algorithm a {@link edu.cmu.tetrad.algcomparison.algorithm.Algorithm} object
     */
    public Eb(Algorithm algorithm) {
        this.algorithm = algorithm;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Graph search(DataModel dataSet, Parameters parameters) {
        if (parameters.getInt(Params.NUMBER_RESAMPLING) < 1) {
            this.externalGraph = this.algorithm.search(dataSet, parameters);

            if (this.algorithm != null) {
                this.externalGraph = this.algorithm.search(dataSet, parameters);
            } else {
                throw new IllegalArgumentException("This EB algorithm needs both data and a graph source as inputs; it \n"
                        + "will orient the edges in the input graph using the data.");
            }

            List<DataSet> dataSets = new ArrayList<>();
            dataSets.add(SimpleDataLoader.getContinuousDataSet(dataSet));

            Lofs lofs = new Lofs(this.externalGraph, dataSets);
            lofs.setRule(Lofs.Rule.EB);

            return lofs.orient();
        } else {
            Eb eb = new Eb(this.algorithm);
            if (this.externalGraph != null) {
                eb.setExternalGraph(this.algorithm);
            }

            DataSet data = (DataSet) dataSet;
            GeneralResamplingTest search = new GeneralResamplingTest(data, eb, parameters.getInt(Params.NUMBER_RESAMPLING), parameters.getDouble(Params.PERCENT_RESAMPLE_SIZE), parameters.getBoolean(Params.RESAMPLING_WITH_REPLACEMENT), parameters.getInt(Params.RESAMPLING_ENSEMBLE), parameters.getBoolean(Params.ADD_ORIGINAL_DATASET), parameters.getInt(Params.BOOTSTRAPPING_NUM_THEADS));

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
        return "EB, entropy based pairwise orientation" + (this.algorithm != null ? " with initial graph from "
                + this.algorithm.getDescription() : "");
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
        List<String> parameters = new LinkedList<>();

        if (this.algorithm != null && !this.algorithm.getParameters().isEmpty()) {
            parameters.addAll(this.algorithm.getParameters());
        }

        parameters.add(Params.VERBOSE);

        return parameters;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setExternalGraph(Algorithm algorithm) {
        if (algorithm == null) {
            throw new IllegalArgumentException("This EB algorithm needs both data and a graph source as inputs; it \n"
                    + "will orient the edges in the input graph using the data.");
        }

        this.algorithm = algorithm;
    }
}
