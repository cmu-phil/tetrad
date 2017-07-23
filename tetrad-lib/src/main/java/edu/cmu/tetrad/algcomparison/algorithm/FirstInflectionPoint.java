package edu.cmu.tetrad.algcomparison.algorithm;

import edu.cmu.tetrad.algcomparison.utils.TakesInitialGraph;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.IKnowledge;
import edu.cmu.tetrad.data.Knowledge2;
import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.util.Parameters;

import java.util.List;
import java.util.Set;

/**
 * First inflection point.
 *
 * @author jdramsey
 */
public class FirstInflectionPoint implements Algorithm, TakesInitialGraph {
    static final long serialVersionUID = 23L;
    private final double low;
    private final double high;
    private final double increment;
    private final String parameter;
    private Algorithm algorithm;
    private IKnowledge knowledge = new Knowledge2();

    public FirstInflectionPoint(Algorithm algorithm, String parameter, double low, double high, double increment) {
//        if (low >= high) throw new IllegalArgumentException("Must have low < high");
//        if (increment <= 0) throw new IllegalArgumentException("Increment must be >= 0");

        this.algorithm = algorithm;
        this.low = low;
        this.high = high;
        this.increment = increment;
        this.parameter = parameter;
    }

    @Override
    public Graph search(DataModel dataSet, Parameters parameters) {
        Parameters _parameters = new Parameters(parameters);

        Graph _previous = null;
        int _prevDiff = Integer.MAX_VALUE;
        double _value = 0.0;

        if (increment > 0) {

            for (double value = low; value <= high + 0.0000001; value += increment) {
                double value0 = getValue(value, parameters);

                _parameters.set(parameter, value0);
                Graph out = algorithm.search(dataSet, _parameters);

                if (_previous == null) {
                    _previous = out;
                    continue;
                }

                out = GraphUtils.replaceNodes(out, _previous.getNodes());
                Set<Edge> edges1 = out.getEdges();

                int numEdges = edges1.size();

                Set<Edge> edges2 = _previous.getEdges();
                edges2.removeAll(edges1);
                int diff = edges2.size();

                System.out.println(parameter + " = " + _parameters.getDouble(parameter)
                        + " # edges = " + numEdges
                        + " # additional = " + diff);

                if (diff >= _prevDiff) break;
                _previous = out;
                _value = _parameters.getDouble(parameter);
                _prevDiff = diff;
            }

            if (_value == Math.round((low + increment) * 1000000000.0) / 1000000000.0) {
                for (double value = low; value >= Double.NEGATIVE_INFINITY; value -= increment) {
                    value = getValue(value, parameters);

                    _parameters.set(parameter, value);
                    Graph out = algorithm.search(dataSet, _parameters);

                    out = GraphUtils.replaceNodes(out, _previous.getNodes());
                    Set<Edge> edges1 = out.getEdges();

                    int numEdges = edges1.size();

                    Set<Edge> edges2 = out.getEdges();
                    edges2.removeAll(_previous.getEdges());
                    int diff = edges2.size();

                    System.out.println(parameter + " = " + _parameters.getDouble(parameter)
                            + " # edges = " + numEdges
                            + " # additional = " + diff);

                    if (diff >= _prevDiff) break;
                    _previous = out;
                    _value = _parameters.getDouble(parameter);
                    _prevDiff = diff;
                }
            }

        } else {
            for (double value = high; value >= low - 0.0000001; value += increment) {
                double value0 = getValue(value, parameters);

                _parameters.set(parameter, value0);
                Graph out = algorithm.search(dataSet, _parameters);

                if (_previous == null) {
                    _previous = out;
                    continue;
                }

                out = GraphUtils.replaceNodes(out, _previous.getNodes());
                Set<Edge> edges1 = out.getEdges();

                int numEdges = edges1.size();

                Set<Edge> edges2 = _previous.getEdges();
                edges2.removeAll(edges1);
                int diff = edges2.size();

                System.out.println(parameter + " = " + _parameters.getDouble(parameter)
                        + " # edges = " + numEdges
                        + " # additional = " + diff);

                if (diff >= _prevDiff) break;
                _previous = out;
                _value = _parameters.getDouble(parameter);
                _prevDiff = diff;
            }

            if (_value == Math.round((high - increment) * 1000000000.0) / 1000000000.0) {
                for (double value = low; value >= Double.NEGATIVE_INFINITY; value -= increment) {
                    value = getValue(value, parameters);

                    _parameters.set(parameter, value);
                    Graph out = algorithm.search(dataSet, _parameters);

                    out = GraphUtils.replaceNodes(out, _previous.getNodes());
                    Set<Edge> edges1 = out.getEdges();

                    int numEdges = edges1.size();

                    Set<Edge> edges2 = out.getEdges();
                    edges2.removeAll(_previous.getEdges());
                    int diff = edges2.size();

                    System.out.println(parameter + " = " + _parameters.getDouble(parameter)
                            + " # edges = " + numEdges
                            + " # additional = " + diff);

                    if (diff >= _prevDiff) break;
                    _previous = out;
                    _value = _parameters.getDouble(parameter);
                    _prevDiff = diff;
                }
            }

        }

        System.out.println(parameter +" = "+_value);

        return _previous;
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
        return "First inflection for " + algorithm.getDescription() + " parameter = " + parameter;
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
