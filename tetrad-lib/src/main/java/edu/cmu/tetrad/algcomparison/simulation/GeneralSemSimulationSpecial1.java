package edu.cmu.tetrad.algcomparison.simulation;

import edu.cmu.tetrad.algcomparison.algorithms.graphs.GraphGenerator;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeType;
import edu.cmu.tetrad.sem.GeneralizedSemIm;
import edu.cmu.tetrad.sem.GeneralizedSemPm;
import edu.cmu.tetrad.sem.TemplateExpander;

import java.text.ParseException;
import java.util.*;

/**
 * This was used for a simulation to test the FTFC and FOFC algorithms and contains
 * some carefully selected functions to test nonlinearity and non-Gaussianity.
 *
 * @author ekummerfeld@gmail.com
 * @author jdramsey
 */
public class GeneralSemSimulationSpecial1 implements Simulation {
    private GraphGenerator graphGenerator;
    private Graph graph;
    private List<DataSet> dataSets;

    public GeneralSemSimulationSpecial1(GraphGenerator graphGenerator) {
        this.graphGenerator = graphGenerator;
    }

    @Override
    public void createData(Parameters parameters) {
        this.graphGenerator = graphGenerator;

        this.dataSets = new ArrayList<>();

        for (int i = 0; i < parameters.getInt("numRuns"); i++) {
            System.out.println("Simulating dataset #" + (i + 1));
            DataSet dataSet = simulate(graph, parameters);
            this.dataSets.add(dataSet);
        }
    }

    private DataSet simulate(Graph graph, Parameters parameters) {
        GeneralizedSemPm pm = getPm(graph);
        GeneralizedSemIm im = new GeneralizedSemIm(pm);
        return im.simulateData(parameters.getInt("sampleSize"), false);
    }

    @Override
    public Graph getTrueGraph() {
        return graph;
    }

    @Override
    public int getNumDataSets() {
        return dataSets.size();
    }

    @Override
    public DataSet getDataSet(int index) {
        return dataSets.get(index);
    }

    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }

    public String getDescription() {
        return "Nonlinear, non-Gaussian SEM simulation";
    }

    @Override
    public List<String> getParameters() {
        List<String> parameters = graphGenerator.getParameters();
        parameters.add("numRuns");
        parameters.add("sampleSize");
        return parameters;
    }

    private GeneralizedSemPm getPm(Graph graph) {

        GeneralizedSemPm pm = new GeneralizedSemPm(graph);

        List<Node> variablesNodes = pm.getVariableNodes();
        List<Node> errorNodes = pm.getErrorNodes();

        Map<String, String> paramMap = new HashMap<String, String>();
        String[] funcs = {"TSUM(NEW(B)*$)", "TSUM(NEW(B)*$+NEW(C)*sin(NEW(T)*$+NEW(A)))",
                "TSUM(NEW(B)*(.5*$ + .5*(sqrt(abs(NEW(b)*$+NEW(exoErrorType))) ) ) )"};
        paramMap.put("s", "U(1,3)");
        paramMap.put("B", "Split(-1.5,-.5,.5,1.5)");
        paramMap.put("C", "Split(-1.5,-.5,.5,1.5)");
        paramMap.put("T", "U(.5,1.5)");
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
            System.out.println(e);
        }

        return pm;
    }
}
