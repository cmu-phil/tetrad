package edu.cmu.tetrad.algcomparison.algorithm;

import edu.cmu.tetrad.algcomparison.utils.TakesExternalGraph;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.util.Parameters;
import org.apache.commons.math3.analysis.MultivariateFunction;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * First inflection point.
 *
 * @author jdramsey
 */
public class FirstInflection implements Algorithm, TakesExternalGraph {

    static final long serialVersionUID = 23L;
    private final double low;
    private final double high;
    private final double increment;
    private final String parameter;
    private final Algorithm algorithm;
    private Graph intialGraph;
    private final IKnowledge knowledge = new Knowledge2();

    public FirstInflection(final Algorithm algorithm, final String parameter, final double low, final double high, final double increment) {
        if (low >= high) {
            throw new IllegalArgumentException("Must have low < high");
        }
        this.algorithm = algorithm;
        this.low = low;
        this.high = high;
        this.increment = increment;
        this.parameter = parameter;
    }

    @Override
    public Graph search(final DataModel dataSet, final Parameters parameters) {
        final Parameters _parameters = new Parameters(parameters);

        Graph _previous = null;
        int _prevDiff = Integer.MAX_VALUE;
        double _value = 0.0;

        if (this.increment > 0) {

            for (double value = this.low - this.increment; value <= this.high + 0.0000001; value += this.increment) {
                final double value0 = FirstInflection.getValue(value, parameters);

                _parameters.set(this.parameter, value0);
                this.intialGraph = this.algorithm.search(dataSet, _parameters);

                if (_previous == null) {
                    _previous = this.intialGraph;
                    continue;
                }

                this.intialGraph = GraphUtils.replaceNodes(this.intialGraph, _previous.getNodes());
                final Set<Edge> edges1 = this.intialGraph.getEdges();

                final int numEdges = edges1.size();

                final Set<Edge> edges2 = _previous.getEdges();
                edges2.removeAll(edges1);
                final int diff = edges2.size();

                System.out.println(this.parameter + " = " + _parameters.getDouble(this.parameter)
                        + " # edges = " + numEdges
                        + " # additional = " + diff);

                if (diff >= _prevDiff) {
                    break;
                }
                _previous = this.intialGraph;
                _value = _parameters.getDouble(this.parameter);
                _prevDiff = diff;
            }

            if (_value == Math.round((this.low + this.increment) * 1000000000.0) / 1000000000.0) {
                for (double value = this.low; value >= Double.NEGATIVE_INFINITY; value -= this.increment) {
                    value = FirstInflection.getValue(value, parameters);

                    _parameters.set(this.parameter, value);
                    this.intialGraph = this.algorithm.search(dataSet, _parameters);

                    this.intialGraph = GraphUtils.replaceNodes(this.intialGraph, _previous.getNodes());
                    final Set<Edge> edges1 = this.intialGraph.getEdges();

                    final int numEdges = edges1.size();

                    final Set<Edge> edges2 = this.intialGraph.getEdges();
                    edges2.removeAll(_previous.getEdges());
                    final int diff = edges2.size();

                    System.out.println(this.parameter + " = " + _parameters.getDouble(this.parameter)
                            + " # edges = " + numEdges
                            + " # additional = " + diff);

                    if (diff >= _prevDiff) {
                        break;
                    }
                    _previous = this.intialGraph;
                    _value = _parameters.getDouble(this.parameter);
                    _prevDiff = diff;
                }
            }

        } else {
            for (double value = this.high; value >= this.low - 0.0000001; value += this.increment) {
                final double value0 = FirstInflection.getValue(value, parameters);

                _parameters.set(this.parameter, value0);
                this.intialGraph = this.algorithm.search(dataSet, _parameters);

                if (_previous == null) {
                    _previous = this.intialGraph;
                    continue;
                }

                this.intialGraph = GraphUtils.replaceNodes(this.intialGraph, _previous.getNodes());
                final Set<Edge> edges1 = this.intialGraph.getEdges();

                final int numEdges = edges1.size();

                final Set<Edge> edges2 = _previous.getEdges();
                edges2.removeAll(edges1);
                final int diff = edges2.size();

                System.out.println(this.parameter + " = " + _parameters.getDouble(this.parameter)
                        + " # edges = " + numEdges
                        + " # additional = " + diff);

                if (diff >= _prevDiff) {
                    break;
                }
                _previous = this.intialGraph;
                _value = _parameters.getDouble(this.parameter);
                _prevDiff = diff;
            }

            if (_value == Math.round((this.high - this.increment) * 1000000000.0) / 1000000000.0) {
                for (double value = this.low; value >= Double.NEGATIVE_INFINITY; value -= this.increment) {
                    value = FirstInflection.getValue(value, parameters);

                    _parameters.set(this.parameter, value);
                    this.intialGraph = this.algorithm.search(dataSet, _parameters);

                    this.intialGraph = GraphUtils.replaceNodes(this.intialGraph, _previous.getNodes());
                    final Set<Edge> edges1 = this.intialGraph.getEdges();

                    final int numEdges = edges1.size();

                    final Set<Edge> edges2 = this.intialGraph.getEdges();
                    edges2.removeAll(_previous.getEdges());
                    final int diff = edges2.size();

                    System.out.println(this.parameter + " = " + _parameters.getDouble(this.parameter)
                            + " # edges = " + numEdges
                            + " # additional = " + diff);

                    if (diff >= _prevDiff) {
                        break;
                    }
                    _previous = this.intialGraph;
                    _value = _parameters.getDouble(this.parameter);
                    _prevDiff = diff;
                }
            }

        }

        System.out.println(this.parameter + " = " + _value);

        return _previous;

//        double tolerance = parameters.getDouble("StARS.tolerance");
//
//        MultivariateOptimizer search = new PowellOptimizer(tolerance, tolerance);
//        FittingFunction f = new FittingFunction(_parameters, algorithm, low, high, parameter, (DataSet) dataSet);
//        PointValuePair p = search.optimize(
//                new InitialGuess(new double[]{increment, increment}),
//                new ObjectiveFunction(f),
//                GoalType.MINIMIZE,
//                new MaxEval(100000)
//        );
//
//
//        double[] point = p.getPoint();
//
//        double p1 = point[0];
//        double p2 = point[1];
//
//        p1 = Math.round(p1 * 10.0) / 10.0;
//        p2 = Math.round(p2 * 10.0) / 10.0;
//
//        double value = Math.max(p1, p2);
//
////        double value = (p.getPoint()[0] + p.getPoint()[1]) / 2;
//        System.out.println(parameter + " = " + getValue(value, parameters));
//        _parameters.set(parameter, getValue(value, parameters));
//
//        return algorithm.search(dataSet, _parameters);
    }

