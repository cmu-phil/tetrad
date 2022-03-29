package edu.cmu.tetrad.algcomparison.algorithm;

import edu.cmu.tetrad.algcomparison.utils.TakesExternalGraph;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.Parameters;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static java.lang.Math.abs;

/**
 * First inflection point.
 *
 * @author jdramsey
 */
public class StARS2 implements Algorithm, TakesExternalGraph {

    static final long serialVersionUID = 23L;
    private final double low;
    private final double high;
    private final String parameter;
    private final double initialGuess;
    private final Algorithm algorithm;
    private final IKnowledge knowledge = new Knowledge2();
    private DataSet _dataSet;
    Map<Double, Double> archive;

    public StARS2(final Algorithm algorithm, final String parameter, final double low, final double high, final double initialGuess) {
        if (low >= high) {
            throw new IllegalArgumentException("Must have low < high");
        }
        this.algorithm = algorithm;
        this.low = low;
        this.high = high;
        this.initialGuess = initialGuess;
        this.parameter = parameter;
    }

    @Override
    public Graph search(final DataModel dataSet, final Parameters parameters) {
        this._dataSet = (DataSet) dataSet;

//        int numVars = Math.min(50, ((DataSet) dataSet).getNumColumns());
//        int[] cols = new int[numVars];
//        for (int i = 0; i < numVars; i++) cols[i] = i;
        this._dataSet = (DataSet) dataSet;//.subsetColumns(cols);

        final double percentageB = parameters.getDouble("StARS.percentageB");
        final double tolerance = parameters.getDouble("StARS.tolerance");
        final double cutoff = parameters.getDouble("StARS.cutoff");
        final int numSubsamples = parameters.getInt("numSubsamples");

        final Parameters _parameters = new Parameters(parameters);

        // Draw 5 samples without replacement.
        final List<DataSet> samples = new ArrayList<>();

        for (int i = 0; i < numSubsamples; i++) {
            final BootstrapSampler sampler = new BootstrapSampler();
            sampler.setWithoutReplacements(true);
            samples.add(sampler.sample(this._dataSet, (int) (percentageB * this._dataSet.getNumRows())));
        }

        double pFrom = this.low;
        double pTo = this.high;
        double pMid = this.high;

        double lastD = getD(parameters, this.parameter, this.high, samples, samples.size(), this.algorithm, this.archive);

        while (abs(pFrom - pTo) > tolerance) {
            pMid = (pFrom + pTo) / 2.0;
            _parameters.set(this.parameter, getValue(pMid, parameters));

            final double D = getD(parameters, this.parameter, pMid, samples, samples.size(), this.algorithm, this.archive);
            System.out.println("pFrom = " + pFrom + " pTo = " + pTo + " pMid = " + pMid + " D = " + D);

            if (D > lastD && D < cutoff) {
                pTo = pMid;
            } else {
                pFrom = pMid;
            }

            lastD = D;
        }
//
//        archive = new HashMap<>();
//
//        MultivariateOptimizer search = new PowellOptimizer(tolerance, tolerance);
//        FittingFunction f = new FittingFunction(samples, numSubsamples, _parameters, algorithm, cutoff, low, high, parameter,
//                _dataSet, archive);
//        PointValuePair p = search.optimize(
//                new InitialGuess(new double[]{initialGuess}),
//                new ObjectiveFunction(f),
//                GoalType.MAXIMIZE,
//                MaxEval.unlimited()
//        );
//
//        double _p = getValue(p.getPoint()[0], parameters);
        final double _p = getValue(pMid, parameters);
//        _p = Math.round(_p * 10.0) / 10.0;
        System.out.println(this.parameter + " = " + _p);
        _parameters.set(this.parameter, getValue(_p, parameters));
//
        return this.algorithm.search(dataSet, _parameters);
    }

