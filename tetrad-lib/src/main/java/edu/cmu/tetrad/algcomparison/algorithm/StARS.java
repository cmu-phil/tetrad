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
    private IKnowledge knowledge = new Knowledge2();

    public StARS(Algorithm algorithm, String parameter, double low, double high) {
        if (low >= high) throw new IllegalArgumentException("Must have low < high");
        this.algorithm = algorithm;
        this.low = low;
        this.high = high;
        this.parameter = parameter;
    }

    @Override
    public Graph search(DataModel dataSet, Parameters parameters) {
        Parameters _parameters = new Parameters(parameters);

        // Draw 5 bootstrap samples.
        List<DataSet> boostraps = new ArrayList<>();

        int numBootstraps = 5;

        for (int i = 0; i < numBootstraps; i++) {
            BootstrapSampler sampler = new BootstrapSampler();
            sampler.setWithoutReplacements(true);
            DataSet dataSet1 = (DataSet) dataSet;
            boostraps.add(sampler.sample(dataSet1, dataSet1.getNumRows() / 2));
        }

        double pFrom = low;
        double pTo = high;
        double pMid = high;

        while (abs(pFrom - pTo) > 1) {
            pMid = pFrom + pTo / 2;
            _parameters.set(parameter, pMid);

            if (getD(_parameters, boostraps, numBootstraps) < 0.05) {
                pTo = pMid;
            } else {
                pFrom = pMid;
            }
        }

        System.out.println(parameter + " = " + pMid);

        return algorithm.search(dataSet, _parameters);
    }

    private double getD(Parameters _parameters, List<DataSet> boostraps, int numBootstraps) {
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

    private double getValue(double value, Parameters parameters) {
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
        return parameters;
    }
}
