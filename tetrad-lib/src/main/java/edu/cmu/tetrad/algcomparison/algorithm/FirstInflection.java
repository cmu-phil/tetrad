package edu.cmu.tetrad.algcomparison.algorithm;

import edu.cmu.tetrad.algcomparison.utils.TakesInitialGraph;
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
public class FirstInflection implements Algorithm, TakesInitialGraph {

    static final long serialVersionUID = 23L;
    private final double low;
    private final double high;
    private final double increment;
    private final String parameter;
    private Algorithm algorithm;
    private Graph intialGraph = null;
    private IKnowledge knowledge = new Knowledge2();

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

    @Override
    public Graph search(DataModel dataSet, Parameters parameters) {
        Parameters _parameters = new Parameters(parameters);

        Graph _previous = null;
        int _prevDiff = Integer.MAX_VALUE;
        double _value = 0.0;

        if (increment > 0) {

            for (double value = low - increment; value <= high + 0.0000001; value += increment) {
                double value0 = getValue(value, parameters);

                _parameters.set(parameter, value0);
                intialGraph = algorithm.search(dataSet, _parameters);

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

                System.out.println(parameter + " = " + _parameters.getDouble(parameter)
                        + " # edges = " + numEdges
                        + " # additional = " + diff);

                if (diff >= _prevDiff) {
                    break;
                }
                _previous = intialGraph;
                _value = _parameters.getDouble(parameter);
                _prevDiff = diff;
            }

            if (_value == Math.round((low + increment) * 1000000000.0) / 1000000000.0) {
                for (double value = low; value >= Double.NEGATIVE_INFINITY; value -= increment) {
                    value = getValue(value, parameters);

                    _parameters.set(parameter, value);
                    intialGraph = algorithm.search(dataSet, _parameters);

                    intialGraph = GraphUtils.replaceNodes(intialGraph, _previous.getNodes());
                    Set<Edge> edges1 = intialGraph.getEdges();

                    int numEdges = edges1.size();

                    Set<Edge> edges2 = intialGraph.getEdges();
                    edges2.removeAll(_previous.getEdges());
                    int diff = edges2.size();

                    System.out.println(parameter + " = " + _parameters.getDouble(parameter)
                            + " # edges = " + numEdges
                            + " # additional = " + diff);

                    if (diff >= _prevDiff) {
                        break;
                    }
                    _previous = intialGraph;
                    _value = _parameters.getDouble(parameter);
                    _prevDiff = diff;
                }
            }

        } else {
            for (double value = high; value >= low - 0.0000001; value += increment) {
                double value0 = getValue(value, parameters);

                _parameters.set(parameter, value0);
                intialGraph = algorithm.search(dataSet, _parameters);

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

                System.out.println(parameter + " = " + _parameters.getDouble(parameter)
                        + " # edges = " + numEdges
                        + " # additional = " + diff);

                if (diff >= _prevDiff) {
                    break;
                }
                _previous = intialGraph;
                _value = _parameters.getDouble(parameter);
                _prevDiff = diff;
            }

            if (_value == Math.round((high - increment) * 1000000000.0) / 1000000000.0) {
                for (double value = low; value >= Double.NEGATIVE_INFINITY; value -= increment) {
                    value = getValue(value, parameters);

                    _parameters.set(parameter, value);
                    intialGraph = algorithm.search(dataSet, _parameters);

                    intialGraph = GraphUtils.replaceNodes(intialGraph, _previous.getNodes());
                    Set<Edge> edges1 = intialGraph.getEdges();

                    int numEdges = edges1.size();

                    Set<Edge> edges2 = intialGraph.getEdges();
                    edges2.removeAll(_previous.getEdges());
                    int diff = edges2.size();

                    System.out.println(parameter + " = " + _parameters.getDouble(parameter)
                            + " # edges = " + numEdges
                            + " # additional = " + diff);

                    if (diff >= _prevDiff) {
                        break;
                    }
                    _previous = intialGraph;
                    _value = _parameters.getDouble(parameter);
                    _prevDiff = diff;
                }
            }

        }

        System.out.println(parameter + " = " + _value);

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
    public void setInitialGraph(Algorithm initialGraph) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    static class FittingFunction implements MultivariateFunction {

        private final Algorithm algorithm;
        private final double low;
        private final double high;
        private final String paramName;
        private final DataSet _dataSet;
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
            int numVars = Math.min(20, ((DataSet) dataSet).getNumColumns());

            int[] cols = new int[numVars];
            for (int i = 0; i < numVars; i++) {
                cols[i] = i;
            }

            _dataSet = dataSet.subsetColumns(cols);
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
        private Map<Double, Graph> archive = new HashMap<>();

        @Override
        public double value(double[] parameters) {
            double p1 = parameters[0];
            double p2 = parameters[1];

            p1 = Math.round(p1 * 10.0) / 10.0;
            p2 = Math.round(p2 * 10.0) / 10.0;

            if (p1 < low) {
                return 10000;
            }
            if (p1 > high) {
                return 10000;
            }
            if (p2 < low) {
                return 10000;
            }
            if (p2 > high) {
                return 10000;
            }
//            if (p1 == p2) return 10000;
            if (Math.abs(p1 - p2) < 0.1) {
                return 100000;
            }

            double _p1 = getValue(p1, params);
            double _p2 = getValue(p2, params);

            if (archive.get(_p1) == null) {
                params.set(paramName, _p1);
                archive.put(_p1, algorithm.search(_dataSet, params));
            }

            Graph out1 = archive.get(_p1);

            if (archive.get(_p2) == null) {
                params.set(paramName, _p2);
                archive.put(_p2, algorithm.search(_dataSet, params));
            }

            Graph out2 = archive.get(_p2);

            Set<Edge> e1 = out1.getEdges();
            e1.removeAll(out2.getEdges());

            Set<Edge> e2 = out2.getEdges();
            e2.removeAll(out1.getEdges());

            int diff = e1.size() + e2.size();

            int numEdges1 = out1.getNumEdges();
            int numEdges2 = out2.getNumEdges();
            System.out.println(paramName + " = " + p1 + ", " + p2
                    + " # edges 1 = " + numEdges1 + " # edges 2  " + numEdges2
                    + " # additional = " + diff);

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

	@Override
	public Graph getInitialGraph() {
		return intialGraph;
	}

	@Override
	public void setInitialGraph(Graph initialGraph) {
		// TODO Auto-generated method stub
		this.intialGraph = intialGraph;
	}
}
