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

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.lang.Math.abs;
import static java.lang.Math.log;

/**
 *
 */
public class GeneralizedSemEstimator {

    private String report = "";

    /**
     * Maximizes likelihood equation by equation. Assumes the equations are recursive and that
     * each has exactly one error term.
     */
    public GeneralizedSemIm estimate(GeneralizedSemPm pm, DataSet data) {
        StringBuilder builder = new StringBuilder();
        NumberFormat nf = new DecimalFormat("0.0000000");

        GeneralizedSemIm estIm = new GeneralizedSemIm(pm);

        List<Node> nodes = pm.getGraph().getNodes();
        nodes.removeAll(pm.getErrorNodes());

        MyContext context = new MyContext();

        List<List<Double>> allResiduals = new ArrayList<>();
        List<RealDistribution> allDistributions = new ArrayList<>();

        for (int index = 0; index < nodes.size(); index++) {
            Node node = nodes.get(index);
            List<String> parameters = new ArrayList<>(pm.getReferencedParameters(node));
            Node error = pm.getErrorNode(node);
            parameters.addAll(pm.getReferencedParameters(error));

            LikelihoodFittingFunction2 likelihoodFittingfunction = new LikelihoodFittingFunction2(index, pm, parameters,
                    nodes, data, context);

            double[] values = new double[parameters.size()];

            for (int j = 0; j < parameters.size(); j++) {
                String parameter = parameters.get(j);
                Expression parameterEstimationInitializationExpression = pm.getParameterEstimationInitializationExpression(parameter);
                values[j] = parameterEstimationInitializationExpression.evaluate(new MyContext());
            }

            double[] point = optimize(likelihoodFittingfunction, values, 1);

            for (int j = 0; j < parameters.size(); j++) {
                estIm.setParameterValue(parameters.get(j), point[j]);
            }

            List<Double> residuals = likelihoodFittingfunction.getResiduals();
            allResiduals.add(residuals);
            RealDistribution distribution = likelihoodFittingfunction.getDistribution();
            allDistributions.add(distribution);
            GeneralAndersonDarlingTest test = new GeneralAndersonDarlingTest(residuals, distribution);

            builder.append("\nEquation: " + node + " := " + estIm.getNodeSubstitutedString(node));
            builder.append("\n\twhere " + pm.getErrorNode(node) + " ~ " + estIm.getNodeSubstitutedString(pm.getErrorNode(node)));
            builder.append("\nAnderson Darling A^2* for this equation =  " + test.getASquaredStar() + "\n");
        }

        List<String> parameters = new ArrayList<>();
        double[] values = new double[parameters.size()];

        for (int i = 0; i < parameters.size(); i++) {
            values[i] = estIm.getParameterValue(parameters.get(i));
        }

        LikelihoodFittingFunction likelihoodFittingFunction = new LikelihoodFittingFunction(pm, parameters,
                nodes, data, context);

        optimize(likelihoodFittingFunction, values, 1);

        StringBuilder builder2 = new StringBuilder();
        builder2.append("Report:\n");

        MultiGeneralAndersonDarlingTest test = new MultiGeneralAndersonDarlingTest(allResiduals, allDistributions);
        builder2.append("\nModel A^2* (Anderson Darling) = " + test.getASquaredStar() + "\n");

        builder2.append(builder);

        this.report = builder2.toString();

        return estIm;
    }

