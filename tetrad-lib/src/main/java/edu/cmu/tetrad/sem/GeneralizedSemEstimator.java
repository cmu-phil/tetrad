///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015 by Peter Spirtes, Richard Scheines, Joseph   //
// Ramsey, and Clark Glymour.                                                //
//                                                                           //
// This program is free software; you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation; either version 2 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program; if not, write to the Free Software               //
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.sem;

import edu.cmu.tetrad.calculator.expression.Context;
import edu.cmu.tetrad.calculator.expression.Expression;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.GeneralAndersonDarlingTest;
import edu.cmu.tetrad.data.MultiGeneralAndersonDarlingTest;
import edu.cmu.tetrad.graph.Node;
import org.apache.commons.math3.analysis.MultivariateFunction;
import org.apache.commons.math3.distribution.RealDistribution;
import org.apache.commons.math3.optim.*;
import org.apache.commons.math3.optim.nonlinear.scalar.GoalType;
import org.apache.commons.math3.optim.nonlinear.scalar.MultivariateOptimizer;
import org.apache.commons.math3.optim.nonlinear.scalar.ObjectiveFunction;
import org.apache.commons.math3.optim.nonlinear.scalar.noderiv.*;
import org.apache.commons.math3.random.MersenneTwister;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.lang.Math.log;

/**
 * Estimates a Generalized SEM IM given a Generalized SEM PM and a data set.
 *
 * @author Joseph Ramsey
 */
public class GeneralizedSemEstimator {

    private String report = "";
    private double aSquaredStar = Double.NaN;

    /**
     * Maximizes likelihood equation by equation. Assumes the equations are recursive and that
     * each has exactly one error term.
     */
    public GeneralizedSemIm estimate(final GeneralizedSemPm pm, final DataSet data) {
        final StringBuilder builder = new StringBuilder();
        final GeneralizedSemIm estIm = new GeneralizedSemIm(pm);
        final List<Node> nodes = pm.getGraph().getNodes();
        nodes.removeAll(pm.getErrorNodes());
        final MyContext context = new MyContext();
        final List<List<Double>> allResiduals = new ArrayList<>();
        final List<RealDistribution> allDistributions = new ArrayList<>();

        for (int index = 0; index < nodes.size(); index++) {
            final Node node = nodes.get(index);
            final List<String> parameters = new ArrayList<>(pm.getReferencedParameters(node));
            final Node error = pm.getErrorNode(node);
            parameters.addAll(pm.getReferencedParameters(error));

            final LikelihoodFittingFunction2 likelihoodFittingfunction = new LikelihoodFittingFunction2(index, pm, parameters,
                    nodes, data, context);

            final double[] values = new double[parameters.size()];

            for (int j = 0; j < parameters.size(); j++) {
                final String parameter = parameters.get(j);
                final Expression parameterEstimationInitializationExpression = pm.getParameterEstimationInitializationExpression(parameter);
                values[j] = parameterEstimationInitializationExpression.evaluate(new MyContext());
            }

            final double[] point = optimize(likelihoodFittingfunction, values, 1);

            for (int j = 0; j < parameters.size(); j++) {
                estIm.setParameterValue(parameters.get(j), point[j]);
            }

            final List<Double> residuals = likelihoodFittingfunction.getResiduals();
            allResiduals.add(residuals);
            final RealDistribution distribution = likelihoodFittingfunction.getDistribution();
            allDistributions.add(distribution);
            final GeneralAndersonDarlingTest test = new GeneralAndersonDarlingTest(residuals, distribution);

            builder.append("\nEquation: ").append(node).append(" := ").append(estIm.getNodeSubstitutedString(node));
            builder.append("\n\twhere ").append(pm.getErrorNode(node)).append(" ~ ").append(estIm.getNodeSubstitutedString(pm.getErrorNode(node)));
            builder.append("\nAnderson Darling A^2* for this equation =  ").append(test.getASquaredStar()).append("\n");
        }

        final List<String> parameters = new ArrayList<>();
        final double[] values = new double[parameters.size()];

        for (int i = 0; i < parameters.size(); i++) {
            values[i] = estIm.getParameterValue(parameters.get(i));
        }

        final LikelihoodFittingFunction likelihoodFittingFunction = new LikelihoodFittingFunction(pm, parameters,
                nodes, data, context);

        optimize(likelihoodFittingFunction, values, 1);

        final MultiGeneralAndersonDarlingTest test = new MultiGeneralAndersonDarlingTest(allResiduals, allDistributions);

        final double aSquaredStar = test.getASquaredStar();

        this.aSquaredStar = aSquaredStar;

        final String builder2 = "Report:\n" +
                "\nModel A^2* (Anderson Darling) = " + aSquaredStar + "\n" +
                builder;

        this.report = builder2;

        return estIm;
    }

//    // Maximizes likelihood of the entire model.
//    public GeneralizedSemIm estimate2(GeneralizedSemPm pm, DataSet data) {
//
//        GeneralizedSemIm estIm = new GeneralizedSemIm(pm);
//        MyContext context = new MyContext();
//
//        List<Node> nodes = pm.getGraph().getNodes();
//        nodes.removeAll(pm.getErrorNodes());
//
//        List<String> parameters = new ArrayList<>(pm.getParameters());
//
//        LikelihoodFittingFunction5 likelihoodFittingFunction = new LikelihoodFittingFunction5(pm, parameters,
//                nodes, data, context);
//
//        double[] values = new double[parameters.size()];
//
//        for (int j = 0; j < parameters.size(); j++) {
//            Expression expression = pm.getParameterEstimationInitializationExpression(parameters.get(j));
//            RealDistribution dist = expression.getRealDistribution(context);
//            if (dist != null) {
//                double lb = dist.getSupportLowerBound();
//                double up = dist.getSupportUpperBound();
//                values[j] = expression.evaluate(context);
//                if (values[j] < lb) values[j] = lb;
//                if (values[j] > up) values[j] = up;
//            } else {
//                values[j] = expression.evaluate(context);
//            }
//        }
//
//        double[] point = optimize(likelihoodFittingFunction, values, 1);
//
//        for (int j = 0; j < parameters.size(); j++) {
//            estIm.setParameterValue(parameters.get(j), point[j]);
//        }
//
////        MultiGeneralAndersonDarlingTest test = new MultiGeneralAndersonDarlingTest(allResiduals, allDistributions);
////        System.out.println("Multi AD A^2 = " + test.getASquared());
////        System.out.println("Multi AD A^2-Star = " + test.getASquaredStar());
////        System.out.println("Multi AD p = " + test.getP());
//
//
//        return estIm;
//    }


