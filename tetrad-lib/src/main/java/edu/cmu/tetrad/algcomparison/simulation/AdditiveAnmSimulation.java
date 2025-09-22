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
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.LayoutUtil;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.sem.AdditiveAnmSimulator;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.cmu.tetrad.util.RandomUtil;
import org.apache.commons.math3.distribution.BetaDistribution;
import org.apache.commons.math3.distribution.NormalDistribution;
import org.apache.commons.math3.distribution.RealDistribution;
import org.apache.commons.math3.distribution.TDistribution;
import org.apache.commons.math3.util.FastMath;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Additive Nonlinear SEM (ANM-style) simulator wrapper for algcomparison.
 *
 * Model:
 *   X_j = sum_{k in pa(j)} f_{jk}(X_k) + eps_j
 *
 * This version exposes compact, high-level controls:
 *  - ANM_PRESET:       "smooth_rbf" | "wavy_rbf" | "tanh" | "poly"   (family & base richness)
 *  - ANM_NONLINEARITY: [0,1] slider (scales units-per-edge and edge amplitude)
 *  - ANM_NOISE_KIND:   "beta" | "gaussian" | "student_t"
 *  - ANM_NOISE_STRENGTH: [0,1] slider (σ of noise after centering/standardizing family base)
 *
 * Keeps existing post-processing knobs: STANDARDIZE, AM_RESCALE_MIN/MAX, etc.
 */
public class AdditiveAnmSimulation implements Simulation {

    @Serial
    private static final long serialVersionUID = 1L;

    private final RandomGraph randomGraph;

    private List<DataSet> dataSets = new ArrayList<>();
    private List<Graph> graphs = new ArrayList<>();

    public AdditiveAnmSimulation(RandomGraph graph) {
        if (graph == null) throw new NullPointerException("Graph is null.");
        this.randomGraph = graph;
    }

    // ------------------------ Simulation API ------------------------

    private static RealDistribution buildNoise(Parameters parameters, String noiseKind, double sigma) {
        switch (noiseKind) {
            case "gaussian": {
                // N(0, sigma^2)
                return new NormalDistribution(0.0, Math.max(1e-8, sigma));
            }
            case "student_t": {
                // df ∈ (2, 30], larger df ~ more Gaussian. Use slider’s inverse for df.
                double strength = clamp01(parameters.getDouble(Params.ANM_NOISE_STRENGTH, 0.4));
                double df = 3.0 + 27.0 * (1.0 - strength); // 30 -> near Gaussian at low strength
                if (df <= 2.1) df = 2.1; // ensure finite variance
                TDistribution base = new TDistribution(df);
                // Base t has sd = sqrt(df/(df-2)). Scale so final sd = sigma.
                final double baseSd = Math.sqrt(df / (df - 2.0));
                return new TransformedOnlyForSampling(base, 0.0, baseSd, sigma);
            }
            case "beta":
            default: {
                // Default Beta(a,b) then center/standardize to unit sd and scale to sigma
                double a = 2.0;//parameters.getDouble(Params.AM_BETA_ALPHA, 2.0);
                double b = 5.0;//parameters.getDouble(Params.AM_BETA_BETA, 5.0);
                BetaDistribution base = new BetaDistribution(a, b);
                double mu = a / (a + b);
                double var = (a * b) / ((a + b) * (a + b) * (a + b + 1.0));
                double sd = Math.sqrt(Math.max(var, 1e-12));
                return new TransformedOnlyForSampling(base, mu, sd, sigma);
            }
        }
    }

    private static double clamp(double v, double lo, double hi) {
        return (v < lo) ? lo : (v > hi) ? hi : v;
    }

    private static double clamp01(double v) {
        return clamp(v, 0.0, 1.0);
    }

    private static int clampInt(int v, int lo, int hi) {
        return (v < lo) ? lo : (v > hi) ? hi : v;
    }

    @Override
    public void createData(Parameters parameters, boolean newModel) {
        // Seed RNG used by post-processing (same pattern as your other wrappers)
        if (parameters.getLong(Params.SEED) != -1L) {
            RandomUtil.getInstance().setSeed(parameters.getLong(Params.SEED));
        }

        this.dataSets = new ArrayList<>();
        this.graphs = new ArrayList<>();

        for (int run = 0; run < parameters.getInt(Params.NUM_RUNS); run++) {
            Graph graph = this.randomGraph.createGraph(parameters);

            // ensure continuous vars with same names/types
            List<Node> vars = new ArrayList<>();
            for (Node node : graph.getNodes()) {
                ContinuousVariable v = new ContinuousVariable(node.getName());
                v.setNodeType(node.getNodeType());
                vars.add(v);
            }
            graph = GraphUtils.replaceNodes(graph, vars);
            LayoutUtil.defaultLayout(graph);

            DataSet ds = simulate(graph, parameters);
            ds = postProcess(parameters, ds);

            graphs.add(graph);
            dataSets.add(ds);
        }
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
        return "Additive Nonlinear SEM (ANM) using " + this.randomGraph.getDescription();
    }

