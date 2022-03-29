package edu.cmu.tetrad.algcomparison.simulation;

import edu.cmu.tetrad.algcomparison.graph.RandomGraph;
import edu.cmu.tetrad.annotation.Experimental;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.cmu.tetrad.util.RandomUtil;

import java.util.*;

/**
 * A simulation method based on the mixed variable polynomial assumption.
 *
 * @author Bryan Andrews
 */
@Experimental
public class LinearSineSimulation implements Simulation {
    static final long serialVersionUID = 23L;
    private final RandomGraph randomGraph;
    private List<DataSet> dataSets = new ArrayList<>();
    private List<Graph> graphs = new ArrayList<>();
    private DataType dataType;
    private List<Node> shuffledOrder;

    private double interceptLow;
    private double interceptHigh = 1;
    private double linearLow = 0.5;
    private double linearHigh = 1;
    private double varLow = 0.5;
    private double varHigh = 0.5;
    private double betaLow = 1;
    private double betaHigh = 3;
    private double gammaLow = 0.5;
    private double gammaHigh = 1.5;

    public LinearSineSimulation(final RandomGraph graph) {
        this.randomGraph = graph;
    }

    @Override
    public void createData(final Parameters parameters, final boolean newModel) {
//        if (!newModel && !dataSets.isEmpty()) return;

        setInterceptLow(parameters.getDouble("interceptLow"));
        setInterceptHigh(parameters.getDouble("interceptHigh"));
        setLinearLow(parameters.getDouble("linearLow"));
        setLinearHigh(parameters.getDouble("linearHigh"));
        setVarLow(parameters.getDouble(Params.VAR_LOW));
        setVarHigh(parameters.getDouble(Params.VAR_HIGH));
        setBetaLow(parameters.getDouble("betaLow"));
        setBetaHigh(parameters.getDouble("betaHigh"));
        setGammaLow(parameters.getDouble("gammaLow"));
        setGammaHigh(parameters.getDouble("gammaHigh"));

        this.dataType = DataType.Continuous;

        this.shuffledOrder = null;

        Graph graph = this.randomGraph.createGraph(parameters);

        this.dataSets = new ArrayList<>();
        this.graphs = new ArrayList<>();

        for (int i = 0; i < parameters.getInt(Params.NUM_RUNS); i++) {
            System.out.println("Simulating dataset #" + (i + 1));

            if (parameters.getBoolean(Params.DIFFERENT_GRAPHS) && i > 0)
                graph = this.randomGraph.createGraph(parameters);

            this.graphs.add(graph);

            final DataSet dataSet = simulate(graph, parameters);
            dataSet.setName("" + (i + 1));
            this.dataSets.add(dataSet);
        }
    }

    @Override
    public Graph getTrueGraph(final int index) {
        return this.graphs.get(index);
    }

    @Override
    public DataModel getDataModel(final int index) {
        return this.dataSets.get(index);
    }

    @Override
    public String getDescription() {
        return "Linear-sine simulation using " + this.randomGraph.getDescription();
    }

    @Override
    public List<String> getParameters() {
        final List<String> parameters = this.randomGraph.getParameters();
        parameters.add(Params.NUM_RUNS);
        parameters.add(Params.DIFFERENT_GRAPHS);
        parameters.add(Params.SAMPLE_SIZE);
        parameters.add("interceptLow");
        parameters.add("interceptHigh");
        parameters.add("linearLow");
        parameters.add("linearHigh");
        parameters.add(Params.VAR_LOW);
        parameters.add(Params.VAR_HIGH);
        parameters.add("betaLow");
        parameters.add("betaHigh");
        parameters.add("gammaLow");
        parameters.add("gammaHigh");
        return parameters;
    }

    @Override
    public int getNumDataModels() {
        return this.dataSets.size();
    }

    @Override
    public DataType getDataType() {
        return this.dataType;
    }