    @Override
    public void setExternalGraph(final Algorithm externalGraph) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    static class FittingFunction implements MultivariateFunction {

        private final Algorithm algorithm;
        private final double low;
        private final double high;
        private final String paramName;
        private final DataSet _dataSet;
        private final Parameters params;
        private final DataSet dataSet;
        Graph _previous;

        /**
         * Constructs a new CoefFittingFunction for the given Sem.
         */
        public FittingFunction(final Parameters params, final Algorithm algorithm,
                               final double low, final double high, final String paramName, final DataSet dataSet) {
            this.params = params;
            this.algorithm = algorithm;
            this.low = low;
            this.high = high;
            this.paramName = paramName;
            this.dataSet = dataSet;
            final int numVars = Math.min(20, dataSet.getNumColumns());

            final int[] cols = new int[numVars];
            for (int i = 0; i < numVars; i++) {
                cols[i] = i;
            }

            this._dataSet = dataSet.subsetColumns(cols);
        }

        /**
         * Computes the maximum likelihood function value for the given
         * parameter values as given by the optimizer. These values are mapped
         * to parameter values.
         */
//        public double value0(double[] parameters) {
//            double p = parameters[0];
//            if (p < low) return 10000;
//            if (p > high) return 10000;
//            double _p = getValue(p, params);
//            params.set(paramName, _p);
//            Graph out = algorithm.search(dataSet, params);
//
//            if (_previous == null) {
//                _previous = out;
//                return out.getNumEdges();
//            }
//
//            out = GraphUtils.replaceNodes(out, _previous.getNodes());
//
//            Set<Edge> e1 = out.getEdges();
//            e1.removeAll(_previous.getEdges());
//
//            Set<Edge> e2 = _previous.getEdges();
//            e2.removeAll(out.getEdges());
//
//            int numEdges = out.getNumEdges();
//
//            int diff = e1.size() + e2.size();
//
//            System.out.println(paramName + " = " + params.getDouble(paramName)
//                    + " # edges = " + numEdges
//                    + " # additional = " + diff);
//
//            _previous = out;
//            return diff;
//        }
        private final Map<Double, Graph> archive = new HashMap<>();

        @Override
        public double value(final double[] parameters) {
            double p1 = parameters[0];
            double p2 = parameters[1];

            p1 = Math.round(p1 * 10.0) / 10.0;
            p2 = Math.round(p2 * 10.0) / 10.0;

            if (p1 < this.low) {
                return 10000;
            }
            if (p1 > this.high) {
                return 10000;
            }
            if (p2 < this.low) {
                return 10000;
            }
            if (p2 > this.high) {
                return 10000;
            }
//            if (p1 == p2) return 10000;
            if (Math.abs(p1 - p2) < 0.1) {
                return 100000;
            }

            final double _p1 = FirstInflection.getValue(p1, this.params);
            final double _p2 = FirstInflection.getValue(p2, this.params);

            if (this.archive.get(_p1) == null) {
                this.params.set(this.paramName, _p1);
                this.archive.put(_p1, this.algorithm.search(this._dataSet, this.params));
            }

            final Graph out1 = this.archive.get(_p1);

            if (this.archive.get(_p2) == null) {
                this.params.set(this.paramName, _p2);
                this.archive.put(_p2, this.algorithm.search(this._dataSet, this.params));
            }

            final Graph out2 = this.archive.get(_p2);

            final Set<Edge> e1 = out1.getEdges();
            e1.removeAll(out2.getEdges());

            final Set<Edge> e2 = out2.getEdges();
            e2.removeAll(out1.getEdges());

            final int diff = e1.size() + e2.size();

            final int numEdges1 = out1.getNumEdges();
            final int numEdges2 = out2.getNumEdges();
            System.out.println(this.paramName + " = " + p1 + ", " + p2
                    + " # edges 1 = " + numEdges1 + " # edges 2  " + numEdges2
                    + " # additional = " + diff);

            return diff;
        }
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
        return "First inflection for " + this.algorithm.getDescription() + " parameter = " + this.parameter;
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
        return parameters;
    }

    @Override
    public Graph getExternalGraph() {
        return this.intialGraph;
    }

    @Override
    public void setExternalGraph(final Graph externalGraph) {
        // TODO Auto-generated method stub
        this.intialGraph = this.intialGraph;
    }
}