    // ------------------------ Internals ------------------------

    public String getShortName() {
        return "ANM";
    }

    @Override
    public List<String> getParameters() {
        List<String> params = new ArrayList<>();

        if (!(this.randomGraph instanceof SingleGraph)) {
            params.addAll(this.randomGraph.getParameters());
        }

        // minimal user-facing
        params.add(Params.ANM_PRESET);
        params.add(Params.ANM_NONLINEARITY);
        params.add(Params.ANM_NOISE_KIND);
        params.add(Params.ANM_NOISE_STRENGTH);
//        params.add(Params.ANM_UNITS_PER_EDGE);

        // sampling & post-process
        params.add(Params.SAMPLE_SIZE);
        params.add(Params.STANDARDIZE);
        params.add(Params.AM_RESCALE_MIN);
        params.add(Params.AM_RESCALE_MAX);
        params.add(Params.SEED);

        // book-keeping
        params.add(Params.NUM_RUNS);
        params.add(Params.PROB_REMOVE_COLUMN);
        params.add(Params.DIFFERENT_GRAPHS);
        params.add(Params.RANDOMIZE_COLUMNS);
        params.add(Params.SAVE_LATENT_VARS);

        return params;
    }

    @Override
    public Class<? extends RandomGraph> getRandomGraphClass() {
        return randomGraph.getClass();
    }

    @Override
    public Class<? extends Simulation> getSimulationClass() {
        return getClass();
    }

    private DataSet simulate(Graph graph, Parameters parameters) {
        return runModel(graph, parameters);
    }

    // ------------------------ small helpers ------------------------

