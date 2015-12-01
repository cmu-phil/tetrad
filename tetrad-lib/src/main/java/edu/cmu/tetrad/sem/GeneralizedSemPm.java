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

import edu.cmu.tetrad.calculator.expression.Expression;
import edu.cmu.tetrad.calculator.parser.ExpressionParser;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.util.PM;
import edu.cmu.tetrad.util.TetradSerializable;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.text.ParseException;
import java.util.*;


/**
 * Parametric model for Generalized SEM model. This contains all of the
 * equations of the model with parameters represented symbolically (i.e.
 * no values for parameters).
 *
 * @author Joseph Ramsey
 */
public final class GeneralizedSemPm implements PM, TetradSerializable {
    static final long serialVersionUID = 23L;

    /**
     * The structural model graph that this sem parametric model is based on.
     *
     * @serial Cannot be null.
     */
    private SemGraph graph;

    /**
     * The list of all nodes (unmodifiable).
     *
     * @serial Cannot be null.
     */
    private List<Node> nodes;

    /**
     * The latent and measured nodes.
     */
    private List<Node> variableNodes;

    /**
     * The measured nodes.
     */
    private List<Node> measuredNodes;

    /**
     * The error nodes.
     */
    private List<Node> errorNodes;

    /**
     * The freeParameters in the model, mapped to the nodes that they are associated with. Each parameter may
     * be associated with more than one node. When freeParameters are removed from an equation or error distribution,
     * the associated nodes should be removed from the relevant set in this map, and if the set is empty,
     * the parameter should be removed from the map. Also, before adding a parameter to this map, it must be
     * checked whether a parameter by the same name already exists. If one such parameter by the same name
     * already exists, that one should be used instead of the new one. This is needed both to avoid confusion
     * and to allow freeParameters to be reused in different parts of the interface, creating equality constraints.
     *
     * @serial Cannot be null.
     */
    private Map<String, Set<Node>> referencedParameters;

    /**
     * The nodes of the model, variable nodes or error nodes, mapped to the other nodes that they are
     * associated with.
     */
    private Map<Node, Set<Node>> referencedNodes;

    /**
     * The map from variable nodes to equations.
     */
    private Map<Node, Expression> nodeExpressions;

    /**
     * The String representations of equations that were set.
     */
    private Map<Node, String> nodeExpressionStrings;

    /**
     * Distributions from which initial values for freeParameters are drawn.
     */
    private Map<String, Expression> parameterExpressions;

    /**
     * String representations of initial parameter distributions. A map from parameter names to expression strings.
     */
    private Map<String, String> parameterExpressionStrings;

    /**
     * Distributions from which initial values for freeParameters are drawn.
     */
    private Map<String, Expression> parameterEstimationInitializationExpressions;

    /**
     * String representations of initial parameter distributions. A map from parameter names to expression strings.
     */
    private Map<String, String> parameterEstimationInitializationExpressionStrings;

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
    private String parametersTemplate = "Split(-1.5,-.5,.5,1.5)";

    /**
     * The stored template for freeParameters.
     */
    private String parametersEstimationInitializationTemplate = "Split(-1.5,-.5,.5,1.5)";

    /**
     * The list of variable names.
     */
    private List<String> variableNames;

    /**
     * A map from initial name strings to parameter templates.
     */
    private Map<String, String> startsWithParametersTemplates;

    /**
     * A map from initial name strings to parameter templates.
     */
    private Map<String, String> startsWithParametersEstimationInitializationTemplates;

    /**
     * A map from names to nodes, speedup.
     */
    private Map<String, Node> namesToNodes = new HashMap<>();

    //===========================CONSTRUCTORS==========================//

    /**
     * Constructs a BayesPm from the given Graph, which must be convertible
     * first into a ProtoSemGraph and then into a SemGraph.
     */
    public GeneralizedSemPm(Graph graph) {
        this(new SemGraph(graph));
    }

