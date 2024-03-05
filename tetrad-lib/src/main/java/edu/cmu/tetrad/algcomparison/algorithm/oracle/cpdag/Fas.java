package edu.cmu.tetrad.algcomparison.algorithm.oracle.cpdag;

import edu.cmu.tetrad.algcomparison.algorithm.AbstractBootstrapAlgorithm;
import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.algorithm.ReturnsBootstrapGraphs;
import edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.algcomparison.utils.TakesIndependenceWrapper;
import edu.cmu.tetrad.annotation.AlgType;
import edu.cmu.tetrad.annotation.Bootstrapping;import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphTransforms;
import edu.cmu.tetrad.search.utils.PcCommon;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;

import java.io.PrintStream;
import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

/**
 * Fast Adjacency Search (FAS)--i.e., the PC adjacency step, which is used in many algorithms.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
@edu.cmu.tetrad.annotation.Algorithm(
        name = "FAS",
        command = "fas",
        algoType = AlgType.produce_undirected_graphs
)
@Bootstrapping
public class Fas extends AbstractBootstrapAlgorithm implements Algorithm, HasKnowledge, TakesIndependenceWrapper,
        ReturnsBootstrapGraphs {

    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The independence test to use.
     */
    private IndependenceWrapper test;

    /**
     * The knowledge.
     */
    private Knowledge knowledge = new Knowledge();

    /**
     * <p>Constructor for Fas.</p>
     */
    public Fas() {
    }

    /**
     * <p>Constructor for Fas.</p>
     *
     * @param test a {@link edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper} object
     */
    public Fas(IndependenceWrapper test) {
        this.test = test;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Graph runSearch(DataSet dataSet, Parameters parameters) {
        PcCommon.PcHeuristicType pcHeuristicType = switch (parameters.getInt(Params.PC_HEURISTIC)) {
            case 0 ->
                PcCommon.PcHeuristicType.NONE;
            case 1 ->
                PcCommon.PcHeuristicType.HEURISTIC_1;
            case 2 ->
                PcCommon.PcHeuristicType.HEURISTIC_2;
            case 3 ->
                PcCommon.PcHeuristicType.HEURISTIC_3;
            default ->
                throw new IllegalArgumentException("Unknown conflict rule: " + parameters.getInt(Params.CONFLICT_RULE));
        };

        edu.cmu.tetrad.search.Fas search = new edu.cmu.tetrad.search.Fas(this.test.getTest(dataSet, parameters));
        search.setStable(parameters.getBoolean(Params.STABLE_FAS));
        search.setPcHeuristicType(pcHeuristicType);
        search.setDepth(parameters.getInt(Params.DEPTH));
        search.setKnowledge(this.knowledge);
        search.setVerbose(parameters.getBoolean(Params.VERBOSE));

        Object obj = parameters.get(Params.PRINT_STREAM);

        if (obj instanceof PrintStream ps) {
            search.setOut(ps);
        }

        return search.search();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Graph getComparisonGraph(Graph graph) {
        Graph dag = new EdgeListGraph(graph);
        return GraphTransforms.cpdagForDag(dag);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription() {
        return "Fast adjacency search (FAS) using " + this.test.getDescription();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DataType getDataType() {
        return this.test.getDataType();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();
        parameters.add(Params.DEPTH);
        parameters.add(Params.PC_HEURISTIC);
        parameters.add(Params.STABLE_FAS);
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
