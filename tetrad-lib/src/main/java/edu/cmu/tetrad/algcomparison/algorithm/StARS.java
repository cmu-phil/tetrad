package edu.cmu.tetrad.algcomparison.algorithm;

import edu.cmu.tetrad.algcomparison.utils.TakesInitialGraph;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.Parameters;
import org.apache.commons.math3.analysis.MultivariateFunction;
import org.apache.commons.math3.optim.InitialGuess;
import org.apache.commons.math3.optim.MaxEval;
import org.apache.commons.math3.optim.PointValuePair;
import org.apache.commons.math3.optim.nonlinear.scalar.GoalType;
import org.apache.commons.math3.optim.nonlinear.scalar.MultivariateOptimizer;
import org.apache.commons.math3.optim.nonlinear.scalar.ObjectiveFunction;
import org.apache.commons.math3.optim.nonlinear.scalar.noderiv.PowellOptimizer;

import java.util.ArrayList;
import java.util.List;

import static java.lang.Math.abs;

/**
 * First inflection point.
 *
 * @author jdramsey
 */
public class StARS implements Algorithm, TakesInitialGraph {
    static final long serialVersionUID = 23L;
    private final double low;
    private final double high;
    private final String parameter;
    private final double initialGuess;
    private Algorithm algorithm;
    private IKnowledge knowledge = new Knowledge2();

    public StARS(Algorithm algorithm, String parameter, double low, double high, double initialGuess) {
        if (low >= high) throw new IllegalArgumentException("Must have low < high");
        this.algorithm = algorithm;
        this.low = low;
        this.high = high;
        this.initialGuess = initialGuess;
        this.parameter = parameter;
    }

    @Override
    public Graph search(DataModel dataSet, Parameters parameters) {
        double percentageB = parameters.getDouble("StARS.percentageB");
        double tolerance = parameters.getDouble("StARS.tolerance");
        double cutoff = parameters.getDouble("StARS.cutoff");
        int numSubsamples = parameters.getInt("numSubsamples");

        Parameters _parameters = new Parameters(parameters);

        // Draw 5 samples without replacement.
        List<DataSet> samples = new ArrayList<>();

        for (int i = 0; i < numSubsamples; i++) {
            BootstrapSampler sampler = new BootstrapSampler();
            sampler.setWithoutReplacements(true);
            DataSet dataSet1 = (DataSet) dataSet;
            samples.add(sampler.sample(dataSet1, (int) (percentageB * dataSet1.getNumRows())));
        }

//        double pFrom = low;
//        double pTo = high;
//        double pMid = high;
//
//        double lastD = Double.NEGATIVE_INFINITY;
//
//        while (abs(pFrom - pTo) > tolerance) {
//            pMid = (pFrom + pTo) / 2.0;
//            _parameters.set(parameter, getValue(pMid, parameters));
//
//            double D = getD(_parameters, samples, numSubsamples, algorithm);
//            System.out.println("pFrom = " + pFrom + " pTo = " + pTo + " pMid = " + pMid + " D = " + D);
//
//            if (D < cutoff && D > lastD) {
//                pTo = pMid;
//            } else {
//                pFrom = pMid;
//            }
//
//            lastD = D;
//        }

        MultivariateOptimizer search = new PowellOptimizer(tolerance, tolerance);
        FittingFunction f = new FittingFunction(samples, numSubsamples, _parameters, algorithm, cutoff, low, high, parameter);
        PointValuePair p = search.optimize(
                new InitialGuess(new double[]{initialGuess}),
                new ObjectiveFunction(f),
                GoalType.MAXIMIZE,
                new MaxEval(100000)
        );

        System.out.println(parameter + " = " + getValue(p.getPoint()[0], parameters));
        _parameters.set(parameter, getValue(p.getPoint()[0], parameters));

        return algorithm.search(dataSet, _parameters);
    }

    static class FittingFunction implements MultivariateFunction {

        private final List<DataSet> samples;
        private final int numSamples;
        private final Algorithm algorithm;
        private final double cutoff;
        private final double low;
        private final double high;
        private final String paramName;
        private Parameters params;

        /**
         * Constructs a new CoefFittingFunction for the given Sem.
         */
        public FittingFunction(List<DataSet> samples, int numSamples, Parameters params, Algorithm algorithm,
                               double cutoff, double low, double high, String paramName) {
            this.samples = samples;
            this.numSamples = numSamples;
            this.params = params;
            this.algorithm = algorithm;
            this.cutoff = cutoff;
            this.low = low;
            this.high = high;
            this.paramName = paramName;
        }

        /**
         * Computes the maximum likelihood function value for the given
         * parameter values as given by the optimizer. These values are mapped to
         * parameter values.
         */

        @Override
        public double value(double[] parameters) {
            double parameter = parameters[0];
            if (parameter < low) return -10000;
            if (parameter > high) return -10000;
            parameter = getValue(parameter, params);
            params.set(paramName, parameter);
            double D = getD(params, samples, numSamples, algorithm);
            System.out.println(paramName + " = " + parameter + " D = " + D);
            if (D > cutoff) return -10000;
            return D;
        }
    }


    private static double getD(Parameters _parameters, List<DataSet> boostraps, int numBootstraps, Algorithm algorithm) {
        List<Graph> graphs = new ArrayList<>();

        for (DataSet _dataSet : boostraps) {
            graphs.add(GraphUtils.undirectedGraph(algorithm.search(_dataSet, _parameters)));
        }

        List<Node> nodes = graphs.get(0).getNodes();

        double D = 0.0;

        for (int i = 0; i < nodes.size(); i++) {
            for (int j = i + 1; j < nodes.size(); j++) {
                double theta = 0.0;

                for (int k = 0; k < numBootstraps; k++) {
                    boolean adj = graphs.get(k).isAdjacentTo(nodes.get(i), nodes.get(j));
                    theta += adj ? 1.0 : 0.0;
                }

                theta /= numBootstraps;
                double xsi = 2 * theta * (1.0 - theta);
                D += xsi;
            }
        }

        int p = nodes.size();
        D /= (double) (p * (p - 1) / 2);
        return D;
    }

    private static double getValue(double value, Parameters parameters) {
        if (parameters.getBoolean("logScale")) {
            return Math.round(Math.pow(10.0, value) * 1000000000.0) / 1000000000.0;
        } else {
            return Math.round(value * 1000000000.0) / 1000000000.0;
        }
    }

    @Override
    public Graph getComparisonGraph(Graph graph) {
        return algorithm.getComparisonGraph(graph);
    }

    @Override
    public String getDescription() {
        return "StARS for " + algorithm.getDescription() + " parameter = " + parameter;
    }

    @Override
    public DataType getDataType() {
        return algorithm.getDataType();
    }

    @Override
    public List<String> getParameters() {
        List<String> parameters = algorithm.getParameters();
        parameters.add("depth");
        parameters.add("verbose");
        parameters.add("StARS.percentageB");
        parameters.add("StARS.tolerance");
        parameters.add("StARS.cutoff");
        parameters.add("numSubsamples");

        return parameters;
    }
}
