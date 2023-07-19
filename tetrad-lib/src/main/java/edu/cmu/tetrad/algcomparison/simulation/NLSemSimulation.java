package edu.cmu.tetrad.algcomparison.simulation;

import edu.cmu.tetrad.algcomparison.graph.RandomGraph;
import edu.cmu.tetrad.algcomparison.graph.SingleGraph;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.*;
import org.apache.commons.math3.distribution.MultivariateNormalDistribution;
import org.apache.commons.math3.linear.BlockRealMatrix;
import org.apache.commons.math3.linear.EigenDecomposition;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.MatrixUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.apache.commons.math3.util.FastMath.*;

/**
 * @author bryanandrews
 */
public class NLSemSimulation implements Simulation {

    static final long serialVersionUID = 23L;
    private final RandomGraph randomGraph;
    private List<DataSet> dataSets = new ArrayList<>();
    private List<Graph> graphs = new ArrayList<>();

    public NLSemSimulation(RandomGraph graph) {
        this.randomGraph = graph;
    }

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
            System.out.println("Simulating dataset #" + (i + 1));

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
                }

                if (Pa.isEmpty()) continue;

//                RealMatrix cov = new BlockRealMatrix(sampleSize, sampleSize);
//
//                for (int j = 0; j < sampleSize; j++) {
//                    for (int l = 0; l <= j; l++) {
//                        double d = 0;
//                        for (Node z : Pa) {
//                            int w = indices.get(z);
//                            d +=  pow(data.getEntry(j, w) - data.getEntry(l, w), 2);
//                        }
//                        cov.setEntry(j, l, exp(- d / Pa.size() / sampleSize));
//                        cov.setEntry(l, j, cov.getEntry(j, l));
//                    }
//                    RealMatrix XX = cov.getSubMatrix(j, j, j, j);
//                    if (j > 0) {
//                        RealMatrix XY = cov.getSubMatrix(j, j, 0, j - 1);
//                        RealMatrix YY = cov.getSubMatrix(0, j - 1, 0, j - 1);
//                        RealMatrix B = XY.multiply(MatrixUtils.inverse(YY));
//                        XX = XX.subtract(B.multiply(XY.transpose()));
//                    }
//                    System.out.println(XX);
//                }

                double low = parameters.getDouble(Params.COEF_LOW);
                double high = parameters.getDouble(Params.COEF_HIGH);
                double beta = RandomUtil.getInstance().nextUniform(low, high);
                double[] mu = new double[sampleSize];

                RealMatrix kernel = new BlockRealMatrix(sampleSize, sampleSize);
                double[][] cov = new double[sampleSize][sampleSize];

                for (Node z : Pa) {
                    int w = indices.get(z);
                    for (int j = 0; j < sampleSize; j++) {
                        mu[j] = beta * data.getEntry(j, w);
                        for (int l = 0; l < sampleSize; l++) {
                            kernel.addToEntry(j, l, abs(data.getEntry(j, w) - data.getEntry(l, w)) / Pa.size());
                        }
                    }
                }

                for (int j = 0; j < sampleSize; j++) {
                    for (int l = 0; l < sampleSize; l++) {
                        cov[j][l] = exp(-kernel.getEntry(j, l));
                    }
                }

                MultivariateNormalDistribution N = new MultivariateNormalDistribution(mu, cov);
                double[] X = N.sample();
                for (int j = 0; j < sampleSize; j++) {
                    data.addToEntry(j, k, X[j]);
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
                dataSet = DataUtils.shuffleColumns(dataSet);
            }

            dataSet.setName("" + (i + 1));
            this.dataSets.add(dataSet);
        }
    }

    @Override
    public DataModel getDataModel(int index) {
        return this.dataSets.get(index);
    }

    @Override
    public Graph getTrueGraph(int index) {
        return this.graphs.get(index);
    }

    @Override
    public String getDescription() {
        return "Non-Linear, SEM simulation using " + this.randomGraph.getDescription();
    }

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

    @Override
    public int getNumDataModels() {
        return this.dataSets.size();
    }

    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }

}