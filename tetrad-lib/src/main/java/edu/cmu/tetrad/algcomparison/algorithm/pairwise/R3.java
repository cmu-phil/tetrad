package edu.cmu.tetrad.algcomparison.algorithm.pairwise;

import edu.cmu.tetrad.algcomparison.algorithm.AbstractBootstrapAlgorithm;
import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.utils.TakesExternalGraph;
import edu.cmu.tetrad.annotation.AlgType;
import edu.cmu.tetrad.annotation.Bootstrapping;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.Lofs;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.pitt.dbmi.algo.resampling.GeneralResamplingTest;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

/**
 * R3.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
@edu.cmu.tetrad.annotation.Algorithm(
        name = "R3",
        command = "r3",
        algoType = AlgType.orient_pairwise,
        dataType = DataType.Continuous

)
@Bootstrapping
public class R3 extends AbstractBootstrapAlgorithm implements Algorithm, TakesExternalGraph {

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
     * <p>Constructor for R3.</p>
     */
    public R3() {
    }

    /**
     * <p>Constructor for R3.</p>
     *
     * @param algorithm a {@link edu.cmu.tetrad.algcomparison.algorithm.Algorithm} object
     */
    public R3(Algorithm algorithm) {
        this.algorithm = algorithm;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Graph runSearch(DataModel dataModel, Parameters parameters) {
        if (!(dataModel instanceof DataSet dataSet && dataModel.isContinuous())) {
            throw new IllegalArgumentException("Expecting a continuous dataset.");
        }

        if (parameters.getInt(Params.NUMBER_RESAMPLING) < 1) {
            Graph graph = this.algorithm.search(dataSet, parameters);

            if (graph != null) {
                this.externalGraph = graph;
            } else {
                throw new IllegalArgumentException("This R3 algorithm needs both data and a graph source as inputs; it \n"
                        + "will orient the edges in the input graph using the data");
            }

            List<DataSet> dataSets = new ArrayList<>();
            dataSets.add(SimpleDataLoader.getContinuousDataSet(dataSet));

            Lofs lofs = new Lofs(this.externalGraph, dataSets);
            lofs.setRule(Lofs.Rule.R3);

            return lofs.orient();
        } else {
            R3 r3 = new R3(this.algorithm);
            if (this.externalGraph != null) {
                r3.setExternalGraph(this.algorithm);
            }

            DataSet data = (DataSet) dataSet;
            GeneralResamplingTest search = new GeneralResamplingTest(data, r3,
                    new Knowledge(), parameters);

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
        return "R3, entropy based pairwise orientation" + (this.algorithm != null ? " with initial graph from "
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
        List<String> parameters = new ArrayList<>();

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
            throw new IllegalArgumentException("This R3 algorithm needs both data and a graph source as inputs; it \n"
                    + "will orient the edges in the input graph using the data.");
        }

        this.algorithm = algorithm;
    }

}
