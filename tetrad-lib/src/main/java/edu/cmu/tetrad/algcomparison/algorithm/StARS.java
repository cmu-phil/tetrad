package edu.cmu.tetrad.algcomparison.algorithm;

import edu.cmu.tetrad.algcomparison.utils.TakesInitialGraph;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.Parameters;

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
    private Algorithm algorithm;
    private DataSet _dataSet;


    public StARS(Algorithm algorithm, String parameter, double low, double high) {
        if (low >= high) throw new IllegalArgumentException("Must have low < high");
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

        double percentageB = parameters.getDouble("StARS.percentageB");
        double tolerance = parameters.getDouble("StARS.tolerance");
        double cutoff = parameters.getDouble("StARS.cutoff");
        int numSubsamples = parameters.getInt("numSubsamples");

        Parameters _parameters = new Parameters(parameters);

        List<DataSet> samples = new ArrayList<>();

        for (int i = 0; i < numSubsamples; i++) {
            BootstrapSampler sampler = new BootstrapSampler();
            sampler.setWithoutReplacements(true);
            samples.add(sampler.sample(_dataSet, (int) (percentageB * _dataSet.getNumRows())));
        }

        double pFrom = low;
        double pTo = high;
        double pMid = high;

        double lastD = getD(parameters, parameter, high, samples, samples.size(), algorithm);

        while (abs(pFrom - pTo) > tolerance) {
            pMid = (pFrom + pTo) / 2.0;
            _parameters.set(parameter, getValue(pMid, parameters));

            double D = getD(parameters, parameter, pMid, samples, samples.size(), algorithm);
            System.out.println("pFrom = " + pFrom + " pTo = " + pTo + " pMid = " + pMid + " D = " + D);

            if (D > lastD && D < cutoff) {
                pTo = pMid;
            } else {
                pFrom = pMid;
            }

            lastD = D;
        }

       double _p = getValue(pMid, parameters);
        System.out.println(parameter + " = " + _p);
        _parameters.set(parameter, getValue(_p, parameters));

        return algorithm.search(dataSet, _parameters);
    }

    private static double getD(Parameters params, String paramName, double paramValue, List<DataSet> samples,
                               int numSamples, Algorithm algorithm) {
        params.set(paramName, paramValue);

        List<Graph> graphs = new ArrayList<>();

        for (DataSet d : samples) {
            Graph e = GraphUtils.undirectedGraph(algorithm.search(d, params));
            e = GraphUtils.replaceNodes(e, samples.get(0).getVariables());
            graphs.add(e);

        }

        int p = samples.get(0).getNumColumns();
        List<Node> nodes = graphs.get(0).getNodes();

        double D = 0.0;

        for (int i = 0; i < p; i++) {
            for (int j = i + 1; j < p; j++) {
                double theta = 0.0;

                for (int k = 0; k < numSamples; k++) {
                    boolean adj = graphs.get(k).isAdjacentTo(nodes.get(i), nodes.get(j));
                    theta += adj ? 1.0 : 0.0;
                }

                theta /= numSamples;
                double xsi = 2 * theta * (1.0 - theta);
                D += xsi;
            }
        }

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