    private DataSet postProcess(Parameters parameters, DataSet dataSet) {
        if (parameters.getBoolean(Params.STANDARDIZE, false)) {
            dataSet = DataTransforms.standardizeData(dataSet);
        }

        double variance = parameters.getDouble(Params.MEASUREMENT_VARIANCE, 0.0);
        if (variance > 0) {
            for (int r = 0; r < dataSet.getNumRows(); r++) {
                for (int c = 0; c < dataSet.getNumColumns(); c++) {
                    double d = dataSet.getDouble(r, c);
                    double n = RandomUtil.getInstance().nextGaussian(0, FastMath.sqrt(variance));
                    dataSet.setDouble(r, c, d + n);
                }
            }
        }

        if (parameters.getBoolean(Params.RANDOMIZE_COLUMNS, false)) {
            dataSet = DataTransforms.shuffleColumns(dataSet);
        }

        double pRemove = parameters.getDouble(Params.PROB_REMOVE_COLUMN, 0.0);
        if (pRemove > 0) {
            dataSet = DataTransforms.removeRandomColumns(dataSet, pRemove);
        }

        dataSet = DataTransforms.restrictToMeasured(dataSet);

        return dataSet;
    }

//    private DataSet runModel(Graph graph, Parameters parameters) {
//        // ---------- 1) Read compact knobs (with defaults) ----------
//        final int presetIndex = 2;// clampInt(parameters.getInt(Params.ANM_PRESET, 2), 1, 4);
//        final int noiseKindIndex = 1;// clampInt(parameters.getInt(Params.ANM_NOISE_KIND, 1), 1, 3);
//
//        String preset = switch (presetIndex) {
//            case 1 -> "smooth_rbf";
//            case 2 -> "wavy_rbf";
//            case 3 -> "tanh";
//            case 4 -> "polynomial";
//            default -> "wavy_rbf";
//        };
//
//        final String noiseKind = switch (noiseKindIndex) {
//            case 1 -> "beta";
//            case 2 -> "gaussian";
//            case 3 -> "student_t";
//            default -> "beta";
//        };
//
//        final double nonlin = clamp01(parameters.getDouble(Params.ANM_NONLINEARITY, 0.6));
//        final double noiseStrength = clamp01(parameters.getDouble(Params.ANM_NOISE_STRENGTH, 0.4));
//
//        final int N = parameters.getInt(Params.SAMPLE_SIZE, 200);
//        final boolean standardizeParents = parameters.getBoolean(Params.STANDARDIZE, true);
//        final double rescaleMin = parameters.getDouble(Params.AM_RESCALE_MIN, -3.0);
//        final double rescaleMax = parameters.getDouble(Params.AM_RESCALE_MAX, 3.0);
//
//        // ---------- 2) Preset -> base family & base richness/amplitude ----------
//        AdditiveAnmSimulator.Family family = switch (preset) {
//            case "smooth_rbf" -> AdditiveAnmSimulator.Family.RBF;
//            case "wavy_rbf" -> AdditiveAnmSimulator.Family.RBF;
//            case "tanh" -> AdditiveAnmSimulator.Family.TANH;
//            case "polynomial" -> AdditiveAnmSimulator.Family.POLY;
//            default -> AdditiveAnmSimulator.Family.RBF; // fallback
//        };
//
//        int baseUnits = switch (preset) {
//            case "smooth_rbf" -> 6;
//            case "wavy_rbf" -> 10;
//            case "tanh" -> 8;
//            case "polynomial" -> 5;
//            default -> 8;
//        };
//
//        double baseEdgeScale = switch (preset) {
//            case "smooth_rbf" -> 0.8;
//            case "wavy_rbf" -> 1.0;
//            case "tanh" -> 0.9;
//            case "polynomial" -> 0.7;
//            default -> 0.9;
//        };
//
//        // ---------- 3) Nonlinearity slider -> adjust units & scale ----------
//        int unitsPerEdge = clampInt((int) Math.round(baseUnits + 6.0 * nonlin), 3, 20);
//        double edgeScale = clamp(baseEdgeScale + 0.8 * (nonlin - 0.5), 0.2, 2.0);
//
////        unitsPerEdge = parameters.getInt(Params.ANM_UNITS_PER_EDGE, unitsPerEdge);
//
//        // ---------- 5) Noise ----------
//        final double sigma = 0.2 + 1.0 * noiseStrength; // maps [0,1] -> [0.2, 1.2]
//        RealDistribution noiseDist = buildNoise(parameters, noiseKind, sigma);
//
//        // ---------- 6) Build generator ----------
//        AdditiveAnmSimulator gen = new AdditiveAnmSimulator(
//                graph,
//                N,
//                noiseDist,
//                rescaleMin,
//                rescaleMax
//        )
//                .setFunctionFamily(family)
//                .setNumUnitsPerEdge(unitsPerEdge)
//                .setInputStandardize(standardizeParents)
//                .setEdgeScale(edgeScale);
//
//        long seed = parameters.getLong(Params.SEED);
//        if (seed != -1L) gen.setSeed(seed);
//
//        return gen.generate();
//    }

