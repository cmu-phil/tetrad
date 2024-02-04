package edu.cmu.tetrad.algcomparison.simulation;

import edu.cmu.tetrad.algcomparison.graph.RandomGraph;
import edu.cmu.tetrad.annotation.Experimental;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.cmu.tetrad.util.RandomUtil;
import org.apache.commons.math3.util.FastMath;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A simulation method based on the mixed variable polynomial assumption.
 *
 * @author Bryan Andrews
 */
@Experimental
public class LinearSineSimulation implements Simulation {
    private static final long serialVersionUID = 23L;
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

    public LinearSineSimulation(RandomGraph graph) {
        this.randomGraph = graph;
    }

    private static Graph makeMixedGraph(Graph g, Map<String, Integer> m) {
        List<Node> nodes = g.getNodes();
        for (int i = 0; i < nodes.size(); i++) {
            Node n = nodes.get(i);
            int nL = m.get(n.getName());
            if (nL > 0) {
                Node nNew = new DiscreteVariable(n.getName(), nL);
                nodes.set(i, nNew);
            } else {
                Node nNew = new ContinuousVariable(n.getName());
                nodes.set(i, nNew);
            }
        }

        Graph outG = new EdgeListGraph(nodes);

        for (Edge e : g.getEdges()) {
            Node n1 = e.getNode1();
            Node n2 = e.getNode2();
            Edge eNew = new Edge(outG.getNode(n1.getName()), outG.getNode(n2.getName()), e.getEndpoint1(), e.getEndpoint2());
            outG.addEdge(eNew);
        }

        return outG;
    }

