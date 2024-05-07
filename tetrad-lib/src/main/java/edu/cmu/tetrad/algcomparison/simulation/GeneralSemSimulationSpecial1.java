package edu.cmu.tetrad.algcomparison.simulation;

import edu.cmu.tetrad.algcomparison.graph.RandomGraph;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataTransforms;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeType;
import edu.cmu.tetrad.sem.GeneralizedSemIm;
import edu.cmu.tetrad.sem.GeneralizedSemPm;
import edu.cmu.tetrad.sem.TemplateExpander;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.cmu.tetrad.util.RandomUtil;

import java.io.Serial;
import java.text.ParseException;
import java.util.*;

/**
 * This was used for a simulation to test the FTFC and FOFC algorithm and contains some carefully selected functions to
 * test nonlinearity and non-Gaussianity.
 *
 * @author ekummerfeld@gmail.com
 * @author josephramsey
 * @version $Id: $Id
 */
public class GeneralSemSimulationSpecial1 implements Simulation {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The random graph.
     */
    private final RandomGraph randomGraph;

    /**
     * The data sets.
     */
    private List<Graph> graphs = new ArrayList<>();

    /**
     * The graphs.
     */
    private List<DataSet> dataSets = new ArrayList<>();

    /**
     * <p>Constructor for GeneralSemSimulationSpecial1.</p>
     *
     * @param randomGraph a {@link edu.cmu.tetrad.algcomparison.graph.RandomGraph} object
     */
    public GeneralSemSimulationSpecial1(RandomGraph randomGraph) {
        this.randomGraph = randomGraph;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void createData(Parameters parameters, boolean newModel) {
        if (parameters.getLong(Params.SEED) != -1L) {
            RandomUtil.getInstance().setSeed(parameters.getLong(Params.SEED));
        }

        Graph graph = this.randomGraph.createGraph(parameters);

        this.dataSets = new ArrayList<>();
        this.graphs = new ArrayList<>();

        for (int i = 0; i < parameters.getInt(Params.NUM_RUNS); i++) {
            if (parameters.getBoolean(Params.DIFFERENT_GRAPHS) && i > 0) {
                graph = this.randomGraph.createGraph(parameters);
            }


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

    private DataSet simulate(Graph graph, Parameters parameters) {
        GeneralizedSemPm pm = getPm(graph);
        GeneralizedSemIm im = new GeneralizedSemIm(pm);
        return im.simulateData(parameters.getInt(Params.SAMPLE_SIZE), false);
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
    public int getNumDataModels() {
        return this.dataSets.size();
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
    public DataType getDataType() {
        return DataType.Continuous;
    }

    /**
     * <p>getDescription.</p>
     *
     * @return a {@link java.lang.String} object
     */
    public String getDescription() {
        return "Nonlinear, non-Gaussian SEM simulation using " + this.randomGraph.getDescription();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getShortName() {
        return "General SEM Special 1";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> getParameters() {
        List<String> parameters = this.randomGraph.getParameters();
        parameters.add(Params.NUM_RUNS);
        parameters.add(Params.PROB_REMOVE_COLUMN);
        parameters.add(Params.DIFFERENT_GRAPHS);
        parameters.add(Params.SAMPLE_SIZE);
        parameters.add(Params.SEED);
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

    private GeneralizedSemPm getPm(Graph graph) {

        GeneralizedSemPm pm = new GeneralizedSemPm(graph);

        List<Node> variablesNodes = pm.getVariableNodes();
        List<Node> errorNodes = pm.getErrorNodes();

        Map<String, String> paramMap = new HashMap<>();
        String[] funcs = {"TSUM(NEW(B)*$)", "TSUM(NEW(B)*$+NEW(C)*sin(NEW(T)*$+NEW(A)))",
                "TSUM(NEW(B)*(.5*$ + .5*(sqrt(abs(NEW(b)*$+NEW(exoErrorType))) ) ) )"};
        paramMap.put("s", "U(1,3)");
        paramMap.put("B", "Split(-1.0,-.0,.0,1.0)");
        paramMap.put("C", "Split(-1.0,-.0,.0,1.0)");
        paramMap.put("T", "U(.0,1.0)");
        paramMap.put("A", "U(0,.25)");
        paramMap.put("exoErrorType", "U(-.5,.5)");
        paramMap.put("funcType", "U(1,5)");

        String nonlinearStructuralEdgesFunction = funcs[0];
        String nonlinearFactorMeasureEdgesFunction = funcs[0];

        try {
            for (Node node : variablesNodes) {
                if (node.getNodeType() == NodeType.LATENT) {
                    String _template = TemplateExpander.getInstance().expandTemplate(
                            nonlinearStructuralEdgesFunction, pm, node);
                    pm.setNodeExpression(node, _template);
                } else {
                    String _template = TemplateExpander.getInstance().expandTemplate(
                            nonlinearFactorMeasureEdgesFunction, pm, node);
                    pm.setNodeExpression(node, _template);
                }
            }

            for (Node node : errorNodes) {
                String _template = TemplateExpander.getInstance().expandTemplate("U(-.5,.5)", pm, node);
                pm.setNodeExpression(node, _template);
            }

            Set<String> parameters = pm.getParameters();

            for (String parameter : parameters) {
                for (String type : paramMap.keySet()) {
                    if (parameter.startsWith(type)) {
                        pm.setParameterExpression(parameter, paramMap.get(type));
                    }
                }
            }
        } catch (ParseException e) {
            e.printStackTrace();
        }

        return pm;
    }
}