    // Maximizes likelihood of the entire model.
    public GeneralizedSemIm estimate2(GeneralizedSemPm pm, DataSet data) {

        GeneralizedSemIm estIm = new GeneralizedSemIm(pm);
        MyContext context = new MyContext();

        List<Node> nodes = pm.getGraph().getNodes();
        nodes.removeAll(pm.getErrorNodes());

        List<String> parameters = new ArrayList<>(pm.getParameters());

        LikelihoodFittingFunction5 likelihoodFittingFunction = new LikelihoodFittingFunction5(pm, parameters,
                nodes, data, context);

        double[] values = new double[parameters.size()];

        for (int j = 0; j < parameters.size(); j++) {
            Expression expression = pm.getParameterEstimationInitializationExpression(parameters.get(j));
            RealDistribution dist = expression.getRealDistribution(context);
            if (dist != null) {
                double lb = dist.getSupportLowerBound();
                double up = dist.getSupportUpperBound();
                values[j] = expression.evaluate(context);
                if (values[j] < lb) values[j] = lb;
                if (values[j] > up) values[j] = up;
            } else {
                values[j] = expression.evaluate(context);
            }
        }

        double[] point = optimize(likelihoodFittingFunction, values, 1);

        for (int j = 0; j < parameters.size(); j++) {
            estIm.setParameterValue(parameters.get(j), point[j]);
        }

//        MultiGeneralAndersonDarlingTest test = new MultiGeneralAndersonDarlingTest(allResiduals, allDistributions);
//        System.out.println("Multi AD A^2 = " + test.getASquared());
//        System.out.println("Multi AD A^2-Star = " + test.getASquaredStar());
//        System.out.println("Multi AD p = " + test.getP());


        return estIm;
    }


    public String getReport() {
        return report;
    }


    public static class MyContext implements Context {
        final Map<String, Double> variableValues = new HashMap<>();
        final Map<String, Double> parameterValues = new HashMap<>();

        public Double getValue(String term) {
            Double value = parameterValues.get(term);

            if (value != null) {
                return value;
            }

            value = variableValues.get(term);

            if (value != null) {
                return value;
            }

            throw new IllegalArgumentException("No value recorded for '" + term + "'");
        }

        public void putParameterValue(String s, double parameter) {
            parameterValues.put(s, parameter);
        }

        public void putVariableValue(String s, double value) {
            variableValues.put(s, value);
        }
    }

    public static double[][] calcResiduals(double[][] data, List<Node> tierOrdering,
                                           List<String> params, double[] paramValues,
                                           GeneralizedSemPm pm, MyContext context) {
        double[][] calculatedValues = new double[data.length][data[0].length];
        double[][] residuals = new double[data.length][data[0].length];

        for (Node node : tierOrdering) {
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
                Node node = tierOrdering.get(j);
                Expression expression = pm.getNodeExpression(node);
                calculatedValues[row][j] = expression.evaluate(context);
                if (Double.isNaN(calculatedValues[row][j])) continue ROWS;
                residuals[row][j] = data[row][j] - calculatedValues[row][j];
            }
        }

