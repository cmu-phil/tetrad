package edu.cmu.tetrad.algcomparison.simulation;

import edu.cmu.tetrad.algcomparison.graph.RandomGraph;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeType;
import edu.cmu.tetrad.sem.GeneralizedSemIm;
import edu.cmu.tetrad.sem.GeneralizedSemPm;
import edu.cmu.tetrad.sem.TemplateExpander;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;

import java.text.ParseException;
import java.util.*;

/**
 * This was used for a simulation to test the FTFC and FOFC algorithm and contains
 * some carefully selected functions to test nonlinearity and non-Gaussianity.
 *
 * @author ekummerfeld@gmail.com
 * @author jdramsey
 */
public class GeneralSemSimulationSpecial1 implements Simulation {
    static final long serialVersionUID = 23L;
    private final RandomGraph randomGraph;
    private List<Graph> graphs = new ArrayList<>();
    private List<DataSet> dataSets = new ArrayList<>();

    public GeneralSemSimulationSpecial1(RandomGraph randomGraph) {
        this.randomGraph = randomGraph;
    }

    @Override
    public void createData(Parameters parameters, boolean newModel) {
//        if (!newModel && !dataSets.isEmpty()) return;

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
            this.dataSets.add(dataSet);
        }
    }

    private DataSet simulate(Graph graph, Parameters parameters) {
        GeneralizedSemPm pm = getPm(graph);
        GeneralizedSemIm im = new GeneralizedSemIm(pm);
        return im.simulateData(parameters.getInt(Params.SAMPLE_SIZE), false);
    }

    @Override
    public Graph getTrueGraph(int index) {
        return this.graphs.get(index);
    }

    @Override
    public int getNumDataModels() {
        return this.dataSets.size();
    }

    @Override
    public DataModel getDataModel(int index) {
        return this.dataSets.get(index);
    }

    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }

    public String getDescription() {
        return "Nonlinear, non-Gaussian SEM simulation using " + this.randomGraph.getDescription();
    }

    @Override
    public List<String> getParameters() {
        List<String> parameters = this.randomGraph.getParameters();
        parameters.add(Params.NUM_RUNS);
        parameters.add(Params.DIFFERENT_GRAPHS);
        parameters.add(Params.SAMPLE_SIZE);
        return parameters;
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