    private DataSet simulate(Graph G, final Parameters parameters) {
        final HashMap<String, Integer> nd = new HashMap<>();

        List<Node> nodes = G.getNodes();

        Collections.shuffle(nodes);

        if (this.shuffledOrder == null) {
            final List<Node> shuffledNodes = new ArrayList<>(nodes);
            Collections.shuffle(shuffledNodes);
            this.shuffledOrder = shuffledNodes;
        }

        for (int i = 0; i < nodes.size(); i++) {
            nd.put(this.shuffledOrder.get(i).getName(), 0);
        }

        G = LinearSineSimulation.makeMixedGraph(G, nd);
        nodes = G.getNodes();

        final DataSet mixedData = new BoxDataSet(new MixedDataBox(nodes, parameters.getInt(Params.SAMPLE_SIZE)), nodes);

        final List<Node> tierOrdering = G.getCausalOrdering();
        final int[] tiers = new int[tierOrdering.size()];
        for (int t = 0; t < tierOrdering.size(); t++) {
            tiers[t] = nodes.indexOf(tierOrdering.get(t));
        }

        for (final int mixedIndex : tiers) {

            final ContinuousVariable child = (ContinuousVariable) nodes.get(mixedIndex);
            final ArrayList<ContinuousVariable> continuousParents = new ArrayList<>();
            for (final Node node : G.getParents(child)) {
                continuousParents.add((ContinuousVariable) node);
            }

            final HashMap<String, double[]> intercept = new HashMap<>();
            final HashMap<String, double[]> linear = new HashMap<>();
            final HashMap<String, double[]> beta = new HashMap<>();
            final HashMap<String, double[]> gamma = new HashMap<>();
            final HashMap<String, double[]> bounds = new HashMap<>();

            for (int j = 1; j <= continuousParents.size(); j++) {
                final String key = continuousParents.get(j - 1).toString();
                if (!bounds.containsKey(key)) {
                    double m0 = mixedData.getDouble(0, mixedData.getColumn(continuousParents.get(j - 1)));
                    double m1 = mixedData.getDouble(0, mixedData.getColumn(continuousParents.get(j - 1)));
                    for (int i = 1; i < parameters.getInt(Params.SAMPLE_SIZE); i++) {
                        m0 = Math.min(m0, mixedData.getDouble(i, mixedData.getColumn(continuousParents.get(j - 1))));
                        m1 = Math.max(m1, mixedData.getDouble(i, mixedData.getColumn(continuousParents.get(j - 1))));
                    }
                    final double[] temp = new double[3];
                    temp[0] = m0;
                    temp[1] = (m1 - m0) / 2;
                    temp[2] = m1;
                    bounds.put(key, temp);
                }
            }

            double mean = 0;
            double var = 0;

            for (int i = 0; i < parameters.getInt(Params.SAMPLE_SIZE); i++) {

                final double[] parents = new double[continuousParents.size()];
                double value = 0;
                final String key = "";

                for (int j = 1; j <= continuousParents.size(); j++)
                    parents[j - 1] = mixedData.getDouble(i, mixedData.getColumn(continuousParents.get(j - 1)));

                if (!intercept.containsKey(key)) {
                    final double[] interceptCoefficients = new double[1];
                    interceptCoefficients[0] = randSign() * RandomUtil.getInstance().nextUniform(this.interceptLow, this.interceptHigh);
                    intercept.put(key, interceptCoefficients);
                }

                if (!linear.containsKey(key) && !continuousParents.isEmpty()) {
                    final double[] linearCoefficients = new double[parents.length];
                    for (int j = 0; j < parents.length; j++)
                        linearCoefficients[j] = randSign() * RandomUtil.getInstance().nextUniform(this.linearLow, this.linearHigh);
                    linear.put(key, linearCoefficients);
                }

                if (!beta.containsKey(key) && !continuousParents.isEmpty()) {
                    final double[] betaCoefficients = new double[parents.length];
                    for (int j = 0; j < parents.length; j++)
                        betaCoefficients[j] = randSign() * RandomUtil.getInstance().nextUniform(this.betaLow, this.betaHigh);
                    beta.put(key, betaCoefficients);
                }

                if (!gamma.containsKey(key) && !continuousParents.isEmpty()) {
                    final double[] gammaCoefficients = new double[parents.length];
                    for (int j = 0; j < parents.length; j++) {
                        final String key2 = continuousParents.get(j).toString();
                        gammaCoefficients[j] = (bounds.get(key2)[1] - bounds.get(key2)[0]) / (2 * Math.PI * RandomUtil.getInstance().nextUniform(this.gammaLow, this.gammaHigh));
                    }
                    gamma.put(key, gammaCoefficients);
                }

                value += intercept.get(key)[0];
                if (!continuousParents.isEmpty()) {
                    for (int x = 0; x < parents.length; x++) {
                        final String key2 = continuousParents.get(x).toString();
                        value += linear.get(key)[x] * parents[x] + beta.get(key)[x] * Math.sin(parents[x] / (gamma.get(key)[x]));
                    }
                }

                mixedData.setDouble(i, mixedIndex, value);

                mean += value;
                var += Math.pow(value, 2);
            }
            if (continuousParents.size() == 0) {
                var = 1;
            } else {
                mean /= mixedData.getNumRows();
                var /= mixedData.getNumRows();
                var -= Math.pow(mean, 2);
                var = Math.sqrt(var);
            }

            final double noiseVar = RandomUtil.getInstance().nextUniform(this.varLow, this.varHigh);
            for (int i = 0; i < parameters.getInt(Params.SAMPLE_SIZE); i++) {
                mixedData.setDouble(i, mixedIndex, mixedData.getDouble(i, mixedIndex) + var * RandomUtil.getInstance().nextNormal(0, noiseVar));
            }
        }
        return mixedData;
    }


