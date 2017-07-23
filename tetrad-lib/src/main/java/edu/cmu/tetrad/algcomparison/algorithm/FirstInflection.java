package edu.cmu.tetrad.algcomparison.algorithm;

import edu.cmu.tetrad.algcomparison.utils.TakesInitialGraph;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.util.Parameters;
import edu.pitt.dbmi.data.Dataset;
import org.apache.commons.math3.analysis.MultivariateFunction;
import org.apache.commons.math3.optim.InitialGuess;
import org.apache.commons.math3.optim.MaxEval;
import org.apache.commons.math3.optim.PointValuePair;
import org.apache.commons.math3.optim.nonlinear.scalar.GoalType;
import org.apache.commons.math3.optim.nonlinear.scalar.MultivariateOptimizer;
import org.apache.commons.math3.optim.nonlinear.scalar.ObjectiveFunction;
import org.apache.commons.math3.optim.nonlinear.scalar.noderiv.PowellOptimizer;

import javax.xml.crypto.Data;
import java.util.List;
import java.util.Set;

/**
 * First inflection point.
 *
 * @author jdramsey
 */
public class FirstInflection implements Algorithm, TakesInitialGraph {
    static final long serialVersionUID = 23L;
    private final double low;
    private final double high;
    private final double increment;
    private final String parameter;
    private Algorithm algorithm;
    private IKnowledge knowledge = new Knowledge2();

    public FirstInflection(Algorithm algorithm, String parameter, double low, double high, double initialValue) {
        if (low >= high) throw new IllegalArgumentException("Must have low < high");
        this.algorithm = algorithm;
        this.low = low;
        this.high = high;
        this.increment = initialValue;
        this.parameter = parameter;
    }

