package edu.cmu.tetrad.algcomparison.algorithm;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.util.Parameters;
import org.apache.commons.math3.util.FastMath;

import java.io.Serial;
import java.util.List;
import java.util.Set;

/**
 * First inflection point.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class FirstInflection implements Algorithm {

    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The low value for the parameter.
     */
    private final double low;

    /**
     * The high value for the parameter.
     */
    private final double high;

    /**
     * The increment for the parameter.
     */
    private final double increment;

    /**
     * The parameter to vary.
     */
    private final String parameter;

    /**
     * The algorithm to use for the initial graph.
     */
    private final Algorithm algorithm;

    /**
     * <p>Constructor for FirstInflection.</p>
     *
     * @param algorithm a {@link edu.cmu.tetrad.algcomparison.algorithm.Algorithm} object
     * @param parameter a {@link java.lang.String} object
     * @param low       a double
     * @param high      a double
     * @param increment a double
     */
    public FirstInflection(Algorithm algorithm, String parameter, double low, double high, double increment) {
        if (low >= high) {
            throw new IllegalArgumentException("Must have low < high");
        }
        this.algorithm = algorithm;
        this.low = low;
        this.high = high;
        this.increment = increment;
        this.parameter = parameter;
    }

    private static double getValue(double value, Parameters parameters) {
        if (parameters.getBoolean("logScale")) {
            return FastMath.round(FastMath.pow(10.0, value) * 1000000000.0) / 1000000000.0;
        } else {
            return FastMath.round(value * 1000000000.0) / 1000000000.0;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Graph search(DataModel dataSet, Parameters parameters) {
        Parameters _parameters = new Parameters(parameters);

        Graph _previous = null;
        int _prevDiff = Integer.MAX_VALUE;
        double _value = 0.0;

        Graph intialGraph;
        if (this.increment > 0) {

            for (double value = this.low - this.increment; value <= this.high + 0.0000001; value += this.increment) {
                double value0 = FirstInflection.getValue(value, parameters);

                _parameters.set(this.parameter, value0);
                intialGraph = this.algorithm.search(dataSet, _parameters);

                if (_previous == null) {
                    _previous = intialGraph;
                    continue;
                }

                intialGraph = GraphUtils.replaceNodes(intialGraph, _previous.getNodes());
                Set<Edge> edges1 = intialGraph.getEdges();

                int numEdges = edges1.size();

                Set<Edge> edges2 = _previous.getEdges();
                edges2.removeAll(edges1);
                int diff = edges2.size();

                System.out.println(this.parameter + " = " + _parameters.getDouble(this.parameter)
                                   + " # edges = " + numEdges
                                   + " # additional = " + diff);

                if (diff >= _prevDiff) {
                    break;
                }
                _previous = intialGraph;
                _value = _parameters.getDouble(this.parameter);
                _prevDiff = diff;
            }

            if (_value == FastMath.round((this.low + this.increment) * 1000000000.0) / 1000000000.0) {
                for (double value = this.low; value >= Double.NEGATIVE_INFINITY; value -= this.increment) {
                    value = FirstInflection.getValue(value, parameters);

                    _parameters.set(this.parameter, value);
                    intialGraph = this.algorithm.search(dataSet, _parameters);

                    assert _previous != null;
                    intialGraph = GraphUtils.replaceNodes(intialGraph, _previous.getNodes());
                    Set<Edge> edges1 = intialGraph.getEdges();

                    int numEdges = edges1.size();

                    Set<Edge> edges2 = intialGraph.getEdges();
                    edges2.removeAll(_previous.getEdges());
                    int diff = edges2.size();

                    System.out.println(this.parameter + " = " + _parameters.getDouble(this.parameter)
                                       + " # edges = " + numEdges
                                       + " # additional = " + diff);

                    if (diff >= _prevDiff) {
                        break;
                    }
                    _previous = intialGraph;
                    _value = _parameters.getDouble(this.parameter);
                    _prevDiff = diff;
                }
            }

        } else {
            for (double value = this.high; value >= this.low - 0.0000001; value += this.increment) {
                double value0 = FirstInflection.getValue(value, parameters);

                _parameters.set(this.parameter, value0);
                intialGraph = this.algorithm.search(dataSet, _parameters);

                if (_previous == null) {
                    _previous = intialGraph;
                    continue;
                }

                intialGraph = GraphUtils.replaceNodes(intialGraph, _previous.getNodes());
                Set<Edge> edges1 = intialGraph.getEdges();

                int numEdges = edges1.size();

                Set<Edge> edges2 = _previous.getEdges();
                edges2.removeAll(edges1);
                int diff = edges2.size();

                System.out.println(this.parameter + " = " + _parameters.getDouble(this.parameter)
                                   + " # edges = " + numEdges
                                   + " # additional = " + diff);

                if (diff >= _prevDiff) {
                    break;
                }
                _previous = intialGraph;
                _value = _parameters.getDouble(this.parameter);
                _prevDiff = diff;
            }

            if (_value == FastMath.round((this.high - this.increment) * 1000000000.0) / 1000000000.0) {
                for (double value = this.low; value >= Double.NEGATIVE_INFINITY; value -= this.increment) {
                    value = FirstInflection.getValue(value, parameters);

                    _parameters.set(this.parameter, value);
                    intialGraph = this.algorithm.search(dataSet, _parameters);

                    assert _previous != null;
                    intialGraph = GraphUtils.replaceNodes(intialGraph, _previous.getNodes());
                    Set<Edge> edges1 = intialGraph.getEdges();

                    int numEdges = edges1.size();

                    Set<Edge> edges2 = intialGraph.getEdges();
                    edges2.removeAll(_previous.getEdges());
                    int diff = edges2.size();

                    System.out.println(this.parameter + " = " + _parameters.getDouble(this.parameter)
                                       + " # edges = " + numEdges
                                       + " # additional = " + diff);

                    if (diff >= _prevDiff) {
                        break;
                    }
                    _previous = intialGraph;
                    _value = _parameters.getDouble(this.parameter);
                    _prevDiff = diff;
                }
            }

        }

        System.out.println(this.parameter + " = " + _value);

        return _previous;

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Graph getComparisonGraph(Graph graph) {
        return this.algorithm.getComparisonGraph(graph);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription() {
        return "First inflection for " + this.algorithm.getDescription() + " parameter = " + this.parameter;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DataType getDataType() {
        return this.algorithm.getDataType();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> getParameters() {
        List<String> parameters = this.algorithm.getParameters();
        parameters.add("depth");
        parameters.add("verbose");
        return parameters;
    }
}
