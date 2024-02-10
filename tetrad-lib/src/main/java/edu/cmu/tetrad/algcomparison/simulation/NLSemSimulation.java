package edu.cmu.tetrad.algcomparison.simulation;

import edu.cmu.tetrad.algcomparison.graph.RandomGraph;
import edu.cmu.tetrad.algcomparison.graph.SingleGraph;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.cmu.tetrad.util.RandomUtil;
import edu.cmu.tetrad.util.StatUtils;
import org.apache.commons.math3.linear.BlockRealMatrix;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.SingularValueDecomposition;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.apache.commons.math3.linear.MatrixUtils.createRealMatrix;
import static org.apache.commons.math3.util.FastMath.*;

/**
 * NL SEM simulation.
 *
 * @author bryanandrews
 * @version $Id: $Id
 */
public class NLSemSimulation implements Simulation {

    private static final long serialVersionUID = 23L;
    private final RandomGraph randomGraph;
    private List<DataSet> dataSets = new ArrayList<>();
    private List<Graph> graphs = new ArrayList<>();

    /**
     * <p>Constructor for NLSemSimulation.</p>
     *
     * @param graph a {@link edu.cmu.tetrad.algcomparison.graph.RandomGraph} object
     */
    public NLSemSimulation(RandomGraph graph) {
        this.randomGraph = graph;
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
        List<Node> variables = graph.getNodes();

        this.dataSets = new ArrayList<>();
        this.graphs = new ArrayList<>();

        int sampleSize = parameters.getInt(Params.SAMPLE_SIZE);
        int numVars = parameters.getInt(Params.NUM_MEASURES);

        for (int i = 0; i < parameters.getInt(Params.NUM_RUNS); i++) {
            if (parameters.getBoolean(Params.DIFFERENT_GRAPHS) && i > 0) {
                graph = this.randomGraph.createGraph(parameters);
            }

            Map<Node, Integer> indices = new HashMap<>();
            for (int j = 0; j < numVars; j++) {
                indices.put(variables.get(j), j);
            }

            this.graphs.add(graph);
            RealMatrix data = new BlockRealMatrix(sampleSize, numVars);

            int errorType = parameters.getInt(Params.SIMULATION_ERROR_TYPE);

            for (int k = 0; k < numVars; k++) {
                Node x = variables.get(k);
                List<Node> Pa = graph.getParents(x);

                // Additive Error

                if (errorType == 1) {
                    double low = parameters.getDouble(Params.VAR_LOW);
                    double high = parameters.getDouble(Params.VAR_HIGH);
                    double std = sqrt(RandomUtil.getInstance().nextUniform(low, high));
                    for (int j = 0; j < sampleSize; j++) {
                        data.setEntry(j, k, RandomUtil.getInstance().nextNormal(0, std));
                    }
                } else if (errorType == 2) {
                    double low = parameters.getDouble(Params.SIMULATION_PARAM1);
                    double high = parameters.getDouble(Params.SIMULATION_PARAM1);
                    for (int j = 0; j < sampleSize; j++) {
                        data.setEntry(j, k, RandomUtil.getInstance().nextUniform(low, high));
                    }
                } else if (errorType == 3) {
                    double lambda = parameters.getDouble(Params.SIMULATION_PARAM1);
                    for (int j = 0; j < sampleSize; j++) {
                        data.setEntry(j, k, RandomUtil.getInstance().nextExponential(lambda));
                    }
                } else if (errorType == 4) {
                    double mu = parameters.getDouble(Params.SIMULATION_PARAM1);
                    double beta = parameters.getDouble(Params.SIMULATION_PARAM2);
                    for (int j = 0; j < sampleSize; j++) {
                        data.setEntry(j, k, RandomUtil.getInstance().nextGumbel(mu, beta));
                    }
                } else if (errorType == 5) {
                    double shape = parameters.getDouble(Params.SIMULATION_PARAM1);
                    double scale = parameters.getDouble(Params.SIMULATION_PARAM2);
                    for (int j = 0; j < sampleSize; j++) {
                        data.setEntry(j, k, RandomUtil.getInstance().nextGamma(shape, scale));
                    }
                }

                // Parents effect

                if (Pa.isEmpty()) continue;

                double low = parameters.getDouble(Params.COEF_LOW);
                double high = parameters.getDouble(Params.COEF_HIGH);
                double beta = RandomUtil.getInstance().nextUniform(low, high);
                double[] mu = new double[sampleSize];

                RealMatrix kernel = createRealMatrix(sampleSize, sampleSize);
                RealMatrix cov = createRealMatrix(sampleSize, sampleSize);

                for (Node z : Pa) {
                    int w = indices.get(z);
                    for (int j = 0; j < sampleSize; j++) {
                        mu[j] = beta * data.getEntry(j, w);
                        for (int l = 0; l < sampleSize; l++) {
                            kernel.addToEntry(j, l, pow(data.getEntry(j, w) - data.getEntry(l, w), 2) / Pa.size());
                        }
                    }
                }

                for (int j = 0; j < sampleSize; j++) {
                    for (int l = 0; l < sampleSize; l++) {
                        // Should the -1 be a tunable parameter?
                        cov.setEntry(j, l, exp(-1 * kernel.getEntry(j, l)));
                    }
                }

                SingularValueDecomposition svd = new SingularValueDecomposition(cov);
                RealMatrix S = svd.getS();
                RealMatrix N = createRealMatrix(sampleSize, 1);
                for (int j = 0; j < sampleSize; j++) {
                    S.setEntry(j, j, sqrt(S.getEntry(j, j)));
                    N.setEntry(j, 0, RandomUtil.getInstance().nextNormal(0, 1));
                }
                double[] X = svd.getU().multiply(S).multiply(N).getColumn(0);

                for (int j = 0; j < sampleSize; j++) {
                    data.addToEntry(j, k, mu[j] + X[j]);
                }

                data.setColumn(k, StatUtils.standardizeData(data.getColumn(k)));
            }

            List<Node> continuousVars = new ArrayList<>();
            for (Node x : variables) {
                ContinuousVariable var = new ContinuousVariable(x.getName());
                var.setNodeType(x.getNodeType());
                continuousVars.add(var);
            }

            DataSet dataSet = new BoxDataSet(new DoubleDataBox(data.getData()), continuousVars);

            if (parameters.getBoolean(Params.RANDOMIZE_COLUMNS)) {
                dataSet = DataTransforms.shuffleColumns(dataSet);
            }

            dataSet.setName(String.valueOf(i + 1));
            this.dataSets.add(dataSet);
        }
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
    public Graph getTrueGraph(int index) {
        return this.graphs.get(index);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription() {
        return "Non-Linear, SEM simulation using " + this.randomGraph.getDescription();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();

        if (!(this.randomGraph instanceof SingleGraph)) {
            parameters.addAll(this.randomGraph.getParameters());
        }

        parameters.add(Params.NUM_RUNS);
        parameters.add(Params.DIFFERENT_GRAPHS);
        parameters.add(Params.RANDOMIZE_COLUMNS);
        parameters.add(Params.SAMPLE_SIZE);
        parameters.add(Params.COEF_LOW);
        parameters.add(Params.COEF_HIGH);
        parameters.add(Params.SIMULATION_ERROR_TYPE);
        parameters.add(Params.VAR_LOW);
        parameters.add(Params.VAR_HIGH);
        parameters.add(Params.SIMULATION_PARAM1);
        parameters.add(Params.SIMULATION_PARAM2);
        parameters.add(Params.SEED);

        return parameters;
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
        return DataType.Continuous;
    }

}
