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
import edu.cmu.tetrad.calculator.parser.ExpressionLexer;
import edu.cmu.tetrad.calculator.parser.Token;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.util.Vector;
import edu.cmu.tetrad.util.*;
import org.apache.commons.math3.analysis.MultivariateFunction;
import org.apache.commons.math3.optim.InitialGuess;
import org.apache.commons.math3.optim.MaxEval;
import org.apache.commons.math3.optim.PointValuePair;
import org.apache.commons.math3.optim.nonlinear.scalar.GoalType;
import org.apache.commons.math3.optim.nonlinear.scalar.MultivariateOptimizer;
import org.apache.commons.math3.optim.nonlinear.scalar.ObjectiveFunction;
import org.apache.commons.math3.optim.nonlinear.scalar.noderiv.PowellOptimizer;

import java.text.NumberFormat;
import java.util.*;

import static edu.cmu.tetrad.util.StatUtils.sd;

/**
 * Represents a generalized SEM instantiated model. The parameteric form of this
 * model allows arbitrary equations for variables. This instantiated model
 * gives values for all of the parameters of the parameterized model.
 *
 * @author Joseph Ramsey
 */
public class GeneralizedSemIm implements IM, Simulator {
    static final long serialVersionUID = 23L;

    /**
     * The wrapped PM, that holds all of the expressions and structure for the model.
     */
    private final GeneralizedSemPm pm;

    /**
     * A map from freeParameters names to their values--these form the context for evaluating expressions.
     * Variables do not appear in this list. All freeParameters are double-valued.
     */
    private final Map<String, Double> parameterValues;

    /**
     * True iff only positive data should be simulated.
     */
    private boolean simulatePositiveDataOnly;

    /**
     * The coefficient of a (linear) self-loop for each variable, or NaN if there is none.
     */
    private final double selfLoopCoef = Double.NaN;


    /**
     * Constructs a new GeneralizedSemIm from the given GeneralizedSemPm by picking values for each of
     * the freeParameters from their initial distributions.
     *
     * @param pm the GeneralizedSemPm. Includes all of the equations and distributions of the model.
     */
    public GeneralizedSemIm(final GeneralizedSemPm pm) {
        this.pm = new GeneralizedSemPm(pm);

        this.parameterValues = new HashMap<>();

        final Set<String> parameters = pm.getParameters();

        for (final String parameter : parameters) {
            final Expression expression = pm.getParameterExpression(parameter);

            final Context context = new Context() {
                public Double getValue(final String var) {
                    return GeneralizedSemIm.this.parameterValues.get(var);
                }
            };

            final double initialValue = expression.evaluate(context);
            this.parameterValues.put(parameter, initialValue);
        }
    }

