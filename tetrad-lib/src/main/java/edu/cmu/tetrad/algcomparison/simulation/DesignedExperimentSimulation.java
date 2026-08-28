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
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.LayoutUtil;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.cmu.tetrad.util.RandomUtil;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

/**
 * Simulates data with the anatomy of a designed physical experiment (the archetype is the NASA
 * Airfoil Self-Noise dataset), in contrast to an observational sample. The variables come in
 * three tiers:
 * <ol>
 * <li><b>Design factors</b> (F1, F2, ...): grid-valued exogenous inputs, each taking a small
 * number of evenly spaced levels sampled uniformly. These are SET by the experimenter, not
 * sampled from a noise distribution. Optionally, a factor's level values are coupled to an
 * earlier factor (a non-factorial design, as when larger chord lengths are tested only at
 * smaller attack angles): with probability deCoupling, a factor's value range is scaled by a
 * random earlier uncoupled factor, producing strong protocol-induced dependence among the
 * inputs. Coupling appears as a directed edge in the true graph, since that is the true
 * data-generating dependence.</li>
 * <li><b>Derived intermediates</b> (D1, ...): near-deterministic smooth functions of a subset
 * of the factors and of EARLIER derived variables (chains, like ISI/BUI feeding FWI), with
 * noise standard deviation deDerivedNoise (default 0.05, i.e., R-squared near 1 given
 * parents).</li>
 * <li><b>Responses</b> (R1, ...): the genuinely measured outputs. Each response is a function
 * of all factors and derived variables, mixing an additive part with a pairwise-interaction
 * part by weight deInteraction (interaction-heavy physics like Strouhal scaling), plus
 * heterogeneous non-Gaussian noise with standard deviation deResponseNoise.</li>
 * </ol>
 * Optionally, <b>selection on measurability</b> is applied (deSelection): the lowest
 * deSelection fraction of rows by (first response + noise) is dropped, as when rows exist only
 * where the response was above a noise floor. This induces additional dependence among the
 * inputs in the observed sample that is NOT in the true graph; the stored true graph is the
 * pre-selection DAG, so searches on selected data are expected to show extra input-input
 * edges. That phenomenon is part of what this simulation is for.
 * <p>
 * NOTE: the random graph passed to the constructor is IGNORED; the tiered structure is
 * generated internally from the deNumFactors / deNumDerived / deNumResponses parameters.
 *
 * @author josephramsey
 */
public class DesignedExperimentSimulation implements Simulation {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The random graph generator (ignored; kept for factory compatibility).
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
     * Constructs a DesignedExperimentSimulation. The given random graph is ignored; the tiered
     * design structure is generated internally from parameters.
     *
     * @param graph the RandomGraph object (ignored).
     */
    public DesignedExperimentSimulation(RandomGraph graph) {
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

        return dataSet;
    }

    /**
     * Creates simulated data and associated graphs based on the given parameters.
     *
     * @param parameters The parameters used to control the simulation process.
     * @param newModel   A flag indicating whether a new model should be created.
     */
    @Override
    public void createData(Parameters parameters, boolean newModel) {
        if (parameters.getLong(Params.SEED) != -1L) {
            RandomUtil.getInstance().setSeed(parameters.getLong(Params.SEED));
        }

        this.dataSets = new ArrayList<>();
        this.graphs = new ArrayList<>();

        for (int i = 0; i < parameters.getInt(Params.NUM_RUNS); i++) {
            simulateOne(parameters);
        }
    }