    public String getReport() {
        return this.report;
    }

    public double getaSquaredStar() {
        return this.aSquaredStar;
    }


    public static class MyContext implements Context {
        final Map<String, Double> variableValues = new HashMap<>();
        final Map<String, Double> parameterValues = new HashMap<>();

        public Double getValue(final String term) {
            Double value = this.parameterValues.get(term);

            if (value != null) {
                return value;
            }

            value = this.variableValues.get(term);

            if (value != null) {
                return value;
            }

            throw new IllegalArgumentException("No value recorded for '" + term + "'");
        }

        public void putParameterValue(final String s, final double parameter) {
            this.parameterValues.put(s, parameter);
        }

        public void putVariableValue(final String s, final double value) {
            this.variableValues.put(s, value);
        }
    }

    private static double[][] calcResiduals(final double[][] data, final List<Node> tierOrdering,
                                            final List<String> params, final double[] paramValues,
                                            final GeneralizedSemPm pm, final MyContext context) {
        if (pm == null) throw new NullPointerException();

        final double[][] calculatedValues = new double[data.length][data[0].length];
        final double[][] residuals = new double[data.length][data[0].length];

        for (final Node node : tierOrdering) {
            context.putVariableValue(pm.getErrorNode(node).toString(), 0.0);
        }

        for (int i = 0; i < params.size(); i++) {
            context.putParameterValue(params.get(i), paramValues[i]);
        }

        ROWS:
        for (int row = 0; row < data.length; row++) {
            for (int j = 0; j < tierOrdering.size(); j++) {
                context.putVariableValue(tierOrdering.get(j).getName(), data[row][j]);
                if (Double.isNaN(data[row][j])) continue ROWS;
            }

            for (int j = 0; j < tierOrdering.size(); j++) {
                final Node node = tierOrdering.get(j);
                final Expression expression = pm.getNodeExpression(node);
                calculatedValues[row][j] = expression.evaluate(context);
                if (Double.isNaN(calculatedValues[row][j])) continue ROWS;
                residuals[row][j] = data[row][j] - calculatedValues[row][j];
            }
        }

        return residuals;
    }

