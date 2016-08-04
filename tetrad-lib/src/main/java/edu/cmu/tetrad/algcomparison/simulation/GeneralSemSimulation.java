package edu.cmu.tetrad.algcomparison.simulation;

import edu.cmu.tetrad.algcomparison.graph.RandomGraph;
import edu.cmu.tetrad.algcomparison.graph.SingleGraph;
import edu.cmu.tetrad.algcomparison.utils.*;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeType;
import edu.cmu.tetrad.sem.*;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author jdramsey
 */
public class GeneralSemSimulation implements Simulation {
    private RandomGraph randomGraph;
    private List<DataSet> dataSets;
    private Graph graph;
    private GeneralizedSemPm pm;
    private GeneralizedSemIm im;

    public GeneralSemSimulation(RandomGraph graph) {
        this.randomGraph = graph;
    }

    public GeneralSemSimulation(GeneralizedSemPm pm) {
        this.randomGraph = new SingleGraph(pm.getGraph());
        this.pm = pm;
    }

    public GeneralSemSimulation(GeneralizedSemIm im) {
        this.randomGraph = new SingleGraph(im.getSemPm().getGraph());
        this.im = im;
    }

    @Override
    public void createData(Parameters parameters) {
        this.graph = randomGraph.createGraph(parameters);

        dataSets = new ArrayList<>();

        for (int i = 0; i < parameters.getInt("numRuns"); i++) {
            System.out.println("Simulating dataset #" + (i + 1));
            DataSet dataSet = simulate(graph, parameters);
            dataSets.add(dataSet);
        }
    }

    private DataSet simulate(Graph graph, Parameters parameters) {
        GeneralizedSemIm im = this.im;

        if (im == null) {
            GeneralizedSemPm pm = this.pm;

            if (pm == null) {
                pm = new GeneralizedSemPm(graph);
            }

            im = new GeneralizedSemIm(pm);
        }

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
        return "Nonlinear, non-Gaussian SEM simulation using " + randomGraph.getDescription();
    }

    @Override
    public List<String> getParameters() {
        List<String> parameters = randomGraph.getParameters();
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