    /**
     * Simulates one run: structure, data, selection; appends to graphs and dataSets.
     *
     * @param parameters the simulation parameters.
     */
    private void simulateOne(Parameters parameters) {
        int numFactors = Math.max(2, parameters.getInt(Params.DE_NUM_FACTORS));
        int numDerived = Math.max(0, parameters.getInt(Params.DE_NUM_DERIVED));
        int numResponses = Math.max(1, parameters.getInt(Params.DE_NUM_RESPONSES));
        int minLevels = Math.max(2, parameters.getInt(Params.DE_MIN_LEVELS));
        int maxLevels = Math.max(minLevels, parameters.getInt(Params.DE_MAX_LEVELS));
        double coupling = parameters.getDouble(Params.DE_COUPLING);
        double derivedNoise = parameters.getDouble(Params.DE_DERIVED_NOISE);
        double interaction = parameters.getDouble(Params.DE_INTERACTION);
        double responseNoise = parameters.getDouble(Params.DE_RESPONSE_NOISE);
        double selection = parameters.getDouble(Params.DE_SELECTION);
        int n = parameters.getInt(Params.SAMPLE_SIZE);

        if (selection < 0 || selection >= 1) {
            throw new IllegalArgumentException("deSelection must be in [0, 1).");
        }

        RandomUtil rand = RandomUtil.getInstance();

        // Rows to generate before selection so that n survive.
        int m = selection > 0 ? (int) Math.ceil((n + 5) / (1.0 - selection)) : n;

        // ---------- Structure ----------

        List<Node> nodes = new ArrayList<>();
        int total = numFactors + numDerived + numResponses;

        for (int j = 0; j < numFactors; j++) nodes.add(new ContinuousVariable("F" + (j + 1)));
        for (int j = 0; j < numDerived; j++) nodes.add(new ContinuousVariable("D" + (j + 1)));
        for (int j = 0; j < numResponses; j++) nodes.add(new ContinuousVariable("R" + (j + 1)));

        Graph graph = new EdgeListGraph(nodes);

        // Coupling: each factor after the first is, with probability deCoupling, coupled to a
        // uniformly chosen earlier UNCOUPLED factor (so the driver's values lie in [0, 1]).
        int[] couplingDriver = new int[numFactors];
        double[] couplingSign = new double[numFactors];
        couplingDriver[0] = -1;

        for (int j = 1; j < numFactors; j++) {
            couplingDriver[j] = -1;
            if (rand.nextDouble() < coupling) {
                List<Integer> uncoupled = new ArrayList<>();
                for (int i = 0; i < j; i++) if (couplingDriver[i] == -1) uncoupled.add(i);
                if (!uncoupled.isEmpty()) {
                    couplingDriver[j] = uncoupled.get(rand.nextInt(uncoupled.size()));
                    couplingSign[j] = rand.nextDouble() < 0.5 ? -1.0 : 1.0;
                    graph.addDirectedEdge(nodes.get(couplingDriver[j]), nodes.get(j));
                }
            }
        }

        // Derived variables: parents are a random subset of factors (each with probability 0.7,
        // at least 2), plus EARLIER derived variables with probability 0.4 (chains, like
        // ISI/BUI feeding FWI).
        boolean[][] derivedParents = new boolean[numDerived][numFactors];
        boolean[][] derivedDerivedParents = new boolean[numDerived][numDerived];

        for (int d = 0; d < numDerived; d++) {
            int count = 0;
            for (int f = 0; f < numFactors; f++) {
                if (rand.nextDouble() < 0.7) {
                    derivedParents[d][f] = true;
                    count++;
                }
            }
            while (count < Math.min(2, numFactors)) {
                int f = rand.nextInt(numFactors);
                if (!derivedParents[d][f]) {
                    derivedParents[d][f] = true;
                    count++;
                }
            }
            for (int f = 0; f < numFactors; f++) {
                if (derivedParents[d][f]) {
                    graph.addDirectedEdge(nodes.get(f), nodes.get(numFactors + d));
                }
            }
            for (int d2 = 0; d2 < d; d2++) {
                if (rand.nextDouble() < 0.4) {
                    derivedDerivedParents[d][d2] = true;
                    graph.addDirectedEdge(nodes.get(numFactors + d2), nodes.get(numFactors + d));
                }
            }
        }

        // Responses: every factor and every derived variable is a parent.
        for (int r = 0; r < numResponses; r++) {
            int rj = numFactors + numDerived + r;
            for (int f = 0; f < numFactors; f++) graph.addDirectedEdge(nodes.get(f), nodes.get(rj));
            for (int d = 0; d < numDerived; d++)
                graph.addDirectedEdge(nodes.get(numFactors + d), nodes.get(rj));
        }

        LayoutUtil.defaultLayout(graph);

        // ---------- Data ----------

        double[][] data = new double[m][total];

        // Factors: grid-valued. Uncoupled: value = level / (L - 1) in [0, 1]. Coupled: the value
        // range is scaled by the driver, value = (level / (L - 1)) * (1 - 0.7 * driverNorm) for
        // negative coupling (range shrinks as the driver grows, as with attack angle vs. chord),
        // or with (0.3 + 0.7 * driverNorm) for positive coupling.
        int[] levels = new int[numFactors];
        for (int f = 0; f < numFactors; f++) {
            levels[f] = minLevels + rand.nextInt(maxLevels - minLevels + 1);
        }

        for (int r = 0; r < m; r++) {
            for (int f = 0; f < numFactors; f++) {
                int lvl = rand.nextInt(levels[f]);
                double base = lvl / (double) (levels[f] - 1);
                int drv = couplingDriver[f];
                if (drv >= 0) {
                    double dn = data[r][drv]; // driver is uncoupled, so already in [0, 1]
                    double scale = couplingSign[f] < 0 ? (1.0 - 0.7 * dn) : (0.3 + 0.7 * dn);
                    base *= scale;
                }
                data[r][f] = base;
            }
        }

        for (int f = 0; f < numFactors; f++) standardizeColumn(data, f);

        // Derived: near-deterministic smooth functions of their factor parents, with the same
        // additive/interaction mixture as responses but tiny noise.
        for (int d = 0; d < numDerived; d++) {
            List<Integer> ps = new ArrayList<>();
            for (int f = 0; f < numFactors; f++) if (derivedParents[d][f]) ps.add(f);
            for (int d2 = 0; d2 < d; d2++) {
                if (derivedDerivedParents[d][d2]) ps.add(numFactors + d2);
            }
            fillFunctionColumn(data, numFactors + d, ps, interaction, derivedNoise, rand);
        }

        // Responses: functions of all factors and derived variables.
        List<Integer> allInputs = new ArrayList<>();
        for (int j = 0; j < numFactors + numDerived; j++) allInputs.add(j);

        for (int r = 0; r < numResponses; r++) {
            fillFunctionColumn(data, numFactors + numDerived + r, allInputs, interaction,
                    responseNoise, rand);
        }

        // ---------- Selection ----------

        if (selection > 0) {
            int firstResponse = numFactors + numDerived;
            double[] score = new double[m];
            for (int r = 0; r < m; r++) {
                score[r] = data[r][firstResponse] + 0.3 * rand.nextGaussian(0, 1);
            }

            double[] sorted = score.clone();
            java.util.Arrays.sort(sorted);
            double threshold = sorted[(int) Math.floor(selection * m)];

            double[][] kept = new double[m][];
            int k = 0;
            for (int r = 0; r < m; r++) {
                if (score[r] >= threshold) kept[k++] = data[r];
            }

            int keep = Math.min(n, k);
            double[][] out = new double[keep][];
            System.arraycopy(kept, 0, out, 0, keep);
            data = out;
        } else if (m > n) {
            double[][] out = new double[n][];
            System.arraycopy(data, 0, out, 0, n);
            data = out;
        }

        DataSet dataSet = new BoxDataSet(new DoubleDataBox(data), nodes);
        dataSet = postProcess(parameters, dataSet);

        this.graphs.add(graph);
        this.dataSets.add(dataSet);
    }