    private static double[] calcOneResiduals(final int index, final double[][] data, final List<Node> tierOrdering,
                                             final List<String> params, final double[] values,
                                             final GeneralizedSemPm pm, final MyContext context) {
        if (pm == null) throw new NullPointerException();

        final double[] residuals = new double[data.length];

        for (final Node node : tierOrdering) {
            context.putVariableValue(pm.getErrorNode(node).toString(), 0.0);
        }

        for (int i = 0; i < params.size(); i++) {
            context.putParameterValue(params.get(i), values[i]);
        }


        for (int row = 0; row < data.length; row++) {
            for (int i = 0; i < tierOrdering.size(); i++) {
                context.putVariableValue(tierOrdering.get(i).getName(), data[row][i]);
            }
            final Node node = tierOrdering.get(index);
            final Expression expression = pm.getNodeExpression(node);
            final double calculatedValue = expression.evaluate(context);
            residuals[row] = data[row][index] - calculatedValue;
        }

        return residuals;
    }


    private static double[][] getDataValues(final DataSet data, final List<Node> tierOrdering) {
        final double[][] dataValues = new double[data.getNumRows()][tierOrdering.size()];

        final int[] indices = new int[tierOrdering.size()];

        for (int i = 0; i < tierOrdering.size(); i++) {
            indices[i] = data.getColumn(data.getVariable(tierOrdering.get(i).getName()));
        }

        for (int i = 0; i < data.getNumRows(); i++) {
            for (int j = 0; j < tierOrdering.size(); j++) {
                dataValues[i][j] = data.getDouble(i, indices[j]);
            }
        }

        return dataValues;
    }

    private double[] optimize(final MultivariateFunction function, final double[] values, final int optimizer) {
        final PointValuePair pair;

        if (optimizer == 1) {
//            0.01, 0.000001
            //2.0D * FastMath.ulp(1.0D), 1e-8
            final MultivariateOptimizer search = new PowellOptimizer(1e-7, 1e-7);
            pair = search.optimize(
                    new InitialGuess(values),
                    new ObjectiveFunction(function),
                    GoalType.MINIMIZE,
                    new MaxEval(100000)
            );
        } else if (optimizer == 2) {
            final MultivariateOptimizer search = new SimplexOptimizer(1e-7, 1e-7);
            pair = search.optimize(
                    new InitialGuess(values),
                    new ObjectiveFunction(function),
                    GoalType.MINIMIZE,
                    new MaxEval(100000),
                    new NelderMeadSimplex(values.length)
            );
        } else if (optimizer == 3) {
            final int dim = values.length;
            final int additionalInterpolationPoints = 0;
            final int numIterpolationPoints = 2 * dim + 1 + additionalInterpolationPoints;

            final BOBYQAOptimizer search = new BOBYQAOptimizer(numIterpolationPoints);
            pair = search.optimize(
                    new MaxEval(100000),
                    new ObjectiveFunction(function),
                    GoalType.MINIMIZE,
                    new InitialGuess(values),
                    SimpleBounds.unbounded(dim)
            );
        } else if (optimizer == 4) {
            final MultivariateOptimizer search = new CMAESOptimizer(3000000, .05, false, 0, 0,
                    new MersenneTwister(), false, new SimplePointChecker<PointValuePair>(0.5, 0.5));
            pair = search.optimize(
                    new MaxEval(30000),
                    new ObjectiveFunction(function),
                    GoalType.MINIMIZE,
                    new InitialGuess(values),
                    new CMAESOptimizer.Sigma(new double[values.length]),
                    new CMAESOptimizer.PopulationSize(1000)
            );
        } else if (optimizer == 5) {
//            0.01, 0.000001
            //2.0D * FastMath.ulp(1.0D), 1e-8
            final MultivariateOptimizer search = new PowellOptimizer(.05, .05);
            pair = search.optimize(
                    new InitialGuess(values),
                    new ObjectiveFunction(function),
                    GoalType.MINIMIZE,
                    new MaxEval(100000)
            );
        } else if (optimizer == 6) {
            final MultivariateOptimizer search = new PowellOptimizer(1e-7, 1e-7);
            pair = search.optimize(
                    new InitialGuess(values),
                    new ObjectiveFunction(function),
                    GoalType.MAXIMIZE,
                    new MaxEval(10000)
            );
        } else {
            throw new IllegalStateException();
        }

        return pair.getPoint();
    }

