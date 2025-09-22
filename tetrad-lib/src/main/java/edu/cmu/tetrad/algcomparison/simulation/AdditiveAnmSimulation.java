package edu.cmu.tetrad.algcomparison.simulation;

import edu.cmu.tetrad.algcomparison.graph.RandomGraph;
import edu.cmu.tetrad.algcomparison.graph.SingleGraph;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.LayoutUtil;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.sem.AdditiveAnmSimulator;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.cmu.tetrad.util.RandomUtil;
import org.apache.commons.math3.distribution.BetaDistribution;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

/**
 * Additive Nonlinear SEM (ANM-style) simulator wrapper with fixed defaults.
 *
 * Hard-coded settings chosen for "crisp" nonlinear additive data:
 * <ul>
 *   <li>Family = RBF</li>
 *   <li>Units per edge = 8</li>
 *   <li>Edge scale = 5.0</li>
 *   <li>Input standardization = true</li>
 *   <li>Noise = Beta(2, 8), rescaled to [-3,3]</li>
 * </ul>
 *
 * Only SAMPLE_SIZE, NUM_RUNS, and SEED are respected from parameters.
 */
public class AdditiveAnmSimulation implements Simulation {

    @Serial
    private static final long serialVersionUID = 1L;

    private final RandomGraph randomGraph;
    private List<DataSet> dataSets = new ArrayList<>();
    private List<Graph> graphs = new ArrayList<>();

    public AdditiveAnmSimulation(RandomGraph graph) {
        if (graph == null) throw new NullPointerException("Graph is null.");
        this.randomGraph = graph;
    }

    @Override
    public void createData(Parameters parameters, boolean newModel) {
        long seed = parameters.getLong(Params.SEED);
        if (seed != -1L) RandomUtil.getInstance().setSeed(seed);

        dataSets = new ArrayList<>();
        graphs   = new ArrayList<>();

        for (int run = 0; run < parameters.getInt(Params.NUM_RUNS); run++) {
            Graph g = randomGraph.createGraph(parameters);

            // replace with continuous vars
            List<Node> vars = new ArrayList<>();
            for (Node n : g.getNodes()) {
                ContinuousVariable v = new ContinuousVariable(n.getName());
                v.setNodeType(n.getNodeType());
                vars.add(v);
            }
            g = GraphUtils.replaceNodes(g, vars);
            LayoutUtil.defaultLayout(g);

            DataSet ds = simulate(g, parameters);
            graphs.add(g);
            dataSets.add(ds);
        }
    }

    private DataSet simulate(Graph graph, Parameters p) {
        return runModel(graph, p);
    }

    private DataSet runModel(Graph graph, Parameters p) {
        // --- Hard-coded defaults ---
        int sampleSize = p.getInt(Params.SAMPLE_SIZE);
        int unitsPerEdge = 6;
        double edgeScale = 6.0;
        boolean inputStandardize = true;

        // Noise: skewed Beta(2,8)
        BetaDistribution noise = new BetaDistribution(2.0, 8.0);

        AdditiveAnmSimulator gen = new AdditiveAnmSimulator(
                graph,
                sampleSize,
                noise,
                -3.0, 3.0
        )
                .setFunctionFamily(AdditiveAnmSimulator.Family.RBF)
                .setNumUnitsPerEdge(unitsPerEdge)
                .setInputStandardize(inputStandardize)
                .setEdgeScale(edgeScale);

        long seed = p.getLong(Params.SEED);
        if (seed != -1L) gen.setSeed(seed);

        return gen.generate();
    }

    @Override public Graph getTrueGraph(int index) { return graphs.get(index); }
    @Override public int getNumDataModels() { return dataSets.size(); }
    @Override public DataModel getDataModel(int index) { return dataSets.get(index); }
    @Override public DataType getDataType() { return DataType.Continuous; }
    @Override public String getDescription() { return "Additive Nonlinear SEM (ANM, fixed defaults)"; }
    @Override public String getShortName() { return "ANM"; }

    @Override
    public List<String> getParameters() {
        List<String> out = new ArrayList<>();
        if (!(randomGraph instanceof SingleGraph)) out.addAll(randomGraph.getParameters());
        out.add(Params.SAMPLE_SIZE);
        out.add(Params.NUM_RUNS);
        out.add(Params.SEED);
        return out;
    }

    @Override public Class<? extends RandomGraph> getRandomGraphClass() { return randomGraph.getClass(); }
    @Override public Class<? extends Simulation> getSimulationClass() { return getClass(); }
}