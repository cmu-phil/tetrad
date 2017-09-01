package edu.cmu.tetrad.algcomparison.algorithm;

import edu.cmu.tetrad.algcomparison.utils.TakesInitialGraph;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.Parameters;
import static java.lang.Math.abs;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * First inflection point.
 *
 * @author jdramsey
 */
public class StARS2 implements Algorithm, TakesInitialGraph {

    static final long serialVersionUID = 23L;
    private final double low;
    private final double high;
    private final String parameter;
    private final double initialGuess;
    private Algorithm algorithm;
    private IKnowledge knowledge = new Knowledge2();
    private DataSet _dataSet;
    Map<Double, Double> archive;

    public StARS2(Algorithm algorithm, String parameter, double low, double high, double initialGuess) {
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
    public Graph search(DataModel dataSet, Parameters parameters) {
        this._dataSet = (DataSet) dataSet;

//        int numVars = Math.min(50, ((DataSet) dataSet).getNumColumns());
//        int[] cols = new int[numVars];
//        for (int i = 0; i < numVars; i++) cols[i] = i;
        _dataSet = (DataSet) dataSet;//.subsetColumns(cols);

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
            samples.add(sampler.sample(_dataSet, (int) (percentageB * _dataSet.getNumRows())));
        }

        double pFrom = low;
        double pTo = high;
        double pMid = high;

        double lastD = getD(parameters, parameter, high, samples, samples.size(), algorithm, archive);

        while (abs(pFrom - pTo) > tolerance) {
            pMid = (pFrom + pTo) / 2.0;
            _parameters.set(parameter, getValue(pMid, parameters));

            double D = getD(parameters, parameter, pMid, samples, samples.size(), algorithm, archive);
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
        double _p = getValue(pMid, parameters);
//        _p = Math.round(_p * 10.0) / 10.0;
        System.out.println(parameter + " = " + _p);
        _parameters.set(parameter, getValue(_p, parameters));
//
        return algorithm.search(dataSet, _parameters);
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
    private static double getD(Parameters params, String paramName, double paramValue, List<DataSet> boostraps,
            int numBootstraps, Algorithm algorithm, Map<Double, Double> archive) {
        params.set(paramName, paramValue);

        List<Graph> graphs = new ArrayList<>();

        for (DataSet d : boostraps) {
            Graph e = GraphUtils.undirectedGraph(algorithm.search(d, params));
            e = GraphUtils.replaceNodes(e, boostraps.get(0).getVariables());
            graphs.add(e);

        }

        int p = boostraps.get(0).getNumColumns();
        List<Node> nodes = graphs.get(0).getNodes();

        double D = 0.0;

        for (int i = 0; i < p; i++) {
            for (int j = i + 1; j < p; j++) {
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

        D /= (double) (p * (p - 1) / 2);
        System.out.println(paramName + " = " + paramValue + " D = " + D);
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

	@Override
	public Graph getInitialGraph() {
		return null;
	}

	@Override
	public void setInitialGraph(Graph initialGraph) {
		
	}

	@Override
    public void setInitialGraph(Algorithm algorithm) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

}
