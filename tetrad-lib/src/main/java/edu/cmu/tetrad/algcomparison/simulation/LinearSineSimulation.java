package edu.cmu.tetrad.algcomparison.simulation;

import edu.cmu.tetrad.algcomparison.graph.RandomGraph;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.RandomUtil;

import java.util.*;

/**
 * A simulation method based on the mixed variable polynomial assumption.
 *
 * @author Bryan Andrews
 */
public class LinearSineSimulation implements Simulation {
    static final long serialVersionUID = 23L;
    private RandomGraph randomGraph;
    private List<DataSet> dataSets = new ArrayList<>();
    private List<Graph> graphs = new ArrayList<>();
    private DataType dataType;
    private List<Node> shuffledOrder;

    private double interceptLow = 0;
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

    @Override
    public void createData(Parameters parameters) {
        setInterceptLow(parameters.getDouble("interceptLow"));
        setInterceptHigh(parameters.getDouble("interceptHigh"));
        setLinearLow(parameters.getDouble("linearLow"));
        setLinearHigh(parameters.getDouble("linearHigh"));
        setVarLow(parameters.getDouble("varLow"));
        setVarHigh(parameters.getDouble("varHigh"));
        setBetaLow(parameters.getDouble("betaLow"));
        setBetaHigh(parameters.getDouble("betaHigh"));
        setGammaLow(parameters.getDouble("gammaLow"));
        setGammaHigh(parameters.getDouble("gammaHigh"));

        this.dataType = DataType.Continuous;

        this.shuffledOrder = null;

        Graph graph = randomGraph.createGraph(parameters);

        dataSets = new ArrayList<>();
        graphs = new ArrayList<>();

        for (int i = 0; i < parameters.getInt("numRuns"); i++) {
            System.out.println("Simulating dataset #" + (i + 1));

            if (parameters.getBoolean("differentGraphs") && i > 0) graph = randomGraph.createGraph(parameters);

            graphs.add(graph);

            DataSet dataSet = simulate(graph, parameters);
            dataSet.setName("" + (i + 1));
            dataSets.add(dataSet);
        }
    }

    @Override
    public Graph getTrueGraph(int index) {
        return graphs.get(index);
    }

    @Override
    public DataModel getDataModel(int index) {
        return dataSets.get(index);
    }

    @Override
    public String getDescription() {
        return "Linear-sine simulation using " + randomGraph.getDescription();
    }

    @Override
    public List<String> getParameters() {
        List<String> parameters = randomGraph.getParameters();
        parameters.add("numRuns");
        parameters.add("differentGraphs");
        parameters.add("sampleSize");
        parameters.add("interceptLow");
        parameters.add("interceptHigh");
        parameters.add("linearLow");
        parameters.add("linearHigh");
        parameters.add("varLow");
        parameters.add("varHigh");
        parameters.add("betaLow");
        parameters.add("betaHigh");
        parameters.add("gammaLow");
        parameters.add("gammaHigh");
        return parameters;
    }

    @Override
    public int getNumDataModels() {
        return dataSets.size();
    }

    @Override
    public DataType getDataType() {
        return dataType;
    }

    private DataSet simulate(Graph G, Parameters parameters) {
        HashMap<String, Integer> nd = new HashMap<>();

        List<Node> nodes = G.getNodes();

        Collections.shuffle(nodes);

        if (this.shuffledOrder == null) {
            List<Node> shuffledNodes = new ArrayList<>(nodes);
            Collections.shuffle(shuffledNodes);
            this.shuffledOrder = shuffledNodes;
        }

        for (int i = 0; i < nodes.size(); i++) {
            nd.put(shuffledOrder.get(i).getName(), 0);
        }

        G = makeMixedGraph(G, nd);
        nodes = G.getNodes();

        DataSet mixedData = new BoxDataSet(new MixedDataBox(nodes, parameters.getInt("sampleSize")), nodes);

        List<Node> tierOrdering = G.getCausalOrdering();
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
                    for (int i = 1; i < parameters.getInt("sampleSize"); i++) {
                        m0 = Math.min(m0, mixedData.getDouble(i, mixedData.getColumn(continuousParents.get(j - 1))));
                        m1 = Math.max(m1, mixedData.getDouble(i, mixedData.getColumn(continuousParents.get(j - 1))));
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

            for (int i = 0; i < parameters.getInt("sampleSize"); i++) {

                double[] parents = new double[continuousParents.size()];
                double value = 0;
                String key = "";

                for (int j = 1; j <= continuousParents.size(); j++)
                    parents[j - 1] = mixedData.getDouble(i, mixedData.getColumn(continuousParents.get(j - 1)));

                if (!intercept.containsKey(key)) {
                    double[] interceptCoefficients = new double[1];
                    interceptCoefficients[0] = randSign() * RandomUtil.getInstance().nextUniform(interceptLow, interceptHigh);
                    intercept.put(key, interceptCoefficients);
                }

                if (!linear.containsKey(key) && !continuousParents.isEmpty()) {
                    double[] linearCoefficients = new double[parents.length];
                    for (int j = 0; j < parents.length; j++)
                        linearCoefficients[j] = randSign() * RandomUtil.getInstance().nextUniform(linearLow, linearHigh);
                    linear.put(key, linearCoefficients);
                }

                if (!beta.containsKey(key) && !continuousParents.isEmpty()) {
                    double[] betaCoefficients = new double[parents.length];
                    for (int j = 0; j < parents.length; j++)
                        betaCoefficients[j] = randSign() * RandomUtil.getInstance().nextUniform(betaLow, betaHigh);
                    beta.put(key, betaCoefficients);
                }

                if (!gamma.containsKey(key) && !continuousParents.isEmpty()) {
                    double[] gammaCoefficients = new double[parents.length];
                    for (int j = 0; j < parents.length; j++) {
                        String key2 = continuousParents.get(j).toString();
                        gammaCoefficients[j] = (bounds.get(key2)[1] - bounds.get(key2)[0]) / (2 * Math.PI * RandomUtil.getInstance().nextUniform(gammaLow, gammaHigh));
                    }
                    gamma.put(key, gammaCoefficients);
                }

                value += intercept.get(key)[0];
                if (!continuousParents.isEmpty()) {
                    for (int x = 0; x < parents.length; x++) {
                        String key2 = continuousParents.get(x).toString();
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

            double noiseVar = RandomUtil.getInstance().nextUniform(varLow, varHigh);
            for (int i = 0; i < parameters.getInt("sampleSize"); i++) {
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

    public void setLinearLow(double linearLow){
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

    private int randSign() { return RandomUtil.getInstance().nextInt(2)*2-1; }

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

}