    // --- DROP-IN REPLACEMENT ---
//    private DataSet runModel(Graph graph, Parameters parameters) {
//        // Stronger default nonlinearity & lower masking by noise
//        final double nonlin = clamp01(parameters.getDouble(Params.ANM_NONLINEARITY, 0.85));
//        final double noiseStrength = clamp01(parameters.getDouble(Params.ANM_NOISE_STRENGTH, 0.25));
//
//        final int N = parameters.getInt(Params.SAMPLE_SIZE, 200);
//
//        // Key change: DON'T standardize parents so tanh saturates more often
//        final boolean standardizeParents = false;
//
//        final double rescaleMin = parameters.getDouble(Params.AM_RESCALE_MIN, -3.0);
//        final double rescaleMax = parameters.getDouble(Params.AM_RESCALE_MAX,  3.0);
//
//        // Force a highly nonlinear family
//        final AdditiveAnmSimulator.Family family = AdditiveAnmSimulator.Family.TANH;
//
//        // Make functions much “wigglier”: more units + stronger amplitude
//        // (These caps keep generation numerically stable.)
//        final int unitsPerEdge = 32;//clampInt((int) Math.round(12 + 12 * nonlin), 10, 32);
//        final double edgeScale  = 3.0;//clamp(1.2 + 1.0 * nonlin, 0.5, 3.0);
//
//        // Keep noise modest so nonlinearity is visible (Gaussian here)
//        // Map [0,1] -> sigma in ~[0.12, 0.72]
//        final double sigma = 0.12 + 0.60 * noiseStrength;
//        final RealDistribution noiseDist = new NormalDistribution(0.0, Math.max(1e-8, sigma));
//
//        AdditiveAnmSimulator gen = new AdditiveAnmSimulator(
//                graph,
//                N,
//                noiseDist,
//                rescaleMin,
//                rescaleMax
//        )
//                .setFunctionFamily(family)
//                .setNumUnitsPerEdge(unitsPerEdge)
//                .setInputStandardize(standardizeParents) // deliberate: stronger tanh saturation
//                .setEdgeScale(edgeScale);
//
//        long seed = parameters.getLong(Params.SEED);
//        if (seed != -1L) gen.setSeed(seed);
//
//        return gen.generate();
//    }

// --- DROP-IN: hyper-nonlinear ANM generation ---
private DataSet runModel(Graph graph, Parameters parameters) {
    // lock in strong nonlinearity & modest noise (visible curves)
    final double nonlin = 0.9;                     // ignore slider; keep it high
    final double noiseStrength = 0.20;             // lighter noise shows shape

    final int N = parameters.getInt(Params.SAMPLE_SIZE, 200);

    // KEY: do NOT standardize parents → more saturation/curvature
    final boolean standardizeParents = false;

    // widen the output window a bit so we don’t over-compress
    final double rescaleMin = parameters.getDouble(Params.AM_RESCALE_MIN, -4.0);
    final double rescaleMax = parameters.getDouble(Params.AM_RESCALE_MAX,  4.0);

    // family: wavy RBF feels “more curved” than tanh in scatter plots
    final AdditiveAnmSimulator.Family family = AdditiveAnmSimulator.Family.RBF;

    // more units + stronger amplitude
    final int    unitsPerEdge = 28;                // rich basis (cap keeps it stable)
    final double edgeScale    = 2.4;               // big but not explosive

    // light, slightly heavy-tailed noise to preserve wiggles
    final double sigma = 0.10 + 0.40 * noiseStrength;  // ~0.18 here
    final double df    = 5.0;                           // gentle heavy tails
    final RealDistribution noiseDist =
            new org.apache.commons.math3.distribution.TDistribution(df) {
                @Override public double sample() {
                    // standardize base t to unit sd, then scale to sigma
                    double x = super.sample();
                    double sd = Math.sqrt(df / (df - 2.0));
                    return (x / sd) * sigma;
                }
            };

    AdditiveAnmSimulator gen = new AdditiveAnmSimulator(
            graph,
            N,
            noiseDist,
            rescaleMin,
            rescaleMax
    )
            .setFunctionFamily(family)
            .setNumUnitsPerEdge(unitsPerEdge)
            .setInputStandardize(standardizeParents)   // << keep false
            .setEdgeScale(edgeScale);

    long seed = parameters.getLong(Params.SEED);
    if (seed != -1L) gen.setSeed(seed);

    return gen.generate();
}

    // Transform wrapper that only implements sampling (the simulator only calls sample()).
    private static final class TransformedOnlyForSampling implements RealDistribution {
        private final RealDistribution base;
        private final double muBase;
        private final double sdBase;
        private final double sigma;

        TransformedOnlyForSampling(RealDistribution base, double muBase, double sdBase, double sigma) {
            this.base = base;
            this.muBase = muBase;
            this.sdBase = Math.max(sdBase, 1e-12);
            this.sigma = Math.max(sigma, 1e-12);
        }

        @Override
        public void reseedRandomGenerator(long seed) {
            base.reseedRandomGenerator(seed);
        }

        @Override
        public double sample() {
            double x = base.sample();
            // Standardize base to mean 0, sd 1 (approx), then scale to sigma
            return (x - muBase) / sdBase * sigma;
        }

        @Override
        public double[] sample(int sampleSize) {
            double[] out = new double[sampleSize];
            for (int i = 0; i < sampleSize; i++) out[i] = sample();
            return out;
        }

        // Everything else is unused by the simulator; keep minimal stubs.
        @Override
        public double density(double x) {
            throw new UnsupportedOperationException();
        }

        @Override
        public double cumulativeProbability(double x) {
            throw new UnsupportedOperationException();
        }

        @Override
        public double cumulativeProbability(double x0, double x1) {
            throw new UnsupportedOperationException();
        }

        @Override
        public double probability(double v) {
            throw new UnsupportedOperationException();
        }

        @Override
        public double inverseCumulativeProbability(double p) {
            throw new UnsupportedOperationException();
        }

        @Override
        public double getNumericalMean() {
            throw new UnsupportedOperationException();
        }

        @Override
        public double getNumericalVariance() {
            throw new UnsupportedOperationException();
        }

        @Override
        public double getSupportLowerBound() {
            throw new UnsupportedOperationException();
        }

        @Override
        public double getSupportUpperBound() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isSupportLowerBoundInclusive() {
            return false;
        }

        @Override
        public boolean isSupportUpperBoundInclusive() {
            return false;
        }

        @Override
        public boolean isSupportConnected() {
            return true;
        }
    }
}