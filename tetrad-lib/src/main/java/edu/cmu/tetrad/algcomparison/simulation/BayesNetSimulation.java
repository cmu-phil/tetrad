package edu.cmu.tetrad.algcomparison.simulation;

import edu.cmu.tetrad.algcomparison.graph.RandomGraph;
import edu.cmu.tetrad.algcomparison.graph.SingleGraph;
import edu.cmu.tetrad.bayes.BayesIm;
import edu.cmu.tetrad.bayes.BayesPm;
import edu.cmu.tetrad.bayes.MlBayesIm;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataTransforms;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.cmu.tetrad.util.RandomUtil;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

/**
 * Bayes net simulation.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class BayesNetSimulation implements Simulation {

    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The random graph.
     */
    private final RandomGraph randomGraph;

    /**
     * The Bayes PM.
     */
    private BayesPm pm;

    /**
     * The Bayes IM.
     */
    private BayesIm im;

    /**
     * The data sets.
     */
    private List<DataSet> dataSets = new ArrayList<>();

    /**
     * The graphs.
     */
    private List<Graph> graphs = new ArrayList<>();

    /**
     * The Bayes IMs.
     */
    private List<BayesIm> ims = new ArrayList<>();

    /**
     * Constructs a new BayesNetSimulation.
     *
     * @param graph The graph.
     */
    public BayesNetSimulation(RandomGraph graph) {
        this.randomGraph = graph;
    }

    /**
     * Constructs a new BayesNetSimulation.
     *
     * @param pm The Bayes PM.
     */
    public BayesNetSimulation(BayesPm pm) {
        this.randomGraph = new SingleGraph(pm.getDag());
        this.pm = pm;
    }

    /**
     * Constructs a new BayesNetSimulation.
     *
     * @param im The Bayes IM.
     */
    public BayesNetSimulation(BayesIm im) {
        this.randomGraph = new SingleGraph(im.getDag());
        this.im = im;
        this.pm = im.getBayesPm();
        this.ims = new ArrayList<>();
        this.ims.add(im);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Creates the data.
     */
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

            if (parameters.getBoolean(Params.RANDOMIZE_COLUMNS)) {
                dataSet = DataTransforms.shuffleColumns(dataSet);
            }

            if (parameters.getDouble(Params.PROB_REMOVE_COLUMN) > 0) {
                double aDouble = parameters.getDouble(Params.PROB_REMOVE_COLUMN);
                dataSet = DataTransforms.removeRandomColumns(dataSet, aDouble);
            }

            dataSet.setName("" + (i + 1));
            this.dataSets.add(dataSet);
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns the simulated data set.
     */
    @Override
    public DataModel getDataModel(int index) {
        return this.dataSets.get(index);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns the true graph.
     */
    @Override
    public Graph getTrueGraph(int index) {
        if (this.graphs.isEmpty()) {
            return new EdgeListGraph();
        } else {
            return this.graphs.get(index);
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns the description.
     */
    @Override
    public String getDescription() {
        return "Bayes net simulation using " + this.randomGraph.getDescription();
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns the parameters.
     */
    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();

        if (!(this.randomGraph instanceof SingleGraph)) {
            parameters.addAll(this.randomGraph.getParameters());
        }

//        if (this.pm == null) {
        parameters.addAll(BayesPm.getParameterNames());
//        }

//        if (this.im == null) {
        parameters.addAll(MlBayesIm.getParameterNames());
//        }

        parameters.add(Params.NUM_RUNS);
        parameters.add(Params.PROB_REMOVE_COLUMN);
        parameters.add(Params.DIFFERENT_GRAPHS);
        parameters.add(Params.RANDOMIZE_COLUMNS);
        parameters.add(Params.SAMPLE_SIZE);
        parameters.add(Params.SAVE_LATENT_VARS);
        parameters.add(Params.SEED);

        return parameters;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns the number of data sets.
     */
    @Override
    public int getNumDataModels() {
        return this.dataSets.size();
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns the data type.
     */
    @Override
    public DataType getDataType() {
        return DataType.Discrete;
    }

    /**
     * Simulates a dataset.
     *
     * @param graph      The graph.
     * @param parameters The parameters.
     * @return Ibid.
     */
    private DataSet simulate(Graph graph, Parameters parameters) {
        boolean saveLatentVars = parameters.getBoolean(Params.SAVE_LATENT_VARS);

        try {
            BayesIm im = this.im;

            if (im == null) {
                BayesPm pm = this.pm;

                if (pm == null) {
                    int minCategories = parameters.getInt(Params.MIN_CATEGORIES);
                    int maxCategories = parameters.getInt(Params.MAX_CATEGORIES);
                    pm = new BayesPm(graph, minCategories, maxCategories);
                    im = new MlBayesIm(pm, MlBayesIm.RANDOM);
                } else {
                    im = new MlBayesIm(pm, MlBayesIm.RANDOM);
                    this.im = im;
                }
            }
            this.ims.add(im);
            return im.simulateData(parameters.getInt(Params.SAMPLE_SIZE), saveLatentVars);
        } catch (Exception e) {
            e.printStackTrace();
            throw new IllegalArgumentException("Sorry, I couldn't simulate from that Bayes IM; perhaps not all of\n"
                    + "the parameters have been specified.");
        }
    }

    /**
     * Returns the list of Bayes IMs.
     *
     * @return Ibid.
     */
    public List<BayesIm> getBayesIms() {
        return this.ims;
    }

}
