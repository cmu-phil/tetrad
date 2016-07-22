package edu.cmu.tetrad.algcomparison.simulation;

import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.sem.*;

import java.text.ParseException;
import java.util.*;

/**
 * @author jdramsey
 */
public class GeneralSemSimulation implements Simulation {
    private Graph graph;
    private List<DataSet> dataSets;

    @Override
    public void createData(Parameters parameters) {
        this.graph = GraphUtils.randomGraphRandomForwardEdges(
                parameters.getInt("numMeasures"),
                parameters.getInt("numLatents"),
                parameters.getInt("avgDegree") * parameters.getInt("numMeasures") / 2,
                parameters.getInt("maxDegree"),
                parameters.getInt("maxIndegree"),
                parameters.getInt("maxOutdegree"),
                parameters.getInt("connected") == 1);

        for (int i = 0; i < parameters.getInt("numRuns"); i++) {
            DataSet dataSet = simulate(graph, parameters);
            this.dataSets.add(dataSet);
        }
    }

    private DataSet simulate(Graph graph, Parameters parameters) {
        GeneralizedSemPm pm = getPm(graph, parameters);
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
        List<String> parameters = new ArrayList<>();
        parameters.add("numMeasures");
        parameters.add("numLatents");
        parameters.add("avgDegree");
        parameters.add("maxDegree");
        parameters.add("maxIndegree");
        parameters.add("maxOutdegree");
        parameters.add("numRuns");
        parameters.add("sampleSize");
        parameters.add("generalSemFunctionTemplateMeasured");
        parameters.add("generalSemFunctionTemplateLatent");
        parameters.add("generalSemErrorTemplate");
        return parameters;
    }

    private GeneralizedSemPm getPm(Graph graph, Parameters parameters) {
        GeneralizedSemPm pm = new GeneralizedSemPm(graph);

        List<Node> variablesNodes = pm.getVariableNodes();
        List<Node> errorNodes = pm.getErrorNodes();

        String measuredFunction = parameters.getString("generalSemFunctionTemplateMeasured");
        String latentFunction = parameters.getString("generalSemFunctionTemplateLatent");
        String error = parameters.getString("generalSemErrorTemplate");

        try {
            for (Node node : variablesNodes) {
                if (node.getNodeType() == NodeType.LATENT) {
                    String _template = TemplateExpander.getInstance().expandTemplate(
                            latentFunction, pm, node);
                    pm.setNodeExpression(node, _template);
                } else {
                    String _template = TemplateExpander.getInstance().expandTemplate(
                            measuredFunction, pm, node);
                    pm.setNodeExpression(node, _template);
                }
            }

            for (Node node : errorNodes) {
                String _template = TemplateExpander.getInstance().expandTemplate(error, pm, node);
                pm.setNodeExpression(node, _template);
            }
        } catch (ParseException e) {
            System.out.println(e);
        }

        return pm;
    }
}