    public GeneralizedSemIm(final GeneralizedSemPm pm, final SemIm semIm) {
        this(pm);
        final SemPm semPm = semIm.getSemPm();

        final Set<String> parameters = pm.getParameters();

        // If there are any missing freeParameters, just ignore the sem IM.
        for (final String parameter : parameters) {
            final Parameter paramObject = semPm.getParameter(parameter);

            if (paramObject == null) {
                return;
            }
        }

        for (final String parameter : parameters) {
            final Parameter paramObject = semPm.getParameter(parameter);

            if (paramObject == null) {
                throw new IllegalArgumentException("Parameter missing from Gaussian SEM IM: " + parameter);
            }

            double value = semIm.getParamValue(paramObject);

            if (paramObject.getType() == ParamType.VAR) {
                value = Math.sqrt(value);
            }

            setParameterValue(parameter, value);
        }
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     */
    public static GeneralizedSemIm serializableInstance() {
        return new GeneralizedSemIm(GeneralizedSemPm.serializableInstance());
    }

    /**
     * @return a copy of the stored GeneralizedSemPm.
     */
    public GeneralizedSemPm getGeneralizedSemPm() {
        return new GeneralizedSemPm(this.pm);
    }

    /**
     * @param parameter The parameter whose values is to be set.
     * @param value     The double value that <code>param</code> is to be set to.
     */
    public void setParameterValue(final String parameter, final double value) {
        if (parameter == null) {
            throw new NullPointerException("Parameter not specified.");
        }

        if (!(this.parameterValues.containsKey(parameter))) {
            throw new IllegalArgumentException("Not a parameter in this model: " + parameter);
        }

        this.parameterValues.put(parameter, value);
    }

    /**
     * @param parameter The parameter whose value is to be retrieved.
     * @return The retrieved value.
     */
    public double getParameterValue(final String parameter) {
        if (parameter == null) {
            throw new NullPointerException("Parameter not specified.");
        }

        if (!this.parameterValues.containsKey(parameter)) {
            throw new IllegalArgumentException("Not a parameter in this model: " + parameter);
        }

        return this.parameterValues.get(parameter);
    }

    /**
     * @return the user's String formula with numbers substituted for freeParameters, where substitutions exist.
     */
    public String getNodeSubstitutedString(final Node node) {
        final NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();
        final String expressionString = this.pm.getNodeExpressionString(node);

        if (expressionString == null) return null;

        final ExpressionLexer lexer = new ExpressionLexer(expressionString);
        final StringBuilder buf = new StringBuilder();
        Token token;

        while ((token = lexer.nextTokenIncludingWhitespace()) != Token.EOF) {
            final String tokenString = lexer.getTokenString();

            if (token == Token.PARAMETER) {
                final Double value = this.parameterValues.get(tokenString);

                if (value != null) {
                    buf.append(nf.format(value));
                    continue;
                }
            }

            buf.append(tokenString);
        }

        return buf.toString();
    }

    /**
     * @param node              The node whose expression is being evaluated.
     * @param substitutedValues A mapping from Strings parameter names to Double values; these values will be
     *                          substituted for the stored values where applicable.
     * @return the expression string with values substituted for freeParameters.
     */
    public String getNodeSubstitutedString(final Node node, final Map<String, Double> substitutedValues) {
        if (node == null) {
            throw new NullPointerException();
        }

        if (substitutedValues == null) {
            throw new NullPointerException();
        }

        final NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();
        final String expressionString = this.pm.getNodeExpressionString(node);

        final ExpressionLexer lexer = new ExpressionLexer(expressionString);
        final StringBuilder buf = new StringBuilder();
        Token token;

        while ((token = lexer.nextTokenIncludingWhitespace()) != Token.EOF) {
            final String tokenString = lexer.getTokenString();

            if (token == Token.PARAMETER) {
                Double value = substitutedValues.get(tokenString);

                if (value == null) {
                    value = this.parameterValues.get(tokenString);
                }

                if (value != null) {
                    buf.append(nf.format(value));
                    continue;
                }
            }

            buf.append(tokenString);
        }

        return buf.toString();
    }

    /**
     * @return a String representation of the IM, in this case a lsit of freeParameters and their values.
     */
    public String toString() {
        final List<String> parameters = new ArrayList<>(this.pm.getParameters());
        Collections.sort(parameters);
        final NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();

        final StringBuilder buf = new StringBuilder();
        final GeneralizedSemPm pm = getGeneralizedSemPm();
        buf.append("\nVariable nodes:\n");

        for (final Node node : pm.getVariableNodes()) {
            final String string = getNodeSubstitutedString(node);
            buf.append("\n").append(node).append(" = ").append(string);
        }

        buf.append("\n\nErrors:\n");

        for (final Node node : pm.getErrorNodes()) {
            final String string = getNodeSubstitutedString(node);
            buf.append("\n").append(node).append(" ~ ").append(string);
        }

        buf.append("\n\nParameter values:\n");
        for (final String parameter : parameters) {
            final double value = getParameterValue(parameter);
            buf.append("\n").append(parameter).append(" = ").append(nf.format(value));
        }

        return buf.toString();
    }

    public synchronized DataSet simulateData(final int sampleSize, final boolean latentDataSaved) {
        final long seed = RandomUtil.getInstance().getSeed();
        TetradLogger.getInstance().log("info", "Seed = " + seed);

        if (this.pm.getGraph().isTimeLagModel()) {
            return simulateTimeSeries(sampleSize);
        }

//        return simulateDataRecursive(sampleSize, latentDataSaved);
//        return simulateDataMinimizeSurface(sampleSize, latentDataSaved);
//        return simulateDataAvoidInfinity(sampleSize, latentDataSaved);
        return simulateDataFisher(sampleSize);
//        return simulateDataNSteps(sampleSize, latentDataSaved);
    }

    @Override
    public DataSet simulateData(final int sampleSize, final long seed, final boolean latentDataSaved) {
        final RandomUtil random = RandomUtil.getInstance();
        final long _seed = random.getSeed();
        random.setSeed(seed);
        final DataSet dataSet = simulateData(sampleSize, latentDataSaved);
        random.revertSeed(_seed);
        return dataSet;
    }

    private DataSet simulateTimeSeries(final int sampleSize) {
        final SemGraph semGraph = new SemGraph(getSemPm().getGraph());
        semGraph.setShowErrorTerms(true);
        final TimeLagGraph timeLagGraph = getSemPm().getGraph().getTimeLagGraph();

        final List<Node> variables = new ArrayList<>();

        for (final Node node : timeLagGraph.getLag0Nodes()) {
            if (node.getNodeType() == NodeType.ERROR) continue;
            variables.add(new ContinuousVariable(timeLagGraph.getNodeId(node).getName()));
        }

        final List<Node> lag0Nodes = timeLagGraph.getLag0Nodes();

        for (final Node node : new ArrayList<>(lag0Nodes)) {
            if (node.getNodeType() == NodeType.ERROR) {
                lag0Nodes.remove(node);
            }
        }

        final DataSet fullData = new BoxDataSet(new VerticalDoubleDataBox(sampleSize, variables.size()), variables);

        final Map<Node, Integer> nodeIndices = new HashMap<>();

        for (int i = 0; i < lag0Nodes.size(); i++) {
            nodeIndices.put(lag0Nodes.get(i), i);
        }

        final Graph contemporaneousDag = timeLagGraph.subgraph(timeLagGraph.getLag0Nodes());

        final List<Node> tierOrdering = contemporaneousDag.getCausalOrdering();

        for (final Node node : new ArrayList<>(tierOrdering)) {
            if (node.getNodeType() == NodeType.ERROR) {
                tierOrdering.remove(node);
            }
        }

        final Map<String, Double> variableValues = new HashMap<>();

        final Context context = new Context() {
            public Double getValue(final String term) {
                Double value = GeneralizedSemIm.this.parameterValues.get(term);

                if (value != null) {
                    return value;
                }

                value = variableValues.get(term);

                if (value != null) {
                    return value;
                } else {
                    return RandomUtil.getInstance().nextNormal(0, 1);
                }
            }
        };

        ROW:
        for (int currentStep = 0; currentStep < sampleSize; currentStep++) {
            for (final Node node : tierOrdering) {
                final Expression expression = this.pm.getNodeExpression(node);
                final double value = expression.evaluate(context);

                if (isSimulatePositiveDataOnly() && value < 0) {
                    currentStep--;
                    continue ROW;
                }

                final int col = nodeIndices.get(node);
                fullData.setDouble(currentStep, col, value);
                variableValues.put(node.getName(), value);
            }

            for (final Node node : lag0Nodes) {
                final TimeLagGraph.NodeId _id = timeLagGraph.getNodeId(node);

                for (int lag = 1; lag <= timeLagGraph.getMaxLag(); lag++) {
                    final Node _node = timeLagGraph.getNode(_id.getName(), lag);
                    final int col = lag0Nodes.indexOf(node);

                    if (_node == null) {
                        continue;
                    }

                    if (currentStep - lag + 1 >= 0) {
                        final double _value = fullData.getDouble((currentStep - lag + 1), col);
                        variableValues.put(_node.getName(), _value);
                    }
                }
            }
        }

        return fullData;
    }

    /**
     * This simulates data by picking random values for the exogenous terms and
     * percolating this information down through the SEM, assuming it is
     * acyclic. Fast for large simulations but hangs for cyclic models.
     *
     * @param sampleSize > 0.
     * @return the simulated data set.
     */
    public DataSet simulateDataRecursive(final int sampleSize, final boolean latentDataSaved) {
        final List<Node> variables = this.pm.getNodes();
        final Map<String, Double> std = new HashMap<>();

        final Map<String, Double> variableValues = new HashMap<>();

        final Context context = new Context() {
            public Double getValue(final String term) {
                Double value = GeneralizedSemIm.this.parameterValues.get(term);

                if (value != null) {
                    return value;
                }

                value = variableValues.get(term);

                if (value != null) {
                    return value * 2 / std.get(term);
                }

                throw new IllegalArgumentException("No value recorded for '" + term + "'");
            }
        };

        final List<Node> continuousVariables = new LinkedList<>();
        final List<Node> nonErrorVariables = this.pm.getVariableNodes();

        // Work with a copy of the variables, because their type can be set externally.
        for (final Node node : nonErrorVariables) {
            final ContinuousVariable var = new ContinuousVariable(node.getName());
            var.setNodeType(node.getNodeType());

            if (var.getNodeType() != NodeType.ERROR) {
                continuousVariables.add(var);
            }
        }

        final DataSet fullDataSet = new BoxDataSet(new VerticalDoubleDataBox(sampleSize, continuousVariables.size()), continuousVariables);

        // Create some index arrays to hopefully speed up the simulation.
        final SemGraph graph = this.pm.getGraph();
        final List<Node> tierOrdering = graph.getFullTierOrdering();

        final int[] tierIndices = new int[variables.size()];

        for (int i = 0; i < tierIndices.length; i++) {
            tierIndices[i] = nonErrorVariables.indexOf(tierOrdering.get(i));
        }

        final int[][] _parents = new int[variables.size()][];

        for (int i = 0; i < variables.size(); i++) {
            final Node node = variables.get(i);
            final List<Node> parents = graph.getParents(node);

            _parents[i] = new int[parents.size()];

            for (int j = 0; j < parents.size(); j++) {
                final Node _parent = parents.get(j);
                _parents[i][j] = variables.indexOf(_parent);
            }
        }


        // Do the simulation.
        for (int tier = 0; tier < variables.size(); tier++) {
            final double[] v = new double[sampleSize];

            final int col = tierIndices[tier];

            if (col == -1) {
                continue;
            }

            for (int row = 0; row < sampleSize; row++) {
                variableValues.clear();

                final Node node = tierOrdering.get(tier);
                final Expression expression = this.pm.getNodeExpression(node);
                final double value = expression.evaluate(context);
                v[row] = value;
                variableValues.put(node.getName(), value);


//                if (isSimulatePositiveDataOnly() && value < 0) {
//                    row--;
//                    continue ROW;
//                }

//                if (!Double.isNaN(selfLoopCoef) && row > 0) {
//                    value += selfLoopCoef * fullDataSet.getDouble(row - 1, col);
//                }

//                value = min(max(value, -5.), 5.);

                fullDataSet.setDouble(row, col, value);
            }

            std.put(tierOrdering.get(tier).getName(), sd(v));

//            for (int row = 0; row < sampleSize; row++) {
//                fullDataSet.setDouble(row, col, 2v[row] / std);
//            }
        }

        if (latentDataSaved) {
            return fullDataSet;
        } else {
            return DataUtils.restrictToMeasured(fullDataSet);
        }
    }


    public DataSet simulateDataMinimizeSurface(final int sampleSize, final boolean latentDataSaved) {
        final Map<String, Double> variableValues = new HashMap<>();

        final List<Node> continuousVariables = new LinkedList<>();
        final List<Node> variableNodes = this.pm.getVariableNodes();

        // Work with a copy of the variables, because their type can be set externally.
        for (final Node node : variableNodes) {
            final ContinuousVariable var = new ContinuousVariable(node.getName());
            var.setNodeType(node.getNodeType());

            if (var.getNodeType() != NodeType.ERROR) {
                continuousVariables.add(var);
            }
        }

        final DataSet fullDataSet = new BoxDataSet(new VerticalDoubleDataBox(sampleSize, continuousVariables.size()), continuousVariables);

        final Context context = new Context() {
            public Double getValue(final String term) {
                Double value = GeneralizedSemIm.this.parameterValues.get(term);

                if (value != null) {
                    return value;
                }

                value = variableValues.get(term);

                if (value != null) {
                    return value;
                }

                throw new IllegalArgumentException("No value recorded for '" + term + "'");
            }
        };

        final double[] _metric = new double[1];

        final MultivariateFunction function = new MultivariateFunction() {
            double metric;

            public double value(final double[] doubles) {
                for (int i = 0; i < variableNodes.size(); i++) {
                    variableValues.put(variableNodes.get(i).getName(), doubles[i]);
                }

                final double[] image = new double[doubles.length];

                for (int i = 0; i < variableNodes.size(); i++) {
                    final Node node = variableNodes.get(i);
                    final Expression expression = GeneralizedSemIm.this.pm.getNodeExpression(node);
                    image[i] = expression.evaluate(context);

                    if (Double.isNaN(image[i])) {
                        throw new IllegalArgumentException("Undefined value for expression " + expression);
                    }
                }

                this.metric = 0.0;

                for (int i = 0; i < variableNodes.size(); i++) {
                    final double diff = doubles[i] - image[i];
                    this.metric += diff * diff;
                }

                for (int i = 0; i < variableNodes.size(); i++) {
                    variableValues.put(variableNodes.get(i).getName(), image[i]);
                }

                _metric[0] = this.metric;

                return this.metric;
            }
        };

        final MultivariateOptimizer search = new PowellOptimizer(1e-7, 1e-7);

        // Do the simulation.
        ROW:
        for (int row = 0; row < sampleSize; row++) {

            // Take random draws from error distributions.
            for (final Node variable : variableNodes) {
                final Node error = this.pm.getErrorNode(variable);

                if (error == null) {
                    throw new NullPointerException();
                }

                final Expression expression = this.pm.getNodeExpression(error);
                final double value = expression.evaluate(context);

                if (Double.isNaN(value)) {
                    throw new IllegalArgumentException("Undefined value for expression: " + expression);
                }

                variableValues.put(error.getName(), value);
            }

            for (final Node variable : variableNodes) {
                variableValues.put(variable.getName(), 0.0);// RandomUtil.getInstance().nextUniform(-5, 5));
            }

            while (true) {

                double[] values = new double[variableNodes.size()];

                for (int i = 0; i < values.length; i++) {
                    values[i] = variableValues.get(variableNodes.get(i).getName());
                }

                final PointValuePair pair = search.optimize(
                        new InitialGuess(values),
                        new ObjectiveFunction(function),
                        GoalType.MINIMIZE,
                        new MaxEval(100000));

                values = pair.getPoint();

                for (int i = 0; i < variableNodes.size(); i++) {
                    if (isSimulatePositiveDataOnly() && values[i] < 0) {
                        row--;
                        continue ROW;
                    }

                    if (!Double.isNaN(this.selfLoopCoef) && row > 0) {
                        values[i] += this.selfLoopCoef * fullDataSet.getDouble(row - 1, i);
                    }

                    variableValues.put(variableNodes.get(i).getName(), values[i]);
                    fullDataSet.setDouble(row, i, values[i]);
                }

                if (_metric[0] < 0.01) {
                    break; // while
                }
            }
        }

        if (latentDataSaved) {
            return fullDataSet;
        } else {
            return DataUtils.restrictToMeasured(fullDataSet);
        }
    }

    public DataSet simulateDataAvoidInfinity(final int sampleSize, final boolean latentDataSaved) {
        final Map<String, Double> variableValues = new HashMap<>();

        final List<Node> continuousVariables = new LinkedList<>();
        final List<Node> variableNodes = this.pm.getVariableNodes();

        // Work with a copy of the variables, because their type can be set externally.
        for (final Node node : variableNodes) {
            final ContinuousVariable var = new ContinuousVariable(node.getName());
            var.setNodeType(node.getNodeType());

            if (var.getNodeType() != NodeType.ERROR) {
                continuousVariables.add(var);
            }
        }

        final DataSet fullDataSet = new BoxDataSet(new VerticalDoubleDataBox(sampleSize, continuousVariables.size()), continuousVariables);

        final Context context = new Context() {
            public Double getValue(final String term) {
                Double value = GeneralizedSemIm.this.parameterValues.get(term);

                if (value != null) {
                    return value;
                }

                value = variableValues.get(term);

                if (value != null) {
                    return value;
                }

                throw new IllegalArgumentException("No value recorded for '" + term + "'");
            }
        };

        boolean allInRange = true;

        // Do the simulation.
        ROW:
        for (int row = 0; row < sampleSize; row++) {

            // Take random draws from error distributions.
            for (final Node variable : variableNodes) {
                final Node error = this.pm.getErrorNode(variable);

                if (error == null) {
                    throw new NullPointerException();
                }

                final Expression expression = this.pm.getNodeExpression(error);
                final double value;

                value = expression.evaluate(context);

                if (Double.isNaN(value)) {
                    throw new IllegalArgumentException("Undefined value for expression: " + expression);
                }

                variableValues.put(error.getName(), value);
            }

            // Set the variable nodes to zero.
            for (final Node variable : variableNodes) {
                final Node error = this.pm.getErrorNode(variable);

                final Expression expression = this.pm.getNodeExpression(error);
                final double value = expression.evaluate(context);

                if (Double.isNaN(value)) {
                    throw new IllegalArgumentException("Undefined value for expression: " + expression);
                }

                variableValues.put(variable.getName(), 0.0);//value); //0.0; //RandomUtil.getInstance().nextUniform(-1, 1));
            }

            // Repeatedly update variable values until one of them hits infinity or negative infinity or
            // convergence within delta.

            final double delta = 1e-10;
            int count = -1;

            while (++count < 5000) {
                final double[] values = new double[variableNodes.size()];

                for (int i = 0; i < values.length; i++) {
                    final Node node = variableNodes.get(i);
                    final Expression expression = this.pm.getNodeExpression(node);
                    final double value = expression.evaluate(context);
                    values[i] = value;
                }

                allInRange = true;

                for (int i = 0; i < values.length; i++) {
                    final Node node = variableNodes.get(i);

                    // If any of the variables hasn't converged or if any of the variable values has gone
                    // outside of the bound (-1e6, 1e6), judge nonconvergence and pick another random starting point.
                    if (!(Math.abs(variableValues.get(node.getName()) - values[i]) < delta)) {
                        if (!(Math.abs(variableValues.get(node.getName())) < 1e6)) {
                            if (count < 1000) {
                                row--;
                                continue ROW;
                            }
                        }

                        allInRange = false;
                        break;
                    }

                }

                for (int i = 0; i < variableNodes.size(); i++) {
                    variableValues.put(variableNodes.get(i).getName(), values[i]);
                }

                if (allInRange) {
                    break;
                }
            }

            if (!allInRange) {
                if (count < 10000) {
                    row--;
                    System.out.println("Trying another starting point...");
                    continue;
                } else {
                    System.out.println("Couldn't converge in simulation.");

                    for (int i = 0; i < variableNodes.size(); i++) {
                        fullDataSet.setDouble(row, i, Double.NaN);
                    }

                    return fullDataSet;
                }
            }

            for (int i = 0; i < variableNodes.size(); i++) {
                double value = variableValues.get(variableNodes.get(i).getName());

                if (isSimulatePositiveDataOnly() && value < 0) {
                    row--;
                    continue ROW;
                }

                if (!Double.isNaN(this.selfLoopCoef) && row > 0) {
                    value += this.selfLoopCoef * fullDataSet.getDouble(row - 1, i);
                }

                fullDataSet.setDouble(row, i, value);
            }
        }

        if (latentDataSaved) {
            return fullDataSet;
        } else {
            return DataUtils.restrictToMeasured(fullDataSet);
        }

    }

    /**
     * Simulates data using the model of R. A. Fisher, for a linear model. Shocks are
     * applied every so many steps. A data point is recorded before each shock is
     * administered. If convergence happens before that number of steps has been reached,
     * a data point is recorded and a new shock immediately applied. The model may be
     * cyclic. If cyclic, all eigenvalues for the coefficient matrix must be less than 1,
     * though this is not checked. Uses an interval between shocks of 50 and a convergence
     * threshold of 1e-5. Uncorrelated Gaussian shocks are used.
     *
     * @param sampleSize The number of samples to be drawn. Must be a positive
     *                   integer.
     */
    public synchronized DataSet simulateDataFisher(final int sampleSize) {
        return simulateDataFisher(sampleSize, 50, 1e-10);
    }

    /**
     * Simulates data using the model of R. A. Fisher, for a linear model. Shocks are
     * applied every so many steps. A data point is recorded before each shock is
     * administered. If convergence happens before that number of steps has been reached,
     * a data point is recorded and a new shock immediately applied. The model may be
     * cyclic. If cyclic, all eigenvalues for the coefficient matrix must be less than 1,
     * though this is not checked.
     *
     * @param sampleSize            The number of samples to be drawn.
     * @param intervalBetweenShocks External shock is applied every this many steps.
     *                              Must be positive integer.
     * @param epsilon               The convergence criterion; |xi.t - xi.t-1| < epsilon.
     */
    public synchronized DataSet simulateDataFisher(final int sampleSize, final int intervalBetweenShocks,
                                                   final double epsilon) {
        boolean printedUndefined = false;
        boolean printedInfinite = false;

        if (intervalBetweenShocks < 1) throw new IllegalArgumentException(
                "Interval between shocks must be >= 1: " + intervalBetweenShocks);
        if (epsilon <= 0.0) throw new IllegalArgumentException(
                "Epsilon must be > 0: " + epsilon);

        final Map<String, Double> variableValues = new HashMap<>();

        final Context context = new Context() {
            public Double getValue(final String term) {
                Double value = GeneralizedSemIm.this.parameterValues.get(term);

                if (value != null) {
                    return value;
                }

                value = variableValues.get(term);

                if (value != null) {
                    return value;
                }

                throw new IllegalArgumentException("No value recorded for '" + term + "'");
            }
        };

        final List<Node> variableNodes = this.pm.getVariableNodes();

        double[] t1 = new double[variableNodes.size()];
        double[] t2 = new double[variableNodes.size()];
        final double[] shocks = new double[variableNodes.size()];
        final double[][] all = new double[variableNodes.size()][sampleSize];

        // Do the simulation.

        for (int row = 0; row < sampleSize; row++) {
            for (int j = 0; j < t1.length; j++) {
                final Node error = this.pm.getErrorNode(variableNodes.get(j));

                if (error == null) {
                    throw new NullPointerException();
                }

                final Expression expression = this.pm.getNodeExpression(error);
                final double value = expression.evaluate(context);

                if (Double.isNaN(value)) {
                    throw new IllegalArgumentException("Undefined value for expression: " + expression);
                }

                variableValues.put(error.getName(), value);
                shocks[j] = value;
                t2[j] += shocks[j];
            }

            for (int i = 0; i < intervalBetweenShocks; i++) {
                for (int j = 0; j < t1.length; j++) {
                    final Node node = variableNodes.get(j);
                    final Expression expression = this.pm.getNodeExpression(node);
                    t2[j] = expression.evaluate(context);

                    if (Double.isNaN(t2[j])) {
                        if (!printedUndefined) {
                            System.out.println("Undefined value.");
                            printedUndefined = true;
                        }
                    }

                    if (Double.isInfinite(t2[j])) {
                        if (!printedInfinite) {
                            System.out.println("Infinite value.");
                            printedInfinite = true;
                        }
                    }

                    variableValues.put(node.getName(), t2[j]);
                }

                boolean converged = true;

                for (int j = 0; j < t1.length; j++) {
                    if (Math.abs(t2[j] - t1[j]) > epsilon) {
                        converged = false;
                        break;
                    }
                }

                final double[] t3 = t1;
                t1 = t2;
                t2 = t3;

                if (converged) {
                    break;
                }
            }

            for (int j = 0; j < t1.length; j++) {
                all[j][row] = t1[j];
            }
        }

        final List<Node> continuousVars = new ArrayList<>();

        for (final Node node : variableNodes) {
            final ContinuousVariable var = new ContinuousVariable(node.getName());
            var.setNodeType(node.getNodeType());
            continuousVars.add(var);
        }

        final BoxDataSet boxDataSet = new BoxDataSet(new VerticalDoubleDataBox(all), continuousVars);
        return DataUtils.restrictToMeasured(boxDataSet);
    }


    public Vector simulateOneRecord(final Vector e) {
        final Map<String, Double> variableValues = new HashMap<>();

        final List<Node> variableNodes = this.pm.getVariableNodes();

        final Context context = new Context() {
            public Double getValue(final String term) {
                Double value = GeneralizedSemIm.this.parameterValues.get(term);

                if (value != null) {
                    return value;
                }

                value = variableValues.get(term);

                if (value != null) {
                    return value;
                }

                throw new IllegalArgumentException("No value recorded for '" + term + "'");
            }
        };

        // Take random draws from error distributions.
        for (int i = 0; i < variableNodes.size(); i++) {
            final Node error = this.pm.getErrorNode(variableNodes.get(i));

            if (error == null) {
                throw new NullPointerException();
            }

            variableValues.put(error.getName(), e.get(i));
        }

        // Set the variable nodes to zero.
        for (final Node variable : variableNodes) {
            variableValues.put(variable.getName(), 0.0);// RandomUtil.getInstance().nextUniform(-5, 5));
        }

        // Repeatedly update variable values until one of them hits infinity or negative infinity or
        // convergence within delta.

        final double delta = 1e-6;
        int count = -1;

        while (++count < 10000) {
            final double[] values = new double[variableNodes.size()];

            for (int i = 0; i < values.length; i++) {
                final Node node = variableNodes.get(i);
                final Expression expression = this.pm.getNodeExpression(node);
                final double value = expression.evaluate(context);
                values[i] = value;
            }

            boolean allInRange = true;

            for (int i = 0; i < values.length; i++) {
                final Node node = variableNodes.get(i);

                if (!(Math.abs(variableValues.get(node.getName()) - values[i]) < delta)) {
                    allInRange = false;
                    break;
                }
            }


            for (int i = 0; i < variableNodes.size(); i++) {
                variableValues.put(variableNodes.get(i).getName(), values[i]);
            }

            if (allInRange) {
                break;
            }
        }

        final Vector _case = new Vector(e.size());

        for (int i = 0; i < variableNodes.size(); i++) {
            final double value = variableValues.get(variableNodes.get(i).getName());
            _case.set(i, value);
        }

        return _case;
    }

    public DataSet simulateDataNSteps(final int sampleSize, final boolean latentDataSaved) {
        final Map<String, Double> variableValues = new HashMap<>();

        final List<Node> continuousVariables = new LinkedList<>();
        final List<Node> variableNodes = this.pm.getVariableNodes();

        // Work with a copy of the variables, because their type can be set externally.
        for (final Node node : variableNodes) {
            final ContinuousVariable var = new ContinuousVariable(node.getName());
            var.setNodeType(node.getNodeType());

            if (var.getNodeType() != NodeType.ERROR) {
                continuousVariables.add(var);
            }
        }

        final DataSet fullDataSet = new BoxDataSet(new VerticalDoubleDataBox(sampleSize, continuousVariables.size()), continuousVariables);

        final Context context = new Context() {
            public Double getValue(final String term) {
                Double value = GeneralizedSemIm.this.parameterValues.get(term);

                if (value != null) {
                    return value;
                }

                value = variableValues.get(term);

                if (value != null) {
                    return value;
                }

                throw new IllegalArgumentException("No value recorded for '" + term + "'");
            }
        };

        // Do the simulation.
        ROW:
        for (int row = 0; row < sampleSize; row++) {

            // Take random draws from error distributions.
            for (final Node variable : variableNodes) {
                final Node error = this.pm.getErrorNode(variable);

                if (error == null) {
                    throw new NullPointerException();
                }

                final Expression expression = this.pm.getNodeExpression(error);
                final double value = expression.evaluate(context);

                if (Double.isNaN(value)) {
                    throw new IllegalArgumentException("Undefined value for expression: " + expression);
                }

                variableValues.put(error.getName(), value);
            }

            // Set the variable nodes to zero.
            for (final Node variable : variableNodes) {
                variableValues.put(variable.getName(), 0.0);// RandomUtil.getInstance().nextUniform(-5, 5));
            }

            // Repeatedly update variable values until one of them hits infinity or negative infinity or
            // convergence within delta.

            for (int m = 0; m < 1; m++) {
                final double[] values = new double[variableNodes.size()];

                for (int i = 0; i < values.length; i++) {
                    final Node node = variableNodes.get(i);
                    final Expression expression = this.pm.getNodeExpression(node);
                    final double value = expression.evaluate(context);

                    if (Double.isNaN(value)) {
                        throw new IllegalArgumentException("Undefined value for expression: " + expression);
                    }

                    values[i] = value;
                }

                for (final double value : values) {
                    if (value == Double.POSITIVE_INFINITY || value == Double.NEGATIVE_INFINITY) {
                        row--;
                        continue ROW;
                    }
                }

                for (int i = 0; i < variableNodes.size(); i++) {
                    variableValues.put(variableNodes.get(i).getName(), values[i]);
                }

            }

            for (int i = 0; i < variableNodes.size(); i++) {
                final double value = variableValues.get(variableNodes.get(i).getName());
                fullDataSet.setDouble(row, i, value);
            }
        }

        if (latentDataSaved) {
            return fullDataSet;
        } else {
            return DataUtils.restrictToMeasured(fullDataSet);
        }

    }


    public GeneralizedSemPm getSemPm() {
        return new GeneralizedSemPm(this.pm);
    }

    public void setSubstitutions(final Map<String, Double> parameterValues) {
        for (final String parameter : parameterValues.keySet()) {
            if (this.parameterValues.containsKey(parameter)) {
                this.parameterValues.put(parameter, parameterValues.get(parameter));
            }
        }
    }

    private boolean isSimulatePositiveDataOnly() {
        return this.simulatePositiveDataOnly;
    }

    public void setSimulatePositiveDataOnly(final boolean simulatedPositiveDataOnly) {
        this.simulatePositiveDataOnly = simulatedPositiveDataOnly;
    }
}



