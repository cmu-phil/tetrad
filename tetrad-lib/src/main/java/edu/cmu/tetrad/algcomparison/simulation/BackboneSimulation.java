/// ////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2025 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
// and Richard Scheines.                                                     //
//                                                                           //
// This program is free software: you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation, either version 3 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program.  If not, see <https://www.gnu.org/licenses/>.    //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.algcomparison.simulation;

import edu.cmu.tetrad.algcomparison.graph.RandomGraph;
import edu.cmu.tetrad.algcomparison.graph.SingleGraph;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.LayoutUtil;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.cmu.tetrad.util.RandomUtil;

import java.io.Serial;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Simulates data intended to resemble the statistical character of typical real observational
 * datasets, in contrast to worst-case nonlinear simulations. Three features distinguish it:
 * <ol>
 * <li><b>Backbone effect-size structure.</b> Each edge is independently designated "strong" with
 * probability bbPropStrong, else "weak". Coefficients are drawn on a <i>standardized</i> scale
 * (every variable is constructed to have unit variance, with the exact covariance tracked
 * analytically), so the strong and weak coefficient ranges are interpretable in correlation
 * units. This reproduces the situation seen in real data, where a few strong dependencies
 * form an obvious backbone and many weak dependencies sit near or below the detection
 * thresholds of conservative scores.</li>
 * <li><b>Monotone marginal distortion.</b> After the standardized linear SEM is generated, each
 * observed column is passed through a random smooth strictly monotone map (a mixture of the
 * identity with sinh, asinh, or signed-power distortions, mixing weight bbDistortion). Because
 * each map is invertible and applied per-variable, the conditional independence structure of
 * the observed variables is exactly that of the underlying SEM, while the margins become
 * skewed and heavy- or light-tailed in the way real measurement scales are.</li>
 * <li><b>Optional structural transmission nonlinearity.</b> If bbEdgeNonlinearity &gt; 0, each
 * parent's value is passed through its own random strictly monotone map (mixed with the
 * identity by that weight) before being summed into the child. Unlike the marginal
 * distortion, this curvature is structural: it cannot be removed by any per-variable
 * transformation of the observed data. The default is 0 (linear latent core).</li>
 * <li><b>Heterogeneous non-Gaussian noise.</b> If bbNonGaussian is set, each node's exogenous
 * noise is drawn from a family randomly chosen per node (Gaussian, scaled t(7), centered
 * exponential, or standardized Gumbel), all standardized to unit variance.</li>
 * </ol>
 * The true standardized coefficient of each edge and the set of strong edges are recorded per
 * run and exposed via {@link #getEdgeCoefficients(int)} and {@link #getStrongEdges(int)}, so
 * that recovery statistics can be stratified by edge strength (e.g., from py-tetrad via JPype).
 *
 * @author josephramsey
 */
public class BackboneSimulation implements Simulation {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The random graph generator.
     */
    private final RandomGraph randomGraph;

    /**
     * The data sets.
     */
    private List<DataSet> dataSets = new ArrayList<>();

    /**
     * The graphs.
     */
    private List<Graph> graphs = new ArrayList<>();

    /**
     * Per-run sets of edges designated "strong".
     */
    private List<Set<Edge>> strongEdges = new ArrayList<>();

    /**
     * Per-run maps from edge to true standardized coefficient.
     */
    private List<Map<Edge, Double>> edgeCoefficients = new ArrayList<>();

    /**
     * Constructs a BackboneSimulation object with the given RandomGraph object.
     *
     * @param graph the RandomGraph object used for simulation.
     * @throws NullPointerException if graph is null.
     */
    public BackboneSimulation(RandomGraph graph) {
        if (graph == null) throw new NullPointerException("Graph is null.");
        this.randomGraph = graph;
    }

    /**
     * Performs post-processing on a given dataset based on the provided parameters.
     *
     * @param parameters The parameters used for post-processing.
     * @param dataSet    The dataset to be post-processed.
     * @return The post-processed dataset.
     */
    private static DataSet postProcess(Parameters parameters, DataSet dataSet) {
        if (parameters.getBoolean(Params.STANDARDIZE)) {
            dataSet = DataTransforms.standardizeData(dataSet);
        }

        double variance = parameters.getDouble(Params.MEASUREMENT_VARIANCE);

        if (variance > 0) {
            for (int k = 0; k < dataSet.getNumRows(); k++) {
                for (int j = 0; j < dataSet.getNumColumns(); j++) {
                    double d = dataSet.getDouble(k, j);
                    double norm = RandomUtil.getInstance().nextGaussian(0, Math.sqrt(variance));
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

        if (!parameters.getBoolean(Params.SAVE_LATENT_VARS)) {
            dataSet = DataTransforms.restrictToMeasured(dataSet);
        }

        return dataSet;
    }

    /**
     * Creates simulated data and associated graphs based on the given parameters.
     *
     * @param parameters The parameters used to control the simulation process.
     * @param newModel   A flag indicating whether a new model should be created for the simulation.
     */
    @Override
    public void createData(Parameters parameters, boolean newModel) {
        if (parameters.getLong(Params.SEED) != -1L) {
            RandomUtil.getInstance().setSeed(parameters.getLong(Params.SEED));
        }

        this.dataSets = new ArrayList<>();
        this.graphs = new ArrayList<>();
        this.strongEdges = new ArrayList<>();
        this.edgeCoefficients = new ArrayList<>();

        for (int i = 0; i < parameters.getInt(Params.NUM_RUNS); i++) {
            Graph graph = this.randomGraph.createGraph(parameters);

            List<Node> continuousVars = new ArrayList<>();

            for (Node node : graph.getNodes()) {
                ContinuousVariable var = new ContinuousVariable(node.getName());
                var.setNodeType(node.getNodeType());
                continuousVars.add(var);
            }

            graph = GraphUtils.replaceNodes(graph, continuousVars);
            LayoutUtil.defaultLayout(graph);

            DataSet dataSet = simulate(graph, parameters);

            dataSet = postProcess(parameters, dataSet);

            graphs.add(graph);
            dataSets.add(dataSet);
        }
    }

    /**
     * Simulates a single dataset from the given graph.
     *
     * @param graph      the true DAG.
     * @param parameters the simulation parameters.
     * @return the simulated dataset (all variables, latents included; latents are removed in
     * post-processing unless saveLatentVars is set).
     */
    private DataSet simulate(Graph graph, Parameters parameters) {
        int sampleSize = parameters.getInt(Params.SAMPLE_SIZE);
        double propStrong = parameters.getDouble(Params.BB_PROP_STRONG);
        double strongLow = parameters.getDouble(Params.BB_STRONG_COEF_LOW);
        double strongHigh = parameters.getDouble(Params.BB_STRONG_COEF_HIGH);
        double weakLow = parameters.getDouble(Params.BB_WEAK_COEF_LOW);
        double weakHigh = parameters.getDouble(Params.BB_WEAK_COEF_HIGH);
        double distortion = parameters.getDouble(Params.BB_DISTORTION);
        double edgeNonlinearity = parameters.getDouble(Params.BB_EDGE_NONLINEARITY);
        boolean nonGaussian = parameters.getBoolean(Params.BB_NON_GAUSSIAN);

        if (strongHigh < strongLow || weakHigh < weakLow) {
            throw new IllegalArgumentException("Coefficient ranges must have low <= high.");
        }

        RandomUtil rand = RandomUtil.getInstance();

        List<Node> order = graph.paths().getValidOrder(graph.getNodes(), true);
        int n = order.size();

        Map<Node, Integer> idx = new HashMap<>();
        for (int j = 0; j < n; j++) idx.put(order.get(j), j);

        double[][] data = new double[sampleSize][n];

        Set<Edge> strong = new HashSet<>();
        Map<Edge, Double> coefs = new HashMap<>();

        for (int j = 0; j < n; j++) {
            Node node = order.get(j);
            List<Node> parents = graph.getParents(node);

            int p = parents.size();
            int[] pIdx = new int[p];
            double[] b = new double[p];
            Edge[] pEdges = new Edge[p];

            for (int k = 0; k < p; k++) {
                Node parent = parents.get(k);
                pIdx[k] = idx.get(parent);

                boolean isStrong = rand.nextDouble() < propStrong;
                double mag = isStrong
                        ? rand.nextUniform(strongLow, strongHigh)
                        : rand.nextUniform(weakLow, weakHigh);
                double sign = rand.nextDouble() < 0.5 ? -1.0 : 1.0;
                b[k] = sign * mag;

                pEdges[k] = graph.getEdge(parent, node);
                if (isStrong && pEdges[k] != null) strong.add(pEdges[k]);
            }

            // Each parent contributes through its own transmission function. With
            // bbEdgeNonlinearity = 0 this is the parent's (unit-variance) column itself and
            // the model is linear in the latents; with bbEdgeNonlinearity > 0 the parent's
            // value is passed through a random strictly monotone map first, mixed with the
            // identity by that weight. This is structural additive nonlinearity: it cannot be
            // removed by any per-variable transformation of the observed data.
            double[] contribution = new double[sampleSize];

            for (int k = 0; k < p; k++) {
                double[] z = new double[sampleSize];
                for (int r = 0; r < sampleSize; r++) z[r] = data[r][pIdx[k]];

                double[] t = (edgeNonlinearity > 0)
                        ? monotoneMix(z, edgeNonlinearity, rand)
                        : z;

                for (int r = 0; r < sampleSize; r++) {
                    contribution[r] += b[k] * t[r];
                }
            }

            // Empirical variance of the parent contribution. (With per-edge nonlinearity the
            // covariance of the transmitted parents is not available analytically, so the
            // normalization is empirical throughout; at these sample sizes the difference
            // from the analytic version in the linear case is negligible.)
            double vC = 0.0;

            if (p > 0) {
                double mC = 0.0;
                for (double v : contribution) mC += v;
                mC /= sampleSize;
                for (double v : contribution) vC += (v - mC) * (v - mC);
                vC /= (sampleSize - 1);
            }

            // Noise variance chosen so the raw variance is near 1; a floor keeps the model
            // from becoming (near-)deterministic when a node has many strong parents.
            double s2 = Math.max(1.0 - vC, 0.05);

            // Per-node noise family, standardized to unit variance.
            int family = nonGaussian ? rand.nextInt(4) : 0;

            double sd = Math.sqrt(s2);

            for (int r = 0; r < sampleSize; r++) {
                data[r][j] = contribution[r] + sd * standardizedNoise(rand, family);
            }

            // Standardize the new column empirically to unit variance, and record the true
            // standardized coefficients (rescaled by the same factor).
            double m = 0.0;
            for (int r = 0; r < sampleSize; r++) m += data[r][j];
            m /= sampleSize;

            double v = 0.0;
            for (int r = 0; r < sampleSize; r++) v += (data[r][j] - m) * (data[r][j] - m);
            v /= (sampleSize - 1);
            double colSd = Math.sqrt(v);

            for (int r = 0; r < sampleSize; r++) data[r][j] = (data[r][j] - m) / colSd;

            double c = 1.0 / colSd;

            for (int k = 0; k < p; k++) {
                if (pEdges[k] != null) coefs.put(pEdges[k], c * b[k]);
            }
        }

        // Post-nonlinear monotone marginal distortion, per column. Each map is strictly
        // increasing and applied to a single variable, so the conditional independence
        // structure of the observed variables is exactly that of the underlying SEM.
        if (distortion > 0) {
            for (int j = 0; j < n; j++) {
                distortColumn(data, j, distortion, rand);
            }
        }

        this.strongEdges.add(strong);
        this.edgeCoefficients.add(coefs);

        List<Node> variables = new ArrayList<>(order);
        return new BoxDataSet(new DoubleDataBox(data), variables);
    }

    /**
     * Draws one unit-variance noise value from the given family.
     *
     * @param rand   the random utility.
     * @param family 0 = Gaussian, 1 = scaled t(7), 2 = centered exponential, 3 = standardized
     *               Gumbel.
     * @return the noise draw.
     */
    private double standardizedNoise(RandomUtil rand, int family) {
        switch (family) {
            case 1:
                // t(7) has variance 7/5.
                return rand.nextT(7) / Math.sqrt(7.0 / 5.0);
            case 2:
                // Exponential(1) has mean 1 and variance 1.
                return rand.nextExponential(1.0) - 1.0;
            case 3:
                // Gumbel(0, 1) has mean gamma_E and variance pi^2/6.
                return (rand.nextGumbel(0.0, 1.0) - 0.5772156649015329) / (Math.PI / Math.sqrt(6.0));
            default:
                return rand.nextGaussian(0, 1);
        }
    }

    /**
     * Returns g(u) = (1 - w) u + w h(softclip(u)), standardized empirically to zero mean and unit
     * variance, where u is assumed approximately standardized, h is a randomly chosen strictly
     * increasing map (sinh, asinh, or signed power, itself empirically standardized), and the
     * soft clip is B tanh(u / B) with B = 4, so heavy-tailed inputs do not blow up through
     * tail-expanding maps. A nonnegative mixture of strictly increasing maps is strictly
     * increasing, so g is invertible.
     *
     * @param u    the (approximately standardized) input column.
     * @param w    the mixing weight of the nonlinear component, in [0, 1].
     * @param rand the random utility.
     * @return the transformed, standardized column.
     */
    private double[] monotoneMix(double[] u, double w, RandomUtil rand) {
        int rows = u.length;

        // Soft-clip the input to the nonlinear map at about +/-4 sd.
        double clipB = 4.0;
        double[] uc = new double[rows];
        for (int r = 0; r < rows; r++) uc[r] = clipB * Math.tanh(u[r] / clipB);

        // Choose a strictly increasing distortion. The first three are odd (symmetric
        // S-curves); the exponential is convex and asymmetric, giving the log-like /
        // exp-like curvature typical of real measurement relationships.
        int type = rand.nextInt(4);
        double a;
        double[] h = new double[rows];

        switch (type) {
            case 0:
                // Tail expansion (light center, heavy tails).
                a = rand.nextUniform(0.3, 0.7);
                for (int r = 0; r < rows; r++) h[r] = Math.sinh(a * uc[r]);
                break;
            case 1:
                // Tail compression (log-like measurement scales).
                a = rand.nextUniform(1.0, 3.0);
                for (int r = 0; r < rows; r++) h[r] = asinh(a * uc[r]);
                break;
            case 2:
                // Signed power; p < 1 expands the center, p > 1 expands the tails.
                a = rand.nextUniform(0.5, 2.0);
                for (int r = 0; r < rows; r++) h[r] = Math.signum(uc[r]) * Math.pow(Math.abs(uc[r]), a);
                break;
            default:
                // Exponential: strictly increasing, convex, asymmetric. Randomly flipped so
                // convex and concave curvature both occur.
                a = rand.nextUniform(0.4, 1.1);
                boolean flip = rand.nextDouble() < 0.5;
                for (int r = 0; r < rows; r++) {
                    h[r] = flip ? -Math.exp(-a * uc[r]) : Math.exp(a * uc[r]);
                }
                break;
        }

        // Rescale h empirically to unit variance so the mixing weight is meaningful.
        standardizeInPlace(h);

        double[] g = new double[rows];
        for (int r = 0; r < rows; r++) g[r] = (1.0 - w) * u[r] + w * h[r];
        standardizeInPlace(g);

        return g;
    }

    /**
     * Standardizes the given column in place to zero mean and unit variance. No-op if the
     * column is constant.
     *
     * @param x the column.
     */
    private void standardizeInPlace(double[] x) {
        int rows = x.length;

        double mean = 0.0;
        for (double d : x) mean += d;
        mean /= rows;

        double var = 0.0;
        for (double d : x) var += (d - mean) * (d - mean);
        var /= (rows - 1);
        double sd = Math.sqrt(var);
        if (sd == 0) return;

        for (int r = 0; r < rows; r++) x[r] = (x[r] - mean) / sd;
    }

    /**
     * Applies a random smooth strictly monotone distortion to column j of the data, in place,
     * using {@link #monotoneMix(double[], double, RandomUtil)} on the empirically standardized
     * column.
     *
     * @param data the data matrix.
     * @param j    the column to distort.
     * @param s    the mixing weight of the nonlinear component, in [0, 1].
     * @param rand the random utility.
     */
    private void distortColumn(double[][] data, int j, double s, RandomUtil rand) {
        int rows = data.length;

        double[] u = new double[rows];
        for (int r = 0; r < rows; r++) u[r] = data[r][j];
        standardizeInPlace(u);

        double[] g = monotoneMix(u, s, rand);
        for (int r = 0; r < rows; r++) data[r][j] = g[r];
    }

    /**
     * Inverse hyperbolic sine.
     *
     * @param x the argument.
     * @return asinh(x).
     */
    private double asinh(double x) {
        return Math.log(x + Math.sqrt(x * x + 1.0));
    }

    /**
     * Returns the set of edges designated "strong" for the run at the given index.
     *
     * @param index the run index.
     * @return the strong edges for that run.
     */
    public Set<Edge> getStrongEdges(int index) {
        return new HashSet<>(this.strongEdges.get(index));
    }

    /**
     * Returns the map from edge to true standardized coefficient for the run at the given index.
     *
     * @param index the run index.
     * @return the coefficient map for that run.
     */
    public Map<Edge, Double> getEdgeCoefficients(int index) {
        return new HashMap<>(this.edgeCoefficients.get(index));
    }

    /**
     * Returns the true graph at the specified index.
     *
     * @param index The index of the desired true graph.
     * @return The true graph at the specified index.
     */
    @Override
    public Graph getTrueGraph(int index) {
        return this.graphs.get(index);
    }

    /**
     * Returns the number of data models.
     *
     * @return The number of data sets to simulate.
     */
    @Override
    public int getNumDataModels() {
        return this.dataSets.size();
    }

    /**
     * Returns the data model at the specified index.
     *
     * @param index The index of the desired simulated data set.
     * @return The data model at the specified index.
     */
    @Override
    public DataModel getDataModel(int index) {
        return this.dataSets.get(index);
    }

    /**
     * Returns the data type of the data set.
     *
     * @return The type of the data set.
     */
    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }

    /**
     * Returns the description of the simulation.
     *
     * @return a short, one-line description of the simulation.
     */
    public String getDescription() {
        return "Backbone SEM (standardized strong/weak edges, monotone margins, non-Gaussian noise)";
    }

    /**
     * Returns the short name of the simulation.
     *
     * @return The short name of the simulation.
     */
    public String getShortName() {
        return "BB-SEM";
    }

    /**
     * Retrieves the parameters required for the simulation.
     *
     * @return A list of String names representing the parameters.
     */
    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();

        if (!(this.randomGraph instanceof SingleGraph)) {
            parameters.addAll(this.randomGraph.getParameters());
        }

        parameters.add(Params.BB_PROP_STRONG);
        parameters.add(Params.BB_STRONG_COEF_LOW);
        parameters.add(Params.BB_STRONG_COEF_HIGH);
        parameters.add(Params.BB_WEAK_COEF_LOW);
        parameters.add(Params.BB_WEAK_COEF_HIGH);
        parameters.add(Params.BB_DISTORTION);
        parameters.add(Params.BB_EDGE_NONLINEARITY);
        parameters.add(Params.BB_NON_GAUSSIAN);
        parameters.add(Params.MEASUREMENT_VARIANCE);
        parameters.add(Params.NUM_RUNS);
        parameters.add(Params.PROB_REMOVE_COLUMN);
        parameters.add(Params.DIFFERENT_GRAPHS);
        parameters.add(Params.RANDOMIZE_COLUMNS);
        parameters.add(Params.SAMPLE_SIZE);
        parameters.add(Params.SAVE_LATENT_VARS);
        parameters.add(Params.STANDARDIZE);
        parameters.add(Params.SEED);

        return parameters;
    }

    /**
     * Returns the random graph class used in the simulation.
     *
     * @return The class of the random graph used in the simulation.
     */
    @Override
    public Class<? extends RandomGraph> getRandomGraphClass() {
        return randomGraph.getClass();
    }

    /**
     * Returns the class of the current simulation.
     *
     * @return The simulation class.
     */
    @Override
    public Class<? extends Simulation> getSimulationClass() {
        return getClass();
    }
}