        return residuals;
    }

    public static double[] calcOneResiduals(int index, double[][] data, List<Node> tierOrdering,
                                            List<String> params, double[] values,
                                            GeneralizedSemPm pm, MyContext context) {
        double[] residuals = new double[data.length];


        for (Node node : tierOrdering) {
            context.putVariableValue(pm.getErrorNode(node).toString(), 0.0);
        }

        for (int i = 0; i < params.size(); i++) {
            context.putParameterValue(params.get(i), values[i]);
        }


        for (int row = 0; row < data.length; row++) {
            for (int i = 0; i < tierOrdering.size(); i++) {
                context.putVariableValue(tierOrdering.get(i).getName(), data[row][i]);
            }
            Node node = tierOrdering.get(index);
            Expression expression = pm.getNodeExpression(node);
            double calculatedValue = expression.evaluate(context);
            residuals[row] = data[row][index] - calculatedValue;
        }

        return residuals;
    }


    private static double[][] getDataValues(DataSet data, List<Node> tierOrdering) {
        double[][] dataValues = new double[data.getNumRows()][tierOrdering.size()];

        int[] indices = new int[tierOrdering.size()];

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

    private double[] optimize(MultivariateFunction function, double[] values, int optimizer) {
        PointValuePair pair = null;

        if (optimizer == 1) {
//            0.01, 0.000001
            //2.0D * FastMath.ulp(1.0D), 1e-8
            MultivariateOptimizer search = new PowellOptimizer(1e-7, 1e-7);
            pair = search.optimize(
                    new InitialGuess(values),
                    new ObjectiveFunction(function),
                    GoalType.MINIMIZE,
                    new MaxEval(100000)
            );
        } else if (optimizer == 2) {
            MultivariateOptimizer search = new SimplexOptimizer(1e-7, 1e-7);
            pair = search.optimize(
                    new InitialGuess(values),
                    new ObjectiveFunction(function),
                    GoalType.MINIMIZE,
                    new MaxEval(100000),
                    new NelderMeadSimplex(values.length)
            );
        } else if (optimizer == 3) {
            int dim = values.length;
            int additionalInterpolationPoints = 0;
            final int numIterpolationPoints = 2 * dim + 1 + additionalInterpolationPoints;

            BOBYQAOptimizer search = new BOBYQAOptimizer(numIterpolationPoints);
            pair = search.optimize(
                    new MaxEval(100000),
                    new ObjectiveFunction(function),
                    GoalType.MINIMIZE,
                    new InitialGuess(values),
                    SimpleBounds.unbounded(dim)
            );
        } else if (optimizer == 4) {
            MultivariateOptimizer search = new CMAESOptimizer(3000000, .05, false, 0, 0,
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
            MultivariateOptimizer search = new PowellOptimizer(.05, .05);
            pair = search.optimize(
                    new InitialGuess(values),
                    new ObjectiveFunction(function),
                    GoalType.MINIMIZE,
                    new MaxEval(100000)
            );
        } else if (optimizer == 6) {
            MultivariateOptimizer search = new PowellOptimizer(1e-7, 1e-7);
            pair = search.optimize(
                    new InitialGuess(values),
                    new ObjectiveFunction(function),
                    GoalType.MAXIMIZE,
                    new MaxEval(10000)
            );
        }

        return pair.getPoint();
    }

    static class LikelihoodFittingFunction implements MultivariateFunction {
        private final GeneralizedSemPm pm;
        private final DataSet data;
        private final MyContext context;
        private List<String> parameters;
        private List<Node> tierOrdering;
        private double[][] dataValues;
        private double maxLikelihood;

        /**
         * f
         * Constructs a new CoefFittingFunction for the given Sem.
         */
        public LikelihoodFittingFunction(GeneralizedSemPm pm, List<String> parameters,
                                         List<Node> tierOrdering, DataSet data, MyContext context) {
            this.pm = pm;
            this.parameters = parameters;
            this.tierOrdering = tierOrdering;
            this.data = data;
            this.context = context;
            dataValues = getDataValues(data, tierOrdering);
        }

        @Override
        public double value(final double[] parameters) {
            for (int i = 0; i < parameters.length; i++) {
                if (Double.isNaN(parameters[i])) {
                    return Double.POSITIVE_INFINITY;
                }
            }

            double[][] r = calcResiduals(dataValues, tierOrdering, this.parameters, parameters, pm, context);

            double total = 0.0;

            for (int index = 0; index < tierOrdering.size(); index++) {
                Node error = pm.getErrorNode(tierOrdering.get(index));

                for (int k = 0; k < parameters.length; k++) {
                    context.putParameterValue(this.parameters.get(k), parameters[k]);
                }

                Expression expression = pm.getNodeExpression(error);
                RealDistribution dist = expression.getRealDistribution(context);

                if (dist == null) {
                    try {
                        dist = new EmpiricalDistributionForExpression(pm, error, context).getDist();
                    } catch (Exception e) {
                        return Double.POSITIVE_INFINITY;
                    }
                }

                List<Double> residuals = new ArrayList<>();

                for (int k = 0; k < r.length; k++) {
                    residuals.add(r[k][index]);
                }

                double likelihood = getLikelihood(residuals, dist);

                total += likelihood;
            }

            maxLikelihood = total;

            return -total;
        }

        private double getLikelihood(List<Double> residuals, RealDistribution dist) {
            double sum = 0.0;

            for (double r : residuals) {
                try {
                    double t = dist.density(r);
                    sum += log(t + 1e-15);
                } catch (Exception e) {
                    //
                }
            }

            return sum;
        }

        public List<Node> getTierOrdering() {
            return tierOrdering;
        }

        public double getMaxLikelihood() {
            return maxLikelihood;
        }
    }

    static class LikelihoodFittingFunction2 implements MultivariateFunction {

        private final GeneralizedSemPm pm;
        private final DataSet data;
        private List<String> parameters;
        private List<Node> tierOrdering;
        private int index = -1;
        private MyContext context;

        private List<Double> disturbances;
        private RealDistribution distribution;
        private double maxLikelihood;


        public LikelihoodFittingFunction2(int index, GeneralizedSemPm pm, List<String> parameters,
                                          List<Node> tierOrdering, DataSet data, MyContext context) {
            this.pm = pm;
            this.parameters = parameters;
            this.tierOrdering = tierOrdering;
            this.data = data;
            this.index = index;

            this.context = context;


        }

        @Override
        public double value(final double[] values) {
            for (int i = 0; i < values.length; i++) {
                if (Double.isNaN(values[i])) {
                    return Double.POSITIVE_INFINITY;
                }
            }

            Node error = pm.getErrorNode(tierOrdering.get(index));

            for (int k = 0; k < values.length; k++) {
                context.putParameterValue(this.parameters.get(k), values[k]);
            }

            double[][] dataValues = getDataValues(data, tierOrdering);

            double[] r = calcOneResiduals(index, dataValues, tierOrdering, parameters, values, pm, context);

            Expression expression = pm.getNodeExpression(error);

            RealDistribution dist;

            try {
                dist = expression.getRealDistribution(context);
            } catch (Exception e) {
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

            List<Double> residuals = new ArrayList<>();

            for (double _r : r) {
                residuals.add(_r);
            }

            double likelihood = getLikelihood(residuals, dist);
            this.maxLikelihood = likelihood;

            if (Double.isNaN(likelihood)) {
                return Double.POSITIVE_INFINITY;
            }

            this.distribution = dist;
            this.disturbances = residuals;

            return -likelihood;
        }

        private double getLikelihood(List<Double> residuals, RealDistribution dist) {
            double sum = 0.0;
//            int count = 0;

            for (int i = 0; i < residuals.size(); i++) {
                double r = residuals.get(i);
                double t = 0;
                try {
                    t = dist.density(r);
                } catch (Exception e) {
                    return Double.NaN;
                }
                if (Double.isNaN(t) || Double.isInfinite(t) || t < 0) t = 0.0;
//                count++;
//                if (Double.isNaN(t)) throw new IllegalArgumentException("Undefined density point.");
                sum += log(t + 1e-15);
            }

            return sum;
        }

        /**
         * @return the number of arguments. Required by the MultivariateFunction
         * interface.
         */
        public int getNumArguments() {
            return parameters.size();
        }

        /**
         * @return the lower bound of argument n. Required by the
         * MultivariateFunction interface.
         */
        public double getLowerBound(final int n) {
            if (n == 0) return -100;
            else return 0;
        }

        /**
         * @return the upper bound of argument n. Required by the
         * MultivariateFunction interface.
         */
        public double getUpperBound(final int n) {
            return 100.0;
        }

        public List<Node> getTierOrdering() {
            return tierOrdering;
        }


        public List<Double> getResiduals() {
            return disturbances;
        }

        public RealDistribution getDistribution() {
            return distribution;
        }
    }


    /**
     * Wraps the SEM maximum likelihood fitting function for purposes of being
     * evaluated using the PAL ConjugateDirection optimizer.
     *
     * @author Joseph Ramsey
     */
    static class CoefFittingFunction implements MultivariateFunction {

        /**
         * The wrapped Sem.
         */
        private final GeneralizedSemPm pm;
        private DataSet data;

        private List<String> freeParameters;

        private MyContext context;
        private List<Node> tierOrdering;
        private double[][] dataValues;
        private double[][] calculatedValues;

        /**
         * Constructs a new CoefFittingFunction for the given Sem.
         */
        public CoefFittingFunction(GeneralizedSemIm init, List<String> parameters, DataSet data) {
            this.pm = init.getSemPm();
            this.data = data;

            context = new MyContext();

            this.freeParameters = parameters;
            tierOrdering = pm.getGraph().getFullTierOrdering();
            tierOrdering.removeAll(pm.getErrorNodes());

            dataValues = new double[data.getNumRows()][tierOrdering.size()];
            calculatedValues = new double[data.getNumRows()][tierOrdering.size()];

            int[] indices = new int[tierOrdering.size()];

            for (int i = 0; i < tierOrdering.size(); i++) {
                indices[i] = data.getColumn(data.getVariable(tierOrdering.get(i).getName()));
            }

            for (int i = 0; i < data.getNumRows(); i++) {
                for (int j = 0; j < tierOrdering.size(); j++) {
                    dataValues[i][j] = data.getDouble(i, indices[j]);
                }
            }
        }

        @Override
        public double value(final double[] parameters) {
            for (int i = 0; i < freeParameters.size(); i++) {
                context.putParameterValue(freeParameters.get(i), parameters[i]);
            }

            for (Node error : pm.getErrorNodes()) {
                context.putVariableValue(error.getName(), 0.0);
            }

            double totalDiscrepancy = 0.0;
            int numRows = 0;

            ROWS:
            for (int row = 0; row < data.getNumRows(); row++) {
                for (int i = 0; i < tierOrdering.size(); i++) {
                    context.putVariableValue(tierOrdering.get(i).getName(), dataValues[row][i]);
                    if (Double.isNaN(dataValues[row][i])) continue ROWS;
                }

                for (int i = 0; i < tierOrdering.size(); i++) {
                    Node node = tierOrdering.get(i);
                    Expression expression = pm.getNodeExpression(node);
                    calculatedValues[row][i] = expression.evaluate(context);
                    if (Double.isNaN(calculatedValues[row][i])) continue ROWS;
                }

                for (int i = 0; i < tierOrdering.size(); i++) {
                    double discrepancy = calculatedValues[row][i] - dataValues[row][i];
//                    totalDiscrepancy += discrepancy * discrepancy;
                    totalDiscrepancy += abs(discrepancy);
                }

                numRows++;
            }

            if (numRows == 0) {
                return Double.POSITIVE_INFINITY;
            }

            return totalDiscrepancy / numRows;
        }

        public List<Node> getTierOrdering() {
            return tierOrdering;
        }
    }

    static class LikelihoodFittingFunction5 implements MultivariateFunction {
        private final GeneralizedSemPm pm;
        private final DataSet data;
        private final MyContext context;
        private List<String> parameters;
        private List<Node> tierOrdering;
        private double[][] dataValues;
        private double maxLikelihood;

        /**
         * f
         * Constructs a new CoefFittingFunction for the given Sem.
         */
        public LikelihoodFittingFunction5(GeneralizedSemPm pm, List<String> parameters,
                                          List<Node> tierOrdering, DataSet data, MyContext context) {
            this.pm = pm;
            this.parameters = parameters;
            this.tierOrdering = tierOrdering;
            this.data = data;
            this.context = context;
            dataValues = getDataValues(data, tierOrdering);
        }

        @Override
        public double value(final double[] parameters) {
            for (int i = 0; i < parameters.length; i++) {
                if (Double.isNaN(parameters[i])) {
                    return Double.POSITIVE_INFINITY;
                }
            }

//            double[][] r = calcResiduals(dataValues, tierOrdering, this.parameters, parameters, pm, context);

            double total = 0.0;

            for (int index = 0; index < tierOrdering.size(); index++) {
                Node node = tierOrdering.get(index);
                Node error = pm.getErrorNode(node);

                for (int k = 0; k < parameters.length; k++) {
                    context.putParameterValue(this.parameters.get(k), parameters[k]);
                }

                for (int i = 0; i < tierOrdering.size(); i++) {
                    Expression expression = pm.getNodeExpression(error);
                    context.putVariableValue(error.getName(), expression.evaluate(context));
                }

                Expression expression = pm.getNodeExpression(error);
                RealDistribution dist = null;//expression.getRealDistribution(context);

                if (dist == null) {
                    try {
                        dist = new EmpiricalDistributionForExpression(pm, node, context).getDist();
                    } catch (Exception e) {
                        return Double.POSITIVE_INFINITY;
                    }
                }

                List<Double> data = new ArrayList<>();

                for (int k = 0; k < dataValues.length; k++) {
                    data.add(dataValues[k][index]);
                }

                double likelihood = getLikelihood(data, dist);

                System.out.println(likelihood);

                total += likelihood;
            }

            maxLikelihood = total;

            return -total;
        }

        private double getLikelihood(List<Double> residuals, RealDistribution dist) {
            double sum = 0.0;

            for (double r : residuals) {
                try {
                    double t = dist.density(r);
                    sum += log(t + 1e-15);
                } catch (Exception e) {
                    //
                }
            }

            return sum;
        }

        public List<Node> getTierOrdering() {
            return tierOrdering;
        }
    }
}

