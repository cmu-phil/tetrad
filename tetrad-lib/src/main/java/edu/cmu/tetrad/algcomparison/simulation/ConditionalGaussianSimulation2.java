package edu.cmu.tetrad.algcomparison.simulation;

import edu.cmu.tetrad.algcomparison.graph.RandomGraph;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.AdLeafTree;
import edu.cmu.tetrad.search.AdTrees;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.RandomUtil;
import org.apache.commons.lang3.RandomUtils;

import java.util.*;

/**
 * A simulation method based on the conditional Gaussian assumption.
 *
 * @author jdramsey
 */
public class ConditionalGaussianSimulation2 implements Simulation {
    static final long serialVersionUID = 23L;
    private RandomGraph randomGraph;
    private List<DataSet> dataSets = new ArrayList<>();
    private List<Graph> graphs = new ArrayList<>();
    private DataType dataType;
    private List<Node> shuffledOrder;
    private double varLow = 1;
    private double varHigh = 3;
    private double coefLow = 0.05;
    private double coefHigh = 1.5;
    private boolean coefSymmetric = true;
    private double meanLow = -1;
    private double meanHigh = 1;

    public ConditionalGaussianSimulation2(RandomGraph graph) {
        this.randomGraph = graph;
    }

    @Override
    public void createData(Parameters parameters) {
        setVarLow(parameters.getDouble("varLow"));
        setVarHigh(parameters.getDouble("varHigh"));
        setCoefLow(parameters.getDouble("coefLow"));
        setCoefHigh(parameters.getDouble("coefHigh"));
        setCoefSymmetric(parameters.getBoolean("coefSymmetric"));
        setMeanLow(parameters.getDouble("meanLow"));
        setMeanHigh(parameters.getDouble("meanHigh"));

        double percentDiscrete = parameters.getDouble("percentDiscrete");

        boolean discrete = parameters.getString("dataType").equals("discrete");
        boolean continuous = parameters.getString("dataType").equals("continuous");

        if (discrete && percentDiscrete != 100.0) {
            throw new IllegalArgumentException("To simulate discrete data, 'percentDiscrete' must be set to 0.0.");
        } else if (continuous && percentDiscrete != 0.0) {
            throw new IllegalArgumentException("To simulate continuoue data, 'percentDiscrete' must be set to 100.0.");
        }

        if (discrete) this.dataType = DataType.Discrete;
        if (continuous) this.dataType = DataType.Continuous;

        this.shuffledOrder = null;

        Graph graph = randomGraph.createGraph(parameters);

        dataSets = new ArrayList<>();
        graphs = new ArrayList<>();

        for (int i = 0; i < parameters.getInt("numRuns"); i++) {
            System.out.println("Simulating dataset #" + (i + 1));

            if (parameters.getBoolean("differentGraphs") && i > 0) {
                graph = randomGraph.createGraph(parameters);
            }

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
    public DataSet getDataSet(int index) {
        return dataSets.get(index);
    }

    @Override
    public String getDescription() {
        return "Conditional Gaussian simulation using " + randomGraph.getDescription();
    }

    @Override
    public List<String> getParameters() {
        List<String> parameters = randomGraph.getParameters();
        parameters.add("minCategories");
        parameters.add("maxCategories");
        parameters.add("percentDiscrete");
        parameters.add("numRuns");
        parameters.add("differentGraphs");
        parameters.add("sampleSize");
        parameters.add("varLow");
        parameters.add("varHigh");
        parameters.add("coefLow");
        parameters.add("coefHigh");
        parameters.add("coefSymmetric");
        parameters.add("meanLow");
        parameters.add("meanHigh");
        return parameters;
    }

    @Override
    public int getNumDataSets() {
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
            if (i < nodes.size() * parameters.getDouble("percentDiscrete") * 0.01) {
                final int minNumCategories = parameters.getInt("minCategories");
                final int maxNumCategories = parameters.getInt("maxCategories");
                final int value = pickNumCategories(minNumCategories, maxNumCategories);
                nd.put(shuffledOrder.get(i).getName(), value);
            } else {
                nd.put(shuffledOrder.get(i).getName(), 0);
            }
        }

        G = makeMixedGraph(G, nd);
        nodes = G.getNodes();

        DataSet mixedData = new BoxDataSet(new MixedDataBox(nodes, parameters.getInt("sampleSize")), nodes);
        AdLeafTree adTree = AdTrees.getAdLeafTree(mixedData);

        List<Node> tierOrdering = G.getCausalOrdering();

        for (Node y : tierOrdering) {
            List<ContinuousVariable> continuousParents = new ArrayList<>();
            List<DiscreteVariable> discreteParents = new ArrayList<>();

            for (Node node : G.getParents(y)) {
                if (node instanceof DiscreteVariable) {
                    discreteParents.add((DiscreteVariable) node);
                } else {
                    continuousParents.add((ContinuousVariable) node);
                }
            }

            int mixedIndex = mixedData.getColumn(y);

            List<List<Integer>> leaves = adTree.getCellLeaves(new ArrayList<>(discreteParents));

            for (List<Integer> leaf : leaves) {
                if (y instanceof ContinuousVariable) {
                    double[] coefs = new double[continuousParents.size()];
                    double mu = getRandomMean();
                    double var = getRandomVariance();

                    for (int j = 0; j < continuousParents.size(); j++) {
                        coefs[j] = getRandomCoef();
                    }

                    for (int i : leaf) {
                        double value = getLinearPrediction(mixedData, continuousParents, i, coefs, var, mu);
                        mixedData.setDouble(i, mixedIndex, value);
                    }
                } else if (y instanceof DiscreteVariable) {
                    DiscreteVariable Y = (DiscreteVariable) y;
                    int dim = Y.getNumCategories();
                    double[] p = new double[dim];

                    if (!continuousParents.isEmpty()) {
                        double[] coefs = new double[continuousParents.size()];

                        for (int j = 0; j < continuousParents.size(); j++) {
                            coefs[j] = getRandomCoef();
                        }

                        double mu = getRandomMean();
                        double var = 0.0;//getRandomVariance();

                        for (int i : leaf) {
                            double sumP = 0.0;

                            for (int c = 0; c < dim - 1; c++) {
                                double value = getLinearPrediction(mixedData, continuousParents, i, coefs, var, mu);
                                p[c] = Math.exp(value) * RandomUtil.getInstance().nextDouble();
                                sumP += p[c];
                            }

                            p[dim - 1] = 1;
                            sumP += p[dim - 1];

                            for (int c = 0; c < dim; c++) {
                                p[c] /= sumP;
                            }

                            double sum = 0.0;

                            double r = RandomUtil.getInstance().nextDouble();

                            for (int c = 0; c < dim; c++) {
                                sum += p[c];

                                if (sum >= r) {
                                    mixedData.setInt(i, mixedIndex, c);
                                    break;
                                }
                            }
                        }
                    } else {
                        double sumP = 0.0;

                        for (int c = 0; c < dim; c++) {
                            p[c] = RandomUtil.getInstance().nextDouble();
                            sumP += p[c];
                        }

                        for (int c = 0; c < dim; c++) {
                            p[c] /= sumP;
                        }

                        for (int i : leaf) {
                            double sum = 0.0;

                            double r = RandomUtil.getInstance().nextDouble();

                            for (int c = 0; c < dim; c++) {
                                sum += p[c];

                                if (sum >= r) {
                                    mixedData.setInt(i, mixedIndex, c);
                                    break;
                                }
                            }
                        }
                    }
                }
            }

            if (y instanceof DiscreteVariable) {
                int[] col = new int[mixedData.getNumRows()];

                for (int i = 0; i < mixedData.getNumRows(); i++) {
                    col[i] = mixedData.getInt(i, mixedIndex);
                }

                adTree.setColumn((DiscreteVariable) y, col);
            }
        }

        return mixedData;
    }

    private double getLinearPrediction(DataSet mixedData,
                                       List<ContinuousVariable> continuousParents,
                                       int i, double[] coefs, double var, double mu) {
        double value = var == 0 ? 0 : RandomUtil.getInstance().nextNormal(0, Math.sqrt(var));

        for (int j = 0; j < continuousParents.size(); j++) {
            int parent = mixedData.getColumn(continuousParents.get(j));
            double parentValue = mixedData.getDouble(i, parent);
            double parentCoef = coefs[j];
            value += parentValue * parentCoef;
        }

        value += mu;

        return value;
    }

    private Double getRandomVariance() {
        return RandomUtil.getInstance().nextUniform(varLow, varHigh);
    }


    private Double getRandomMean() {
        return RandomUtil.getInstance().nextUniform(meanLow, meanHigh);
    }


    private Double getRandomCoef() {
        double min = coefLow;
        double max = coefHigh;
        double value = RandomUtil.getInstance().nextUniform(min, max);
        return RandomUtil.getInstance().nextUniform(0, 1) < 0.5 && coefSymmetric ? -value : value;
    }

    public void setVarLow(double varLow) {
        this.varLow = varLow;
    }

    public void setVarHigh(double varHigh) {
        this.varHigh = varHigh;
    }

    public void setCoefLow(double coefLow) {
        this.coefLow = coefLow;
    }

    public void setCoefHigh(double coefHigh) {
        this.coefHigh = coefHigh;
    }

    public void setCoefSymmetric(boolean coefSymmetric) {
        this.coefSymmetric = coefSymmetric;
    }

    public void setMeanLow(double meanLow) {
        this.meanLow = meanLow;
    }

    public void setMeanHigh(double meanHigh) {
        this.meanHigh = meanHigh;
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

    private int pickNumCategories(int min, int max) {
        return RandomUtils.nextInt(min, max + 1);
    }
}