    static class LikelihoodFittingFunction implements MultivariateFunction {
        private final GeneralizedSemPm pm;
        private final MyContext context;
        private final List<String> parameters;
        private final List<Node> tierOrdering;
        private final double[][] dataValues;

        /**
         * f
         * Constructs a new CoefFittingFunction for the given Sem.
         */
        public LikelihoodFittingFunction(final GeneralizedSemPm pm, final List<String> parameters,
                                         final List<Node> tierOrdering, final DataSet data, final MyContext context) {
            this.pm = pm;
            this.parameters = parameters;
            this.tierOrdering = tierOrdering;
            this.context = context;
            this.dataValues = GeneralizedSemEstimator.getDataValues(data, tierOrdering);
        }

        @Override
        public double value(final double[] parameters) {
            for (final double parameter : parameters) {
                if (Double.isNaN(parameter)) {
                    return Double.POSITIVE_INFINITY;
                }
            }

            final double[][] r = GeneralizedSemEstimator.calcResiduals(this.dataValues, this.tierOrdering, this.parameters, parameters, this.pm, this.context);

            double total = 0.0;

            for (int index = 0; index < this.tierOrdering.size(); index++) {
                final Node error = this.pm.getErrorNode(this.tierOrdering.get(index));

                for (int k = 0; k < parameters.length; k++) {
                    this.context.putParameterValue(this.parameters.get(k), parameters[k]);
                }

                final Expression expression = this.pm.getNodeExpression(error);
                RealDistribution dist = expression.getRealDistribution(this.context);

                if (dist == null) {
                    try {
                        dist = new EmpiricalDistributionForExpression(this.pm, error, this.context).getDist();
                    } catch (final Exception e) {
                        return Double.POSITIVE_INFINITY;
                    }
                }

                final List<Double> residuals = new ArrayList<>();

                for (final double[] aR : r) {
                    residuals.add(aR[index]);
                }

                final double likelihood = getLikelihood(residuals, dist);

                total += likelihood;
            }

            return -total;
        }

        private double getLikelihood(final List<Double> residuals, final RealDistribution dist) {
            double sum = 0.0;

            for (final double r : residuals) {
                try {
                    final double t = dist.density(r);
                    sum += log(t + 1e-15);
                } catch (final Exception e) {
                    //
                }
            }

            return sum;
        }

        public List<Node> getTierOrdering() {
            return this.tierOrdering;
        }
    }

    static class LikelihoodFittingFunction2 implements MultivariateFunction {

        private final GeneralizedSemPm pm;
        private final DataSet data;
        private final List<String> parameters;
        private final List<Node> tierOrdering;
        private int index = -1;
        private final MyContext context;

        private List<Double> disturbances;
        private RealDistribution distribution;

        public LikelihoodFittingFunction2(final int index, final GeneralizedSemPm pm, final List<String> parameters,
                                          final List<Node> tierOrdering, final DataSet data, final MyContext context) {
            this.pm = pm;
            this.parameters = parameters;
            this.tierOrdering = tierOrdering;
            this.data = data;
            this.index = index;

            this.context = context;


        }