    public void setInterceptLow(final double interceptLow) {
        this.interceptLow = interceptLow;
    }

    public void setInterceptHigh(final double interceptHigh) {
        this.interceptHigh = interceptHigh;
    }

    public void setLinearLow(final double linearLow) {
        this.linearLow = linearLow;
    }

    public void setLinearHigh(final double linearHigh) {
        this.linearHigh = linearHigh;
    }

    public void setVarLow(final double varLow) {
        this.varLow = varLow;
    }

    public void setVarHigh(final double varHigh) {
        this.varHigh = varHigh;
    }

    public void setBetaLow(final double betaLow) {
        this.betaLow = betaLow;
    }

    public void setBetaHigh(final double betaHigh) {
        this.betaHigh = betaHigh;
    }

    public void setGammaLow(final double gammaLow) {
        this.gammaLow = gammaLow;
    }

    public void setGammaHigh(final double gammaHigh) {
        this.gammaHigh = gammaHigh;
    }

    private int randSign() {
        return RandomUtil.getInstance().nextInt(2) * 2 - 1;
    }

    private static Graph makeMixedGraph(final Graph g, final Map<String, Integer> m) {
        final List<Node> nodes = g.getNodes();
        for (int i = 0; i < nodes.size(); i++) {
            final Node n = nodes.get(i);
            final int nL = m.get(n.getName());
            if (nL > 0) {
                final Node nNew = new DiscreteVariable(n.getName(), nL);
                nodes.set(i, nNew);
            } else {
                final Node nNew = new ContinuousVariable(n.getName());
                nodes.set(i, nNew);
            }
        }

        final Graph outG = new EdgeListGraph(nodes);

        for (final Edge e : g.getEdges()) {
            final Node n1 = e.getNode1();
            final Node n2 = e.getNode2();
            final Edge eNew = new Edge(outG.getNode(n1.getName()), outG.getNode(n2.getName()), e.getEndpoint1(), e.getEndpoint2());
            outG.addEdge(eNew);
        }

        return outG;
    }

}
