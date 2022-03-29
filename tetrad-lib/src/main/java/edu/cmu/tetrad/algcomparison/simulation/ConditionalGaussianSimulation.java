package edu.cmu.tetrad.algcomparison.simulation;

import edu.cmu.tetrad.algcomparison.graph.RandomGraph;
import edu.cmu.tetrad.bayes.BayesIm;
import edu.cmu.tetrad.bayes.BayesPm;
import edu.cmu.tetrad.bayes.MlBayesIm;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.sem.ParamType;
import edu.cmu.tetrad.sem.Parameter;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.cmu.tetrad.util.RandomUtil;
import org.apache.commons.lang3.RandomUtils;

import java.util.*;

/**
 * A simulation method based on the conditional Gaussian assumption.
 *
 * @author jdramsey
 */
public class ConditionalGaussianSimulation implements Simulation {

    static final long serialVersionUID = 23L;
    private final RandomGraph randomGraph;
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

    public ConditionalGaussianSimulation(RandomGraph graph) {
        this.randomGraph = graph;
    }

    @Override
    public void createData(Parameters parameters, boolean newModel) {
//        if (!newModel && !dataSets.isEmpty()) return;

        setVarLow(parameters.getDouble(Params.VAR_LOW));
        setVarHigh(parameters.getDouble(Params.VAR_HIGH));
        setCoefLow(parameters.getDouble(Params.COEF_LOW));
        setCoefHigh(parameters.getDouble(Params.COEF_HIGH));
        setCoefSymmetric(parameters.getBoolean(Params.COV_SYMMETRIC));
        setMeanLow(parameters.getDouble(Params.MEAN_LOW));
        setMeanHigh(parameters.getDouble(Params.MEAN_HIGH));

        double percentDiscrete = parameters.getDouble(Params.PERCENT_DISCRETE);

        boolean discrete = parameters.getString(Params.DATA_TYPE).equals("discrete");
        boolean continuous = parameters.getString(Params.DATA_TYPE).equals("continuous");

        if (discrete && percentDiscrete != 100.0) {
            throw new IllegalArgumentException("To simulate discrete data, 'percentDiscrete' must be set to 0.0.");
        } else if (continuous && percentDiscrete != 0.0) {
            throw new IllegalArgumentException("To simulate continuoue data, 'percentDiscrete' must be set to 100.0.");
        }

        if (discrete) {
            this.dataType = DataType.Discrete;
        }
        if (continuous) {
            this.dataType = DataType.Continuous;
        }

        this.shuffledOrder = null;

        Graph graph = this.randomGraph.createGraph(parameters);

        this.dataSets = new ArrayList<>();
        this.graphs = new ArrayList<>();

        for (int i = 0; i < parameters.getInt(Params.NUM_RUNS); i++) {
            System.out.println("Simulating dataset #" + (i + 1));

            if (parameters.getBoolean(Params.DIFFERENT_GRAPHS) && i > 0) {
                graph = this.randomGraph.createGraph(parameters);
            }

            this.graphs.add(graph);

            DataSet dataSet = simulate(graph, parameters);
            dataSet.setName("" + (i + 1));

            if (parameters.getBoolean(Params.RANDOMIZE_COLUMNS)) {
                dataSet = DataUtils.shuffleColumns(dataSet);
            }

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
        return "Conditional Gaussian simulation using " + this.randomGraph.getDescription();
    }

    @Override
    public List<String> getParameters() {
        List<String> parameters = this.randomGraph.getParameters();
        parameters.add(Params.MIN_CATEGORIES);
        parameters.add(Params.MAX_CATEGORIES);
        parameters.add(Params.PERCENT_DISCRETE);
        parameters.add(Params.NUM_RUNS);
        parameters.add(Params.DIFFERENT_GRAPHS);
        parameters.add(Params.SAMPLE_SIZE);
        parameters.add(Params.VAR_LOW);
        parameters.add(Params.VAR_HIGH);
        parameters.add(Params.COEF_LOW);
        parameters.add(Params.COEF_HIGH);
        parameters.add(Params.COV_SYMMETRIC);
        parameters.add(Params.MEAN_LOW);
        parameters.add(Params.MEAN_HIGH);
        parameters.add(Params.SAVE_LATENT_VARS);
        parameters.add(Params.RANDOMIZE_COLUMNS);

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

        Collections.shuffle(nodes);

        if (this.shuffledOrder == null) {
            List<Node> shuffledNodes = new ArrayList<>(nodes);
            Collections.shuffle(shuffledNodes);
            this.shuffledOrder = shuffledNodes;
        }

        for (int i = 0; i < nodes.size(); i++) {
            if (i < nodes.size() * parameters.getDouble(Params.PERCENT_DISCRETE) * 0.01) {
                int minNumCategories = parameters.getInt(Params.MIN_CATEGORIES);
                int maxNumCategories = parameters.getInt(Params.MAX_CATEGORIES);
                int value = pickNumCategories(minNumCategories, maxNumCategories);
                nd.put(this.shuffledOrder.get(i).getName(), value);
            } else {
                nd.put(this.shuffledOrder.get(i).getName(), 0);
            }
        }

        G = ConditionalGaussianSimulation.makeMixedGraph(G, nd);
        nodes = G.getNodes();

        DataSet mixedData = new BoxDataSet(new MixedDataBox(nodes, parameters.getInt(Params.SAMPLE_SIZE)), nodes);

        List<Node> X = new ArrayList<>();
        List<Node> A = new ArrayList<>();

        for (Node node : G.getNodes()) {
            if (node instanceof ContinuousVariable) {
                X.add(node);
            } else {
                A.add(node);
            }
        }

        Graph AG = G.subgraph(A);
        Graph XG = G.subgraph(X);

        Map<ContinuousVariable, DiscreteVariable> erstatzNodes = new HashMap<>();
        Map<String, ContinuousVariable> erstatzNodesReverse = new HashMap<>();

        for (Node y : A) {
            for (Node x : G.getParents(y)) {
                if (x instanceof ContinuousVariable) {
                    DiscreteVariable ersatz = erstatzNodes.get(x);

                    if (ersatz == null) {
                        ersatz = new DiscreteVariable("Ersatz_" + x.getName(), RandomUtil.getInstance().nextInt(3) + 2);
                        erstatzNodes.put((ContinuousVariable) x, ersatz);
                        erstatzNodesReverse.put(ersatz.getName(), (ContinuousVariable) x);
                        AG.addNode(ersatz);
                    }

                    AG.addDirectedEdge(ersatz, y);
                }
            }
        }

        BayesPm bayesPm = new BayesPm(AG);
        BayesIm bayesIm = new MlBayesIm(bayesPm, MlBayesIm.RANDOM);

        SemPm semPm = new SemPm(XG);

        Map<Combination, Double> paramValues = new HashMap<>();

        List<Node> tierOrdering = G.getCausalOrdering();

        int[] tiers = new int[tierOrdering.size()];

        for (int t = 0; t < tierOrdering.size(); t++) {
            tiers[t] = nodes.indexOf(tierOrdering.get(t));
        }

        Map<Integer, double[]> breakpointsMap = new HashMap<>();

        for (int mixedIndex : tiers) {
            for (int i = 0; i < parameters.getInt(Params.SAMPLE_SIZE); i++) {
                if (nodes.get(mixedIndex) instanceof DiscreteVariable) {
                    int bayesIndex = bayesIm.getNodeIndex(nodes.get(mixedIndex));

                    int[] bayesParents = bayesIm.getParents(bayesIndex);
                    int[] parentValues = new int[bayesParents.length];

                    for (int k = 0; k < parentValues.length; k++) {
                        int bayesParentColumn = bayesParents[k];

                        Node bayesParent = bayesIm.getVariables().get(bayesParentColumn);
                        DiscreteVariable _parent = (DiscreteVariable) bayesParent;
                        int value;

                        ContinuousVariable orig = erstatzNodesReverse.get(_parent.getName());

                        if (orig != null) {
                            int mixedParentColumn = mixedData.getColumn(orig);
                            double d = mixedData.getDouble(i, mixedParentColumn);
                            double[] breakpoints = breakpointsMap.get(mixedParentColumn);

                            if (breakpoints == null) {
                                breakpoints = getBreakpoints(mixedData, _parent, mixedParentColumn);
                                breakpointsMap.put(mixedParentColumn, breakpoints);
                            }

                            value = breakpoints.length;

                            for (int j = 0; j < breakpoints.length; j++) {
                                if (d < breakpoints[j]) {
                                    value = j;
                                    break;
                                }
                            }
                        } else {
                            int mixedColumn = mixedData.getColumn(bayesParent);
                            value = mixedData.getInt(i, mixedColumn);
                        }

                        parentValues[k] = value;
                    }

                    int rowIndex = bayesIm.getRowIndex(bayesIndex, parentValues);
                    double sum = 0.0;

                    double r = RandomUtil.getInstance().nextDouble();
                    mixedData.setInt(i, mixedIndex, 0);

                    for (int k = 0; k < bayesIm.getNumColumns(bayesIndex); k++) {
                        double probability = bayesIm.getProbability(bayesIndex, rowIndex, k);
                        sum += probability;

                        if (sum >= r) {
                            mixedData.setInt(i, mixedIndex, k);
                            break;
                        }
                    }
                } else {
                    Node y = nodes.get(mixedIndex);

                    Set<DiscreteVariable> discreteParents = new HashSet<>();
                    Set<ContinuousVariable> continuousParents = new HashSet<>();

                    for (Node node : G.getParents(y)) {
                        if (node instanceof DiscreteVariable) {
                            discreteParents.add((DiscreteVariable) node);
                        } else {
                            continuousParents.add((ContinuousVariable) node);
                        }
                    }

                    Parameter varParam = semPm.getParameter(y, y);
                    Parameter muParam = semPm.getMeanParameter(y);

                    Combination varComb = new Combination(varParam);
                    Combination muComb = new Combination(muParam);

                    for (DiscreteVariable v : discreteParents) {
                        varComb.addParamValue(v, mixedData.getInt(i, mixedData.getColumn(v)));
                        muComb.addParamValue(v, mixedData.getInt(i, mixedData.getColumn(v)));
                    }

                    double value = RandomUtil.getInstance().nextNormal(0, getParamValue(varComb, paramValues));

                    for (Node x : continuousParents) {
                        Parameter coefParam = semPm.getParameter(x, y);
                        Combination coefComb = new Combination(coefParam);

                        for (DiscreteVariable v : discreteParents) {
                            coefComb.addParamValue(v, mixedData.getInt(i, mixedData.getColumn(v)));
                        }

                        int parent = nodes.indexOf(x);
                        double parentValue = mixedData.getDouble(i, parent);
                        double parentCoef = getParamValue(coefComb, paramValues);
                        value += parentValue * parentCoef;
                    }

                    value += getParamValue(muComb, paramValues);
                    mixedData.setDouble(i, mixedIndex, value);
                }
            }
        }

        boolean saveLatentVars = parameters.getBoolean(Params.SAVE_LATENT_VARS);
        return saveLatentVars ? mixedData : DataUtils.restrictToMeasured(mixedData);
    }

    private double[] getBreakpoints(DataSet mixedData, DiscreteVariable _parent, int mixedParentColumn) {
        double[] data = new double[mixedData.getNumRows()];

        for (int r = 0; r < mixedData.getNumRows(); r++) {
            data[r] = mixedData.getDouble(r, mixedParentColumn);
        }

        return Discretizer.getEqualFrequencyBreakPoints(data, _parent.getNumCategories());
    }

    private Double getParamValue(Combination values, Map<Combination, Double> map) {
        Double d = map.get(values);

        if (d == null) {
            Parameter parameter = values.getParameter();

            if (parameter.getType() == ParamType.VAR) {
                d = RandomUtil.getInstance().nextUniform(this.varLow, this.varHigh);
                map.put(values, d);
            } else if (parameter.getType() == ParamType.COEF) {
                double min = this.coefLow;
                double max = this.coefHigh;
                double value = RandomUtil.getInstance().nextUniform(min, max);
                d = RandomUtil.getInstance().nextUniform(0, 1) < 0.5 && this.coefSymmetric ? -value : value;
                map.put(values, d);
            } else if (parameter.getType() == ParamType.MEAN) {
                d = RandomUtil.getInstance().nextUniform(this.meanLow, this.meanHigh);
                map.put(values, d);
            }
        }

        return d;
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

    private class Combination {

        private final Parameter parameter;
        private final Set<VariableValues> paramValues;

        public Combination(Parameter parameter) {
            this.parameter = parameter;
            this.paramValues = new HashSet<>();
        }

        public void addParamValue(DiscreteVariable variable, int value) {
            this.paramValues.add(new VariableValues(variable, value));
        }

        public int hashCode() {
            return this.parameter.hashCode() + this.paramValues.hashCode();
        }

        public boolean equals(Object o) {
            if (o == this) {
                return true;
            }
            if (!(o instanceof Combination)) {
                return false;
            }
            Combination v = (Combination) o;
            return v.parameter == this.parameter && v.paramValues.equals(this.paramValues);
        }

        public Parameter getParameter() {
            return this.parameter;
        }
    }

    private class VariableValues {

        private final DiscreteVariable variable;
        private final int value;

        public VariableValues(DiscreteVariable variable, int value) {
            this.variable = variable;
            this.value = value;
        }

        public DiscreteVariable getVariable() {
            return this.variable;
        }

        public int getValue() {
            return this.value;
        }

        public int hashCode() {
            return this.variable.hashCode() + this.value;
        }

        public boolean equals(Object o) {
            if (o == this) {
                return true;
            }
            if (!(o instanceof VariableValues)) {
                return false;
            }
            VariableValues v = (VariableValues) o;
            return v.variable.equals(this.variable) && v.value == this.value;
        }
    }

    private static Graph makeMixedGraph(Graph g, Map<String, Integer> m) {
        List<Node> nodes = g.getNodes();
        for (int i = 0; i < nodes.size(); i++) {
            Node n = nodes.get(i);
            int nL = m.get(n.getName());
            if (nL > 0) {
                Node nNew = new DiscreteVariable(n.getName(), nL);
                nNew.setNodeType(n.getNodeType());
                nodes.set(i, nNew);
            } else {
                Node nNew = new ContinuousVariable(n.getName());
                nNew.setNodeType(n.getNodeType());
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
