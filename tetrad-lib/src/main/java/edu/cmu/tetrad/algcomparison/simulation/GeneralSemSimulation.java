package edu.cmu.tetrad.algcomparison.simulation;

import edu.cmu.tetrad.algcomparison.graph.RandomGraph;
import edu.cmu.tetrad.algcomparison.graph.SingleGraph;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.DataUtils;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.SemGraph;
import edu.cmu.tetrad.sem.GeneralizedSemIm;
import edu.cmu.tetrad.sem.GeneralizedSemPm;
import edu.cmu.tetrad.sem.TemplateExpander;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.cmu.tetrad.util.RandomUtil;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author jdramsey
 */
public class GeneralSemSimulation implements Simulation {
    static final long serialVersionUID = 23L;
    private final RandomGraph randomGraph;
    private GeneralizedSemPm pm;
    private GeneralizedSemIm im;
    private List<DataSet> dataSets = new ArrayList<>();
    private final List<DataSet> dataWithLatents = new ArrayList<>();
    private List<Graph> graphs = new ArrayList<>();
    private List<GeneralizedSemIm> ims = new ArrayList<>();

    public GeneralSemSimulation(final RandomGraph graph) {
        this.randomGraph = graph;
    }

    public GeneralSemSimulation(final GeneralizedSemPm pm) {
        final SemGraph graph = pm.getGraph();
        graph.setShowErrorTerms(false);
        this.randomGraph = new SingleGraph(graph);
        this.pm = pm;
    }

    public GeneralSemSimulation(final GeneralizedSemIm im) {
        final SemGraph graph = im.getSemPm().getGraph();
        graph.setShowErrorTerms(false);
        this.randomGraph = new SingleGraph(graph);
        this.im = im;
        this.ims = new ArrayList<>();
        this.ims.add(im);
        this.pm = im.getGeneralizedSemPm();
    }

    @Override
    public void createData(final Parameters parameters, final boolean newModel) {
//        if (!newModel && !dataSets.isEmpty()) return;

        Graph graph = this.randomGraph.createGraph(parameters);

        this.dataSets = new ArrayList<>();
        this.graphs = new ArrayList<>();
        this.ims = new ArrayList<>();

        for (int i = 0; i < parameters.getInt(Params.NUM_RUNS); i++) {
            System.out.println("Simulating dataset #" + (i + 1));

            if (parameters.getBoolean(Params.DIFFERENT_GRAPHS) && i > 0) {
                graph = this.randomGraph.createGraph(parameters);
            }

            this.graphs.add(graph);

            DataSet dataSet = simulate(graph, parameters);

            if (parameters.getBoolean(Params.STANDARDIZE)) {
                dataSet = DataUtils.standardizeData(dataSet);
            }

            final double variance = parameters.getDouble(Params.MEASUREMENT_VARIANCE);

            if (variance > 0) {
                for (int k = 0; k < dataSet.getNumRows(); k++) {
                    for (int j = 0; j < dataSet.getNumColumns(); j++) {
                        final double d = dataSet.getDouble(k, j);
                        final double norm = RandomUtil.getInstance().nextNormal(0, Math.sqrt(variance));
                        dataSet.setDouble(k, j, d + norm);
                    }
                }
            }

            if (parameters.getBoolean(Params.RANDOMIZE_COLUMNS)) {
                dataSet = DataUtils.shuffleColumns(dataSet);
            }

            dataSet.setName("" + (i + 1));
            this.dataSets.add(DataUtils.restrictToMeasured(dataSet));
            this.dataWithLatents.add(dataSet);
        }
    }

    private synchronized DataSet simulate(final Graph graph, final Parameters parameters) {
        if (this.pm == null) {
            this.pm = getPm(graph, parameters);
        }

        System.out.println(this.pm);

        this.im = new GeneralizedSemIm(this.pm);

        System.out.println(this.im);

        this.ims.add(this.im);
        return this.im.simulateData(parameters.getInt(Params.SAMPLE_SIZE), true);
    }

    @Override
    public Graph getTrueGraph(final int index) {
        return this.graphs.get(index);
    }

    @Override
    public int getNumDataModels() {
        return this.dataSets.size();
    }

    @Override
    public DataModel getDataModel(final int index) {
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
        final List<String> parameters = new ArrayList<>();

        if (!(this.randomGraph instanceof SingleGraph)) {
            parameters.addAll(this.randomGraph.getParameters());
        }

        if (this.pm == null) {
            parameters.addAll(GeneralizedSemPm.getParameterNames());
        }

        parameters.add(Params.NUM_RUNS);
        parameters.add(Params.DIFFERENT_GRAPHS);
        parameters.add(Params.SAMPLE_SIZE);

        return parameters;
    }

    private GeneralizedSemPm getPm(final Graph graph, final Parameters parameters) {
        final GeneralizedSemPm pm = new GeneralizedSemPm(graph);

        final List<Node> variablesNodes = pm.getVariableNodes();
        final List<Node> errorNodes = pm.getErrorNodes();

        try {

            for (final Node node : variablesNodes) {
                final String _template = TemplateExpander.getInstance().expandTemplate(
                        parameters.getString(Params.GENERAL_SEM_FUNCTION_TEMPLATE_MEASURED), pm, node);
                pm.setNodeExpression(node, _template);
            }

            for (final Node node : errorNodes) {
                final String _template = TemplateExpander.getInstance().expandTemplate(
                        parameters.getString(Params.GENERAL_SEM_ERROR_TEMPLATE), pm, node);
                pm.setNodeExpression(node, _template);
            }

            for (final String parameter : pm.getParameters()) {
                pm.setParameterExpression(parameter, parameters.getString(Params.GENERAL_SEM_PARAMETER_TEMPLATE));
            }

            pm.setVariablesTemplate(parameters.getString(Params.GENERAL_SEM_FUNCTION_TEMPLATE_MEASURED));
            pm.setErrorsTemplate(parameters.getString(Params.GENERAL_SEM_ERROR_TEMPLATE));
            pm.setParametersTemplate(parameters.getString(Params.GENERAL_SEM_PARAMETER_TEMPLATE));

        } catch (final ParseException e) {
            System.out.println(e);
        }

        return pm;
    }

    public List<GeneralizedSemIm> getIms() {
        return this.ims;
    }
}