    /**
     * Fills column j with a function of the given parent columns: a mixture of an additive part
     * (per-parent random monotone maps with random coefficients) and a pairwise-interaction part
     * (products of the mapped parents), by the given interaction weight; plus noise from a
     * randomly chosen standardized non-Gaussian family. The column is standardized at the end.
     *
     * @param data        the data matrix.
     * @param j           the column to fill.
     * @param parents     the parent column indices.
     * @param interaction the interaction mixing weight, in [0, 1].
     * @param noiseSd     the noise standard deviation relative to the unit-variance systematic
     *                    part.
     * @param rand        the random utility.
     */
    private void fillFunctionColumn(double[][] data, int j, List<Integer> parents,
                                    double interaction, double noiseSd, RandomUtil rand) {
        int rows = data.length;
        int p = parents.size();

        // Mapped parent columns.
        double[][] g = new double[p][];
        for (int k = 0; k < p; k++) {
            double[] z = new double[rows];
            for (int r = 0; r < rows; r++) z[r] = data[r][parents.get(k)];
            g[k] = monotoneMix(z, 0.5, rand);
        }

        // Additive part.
        double[] add = new double[rows];
        for (int k = 0; k < p; k++) {
            double b = rand.nextUniform(0.4, 0.9) * (rand.nextDouble() < 0.5 ? -1 : 1);
            for (int r = 0; r < rows; r++) add[r] += b * g[k][r];
        }
        standardizeInPlace(add);

        // Pairwise-interaction part.
        double[] sys;
        if (interaction > 0 && p >= 2) {
            double[] inter = new double[rows];
            for (int k = 0; k < p; k++) {
                for (int l = k + 1; l < p; l++) {
                    double c = rand.nextUniform(0.3, 0.7) * (rand.nextDouble() < 0.5 ? -1 : 1);
                    for (int r = 0; r < rows; r++) inter[r] += c * g[k][r] * g[l][r];
                }
            }
            standardizeInPlace(inter);

            sys = new double[rows];
            for (int r = 0; r < rows; r++) {
                sys[r] = (1.0 - interaction) * add[r] + interaction * inter[r];
            }
            standardizeInPlace(sys);
        } else {
            sys = add;
        }

        int family = rand.nextInt(4);
        for (int r = 0; r < rows; r++) {
            data[r][j] = sys[r] + noiseSd * standardizedNoise(rand, family);
        }
        standardizeColumn(data, j);
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
                return rand.nextT(7) / Math.sqrt(7.0 / 5.0);
            case 2:
                return rand.nextExponential(1.0) - 1.0;
            case 3:
                return (rand.nextGumbel(0.0, 1.0) - 0.5772156649015329) / (Math.PI / Math.sqrt(6.0));
            default:
                return rand.nextGaussian(0, 1);
        }
    }

    /**
     * Returns g(u) = (1 - w) u + w h(softclip(u)), standardized, where u is first standardized,
     * h is a randomly chosen strictly increasing map (sinh, asinh, signed power, or randomly
     * flipped exponential, itself standardized), and the soft clip is 4 tanh(u / 4). A
     * nonnegative mixture of strictly increasing maps is strictly increasing.
     *
     * @param uIn  the input column.
     * @param w    the mixing weight of the nonlinear component, in [0, 1].
     * @param rand the random utility.
     * @return the transformed, standardized column.
     */
    private double[] monotoneMix(double[] uIn, double w, RandomUtil rand) {
        int rows = uIn.length;

        double[] u = uIn.clone();
        standardizeInPlace(u);

        double clipB = 4.0;
        double[] uc = new double[rows];
        for (int r = 0; r < rows; r++) uc[r] = clipB * Math.tanh(u[r] / clipB);

        int type = rand.nextInt(4);
        double a;
        double[] h = new double[rows];

        switch (type) {
            case 0:
                a = rand.nextUniform(0.3, 0.7);
                for (int r = 0; r < rows; r++) h[r] = Math.sinh(a * uc[r]);
                break;
            case 1:
                a = rand.nextUniform(1.0, 3.0);
                for (int r = 0; r < rows; r++) h[r] = asinh(a * uc[r]);
                break;
            case 2:
                a = rand.nextUniform(0.5, 2.0);
                for (int r = 0; r < rows; r++) h[r] = Math.signum(uc[r]) * Math.pow(Math.abs(uc[r]), a);
                break;
            default:
                a = rand.nextUniform(0.4, 1.1);
                boolean flip = rand.nextDouble() < 0.5;
                for (int r = 0; r < rows; r++) {
                    h[r] = flip ? -Math.exp(-a * uc[r]) : Math.exp(a * uc[r]);
                }
                break;
        }

        standardizeInPlace(h);

        double[] out = new double[rows];
        for (int r = 0; r < rows; r++) out[r] = (1.0 - w) * u[r] + w * h[r];
        standardizeInPlace(out);

        return out;
    }

    /**
     * Standardizes the given column of the data matrix in place. No-op if constant.
     *
     * @param data the data matrix.
     * @param j    the column index.
     */
    private void standardizeColumn(double[][] data, int j) {
        int rows = data.length;

        double mean = 0.0;
        for (double[] row : data) mean += row[j];
        mean /= rows;

        double var = 0.0;
        for (double[] row : data) var += (row[j] - mean) * (row[j] - mean);
        var /= (rows - 1);
        double sd = Math.sqrt(var);
        if (sd == 0) return;

        for (int r = 0; r < rows; r++) data[r][j] = (data[r][j] - mean) / sd;
    }

    /**
     * Standardizes the given array in place to zero mean and unit variance. No-op if constant.
     *
     * @param x the array.
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
     * Inverse hyperbolic sine.
     *
     * @param x the argument.
     * @return asinh(x).
     */
    private double asinh(double x) {
        return Math.log(x + Math.sqrt(x * x + 1.0));
    }

    /**
     * Returns the true (pre-selection) graph at the specified index.
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
        return "Designed Experiment (grid factors, derived intermediates, interaction-heavy "
               + "responses, optional selection); the random graph is ignored";
    }

    /**
     * Returns the short name of the simulation.
     *
     * @return The short name of the simulation.
     */
    public String getShortName() {
        return "DoE";
    }

    /**
     * Retrieves the parameters required for the simulation. Graph parameters are omitted since
     * the structure is generated internally.
     *
     * @return A list of String names representing the parameters.
     */
    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();

        parameters.add(Params.DE_NUM_FACTORS);
        parameters.add(Params.DE_NUM_DERIVED);
        parameters.add(Params.DE_NUM_RESPONSES);
        parameters.add(Params.DE_MIN_LEVELS);
        parameters.add(Params.DE_MAX_LEVELS);
        parameters.add(Params.DE_COUPLING);
        parameters.add(Params.DE_DERIVED_NOISE);
        parameters.add(Params.DE_INTERACTION);
        parameters.add(Params.DE_RESPONSE_NOISE);
        parameters.add(Params.DE_SELECTION);
        parameters.add(Params.MEASUREMENT_VARIANCE);
        parameters.add(Params.NUM_RUNS);
        parameters.add(Params.PROB_REMOVE_COLUMN);
        parameters.add(Params.DIFFERENT_GRAPHS);
        parameters.add(Params.RANDOMIZE_COLUMNS);
        parameters.add(Params.SAMPLE_SIZE);
        parameters.add(Params.STANDARDIZE);
        parameters.add(Params.SEED);

        return parameters;
    }

    /**
     * Returns the random graph class used in the simulation (ignored for structure).
     *
     * @return The class of the random graph.
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
