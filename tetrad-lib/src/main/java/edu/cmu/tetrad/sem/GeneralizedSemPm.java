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

import edu.cmu.tetrad.calculator.expression.Expression;
import edu.cmu.tetrad.calculator.parser.ExpressionParser;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.util.Pm;
import edu.cmu.tetrad.util.TetradSerializable;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serial;
import java.text.ParseException;
import java.util.*;


/**
 * Parametric model for a Generalized SEM model. This contains all the equations of the model with parameters
 * represented symbolically (i.e., no values for parameters).
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public final class GeneralizedSemPm implements Pm, TetradSerializable {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The structural model graph that this sem parametric model is based on.
     *
     * @serial Cannot be null.
     */
    private final SemGraph graph;

    /**
     * The list of all nodes (unmodifiable).
     *
     * @serial Cannot be null.
     */
    private final List<Node> nodes;

    /**
     * The latent and measured nodes.
     */
    private final List<Node> variableNodes;

    /**
     * The measured nodes.
     */
    private final List<Node> measuredNodes;

    /**
     * The error nodes.
     */
    private final List<Node> errorNodes;

    /**
     * The freeParameters in the model, mapped to the nodes that they are associated with. Each parameter may be
     * associated with more than one node. When freeParameters are removed from an equation or error distribution, the
     * associated nodes should be removed from the relevant set in this map, and if the set is empty, the parameter
     * should be removed from the map. Also, before adding a parameter to this map, it must be checked whether a
     * parameter by the same name already exists. If one such parameter by the same name already exists, that one should
     * be used instead of the new one. This is needed both to avoid confusion and to allow freeParameters to be reused
     * in different parts of the interface, creating equality constraints.
     *
     * @serial Cannot be null.
     */
    private final Map<String, Set<Node>> referencedParameters;

    /**
     * The nodes of the model, variable nodes or error nodes, mapped to the other nodes that they are associated with.
     */
    private final Map<Node, Set<Node>> referencedNodes;

    /**
     * The map from variable nodes to equations.
     */
    private final Map<Node, Expression> nodeExpressions;

    /**
     * The String representations of equations that were set.
     */
    private final Map<Node, String> nodeExpressionStrings;

    /**
     * Distributions from which initial values for freeParameters are drawn.
     */
    private final Map<String, Expression> parameterExpressions;

    /**
     * String representations of initial parameter distributions. A map from parameter names to expression strings.
     */
    private final Map<String, String> parameterExpressionStrings;
    /**
     * String representations of initial parameter distributions. A map from parameter names to expression strings.
     */
    private final Map<String, String> parameterEstimationInitializationExpressionStrings;
    /**
     * A map from initial name strings to parameter templates.
     */
    private final Map<String, String> startsWithParametersTemplates;
    /**
     * A map from initial name strings to parameter templates.
     */
    private final Map<String, String> startsWithParametersEstimationInitializationTemplates;
    /**
     * A map from names to nodes, speedup.
     */
    private final Map<String, Node> namesToNodes = new HashMap<>();
    /**
     * A map from names to nodes, speedup.
     */
    private final Map<String, Integer> parameterSubscript = new HashMap<>();
    /**
     * Distributions from which initial values for freeParameters are drawn.
     */
    private Map<String, Expression> parameterEstimationInitializationExpressions;
    /**
     * The stored template for variables.
     */
    private String variablesTemplate = "TSUM(NEW(B)*$)";
    /**
     * The stored template for error terms.
     */
    private String errorsTemplate = "Normal(0, NEW(s))";
    /**
     * The stored template for freeParameters.
     */
    private String parametersTemplate = "U(-1.0, 1.0)";
    /**
     * The stored template for freeParameters.
     */
    private String parametersEstimationInitializationTemplate = "U(-1.0, 1.0)";

    /**
     * The list of variable names.
     */
    private List<String> variableNames;

    /**
     * Constructs a BayesPm from the given Graph, which must be convertible first into a ProtoSemGraph and then into a
     * SemGraph.
     *
     * @param graph a {@link edu.cmu.tetrad.graph.Graph} object
     */
    public GeneralizedSemPm(Graph graph) {
        this(new SemGraph(graph));
    }

    /**
     * Constructs a new SemPm from the given SemGraph.
     *
     * @param graph a {@link edu.cmu.tetrad.graph.SemGraph} object
     */
    public GeneralizedSemPm(SemGraph graph) {
        if (graph == null) {
            throw new NullPointerException("Graph must not be null.");
        }

        // Cannot afford to allow error terms on this graph to be shown or hidden from the outside; must make a
        // hidden copy of it and make sure error terms are shown.
        this.graph = new SemGraph(graph);
        this.graph.setShowErrorTerms(true);

        for (Edge edge : this.graph.getEdges()) {
            if (Edges.isBidirectedEdge(edge)) {
                throw new IllegalArgumentException("The generalized SEM PM cannot currently deal with bidirected " +
                        "edges. Sorry.");
            }
        }

        this.nodes = Collections.unmodifiableList(this.graph.getNodes());

        for (Node node : this.nodes) {
            this.namesToNodes.put(node.getName(), node);
        }

        this.variableNodes = new ArrayList<>();
        this.measuredNodes = new ArrayList<>();

        for (Node variable : this.nodes) {
            if (variable.getNodeType() == NodeType.MEASURED ||
                    variable.getNodeType() == NodeType.LATENT) {
                this.variableNodes.add(variable);
            }

            if (variable.getNodeType() == NodeType.MEASURED) {
                this.measuredNodes.add(variable);
            }
        }

        this.errorNodes = new ArrayList<>();

        for (Node variable : this.variableNodes) {
            List<Node> parents = this.graph.getParents(variable);
            boolean added = false;

            for (Node _node : parents) {
                if (_node.getNodeType() == NodeType.ERROR) {
                    this.errorNodes.add(_node);
                    added = true;
                    break;
                }
            }

            if (!added) {
                this.errorNodes.add(null);
            }
        }

        this.referencedParameters = new HashMap<>();
        this.referencedNodes = new HashMap<>();
        this.nodeExpressions = new HashMap<>();
        this.nodeExpressionStrings = new HashMap<>();
        this.parameterExpressions = new HashMap<>();
        this.parameterExpressionStrings = new HashMap<>();
        this.parameterEstimationInitializationExpressions = new HashMap<>();
        this.parameterEstimationInitializationExpressionStrings = new HashMap<>();
        this.startsWithParametersTemplates = new HashMap<>();
        this.startsWithParametersEstimationInitializationTemplates = new HashMap<>();

        this.variableNames = new ArrayList<>();
        for (Node _node : this.variableNodes) this.variableNames.add(_node.getName());

        for (Node _node : this.errorNodes) {
            if (_node != null) {
                this.variableNames.add(_node.getName());
            }
        }

        try {
            List<Node> variableNodes = getVariableNodes();

            for (Node node : variableNodes) {
                if (!this.graph.isParameterizable(node)) continue;

                if (this.nodeExpressions.get(node) != null) {
                    continue;
                }

                String variablesTemplate = getVariablesTemplate();
                String formula = TemplateExpander.getInstance().expandTemplate(variablesTemplate, this, node);
                setNodeExpression(node, formula);
                Set<String> parameters = getReferencedParameters(node);

                String parametersTemplate = getParametersTemplate();

                for (String parameter : parameters) {
                    if (this.parameterExpressions.get(parameter) != null) {
                        if (parametersTemplate != null) {
                            setParameterExpression(parameter, parametersTemplate);
                        } else if (this.graph.isTimeLagModel()) {
                            final String expressionString = "U(-0.9, 0.9)";
                            setParameterExpression(parameter, expressionString);
                            setParametersTemplate(expressionString);
                        } else {
                            final String expressionString = "U(-1.0, 1.0)";
                            setParameterExpression(parameter, expressionString);
                            setParametersTemplate(expressionString);
                        }
                    }
                }

                for (String parameter : parameters) {
                    if (this.parameterEstimationInitializationExpressions.get(parameter) == null) {
                        if (parametersTemplate != null) {
                            setParameterEstimationInitializationExpression(parameter, parametersTemplate);
                        } else if (this.graph.isTimeLagModel()) {
                            final String expressionString = "U(-0.9, 0.9)";
                            setParameterEstimationInitializationExpression(parameter, expressionString);
                        } else {
                            final String expressionString = "U(-1.0, 1.0)";
                            setParameterEstimationInitializationExpression(parameter, expressionString);
                        }
                    }

                    setStartsWithParametersTemplate("s", "U(-1.0, 1.0)");
                    setStartsWithParametersEstimationInitializaationTemplate("s", "U(-1.5, 1.5)");
                }
            }

            for (Node node : this.errorNodes) {
                if (node == null) continue;

                String template = getErrorsTemplate();
                String formula = TemplateExpander.getInstance().expandTemplate(template, this, node);
                setNodeExpression(node, formula);
                Set<String> parameters = getReferencedParameters(node);

                setStartsWithParametersTemplate("s", "U(1, 3)");
                setStartsWithParametersEstimationInitializaationTemplate("s", "U(1, 3)");

                for (String parameter : parameters) {
                    setParameterExpression(parameter, "U(1, 3)");
                }
            }
        } catch (ParseException e) {
            throw new IllegalStateException("Parse error in constructing initial model.", e);
        }
    }

    /**
     * Constructs a new GeneralizedSemPm object based on the given SemPm object.
     *
     * @param semPm the SemPm object used to construct the GeneralizedSemPm
     * @throws IllegalStateException if there is a parse error in constructing the initial model
     */
    public GeneralizedSemPm(SemPm semPm) {
        this(semPm.getGraph());

        // Write down equations.
        try {
            List<Node> variableNodes = getVariableNodes();

            for (int i = 0; i < variableNodes.size(); i++) {
                Node node = variableNodes.get(i);
                List<Node> parents = new ArrayList<>(getVariableParents(node));

                StringBuilder buf = new StringBuilder();

                for (int j = 0; j < parents.size(); j++) {
                    if (!(variableNodes.contains(parents.get(j)))) {
                        continue;
                    }

                    Node parent = parents.get(j);

                    Parameter _parameter = semPm.getParameter(parent, node);
                    String parameter = _parameter.getName();
                    Set<Node> nodes = new HashSet<>();
                    nodes.add(node);

                    this.referencedParameters.put(parameter, nodes);

                    buf.append(parameter);
                    buf.append("*");
                    buf.append(parents.get(j).getName());

                    setParameterExpression(parameter, "U(-1.0, 1.0)");
                    setStartsWithParametersTemplate(parameter.substring(0, 1), "U(-1.0, 1.0)");
                    setStartsWithParametersEstimationInitializaationTemplate(parameter.substring(0, 1), "U(-1.5, 1.5)");

                    if (j < parents.size() - 1) {
                        buf.append(" + ");
                    }
                }

                if (!buf.toString().trim().isEmpty()) {
                    buf.append(" + ");
                }

                buf.append(this.errorNodes.get(i));
                setNodeExpression(node, buf.toString());
            }

            for (Node node : variableNodes) {
                Parameter _parameter = semPm.getParameter(node, node);
                String parameter = _parameter.getName();

                String distributionFormula = "N(0," + parameter + ")";
                setNodeExpression(getErrorNode(node), distributionFormula);
                setParameterExpression(parameter, "U(0, 1)");
                setStartsWithParametersTemplate(parameter.substring(0, 1), "U(0, 1)");
                setStartsWithParametersEstimationInitializaationTemplate(parameter.substring(0, 1), "U(0, 1)");
            }

            this.variableNames = new ArrayList<>();
            for (Node _node : variableNodes) this.variableNames.add(_node.getName());
            for (Node _node : this.errorNodes) this.variableNames.add(_node.getName());

        } catch (ParseException e) {
            throw new IllegalStateException("Parse error in constructing initial model.", e);
        }
    }

    /**
     * Initializes a new instance of the GeneralizedSemPm class by copying the properties of the provided GeneralizedSemPm object.
     *
     * @param semPm The GeneralizedSemPm object to copy.
     */
    public GeneralizedSemPm(GeneralizedSemPm semPm) {
        this.graph = new SemGraph(semPm.graph);
        this.nodes = new ArrayList<>(semPm.nodes);
        this.variableNodes = new ArrayList<>(semPm.variableNodes);
        this.measuredNodes = new ArrayList<>(semPm.measuredNodes);
        this.errorNodes = new ArrayList<>(semPm.errorNodes);
        this.referencedParameters = new HashMap<>();
        this.referencedNodes = new HashMap<>();
        this.parameterEstimationInitializationExpressions
                = new HashMap<>(semPm.parameterEstimationInitializationExpressions);
        this.parametersEstimationInitializationTemplate = semPm.parametersEstimationInitializationTemplate;

        for (String parameter : semPm.referencedParameters.keySet()) {
            this.referencedParameters.put(parameter, new HashSet<>(semPm.referencedParameters.get(parameter)));
        }

        for (Node node : semPm.referencedNodes.keySet()) {
            this.referencedNodes.put(node, new HashSet<>(semPm.referencedNodes.get(node)));
        }

        this.nodeExpressions = new HashMap<>(semPm.nodeExpressions);
        this.nodeExpressionStrings = new HashMap<>(semPm.nodeExpressionStrings);
        this.parameterExpressions = new HashMap<>(semPm.parameterExpressions);
        this.parameterExpressionStrings = new HashMap<>(semPm.parameterExpressionStrings);
        this.parameterEstimationInitializationExpressions = new HashMap<>(semPm.parameterEstimationInitializationExpressions);
        this.parameterEstimationInitializationExpressionStrings = new HashMap<>(semPm.parameterEstimationInitializationExpressionStrings);
        this.variablesTemplate = semPm.variablesTemplate;
        this.errorsTemplate = semPm.errorsTemplate;
        this.parametersTemplate = semPm.parametersTemplate;
        this.variableNames = semPm.variableNames == null ? new ArrayList<>() : new ArrayList<>(semPm.variableNames);
        this.startsWithParametersTemplates = new HashMap<>(semPm.startsWithParametersTemplates);
        this.startsWithParametersEstimationInitializationTemplates
                = new HashMap<>(semPm.startsWithParametersEstimationInitializationTemplates);

    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @return a {@link edu.cmu.tetrad.sem.GeneralizedSemPm} object
     */
    public static GeneralizedSemPm serializableInstance() {
        Dag dag = new Dag();
        GraphNode node1 = new GraphNode("X");
        dag.addNode(node1);
        return new GeneralizedSemPm(Dag.serializableInstance());
    }

    /**
     * Retrieves the names of the parameters required for a certain operation.
     *
     * @return a List<String> containing the names of the parameters
     */
    public static List<String> getParameterNames() {
        List<String> parameters = new ArrayList<>();
        parameters.add("generalSemFunctionTemplateMeasured");
        parameters.add("generalSemFunctionTemplateLatent");
        parameters.add("generalSemErrorTemplate");
        parameters.add("generalSemParameterTemplate");
        return parameters;
    }

    /**
     * <p>getNodeExpression.</p>
     *
     * @param node a {@link edu.cmu.tetrad.graph.Node} object
     * @return a {@link edu.cmu.tetrad.calculator.expression.Expression} object
     */
    public Expression getNodeExpression(Node node) {
        return this.nodeExpressions.get(node);
    }

    /**
     * <p>getNodeExpressionString.</p>
     *
     * @param node a {@link edu.cmu.tetrad.graph.Node} object
     * @return a {@link java.lang.String} object
     */
    public String getNodeExpressionString(Node node) {
        return this.nodeExpressionStrings.get(node);
    }

    /**
     * Sets the expression for a given node.
     *
     * @param node             the node for which to set the expression
     * @param expressionString the expression string to set
     * @throws ParseException       if the expression string cannot be parsed
     * @throws NullPointerException if node is null or expressionString is null
     */
    public void setNodeExpression(Node node, String expressionString) throws ParseException {
        if (node == null) {
            throw new NullPointerException("Node was null.");
        }

        if (expressionString == null) {
            throw new NullPointerException("Expression string was null.");
        }

        // Parse the expression. This could throw a ParseException, but that exception needs to hand up the
        // chain, because the interface will need it.
        ExpressionParser parser = new ExpressionParser();
        Expression expression = parser.parseExpression(expressionString);
        List<String> parameterNames = parser.getParameters();

        // Make a list of parent names.
        List<Node> parents = this.graph.getParents(node);
        List<String> parentNames = new LinkedList<>();

        for (Node parent : parents) {
            parentNames.add(parent.getName());
        }

        // Make a list of parameter names, by removing from the parser's list of freeParameters any that correspond
        // to parent variables. If there are any variable names (including error terms) that are not among the list of
        // parents, that's a time to throw an exception. We must respect the graph! (We will not complain if any parents
        // are missing.)
        parameterNames.removeAll(this.variableNames);

        for (Node variable : this.nodes) {
            //                throw new IllegalArgumentException("The list of parameter names may not include variables: " + variable.getNode());
            parameterNames.remove(variable.getName());
        }

        // Remove old parameter references.
        List<String> parametersToRemove = new LinkedList<>();

        for (String parameter : this.referencedParameters.keySet()) {
            Set<Node> nodes = this.referencedParameters.get(parameter);

            nodes.remove(node);

            if (nodes.isEmpty()) {
                parametersToRemove.add(parameter);
            }
        }

        for (String parameter : parametersToRemove) {
            this.referencedParameters.remove(parameter);
            this.parameterExpressions.remove(parameter);
            this.parameterExpressionStrings.remove(parameter);
            this.parameterEstimationInitializationExpressions.remove(parameter);
            this.parameterEstimationInitializationExpressionStrings.remove(parameter);
        }

        // Add new parameter references.
        for (String parameter : parameterNames) {
            this.referencedParameters.computeIfAbsent(parameter, k -> new HashSet<>());

            Set<Node> nodes = this.referencedParameters.get(parameter);
            nodes.add(node);

            setSuitableParameterDistribution(parameter);
        }

        // Remove old node references.
        List<Node> nodesToRemove = new LinkedList<>();

        for (Node _node : this.referencedNodes.keySet()) {
            Set<Node> nodes = this.referencedNodes.get(_node);

            nodes.remove(node);

            if (nodes.isEmpty()) {
                nodesToRemove.add(_node);
            }
        }

        for (Node _node : nodesToRemove) {
            this.referencedNodes.remove(_node);
        }

        // Add new freeParameters.
        for (String variableString : this.variableNames) {
            Node _node = getNode(variableString);

            this.referencedNodes.computeIfAbsent(_node, k -> new HashSet<>());

            for (String s : parentNames) {
                if (s.equals(variableString)) {
                    Set<Node> nodes = this.referencedNodes.get(_node);
                    nodes.add(node);
                }
            }
        }

        // Finally, save the parsed expression and the original string that the user entered. No need to annoy
        // the user by changing spacing.
        this.nodeExpressions.put(node, expression);
        this.nodeExpressionStrings.put(node, expressionString);
    }

    /**
     * Sets the suitable parameter distribution based on the provided parameter.
     *
     * @param parameter the parameter to set the suitable distribution for
     * @throws ParseException if there is an error parsing the parameter
     */
    private void setSuitableParameterDistribution(String parameter) throws ParseException {
        boolean found = false;

        for (String prefix : this.startsWithParametersTemplates.keySet()) {
            if (parameter.startsWith(prefix)) {
                if (this.parameterExpressions.get(parameter) == null) {
                    setParameterExpression(parameter, this.startsWithParametersTemplates.get(prefix));
                }
                if (this.parameterEstimationInitializationExpressions.get(parameter) == null) {
                    setParameterEstimationInitializationExpression(parameter, this.startsWithParametersTemplates.get(prefix));
                }
                found = true;
            }
        }

        if (!found) {
            if (this.parameterExpressions.get(parameter) == null) {
                setParameterExpression(parameter, getParametersTemplate());
            }
            if (this.parameterEstimationInitializationExpressions.get(parameter) == null) {
                setParameterEstimationInitializationExpression(parameter, getParametersTemplate());
            }
        }
    }

    /**
     * Sets the expression which should be evaluated when calculating new values for the given parameter. These values
     * are used to initialize the freeParameters.
     *
     * @param parameter        The parameter whose initial value needs to be computed.
     * @param expressionString The formula for picking initial values.
     * @throws java.text.ParseException If the formula cannot be parsed or contains variable names.
     */
    public void setParameterExpression(String parameter, String expressionString)
            throws ParseException {
        if (parameter == null) {
            throw new NullPointerException("Parameter was null.");
        }

        if (expressionString == null) {
            throw new NullPointerException("Expression string was null.");
        }

        // Parse the expression. This could throw a ParseException, but that exception needs to hand up the
        // chain, because the interface will need it.
        ExpressionParser parser = new ExpressionParser();
        Expression expression = parser.parseExpression(expressionString);
        List<String> parameterNames = parser.getParameters();

        if (!parameterNames.isEmpty()) {
            throw new IllegalArgumentException("Initial distribution for a parameter may not " +
                    "contain parameters: " + expressionString);
        }

        this.parameterExpressions.put(parameter, expression);
        this.parameterExpressionStrings.put(parameter, expressionString);
    }

    /**
     * Sets the parameter estimation initialization expression for the given parameter.
     *
     * @param parameter        The parameter to set the initialization expression for.
     * @param expressionString The expression string to be parsed and stored.
     * @throws NullPointerException     If either parameter or expressionString is null.
     * @throws ParseException           If the expressionString is not valid and cannot be parsed.
     * @throws IllegalArgumentException If the expressionString contains parameters.
     */
    public void setParameterEstimationInitializationExpression(String parameter, String expressionString)
            throws ParseException {
        if (parameter == null) {
            throw new NullPointerException("Parameter was null.");
        }

        if (expressionString == null) {
            throw new NullPointerException("Expression string was null.");
        }

        // Parse the expression. This could throw a ParseException, but that exception needs to hand up the
        // chain, because the interface will need it.
        ExpressionParser parser = new ExpressionParser();
        Expression expression = parser.parseExpression(expressionString);
        List<String> parameterNames = parser.getParameters();

        if (!parameterNames.isEmpty()) {
            throw new IllegalArgumentException("Initial distribution may not " +
                    "contain parameters: " + expressionString);
        }

        this.parameterEstimationInitializationExpressions.put(parameter, expression);
        this.parameterEstimationInitializationExpressionStrings.put(parameter, expressionString);
    }

    /**
     * Sets the expression which should be evaluated when calculating new values for the given parameter. These values
     * are used to initialize the freeParameters.
     *
     * @param parameter        The parameter whose initial value needs to be computed.
     * @param expressionString The formula for picking initial values.
     * @param startsWith       a {@link java.lang.String} object
     * @throws java.text.ParseException If the formula cannot be parsed or contains variable names.
     */
    public void setParameterExpression(String startsWith, String parameter, String expressionString)
            throws ParseException {
        if (parameter == null) {
            throw new NullPointerException("Parameter was null.");
        }

        if (startsWith == null) {
            throw new NullPointerException("StartsWith expression was null.");
        }

        if (startsWith.contains(" ")) {
            throw new IllegalArgumentException("StartsWith expression contains spaces.");
        }

        if (expressionString == null) {
            throw new NullPointerException("Expression string was null.");
        }

        // Parse the expression. This could throw a ParseException, but that exception needs to hand up the
        // chain, because the interface will need it.
        ExpressionParser parser = new ExpressionParser();
        Expression expression = parser.parseExpression(expressionString);
        List<String> parameterNames = parser.getParameters();

        if (!parameterNames.isEmpty()) {
            throw new IllegalArgumentException("Initial distribution may not " +
                    "contain parameters: " + expressionString);
        }

        this.parameterExpressions.put(parameter, expression);
        this.parameterExpressionStrings.put(parameter, expressionString);
        this.startsWithParametersTemplates.put(startsWith, expressionString);
    }

    /**
     * Sets the expression which should be evaluated when calculating new values for the given parameter. These values
     * are used to initialize the freeParameters.
     *
     * @param parameter        The parameter whose initial value needs to be computed.
     * @param expressionString The formula for picking initial values.
     * @param startsWith       a {@link java.lang.String} object
     * @throws java.text.ParseException If the formula cannot be parsed or contains variable names.
     */
    public void setParameterEstimationInitializationExpression(String startsWith, String parameter, String expressionString)
            throws ParseException {
        if (parameter == null) {
            throw new NullPointerException("Parameter was null.");
        }

        if (startsWith == null) {
            throw new NullPointerException("StartsWith expression was null.");
        }

        if (startsWith.contains(" ")) {
            throw new IllegalArgumentException("StartsWith expression contains spaces.");
        }

        if (expressionString == null) {
            throw new NullPointerException("Expression string was null.");
        }

        // Parse the expression. This could throw a ParseException, but that exception needs to hand up the
        // chain, because the interface will need it.
        ExpressionParser parser = new ExpressionParser();
        Expression expression = parser.parseExpression(expressionString);
        List<String> parameterNames = parser.getParameters();

        if (!parameterNames.isEmpty()) {
            throw new IllegalArgumentException("Initial distribution may not " +
                    "contain parameters: " + expressionString);
        }

        this.parameterEstimationInitializationExpressions.put(parameter, expression);
        this.parameterEstimationInitializationExpressionStrings.put(parameter, expressionString);
        this.startsWithParametersTemplates.put(startsWith, expressionString);
    }

    /**
     * Retrieves the set of parameters.
     *
     * @return a Set of String representing the parameters.
     */
    public Set<String> getParameters() {
        return new HashSet<>(this.parameterExpressions.keySet());
    }

    /**
     * Retrieves the expression associated with the given parameter.
     *
     * @param parameter the name of the parameter
     * @return the expression associated with the given parameter, or null if not found
     */
    public Expression getParameterExpression(String parameter) {
        return this.parameterExpressions.get(parameter);
    }

    /**
     * Retrieves the initialization expression for a given parameter used in parameter estimation.
     *
     * @param parameter the parameter for which to retrieve the initialization expression
     * @return the initialization expression for the specified parameter, or null if not found
     */
    public Expression getParameterEstimationInitializationExpression(String parameter) {
        return this.parameterEstimationInitializationExpressions.get(parameter);
    }

    /**
     * Returns the expression string associated with the given parameter.
     *
     * @param parameter the parameter name
     * @return the expression string associated with the parameter
     */
    public String getParameterExpressionString(String parameter) {
        return this.parameterExpressionStrings.get(parameter);
    }

    /**
     * Returns the initialization expression string for the given parameter.
     *
     * @param parameter the parameter for which the initialization expression string is needed
     * @return the initialization expression string for the given parameter, or null if not found
     */
    public String getParameterEstimationInitializationExpressionString(String parameter) {
        return this.parameterEstimationInitializationExpressionStrings.get(parameter);
    }

    /**
     * Returns the structural model graph this SEM PM is using.
     *
     * @return a {@link edu.cmu.tetrad.graph.SemGraph} object
     */
    public SemGraph getGraph() {
        return new SemGraph(this.graph);
    }

    /**
     * Retrieves a list of nodes.
     *
     * @return A list of nodes.
     */
    public List<Node> getNodes() {
        return new ArrayList<>(this.nodes);
    }

    /**
     * Returns the list of variable nodes--that is, node that is not error nodes.
     *
     * @return a {@link java.util.List} object
     */
    public List<Node> getVariableNodes() {
        return new ArrayList<>(this.variableNodes);
    }

    /**
     * Returns a list of measured nodes.
     *
     * @return a list of measured nodes.
     */
    public List<Node> getMeasuredNodes() {
        return new ArrayList<>(this.measuredNodes);
    }

    /**
     * Returns the list of exogenous variableNodes.
     *
     * @return a {@link java.util.List} object
     */
    public List<Node> getErrorNodes() {
        return new ArrayList<>(this.errorNodes);
    }

    /**
     * Retrieves the error node associated with the given node.
     *
     * @param node The node to check for error association.
     * @return The error node associated with the given node, or null if no error node is found.
     */
    public Node getErrorNode(Node node) {
        if (this.errorNodes.contains(node)) {
            return node;
        }

        int index = this.variableNodes.indexOf(node);

        if (index == -1) {
            return null;
        }

        return this.errorNodes.get(index);
    }

    /**
     * Returns the Node with the given name.
     *
     * @param name the name of the Node to retrieve
     * @return the Node with the given name, or null if no Node exists with the specified name
     */
    public Node getNode(String name) {
        return this.namesToNodes.get(name);
    }

    /**
     * Returns a set of nodes that reference the given parameter.
     *
     * @param parameter the parameter to search for referencing nodes
     * @return a set of nodes that reference the given parameter, or an empty set if no nodes are found
     */
    public Set<Node> getReferencingNodes(String parameter) {
        Set<Node> set = this.referencedParameters.get(parameter);
        return set == null ? new HashSet<>() : new HashSet<>(set);
    }

    /**
     * Retrieves the set of referenced parameters from a given node.
     *
     * @param node The node for which to retrieve referenced parameters.
     * @return The set of referenced parameters found in the given node.
     */
    public Set<String> getReferencedParameters(Node node) {
        Set<String> parameters = new HashSet<>();

        for (String parameter : this.referencedParameters.keySet()) {
            if (this.referencedParameters.get(parameter).contains(node)) {
                parameters.add(parameter);
            }
        }

        return parameters;
    }

    /**
     * Retrieves the set of referencing nodes for a given node.
     *
     * @param node the node for which to retrieve the referencing nodes
     * @return a set of referencing nodes
     */
    public Set<Node> getReferencingNodes(Node node) {
        Set<Node> set = this.referencedNodes.get(node);
        return set == null ? new HashSet<>() : new HashSet<>(set);
    }

    /**
     * Retrieves a set of referenced nodes for the given node.
     *
     * @param node the node for which to retrieve the referenced nodes
     * @return a set of referenced nodes
     */
    public Set<Node> getReferencedNodes(Node node) {
        Set<Node> nodes = new HashSet<>();

        for (Node _node : this.referencedNodes.keySet()) {
            if (this.referencedNodes.get(_node).contains(node)) {
                nodes.add(_node);
            }
        }

        return nodes;
    }

    /**
     * Given base b (a String), returns the first name in the sequence "b1", "b2", "b3", etc., which is not already the
     * name of a node in the workbench.
     *
     * @param base the base string.
     * @return the first string in the sequence not already being used.
     */
    public String nextParameterName(String base) {
        if (this.graph.getNode(base) != null) {
            throw new IllegalArgumentException(base + " is a variable name.");
        }

        // Names should start with "1."
        int subscript = 0;

        if (this.parameterSubscript.containsKey(base)) {
            subscript = this.parameterSubscript.get(base);
        }

        subscript++;
        this.parameterSubscript.put(base, subscript);
        return base + subscript;
    }

    /**
     * Retrieves the list of parent nodes for the given node.
     *
     * @param node the specified node
     * @return the list of parent nodes
     */
    public List<Node> getParents(Node node) {
        List<Node> parents = this.graph.getParents(node);
        parents = putErrorNodesLast(parents);
        return new ArrayList<>(parents);
    }

    /**
     * Returns a relatively brief String representation of this SEM PM--the equations and distributions of the model.
     * Initial value distributions for freeParameters are not printed.
     *
     * @return a {@link java.lang.String} object
     */
    public String toString() {
        StringBuilder buf = new StringBuilder();
        buf.append("\nEquations:\n");

        for (Node node : this.variableNodes) {
            buf.append("\n").append(node).append(" = ").append(this.nodeExpressionStrings.get(node));
        }

        buf.append("\n\nErrors:\n");

        for (Node node : this.errorNodes) {
            buf.append("\n").append(node).append(" ~ ").append(this.nodeExpressionStrings.get(node));
        }

        buf.append("\n\nParameters:\n");

        for (String param : getParameters()) {
            buf.append("\n").append(param).append(" ~ ").append(getParameterExpressionString(param));
        }

        return buf.toString();
    }

    /**
     * Retrieves the parent nodes of the given node that are not of type "ERROR".
     *
     * @param node The node for which to retrieve the parent nodes
     * @return A set of nodes that are the parents of the given node and are not of type "ERROR"
     */
    private Set<Node> getVariableParents(Node node) {
        List<Node> allParents = this.graph.getParents(node);
        Set<Node> parents = new HashSet<>();

        for (Node _parent : allParents) {
            if (_parent.getNodeType() != NodeType.ERROR) {
                parents.add(_parent);
            }
        }
        return parents;
    }

    /**
     * Moves error nodes to the end of the list while preserving the order of non-error nodes.
     *
     * @param parents a list of nodes to be sorted
     * @return a new list with error nodes moved to the end
     */
    private List<Node> putErrorNodesLast(List<Node> parents) {
        List<Node> sortedNodes = new ArrayList<>();

        for (Node node : parents) {
            if (node.getNodeType() != NodeType.ERROR) {
                sortedNodes.add(node);
            }
        }

        for (Node node : parents) {
            if (node.getNodeType() == NodeType.ERROR) {
                sortedNodes.add(node);
            }
        }

        return sortedNodes;
    }

    /**
     * Adds semantic checks to the default deserialization method. This method must have the standard signature for a
     * readObject method, and the body of the method must begin with "s.defaultReadObject();". Other than that, any
     * semantic checks can be specified and do not need to stay the same from version to version. A readObject method of
     * this form may be added to any class, even if Tetrad sessions were previously saved out using a version of the
     * class that didn't include it. (That's what the "s.defaultReadObject();" is for. See J. Bloch, Effective Java, for
     * help).
     *
     * @param s The object input stream.
     * @throws IOException            If any.
     * @throws ClassNotFoundException If any.
     */
    @Serial
    private void readObject(ObjectInputStream s)
            throws IOException, ClassNotFoundException {
        s.defaultReadObject();

    }

    /**
     * Retrieves the variables template.
     *
     * @return the variables template
     */
    public String getVariablesTemplate() {
        return this.variablesTemplate;
    }

    /**
     * Sets the variables template.
     *
     * @param variablesTemplate the template for variables
     * @throws ParseException       if the variables template fails to parse
     * @throws NullPointerException if the variables template is null
     */
    public void setVariablesTemplate(String variablesTemplate) throws ParseException {
        if (variablesTemplate == null) {
            throw new NullPointerException();
        }

        // Test to make sure it's parsable.
        ExpressionParser parser = new ExpressionParser();
        parser.parseExpression(variablesTemplate);

        this.variablesTemplate = variablesTemplate;
    }

    /**
     * Returns the errors template string.
     *
     * @return the errors template string
     */
    public String getErrorsTemplate() {
        return this.errorsTemplate;
    }

    /**
     * Sets the errors template for the software.
     *
     * @param errorsTemplate the string representation of the errors template
     * @throws ParseException       if the errors template cannot be parsed successfully
     * @throws NullPointerException if the errorsTemplate parameter is null
     */
    public void setErrorsTemplate(String errorsTemplate) throws ParseException {
        if (errorsTemplate == null) {
            throw new NullPointerException();
        }

        // Test to make sure it's parsable.
        ExpressionParser parser = new ExpressionParser();
        parser.parseExpression(errorsTemplate);

        this.errorsTemplate = errorsTemplate;
    }

    /**
     * Retrieves the template for the parameters.
     *
     * @return The template for the parameters.
     */
    public String getParametersTemplate() {
        return this.parametersTemplate;
    }

    /**
     * Sets the parameters template for the object.
     *
     * @param parametersTemplate the template string representing the parameters
     * @throws ParseException           if the given parameters template is not valid
     * @throws NullPointerException     if the parameters template is null
     * @throws IllegalArgumentException if the parameters template contains parameters
     * @see ExpressionParser
     */
    public void setParametersTemplate(String parametersTemplate) throws ParseException {
        if (parametersTemplate == null) {
            throw new NullPointerException();
        }

        // Test to make sure it's parsable.
        ExpressionParser parser = new ExpressionParser();
        Expression expression = parser.parseExpression(parametersTemplate);
        List<String> parameterNames = parser.getParameters();

        if (!parameterNames.isEmpty()) {
            throw new IllegalArgumentException("Initial distribution for a parameter may not " +
                    "contain parameters: " + expression.toString());
        }

        this.parametersTemplate = parametersTemplate;
    }

    /**
     * Retrieves the parameters estimation initialization template.
     *
     * @return The parameters estimation initialization template as a {@code String}.
     */
    public String getParametersEstimationInitializationTemplate() {
        return this.parametersEstimationInitializationTemplate;
    }

    /**
     * Sets the template for parameters estimation initialization.
     *
     * @param parametersTemplate the template string for parameters estimation initialization
     * @throws ParseException       if the provided parameters template is not parseable
     * @throws NullPointerException if the provided parameters template is null
     */
    public void setParametersEstimationInitializationTemplate(String parametersTemplate) throws ParseException {
        if (parametersTemplate == null) {
            throw new NullPointerException();
        }

        // Test to make sure it's parsable.
        ExpressionParser parser = new ExpressionParser();
        parser.parseExpression(parametersTemplate);

        this.parametersEstimationInitializationTemplate = parametersTemplate;
    }

    /**
     * Sets the parameters template for expressions that start with the specified string. If the startsWith parameter is
     * null or empty, the method does nothing. If the parametersTemplate parameter is null, a NullPointerException is
     * thrown. The method parses the parametersTemplate to ensure it is a valid expression. If the startsWith string
     * contains spaces, an IllegalArgumentException is thrown.
     *
     * @param startsWith         The starting string for the expressions.
     * @param parametersTemplate The template for the parameters of the expressions.
     * @throws ParseException           If the parametersTemplate is not a valid expression.
     * @throws NullPointerException     If the parametersTemplate is null.
     * @throws IllegalArgumentException If the startsWith string contains spaces.
     */
    public void setStartsWithParametersTemplate(String startsWith, String parametersTemplate) throws ParseException {
        if (startsWith == null || startsWith.isEmpty()) {
            return;
        }

        if (parametersTemplate == null) {
            throw new NullPointerException();
        }

        // Test to make sure it's parsable.
        ExpressionParser parser = new ExpressionParser();
        parser.parseExpression(parametersTemplate);

        if (startsWith.contains(" ")) {
            throw new IllegalArgumentException("Starts with string contains spaces.");
        }

        this.startsWithParametersTemplates.put(startsWith, parametersTemplate);
    }

    /**
     * Sets the parameters estimation initialization template for a given startsWith string.
     *
     * @param startsWith                                 the string that the template should start with
     * @param parametersEstimationInitializationTemplate the template for initializing the parameters estimation
     * @throws ParseException           if the template is unable to be parsed
     * @throws NullPointerException     if the parametersTemplate is null
     * @throws IllegalArgumentException if the startsWith string contains spaces
     */
    public void setStartsWithParametersEstimationInitializaationTemplate(String startsWith,
                                                                         String parametersEstimationInitializationTemplate)
            throws ParseException {
        if (startsWith == null || startsWith.isEmpty()) {
            return;
        }

        if (this.parametersTemplate == null) {
            throw new NullPointerException();
        }

        // Test to make sure it's parsable.
        ExpressionParser parser = new ExpressionParser();
        parser.parseExpression(parametersEstimationInitializationTemplate);

        if (startsWith.contains(" ")) {
            throw new IllegalArgumentException("Starts with string contains spaces.");
        }

        this.startsWithParametersEstimationInitializationTemplates.put(startsWith, parametersEstimationInitializationTemplate);
    }

    /**
     * <p>getStartsWithParameterTemplate.</p>
     *
     * @param startsWith a {@link java.lang.String} object
     * @return a {@link java.lang.String} object
     */
    public String getStartsWithParameterTemplate(String startsWith) {

        return this.startsWithParametersTemplates.get(startsWith);
    }

    /**
     * <p>getStartsWithParameterEstimationInitializatonTemplate.</p>
     *
     * @param startsWith a {@link java.lang.String} object
     * @return a {@link java.lang.String} object
     */
    public String getStartsWithParameterEstimationInitializatonTemplate(String startsWith) {
        return this.startsWithParametersEstimationInitializationTemplates.get(startsWith);
    }

    /**
     * <p>startsWithPrefixes.</p>
     *
     * @return a {@link java.util.Set} object
     */
    public Set<String> startsWithPrefixes() {
        return this.startsWithParametersTemplates.keySet();
    }
}