        @Override
        public double value(final double[] values) {
            for (final double value : values) {
                if (Double.isNaN(value)) {
                    return Double.POSITIVE_INFINITY;
                }
            }

            final Node error = this.pm.getErrorNode(this.tierOrdering.get(this.index));

            for (int k = 0; k < values.length; k++) {
                this.context.putParameterValue(this.parameters.get(k), values[k]);
            }

            final double[][] dataValues = GeneralizedSemEstimator.getDataValues(this.data, this.tierOrdering);

            final double[] r = GeneralizedSemEstimator.calcOneResiduals(this.index, dataValues, this.tierOrdering, this.parameters, values, this.pm, this.context);

            final Expression expression = this.pm.getNodeExpression(error);

            final RealDistribution dist;

            try {
                dist = expression.getRealDistribution(this.context);
            } catch (final Exception e) {
                return Double.POSITIVE_INFINITY;
            }

            if (dist == null) {
                throw new IllegalArgumentException("For estimation, only error distributions may be used for which " +
                        "a p.d.f. is available.");

//                try {
//                    dist = new EmpiricalDistributionForExpression(pm, error, context).getDist();
//                } catch (Exception e) {
//                    return Double.POSITIVE_INFINITY;
//                }
            }

            final List<Double> residuals = new ArrayList<>();

            for (final double _r : r) {
                residuals.add(_r);
            }

            final double likelihood = getLikelihood(residuals, dist);

            if (Double.isNaN(likelihood)) {
                return Double.POSITIVE_INFINITY;
            }

            this.distribution = dist;
            this.disturbances = residuals;

            return -likelihood;
        }

        private double getLikelihood(final List<Double> residuals, final RealDistribution dist) {
            double sum = 0.0;

            for (final Double residual : residuals) {
                final double r = residual;
                double t;
                try {
                    t = dist.density(r);
                } catch (final Exception e) {
                    return Double.NaN;
                }
                if (Double.isNaN(t) || Double.isInfinite(t) || t < 0) t = 0.0;
                sum += log(t + 1e-15);
            }

            return sum;
        }

        public List<Node> getTierOrdering() {
            return this.tierOrdering;
        }


        public List<Double> getResiduals() {
            return this.disturbances;
        }