    /**
     * Constructs a new SemPm from the given SemGraph.
     */
    public GeneralizedSemPm(SemGraph graph) {
        if (graph == null) {
            throw new NullPointerException("Graph must not be null.");
        }

//        if (graph.existsDirectedCycle()) {
//            throw new IllegalArgumentExcneption("Cycles are not supported.");
//        }

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

        for (Node node : nodes) {
            namesToNodes.put(node.getName(), node);
        }

        this.variableNodes = new ArrayList<>();
        this.measuredNodes = new ArrayList<>();

        for (Node variable : this.nodes) {
            if (variable.getNodeType() == NodeType.MEASURED ||
                    variable.getNodeType() == NodeType.LATENT) {
                variableNodes.add(variable);
            }

            if (variable.getNodeType() == NodeType.MEASURED) {
                measuredNodes.add(variable);
            }
        }

        this.errorNodes = new ArrayList<Node>();

        for (Node variable : this.variableNodes) {
            List<Node> parents = this.graph.getParents(variable);
            boolean added = false;

            for (Node _node : parents) {
                if (_node.getNodeType() == NodeType.ERROR) {
                    errorNodes.add(_node);
                    added = true;
                    break;
                }
            }

            if (!added) {
                if (!added) errorNodes.add(null);
            }
        }

        this.referencedParameters = new HashMap<String, Set<Node>>();
        this.referencedNodes = new HashMap<Node, Set<Node>>();
        this.nodeExpressions = new HashMap<Node, Expression>();
        this.nodeExpressionStrings = new HashMap<Node, String>();
        this.parameterExpressions = new HashMap<String, Expression>();
        this.parameterExpressionStrings = new HashMap<String, String>();
        this.parameterEstimationInitializationExpressions = new HashMap<String, Expression>();
        this.parameterEstimationInitializationExpressionStrings = new HashMap<String, String>();
        this.startsWithParametersTemplates = new HashMap<String, String>();
        this.startsWithParametersEstimationInitializationTemplates = new HashMap<String, String>();

        this.variableNames = new ArrayList<String>();
        for (Node _node : variableNodes) variableNames.add(_node.getName());
        for (Node _node : errorNodes) variableNames.add(_node.getName());

        try {
            List<Node> variableNodes = getVariableNodes();

            for (int i = 0; i < variableNodes.size(); i++) {
                Node node = variableNodes.get(i);

                if (!this.graph.isParameterizable(node)) continue;

                if (nodeExpressions.get(node) != null) {
                    continue;
                }

                String variablestemplate = getVariablesTemplate();
                String formula = TemplateExpander.getInstance().expandTemplate(variablestemplate, this, node);
                setNodeExpression(node, formula);
                Set<String> parameters = getReferencedParameters(node);

                String parametersTemplate = getParametersTemplate();

                for (String parameter : parameters) {
                    if (parameterExpressions.get(parameter) != null) {
                        //
                    } else if (parametersTemplate != null) {
                        setParameterExpression(parameter, parametersTemplate);
                    } else if (this.graph.isTimeLagModel()) {
                        String expressionString = "Split(-0.9, -.1, .1, 0.9)";
                        setParameterExpression(parameter, expressionString);
                        setParametersTemplate(expressionString);
                    } else {
                        String expressionString = "Split(-1.5, -.5, .5, 1.5)";
                        setParameterExpression(parameter, expressionString);
                        setParametersTemplate(expressionString);
                    }
                }

                for (String parameter : parameters) {
                    if (parameterEstimationInitializationExpressions.get(parameter) != null) {
                        //
                    } else if (parametersTemplate != null) {
                        setParameterEstimationInitializationExpression(parameter, parametersTemplate);
                    } else if (this.graph.isTimeLagModel()) {
                        String expressionString = "Split(-0.9, -.1, .1, 0.9)";
                        setParameterEstimationInitializationExpression(parameter, expressionString);
                    } else {
                        String expressionString = "Split(-1.5, -.5, .5, 1.5)";
                        setParameterEstimationInitializationExpression(parameter, expressionString);
                    }

                    setStartsWithParametersTemplate("s", "Split(-1.5, -.5, .5, 1.5)");
                    setStartsWithParametersEstimationInitializaationTemplate("s", "Split(-1.5, -.5, .5, 1.5)");
                }
            }

            for (Node node : errorNodes) {
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

    public GeneralizedSemPm(SemPm semPm) {
        this(semPm.getGraph());

        // Write down equations.
        try {
            List<Node> variableNodes = getVariableNodes();

            for (int i = 0; i < variableNodes.size(); i++) {
                Node node = variableNodes.get(i);
                List<Node> parents = getVariableParents(node);

                StringBuilder buf = new StringBuilder();

                for (int j = 0; j < parents.size(); j++) {
                    if (!(variableNodes.contains(parents.get(j)))) {
                        continue;
                    }

                    Node parent = parents.get(j);

                    Parameter _parameter = semPm.getParameter(parent, node);
                    String parameter = _parameter.getName();
                    Set<Node> nodes = new HashSet<Node>();
                    nodes.add(node);

                    referencedParameters.put(parameter, nodes);

                    buf.append(parameter);
                    buf.append("*");
                    buf.append(parents.get(j).getName());

                    setParameterExpression(parameter, "Split(-1.5, -.5, .5, 1.5)");
                    setStartsWithParametersTemplate(parameter.substring(0, 1), "Split(-1.5, -.5, .5, 1.5)");
                    setStartsWithParametersEstimationInitializaationTemplate(parameter.substring(0, 1), "Split(-1.5, -.5, .5, 1.5)");

                    if (j < parents.size() - 1) {
                        buf.append(" + ");
                    }
                }

                if (buf.toString().trim().length() != 0) {
                    buf.append(" + ");
                }

                buf.append(errorNodes.get(i));
                setNodeExpression(node, buf.toString());
            }

            for (Node node : variableNodes) {
                Parameter _parameter = semPm.getParameter(node, node);
                String parameter = _parameter.getName();

                Set<Node> nodes = new HashSet<Node>();
                nodes.add(node);

                String distributionFormula = "N(0," + parameter + ")";
                setNodeExpression(getErrorNode(node), distributionFormula);
                setParameterExpression(parameter, "U(0, 1)");
                setStartsWithParametersTemplate(parameter.substring(0, 1), "U(0, 1)");
                setStartsWithParametersEstimationInitializaationTemplate(parameter.substring(0, 1), "U(0, 1)");
            }

            variableNames = new ArrayList<String>();
            for (Node _node : variableNodes) variableNames.add(_node.getName());
            for (Node _node : errorNodes) variableNames.add(_node.getName());

        } catch (ParseException e) {
            throw new IllegalStateException("Parse error in constructing initial model.", e);
        }
    }

    /**
     * Copy constructor.
     */
    public GeneralizedSemPm(GeneralizedSemPm semPm) {
        this.graph = new SemGraph(semPm.graph);
        this.nodes = new ArrayList<Node>(semPm.nodes);
        this.variableNodes = new ArrayList<Node>(semPm.variableNodes);
        this.measuredNodes = new ArrayList<Node>(semPm.measuredNodes);
        this.errorNodes = new ArrayList<Node>(semPm.errorNodes);
        this.referencedParameters = new HashMap<String, Set<Node>>();
        this.referencedNodes = new HashMap<Node, Set<Node>>();

        for (String parameter : semPm.referencedParameters.keySet()) {
            this.referencedParameters.put(parameter, new HashSet<Node>(semPm.referencedParameters.get(parameter)));
        }

        for (Node node : semPm.referencedNodes.keySet()) {
            this.referencedNodes.put(node, new HashSet<Node>(semPm.referencedNodes.get(node)));
        }

        this.nodeExpressions = new HashMap<Node, Expression>(semPm.nodeExpressions);
        this.nodeExpressionStrings = new HashMap<Node, String>(semPm.nodeExpressionStrings);
        this.parameterExpressions = new HashMap<String, Expression>(semPm.parameterExpressions);
        this.parameterExpressionStrings = new HashMap<String, String>(semPm.parameterExpressionStrings);
        this.parameterEstimationInitializationExpressions = new HashMap<String, Expression>(semPm.parameterEstimationInitializationExpressions);
        this.parameterEstimationInitializationExpressionStrings = new HashMap<String, String>(semPm.parameterEstimationInitializationExpressionStrings);
        this.variablesTemplate = semPm.variablesTemplate;
        this.errorsTemplate = semPm.errorsTemplate;
        this.parametersTemplate = semPm.parametersTemplate;
        this.variableNames = semPm.variableNames == null ? new ArrayList<String>() : new ArrayList<String>(semPm.variableNames);
        this.startsWithParametersTemplates = new HashMap<String, String>(semPm.startsWithParametersTemplates);
        this.startsWithParametersEstimationInitializationTemplates
                = new HashMap<String, String>(semPm.startsWithParametersEstimationInitializationTemplates);

    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     */
    public static GeneralizedSemPm serializableInstance() {
        Dag dag = new Dag();
        GraphNode node1 = new GraphNode("X");
        dag.addNode(node1);
        return new GeneralizedSemPm(Dag.serializableInstance());
    }

    //============================PUBLIC METHODS========================//

    public Expression getNodeExpression(Node node) {
        return this.nodeExpressions.get(node);
    }

    public String getNodeExpressionString(Node node) {
        return nodeExpressionStrings.get(node);
    }

    public void setNodeExpression(Node node, String expressionString) throws ParseException {
        if (node == null) {
            throw new NullPointerException("Node was null.");
        }

        if (expressionString == null) {
//            return;
            throw new NullPointerException("Expression string was null.");
        }

        // Parse the expression. This could throw an ParseException, but that exception needs to handed up the
        // chain, because the interface will need it.
        ExpressionParser parser = new ExpressionParser();
        Expression expression = parser.parseExpression(expressionString);
        List<String> parameterNames = parser.getParameters();

        // Make a list of parent names.
        List<Node> parents = this.graph.getParents(node);
        List<String> parentNames = new LinkedList<String>();

        for (Node parent : parents) {
            parentNames.add(parent.getName());
        }

//        List<String> _params = new ArrayList<String>(parameterNames);
//        _params.retainAll(variableNames);
//        _params.removeAll(parentNames);
//
//        if (!_params.isEmpty()) {
//            throw new IllegalArgumentException("Conditioning on a variable other than the parents: " + node);
//        }

        // Make a list of parameter names, by removing from the parser's list of freeParameters any that correspond
        // to parent variables. If there are any variable names (including error terms) that are not among the list of
        // parents, that's a time to throw an exception. We must respect the graph! (We will not complain if any parents
        // are missing.)
        parameterNames.removeAll(variableNames);

        for (Node variable : nodes) {
            if (parameterNames.contains(variable.getName())) {
                parameterNames.remove(variable.getName());
//                throw new IllegalArgumentException("The list of parameter names may not include variables: " + variable.getName());
            }
        }

        // Remove old parameter references.
        List<String> parametersToRemove = new LinkedList<String>();

        for (String parameter : this.referencedParameters.keySet()) {
            Set<Node> nodes = this.referencedParameters.get(parameter);

            if (nodes.contains(node)) {
                nodes.remove(node);
            }

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
            if (this.referencedParameters.get(parameter) == null) {
                this.referencedParameters.put(parameter, new HashSet<Node>());
            }

            Set<Node> nodes = this.referencedParameters.get(parameter);
            nodes.add(node);

            setSuitableParameterDistribution(parameter);
        }

        // Remove old node references.
        List<Node> nodesToRemove = new LinkedList<Node>();

        for (Node _node : this.referencedNodes.keySet()) {
            Set<Node> nodes = this.referencedNodes.get(_node);

            if (nodes.contains(node)) {
                nodes.remove(node);
            }

            if (nodes.isEmpty()) {
                nodesToRemove.add(_node);
            }
        }

        for (Node _node : nodesToRemove) {
            this.referencedNodes.remove(_node);
        }

        // Add new freeParameters.
        for (String variableString : variableNames) {
            Node _node = getNode(variableString);

            if (this.referencedNodes.get(_node) == null) {
                this.referencedNodes.put(_node, new HashSet<Node>());
            }

            for (String s : parentNames) {
                if (s.equals(variableString)) {
                    Set<Node> nodes = this.referencedNodes.get(_node);
                    nodes.add(node);
                }
            }
        }

        // Finally, save the parsed expression and the original string that the user entered. No need to annoy
        // the user by changing spacing.
        nodeExpressions.put(node, expression);
        nodeExpressionStrings.put(node, expressionString);
    }

    private void setSuitableParameterDistribution(String parameter) throws ParseException {
        boolean found = false;

        for (String prefix : startsWithParametersTemplates.keySet()) {
            if (parameter.startsWith(prefix)) {
                if (parameterExpressions.get(parameter) == null) {
                    setParameterExpression(parameter, startsWithParametersTemplates.get(prefix));
                }
                if (parameterEstimationInitializationExpressions.get(parameter) == null) {
                    setParameterEstimationInitializationExpression(parameter, startsWithParametersTemplates.get(prefix));
                }
                found = true;
            }
        }

        if (!found) {
            if (parameterExpressions.get(parameter) == null) {
                setParameterExpression(parameter, getParametersTemplate());
            }
            if (parameterEstimationInitializationExpressions.get(parameter) == null) {
                setParameterEstimationInitializationExpression(parameter, getParametersTemplate());
            }
        }
    }

    /**
     * Sets the expression which should be evaluated when calculating new values for the given
     * parameter. These values are used to initialize the freeParameters.
     *
     * @param parameter        The parameter whose initial value needs to be computed.
     * @param expressionString The formula for picking initial values.
     * @throws ParseException If the formula cannot be parsed or contains variable names.
     */
    public void setParameterExpression(String parameter, String expressionString)
            throws ParseException {
        if (parameter == null) {
            throw new NullPointerException("Parameter was null.");
        }

        if (expressionString == null) {
            throw new NullPointerException("Expression string was null.");
        }

        // Parse the expression. This could throw an ParseException, but that exception needs to handed up the
        // chain, because the interface will need it.
        ExpressionParser parser = new ExpressionParser();
        Expression expression = parser.parseExpression(expressionString);
        List<String> parameterNames = parser.getParameters();

        if (parameterNames.size() > 0) {
            throw new IllegalArgumentException("Initial distribution for a parameter may not " +
                    "contain parameters: " + expressionString);
        }

        parameterExpressions.put(parameter, expression);
        parameterExpressionStrings.put(parameter, expressionString);
    }

    public void setParameterEstimationInitializationExpression(String parameter, String expressionString)
            throws ParseException {
        if (parameter == null) {
            throw new NullPointerException("Parameter was null.");
        }

        if (expressionString == null) {
            throw new NullPointerException("Expression string was null.");
        }

        // Parse the expression. This could throw an ParseException, but that exception needs to handed up the
        // chain, because the interface will need it.
        ExpressionParser parser = new ExpressionParser();
        Expression expression = parser.parseExpression(expressionString);
        List<String> parameterNames = parser.getParameters();

        if (parameterNames.size() > 0) {
            throw new IllegalArgumentException("Initial distribution may not " +
                    "contain parameters: " + expressionString);
        }

        parameterEstimationInitializationExpressions.put(parameter, expression);
        parameterEstimationInitializationExpressionStrings.put(parameter, expressionString);
    }

    /**
     * Sets the expression which should be evaluated when calculating new values for the given
     * parameter. These values are used to initialize the freeParameters.
     *
     * @param parameter        The parameter whose initial value needs to be computed.
     * @param expressionString The formula for picking initial values.
     * @throws ParseException If the formula cannot be parsed or contains variable names.
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

        // Parse the expression. This could throw an ParseException, but that exception needs to handed up the
        // chain, because the interface will need it.
        ExpressionParser parser = new ExpressionParser();
        Expression expression = parser.parseExpression(expressionString);
        List<String> parameterNames = parser.getParameters();

        if (parameterNames.size() > 0) {
            throw new IllegalArgumentException("Initial distribution may not " +
                    "contain parameters: " + expressionString);
        }

        parameterExpressions.put(parameter, expression);
        parameterExpressionStrings.put(parameter, expressionString);
        startsWithParametersTemplates.put(startsWith, expressionString);
    }

    /**
     * Sets the expression which should be evaluated when calculating new values for the given
     * parameter. These values are used to initialize the freeParameters.
     *
     * @param parameter        The parameter whose initial value needs to be computed.
     * @param expressionString The formula for picking initial values.
     * @throws ParseException If the formula cannot be parsed or contains variable names.
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

        // Parse the expression. This could throw an ParseException, but that exception needs to handed up the
        // chain, because the interface will need it.
        ExpressionParser parser = new ExpressionParser();
        Expression expression = parser.parseExpression(expressionString);
        List<String> parameterNames = parser.getParameters();

        if (parameterNames.size() > 0) {
            throw new IllegalArgumentException("Initial distribution may not " +
                    "contain parameters: " + expressionString);
        }

        parameterEstimationInitializationExpressions.put(parameter, expression);
        parameterEstimationInitializationExpressionStrings.put(parameter, expressionString);
        startsWithParametersTemplates.put(startsWith, expressionString);
    }

    /**
     * @return the set of freeParameters for the model.
     */
    public Set<String> getParameters() {
        return new HashSet<String>(parameterExpressions.keySet());
    }

    /**
     * @return the set of freeParameters for the model.
     */
    public Set<String> getEstimationInitializationParameters() {
        return new HashSet<String>(parameterEstimationInitializationExpressions.keySet());
    }

    /**
     * @param parameter The parameter whose initial value needs to be evaluated.
     * @return an expression that can be used to calculate the initial value.
     */
    public Expression getParameterExpression(String parameter) {
        return parameterExpressions.get(parameter);
    }

    /**
     * @param parameter The parameter whose initial value needs to be evaluated.
     * @return an expression that can be used to calculate the initial value.
     */
    public Expression getParameterEstimationInitializationExpression(String parameter) {
        return parameterEstimationInitializationExpressions.get(parameter);
    }

    /**
     * @param parameter The parameter whose initial value needs to be computed.
     * @return The formula string that was set using <code>setParameterExpression</code>, with spacing intact.
     */
    public String getParameterExpressionString(String parameter) {
        return parameterExpressionStrings.get(parameter);
    }

    /**
     * @param parameter The parameter whose initial value needs to be computed.
     * @return The formula string that was set using <code>setParameterExpression</code>, with spacing intact.
     */
    public String getParameterEstimationInitializationExpressionString(String parameter) {
        return parameterEstimationInitializationExpressionStrings.get(parameter);
    }

    /**
     * @return the structural model graph this SEM PM is using.
     */
    public SemGraph getGraph() {
        return new SemGraph(this.graph);
    }


    /**
     * @return all of the nodes in the sem, including error nodes.
     */
    public List<Node> getNodes() {
        return new ArrayList<Node>(nodes);
    }

    /**
     * @return the list of variable nodes--that is, node that are not error
     * nodes.
     */
    public List<Node> getVariableNodes() {
        return new ArrayList<Node>(this.variableNodes);
    }

    /**
     * @return the lsit of measured nodes.
     */
    public List<Node> getMeasuredNodes() {
        return new ArrayList<Node>(this.measuredNodes);
    }

    /**
     * @return the list of exogenous variableNodes.
     */
    public List<Node> getErrorNodes() {
        return new ArrayList<Node>(this.errorNodes);
    }

    /**
     * @param errorNode the error node.
     * @return the variable node for the given error node.
     */
    public Node getVariableNode(Node errorNode) {
        int index = errorNodes.indexOf(errorNode);

        if (index == -1) {
            throw new NullPointerException(errorNode + " is not an error node in this model.");
        }

        return variableNodes.get(index);
    }

    /**
     * @param node The variable node in question.
     * @return the error node for the given node.
     */
    public Node getErrorNode(Node node) {
        if (errorNodes.contains(node)) {
            return node;
        }

        int index = variableNodes.indexOf(node);

        if (index == -1) {
            return null;
//            throw new NullPointerException(node + " is not a node in this model.");
        }

        return errorNodes.get(index);
    }

    /**
     * @param name the name of the parameter.
     * @return the variable with the given name, if there is one. Otherwise, null.
     */
    public Node getNode(String name) {
//        for (Node node : nodes) {
//            if (name.equals(node.getName())) {
//                return node;
//            }
//        }
//
//        return null;

        // This was slow. jdramsey 20150929
        return namesToNodes.get(name);
    }

    /**
     * @param parameter The parameter in question.
     * @return the set of nodes that reference a given parameter.
     */
    public Set<Node> getReferencingNodes(String parameter) {
        Set<Node> set = this.referencedParameters.get(parameter);
        return set == null ? new HashSet<Node>() : new HashSet<Node>(set);
    }

    /**
     * @param node the node doing the referencing.
     * @return the freeParameters referenced by the given variable (variable node or error node).
     */
    public Set<String> getReferencedParameters(Node node) {
        Set<String> parameters = new HashSet<String>();

        for (String parameter : this.referencedParameters.keySet()) {
            if (this.referencedParameters.get(parameter).contains(node)) {
                parameters.add(parameter);
            }
        }

        return parameters;
    }

    /**
     * @param node the node doing the referencing.
     * @return the set of nodes (variable or error) referenced by the expression for the given
     * node.
     */
    public Set<Node> getReferencingNodes(Node node) {
        Set<Node> set = referencedNodes.get(node);
        return set == null ? new HashSet<Node>() : new HashSet<Node>(set);
    }

    /**
     * @param node the node doing the referencing.
     * @return the variables referenced by the expression for the given node (variable node or
     * error node.
     */
    public Set<Node> getReferencedNodes(Node node) {
        Set<Node> nodes = new HashSet<Node>();

        for (Node _node : this.referencedNodes.keySet()) {
            if (this.referencedNodes.get(_node).contains(node)) {
                nodes.add(_node);
            }
        }

        return nodes;
    }

    /**
     * Given base <b> (a String), returns the first name in the sequence "<b>1",
     * "<b>2", "<b>3", etc., which is not already the name of a node in the
     * workbench.
     *
     * @param base      the base string.
     * @param usedNames A further list of parameter names to avoid.
     * @return the first string in the sequence not already being used.
     */
    public String nextParameterName(String base, List<String> usedNames) {
        if (this.graph.getNode(base) != null) {
            throw new IllegalArgumentException(base + " is a variable name.");
        }

        // Names should start with "1."
        int i = 0;
        int subscript = 0;

        if (parameterSubscript.containsKey(base)) {
            subscript = parameterSubscript.get(base);
        }

        subscript++;
        parameterSubscript.put(base, subscript);
        return base + subscript;

//        Integer subscript = parameterSubscript.get(base);
//        if (subscript == null) subscript = 1;
//
//
//        loop:
//        while (true) {
//            String name = base + (++i);
//
//            for (String parameter : referencedParameters.keySet()) {
//                if (parameter.equals(name)) {
//                    continue loop;
//                }
//            }
//
//            for (String parameter : usedNames) {
//                if (parameter.equals(name)) {
//                    continue loop;
//                }
//            }
//
//            break;
//        }
//
//        parameterSubscript.put(base, i);
//
//        return base + i;
    }

    private Map<String, Integer> parameterSubscript = new HashMap<String, Integer>();

    /**
     * @param node the given node, variable or error.
     * @return all parents of the given node, with error node(s?) last.
     */
    public List<Node> getParents(Node node) {
        List<Node> parents = this.graph.getParents(node);
        parents = putErrorNodesLast(parents);
        return new ArrayList<Node>(parents);
    }

    /**
     * @return a relatively brief String representation of this SEM PM--the equations and distributions
     * of the model. Initial value distributions for freeParameters are not printed.
     */
    public String toString() {
        StringBuilder buf = new StringBuilder();
        buf.append("\nEquations:\n");

        for (Node node : variableNodes) {
            buf.append("\n").append(node).append(" = ").append(nodeExpressionStrings.get(node));
        }

        buf.append("\n\nErrors:\n");

        for (Node node : errorNodes) {
            buf.append("\n").append(node).append(" ~ ").append(nodeExpressionStrings.get(node));
        }

        buf.append("\n\nParameters:\n");

        for (String param : getParameters()) {
            buf.append("\n").append(param).append(" ~ ").append(getParameterExpressionString(param));
        }

        return buf.toString();
    }

    //============================PRIVATE METHODS======================//

    /**
     * @param node A node in the graph.
     * @return The non-error parents of <code>node</code>.
     */
    private List<Node> getVariableParents(Node node) {
        List<Node> allParents = this.graph.getParents(node);
        List<Node> parents = new LinkedList<Node>();

        for (Node _parent : allParents) {
            if (_parent.getNodeType() != NodeType.ERROR) {
                parents.add(_parent);
            }
        }
        return parents;
    }

    private String getVariableString(List<Node> parents) {
        StringBuilder buf = new StringBuilder();

        // Putting error nodes last. (Allowing multiple error nodes for debugging purposes; doesn't hurt.)
        List<Node> sortedNodes = putErrorNodesLast(parents);

        for (int i = 0; i < sortedNodes.size(); i++) {
            Node node = sortedNodes.get(i);
            buf.append(node.getName());

            if (i < sortedNodes.size() - 1) {
                buf.append(", ");
            }
        }

        return buf.toString();
    }

    private List<Node> putErrorNodesLast(List<Node> parents) {
        List<Node> sortedNodes = new LinkedList<Node>();

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
     * Adds semantic checks to the default deserialization method. This method
     * must have the standard signature for a readObject method, and the body of
     * the method must begin with "s.defaultReadObject();". Other than that, any
     * semantic checks can be specified and do not need to stay the same from
     * version to version. A readObject method of this form may be added to any
     * class, even if Tetrad sessions were previously saved out using a version
     * of the class that didn't include it. (That's what the
     * "s.defaultReadObject();" is for. See J. Bloch, Effective Java, for help.
     *
     * @throws java.io.IOException
     * @throws ClassNotFoundException
     */
    private void readObject(ObjectInputStream s)
            throws IOException, ClassNotFoundException {
        s.defaultReadObject();

    }

    public String getVariablesTemplate() {
        return variablesTemplate;
    }

    public void setVariablesTemplate(String variablesTemplate) throws ParseException {
        if (variablesTemplate == null) {
            throw new NullPointerException();
        }

        // Test to make sure it's parsable.
        ExpressionParser parser = new ExpressionParser();
        parser.parseExpression(variablesTemplate);

        this.variablesTemplate = variablesTemplate;
    }

    public String getErrorsTemplate() {
        return errorsTemplate;
    }

    public void setErrorsTemplate(String errorsTemplate) throws ParseException {
        if (errorsTemplate == null) {
            throw new NullPointerException();
        }

        // Test to make sure it's parsable.
        ExpressionParser parser = new ExpressionParser();
        parser.parseExpression(errorsTemplate);

        this.errorsTemplate = errorsTemplate;
    }

    public String getParametersTemplate() {
        return this.parametersTemplate;
    }

    public String getParametersEstimationInitializationTemplate() {
        return this.parametersEstimationInitializationTemplate;
    }

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

    public void setParametersEstimationInitializationTemplate(String parametersTemplate) throws ParseException {
        if (parametersTemplate == null) {
            throw new NullPointerException();
        }

        // Test to make sure it's parsable.
        ExpressionParser parser = new ExpressionParser();
        parser.parseExpression(parametersTemplate);

        this.parametersEstimationInitializationTemplate = parametersTemplate;
    }

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

//        this.parametersTemplate = parametersTemplate;

        this.startsWithParametersTemplates.put(startsWith, parametersTemplate);
    }

    public void setStartsWithParametersEstimationInitializaationTemplate(String startsWith,
                                                                         String parametersEstimationInitializationTemplate)
            throws ParseException {
        if (startsWith == null || startsWith.isEmpty()) {
            return;
        }

        if (parametersTemplate == null) {
            throw new NullPointerException();
        }

        // Test to make sure it's parsable.
        ExpressionParser parser = new ExpressionParser();
        parser.parseExpression(parametersEstimationInitializationTemplate);

        if (startsWith.contains(" ")) {
            throw new IllegalArgumentException("Starts with string contains spaces.");
        }

//        this.parametersTemplate = parametersTemplate;

        this.startsWithParametersEstimationInitializationTemplates.put(startsWith, parametersEstimationInitializationTemplate);
    }

    public String getStartsWithParameterTemplate(String startsWith) {

        return startsWithParametersTemplates.get(startsWith);
    }

    public String getStartsWithParameterEstimationInitializatonTemplate(String startsWith) {
        return startsWithParametersEstimationInitializationTemplates.get(startsWith);
    }

    public Set<String> startsWithPrefixes() {
        return startsWithParametersTemplates.keySet();
    }
}


