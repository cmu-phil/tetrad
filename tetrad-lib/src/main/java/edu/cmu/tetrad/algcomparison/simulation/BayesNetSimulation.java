package edu.cmu.tetrad.algcomparison.simulation;

import edu.cmu.tetrad.algcomparison.graph.RandomGraph;
import edu.cmu.tetrad.algcomparison.graph.SingleGraph;
import edu.cmu.tetrad.bayes.BayesIm;
import edu.cmu.tetrad.bayes.BayesPm;
import edu.cmu.tetrad.bayes.MlBayesIm;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.util.Parameters;
import java.util.ArrayList;
import java.util.List;

/**
 * @author jdramsey
 */
public class BayesNetSimulation implements Simulation {

    static final long serialVersionUID = 23L;
    private RandomGraph randomGraph;
    private BayesPm pm;
    private BayesIm im;
    private List<DataSet> dataSets = new ArrayList<>();
    private List<Graph> graphs = new ArrayList<>();
    private List<BayesIm> ims = new ArrayList<>();

    public BayesNetSimulation(RandomGraph graph) {
        this.randomGraph = graph;
    }

    public BayesNetSimulation(BayesPm pm) {
        this.randomGraph = new SingleGraph(pm.getDag());
        this.pm = pm;
    }

    public BayesNetSimulation(BayesIm im) {
        this.randomGraph = new SingleGraph(im.getDag());
        this.im = im;
        this.pm = im.getBayesPm();
    }

    @Override
    public void createData(Parameters parameters) {
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
    public DataModel getDataModel(int index) {
        return dataSets.get(index);
    }

    @Override
    public Graph getTrueGraph(int index) {
        if (graphs.isEmpty()) {
            return new EdgeListGraph();
        } else {
            return graphs.get(index);
        }
    }

    @Override
    public String getDescription() {
        return "Bayes net simulation using " + randomGraph.getDescription();
    }

    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();

        if (!(randomGraph instanceof SingleGraph)) {
            parameters.addAll(randomGraph.getParameters());
        }

        if (pm == null) {
            parameters.addAll(BayesPm.getParameterNames());
        }

        if (im == null) {
            parameters.addAll(MlBayesIm.getParameterNames());
        }

        parameters.add("numRuns");
        parameters.add("differentGraphs");
        parameters.add("sampleSize");
        parameters.add("saveLatentVars");

        return parameters;
    }

    @Override
    public int getNumDataModels() {
        return dataSets.size();
    }

    @Override
    public DataType getDataType() {
        return DataType.Discrete;
    }

    private DataSet simulate(Graph graph, Parameters parameters) {
        boolean saveLatentVars = parameters.getBoolean("saveLatentVars");

        try {
            BayesIm im = this.im;

            if (im == null) {
                BayesPm pm = this.pm;

                if (pm == null) {
                    int minCategories = parameters.getInt("minCategories");
                    int maxCategories = parameters.getInt("maxCategories");
                    pm = new BayesPm(graph, minCategories, maxCategories);
                    im = new MlBayesIm(pm, MlBayesIm.RANDOM);
                    ims.add(im);
                    return im.simulateData(parameters.getInt("sampleSize"), saveLatentVars);
                } else {
                    im = new MlBayesIm(pm, MlBayesIm.RANDOM);
                    this.im = im;
                    ims.add(im);
                    return im.simulateData(parameters.getInt("sampleSize"), saveLatentVars);
                }
            } else {
                ims = new ArrayList<>();
                ims.add(im);
                return im.simulateData(parameters.getInt("sampleSize"), saveLatentVars);
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new IllegalArgumentException("Sorry, I couldn't simulate from that Bayes IM; perhaps not all of\n"
                    + "the parameters have been specified.");
        }
    }

    public List<BayesIm> getBayesIms() {
        return ims;
    }

}
