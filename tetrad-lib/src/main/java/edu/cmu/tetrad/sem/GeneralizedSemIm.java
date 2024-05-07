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
import edu.cmu.tetrad.calculator.parser.ExpressionLexer;
import edu.cmu.tetrad.calculator.parser.Token;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.util.Im;
import edu.cmu.tetrad.util.NumberFormatUtil;
import edu.cmu.tetrad.util.RandomUtil;
import edu.cmu.tetrad.util.Vector;
import org.apache.commons.math3.analysis.MultivariateFunction;
import org.apache.commons.math3.optim.InitialGuess;
import org.apache.commons.math3.optim.MaxEval;
import org.apache.commons.math3.optim.PointValuePair;
import org.apache.commons.math3.optim.nonlinear.scalar.GoalType;
import org.apache.commons.math3.optim.nonlinear.scalar.MultivariateOptimizer;
import org.apache.commons.math3.optim.nonlinear.scalar.ObjectiveFunction;
import org.apache.commons.math3.optim.nonlinear.scalar.noderiv.PowellOptimizer;
import org.apache.commons.math3.util.FastMath;

import java.io.Serial;
import java.text.NumberFormat;
import java.util.*;

/**
 * Represents a generalized SEM-instantiated model. The parametric form of this model allows arbitrary equations for
 * variables. This instantiated model gives values for all the parameters of the parameterized model.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class GeneralizedSemIm implements Im, Simulator {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The wrapped PM, that holds all the expressions and structure for the model.
     */
    private final GeneralizedSemPm pm;

    /**
     * A map from freeParameters names to their values--these form the context for evaluating expressions. Variables do
     * not appear in this list. All freeParameters are double-valued.
     */
    private final Map<String, Double> parameterValues;

    /**
     * Whether to guarantee that the IID is preserved when simulating data.
     */
    private boolean guaranteeIid = true;

    /**
     * Constructs a new GeneralizedSemIm from the given GeneralizedSemPm by picking values for each of the
     * freeParameters from their initial distributions.
     *
     * @param pm the GeneralizedSemPm. Includes all the equations and distributions of the model.
     */
    public GeneralizedSemIm(GeneralizedSemPm pm) {
        this.pm = new GeneralizedSemPm(pm);

        this.parameterValues = new HashMap<>();

        Set<String> parameters = pm.getParameters();

        for (String parameter : parameters) {
            Expression expression = pm.getParameterExpression(parameter);

            Context context = GeneralizedSemIm.this.parameterValues::get;

            double initialValue = expression.evaluate(context);
            this.parameterValues.put(parameter, initialValue);
        }
    }

    /**
     * Initializes a GeneralizedSemIm object by applying values to free parameters from the given GeneralizedSemPm and
     * SemIm.
     *
     * @param pm    The GeneralizedSemPm object that contains the equations and distributions of the model.
     * @param semIm The SemIm object to apply values from.
     */
    public GeneralizedSemIm(GeneralizedSemPm pm, SemIm semIm) {
        this(pm);
        SemPm semPm = semIm.getSemPm();

        Set<String> parameters = pm.getParameters();

        // If there are any missing freeParameters, ignore the sem IM.
        for (String parameter : parameters) {
            Parameter paramObject = semPm.getParameter(parameter);

            if (paramObject == null) {
                return;
            }
        }

        for (String parameter : parameters) {
            Parameter paramObject = semPm.getParameter(parameter);

            if (paramObject == null) {
                throw new IllegalArgumentException("Parameter missing from Gaussian SEM IM: " + parameter);
            }

            double value = semIm.getParamValue(paramObject);

            if (paramObject.getType() == ParamType.VAR) {
                value = FastMath.sqrt(value);
            }

            setParameterValue(parameter, value);
        }
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @return a {@link edu.cmu.tetrad.sem.GeneralizedSemIm} object
     */
    public static GeneralizedSemIm serializableInstance() {
        return new GeneralizedSemIm(GeneralizedSemPm.serializableInstance());
    }

    /**
     * Retrieves the GeneralizedSemPm object associated with this GeneralizedSemIm.
     *
     * @return The GeneralizedSemPm object.
     */
    public GeneralizedSemPm getGeneralizedSemPm() {
        return new GeneralizedSemPm(this.pm);
    }

    /**
     * <p>setParameterValue.</p>
     *
     * @param parameter The parameter whose values are to be set.
     * @param value     The double value that <code>param</code> is to be set to.
     */
    public void setParameterValue(String parameter, double value) {
        if (parameter == null) {
            throw new NullPointerException("Parameter not specified.");
        }

        if (!(this.parameterValues.containsKey(parameter))) {
            throw new IllegalArgumentException("Not a parameter in this model: " + parameter);
        }

        this.parameterValues.put(parameter, value);
    }

    /**
     * Retrieves the value of a parameter in the model.
     *
     * @param parameter The name of the parameter to retrieve.
     * @return The value of the parameter.
     * @throws NullPointerException     If the parameter name is null.
     * @throws IllegalArgumentException If the parameter name is not present in the model.
     */
    public double getParameterValue(String parameter) {
        if (parameter == null) {
            throw new NullPointerException("Parameter not specified.");
        }

        if (!this.parameterValues.containsKey(parameter)) {
            throw new IllegalArgumentException("Not a parameter in this model: " + parameter);
        }

        return this.parameterValues.get(parameter);
    }

    /**
     * Retrieves the substituted string representation of a given Node.
     *
     * @param node The Node whose expression is being evaluated.
     * @return The substituted expression string for the Node, or null if the expression is null.
     */
    public String getNodeSubstitutedString(Node node) {
        NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();
        String expressionString = this.pm.getNodeExpressionString(node);

        if (expressionString == null) return null;

        ExpressionLexer lexer = new ExpressionLexer(expressionString);
        StringBuilder buf = new StringBuilder();
        Token token;

        while ((token = lexer.nextTokenIncludingWhitespace()) != Token.EOF) {
            String tokenString = lexer.getTokenString();

            if (token == Token.PARAMETER) {
                Double value = this.parameterValues.get(tokenString);

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
     * Retrieves the substituted string representation of a given Node.
     *
     * @param node              The Node whose expression is being evaluated.
     * @param substitutedValues A map of substituted values for parameters.
     * @return The substituted expression string for the Node.
     * @throws NullPointerException if the node or substitutedValues is null.
     */
    public String getNodeSubstitutedString(Node node, Map<String, Double> substitutedValues) {
        if (node == null) {
            throw new NullPointerException();
        }

        if (substitutedValues == null) {
            throw new NullPointerException();
        }

        NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();
        String expressionString = this.pm.getNodeExpressionString(node);

        ExpressionLexer lexer = new ExpressionLexer(expressionString);
        StringBuilder buf = new StringBuilder();
        Token token;

        while ((token = lexer.nextTokenIncludingWhitespace()) != Token.EOF) {
            String tokenString = lexer.getTokenString();

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
     * Returns a string representation of the object.
     *
     * @return a string representation of the object.
     */
    public String toString() {
        List<String> parameters = new ArrayList<>(this.pm.getParameters());
        Collections.sort(parameters);
        NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();

        StringBuilder buf = new StringBuilder();
        GeneralizedSemPm pm = getGeneralizedSemPm();
        buf.append("\nVariable nodes:\n");

        for (Node node : pm.getVariableNodes()) {
            String string = getNodeSubstitutedString(node);
            buf.append("\n").append(node).append(" = ").append(string);
        }

        buf.append("\n\nErrors:\n");

        for (Node node : pm.getErrorNodes()) {
            String string = getNodeSubstitutedString(node);
            buf.append("\n").append(node).append(" ~ ").append(string);
        }

        buf.append("\n\nParameter values:\n");
        for (String parameter : parameters) {
            double value = getParameterValue(parameter);
            buf.append("\n").append(parameter).append(" = ").append(nf.format(value));
        }

        return buf.toString();
    }

    /**
     * Simulates data based on the given parameters.
     *
     * @param sampleSize      The number of rows to simulate.
     * @param latentDataSaved If true, latent variables are saved in the data set.
     * @return A DataSet object representing the simulated data.
     */
    public DataSet simulateData(int sampleSize, boolean latentDataSaved) {
        if (this.pm.getGraph().isTimeLagModel()) {
            return simulateTimeSeries(sampleSize);
        } else if (!this.pm.getGraph().paths().existsDirectedCycle()) {
            return simulateDataRecursive(sampleSize, latentDataSaved);
        } else {
            return simulateDataFisher(sampleSize);
        }
    }

    private DataSet simulateTimeSeries(int sampleSize) {
        SemGraph semGraph = new SemGraph(getSemPm().getGraph());
        semGraph.setShowErrorTerms(true);
        TimeLagGraph timeLagGraph = getSemPm().getGraph().getTimeLagGraph();

        List<Node> variables = new ArrayList<>();

        for (Node node : timeLagGraph.getLag0Nodes()) {
            if (node.getNodeType() == NodeType.ERROR) continue;
            variables.add(new ContinuousVariable(timeLagGraph.getNodeId(node).getName()));
        }

        List<Node> lag0Nodes = timeLagGraph.getLag0Nodes();

        lag0Nodes.removeIf(node -> node.getNodeType() == NodeType.ERROR);

        DataSet fullData = new BoxDataSet(new VerticalDoubleDataBox(sampleSize, variables.size()), variables);

        Map<Node, Integer> nodeIndices = new HashMap<>();

        for (int i = 0; i < lag0Nodes.size(); i++) {
            nodeIndices.put(lag0Nodes.get(i), i);
        }

        Graph contemporaneousDag = timeLagGraph.subgraph(timeLagGraph.getLag0Nodes());

        Paths paths = contemporaneousDag.paths();
        List<Node> initialOrder = contemporaneousDag.getNodes();
        List<Node> tierOrdering = paths.getValidOrder(initialOrder, true);

        tierOrdering.removeIf(node -> node.getNodeType() == NodeType.ERROR);

        Map<String, Double> variableValues = new HashMap<>();

        Context context = term -> {
            Double value = GeneralizedSemIm.this.parameterValues.get(term);

            if (value != null) {
                return value;
            }

            value = variableValues.get(term);

            return Objects.requireNonNullElseGet(value, () ->
                    RandomUtil.getInstance().nextNormal(0, 1));
        };

        for (int currentStep = 0; currentStep < sampleSize; currentStep++) {
            for (Node node : tierOrdering) {
                Expression expression = this.pm.getNodeExpression(node);
                double value = expression.evaluate(context);

                int col = nodeIndices.get(node);
                fullData.setDouble(currentStep, col, value);
                variableValues.put(node.getName(), value);
            }

            for (Node node : lag0Nodes) {
                TimeLagGraph.NodeId _id = timeLagGraph.getNodeId(node);

                for (int lag = 1; lag <= timeLagGraph.getMaxLag(); lag++) {
                    Node _node = timeLagGraph.getNode(_id.getName(), lag);
                    int col = lag0Nodes.indexOf(node);

                    if (_node == null) {
                        continue;
                    }

                    if (currentStep - lag + 1 >= 0) {
                        double _value = fullData.getDouble((currentStep - lag + 1), col);
                        variableValues.put(_node.getName(), _value);
                    }
                }
            }
        }

        return fullData;
    }

    /**
     * This simulates data by picking random values for the exogenous terms and percolating this information down
     * through the SEM, assuming it is acyclic. Fast for large simulations but hangs for cyclic models.
     *
     * @param sampleSize      &gt; 0.
     * @param latentDataSaved a boolean
     * @return the simulated data set.
     */
    public DataSet simulateDataRecursive(int sampleSize, boolean latentDataSaved) {
        boolean printedUndefined = false;
        boolean printedInfinite = false;

        Map<String, Double> variableValues = new HashMap<>();

        Context context = term -> {
            Double value = GeneralizedSemIm.this.parameterValues.get(term);

            if (value != null) {
                return value;
            }

            value = variableValues.get(term);

            if (value != null) {
                return value;
            }

            throw new IllegalArgumentException("No value recorded for '" + term + "'");
        };

        List<Node> variableNodes = this.pm.getVariableNodes();
        Map<Node, Integer> indices = new HashMap<>();
        for (int i = 0; i < variableNodes.size(); i++) indices.put(variableNodes.get(i), i);
        double[][] all = new double[variableNodes.size()][sampleSize];
        List<Node> order = pm.getGraph().paths().getValidOrder(variableNodes, true);

        // Do the simulation.
        for (int row = 0; row < sampleSize; row++) {
            for (Node node : order) {
                Node error = this.pm.getErrorNode(node);

                if (error == null) {
                    throw new NullPointerException();
                }

                Expression errorExpression = this.pm.getNodeExpression(error);
                double errorValue = errorExpression.evaluate(context);

                if (Double.isNaN(errorValue)) {
                    throw new IllegalArgumentException("Undefined errorValue for error expression: " + errorExpression);
                }

                variableValues.put(error.getName(), errorValue);

                Expression nodeExpression = this.pm.getNodeExpression(node);
                double nodeValue = nodeExpression.evaluate(context);

                if (Double.isNaN(nodeValue)) {
                    if (!printedUndefined) {
                        System.out.println("Undefined errorValue.");
                        printedUndefined = true;
                    }
                }

                if (Double.isInfinite(nodeValue)) {
                    if (!printedInfinite) {
                        System.out.println("Infinite errorValue.");
                        printedInfinite = true;
                    }
                }

                variableValues.put(node.getName(), nodeValue);
                all[indices.get(node)][row] = nodeValue;
            }
        }

        List<Node> continuousVars = new ArrayList<>();

        for (Node node : variableNodes) {
            ContinuousVariable var = new ContinuousVariable(node.getName());
            var.setNodeType(node.getNodeType());
            continuousVars.add(var);
        }

        BoxDataSet fullDataSet = new BoxDataSet(new VerticalDoubleDataBox(all), continuousVars);

        if (latentDataSaved) {
            return fullDataSet;
        } else {
            return DataTransforms.restrictToMeasured(fullDataSet);
        }
    }


    /**
     * Simulates data by minimizing the surface defined by the given sample size and whether latent data is saved.
     *
     * @param sampleSize      The size of the sample to simulate.
     * @param latentDataSaved Specifies whether the latent data should be saved.
     * @return The simulated data set.
     */
    public DataSet simulateDataMinimizeSurface(int sampleSize, boolean latentDataSaved) {
        Map<String, Double> variableValues = new HashMap<>();

        List<Node> continuousVariables = new LinkedList<>();
        List<Node> variableNodes = this.pm.getVariableNodes();

        // Work with a copy of the variables, because their type can be set externally.
        for (Node node : variableNodes) {
            ContinuousVariable var = new ContinuousVariable(node.getName());
            var.setNodeType(node.getNodeType());

            if (var.getNodeType() != NodeType.ERROR) {
                continuousVariables.add(var);
            }
        }

        DataSet fullDataSet = new BoxDataSet(new VerticalDoubleDataBox(sampleSize, continuousVariables.size()), continuousVariables);

        Context context = term -> {
            Double value = GeneralizedSemIm.this.parameterValues.get(term);

            if (value != null) {
                return value;
            }

            value = variableValues.get(term);

            if (value != null) {
                return value;
            }

            throw new IllegalArgumentException("No value recorded for '" + term + "'");
        };

        double[] _metric = new double[1];

        MultivariateFunction function = new MultivariateFunction() {
            double metric;

            public double value(double[] doubles) {
                for (int i = 0; i < variableNodes.size(); i++) {
                    variableValues.put(variableNodes.get(i).getName(), doubles[i]);
                }

                double[] image = new double[doubles.length];

                for (int i = 0; i < variableNodes.size(); i++) {
                    Node node = variableNodes.get(i);
                    Expression expression = GeneralizedSemIm.this.pm.getNodeExpression(node);
                    image[i] = expression.evaluate(context);

                    if (Double.isNaN(image[i])) {
                        throw new IllegalArgumentException("Undefined value for expression " + expression);
                    }
                }

                this.metric = 0.0;

                for (int i = 0; i < variableNodes.size(); i++) {
                    double diff = doubles[i] - image[i];
                    this.metric += diff * diff;
                }

                for (int i = 0; i < variableNodes.size(); i++) {
                    variableValues.put(variableNodes.get(i).getName(), image[i]);
                }

                _metric[0] = this.metric;

                return this.metric;
            }
        };

        MultivariateOptimizer search = new PowellOptimizer(1e-7, 1e-7);

        // Do the simulation.
        for (int row = 0; row < sampleSize; row++) {

            // Take random draws from error distributions.
            for (Node variable : variableNodes) {
                Node error = this.pm.getErrorNode(variable);

                if (error == null) {
                    throw new NullPointerException();
                }

                Expression expression = this.pm.getNodeExpression(error);
                double value = expression.evaluate(context);

                if (Double.isNaN(value)) {
                    throw new IllegalArgumentException("Undefined value for expression: " + expression);
                }

                variableValues.put(error.getName(), value);
            }

            for (Node variable : variableNodes) {
                variableValues.put(variable.getName(), 0.0);// RandomUtil.getInstance().nextUniform(-5, 5));
            }

            do {

                double[] values = new double[variableNodes.size()];

                for (int i = 0; i < values.length; i++) {
                    values[i] = variableValues.get(variableNodes.get(i).getName());
                }

                PointValuePair pair = search.optimize(
                        new InitialGuess(values),
                        new ObjectiveFunction(function),
                        GoalType.MINIMIZE,
                        new MaxEval(100000));

                values = pair.getPoint();

                for (int i = 0; i < variableNodes.size(); i++) {
                    variableValues.put(variableNodes.get(i).getName(), values[i]);
                    fullDataSet.setDouble(row, i, values[i]);
                }

            } while (!(_metric[0] < 0.01));
        }

        if (latentDataSaved) {
            return fullDataSet;
        } else {
            return DataTransforms.restrictToMeasured(fullDataSet);
        }
    }

    /**
     * Simulates data avoiding infinity values.
     *
     * @param sampleSize      The number of data samples to simulate.
     * @param latentDataSaved Indicates whether the latent (unmeasured) data is saved in the result.
     * @return The simulated data as a DataSet object.
     * @throws IllegalArgumentException if undefined value is encountered during simulation.
     * @throws NullPointerException     if error node is null for any variable node.
     */
    public DataSet simulateDataAvoidInfinity(int sampleSize, boolean latentDataSaved) {
        Map<String, Double> variableValues = new HashMap<>();

        List<Node> continuousVariables = new LinkedList<>();
        List<Node> variableNodes = this.pm.getVariableNodes();

        // Work with a copy of the variables, because their type can be set externally.
        for (Node node : variableNodes) {
            ContinuousVariable var = new ContinuousVariable(node.getName());
            var.setNodeType(node.getNodeType());

            if (var.getNodeType() != NodeType.ERROR) {
                continuousVariables.add(var);
            }
        }

        DataSet fullDataSet = new BoxDataSet(new VerticalDoubleDataBox(sampleSize, continuousVariables.size()), continuousVariables);

        Context context = term -> {
            Double value = GeneralizedSemIm.this.parameterValues.get(term);

            if (value != null) {
                return value;
            }

            value = variableValues.get(term);

            if (value != null) {
                return value;
            }

            throw new IllegalArgumentException("No value recorded for '" + term + "'");
        };

        boolean allInRange = true;

        // Do the simulation.
        ROW:
        for (int row = 0; row < sampleSize; row++) {

            // Take random draws from error distributions.
            for (Node variable : variableNodes) {
                Node error = this.pm.getErrorNode(variable);

                if (error == null) {
                    throw new NullPointerException();
                }

                Expression expression = this.pm.getNodeExpression(error);
                double value;

                value = expression.evaluate(context);

                if (Double.isNaN(value)) {
                    throw new IllegalArgumentException("Undefined value for expression: " + expression);
                }

                variableValues.put(error.getName(), value);
            }

            // Set the variable nodes to zero.
            for (Node variable : variableNodes) {
                Node error = this.pm.getErrorNode(variable);

                Expression expression = this.pm.getNodeExpression(error);
                double value = expression.evaluate(context);

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
                double[] values = new double[variableNodes.size()];

                for (int i = 0; i < values.length; i++) {
                    Node node = variableNodes.get(i);
                    Expression expression = this.pm.getNodeExpression(node);
                    double value = expression.evaluate(context);
                    values[i] = value;
                }

                allInRange = true;

                for (int i = 0; i < values.length; i++) {
                    Node node = variableNodes.get(i);

                    // If any of the variables hasn't converged or if any of the variable values has gone
                    // outside the bound (-1e6, 1e6), judge non-convergence and pick another random starting point.
                    if (!(FastMath.abs(variableValues.get(node.getName()) - values[i]) < delta)) {
                        if (!(FastMath.abs(variableValues.get(node.getName())) < 1e6)) {
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
                row--;
                System.out.println("Trying another starting point...");
                continue;
            }

            for (int i = 0; i < variableNodes.size(); i++) {
                double value = variableValues.get(variableNodes.get(i).getName());
                fullDataSet.setDouble(row, i, value);
            }
        }

        if (latentDataSaved) {
            return fullDataSet;
        } else {
            return DataTransforms.restrictToMeasured(fullDataSet);
        }

    }

    /**
     * Simulates data using the model of R. A. Fisher, for a linear model. Shocks are applied every so many steps. A
     * data point is recorded before each shock is administered. If convergence happens before that number of steps has
     * been reached, a data point is recorded and a new shock immediately applied. The model may be cyclic. If cyclic,
     * all eigenvalues for the coefficient matrix must be less than 1, though this is not checked. Uses an interval
     * between shocks of 50 and a convergence threshold of 1e-5. Uncorrelated Gaussian shocks are used.
     *
     * @param sampleSize The number of samples to be drawn. Must be a positive integer.
     * @return a {@link edu.cmu.tetrad.data.DataSet} object
     */
    public DataSet simulateDataFisher(int sampleSize) {
        return simulateDataFisher(sampleSize, 50, 1e-10);
    }

    /**
     * Simulates data using the model of R. A. Fisher, for a linear model. Shocks are applied every so many steps. A
     * data point is recorded before each shock is administered. If convergence happens before that number of steps has
     * been reached, a data point is recorded and a new shock immediately applied. The model may be cyclic. If cyclic,
     * all eigenvalues for the coefficient matrix must be less than 1, though this is not checked.
     *
     * @param sampleSize            The number of samples to be drawn.
     * @param intervalBetweenShocks External shock is applied every this many steps. Must be positive integer.
     * @param epsilon               The convergence criterion; |xi.t - xi.t-1| &lt; epsilon.
     * @return a {@link edu.cmu.tetrad.data.DataSet} object
     */
    public DataSet simulateDataFisher(int sampleSize, int intervalBetweenShocks,
                                      double epsilon) {
        boolean printedUndefined = false;
        boolean printedInfinite = false;

        if (intervalBetweenShocks < 1) throw new IllegalArgumentException(
                "Interval between shocks must be >= 1: " + intervalBetweenShocks);
        if (epsilon <= 0.0) throw new IllegalArgumentException(
                "Epsilon must be > 0: " + epsilon);

        Map<String, Double> variableValues = new HashMap<>();

        Context context = term -> {
            Double value = GeneralizedSemIm.this.parameterValues.get(term);

            if (value != null) {
                return value;
            }

            value = variableValues.get(term);

            if (value != null) {
                return value;
            }

            throw new IllegalArgumentException("No value recorded for '" + term + "'");
        };

        List<Node> variableNodes = this.pm.getVariableNodes();

        double[] t1 = new double[variableNodes.size()];
        double[] t2 = new double[variableNodes.size()];
        double[] shocks = new double[variableNodes.size()];
        double[][] all = new double[variableNodes.size()][sampleSize];

        // Do the simulation.

        for (int row = 0; row < sampleSize; row++) {
            for (int j = 0; j < t1.length; j++) {
                Node error = this.pm.getErrorNode(variableNodes.get(j));

                if (error == null) {
                    throw new NullPointerException();
                }

                Expression expression = this.pm.getNodeExpression(error);
                double value = expression.evaluate(context);

                if (Double.isNaN(value)) {
                    throw new IllegalArgumentException("Undefined value for expression: " + expression);
                }

                variableValues.put(error.getName(), value);
                shocks[j] = value;

                if (guaranteeIid) {
                    t2[j] = shocks[j];
                } else {
                    t2[j] += shocks[j];
                }
            }

            for (int i = 0; i < intervalBetweenShocks; i++) {
                for (int j = 0; j < t1.length; j++) {
                    Node node = variableNodes.get(j);
                    Expression expression = this.pm.getNodeExpression(node);
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
                    if (FastMath.abs(t2[j] - t1[j]) > epsilon) {
                        converged = false;
                        break;
                    }
                }

                double[] t3 = t1;
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

        List<Node> continuousVars = new ArrayList<>();

        for (Node node : variableNodes) {
            ContinuousVariable var = new ContinuousVariable(node.getName());
            var.setNodeType(node.getNodeType());
            continuousVars.add(var);
        }

        BoxDataSet boxDataSet = new BoxDataSet(new VerticalDoubleDataBox(all), continuousVars);
        return DataTransforms.restrictToMeasured(boxDataSet);
    }


    /**
     * <p>simulateOneRecord.</p>
     *
     * @param e a {@link edu.cmu.tetrad.util.Vector} object
     * @return a {@link edu.cmu.tetrad.util.Vector} object
     */
    public Vector simulateOneRecord(Vector e) {
        Map<String, Double> variableValues = new HashMap<>();

        List<Node> variableNodes = this.pm.getVariableNodes();

        Context context = term -> {
            Double value = GeneralizedSemIm.this.parameterValues.get(term);

            if (value != null) {
                return value;
            }

            value = variableValues.get(term);

            if (value != null) {
                return value;
            }

            throw new IllegalArgumentException("No value recorded for '" + term + "'");
        };

        // Take random draws from error distributions.
        for (int i = 0; i < variableNodes.size(); i++) {
            Node error = this.pm.getErrorNode(variableNodes.get(i));

            if (error == null) {
                throw new NullPointerException();
            }

            variableValues.put(error.getName(), e.get(i));
        }

        // Set the variable nodes to zero.
        for (Node variable : variableNodes) {
            variableValues.put(variable.getName(), 0.0);// RandomUtil.getInstance().nextUniform(-5, 5));
        }

        // Repeatedly update variable values until one of them hits infinity or negative infinity or
        // convergence within delta.

        final double delta = 1e-6;
        int count = -1;

        while (++count < 10000) {
            double[] values = new double[variableNodes.size()];

            for (int i = 0; i < values.length; i++) {
                Node node = variableNodes.get(i);
                Expression expression = this.pm.getNodeExpression(node);
                double value = expression.evaluate(context);
                values[i] = value;
            }

            boolean allInRange = true;

            for (int i = 0; i < values.length; i++) {
                Node node = variableNodes.get(i);

                if (!(FastMath.abs(variableValues.get(node.getName()) - values[i]) < delta)) {
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

        Vector _case = new Vector(e.size());

        for (int i = 0; i < variableNodes.size(); i++) {
            double value = variableValues.get(variableNodes.get(i).getName());
            _case.set(i, value);
        }

        return _case;
    }

    /**
     * Simulates data for a given number of steps.
     *
     * @param sampleSize      The number of samples to generate.
     * @param latentDataSaved Flag indicating whether to save latent data.
     * @return The generated dataset.
     * @throws NullPointerException     If an error node is null.
     * @throws IllegalArgumentException If an expression evaluates to NaN or is undefined.
     */
    public DataSet simulateDataNSteps(int sampleSize, boolean latentDataSaved) {
        Map<String, Double> variableValues = new HashMap<>();

        List<Node> continuousVariables = new LinkedList<>();
        List<Node> variableNodes = this.pm.getVariableNodes();

        // Work with a copy of the variables, because their type can be set externally.
        for (Node node : variableNodes) {
            ContinuousVariable var = new ContinuousVariable(node.getName());
            var.setNodeType(node.getNodeType());

            if (var.getNodeType() != NodeType.ERROR) {
                continuousVariables.add(var);
            }
        }

        DataSet fullDataSet = new BoxDataSet(new VerticalDoubleDataBox(sampleSize, continuousVariables.size()), continuousVariables);

        Context context = term -> {
            Double value = GeneralizedSemIm.this.parameterValues.get(term);

            if (value != null) {
                return value;
            }

            value = variableValues.get(term);

            if (value != null) {
                return value;
            }

            throw new IllegalArgumentException("No value recorded for '" + term + "'");
        };

        // Do the simulation.
        ROW:
        for (int row = 0; row < sampleSize; row++) {

            // Take random draws from error distributions.
            for (Node variable : variableNodes) {
                Node error = this.pm.getErrorNode(variable);

                if (error == null) {
                    throw new NullPointerException();
                }

                Expression expression = this.pm.getNodeExpression(error);
                double value = expression.evaluate(context);

                if (Double.isNaN(value)) {
                    throw new IllegalArgumentException("Undefined value for expression: " + expression);
                }

                variableValues.put(error.getName(), value);
            }

            // Set the variable nodes to zero.
            for (Node variable : variableNodes) {
                variableValues.put(variable.getName(), 0.0);// RandomUtil.getInstance().nextUniform(-5, 5));
            }

            // Repeatedly update variable values until one of them hits infinity or negative infinity or
            // convergence within delta.

            for (int m = 0; m < 1; m++) {
                double[] values = new double[variableNodes.size()];

                for (int i = 0; i < values.length; i++) {
                    Node node = variableNodes.get(i);
                    Expression expression = this.pm.getNodeExpression(node);
                    double value = expression.evaluate(context);

                    if (Double.isNaN(value)) {
                        throw new IllegalArgumentException("Undefined value for expression: " + expression);
                    }

                    values[i] = value;
                }

                for (double value : values) {
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
                double value = variableValues.get(variableNodes.get(i).getName());
                fullDataSet.setDouble(row, i, value);
            }
        }

        if (latentDataSaved) {
            return fullDataSet;
        } else {
            return DataTransforms.restrictToMeasured(fullDataSet);
        }

    }


    /**
     * <p>getSemPm.</p>
     *
     * @return a {@link edu.cmu.tetrad.sem.GeneralizedSemPm} object
     */
    public GeneralizedSemPm getSemPm() {
        return new GeneralizedSemPm(this.pm);
    }

    /**
     * <p>setSubstitutions.</p>
     *
     * @param parameterValues a {@link java.util.Map} object
     */
    public void setSubstitutions(Map<String, Double> parameterValues) {
        for (String parameter : parameterValues.keySet()) {
            if (this.parameterValues.containsKey(parameter)) {
                this.parameterValues.put(parameter, parameterValues.get(parameter));
            }
        }
    }

    /**
     * <p>Setter for the field <code>guaranteeIid</code>.</p>
     *
     * @param guaranteeIid a boolean
     */
    public void setGuaranteeIid(boolean guaranteeIid) {
        this.guaranteeIid = guaranteeIid;
    }
}



