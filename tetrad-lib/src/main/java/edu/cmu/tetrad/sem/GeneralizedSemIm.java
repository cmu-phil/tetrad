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
import edu.cmu.tetrad.data.ColtDataSet;
import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataUtils;
import edu.cmu.tetrad.graph.*;
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

/**
 * Represents a generalized SEM instantiated model. The parameteric form of this
 * model allows arbitrary equations for variables. This instantiated model
 * gives values for all of the parameters of the parameterized model.
 *
 * @author Joseph Ramsey
 */
public class GeneralizedSemIm implements IM, Simulator, TetradSerializable {
    static final long serialVersionUID = 23L;

    /**
     * The wrapped PM, that holds all of the expressions and structure for the model.
     */
    private GeneralizedSemPm pm;

    /**
     * A map from freeParameters names to their values--these form the context for evaluating expressions.
     * Variables do not appear in this list. All freeParameters are double-valued.
     */
    private Map<String, Double> parameterValues;

    /**
     * True iff only positive data should be simulated.
     */
    private boolean simulatePositiveDataOnly = false;

    /**
     * The coefficient of a (linear) self-loop for each variable, or NaN if there is none.
     */
    private double selfLoopCoef = Double.NaN;


    /**
     * Constructs a new GeneralizedSemIm from the given GeneralizedSemPm by picking values for each of
     * the freeParameters from their initial distributions.
     *
     * @param pm the GeneralizedSemPm. Includes all of the equations and distributions of the model.
     */
    public GeneralizedSemIm(GeneralizedSemPm pm) {
        this.pm = new GeneralizedSemPm(pm);

        this.parameterValues = new HashMap<>();

        Set<String> parameters = pm.getParameters();

        for (String parameter : parameters) {
            Expression expression = pm.getParameterExpression(parameter);

            Context context = new Context() {
                public Double getValue(String var) {
                    return parameterValues.get(var);
                }
            };

            double initialValue = expression.evaluate(context);
            parameterValues.put(parameter, initialValue);
        }
    }

    public GeneralizedSemIm(GeneralizedSemPm pm, SemIm semIm) {
        this(pm);
        SemPm semPm = semIm.getSemPm();

        Set<String> parameters = pm.getParameters();

        // If there are any missing freeParameters, just ignore the sem IM.
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
        return new GeneralizedSemPm(pm);
    }

    /**
     * @param parameter The parameter whose values is to be set.
     * @param value     The double value that <code>param</code> is to be set to.
     */
    public void setParameterValue(String parameter, double value) {
        if (parameter == null) {
            throw new NullPointerException("Parameter not specified.");
        }

        if (!(parameterValues.keySet().contains(parameter))) {
            throw new IllegalArgumentException("Not a parameter in this model: " + parameter);
        }

        parameterValues.put(parameter, value);
    }

    /**
     * @param parameter The parameter whose value is to be retrieved.
     * @return The retrieved value.
     */
    public double getParameterValue(String parameter) {
        if (parameter == null) {
            throw new NullPointerException("Parameter not specified.");
        }

        if (!parameterValues.keySet().contains(parameter)) {
            throw new IllegalArgumentException("Not a parameter in this model: " + parameter);
        }

        return parameterValues.get(parameter);
    }

