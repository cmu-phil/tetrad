package edu.cmu.tetrad.algcomparison.simulation;

import edu.cmu.tetrad.algcomparison.graph.RandomGraph;
import edu.cmu.tetrad.algcomparison.graph.SingleGraph;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.sem.Parameter;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.stat.correlation.Covariance;
import edu.cmu.tetrad.util.*;
import org.apache.commons.math3.linear.EigenDecomposition;

import java.util.ArrayList;
import java.util.List;

import static java.lang.Math.pow;
import static java.lang.Math.sqrt;

/**
 * @author bryan andrews
 */
public class UnifCorrSimulation implements Simulation {

    static final long serialVersionUID = 23L;
    private final RandomGraph randomGraph;
    private List<DataSet> dataSets = new ArrayList<>();
    private List<Graph> graphs = new ArrayList<>();

    public UnifCorrSimulation(RandomGraph graph) {
        this.randomGraph = graph;
    }

    @Override
    public void createData(Parameters parameters, boolean newModel) {
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

            if (parameters.getBoolean(Params.STANDARDIZE)) {
                dataSet = DataUtils.standardizeData(dataSet);
            }

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
        return "Uniform correlation, Gaussian simulation using " + this.randomGraph.getDescription();
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
        parameters.add(Params.SAVE_LATENT_VARS);
        parameters.add(Params.STANDARDIZE);

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

    private Matrix getAdjMat(Graph graph) {

        List<Node> nodes = graph.getNodes();
        int p = nodes.size();

        Matrix G = new Matrix(p, p);

        for (int i = 0; i < p; i++) {
            for (int j = 0; j < i; j++) {
                if (graph.isChildOf(nodes.get(i), nodes.get(j))) {
                    G.set(i, j, 1);
                } else {
                    G.set(i, j, 0);
                }
            }
        }

        return G;
    }

    private Matrix genUnifCorr(Graph graph, int n) {

        int a = 0;
        int p = graph.getNumNodes();

        Matrix G = getAdjMat(graph);
        for (int i = 0; i < p; i++) {
            for (int j = 0; j < i; j++) {
                if (G.get(j, i) == 1) {
                    throw new IllegalArgumentException("Matrix must be lower triangular.");
                }
            }
        }

        Matrix R = Matrix.identity(p);

        double[][] B = new double[p][p];
        double[] e = new double[p];
        for (int i = 0; i < p; i++) {
            for (int j = 0; j < p; j++) {
                B[i][j] = 0;
            }
            e[i] = 1.0;
        }

        double b = a + p / 2.0;

        if (G.get(1, 0) > 0) {
            double r = 2.0 * RandomUtil.getInstance().nextBeta(b, b) - 1.0;
            R.set(1, 0, r);
            R.set(0, 1, r);
            B[1][0] = r;
            e[1] -= pow(r, 2);
        }

        for (int i = 2; i < p; i++) {

            b -= 0.5;

            Vector w = new Vector(i);
            double norm = 0;
            int k = 0;

            for (int j = 0; j < i; j++) {
                if (G.get(i, j) > 0) {
                    w.set(j, RandomUtil.getInstance().nextNormal(0, 1));
                    norm += pow(w.get(j), 2);
                    k++;
                } else {
                    w.set(j, 0);
                }
            }

            if (k > 0) {
                double r = sqrt(RandomUtil.getInstance().nextBeta(k / 2.0, b));
                w = w.scalarMult(r / sqrt(norm));
                e[i] -= pow(r, 2);
            }

            Matrix A = R.getPart(0, i - 1, 0, i - 1).sqrt();
            Vector z = A.times(w);
            Vector bs = A.inverse().times(w);
            for (int j = 0; j < i; j++) {
                R.set(i, j, z.get(j));
                R.set(j, i, z.get(j));
                B[i][j] = G.get(i, j) * bs.get(j);;
            }
        }

        Matrix X = new Matrix(n, p);
        double x;

        for (int i = 0; i < p; i++) {
            e[i] = sqrt(e[i]);
            for (int j = 0; j < n; j++) {
                x = RandomUtil.getInstance().nextNormal(0, e[i]);
                for (int k = 0; k < i; k++) {
                    if (G.get(i, k) > 0) {
                        x += B[i][k] * X.get(j, k);
                    }
                }
                X.set(j, i, x);
            }
        }

        return X;
    }

    private DataSet simulate(Graph graph, Parameters parameters) {

        int n = parameters.getInt(Params.SAMPLE_SIZE);
        boolean latentDataSaved = parameters.getBoolean(Params.SAVE_LATENT_VARS);

        Matrix X = genUnifCorr(graph, n);

        List<Node> continuousVars = new ArrayList<>();

        for (Node node : graph.getNodes()) {
            ContinuousVariable var = new ContinuousVariable(node.getName());
            var.setNodeType(node.getNodeType());
            continuousVars.add(var);
        }

        DataSet fullDataSet = new BoxDataSet(new DoubleDataBox(X.toArray()), continuousVars);

        if (latentDataSaved) {
            return fullDataSet;
        } else {
            return DataUtils.restrictToMeasured(fullDataSet);
        }
    }

}
