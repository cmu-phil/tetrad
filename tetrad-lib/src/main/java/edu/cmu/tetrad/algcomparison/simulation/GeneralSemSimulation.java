package edu.cmu.tetrad.algcomparison.simulation;

import edu.cmu.tetrad.algcomparison.graph.RandomGraph;
import edu.cmu.tetrad.algcomparison.graph.SingleGraph;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.SemGraph;
import edu.cmu.tetrad.sem.GeneralizedSemIm;
import edu.cmu.tetrad.sem.GeneralizedSemPm;
import edu.cmu.tetrad.sem.TemplateExpander;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.cmu.tetrad.util.RandomUtil;
import org.apache.commons.math3.util.FastMath;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * General SEM simulation.
 *
 * @author josephramsey
 */
public class GeneralSemSimulation implements Simulation {
    private static final long serialVersionUID = 23L;
    private final RandomGraph randomGraph;
    private GeneralizedSemPm pm;
    private GeneralizedSemIm im;
    private List<DataSet> dataSets = new ArrayList<>();
    private List<Graph> graphs = new ArrayList<>();
    private List<GeneralizedSemIm> ims = new ArrayList<>();

    public GeneralSemSimulation(RandomGraph graph) {
        this.randomGraph = graph;
    }

    public GeneralSemSimulation(GeneralizedSemPm pm) {
        SemGraph graph = pm.getGraph();
        graph.setShowErrorTerms(false);
        this.randomGraph = new SingleGraph(graph);
        this.pm = pm;
    }

    public GeneralSemSimulation(GeneralizedSemIm im) {
        SemGraph graph = im.getSemPm().getGraph();
        graph.setShowErrorTerms(false);
        this.randomGraph = new SingleGraph(graph);
        this.im = im;
        this.ims = new ArrayList<>();
        this.ims.add(im);
        this.pm = im.getGeneralizedSemPm();
    }

    @Override
    public void createData(Parameters parameters, boolean newModel) {
        if (parameters.getLong(Params.SEED) != -1L) {
            RandomUtil.getInstance().setSeed(parameters.getLong(Params.SEED));
        }

        Graph graph = this.randomGraph.createGraph(parameters);

        this.dataSets = new ArrayList<>();
        this.graphs = new ArrayList<>();
        this.ims = new ArrayList<>();

        for (int i = 0; i < parameters.getInt(Params.NUM_RUNS); i++) {
            if (parameters.getBoolean(Params.DIFFERENT_GRAPHS) && i > 0) {
                graph = this.randomGraph.createGraph(parameters);
            }

            this.graphs.add(graph);

            DataSet dataSet = simulate(graph, parameters);

            if (parameters.getBoolean(Params.STANDARDIZE)) {
                dataSet = DataTransforms.standardizeData(dataSet);
            }

            double variance = parameters.getDouble(Params.MEASUREMENT_VARIANCE);

            if (variance > 0) {
                for (int k = 0; k < dataSet.getNumRows(); k++) {
                    for (int j = 0; j < dataSet.getNumColumns(); j++) {
                        double d = dataSet.getDouble(k, j);
                        double norm = RandomUtil.getInstance().nextNormal(0, FastMath.sqrt(variance));
                        dataSet.setDouble(k, j, d + norm);
                    }
                }
            }

            if (parameters.getBoolean(Params.RANDOMIZE_COLUMNS)) {
                dataSet = DataTransforms.shuffleColumns(dataSet);
            }

            if (parameters.getDouble(Params.PROB_REMOVE_COLUMN) > 0) {
                double aDouble = parameters.getDouble(Params.PROB_REMOVE_COLUMN);
                dataSet = DataTransforms.removeRandomColumns(dataSet, aDouble);
            }

            dataSet.setName("" + (i + 1));
            this.dataSets.add(DataTransforms.restrictToMeasured(dataSet));
        }
    }

    private synchronized DataSet simulate(Graph graph, Parameters parameters) {
        if (this.pm == null) {
            this.pm = getPm(graph, parameters);
        }

        System.out.println(this.pm);

        this.im = new GeneralizedSemIm(this.pm);
        this.im.setGuaranteeIid(parameters.getBoolean(Params.GUARANTEE_IID));

        System.out.println(this.im);

        this.ims.add(this.im);
        return this.im.simulateData(parameters.getInt(Params.SAMPLE_SIZE), true);
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
        List<String> parameters = new ArrayList<>();

        if (!(this.randomGraph instanceof SingleGraph)) {
            parameters.addAll(this.randomGraph.getParameters());
        }

        if (this.pm == null) {
            parameters.addAll(GeneralizedSemPm.getParameterNames());
        }

        parameters.add(Params.NUM_RUNS);
        parameters.add(Params.PROB_REMOVE_COLUMN);
        parameters.add(Params.DIFFERENT_GRAPHS);
        parameters.add(Params.SAMPLE_SIZE);
        parameters.add(Params.GUARANTEE_IID);
        parameters.add(Params.SEED);

        return parameters;
    }

    private GeneralizedSemPm getPm(Graph graph, Parameters parameters) {
        GeneralizedSemPm pm = new GeneralizedSemPm(graph);

        List<Node> variablesNodes = pm.getVariableNodes();
        List<Node> errorNodes = pm.getErrorNodes();

        try {

            for (Node node : variablesNodes) {
                String _template = TemplateExpander.getInstance().expandTemplate(
                        parameters.getString(Params.GENERAL_SEM_FUNCTION_TEMPLATE_MEASURED), pm, node);
                pm.setNodeExpression(node, _template);
            }

            for (Node node : errorNodes) {
                String _template = TemplateExpander.getInstance().expandTemplate(
                        parameters.getString(Params.GENERAL_SEM_ERROR_TEMPLATE), pm, node);
                pm.setNodeExpression(node, _template);
            }

            for (String parameter : pm.getParameters()) {
                pm.setParameterExpression(parameter, parameters.getString(Params.GENERAL_SEM_PARAMETER_TEMPLATE));
            }

            pm.setVariablesTemplate(parameters.getString(Params.GENERAL_SEM_FUNCTION_TEMPLATE_MEASURED));
            pm.setErrorsTemplate(parameters.getString(Params.GENERAL_SEM_ERROR_TEMPLATE));
            pm.setParametersTemplate(parameters.getString(Params.GENERAL_SEM_PARAMETER_TEMPLATE));

        } catch (ParseException e) {
            e.printStackTrace();
        }

        return pm;
    }

    public List<GeneralizedSemIm> getIms() {
        return this.ims;
    }
}
