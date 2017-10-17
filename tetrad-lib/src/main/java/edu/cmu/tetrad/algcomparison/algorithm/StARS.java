package edu.cmu.tetrad.algcomparison.algorithm;

import edu.cmu.tetrad.algcomparison.utils.TakesInitialGraph;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.ForkJoinPoolInstance;
import edu.cmu.tetrad.util.Parameters;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;

/**
 * StARS
 *
 * @author jdramsey
 */
public class StARS implements Algorithm, TakesInitialGraph {

    static final long serialVersionUID = 23L;
    private final double low;
    private final double high;
    private final String parameter;
    private Algorithm algorithm;
    private Graph initialGraph = null;
    private DataSet _dataSet;

    public StARS(Algorithm algorithm, String parameter, double low, double high) {
        if (low >= high) {
            throw new IllegalArgumentException("Must have low < high");
        }
        this.algorithm = algorithm;
        this.low = low;
        this.high = high;
        this.parameter = parameter;
    }

    @Override
    public Graph search(DataModel dataSet, Parameters parameters) {
        this._dataSet = (DataSet) dataSet;

//        int numVars = Math.min(50, ((DataSet) dataSet).getNumColumns());
//        int[] cols = new int[numVars];
//        for (int i = 0; i < numVars; i++) cols[i] = i;
        _dataSet = (DataSet) dataSet;//.subsetColumns(cols);

        double percentageB = parameters.getDouble("percentSubsampleSize");
        double tolerance = parameters.getDouble("StARS.tolerance");
        double beta = parameters.getDouble("StARS.cutoff");
        int numSubsamples = parameters.getInt("numSubsamples");

        Parameters _parameters = new Parameters(parameters);

        List<DataSet> samples = new ArrayList<>();

        for (int i = 0; i < numSubsamples; i++) {
            BootstrapSampler sampler = new BootstrapSampler();
            sampler.setWithoutReplacements(true);
            samples.add(sampler.sample(_dataSet, (int) (percentageB * _dataSet.getNumRows())));
        }

//        double pFrom = low;
//        double pTo = high;
        double maxD = Double.NEGATIVE_INFINITY;
        double _lambda = Double.NaN;

        for (double lambda = low; lambda <= high; lambda += 0.5) {
            double D = getD(parameters, parameter, lambda, samples, algorithm);
            System.out.println("lambda = " + lambda + " D = " + D);

            if (D > maxD && D < beta) {
                maxD = D;
                _lambda = lambda;
            }
        }

//        double D1 = getD(parameters, parameter, low, samples, algorithm);
//        System.out.println("D1 (low) = " + D1);
//        double D2 = getD(parameters, parameter, high, samples, algorithm);
//        System.out.println("D2 (high) = " + D2);
//
//        double lastD;
//        double pMid;
//        double pBest;
//        double bestD;
//
//        if (D1 > D2 && D1 < beta) {
//            lastD = D1;
//            pBest = low;
//            bestD = D1;
//        } else if (D2 > D1 && D2 < beta) {
//            lastD = D2;
//            pBest = high;
//            bestD = D2;
//        } else {
//            lastD = Double.NEGATIVE_INFINITY;
//            pBest = (low + high) / 2.0;
//            bestD = Double.NEGATIVE_INFINITY;
//        }
//
//        System.out.println("lastD = " + lastD);
//
//        while (abs(pFrom - pTo) > tolerance) {
//            pMid = (pFrom + pTo) / 2.0;
//            double D = getD(parameters, parameter, pMid, samples, algorithm);
//            System.out.println("pFrom = " + pFrom + " pTo = " + pTo + " pMid = " + pMid + " D = " + D);
//
//            if (D1 > D2) {
//                if (D > bestD && D < beta) {
//                    pTo = pMid;
//                    pBest = pMid;
//                    bestD = D;
//                } else {
//                    pFrom = pMid;
//                }
//            } else {
//                if (D > bestD && D < beta) {
//                    pFrom = pMid;
//                    pBest = pMid;
//                    bestD = D;
//                } else {
//                    pTo = pMid;
//                }
//            }
//
//            lastD = D;
//            System.out.println("lastD = " + lastD + " pBest = " + pBest);
//        }
//
//        if (D1 > bestD) {
//            pBest = low;
//        }
//
//        if (D2 > bestD) {
//            pBest = high;
//        }
        System.out.println("FINAL: lambda = " + _lambda + " D = " + maxD);

        System.out.println(parameter + " = " + getValue(_lambda, parameters));
        _parameters.set(parameter, getValue(_lambda, parameters));

        return algorithm.search(dataSet, _parameters);
    }

    private static double getD(Parameters params, String paramName, double paramValue, final List<DataSet> samples,
            Algorithm algorithm) {
        params.set(paramName, paramValue);

        List<Graph> graphs = new ArrayList<>();

//        for (DataSet d : samples) {
//            Graph e = GraphUtils.undirectedGraph(algorithm.search(d, params));
//            e = GraphUtils.replaceNodes(e, samples.get(0).getVariables());
//            graphs.add(e);
//        }
        final ForkJoinPool pool = ForkJoinPoolInstance.getInstance().getPool();

        class StabilityAction extends RecursiveAction {

            private int chunk;
            private int from;
            private int to;

            private StabilityAction(int chunk, int from, int to) {
                this.chunk = chunk;
                this.from = from;
                this.to = to;
            }

            @Override
            protected void compute() {
                if (to - from <= chunk) {
                    for (int s = from; s < to; s++) {
                        Graph e = algorithm.search(samples.get(s), params);
                        e = GraphUtils.replaceNodes(e, samples.get(0).getVariables());
                        graphs.add(e);
                    }
                } else {
                    final int mid = (to + from) / 2;

                    StabilityAction left = new StabilityAction(chunk, from, mid);
                    StabilityAction right = new StabilityAction(chunk, mid, to);

                    left.fork();
                    right.compute();
                    left.join();
                }
            }
        }

        final int chunk = 1;

        pool.invoke(new StabilityAction(chunk, 0, samples.size()));

        int p = samples.get(0).getNumColumns();
        List<Node> nodes = graphs.get(0).getNodes();

        double D = 0.0;
        int count = 0;

        for (int i = 0; i < p; i++) {
            for (int j = i + 1; j < p; j++) {
                double theta = 0.0;
                Node x = nodes.get(i);
                Node y = nodes.get(j);

                for (int k = 0; k < graphs.size(); k++) {
                    if (graphs.get(k).isAdjacentTo(x, y)) {
                        theta += 1.0;
                    }
                }

                theta /= graphs.size();
                double xsi = 2 * theta * (1.0 - theta);

//                if (xsi != 0){
                D += xsi;
                count++;
//                }
            }
        }

        D /= (double) count;
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
		// TODO Auto-generated method stub
		return initialGraph;
	}

	@Override
	public void setInitialGraph(Graph initialGraph) {
		// TODO Auto-generated method stub
		this.initialGraph = initialGraph;
	}

	@Override
    public void setInitialGraph(Algorithm algorithm) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

}