        public RealDistribution getDistribution() {
            return this.distribution;
        }
    }


//    /**
//     * Wraps the SEM maximum likelihood fitting function for purposes of being
//     * evaluated using the PAL ConjugateDirection optimizer.
//     *
//     * @author Joseph Ramsey
//     */
//    static class CoefFittingFunction implements MultivariateFunction {
//
//        /**
//         * The wrapped Sem.
//         */
//        private final GeneralizedSemPm pm;
//        private DataSet data;
//
//        private List<String> freeParameters;
//
//        private MyContext context;
//        private List<Node> tierOrdering;
//        private double[][] dataValues;
//        private double[][] calculatedValues;
//
//        /**
//         * Constructs a new CoefFittingFunction for the given Sem.
//         */
//        public CoefFittingFunction(GeneralizedSemIm init, List<String> parameters, DataSet data) {
//            this.pm = init.getSemPm();
//            this.data = data;
//
//            context = new MyContext();
//
//            this.freeParameters = parameters;
//            tierOrdering = pm.getGraph().getFullTierOrdering();
//            tierOrdering.removeAll(pm.getErrorNodes());
//
//            dataValues = new double[data.getNumRows()][tierOrdering.size()];
//            calculatedValues = new double[data.getNumRows()][tierOrdering.size()];
//
//            int[] indices = new int[tierOrdering.size()];
//
//            for (int i = 0; i < tierOrdering.size(); i++) {
//                indices[i] = data.getColumn(data.getVariable(tierOrdering.get(i).getNode()));
//            }
//
//            for (int i = 0; i < data.getNumRows(); i++) {
//                for (int j = 0; j < tierOrdering.size(); j++) {
//                    dataValues[i][j] = data.getDouble(i, indices[j]);
//                }
//            }
//        }
//
//        @Override
//        public double value(final double[] parameters) {
//            for (int i = 0; i < freeParameters.size(); i++) {
//                context.putParameterValue(freeParameters.get(i), parameters[i]);
//            }
//
//            for (Node error : pm.getErrorNodes()) {
//                context.putVariableValue(error.getNode(), 0.0);
//            }
//
//            double totalDiscrepancy = 0.0;
//            int numRows = 0;
//
//            ROWS:
//            for (int row = 0; row < data.getNumRows(); row++) {
//                for (int i = 0; i < tierOrdering.size(); i++) {
//                    context.putVariableValue(tierOrdering.get(i).getNode(), dataValues[row][i]);
//                    if (Double.isNaN(dataValues[row][i])) continue ROWS;
//                }
//
//                for (int i = 0; i < tierOrdering.size(); i++) {
//                    Node node = tierOrdering.get(i);
//                    Expression expression = pm.getNodeExpression(node);
//                    calculatedValues[row][i] = expression.evaluate(context);
//                    if (Double.isNaN(calculatedValues[row][i])) continue ROWS;
//                }
//
//                for (int i = 0; i < tierOrdering.size(); i++) {
//                    double discrepancy = calculatedValues[row][i] - dataValues[row][i];
////                    totalDiscrepancy += discrepancy * discrepancy;
//                    totalDiscrepancy += abs(discrepancy);
//                }
//
//                numRows++;
//            }
//
//            if (numRows == 0) {
//                return Double.POSITIVE_INFINITY;
//            }
//
//            return totalDiscrepancy / numRows;
//        }
//
//        public List<Node> getTierOrdering() {
//            return tierOrdering;
//        }
//    }

//    static class LikelihoodFittingFunction5 implements MultivariateFunction {
//        private final GeneralizedSemPm pm;
//        private final MyContext context;
//        private List<String> parameters;
//        private List<Node> tierOrdering;
//        private double[][] dataValues;
//        private double maxLikelihood;
//
//        /**
//         * f
//         * Constructs a new CoefFittingFunction for the given Sem.
//         */
//        public LikelihoodFittingFunction5(GeneralizedSemPm pm, List<String> parameters,
//                                          List<Node> tierOrdering, DataSet data, MyContext context) {
//            this.pm = pm;
//            this.parameters = parameters;
//            this.tierOrdering = tierOrdering;
//            this.context = context;
//            dataValues = getDataValues(data, tierOrdering);
//        }
//
//        @Override
//        public double value(final double[] parameters) {
//            for (double parameter : parameters) {
//                if (Double.isNaN(parameter)) {
//                    return Double.POSITIVE_INFINITY;
//                }
//            }
//
////            double[][] r = calcResiduals(dataValues, tierOrdering, this.parameters, parameters, pm, context);
//
//            double total = 0.0;
//
//            for (int index = 0; index < tierOrdering.size(); index++) {
//                Node node = tierOrdering.get(index);
//                Node error = pm.getErrorNode(node);
//
//                if (error == null) {
//                    throw new NullPointerException();
//                }
//
//                for (int k = 0; k < parameters.length; k++) {
//                    context.putParameterValue(this.parameters.get(k), parameters[k]);
//                }
//
//                for (int i = 0; i < tierOrdering.size(); i++) {
//                    Expression expression = pm.getNodeExpression(error);
//                    context.putVariableValue(error.getNode(), expression.evaluate(context));
//                }
//
//                RealDistribution dist;
//
//                try {
//                    dist = new EmpiricalDistributionForExpression(pm, node, context).getDist();
//                } catch (Exception e) {
//                    return Double.POSITIVE_INFINITY;
//                }
//
//                List<Double> data = new ArrayList<>();
//
//                for (double[] dataValue : dataValues) {
//                    data.add(dataValue[index]);
//                }
//
//                double likelihood = getLikelihood(data, dist);
//
//                System.out.println(likelihood);
//
//                total += likelihood;
//            }
//
//            maxLikelihood = total;
//
//            return -total;
//        }
//
//        private double getLikelihood(List<Double> residuals, RealDistribution dist) {
//            double sum = 0.0;
//
//            for (double r : residuals) {
//                try {
//                    double t = dist.density(r);
//                    sum += log(t + 1e-15);
//                } catch (Exception e) {
//                    //
//                }
//            }
//
//            return sum;
//        }
//
//        public List<Node> getTierOrdering() {
//            return tierOrdering;
//        }
//    }
}