    /**
     * @return the user's String formula with numbers substituted for freeParameters, where substitutions exist.
     */
    public String getNodeSubstitutedString(Node node) {
        NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();
        String expressionString = pm.getNodeExpressionString(node);

        if (expressionString == null) return null;

        ExpressionLexer lexer = new ExpressionLexer(expressionString);
        StringBuilder buf = new StringBuilder();
        Token token;

        while ((token = lexer.nextTokenIncludingWhitespace()) != Token.EOF) {
            String tokenString = lexer.getTokenString();

            if (token == Token.PARAMETER) {
                Double value = parameterValues.get(tokenString);

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
    public String getNodeSubstitutedString(Node node, Map<String, Double> substitutedValues) {
        if (node == null) {
            throw new NullPointerException();
        }

        if (substitutedValues == null) {
            throw new NullPointerException();
        }

        NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();
        String expressionString = pm.getNodeExpressionString(node);

        ExpressionLexer lexer = new ExpressionLexer(expressionString);
        StringBuilder buf = new StringBuilder();
        Token token;

        while ((token = lexer.nextTokenIncludingWhitespace()) != Token.EOF) {
            String tokenString = lexer.getTokenString();

            if (token == Token.PARAMETER) {
                Double value = substitutedValues.get(tokenString);

                if (value == null) {
                    value = parameterValues.get(tokenString);
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
        List<String> parameters = new ArrayList<>(pm.getParameters());
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

    public DataSet simulateData(int sampleSize, boolean latentDataSaved) {
        long seed = RandomUtil.getInstance().getSeed();
        TetradLogger.getInstance().log("info", "Seed = " + seed);

        if (pm.getGraph().isTimeLagModel()) {
            return simulateTimeSeries(sampleSize);
        }

//        return simulateDataRecursive(sampleSize, latentDataSaved);
//        return simulateDataMinimizeSurface(sampleSize, latentDataSaved);
        return simulateDataAvoidInfinity(sampleSize, latentDataSaved);
//        return simulateDataNSteps(sampleSize, latentDataSaved);
    }

    @Override
    public DataSet simulateData(int sampleSize, long seed, boolean latentDataSaved) {
        RandomUtil random = RandomUtil.getInstance();
        long _seed = random.getSeed();
        random.setSeed(seed);
        DataSet dataSet = simulateData(sampleSize, latentDataSaved);
        random.revertSeed(_seed);
        return dataSet;
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

        for (Node node : new ArrayList<>(lag0Nodes)) {
            if (node.getNodeType() == NodeType.ERROR) {
                lag0Nodes.remove(node);
            }
        }

        DataSet fullData = new ColtDataSet(sampleSize, variables);

        Map<Node, Integer> nodeIndices = new HashMap<>();

        for (int i = 0; i < lag0Nodes.size(); i++) {
            nodeIndices.put(lag0Nodes.get(i), i);
        }

        Graph contemporaneousDag = timeLagGraph.subgraph(timeLagGraph.getLag0Nodes());

        List<Node> tierOrdering = contemporaneousDag.getCausalOrdering();

        for (Node node : new ArrayList<>(tierOrdering)) {
            if (node.getNodeType() == NodeType.ERROR) {
                tierOrdering.remove(node);
            }
        }

        final Map<String, Double> variableValues = new HashMap<>();

        Context context = new Context() {
            public Double getValue(String term) {
                Double value = parameterValues.get(term);

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
            for (Node node : tierOrdering) {
                Expression expression = pm.getNodeExpression(node);
                double value = expression.evaluate(context);

                if (isSimulatePositiveDataOnly() && value < 0) {
                    currentStep--;
                    continue ROW;
                }

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
     * This simulates data by picking random values for the exogenous terms and
     * percolating this information down through the SEM, assuming it is
     * acyclic. Fast for large simulations but hangs for cyclic models.
     *
     * @param sampleSize > 0.
     * @return the simulated data set.
     */
    public DataSet simulateDataRecursive(int sampleSize, boolean latentDataSaved) {
        final Map<String, Double> variableValues = new HashMap<>();

        Context context = new Context() {
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
        };

        List<Node> variables = pm.getNodes();
        List<Node> continuousVariables = new LinkedList<>();
        List<Node> nonErrorVariables = pm.getVariableNodes();

        // Work with a copy of the variables, because their type can be set externally.
        for (Node node : nonErrorVariables) {
            ContinuousVariable var = new ContinuousVariable(node.getName());
            var.setNodeType(node.getNodeType());

            if (var.getNodeType() != NodeType.ERROR) {
                continuousVariables.add(var);
            }
        }

        DataSet fullDataSet = new ColtDataSet(sampleSize, continuousVariables);

        // Create some index arrays to hopefully speed up the simulation.
        SemGraph graph = pm.getGraph();
        List<Node> tierOrdering = graph.getFullTierOrdering();

        int[] tierIndices = new int[variables.size()];

        for (int i = 0; i < tierIndices.length; i++) {
            tierIndices[i] = nonErrorVariables.indexOf(tierOrdering.get(i));
        }

        int[][] _parents = new int[variables.size()][];

        for (int i = 0; i < variables.size(); i++) {
            Node node = variables.get(i);
            List<Node> parents = graph.getParents(node);

            _parents[i] = new int[parents.size()];

            for (int j = 0; j < parents.size(); j++) {
                Node _parent = parents.get(j);
                _parents[i][j] = variables.indexOf(_parent);
            }
        }

        // Do the simulation.
        for (int row = 0; row < sampleSize; row++) {
            variableValues.clear();

            for (int tier = 0; tier < tierOrdering.size(); tier++) {
                Node node = tierOrdering.get(tier);
                Expression expression = pm.getNodeExpression(node);
                double value = expression.evaluate(context);
                variableValues.put(node.getName(), value);

                int col = tierIndices[tier];

                if (col == -1) {
                    continue;
                }

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
        }

        if (latentDataSaved) {
            return fullDataSet;
        } else {
            return DataUtils.restrictToMeasured(fullDataSet);
        }
    }


    public DataSet simulateDataMinimizeSurface(int sampleSize, boolean latentDataSaved) {
        final Map<String, Double> variableValues = new HashMap<>();

        List<Node> continuousVariables = new LinkedList<>();
        final List<Node> variableNodes = pm.getVariableNodes();

        // Work with a copy of the variables, because their type can be set externally.
        for (Node node : variableNodes) {
            ContinuousVariable var = new ContinuousVariable(node.getName());
            var.setNodeType(node.getNodeType());

            if (var.getNodeType() != NodeType.ERROR) {
                continuousVariables.add(var);
            }
        }

        DataSet fullDataSet = new ColtDataSet(sampleSize, continuousVariables);

        final Context context = new Context() {
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
        };

        final double[] _metric = new double[1];

        MultivariateFunction function = new MultivariateFunction() {
            double metric;

            public double value(double[] doubles) {
                for (int i = 0; i < variableNodes.size(); i++) {
                    variableValues.put(variableNodes.get(i).getName(), doubles[i]);
                }

                double[] image = new double[doubles.length];

                for (int i = 0; i < variableNodes.size(); i++) {
                    Node node = variableNodes.get(i);
                    Expression expression = pm.getNodeExpression(node);
                    image[i] = expression.evaluate(context);

                    if (Double.isNaN(image[i])) {
                        throw new IllegalArgumentException("Undefined value for expression " + expression);
                    }
                }

                metric = 0.0;

                for (int i = 0; i < variableNodes.size(); i++) {
                    double diff = doubles[i] - image[i];
                    metric += diff * diff;
                }

                for (int i = 0; i < variableNodes.size(); i++) {
                    variableValues.put(variableNodes.get(i).getName(), image[i]);
                }

                _metric[0] = metric;

                return metric;
            }
        };

        MultivariateOptimizer search = new PowellOptimizer(1e-7, 1e-7);

        // Do the simulation.
        ROW:
        for (int row = 0; row < sampleSize; row++) {

            // Take random draws from error distributions.
            for (Node variable : variableNodes) {
                Node error = pm.getErrorNode(variable);

                if (error == null) {
                    throw new NullPointerException();
                }

                Expression expression = pm.getNodeExpression(error);
                double value = expression.evaluate(context);

                if (Double.isNaN(value)) {
                    throw new IllegalArgumentException("Undefined value for expression: " + expression);
                }

                variableValues.put(error.getName(), value);
            }

            for (Node variable : variableNodes) {
                variableValues.put(variable.getName(), 0.0);// RandomUtil.getInstance().nextUniform(-5, 5));
            }

            while (true) {

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
                    if (isSimulatePositiveDataOnly() && values[i] < 0) {
                        row--;
                        continue ROW;
                    }

                    if (!Double.isNaN(selfLoopCoef) && row > 0) {
                        values[i] += selfLoopCoef * fullDataSet.getDouble(row - 1, i);
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

    public DataSet simulateDataAvoidInfinity(int sampleSize, boolean latentDataSaved) {
        final Map<String, Double> variableValues = new HashMap<>();

        List<Node> continuousVariables = new LinkedList<>();
        final List<Node> variableNodes = pm.getVariableNodes();

        // Work with a copy of the variables, because their type can be set externally.
        for (Node node : variableNodes) {
            ContinuousVariable var = new ContinuousVariable(node.getName());
            var.setNodeType(node.getNodeType());

            if (var.getNodeType() != NodeType.ERROR) {
                continuousVariables.add(var);
            }
        }

        DataSet fullDataSet = new ColtDataSet(sampleSize, continuousVariables);

        final Context context = new Context() {
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
        };

        boolean allInRange = true;

        // Do the simulation.
        ROW:
        for (int row = 0; row < sampleSize; row++) {

            // Take random draws from error distributions.
            for (Node variable : variableNodes) {
                Node error = pm.getErrorNode(variable);

                if (error == null) {
                    throw new NullPointerException();
                }

                Expression expression = pm.getNodeExpression(error);
                double value;

                value = expression.evaluate(context);

                if (Double.isNaN(value)) {
                    throw new IllegalArgumentException("Undefined value for expression: " + expression);
                }

                variableValues.put(error.getName(), value);
            }

            // Set the variable nodes to zero.
            for (Node variable : variableNodes) {
                Node error = pm.getErrorNode(variable);

                Expression expression = pm.getNodeExpression(error);
                double value = expression.evaluate(context);

                if (Double.isNaN(value)) {
                    throw new IllegalArgumentException("Undefined value for expression: " + expression);
                }

                variableValues.put(variable.getName(), 0.0);//value); //0.0; //RandomUtil.getInstance().nextUniform(-1, 1));
            }

            // Repeatedly update variable values until one of them hits infinity or negative infinity or
            // convergence within delta.

            double delta = 1e-10;
            int count = -1;

            while (++count < 5000) {
                double[] values = new double[variableNodes.size()];

                for (int i = 0; i < values.length; i++) {
                    Node node = variableNodes.get(i);
                    Expression expression = pm.getNodeExpression(node);
                    double value = expression.evaluate(context);
                    values[i] = value;
                }

                allInRange = true;

                for (int i = 0; i < values.length; i++) {
                    Node node = variableNodes.get(i);

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

                if (!Double.isNaN(selfLoopCoef) && row > 0) {
                    value += selfLoopCoef * fullDataSet.getDouble(row - 1, i);
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

    public TetradVector simulateOneRecord(TetradVector e) {
        final Map<String, Double> variableValues = new HashMap<>();

        final List<Node> variableNodes = pm.getVariableNodes();

        final Context context = new Context() {
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
        };

        // Take random draws from error distributions.
        for (int i = 0; i < variableNodes.size(); i++) {
            Node error = pm.getErrorNode(variableNodes.get(i));

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

        double delta = 1e-6;
        int count = -1;

        while (++count < 10000) {
            double[] values = new double[variableNodes.size()];

            for (int i = 0; i < values.length; i++) {
                Node node = variableNodes.get(i);
                Expression expression = pm.getNodeExpression(node);
                double value = expression.evaluate(context);
                values[i] = value;
            }

            boolean allInRange = true;

            for (int i = 0; i < values.length; i++) {
                Node node = variableNodes.get(i);

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

        TetradVector _case = new TetradVector(e.size());

        for (int i = 0; i < variableNodes.size(); i++) {
            double value = variableValues.get(variableNodes.get(i).getName());
            _case.set(i, value);
        }

        return _case;
    }

    public DataSet simulateDataNSteps(int sampleSize, boolean latentDataSaved) {
        final Map<String, Double> variableValues = new HashMap<>();

        List<Node> continuousVariables = new LinkedList<>();
        final List<Node> variableNodes = pm.getVariableNodes();

        // Work with a copy of the variables, because their type can be set externally.
        for (Node node : variableNodes) {
            ContinuousVariable var = new ContinuousVariable(node.getName());
            var.setNodeType(node.getNodeType());

            if (var.getNodeType() != NodeType.ERROR) {
                continuousVariables.add(var);
            }
        }

        DataSet fullDataSet = new ColtDataSet(sampleSize, continuousVariables);

        final Context context = new Context() {
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
        };

        // Do the simulation.
        ROW:
        for (int row = 0; row < sampleSize; row++) {

            // Take random draws from error distributions.
            for (Node variable : variableNodes) {
                Node error = pm.getErrorNode(variable);

                if (error == null) {
                    throw new NullPointerException();
                }

                Expression expression = pm.getNodeExpression(error);
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
                    Expression expression = pm.getNodeExpression(node);
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
            return DataUtils.restrictToMeasured(fullDataSet);
        }

    }


    public GeneralizedSemPm getSemPm() {
        return new GeneralizedSemPm(pm);
    }

    public void setSubstitutions(Map<String, Double> parameterValues) {
        for (String parameter : parameterValues.keySet()) {
            if (this.parameterValues.keySet().contains(parameter)) {
                this.parameterValues.put(parameter, parameterValues.get(parameter));
            }
        }
    }

    private boolean isSimulatePositiveDataOnly() {
        return simulatePositiveDataOnly;
    }

    public void setSimulatePositiveDataOnly(boolean simulatedPositiveDataOnly) {
        this.simulatePositiveDataOnly = simulatedPositiveDataOnly;
    }
}