    @Override
    public Graph search(DataModel dataSet, Parameters parameters) {
        Parameters _parameters = new Parameters(parameters);

//        Graph _previous = null;
//        int _prevDiff = Integer.MAX_VALUE;
//        double _value = 0.0;
//
//        if (increment > 0) {
//
//            for (double value = low; value <= high + 0.0000001; value += increment) {
//                double value0 = getValue(value, parameters);
//
//                _parameters.set(parameter, value0);
//                Graph out = algorithm.search(dataSet, _parameters);
//
//                if (_previous == null) {
//                    _previous = out;
//                    continue;
//                }
//
//                out = GraphUtils.replaceNodes(out, _previous.getNodes());
//                Set<Edge> edges1 = out.getEdges();
//
//                int numEdges = edges1.size();
//
//                Set<Edge> edges2 = _previous.getEdges();
//                edges2.removeAll(edges1);
//                int diff = edges2.size();
//
//                System.out.println(parameter + " = " + _parameters.getDouble(parameter)
//                        + " # edges = " + numEdges
//                        + " # additional = " + diff);
//
//                if (diff >= _prevDiff) break;
//                _previous = out;
//                _value = _parameters.getDouble(parameter);
//                _prevDiff = diff;
//            }
//
//            if (_value == Math.round((low + increment) * 1000000000.0) / 1000000000.0) {
//                for (double value = low; value >= Double.NEGATIVE_INFINITY; value -= increment) {
//                    value = getValue(value, parameters);
//
//                    _parameters.set(parameter, value);
//                    Graph out = algorithm.search(dataSet, _parameters);
//
//                    out = GraphUtils.replaceNodes(out, _previous.getNodes());
//                    Set<Edge> edges1 = out.getEdges();
//
//                    int numEdges = edges1.size();
//
//                    Set<Edge> edges2 = out.getEdges();
//                    edges2.removeAll(_previous.getEdges());
//                    int diff = edges2.size();
//
//                    System.out.println(parameter + " = " + _parameters.getDouble(parameter)
//                            + " # edges = " + numEdges
//                            + " # additional = " + diff);
//
//                    if (diff >= _prevDiff) break;
//                    _previous = out;
//                    _value = _parameters.getDouble(parameter);
//                    _prevDiff = diff;
//                }
//            }
//
//        } else {
//            for (double value = high; value >= low - 0.0000001; value += increment) {
//                double value0 = getValue(value, parameters);
//
//                _parameters.set(parameter, value0);
//                Graph out = algorithm.search(dataSet, _parameters);
//
//                if (_previous == null) {
//                    _previous = out;
//                    continue;
//                }
//
//                out = GraphUtils.replaceNodes(out, _previous.getNodes());
//                Set<Edge> edges1 = out.getEdges();
//
//                int numEdges = edges1.size();
//
//                Set<Edge> edges2 = _previous.getEdges();
//                edges2.removeAll(edges1);
//                int diff = edges2.size();
//
//                System.out.println(parameter + " = " + _parameters.getDouble(parameter)
//                        + " # edges = " + numEdges
//                        + " # additional = " + diff);
//
//                if (diff >= _prevDiff) break;
//                _previous = out;
//                _value = _parameters.getDouble(parameter);
//                _prevDiff = diff;
//            }
//
//            if (_value == Math.round((high - increment) * 1000000000.0) / 1000000000.0) {
//                for (double value = low; value >= Double.NEGATIVE_INFINITY; value -= increment) {
//                    value = getValue(value, parameters);
//
//                    _parameters.set(parameter, value);
//                    Graph out = algorithm.search(dataSet, _parameters);
//
//                    out = GraphUtils.replaceNodes(out, _previous.getNodes());
//                    Set<Edge> edges1 = out.getEdges();
//
//                    int numEdges = edges1.size();
//
//                    Set<Edge> edges2 = out.getEdges();
//                    edges2.removeAll(_previous.getEdges());
//                    int diff = edges2.size();
//
//                    System.out.println(parameter + " = " + _parameters.getDouble(parameter)
//                            + " # edges = " + numEdges
//                            + " # additional = " + diff);
//
//                    if (diff >= _prevDiff) break;
//                    _previous = out;
//                    _value = _parameters.getDouble(parameter);
//                    _prevDiff = diff;
//                }
//            }
//
//        }
//
//        System.out.println(parameter + " = " + _value);
//
//        return _previous;

        double tolerance = parameters.getDouble("StARS.tolerance");

        MultivariateOptimizer search = new PowellOptimizer(tolerance, tolerance);
        FittingFunction f = new FittingFunction(_parameters, algorithm, low, high, parameter, (DataSet) dataSet);
        PointValuePair p = search.optimize(
                new InitialGuess(new double[]{increment}),
                new ObjectiveFunction(f),
                GoalType.MINIMIZE,
                new MaxEval(100000)
        );

        double value = p.getPoint()[0];
        System.out.println(parameter + " = " + getValue(value, parameters));
        _parameters.set(parameter, getValue(value, parameters));

        return algorithm.search(dataSet, _parameters);
    }

    static class FittingFunction implements MultivariateFunction {

        private final Algorithm algorithm;
        private final double low;
        private final double high;
        private final String paramName;
        private Parameters params;
        private DataSet dataSet;
        Graph _previous = null;

        /**
         * Constructs a new CoefFittingFunction for the given Sem.
         */
        public FittingFunction(Parameters params, Algorithm algorithm,
                               double low, double high, String paramName, DataSet dataSet) {
            this.params = params;
            this.algorithm = algorithm;
            this.low = low;
            this.high = high;
            this.paramName = paramName;
            this.dataSet = dataSet;
        }

        /**
         * Computes the maximum likelihood function value for the given
         * parameter values as given by the optimizer. These values are mapped to
         * parameter values.
         */

        @Override
        public double value(double[] parameters) {
            double p = parameters[0];
            if (p < low) return 10000;
            if (p > high) return 10000;
            double _p = getValue(p, params);
            params.set(paramName, _p);
            Graph out = algorithm.search(dataSet, params);

            if (_previous == null) {
                _previous = out;
                return out.getNumEdges();
            }

            out = GraphUtils.replaceNodes(out, _previous.getNodes());

            Set<Edge> e1 = out.getEdges();
            e1.removeAll(_previous.getEdges());

            Set<Edge> e2 = _previous.getEdges();
            e2.removeAll(out.getEdges());

            int numEdges = out.getNumEdges();

            int diff = e1.size() + e2.size();

            System.out.println(paramName + " = " + params.getDouble(paramName)
                    + " # edges = " + numEdges
                    + " # additional = " + diff);

            _previous = out;
            return diff;
        }
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