    //    static class FittingFunction implements MultivariateFunction {
//
//        private final List<DataSet> samples;
//        private final int numSamples;
//        private final Algorithm algorithm;
//        private final double cutoff;
//        private final double low;
//        private final double high;
//        private final String paramName;
//        private final DataSet _dataSet;
//        private Parameters params;
//        Map<Double, Double> archive = new HashMap<>();
//
//        /**
//         * Constructs a new CoefFittingFunction for the given Sem.
//         */
//        public FittingFunction(List<DataSet> samples, int numSamples, Parameters params, Algorithm algorithm,
//                               double cutoff, double low, double high, String paramName, DataSet _dataSet,
//                               Map<Double, Double> archive
//        ) {
//            this.samples = samples;
//            this.numSamples = numSamples;
//            this.params = params;
//            this.algorithm = algorithm;
//            this.cutoff = cutoff;
//            this.low = low;
//            this.high = high;
//            this.paramName = paramName;
//            this._dataSet = _dataSet;
//            this.archive = archive;
//        }
//
//        /**
//         * Computes the maximum likelihood function value for the given
//         * parameter values as given by the optimizer. These values are mapped to
//         * parameter values.
//         */
//
//        @Override
//        public double value(double[] parameters) {
//            double paramValue = parameters[0];
//            paramValue = getValue(paramValue, params);
////            paramValue = Math.round(paramValue * 10.0) / 10.0;
//            if (paramValue < low) return -10000;
//            if (paramValue > high) return -10000;
//            if (archive.containsKey(paramValue)) {
//                return archive.get(paramValue);
//            }
//            double D = getD(params, paramName, paramValue, samples, numSamples, algorithm, archive);
//            if (D > cutoff) return -10000;
//            archive.put(paramValue, D);
//            return D;
//        }
//    }
    private static double getD(final Parameters params, final String paramName, final double paramValue, final List<DataSet> boostraps,
                               final int numBootstraps, final Algorithm algorithm, final Map<Double, Double> archive) {
        params.set(paramName, paramValue);

        final List<Graph> graphs = new ArrayList<>();

        for (final DataSet d : boostraps) {
            Graph e = GraphUtils.undirectedGraph(algorithm.search(d, params));
            e = GraphUtils.replaceNodes(e, boostraps.get(0).getVariables());
            graphs.add(e);

        }

        final int p = boostraps.get(0).getNumColumns();
        final List<Node> nodes = graphs.get(0).getNodes();

        double D = 0.0;

        for (int i = 0; i < p; i++) {
            for (int j = i + 1; j < p; j++) {
                double theta = 0.0;

                for (int k = 0; k < numBootstraps; k++) {
                    final boolean adj = graphs.get(k).isAdjacentTo(nodes.get(i), nodes.get(j));
                    theta += adj ? 1.0 : 0.0;
                }

                theta /= numBootstraps;
                final double xsi = 2 * theta * (1.0 - theta);
                D += xsi;
            }
        }

        D /= (double) (p * (p - 1) / 2);
        System.out.println(paramName + " = " + paramValue + " D = " + D);
        return D;
    }

    private static double getValue(final double value, final Parameters parameters) {
        if (parameters.getBoolean("logScale")) {
            return Math.round(Math.pow(10.0, value) * 1000000000.0) / 1000000000.0;
        } else {
            return Math.round(value * 1000000000.0) / 1000000000.0;
        }
    }

    @Override
    public Graph getComparisonGraph(final Graph graph) {
        return this.algorithm.getComparisonGraph(graph);
    }

    @Override
    public String getDescription() {
        return "StARS for " + this.algorithm.getDescription() + " parameter = " + this.parameter;
    }

    @Override
    public DataType getDataType() {
        return this.algorithm.getDataType();
    }

    @Override
    public List<String> getParameters() {
        final List<String> parameters = this.algorithm.getParameters();
        parameters.add("depth");
        parameters.add("verbose");
        parameters.add("StARS.percentageB");
        parameters.add("StARS.tolerance");
        parameters.add("StARS.cutoff");
        parameters.add("numSubsamples");

        return parameters;
    }

    @Override
    public Graph getExternalGraph() {
        return null;
    }

    @Override
    public void setExternalGraph(final Graph externalGraph) {

    }

    @Override
    public void setExternalGraph(final Algorithm algorithm) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

}
