///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015, 2022 by Peter Spirtes, Richard        //
// Scheines, Joseph Ramsey, and Clark Glymour.                               //
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
import org.apache.commons.math3.optim.InitialGuess;
import org.apache.commons.math3.optim.MaxEval;
import org.apache.commons.math3.optim.PointValuePair;
import org.apache.commons.math3.optim.nonlinear.scalar.GoalType;
import org.apache.commons.math3.optim.nonlinear.scalar.MultivariateOptimizer;
import org.apache.commons.math3.optim.nonlinear.scalar.ObjectiveFunction;
import org.apache.commons.math3.optim.nonlinear.scalar.noderiv.PowellOptimizer;

import java.util.*;

import static org.apache.commons.math3.util.FastMath.log;

/**
 * Estimates a Generalized SEM I'M given a Generalized SEM PM and a data set.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class GeneralizedSemEstimator {

    private String report = "";
    private double aSquaredStar = Double.NaN;

    private static double[][] calcResiduals(double[][] data, List<Node> tierOrdering,
                                            List<String> params, double[] paramValues,
                                            GeneralizedSemPm pm, MyContext context) {
        if (pm == null) throw new NullPointerException();

        double[][] calculatedValues = new double[data.length][data[0].length];
        double[][] residuals = new double[data.length][data[0].length];

        for (Node node : tierOrdering) {
            context.putVariableValue(Objects.requireNonNull(pm.getErrorNode(node)).toString(), 0.0);
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

    private static double[] calcOneResiduals(int index, double[][] data, List<Node> tierOrdering,
                                             List<String> params, double[] values,
                                             GeneralizedSemPm pm, MyContext context) {
        if (pm == null) throw new NullPointerException();

        double[] residuals = new double[data.length];

        for (Node node : tierOrdering) {
            context.putVariableValue(Objects.requireNonNull(pm.getErrorNode(node)).toString(), 0.0);
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

    /**
     * Maximizes likelihood equation by equation. Assumes the equations are recursive and that each has exactly one
     * error term.
     *
     * @param pm   a {@link edu.cmu.tetrad.sem.GeneralizedSemPm} object
     * @param data a {@link edu.cmu.tetrad.data.DataSet} object
     * @return a {@link edu.cmu.tetrad.sem.GeneralizedSemIm} object
     */
    public GeneralizedSemIm estimate(GeneralizedSemPm pm, DataSet data) {
        StringBuilder builder = new StringBuilder();
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

            double[] point = optimize(likelihoodFittingfunction, values);

            for (int j = 0; j < parameters.size(); j++) {
                estIm.setParameterValue(parameters.get(j), point[j]);
            }

            List<Double> residuals = likelihoodFittingfunction.getResiduals();
            allResiduals.add(residuals);
            RealDistribution distribution = likelihoodFittingfunction.getDistribution();
            allDistributions.add(distribution);
            GeneralAndersonDarlingTest test = new GeneralAndersonDarlingTest(residuals, distribution);

            builder.append("\nEquation: ").append(node).append(" := ").append(estIm.getNodeSubstitutedString(node));
            builder.append("\n\twhere ").append(pm.getErrorNode(node)).append(" ~ ").append(estIm.getNodeSubstitutedString(pm.getErrorNode(node)));
            builder.append("\nAnderson Darling A^2* for this equation =  ").append(test.getASquaredStar()).append("\n");
        }

        List<String> parameters = new ArrayList<>();
        double[] values = new double[0];

        LikelihoodFittingFunction likelihoodFittingFunction = new LikelihoodFittingFunction(pm, parameters,
                nodes, data, context);

        optimize(likelihoodFittingFunction, values);

        MultiGeneralAndersonDarlingTest test = new MultiGeneralAndersonDarlingTest(allResiduals, allDistributions);

        double aSquaredStar = test.getASquaredStar();

        this.aSquaredStar = aSquaredStar;

        this.report = "Report:\n" +
                "\nModel A^2* (Anderson Darling) = " + aSquaredStar + "\n" +
                builder;

        return estIm;
    }

    /**
     * <p>Getter for the field <code>report</code>.</p>
     *
     * @return a {@link java.lang.String} object
     */
    public String getReport() {
        return this.report;
    }

    /**
     * <p>Getter for the field <code>aSquaredStar</code>.</p>
     *
     * @return a double
     */
    public double getaSquaredStar() {
        return this.aSquaredStar;
    }

    private double[] optimize(MultivariateFunction function, double[] values) {
        PointValuePair pair;

        {
//            0.01, 0.000001
            //2.0D * FastMath.ulp(1.0D), 1e-8
            MultivariateOptimizer search = new PowellOptimizer(1e-7, 1e-7);
            pair = search.optimize(
                    new InitialGuess(values),
                    new ObjectiveFunction(function),
                    GoalType.MINIMIZE,
                    new MaxEval(100000)
            );
        }

        return pair.getPoint();
    }

    /**
     * Context.
     */
    public static class MyContext implements Context {

        /** The variable values. */
        final Map<String, Double> variableValues = new HashMap<>();

        /** The parameter values. */
        final Map<String, Double> parameterValues = new HashMap<>();

        /** Get the value of a variable or parameter. */
        public Double getValue(String term) {
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

        /**
         * <p>putParameterValue.</p>
         * @param s a {@link java.lang.String} object
         * @param parameter a double
         */
        public void putParameterValue(String s, double parameter) {
            this.parameterValues.put(s, parameter);
        }

        /**
         * <p>putVariableValue.</p>
         * @param s a {@link java.lang.String} object
         * @param value a double
         */
        public void putVariableValue(String s, double value) {
            this.variableValues.put(s, value);
        }
    }

    /**
     * Fitting function for the likelihood.
     */
    static class LikelihoodFittingFunction implements MultivariateFunction {

        /** The pm. */
        private final GeneralizedSemPm pm;

        /** The context. */
        private final MyContext context;

        /** The parameters. */
        private final List<String> parameters;

        /** The tier ordering. */
        private final List<Node> tierOrdering;

        /** The data values. */
        private final double[][] dataValues;

        /**
         * f Constructs a new CoefFittingFunction for the given Sem.
         */
        public LikelihoodFittingFunction(GeneralizedSemPm pm, List<String> parameters,
                                         List<Node> tierOrdering, DataSet data, MyContext context) {
            this.pm = pm;
            this.parameters = parameters;
            this.tierOrdering = tierOrdering;
            this.context = context;
            this.dataValues = GeneralizedSemEstimator.getDataValues(data, tierOrdering);
        }

        /**
         * <p>value.</p>
         *
         * @param parameters an array of double.
         * @return a double
         */
        @Override
        public double value(double[] parameters) {
            for (double parameter : parameters) {
                if (Double.isNaN(parameter)) {
                    return Double.POSITIVE_INFINITY;
                }
            }

            double[][] r = GeneralizedSemEstimator.calcResiduals(this.dataValues, this.tierOrdering, this.parameters, parameters, this.pm, this.context);

            double total = 0.0;

            for (int index = 0; index < this.tierOrdering.size(); index++) {
                Node error = this.pm.getErrorNode(this.tierOrdering.get(index));

                for (int k = 0; k < parameters.length; k++) {
                    this.context.putParameterValue(this.parameters.get(k), parameters[k]);
                }

                Expression expression = this.pm.getNodeExpression(error);
                RealDistribution dist = expression.getRealDistribution(this.context);

                if (dist == null) {
                    try {
                        dist = new EmpiricalDistributionForExpression(this.pm, error, this.context).getDist();
                    } catch (Exception e) {
                        return Double.POSITIVE_INFINITY;
                    }
                }

                List<Double> residuals = new ArrayList<>();

                for (double[] aR : r) {
                    residuals.add(aR[index]);
                }

                double likelihood = getLikelihood(residuals, dist);

                total += likelihood;
            }

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
            return this.tierOrdering;
        }
    }

    /**
     * Fitting function for the likelihood.
     */
    static class LikelihoodFittingFunction2 implements MultivariateFunction {

        /** The pm. */
        private final GeneralizedSemPm pm;

        /** The data. */
        private final DataSet data;

        /** The parameters. */
        private final List<String> parameters;

        /** The tier ordering. */
        private final List<Node> tierOrdering;

        /** The index. */
        private final int index;

        /** The context. */
        private final MyContext context;

        /** The disturbances. */
        private List<Double> disturbances;

        /** The distribution. */
        private RealDistribution distribution;

        /**
         * Constructs a new CoefFittingFunction for the given Sem.
         *
         * @param index a int
         * @param pm a {@link edu.cmu.tetrad.sem.GeneralizedSemPm} object
         * @param parameters a {@link java.util.List} object
         * @param tierOrdering a {@link java.util.List} object
         * @param data a {@link edu.cmu.tetrad.data.DataSet} object
         * @
         */
        public LikelihoodFittingFunction2(int index, GeneralizedSemPm pm, List<String> parameters,
                                          List<Node> tierOrdering, DataSet data, MyContext context) {
            this.pm = pm;
            this.parameters = parameters;
            this.tierOrdering = tierOrdering;
            this.data = data;
            this.index = index;

            this.context = context;


        }

        /**
         * <p>value.</p>
         *
         * @param values an array of double.
         * @return a double
         */
        @Override
        public double value(double[] values) {
            for (double value : values) {
                if (Double.isNaN(value)) {
                    return Double.POSITIVE_INFINITY;
                }
            }

            Node error = this.pm.getErrorNode(this.tierOrdering.get(this.index));

            for (int k = 0; k < values.length; k++) {
                this.context.putParameterValue(this.parameters.get(k), values[k]);
            }

            double[][] dataValues = GeneralizedSemEstimator.getDataValues(this.data, this.tierOrdering);

            double[] r = GeneralizedSemEstimator.calcOneResiduals(this.index, dataValues, this.tierOrdering, this.parameters, values, this.pm, this.context);

            Expression expression = this.pm.getNodeExpression(error);

            RealDistribution dist;

            try {
                dist = expression.getRealDistribution(this.context);
            } catch (Exception e) {
                return Double.POSITIVE_INFINITY;
            }

            if (dist == null) {
                throw new IllegalArgumentException("For estimation, only error distributions may be used for which " +
                        "a p.d.f. is available.");

            }

            List<Double> residuals = new ArrayList<>();

            for (double _r : r) {
                residuals.add(_r);
            }

            double likelihood = getLikelihood(residuals, dist);

            if (Double.isNaN(likelihood)) {
                return Double.POSITIVE_INFINITY;
            }

            this.distribution = dist;
            this.disturbances = residuals;

            return -likelihood;
        }

        private double getLikelihood(List<Double> residuals, RealDistribution dist) {
            double sum = 0.0;

            for (Double residual : residuals) {
                double r = residual;
                double t;
                try {
                    t = dist.density(r);
                } catch (Exception e) {
                    return Double.NaN;
                }
                if (Double.isNaN(t) || Double.isInfinite(t) || t < 0) t = 0.0;
                sum += log(t + 1e-15);
            }

            return sum;
        }

        /**
         * <p>Getter for the field <code>disturbances</code>.</p>
         *
         * @return a {@link java.util.List} object
         */
        public List<Node> getTierOrdering() {
            return this.tierOrdering;
        }


        /**
         * <p>Getter for the field <code>disturbances</code>.</p>
         *
         * @return a {@link java.util.List} object
         */
        public List<Double> getResiduals() {
            return this.disturbances;
        }

        /**
         * <p>Getter for the field <code>distribution</code>.</p>
         *
         * @return a {@link org.apache.commons.math3.distribution.RealDistribution} object
         */
        public RealDistribution getDistribution() {
            return this.distribution;
        }
    }


}