    @Override
    public void createData(Parameters parameters, boolean newModel) {
        if (parameters.getLong(Params.SEED) != -1L) {
            RandomUtil.getInstance().setSeed(parameters.getLong(Params.SEED));
        }

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
            if (parameters.getBoolean(Params.DIFFERENT_GRAPHS) && i > 0)
                graph = this.randomGraph.createGraph(parameters);

            this.graphs.add(graph);

            DataSet dataSet = simulate(graph, parameters);

            if (parameters.getDouble(Params.PROB_REMOVE_COLUMN) > 0) {
                double aDouble = parameters.getDouble(Params.PROB_REMOVE_COLUMN);
                dataSet = DataTransforms.removeRandomColumns(dataSet, aDouble);
            }

            dataSet.setName("" + (i + 1));
            this.dataSets.add(dataSet);
        }
    }

    @Override
    public Graph getTrueGraph(int index) {
        return this.graphs.get(index);
    }

    @Override
    public DataModel getDataModel(int index) {
        return this.dataSets.get(index);
    }

    @Override
    public String getDescription() {
        return "Linear-sine simulation using " + this.randomGraph.getDescription();
    }

    @Override
    public List<String> getParameters() {
        List<String> parameters = this.randomGraph.getParameters();
        parameters.add(Params.NUM_RUNS);
        parameters.add(Params.PROB_REMOVE_COLUMN);
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
        parameters.add(Params.SEED);
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

    private DataSet simulate(Graph G, Parameters parameters) {
        HashMap<String, Integer> nd = new HashMap<>();

        List<Node> nodes = G.getNodes();

        RandomUtil.shuffle(nodes);

        if (this.shuffledOrder == null) {
            List<Node> shuffledNodes = new ArrayList<>(nodes);
            RandomUtil.shuffle(shuffledNodes);
            this.shuffledOrder = shuffledNodes;
        }

        for (int i = 0; i < nodes.size(); i++) {
            nd.put(this.shuffledOrder.get(i).getName(), 0);
        }

        G = LinearSineSimulation.makeMixedGraph(G, nd);
        nodes = G.getNodes();

        DataSet mixedData = new BoxDataSet(new MixedDataBox(nodes, parameters.getInt(Params.SAMPLE_SIZE)), nodes);

        Paths paths = G.paths();
        List<Node> initialOrder = G.getNodes();
        List<Node> tierOrdering = paths.getValidOrder(initialOrder, true);
        int[] tiers = new int[tierOrdering.size()];
        for (int t = 0; t < tierOrdering.size(); t++) {
            tiers[t] = nodes.indexOf(tierOrdering.get(t));
        }

        for (int mixedIndex : tiers) {

            ContinuousVariable child = (ContinuousVariable) nodes.get(mixedIndex);
            ArrayList<ContinuousVariable> continuousParents = new ArrayList<>();
            for (Node node : G.getParents(child)) {
                continuousParents.add((ContinuousVariable) node);
            }

            HashMap<String, double[]> intercept = new HashMap<>();
            HashMap<String, double[]> linear = new HashMap<>();
            HashMap<String, double[]> beta = new HashMap<>();
            HashMap<String, double[]> gamma = new HashMap<>();
            HashMap<String, double[]> bounds = new HashMap<>();

            for (int j = 1; j <= continuousParents.size(); j++) {
                String key = continuousParents.get(j - 1).toString();
                if (!bounds.containsKey(key)) {
                    double m0 = mixedData.getDouble(0, mixedData.getColumn(continuousParents.get(j - 1)));
                    double m1 = mixedData.getDouble(0, mixedData.getColumn(continuousParents.get(j - 1)));
                    for (int i = 1; i < parameters.getInt(Params.SAMPLE_SIZE); i++) {
                        m0 = FastMath.min(m0, mixedData.getDouble(i, mixedData.getColumn(continuousParents.get(j - 1))));
                        m1 = FastMath.max(m1, mixedData.getDouble(i, mixedData.getColumn(continuousParents.get(j - 1))));
                    }
                    double[] temp = new double[3];
                    temp[0] = m0;
                    temp[1] = (m1 - m0) / 2;
                    temp[2] = m1;
                    bounds.put(key, temp);
                }
            }

            double mean = 0;
            double var = 0;

            for (int i = 0; i < parameters.getInt(Params.SAMPLE_SIZE); i++) {

                double[] parents = new double[continuousParents.size()];
                double value = 0;
                final String key = "";

                for (int j = 1; j <= continuousParents.size(); j++)
                    parents[j - 1] = mixedData.getDouble(i, mixedData.getColumn(continuousParents.get(j - 1)));

                if (!intercept.containsKey(key)) {
                    double[] interceptCoefficients = new double[1];
                    interceptCoefficients[0] = randSign() * RandomUtil.getInstance().nextUniform(this.interceptLow, this.interceptHigh);
                    intercept.put(key, interceptCoefficients);
                }

                if (!linear.containsKey(key) && !continuousParents.isEmpty()) {
                    double[] linearCoefficients = new double[parents.length];
                    for (int j = 0; j < parents.length; j++)
                        linearCoefficients[j] = randSign() * RandomUtil.getInstance().nextUniform(this.linearLow, this.linearHigh);
                    linear.put(key, linearCoefficients);
                }

                if (!beta.containsKey(key) && !continuousParents.isEmpty()) {
                    double[] betaCoefficients = new double[parents.length];
                    for (int j = 0; j < parents.length; j++)
                        betaCoefficients[j] = randSign() * RandomUtil.getInstance().nextUniform(this.betaLow, this.betaHigh);
                    beta.put(key, betaCoefficients);
                }

                if (!gamma.containsKey(key) && !continuousParents.isEmpty()) {
                    double[] gammaCoefficients = new double[parents.length];
                    for (int j = 0; j < parents.length; j++) {
                        String key2 = continuousParents.get(j).toString();
                        gammaCoefficients[j] = (bounds.get(key2)[1] - bounds.get(key2)[0]) / (2 * FastMath.PI * RandomUtil.getInstance().nextUniform(this.gammaLow, this.gammaHigh));
                    }
                    gamma.put(key, gammaCoefficients);
                }

                value += intercept.get(key)[0];
                if (!continuousParents.isEmpty()) {
                    for (int x = 0; x < parents.length; x++) {
                        value += linear.get(key)[x] * parents[x] + beta.get(key)[x] * FastMath.sin(parents[x] / (gamma.get(key)[x]));
                    }
                }

                mixedData.setDouble(i, mixedIndex, value);

                mean += value;
                var += FastMath.pow(value, 2);
            }
            if (continuousParents.size() == 0) {
                var = 1;
            } else {
                mean /= mixedData.getNumRows();
                var /= mixedData.getNumRows();
                var -= FastMath.pow(mean, 2);
                var = FastMath.sqrt(var);
            }

            double noiseVar = RandomUtil.getInstance().nextUniform(this.varLow, this.varHigh);
            for (int i = 0; i < parameters.getInt(Params.SAMPLE_SIZE); i++) {
                mixedData.setDouble(i, mixedIndex, mixedData.getDouble(i, mixedIndex) + var * RandomUtil.getInstance().nextNormal(0, noiseVar));
            }
        }
        return mixedData;
    }

    public void setInterceptLow(double interceptLow) {
        this.interceptLow = interceptLow;
    }

    public void setInterceptHigh(double interceptHigh) {
        this.interceptHigh = interceptHigh;
    }

    public void setLinearLow(double linearLow) {
        this.linearLow = linearLow;
    }

    public void setLinearHigh(double linearHigh) {
        this.linearHigh = linearHigh;
    }

    public void setVarLow(double varLow) {
        this.varLow = varLow;
    }

    public void setVarHigh(double varHigh) {
        this.varHigh = varHigh;
    }

    public void setBetaLow(double betaLow) {
        this.betaLow = betaLow;
    }

    public void setBetaHigh(double betaHigh) {
        this.betaHigh = betaHigh;
    }

    public void setGammaLow(double gammaLow) {
        this.gammaLow = gammaLow;
    }

    public void setGammaHigh(double gammaHigh) {
        this.gammaHigh = gammaHigh;
    }

    private int randSign() {
        return RandomUtil.getInstance().nextInt(2) * 2 - 1;
    }

}
