package edu.cmu.tetrad.algcomparison.simulation;

import edu.cmu.tetrad.algcomparison.graph.RandomGraph;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.sem.GeneralizedSemIm;
import edu.cmu.tetrad.sem.GeneralizedSemPm;
import edu.cmu.tetrad.sem.TemplateExpander;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.cmu.tetrad.util.RandomUtil;
import edu.cmu.tetrad.util.StatUtils;
import org.apache.commons.math3.util.FastMath;

import java.io.Serial;
import java.text.ParseException;
import java.util.*;

/**
 * A version of the Lee and Hastic simulation.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class LeeHastieSimulation implements Simulation {

    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The random graph.
     */
    private final RandomGraph randomGraph;

    /**
     * The data sets.
     */
    private List<DataSet> dataSets = new ArrayList<>();

    /**
     * The graphs.
     */
    private List<Graph> graphs = new ArrayList<>();

    /**
     * The data type.
     */
    private DataType dataType;

    /**
     * The shuffled order.
     */
    private List<Node> shuffledOrder;

    /**
     * <p>Constructor for LeeHastieSimulation.</p>
     *
     * @param graph a {@link edu.cmu.tetrad.algcomparison.graph.RandomGraph} object
     */
    public LeeHastieSimulation(RandomGraph graph) {
        this.randomGraph = graph;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void createData(Parameters parameters, boolean newModel) {
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
            if (parameters.getBoolean(Params.DIFFERENT_GRAPHS) && i > 0) {
                graph = this.randomGraph.createGraph(parameters);
            }

            this.graphs.add(graph);

            DataSet dataSet = simulate(graph, parameters);
            dataSet.setName("" + (i + 1));

            if (parameters.getBoolean(Params.RANDOMIZE_COLUMNS)) {
                dataSet = DataTransforms.shuffleColumns(dataSet);
            }

            if (parameters.getDouble(Params.PROB_REMOVE_COLUMN) > 0) {
                double aDouble = parameters.getDouble(Params.PROB_REMOVE_COLUMN);
                dataSet = DataTransforms.removeRandomColumns(dataSet, aDouble);
            }

            this.dataSets.add(dataSet);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Graph getTrueGraph(int index) {
        return this.graphs.get(index);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DataModel getDataModel(int index) {
        return this.dataSets.get(index);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription() {
        return "Lee & Hastie simulation using " + this.randomGraph.getDescription();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getShortName() {
        return "Lee & Hastie Simulation";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> getParameters() {
        List<String> parameters = this.randomGraph.getParameters();
        parameters.add(Params.MIN_CATEGORIES);
        parameters.add(Params.MAX_CATEGORIES);
        parameters.add(Params.PERCENT_DISCRETE);
        parameters.add(Params.NUM_RUNS);
        parameters.add(Params.PROB_REMOVE_COLUMN);
        parameters.add(Params.DIFFERENT_GRAPHS);
        parameters.add(Params.RANDOMIZE_COLUMNS);
        parameters.add(Params.SAMPLE_SIZE);
        parameters.add(Params.SAVE_LATENT_VARS);
        parameters.add(Params.VERBOSE);

        return parameters;
    }

    @Override
    public Class<? extends RandomGraph> getRandomGraphClass() {
        return randomGraph.getClass();
    }

    @Override
    public Class<? extends Simulation> getSimulationClass() {
        return getClass();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getNumDataModels() {
        return this.dataSets.size();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DataType getDataType() {
        return this.dataType;
    }

    private DataSet simulate(Graph dag, Parameters parameters) {
        HashMap<String, Integer> nd = new HashMap<>();

        List<Node> nodes = dag.getNodes();

        List<Node> shuffledNodes = new ArrayList<>(nodes);
        RandomUtil.shuffle(shuffledNodes);

        if (this.shuffledOrder == null) {
            this.shuffledOrder = shuffledNodes;
        }

        for (int i = 0; i < this.shuffledOrder.size(); i++) {
            if (i < this.shuffledOrder.size() * parameters.getDouble(Params.PERCENT_DISCRETE) * 0.01) {
                int minNumCategories = parameters.getInt(Params.MIN_CATEGORIES);
                int maxNumCategories = parameters.getInt(Params.MAX_CATEGORIES);
                int value = pickNumCategories(minNumCategories, maxNumCategories);
                nd.put(this.shuffledOrder.get(i).getName(), value);
            } else {
                nd.put(this.shuffledOrder.get(i).getName(), 0);
            }
        }

        Graph graph = makeMixedGraph(dag, nd);

        GeneralizedSemPm pm = GaussianCategoricalPm(graph, "Split(-1.0,-.0,.0,1.0)");
        GeneralizedSemIm im = GaussianCategoricalIm(pm);

        boolean saveLatentVars = parameters.getBoolean(Params.SAVE_LATENT_VARS);
        DataSet ds = im.simulateDataAvoidInfinity(parameters.getInt(Params.SAMPLE_SIZE), saveLatentVars);

        return makeMixedData(ds, nd);
    }

    /**
     * <p>makeMixedGraph.</p>
     *
     * @param g a {@link edu.cmu.tetrad.graph.Graph} object
     * @param m a {@link java.util.Map} object
     * @return a {@link edu.cmu.tetrad.graph.Graph} object
     */
    public static Graph makeMixedGraph(Graph g, Map<String, Integer> m) {
        List<Node> nodes = g.getNodes();
        for (int i = 0; i < nodes.size(); i++) {
            Node n = nodes.get(i);
            int nL = m.get(n.getName());
            if (nL > 0) {
                Node nNew = new DiscreteVariable(n.getName(), nL);
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

    /**
     * <p>GaussianCategoricalPm.</p>
     *
     * @param trueGraph     a {@link edu.cmu.tetrad.graph.Graph} object
     * @param paramTemplate a {@link java.lang.String} object
     * @return a {@link edu.cmu.tetrad.sem.GeneralizedSemPm} object
     * @throws java.lang.IllegalStateException if any.
     */
    public static GeneralizedSemPm GaussianCategoricalPm(Graph trueGraph, String paramTemplate) throws IllegalStateException {

        Map<String, Integer> nodeDists = getNodeDists(trueGraph);

        GeneralizedSemPm semPm = new GeneralizedSemPm(trueGraph);
        try {
            List<Node> variableNodes = semPm.getVariableNodes();
            int numVars = variableNodes.size();


            semPm.setStartsWithParametersTemplate("B", paramTemplate);
            semPm.setStartsWithParametersTemplate("C", paramTemplate);
            semPm.setStartsWithParametersTemplate("D", paramTemplate);

            // empirically should give us a stddev of 1 - 2
            semPm.setStartsWithParametersTemplate("s", "U(1,2)");

            //if we don't use NB error, we could do this instead

            final String templateDisc0 = "DiscError(err, ";

            for (Node node : variableNodes) {

                List<Node> parents = trueGraph.getParents(node);
                //System.out.println("nParents: " + parents.size() );
                Node eNode = semPm.getErrorNode(node);

                //normal and nb work like normal sems
                String curEx = semPm.getNodeExpressionString(node);
                String errEx = semPm.getNodeExpressionString(eNode);
                String newTemp = "";

                //System.out.println("Node: " + node + "Type: " + nodeDists.get(node));

                //dist of 0 means Gaussian
                int curDist = nodeDists.get(node.getName());
                if (curDist == 1)
                    throw new IllegalArgumentException("Dist for node " + node.getName() + " is set to one (i.e. constant) which is not supported.");


                //for each discrete node use DiscError for categorical draw
                if (curDist > 0) {
                    if (parents.size() == 0) {
                        newTemp = "DiscError(err";
                        for (int l = 0; l < curDist; l++) {
                            newTemp += ",1";
                        }
                        //                        newTemp = templateDisc0;
                    } else {
                        newTemp = "DiscError(err";
                        for (int l = 0; l < curDist; l++) {
                            newTemp += ", TSUM(NEW(C)*$)";
                        }
                    }
                    newTemp += ")";
                    newTemp = newTemp.replaceAll("err", eNode.getName());
                    curEx = TemplateExpander.getInstance().expandTemplate(newTemp, semPm, node);
                    //System.out.println("Disc CurEx: " + curEx);
                    errEx = TemplateExpander.getInstance().expandTemplate("U(0,1)", semPm, eNode);
                }

                //now for every discrete parent, swap for discrete params
                newTemp = curEx;
                if (parents.size() != 0) {
                    for (Node parNode : parents) {
                        int parDist = nodeDists.get(parNode.getName());

                        if (parDist > 0) {
                            //String curName = trueGraph.getParents(node).get(0).toString();
                            String curName = parNode.getName();
                            String disRep = "Switch(" + curName;
                            for (int l = 0; l < parDist; l++) {
                                if (curDist > 0) {
                                    disRep += ",NEW(D)";
                                } else {
                                    disRep += ",NEW(C)";
                                }
                            }
                            disRep += ")";

                            //replaces BX * curName with new discrete expression
                            if (curDist > 0) {
                                newTemp = newTemp.replaceAll("(C[0-9]*\\*" + curName + ")(?![0-9])", disRep);
                            } else {
                                newTemp = newTemp.replaceAll("(B[0-9]*\\*" + curName + ")(?![0-9])", disRep);
                            }
                        }
                    }
                }

                if (newTemp.length() != 0) {
                    //System.out.println(newTemp);
                    curEx = TemplateExpander.getInstance().expandTemplate(newTemp, semPm, node);
                }

                semPm.setNodeExpression(node, curEx);
                semPm.setNodeExpression(eNode, errEx);
            }
        } catch (ParseException e) {
            throw new IllegalStateException("Parse error in fixing parameters.", e);
        }

        return semPm;
    }


    /**
     * <p>GaussianCategoricalIm.</p>
     *
     * @param pm a {@link edu.cmu.tetrad.sem.GeneralizedSemPm} object
     * @return a {@link edu.cmu.tetrad.sem.GeneralizedSemIm} object
     */
    public static GeneralizedSemIm GaussianCategoricalIm(GeneralizedSemPm pm) {
        return GaussianCategoricalIm(pm, true);
    }

    /**
     * This method is needed to normalize edge parameters for an Instantiated Mixed Model Generates edge parameters for
     * c-d and d-d edges from a single weight, abs(w), drawn by the normal IM constructor. Abs(w) is used for d-d
     * edges.
     * <p>
     * For deterministic, c-d are evenly spaced between -w and w, and d-d are a matrix with w on the diagonal and
     * -w/(categories-1) in the rest. For random, c-d params are uniformly drawn from 0 to 1 then transformed to have w
     * as max value and sum to 0.
     *
     * @param pm            a {@link edu.cmu.tetrad.sem.GeneralizedSemPm} object
     * @param discParamRand true for random edge generation behavior, false for deterministic
     * @return a {@link edu.cmu.tetrad.sem.GeneralizedSemIm} object
     */
    public static GeneralizedSemIm GaussianCategoricalIm(GeneralizedSemPm pm, boolean discParamRand) {

        Map<String, Integer> nodeDists = getNodeDists(pm.getGraph());

        GeneralizedSemIm im = new GeneralizedSemIm(pm);
        //System.out.println(im);
        List<Node> nodes = pm.getVariableNodes();

        //this needs to be changed for cyclic graphs...
        for (Node n : nodes) {
            Set<Node> parNodes = pm.getReferencedNodes(n);
            if (parNodes.size() == 0) {
                continue;
            }
            for (Node par : parNodes) {
                if (par.getNodeType() == NodeType.ERROR) {
                    continue;
                }
                int cL = nodeDists.get(n.getName());
                int pL = nodeDists.get(par.getName());

                // c-c edges don't need params changed
                if (cL == 0 && pL == 0) {
                    continue;
                }

                List<String> params = getEdgeParams(n, par, pm);
                // just use the first parameter as the "weight" for the whole edge
                double w = im.getParameterValue(params.get(0));
                // double[] newWeights;

                // d-d edges use one vector and permute edges, could use different strategy
                if (cL > 0 && pL > 0) {
                    double[][] newWeights = new double[cL][pL];
                    w = FastMath.abs(w);
                    double bgW = w / ((double) pL - 1.0);
                    double[] weightVals;

                    int[] weightInds = new int[cL];
                    for (int i = 0; i < cL; i++) {
                        if (i < pL)
                            weightInds[i] = i;
                        else
                            weightInds[i] = i % pL;
                    }

                    if (discParamRand)
                        weightInds = arrayPermute(weightInds);


                    for (int i = 0; i < cL; i++) {
                        for (int j = 0; j < pL; j++) {
                            int index = i * pL + j;
                            if (weightInds[i] == j)
                                im.setParameterValue(params.get(index), w);
                            else
                                im.setParameterValue(params.get(index), -bgW);
                        }
                    }
                    //params for c-d edges
                } else {
                    double[] newWeights;
                    int curL = (pL > 0 ? pL : cL);
                    if (discParamRand)
                        newWeights = generateMixedEdgeParams(w, curL);
                    else
                        newWeights = evenSplitVector(w, curL);

                    int count = 0;
                    for (String p : params) {
                        im.setParameterValue(p, newWeights[count]);
                        count++;
                    }
                }
            }
            //pm.

            //if(p.startsWith("B")){
            //    continue;
            //} else if(p.startsWith())
        }


        return im;
    }

    /**
     * <p>makeMixedData.</p>
     *
     * @param dsCont    a {@link edu.cmu.tetrad.data.DataSet} object
     * @param nodeDists a {@link java.util.Map} object
     * @return a {@link edu.cmu.tetrad.data.DataSet} object
     */
    public static DataSet makeMixedData(DataSet dsCont, Map<String, Integer> nodeDists) {
        ArrayList<Node> mixVars = new ArrayList<>();
        for (Node n : dsCont.getVariables()) {
            int nC = nodeDists.get(n.getName());
            if (nC > 0) {
                DiscreteVariable nd = new DiscreteVariable(n.getName(), nC);
                mixVars.add(nd);
            } else {
                mixVars.add(n);
            }
        }

        return new BoxDataSet(new DoubleDataBox(dsCont.getDoubleData().toArray()), mixVars);
    }

    /**
     * <p>makeMixedData.</p>
     *
     * @param dsCont        a {@link edu.cmu.tetrad.data.DataSet} object
     * @param nodeDists     a {@link java.util.Map} object
     * @param numCategories a int
     * @return a {@link edu.cmu.tetrad.data.DataSet} object
     */
    public static DataSet makeMixedData(DataSet dsCont, Map<String, String> nodeDists, int numCategories) {
        ArrayList<Node> mixVars = new ArrayList<>();
        for (Node n : dsCont.getVariables()) {
            if (nodeDists.get(n.getName()).equals("Disc")) {
                DiscreteVariable nd = new DiscreteVariable(n.getName(), numCategories);
                mixVars.add(nd);
            } else {
                mixVars.add(n);
            }
        }

        return new BoxDataSet(new DoubleDataBox(dsCont.getDoubleData().toArray()), mixVars);
    }

    /**
     * <p>getNodeDists.</p>
     *
     * @param g a {@link edu.cmu.tetrad.graph.Graph} object
     * @return a {@link java.util.Map} object
     */
    public static Map<String, Integer> getNodeDists(Graph g) {
        HashMap<String, Integer> map = new HashMap<>();
        List<Node> nodes = g.getNodes();
        for (Node n : nodes) {
            if (n instanceof DiscreteVariable)
                map.put(n.getName(), ((DiscreteVariable) n).getNumCategories());
            else
                map.put(n.getName(), 0);
        }
        return map;
    }

    /**
     * <p>getEdgeParams.</p>
     *
     * @param n1 a {@link edu.cmu.tetrad.graph.Node} object
     * @param n2 a {@link edu.cmu.tetrad.graph.Node} object
     * @param pm a {@link edu.cmu.tetrad.sem.GeneralizedSemPm} object
     * @return a {@link java.util.List} object
     */
    public static List<String> getEdgeParams(Node n1, Node n2, GeneralizedSemPm pm) {
        //there may be a better way to do this using recursive calls of Expression.getExpressions
        Set<String> allParams = pm.getParameters();

        Node child;
        Node parent;
        if (pm.getReferencedNodes(n1).contains(n2)) {
            child = n1;
            parent = n2;
        } else if (pm.getReferencedNodes(n2).contains(n1)) {
            child = n2;
            parent = n1;
        } else {
            return null;
        }

        java.util.regex.Pattern parPat;
        if (parent instanceof DiscreteVariable) {
            parPat = java.util.regex.Pattern.compile("Switch\\(" + parent.getName() + ",.*?\\)");
        } else {
            parPat = java.util.regex.Pattern.compile("([BC][0-9]*\\*" + parent.getName() + ")(?![0-9])");
        }

        ArrayList<String> paramList = new ArrayList<>();
        String ex = pm.getNodeExpressionString(child);
        java.util.regex.Matcher mat = parPat.matcher(ex);
        while (mat.find()) {
            String curGroup = mat.group();
            if (parent instanceof DiscreteVariable) {
                curGroup = curGroup.substring(("Switch(" + parent.getName()).length() + 1, curGroup.length() - 1);
                String[] pars = curGroup.split(",");
                paramList.addAll(Arrays.asList(pars));
            } else {
                String p = curGroup.split("\\*")[0];
                paramList.add(p);
            }
        }

        return paramList;
    }

    /**
     * <p>arrayPermute.</p>
     *
     * @param a an array of  objects
     * @return an array of  objects
     */
    public static int[] arrayPermute(int[] a) {
        int[] out = new int[a.length];
        List<Integer> l = new ArrayList<>(a.length);
        for (int i = 0; i < a.length; i++) {
            l.add(i, a[i]);
        }
        RandomUtil.shuffle(l);
        for (int i = 0; i < a.length; i++) {
            out[i] = l.get(i);
        }
        return out;
    }

    /**
     * <p>generateMixedEdgeParams.</p>
     *
     * @param w a double
     * @param L a int
     * @return an array of  objects
     */
    public static double[] generateMixedEdgeParams(double w, int L) {
        double[] vec = new double[L];
        RandomUtil ru = RandomUtil.getInstance();

        for (int i = 0; i < L; i++) {
            vec[i] = ru.nextUniform(0, 1);
        }

        double vMean = StatUtils.mean(vec);
        double vMax = 0;
        for (int i = 0; i < L; i++) {
            vec[i] = vec[i] - vMean;
            if (FastMath.abs(vec[i]) > FastMath.abs(vMax))
                vMax = vec[i];
        }

        double scale = w / vMax;
        //maintain sign of w;
        if (vMax < 0)
            scale *= -1;

        for (int i = 0; i < L; i++) {
            vec[i] *= scale;
        }

        return vec;
    }

    /**
     * <p>evenSplitVector.</p>
     *
     * @param w a double
     * @param L a int
     * @return an array of  objects
     */
    public static double[] evenSplitVector(double w, int L) {
        double[] vec = new double[L];
        double step = 2.0 * w / (L - 1.0);
        for (int i = 0; i < L; i++) {
            vec[i] = -w + i * step;
        }
        return vec;
    }


    private int pickNumCategories(int min, int max) {
        return min + RandomUtil.getInstance().nextInt(max - min + 1);
    }
}
